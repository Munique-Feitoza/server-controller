// agent/src/security/wp_admin_audit.rs
//! Audit SQL-level dos admins WP em todos os webapps locais.
//!
//! Roda a cada 5 min. Pra cada docroot com `wp-config.php` casado por
//! `WP_SITES_GLOB` (default `/home/runcloud/webapps/*`):
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

const BASELINE_DIR: &str = "/var/lib/pocket-noc-agent/admin-baseline";
const DEFAULT_CYCLE_SECS: u64 = 300; // 5 min

/// Cada invocação do wp-cli custa ~1,9s (boot do PHP + WordPress + DB). Num host
/// com muitos sites o ciclo inteiro pode passar do intervalo — daí ser ajustável.
fn cycle_secs() -> u64 {
    std::env::var("WP_AUDIT_CYCLE_SECS")
        .ok()
        .and_then(|v| v.trim().parse::<u64>().ok())
        .filter(|v| *v >= 60)
        .unwrap_or(DEFAULT_CYCLE_SECS)
}

/// Padrão de descoberta dos docroots, com UM `*`. O que o `*` casar vira o nome
/// do site — dá nome útil nos dois layouts sem caso especial:
///   RunCloud   `/home/runcloud/webapps/*` → `*` = nome do webapp
///   CyberPanel `/home/*/public_html`      → `*` = domínio
/// Sem isso, o CyberPanel colidiria todos os baselines em `public_html`.
const DEFAULT_SITES_GLOB: &str = "/home/runcloud/webapps/*";

/// Candidatos de wp-cli, na ordem. RunCloud instala em `/usr/local/bin`,
/// CyberPanel/AlmaLinux em `/usr/bin`.
const WP_CLI_CANDIDATES: [&str; 2] = ["/usr/local/bin/wp", "/usr/bin/wp"];

/// Valor de `WP_RUN_AS_USER` que dispara resolução do dono do docroot em vez de
/// usuário fixo. No CyberPanel cada site tem seu próprio usuário.
const RUN_AS_OWNER: &str = "owner";

fn sites_glob() -> String {
    std::env::var("WP_SITES_GLOB")
        .ok()
        .filter(|s| !s.trim().is_empty())
        .unwrap_or_else(|| DEFAULT_SITES_GLOB.to_string())
}

/// Usuário para o `sudo -u`. Default `runcloud` preserva o comportamento
/// histórico dos hosts RunCloud.
fn run_as_user() -> String {
    std::env::var("WP_RUN_AS_USER")
        .ok()
        .filter(|s| !s.trim().is_empty())
        .unwrap_or_else(|| "runcloud".to_string())
}

fn wp_cli() -> String {
    if let Ok(p) = std::env::var("WP_CLI_PATH") {
        if !p.trim().is_empty() {
            return p;
        }
    }
    for c in WP_CLI_CANDIDATES {
        if Path::new(c).is_file() {
            return c.to_string();
        }
    }
    WP_CLI_CANDIDATES[0].to_string()
}

/// Resolve o dono de `path` lendo `/etc/passwd` (sem depender de libc).
fn owner_of(path: &Path) -> Option<String> {
    use std::os::unix::fs::MetadataExt;
    let uid = std::fs::metadata(path).ok()?.uid();
    let passwd = std::fs::read_to_string("/etc/passwd").ok()?;
    for line in passwd.lines() {
        let mut f = line.split(':');
        let name = f.next()?;
        let _pw = f.next();
        let u: u32 = match f.next().and_then(|v| v.parse().ok()) {
            Some(v) => v,
            None => continue,
        };
        if u == uid {
            return Some(name.to_string());
        }
    }
    None
}

/// Só aceita nome de usuário sensato antes de passar pro `sudo -u`. Os args vão
/// separados pro `Command` (não há shell), mas um nome estranho indica ambiente
/// inesperado — melhor pular o site do que rodar como quem não se espera.
fn valid_username(u: &str) -> bool {
    !u.is_empty()
        && u.len() <= 32
        && u.chars().all(|c| c.is_ascii_alphanumeric() || c == '_' || c == '-' || c == '.')
}

/// Expande o glob de um `*` em `(docroot, nome_do_site)`, mantendo só os que têm
/// `wp-config.php`.
fn discover_sites(glob: &str) -> std::io::Result<Vec<(PathBuf, String)>> {
    let (prefix, suffix) = match glob.split_once('*') {
        Some(p) => p,
        None => {
            warn!("WP_SITES_GLOB '{}' sem '*' — nenhum site descoberto", glob);
            return Ok(Vec::new());
        }
    };
    let mut out = Vec::new();
    for entry in std::fs::read_dir(prefix)?.flatten() {
        let name = match entry.file_name().into_string() {
            Ok(n) => n,
            Err(_) => continue,
        };
        // Evita que um nome com '/' ou '..' escape do prefixo
        if name.contains('/') || name == "." || name == ".." {
            continue;
        }
        let docroot = PathBuf::from(format!("{prefix}{name}{suffix}"));
        if !docroot.join("wp-config.php").is_file() {
            continue;
        }
        out.push((docroot, name));
    }
    Ok(out)
}

/// Spawn-friendly loop. Falha silenciosamente entre ciclos.
pub async fn run(incidents: Arc<Mutex<IncidentStore>>, ntfy: Arc<NtfyClient>, server_id: Arc<String>) {
    // Garante diretório do baseline (best-effort)
    let _ = std::fs::create_dir_all(BASELINE_DIR);

    let cycle = cycle_secs();
    info!("🛡 wp-admin-audit ativo (ciclo {}s, glob '{}')", cycle, sites_glob());

    // O prefix da tabela vem do wp-config e praticamente nunca muda. Guardar em
    // memória corta metade das invocações do wp-cli — o item mais caro do ciclo.
    let mut prefix_cache: HashMap<String, String> = HashMap::new();

    loop {
        if let Err(e) = scan_cycle(&incidents, &ntfy, &server_id, &mut prefix_cache).await {
            warn!("wp-admin-audit cycle failed: {}", e);
        }
        tokio::time::sleep(Duration::from_secs(cycle)).await;
    }
}

async fn scan_cycle(
    incidents: &Arc<Mutex<IncidentStore>>,
    ntfy: &Arc<NtfyClient>,
    server_id: &str,
    prefix_cache: &mut HashMap<String, String>,
) -> std::io::Result<()> {
    let configured_user = run_as_user();
    let wp = wp_cli();
    for (path, site) in discover_sites(&sites_glob())? {
        // No CyberPanel cada docroot pertence a um usuário diferente; no RunCloud
        // é sempre o mesmo. `owner` cobre o primeiro caso sem hardcode.
        let user = if configured_user == RUN_AS_OWNER {
            match owner_of(&path) {
                Some(u) => u,
                None => {
                    warn!("wp-admin-audit: dono de '{}' nao resolvido, pulando", path.display());
                    continue;
                }
            }
        } else {
            configured_user.clone()
        };
        if !valid_username(&user) {
            warn!("wp-admin-audit: usuario '{}' invalido para '{}', pulando", user, site);
            continue;
        }
        // `wp` é um processo externo de ~1,9s. Rodar isso direto na task async
        // bloquearia uma thread do runtime durante o ciclo inteiro (minutos, num
        // host com muitos sites), travando a API e o watchdog junto.
        let cached_prefix = prefix_cache.get(&site).cloned();
        let (p, w, u) = (path.clone(), wp.clone(), user.clone());
        let scanned = tokio::task::spawn_blocking(move || list_admins(&p, &w, &u, cached_prefix))
            .await
            .ok()
            .flatten();
        let (current, prefix) = match scanned {
            Some((v, pfx)) if !v.is_empty() => (v, pfx),
            _ => continue,
        };
        prefix_cache.insert(site.clone(), prefix);
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

/// Retorna `(linhas no formato `id|user_login|user_email` ordenadas por ID, prefix)`.
/// Roda `wp db query` como `user`. O prefix vem do cache quando disponível; senão
/// é lido da própria CLI (respeita prefix customizado).
fn list_admins(
    path: &Path,
    wp: &str,
    user: &str,
    cached_prefix: Option<String>,
) -> Option<(Vec<String>, String)> {
    let prefix = match cached_prefix {
        Some(p) => p,
        None => run_wp(path, wp, user, &["db", "prefix"])?.trim().to_string(),
    };
    if prefix.is_empty() || !prefix.chars().all(|c| c.is_ascii_alphanumeric() || c == '_') {
        return None;
    }
    let q = format!(
        "SELECT CONCAT(u.ID,'|',u.user_login,'|',u.user_email) \
         FROM {prefix}users u JOIN {prefix}usermeta m ON m.user_id=u.ID \
         WHERE m.meta_key='{prefix}capabilities' AND m.meta_value LIKE '%administrator%' \
         ORDER BY u.ID"
    );
    let out = run_wp(path, wp, user, &["db", "query", &q])?;
    // O header do mysql é a própria expressão `CONCAT(u.ID,'|',...)`, que CONTÉM
    // '|' — filtrar por "tem pipe" deixaria ele passar como se fosse um admin.
    // Toda linha real começa com o ID numérico, então é esse o critério.
    let mut admins: Vec<String> = out
        .lines()
        .map(|l| l.trim().to_string())
        .filter(|l| {
            l.split('|')
                .next()
                .map(|id| !id.is_empty() && id.chars().all(|c| c.is_ascii_digit()))
                .unwrap_or(false)
        })
        .collect();
    admins.sort();
    admins.dedup();
    Some((admins, prefix))
}

fn run_wp(path: &Path, wp: &str, user: &str, args: &[&str]) -> Option<String> {
    // O agente já roda como root. Se o docroot for de root, o `sudo -u root` seria
    // redundante e o wp-cli recusa rodar como root sem `--allow-root`.
    let mut cmd = if user == "root" {
        let mut c = Command::new(wp);
        c.arg("--allow-root");
        c
    } else {
        let mut c = Command::new("/usr/bin/sudo");
        c.arg("-u").arg(user).arg(wp);
        c
    };
    cmd.arg(format!("--path={}", path.display()));
    for a in args {
        cmd.arg(a);
    }
    let out = cmd.output().ok()?;
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
