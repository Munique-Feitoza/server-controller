use serde::{Deserialize, Serialize};

/// Informacoes coletadas sobre o atacante via ip-api.com
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct AttackerIntel {
    pub ip: String,
    pub country: String,
    pub country_code: String,
    pub city: String,
    pub isp: String,
    pub org: String,
    pub is_proxy: bool,
    pub is_hosting: bool,
    pub is_mobile: bool,
    pub reverse_dns: String,
    pub user_agent: String,
    pub paths_attempted: Vec<String>,
    pub collected_at: String,
    /// true se parece ser um humano (nao bot)
    pub likely_human: bool,
    /// Razao pela qual classificamos como humano ou bot
    pub classification_reason: String,
}

/// Resultado da API ip-api.com
#[derive(Deserialize)]
#[allow(dead_code)]
struct IpApiResponse {
    status: String,
    country: Option<String>,
    #[serde(rename = "countryCode")]
    country_code: Option<String>,
    city: Option<String>,
    isp: Option<String>,
    org: Option<String>,
    #[serde(rename = "as")]
    as_info: Option<String>,
    proxy: Option<bool>,
    hosting: Option<bool>,
    mobile: Option<bool>,
    reverse: Option<String>,
}

/// Coleta inteligencia sobre um IP usando ip-api.com.
/// Funciona sem chave de API (limite de 45 req/min).
pub async fn coletar_intel(ip: &str, user_agent: &str, paths: &[String]) -> AttackerIntel {
    // IPs locais — nao consulta API
    if ip == "127.0.0.1" || ip == "::1" || ip.starts_with("192.168.") || ip.starts_with("10.") {
        return AttackerIntel {
            ip: ip.to_string(),
            country: "Local".to_string(),
            city: "Internal".to_string(),
            isp: "Rede Privada".to_string(),
            classification_reason: "IP de rede interna".to_string(),
            collected_at: chrono::Utc::now().to_rfc3339(),
            user_agent: user_agent.to_string(),
            paths_attempted: paths.to_vec(),
            ..Default::default()
        };
    }

    let api_url = format!(
        "http://ip-api.com/json/{}?fields=status,country,countryCode,city,isp,org,as,proxy,hosting,mobile,reverse",
        ip
    );

    let result = match reqwest::Client::new()
        .get(&api_url)
        .timeout(std::time::Duration::from_secs(3))
        .send()
        .await
    {
        Ok(resp) => resp.json::<IpApiResponse>().await.ok(),
        Err(_) => None,
    };

    let (country, country_code, city, isp, org, is_proxy, is_hosting, is_mobile, reverse) =
        match result {
            Some(data) if data.status == "success" => (
                data.country.unwrap_or_default(),
                data.country_code.unwrap_or_default(),
                data.city.unwrap_or_default(),
                data.isp.unwrap_or_default(),
                data.org.unwrap_or_default(),
                data.proxy.unwrap_or(false),
                data.hosting.unwrap_or(false),
                data.mobile.unwrap_or(false),
                data.reverse.unwrap_or_default(),
            ),
            _ => (
                "Desconhecido".to_string(),
                "??".to_string(),
                "Desconhecido".to_string(),
                "Desconhecido".to_string(),
                String::new(),
                false,
                false,
                false,
                String::new(),
            ),
        };

    // Classificacao: bot ou humano?
    let (likely_human, reason) = classificar(user_agent, &isp, is_proxy, is_hosting, paths);

    let intel = AttackerIntel {
        ip: ip.to_string(),
        country,
        country_code,
        city,
        isp,
        org,
        is_proxy,
        is_hosting,
        is_mobile,
        reverse_dns: reverse,
        user_agent: user_agent.to_string(),
        paths_attempted: paths.to_vec(),
        collected_at: chrono::Utc::now().to_rfc3339(),
        likely_human,
        classification_reason: reason,
    };

    tracing::info!(
        "🔍 Intel coletada: {} ({}, {}) — ISP: {} — {} — {}",
        intel.ip,
        intel.city,
        intel.country,
        intel.isp,
        if intel.likely_human {
            "HUMANO PROVAVEL"
        } else {
            "BOT PROVAVEL"
        },
        intel.classification_reason
    );

    intel
}

/// Classifica se o atacante eh provavelmente humano ou bot.
/// Humanos merecem mais atencao; bots sao descartaveis.
fn classificar(
    user_agent: &str,
    isp: &str,
    is_proxy: bool,
    is_hosting: bool,
    paths: &[String],
) -> (bool, String) {
    let ua_lower = user_agent.to_lowercase();

    // Bots obvios — User-Agent de scanner conhecido
    let bot_signatures = [
        "masscan",
        "nmap",
        "zgrab",
        "censys",
        "shodan",
        "nuclei",
        "nikto",
        "sqlmap",
        "dirbuster",
        "gobuster",
        "wpscan",
        "curl/",
        "wget/",
        "python-requests",
        "go-http-client",
        "httpclient",
        "libwww",
        "scrapy",
        "bot",
        "crawler",
        "spider",
    ];
    for sig in bot_signatures {
        if ua_lower.contains(sig) {
            return (
                false,
                format!("User-Agent contem '{}' — scanner automatizado", sig),
            );
        }
    }

    // Hosting/datacenter = provavel bot/VPS de ataque
    if is_hosting && !is_proxy {
        return (
            false,
            format!(
                "IP de datacenter/hosting ({}) sem proxy — provavel servidor de ataque",
                isp
            ),
        );
    }

    // Se acessou mais de 3 honeypots diferentes = scan automatizado
    if paths.len() > 3 {
        let unique: std::collections::HashSet<&String> = paths.iter().collect();
        if unique.len() > 3 {
            return (
                false,
                format!("{} paths unicos tentados — scan automatizado", unique.len()),
            );
        }
    }

    // User-Agent de navegador real = provavel humano
    let browser_signatures = [
        "mozilla/", "chrome/", "safari/", "firefox/", "edge/", "opera/",
    ];
    let has_browser_ua = browser_signatures.iter().any(|s| ua_lower.contains(s));

    if has_browser_ua && !is_hosting {
        return (
            true,
            format!("User-Agent de navegador real + ISP residencial ({})", isp),
        );
    }

    if has_browser_ua && is_proxy {
        return (
            true,
            "User-Agent de navegador + VPN/proxy — possivelmente humano curioso".to_string(),
        );
    }

    // Default: sem certeza, mas User-Agent vazio ou generico = bot
    if user_agent.is_empty() || user_agent == "unknown" || ua_lower.len() < 10 {
        return (
            false,
            "User-Agent ausente ou muito curto — bot provavel".to_string(),
        );
    }

    (
        false,
        "Nao classificado com certeza — tratando como bot por precaucao".to_string(),
    )
}
