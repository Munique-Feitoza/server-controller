pub mod incidents;
pub mod intel;

use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::net::IpAddr;
use std::time::Instant;

/// Limiar de tentativas falhas antes de aplicar contra-medidas.
/// Apenas honeypot paths contam — erros normais de auth NAO contam.
const BAN_THRESHOLD: u32 = 5;

/// Tempo (em segundos) para resetar o contador de um IP (1 hora)
const WINDOW_SECS: u64 = 3600;

/// IPs que NUNCA serao banidos (localhost, tuneis SSH, redes internas).
/// Erros de auth desses IPs sao logados mas nunca disparam contra-medidas.
const SAFE_IPS: &[&str] = &["127.0.0.1", "::1", "localhost"];

/// Prefixos de rede interna que nunca serao banidos
const SAFE_PREFIXES: &[&str] = &[
    "10.",      // rede privada classe A
    "172.16.",  // rede privada classe B
    "172.17.",  // docker bridge
    "192.168.", // rede privada classe C
];

/// Rastreador de ameacas por IP.
/// IMPORTANTE: so conta como ameaca acesso a honeypot paths.
/// Erros normais de JWT (token expirado, header faltando) NAO contam
/// para o ban — apenas retornam 401 normalmente.
#[derive(Debug)]
pub struct ThreatTracker {
    attempts: HashMap<IpAddr, IpRecord>,
    banned: Vec<BanRecord>,
}

#[derive(Debug)]
struct IpRecord {
    count: u32,
    first_seen: Instant,
    last_seen: Instant,
    paths_acessados: Vec<String>,
}

/// Registro de banimento para auditoria
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BanRecord {
    pub ip: String,
    pub reason: String,
    pub attempts: u32,
    pub timestamp: String,
    pub paths: Vec<String>,
}

/// Resultado da avaliacao de uma tentativa suspeita
#[derive(Debug, PartialEq)]
pub enum ThreatAction {
    /// Apenas registrar, ainda nao atingiu o limiar
    Track,
    /// Servir zip bomb + banir (atingiu o limiar)
    NeutralizarEBanir,
    /// Ja esta banido, rejeitar silenciosamente
    JaBanido,
    /// IP seguro — nunca sera banido (localhost, rede interna)
    IpSeguro,
}

impl Default for ThreatTracker {
    fn default() -> Self {
        Self::new()
    }
}

impl ThreatTracker {
    pub fn new() -> Self {
        Self {
            attempts: HashMap::new(),
            banned: Vec::new(),
        }
    }

    /// Verifica se um IP eh seguro (localhost, rede interna, docker).
    /// IPs seguros NUNCA sao banidos — sao os proprios devs acessando via SSH tunnel.
    fn ip_seguro(ip: &IpAddr) -> bool {
        let ip_str = ip.to_string();
        if SAFE_IPS.contains(&ip_str.as_str()) {
            return true;
        }
        SAFE_PREFIXES
            .iter()
            .any(|prefix| ip_str.starts_with(prefix))
    }

    /// Registra acesso a um honeypot path e retorna a acao a ser tomada.
    /// So deve ser chamada para honeypot paths — NAO para erros de JWT.
    pub fn registrar_honeypot(&mut self, ip: IpAddr, path: &str, user_agent: &str) -> ThreatAction {
        // Nunca bane IPs internos/localhost
        if Self::ip_seguro(&ip) {
            tracing::info!(
                "🔒 IP seguro {} acessou honeypot {} — ignorado (dev/tunel SSH)",
                ip,
                path
            );
            return ThreatAction::IpSeguro;
        }

        self.limpar_expirados();

        if self.esta_banido(&ip) {
            return ThreatAction::JaBanido;
        }

        let now = Instant::now();
        let record = self.attempts.entry(ip).or_insert(IpRecord {
            count: 0,
            first_seen: now,
            last_seen: now,
            paths_acessados: Vec::new(),
        });

        record.count += 1;
        record.last_seen = now;
        if record.paths_acessados.len() < 10 {
            record.paths_acessados.push(path.to_string());
        }

        tracing::warn!(
            "🎯 Honeypot hit #{} do IP {} no path {}",
            record.count,
            ip,
            path
        );

        if record.count >= BAN_THRESHOLD {
            let paths = record.paths_acessados.clone();
            let ua = user_agent.to_string();

            self.banned.push(BanRecord {
                ip: ip.to_string(),
                reason: format!(
                    "{} acessos a honeypot paths em {}s",
                    record.count,
                    record.first_seen.elapsed().as_secs()
                ),
                attempts: record.count,
                timestamp: chrono::Utc::now().to_rfc3339(),
                paths: paths.clone(),
            });

            self.attempts.remove(&ip);

            // Coleta intel + bane em background
            let ip_str = ip.to_string();
            let paths_clone = paths;
            let ua_clone = ua;
            tokio::spawn(async move {
                // Coleta dados do atacante antes de banir
                let intel = intel::coletar_intel(&ip_str, &ua_clone, &paths_clone).await;

                if intel.likely_human {
                    tracing::error!(
                        "🧑 HUMANO DETECTADO tentando invadir: {} ({}, {}) — ISP: {} — {}",
                        intel.ip,
                        intel.city,
                        intel.country,
                        intel.isp,
                        intel.classification_reason
                    );
                } else {
                    tracing::warn!(
                        "🤖 BOT detectado e neutralizado: {} ({}, {}) — ISP: {} — {}",
                        intel.ip,
                        intel.city,
                        intel.country,
                        intel.isp,
                        intel.classification_reason
                    );
                }

                banir_ip_iptables(&ip_str).await;
            });

            ThreatAction::NeutralizarEBanir
        } else {
            ThreatAction::Track
        }
    }

    /// Verifica se um IP esta na lista de banidos
    pub fn esta_banido(&self, ip: &IpAddr) -> bool {
        // IPs seguros nunca estao banidos
        if Self::ip_seguro(ip) {
            return false;
        }
        self.banned.iter().any(|b| b.ip == ip.to_string())
    }

    /// Retorna o historico de banimentos
    pub fn banidos(&self) -> &[BanRecord] {
        &self.banned
    }

    /// Remove entradas com janela expirada
    fn limpar_expirados(&mut self) {
        let now = Instant::now();
        self.attempts
            .retain(|_, record| now.duration_since(record.first_seen).as_secs() < WINDOW_SECS);
    }
}

/// Gera um zip bomb em memoria (~50KB comprimido → 50MB ao descomprimir)
pub fn gerar_zip_bomb() -> Vec<u8> {
    use std::io::Write;
    let mut encoder = flate2::write::GzEncoder::new(Vec::new(), flate2::Compression::best());
    let zeros = vec![0u8; 50_000_000];
    let _ = encoder.write_all(&zeros);
    encoder.finish().unwrap_or_default()
}

/// Bloqueia o IP via iptables (requer root)
async fn banir_ip_iptables(ip: &str) {
    tracing::error!("🚫 AUTO-BAN: Bloqueando IP {} via iptables", ip);

    let result = tokio::process::Command::new("iptables")
        .args(["-I", "INPUT", "-s", ip, "-j", "DROP"])
        .status()
        .await;

    match result {
        Ok(status) if status.success() => {
            tracing::info!("✅ IP {} bloqueado com sucesso via iptables", ip);
        }
        Ok(status) => {
            tracing::error!(
                "❌ iptables retornou codigo {}: IP {} nao bloqueado",
                status,
                ip
            );
        }
        Err(e) => {
            tracing::error!("❌ Falha ao executar iptables para {}: {}", ip, e);
        }
    }
}

/// Paths conhecidos de scanners/bots que nunca deveriam ser acessados.
/// Qualquer acesso a estes paths eh um scanner ou bot tentando explorar.
pub const HONEYPOT_PATHS: &[&str] = &[
    "/wp-admin",
    "/wp-login.php",
    "/wp-content",
    "/.env",
    "/.git",
    "/.ssh",
    "/.aws",
    "/phpmyadmin",
    "/pma",
    "/adminer",
    "/admin",
    "/administrator",
    "/cpanel",
    "/shell",
    "/cmd",
    "/webshell",
    "/c99",
    "/id_rsa",
    "/config.php",
    "/wp-config.php",
    "/.htaccess",
    "/.htpasswd",
    "/actuator",
    "/swagger",
    "/graphql",
    "/xmlrpc.php",
    "/vendor",
    "/composer.json",
    "/package.json",
    "/debug",
    "/trace",
    "/console",
    "/../",
    "/etc/passwd",
    "/proc/self",
];

/// Verifica se o path eh um honeypot conhecido
pub fn is_honeypot_path(path: &str) -> bool {
    let path_lower = path.to_lowercase();
    HONEYPOT_PATHS.iter().any(|hp| path_lower.contains(hp))
}
