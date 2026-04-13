use serde::{Deserialize, Serialize};
use std::process::Command;

/// Status de um certificado SSL de um dominio
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SslCertStatus {
    pub domain: String,
    pub valid: bool,
    pub days_remaining: i64,
    pub issuer: String,
    pub subject: String,
    pub expiry_date: String,
    pub status: String, // "ok", "expiring", "expired", "error", "no_cert"
}

/// Resultado da verificacao de SSL de todos os sites do servidor
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SslCheckResult {
    pub total_domains: usize,
    pub ok_count: usize,
    pub expiring_count: usize,
    pub expired_count: usize,
    pub error_count: usize,
    pub certs: Vec<SslCertStatus>,
}

/// Coleta todos os dominios configurados no nginx e verifica o SSL de cada um.
/// Respeita a env var SSL_SKIP_DOMAINS (lista separada por virgula) para nao
/// verificar dominios internos que nao precisam de certificado publico.
pub fn check_all_ssl() -> SslCheckResult {
    let skip = skip_domains();
    let domains: Vec<String> = list_nginx_domains()
        .into_iter()
        .filter(|d| !skip.contains(&d.to_lowercase()))
        .collect();
    let mut certs = Vec::new();

    for domain in &domains {
        certs.push(check_domain_ssl(domain));
    }

    let ok_count = certs.iter().filter(|c| c.status == "ok").count();
    let expiring_count = certs.iter().filter(|c| c.status == "expiring").count();
    let expired_count = certs.iter().filter(|c| c.status == "expired").count();
    let error_count = certs.iter().filter(|c| c.status == "error" || c.status == "no_cert").count();

    // Ordena: problemas primeiro
    certs.sort_by(|a, b| a.days_remaining.cmp(&b.days_remaining));

    SslCheckResult {
        total_domains: domains.len(),
        ok_count,
        expiring_count,
        expired_count,
        error_count,
        certs,
    }
}

/// Le a lista de dominios a nao verificar da env var SSL_SKIP_DOMAINS
fn skip_domains() -> Vec<String> {
    std::env::var("SSL_SKIP_DOMAINS")
        .unwrap_or_default()
        .split(',')
        .map(|s| s.trim().to_lowercase())
        .filter(|s| !s.is_empty())
        .collect()
}

/// Lista todos os dominios configurados no nginx
fn list_nginx_domains() -> Vec<String> {
    // Tenta nginx (Hosting) primeiro, depois nginx padrao
    let output = Command::new("sh")
        .args(["-c", "grep -rh 'server_name' /etc/nginx/conf.d/ /etc/nginx/conf.d/ /etc/nginx/sites-enabled/ 2>/dev/null | sed 's/server_name//g;s/;//g' | tr ' ' '\\n' | grep '\\.' | sort -u"])
        .output();

    match output {
        Ok(o) if o.status.success() => {
            String::from_utf8_lossy(&o.stdout)
                .lines()
                .filter(|l| !l.is_empty() && !l.contains("_") && l.contains('.'))
                .map(|l| l.trim().to_string())
                .collect()
        }
        _ => Vec::new(),
    }
}

/// Verifica o certificado SSL de um dominio especifico
fn check_domain_ssl(domain: &str) -> SslCertStatus {
    let output = Command::new("sh")
        .args(["-c", &format!(
            "echo | timeout 5 openssl s_client -connect {}:443 -servername {} 2>/dev/null | openssl x509 -noout -subject -issuer -enddate 2>/dev/null",
            domain, domain
        )])
        .output();

    let stdout = match output {
        Ok(o) => String::from_utf8_lossy(&o.stdout).to_string(),
        Err(_) => {
            return SslCertStatus {
                domain: domain.to_string(),
                valid: false,
                days_remaining: -1,
                issuer: String::new(),
                subject: String::new(),
                expiry_date: String::new(),
                status: "error".to_string(),
            };
        }
    };

    if stdout.is_empty() {
        return SslCertStatus {
            domain: domain.to_string(),
            valid: false,
            days_remaining: -1,
            issuer: String::new(),
            subject: String::new(),
            expiry_date: String::new(),
            status: "no_cert".to_string(),
        };
    }

    let mut subject = String::new();
    let mut issuer = String::new();
    let mut expiry_date = String::new();

    for line in stdout.lines() {
        if let Some(s) = line.strip_prefix("subject=") {
            subject = s.trim().to_string();
        } else if let Some(s) = line.strip_prefix("issuer=") {
            issuer = s.trim().to_string();
        } else if let Some(s) = line.strip_prefix("notAfter=") {
            expiry_date = s.trim().to_string();
        }
    }

    // Calcula dias restantes
    let days_remaining = parse_expiry_days(&expiry_date);

    // Verifica se o certificado eh do dominio certo
    // Aceita: cert exato, wildcard, ou cert do dominio pai (subdominio de idioma)
    // Ex: fr.zenlupe.com aceita cert de zenlupe.com ou *.zenlupe.com
    let parent_domain = domain.splitn(2, '.').nth(1).unwrap_or("");
    let cert_matches = subject.contains(domain)
        || subject.contains(&format!("*.{}", parent_domain))
        || subject.contains(parent_domain);

    let status = if !cert_matches {
        "wrong_cert".to_string()
    } else if days_remaining < 0 {
        "expired".to_string()
    } else if days_remaining <= 7 {
        "expiring".to_string()
    } else {
        "ok".to_string()
    };

    SslCertStatus {
        domain: domain.to_string(),
        valid: cert_matches && days_remaining > 0,
        days_remaining,
        issuer,
        subject,
        expiry_date,
        status,
    }
}

/// Converte a data de expiracao do openssl (ex: "Apr 15 12:00:00 2026 GMT") em dias restantes
fn parse_expiry_days(date_str: &str) -> i64 {
    if date_str.is_empty() {
        return -1;
    }

    // Usa o comando date para converter
    let output = Command::new("date")
        .args(["-d", date_str, "+%s"])
        .output();

    match output {
        Ok(o) if o.status.success() => {
            let expiry_ts: i64 = String::from_utf8_lossy(&o.stdout).trim().parse().unwrap_or(0);
            let now_ts = std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap_or_default()
                .as_secs() as i64;
            (expiry_ts - now_ts) / 86400
        }
        _ => -1,
    }
}
