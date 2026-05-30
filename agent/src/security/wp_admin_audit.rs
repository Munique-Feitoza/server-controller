// agent/src/security/wp_admin_audit.rs
//! Audit SQL-level dos admins WP em todos os webapps locais.
//!
//! Roda a cada 5 min. Pra cada site `/home/runcloud/webapps/*/wp-config.php`:
//! 1. lista admins via `wp db query` (lê DB diretamente — independente de hooks PHP)
//! 2. compara com baseline persistida em `/var/lib/pocket-noc-agent/admin-baseline/`
//! 3. qualquer admin novo dispara `SecurityIncident` + ntfy
//!
//! Cobre o gap do `winup-no-hidden-admin.php`: um backdoor que faz
//! `$wpdb->insert(wp_users, ...)` direto NÃO passa pelos hooks `wp_pre_insert_user_data`
//! nem `set_user_role` — mas aparece no SELECT.

use crate::notifications::ntfy::NtfyClient;
use crate::security::incidents::{IncidentStore, SecurityIncident};
use std::collections::{HashMap, HashSet};
use std::path::{Path, PathBuf};
use std::process::Command;
use std::sync::Arc;
use std::time::Duration;
use tokio::sync::Mutex;
use tracing::{info, warn};

const WEBAPPS_DIR: &str = "/home/runcloud/webapps";
const BASELINE_DIR: &str = "/var/lib/pocket-noc-agent/admin-baseline";
const CYCLE_SECS: u64 = 300; // 5 min
const WP_CLI: &str = "/usr/local/bin/wp";

/// Spawn-friendly loop. Falha silenciosamente entre ciclos.
pub async fn run(incidents: Arc<Mutex<IncidentStore>>, ntfy: Arc<NtfyClient>, server_id: Arc<String>) {
    // Garante diretório do baseline (best-effort)
    let _ = std::fs::create_dir_all(BASELINE_DIR);

    info!("🛡 wp-admin-audit ativo (ciclo {}s)", CYCLE_SECS);
    loop {
        if let Err(e) = scan_cycle(&incidents, &ntfy, &server_id).await {
            warn!("wp-admin-audit cycle failed: {}", e);
        }
        tokio::time::sleep(Duration::from_secs(CYCLE_SECS)).await;
    }
}

async fn scan_cycle(
    incidents: &Arc<Mutex<IncidentStore>>,
    ntfy: &Arc<NtfyClient>,
    server_id: &str,
) -> std::io::Result<()> {
    let entries = std::fs::read_dir(WEBAPPS_DIR)?;
    for entry in entries.flatten() {
        let path = entry.path();
        if !path.join("wp-config.php").is_file() {
            continue;
        }
        let site = match path.file_name().and_then(|n| n.to_str()) {
            Some(s) => s.to_string(),
            None => continue,
        };
        let current = match list_admins(&path) {
            Some(v) if !v.is_empty() => v,
            _ => continue,
        };
        let baseline_path = baseline_file(&site);
        let prev = read_baseline(&baseline_path);

        if prev.is_none() {
            // 1ª execução pra esse site — só registra, não alerta
            let _ = write_baseline(&baseline_path, &current);
            continue;
        }
        let prev = prev.unwrap();
        let novos: Vec<&String> = current.iter().filter(|c| !prev.contains(*c)).collect();
        if !novos.is_empty() {
            for novo in &novos {
                push_incident(incidents, ntfy, server_id, &site, novo).await;
            }
        }
        let _ = write_baseline(&baseline_path, &current);
    }
    Ok(())
}

/// Retorna linhas no formato `id|user_login|user_email` ordenadas por ID.
/// Usa `wp db query` como usuário `runcloud`. Lê o prefix da própria CLI (respeita custom).
fn list_admins(path: &Path) -> Option<Vec<String>> {
    let prefix = run_wp(path, &["db", "prefix"])?.trim().to_string();
    if prefix.is_empty() || !prefix.chars().all(|c| c.is_ascii_alphanumeric() || c == '_') {
        return None;
    }
    let q = format!(
        "SELECT CONCAT(u.ID,'|',u.user_login,'|',u.user_email) \
         FROM {prefix}users u JOIN {prefix}usermeta m ON m.user_id=u.ID \
         WHERE m.meta_key='{prefix}capabilities' AND m.meta_value LIKE '%administrator%' \
         ORDER BY u.ID"
    );
    let out = run_wp(path, &["db", "query", &q])?;
    let mut admins: Vec<String> = out
        .lines()
        .skip_while(|l| !l.contains('|')) // pula header da tabela mysql
        .map(|l| l.trim().to_string())
        .filter(|l| !l.is_empty())
        .collect();
    admins.sort();
    admins.dedup();
    Some(admins)
}

fn run_wp(path: &Path, args: &[&str]) -> Option<String> {
    let mut cmd_args: Vec<String> = vec![
        "-u".into(), "runcloud".into(),
        WP_CLI.into(),
        format!("--path={}", path.display()),
    ];
    for a in args { cmd_args.push((*a).into()); }
    let out = Command::new("/usr/bin/sudo").args(&cmd_args).output().ok()?;
    if !out.status.success() {
        return None;
    }
    Some(String::from_utf8_lossy(&out.stdout).to_string())
}

fn baseline_file(site: &str) -> PathBuf {
    // sanitiza site name pra path
    let safe: String = site
        .chars()
        .map(|c| if c.is_ascii_alphanumeric() || c == '-' || c == '_' || c == '.' { c } else { '_' })
        .collect();
    PathBuf::from(BASELINE_DIR).join(format!("{}.admins", safe))
}

fn read_baseline(path: &Path) -> Option<HashSet<String>> {
    let content = std::fs::read_to_string(path).ok()?;
    Some(content.lines().map(|l| l.trim().to_string()).filter(|l| !l.is_empty()).collect())
}

fn write_baseline(path: &Path, admins: &[String]) -> std::io::Result<()> {
    let tmp = path.with_extension("tmp");
    std::fs::write(&tmp, admins.join("\n"))?;
    std::fs::rename(&tmp, path)?;
    // 600
    use std::os::unix::fs::PermissionsExt;
    let _ = std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o600));
    Ok(())
}

/// Janela pra correlacionar com webhook do `winup-security-guard`. Se já vimos
/// o mesmo (site,user_login) anunciado por ele nos últimos N segundos, não
/// re-alertamos no SQL audit — é admin legítimo criado via wp-admin.
const WINUP_GUARD_GRACE_SECS: i64 = 900; // 15 min

async fn already_announced_by_guard(
    incidents: &Arc<Mutex<IncidentStore>>,
    site: &str,
    login: &str,
) -> bool {
    let now = chrono::Utc::now().timestamp();
    let store = incidents.lock().await;
    // Olha incidentes recentes do source winup-guard com mesma (site, login)
    for inc in store.recent(200) {
        if !inc.source.contains("winup-guard") && !inc.source.contains("winup_guard") {
            continue;
        }
        if !inc.event_type.contains("admin_created") {
            continue;
        }
        let ts = chrono::DateTime::parse_from_rfc3339(&inc.timestamp)
            .map(|d| d.timestamp())
            .unwrap_or(0);
        if now - ts > WINUP_GUARD_GRACE_SECS {
            continue;
        }
        if let Some(details) = &inc.details {
            // Busca substring exata pra evitar match parcial entre logins similares
            let needle_login = format!("\"user_login\":\"{}\"", login);
            let needle_site = format!("\"site\":\"{}\"", site);
            if details.contains(&needle_login) && details.contains(&needle_site) {
                return true;
            }
        }
    }
    false
}

async fn push_incident(
    incidents: &Arc<Mutex<IncidentStore>>,
    ntfy: &Arc<NtfyClient>,
    server_id: &str,
    site: &str,
    admin_line: &str,
) {
    let parts: Vec<&str> = admin_line.splitn(3, '|').collect();
    let uid = parts.first().copied().unwrap_or("?");
    let login = parts.get(1).copied().unwrap_or("?");
    let email = parts.get(2).copied().unwrap_or("?");

    // Skip se o winup-security-guard já anunciou esse mesmo (site,login) recentemente
    // (admin criado pela equipe via wp-admin → hook PHP disparou webhook → não é backdoor)
    if already_announced_by_guard(incidents, site, login).await {
        info!("wp-admin-audit: '{}' em '{}' ja anunciado pelo winup-guard, skip", login, site);
        return;
    }

    let details = format!(
        "{{\"site\":\"{site}\",\"user_id\":{uid},\"user_login\":\"{login}\",\"user_email\":\"{email}\",\"detection\":\"sql_audit\",\"note\":\"Admin presente na DB que NAO estava no baseline anterior E NAO foi anunciado pelo winup-security-guard. Possivel insert direto via backdoor (bypassa wp_insert_user hooks).\"}}"
    );
    let incident = SecurityIncident {
        id: format!("wpaudit-{}-{}-{}", server_id, site, uid),
        timestamp: chrono::Utc::now().to_rfc3339(),
        source: "wp-admin-audit".to_string(),
        severity: "CRITICAL".to_string(),
        event_type: "admin_appeared_sql".to_string(),
        attacker_ip: "0.0.0.0".to_string(),
        country: None,
        city: None,
        isp: None,
        details: Some(details),
        from_webhook: false,
    };
    {
        let mut store = incidents.lock().await;
        store.push(incident);
    }
    let title = format!("🚨 ADMIN NOVO via SQL — {}", site);
    let body = format!(
        "Site: {site}\nUser ID: {uid}\nLogin: {login}\nEmail: {email}\n\nDetectado por audit SQL-level (NAO passou pelos hooks do WP — provavel backdoor)."
    );
    let _ = ntfy.send_alert(&title, &body, 5, "rotating_light,no_entry,bug").await;
    warn!("🚨 wp-admin-audit: novo admin '{}' em '{}'", login, site);
    let _ = HashMap::<(), ()>::new(); // suppress unused warning if any
}
