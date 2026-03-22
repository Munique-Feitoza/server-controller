use serde::{Deserialize, Serialize};
use std::time::{Duration, Instant};

/// Status de saúde retornado por qualquer probe
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "PascalCase")]
pub enum ProbeStatus {
    /// Serviço respondendo dentro do timeout
    Healthy,
    /// Serviço respondendo mas com latência alta ou código HTTP != 2xx
    Degraded,
    /// Serviço não responde ou está inativo
    Down,
}

impl std::fmt::Display for ProbeStatus {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ProbeStatus::Healthy   => write!(f, "Healthy"),
            ProbeStatus::Degraded  => write!(f, "Degraded"),
            ProbeStatus::Down      => write!(f, "Down"),
        }
    }
}

/// Resultado estruturado de uma probe — contém status, latência e mensagem legível
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ProbeResult {
    pub status:      ProbeStatus,
    pub latency_ms:  Option<u64>,
    pub message:     String,
    pub service:     String,
}

impl ProbeResult {
    pub fn is_healthy(&self) -> bool {
        self.status == ProbeStatus::Healthy
    }
    pub fn needs_remediation(&self) -> bool {
        self.status == ProbeStatus::Down
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HTTP PROBE
// Testa um endpoint HTTP local (Nginx, WordPress, ERP, Next.js, etc.)
// Padrão Strategy: cada Probe implementa a mesma interface de "verificação".
// ─────────────────────────────────────────────────────────────────────────────

/// Configuração de uma probe HTTP
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HttpProbeConfig {
    /// Nome lógico do serviço (ex: "erp-python", "nextjs-frontend", "wordpress-site1")
    pub service:             String,
    /// URL para testar (ex: "http://127.0.0.1:8000/health")
    pub url:                 String,
    /// Timeout em segundos (padrão: 5)
    pub timeout_secs:        u64,
    /// Código HTTP esperado (padrão: 200)
    pub expected_status:     u16,
    /// Latência máxima tolerada em ms antes de marcar como Degraded (padrão: 2000)
    pub degraded_latency_ms: u64,
}

impl Default for HttpProbeConfig {
    fn default() -> Self {
        Self {
            service:             "http-service".to_string(),
            url:                 "http://127.0.0.1".to_string(),
            timeout_secs:        5,
            expected_status:     200,
            degraded_latency_ms: 2000,
        }
    }
}

/// Executa uma probe HTTP assíncrona usando reqwest
///
/// # Design Pattern: Command
/// A função recebe a configuração e retorna o resultado sem manter estado interno,
/// tornando-a facilmente testável (basta mockar o HttpProbeConfig).
pub async fn run_http_probe(config: &HttpProbeConfig) -> ProbeResult {
    let client = match reqwest::Client::builder()
        .timeout(Duration::from_secs(config.timeout_secs))
        .build()
    {
        Ok(c) => c,
        Err(e) => {
            return ProbeResult {
                status:     ProbeStatus::Down,
                latency_ms: None,
                message:    format!("Falha ao criar HTTP client: {e}"),
                service:    config.service.clone(),
            }
        }
    };

    let start = Instant::now();
    match client.get(&config.url).send().await {
        Ok(resp) => {
            let elapsed_ms = start.elapsed().as_millis() as u64;
            let http_status = resp.status().as_u16();

            // Verifica código HTTP esperado
            if http_status != config.expected_status {
                return ProbeResult {
                    status:     ProbeStatus::Down,
                    latency_ms: Some(elapsed_ms),
                    message:    format!(
                        "HTTP {} inesperado em {} (esperado: {})",
                        http_status, config.url, config.expected_status
                    ),
                    service: config.service.clone(),
                };
            }

            // Verifica latência (Degraded se acima do threshold)
            let status = if elapsed_ms > config.degraded_latency_ms {
                ProbeStatus::Degraded
            } else {
                ProbeStatus::Healthy
            };

            ProbeResult {
                message: format!(
                    "{} respondeu em {}ms (HTTP {})",
                    config.url, elapsed_ms, http_status
                ),
                status,
                latency_ms: Some(elapsed_ms),
                service: config.service.clone(),
            }
        }
        Err(e) => {
            let elapsed_ms = start.elapsed().as_millis() as u64;
            let msg = if e.is_timeout() {
                format!("Timeout após {}s em {}", config.timeout_secs, config.url)
            } else if e.is_connect() {
                format!("Conexão recusada em {} — serviço possivelmente down", config.url)
            } else {
                format!("Erro HTTP em {}: {e}", config.url)
            };

            ProbeResult {
                status:     ProbeStatus::Down,
                latency_ms: Some(elapsed_ms),
                message:    msg,
                service:    config.service.clone(),
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SERVICE PROBE (systemctl)
// Verifica se um daemon Linux está ativo via `systemctl is-active`
// Compatível com: nginx, nginx, mysql, postgresql, php-fpm, gunicorn, docker
// ─────────────────────────────────────────────────────────────────────────────

/// Configuração de uma probe de serviço systemd
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ServiceProbeConfig {
    /// Nome do daemon (ex: "nginx", "mysql", "postgresql", "php8.1-fpm")
    pub service_name: String,
}

/// Executa `systemctl is-active <service>` e interpreta o retorno
///
/// # Segurança
/// Usa `Command::new` com args separados — sem interpolação de string no shell,
/// prevenindo qualquer forma de command injection (Zero Trust nos nomes de serviço).
pub fn run_service_probe(config: &ServiceProbeConfig) -> ProbeResult {
    let service = &config.service_name;

    let output = std::process::Command::new("systemctl")
        .arg("is-active")
        .arg(service)
        .output();

    match output {
        Ok(out) => {
            let stdout = String::from_utf8_lossy(&out.stdout).trim().to_string();
            let status = match stdout.as_str() {
                "active"       => ProbeStatus::Healthy,
                "activating"   => ProbeStatus::Degraded,
                "deactivating" => ProbeStatus::Degraded,
                _              => ProbeStatus::Down,  // "inactive", "failed", "unknown"
            };
            let message = format!(
                "systemctl {service}: {stdout}"
            );
            ProbeResult { status, latency_ms: None, message, service: service.clone() }
        }
        Err(e) => ProbeResult {
            status:     ProbeStatus::Down,
            latency_ms: None,
            message:    format!("Falha ao executar systemctl is-active {service}: {e}"),
            service:    service.clone(),
        },
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TCP PROBE
// Testa conexão TCP pura — útil para MySQL (3306), PostgreSQL (5432), Redis (6379)
// sem precisar de credenciais de banco para confirmar que a porta está aberta.
// ─────────────────────────────────────────────────────────────────────────────

/// Configuração de uma probe TCP
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TcpProbeConfig {
    /// Nome lógico do serviço (ex: "mysql-db", "postgres-erp")
    pub service:      String,
    /// Host do servidor (ex: "127.0.0.1" ou "10.0.0.5")
    pub host:         String,
    /// Porta TCP (ex: 3306 para MySQL, 5432 para PostgreSQL)
    pub port:         u16,
    /// Timeout em segundos (padrão: 3)
    pub timeout_secs: u64,
}

/// Executa uma conexão TCP simples e mede latência
///
/// Essa abordagem é a mais leve possível: abre o socket e fecha imediatamente.
/// Não autentica, não envia dados — apenas confirma que o serviço está escutando.
pub fn run_tcp_probe(config: &TcpProbeConfig) -> ProbeResult {
    use std::net::{TcpStream, SocketAddr, ToSocketAddrs};

    let addr_str = format!("{}:{}", config.host, config.port);
    let service  = config.service.clone();
    let timeout  = Duration::from_secs(config.timeout_secs);

    let start = Instant::now();

    // Resolve o endereço (pode falhar se o host não existir)
    let addr: SocketAddr = match addr_str.to_socket_addrs()
        .ok()
        .and_then(|mut a| a.next())
    {
        Some(a) => a,
        None => {
            return ProbeResult {
                status:     ProbeStatus::Down,
                latency_ms: None,
                message:    format!("Não foi possível resolver o endereço {addr_str}"),
                service,
            }
        }
    };

    match TcpStream::connect_timeout(&addr, timeout) {
        Ok(_) => {
            let elapsed_ms = start.elapsed().as_millis() as u64;
            ProbeResult {
                status:     ProbeStatus::Healthy,
                latency_ms: Some(elapsed_ms),
                message:    format!("Porta TCP {addr_str} acessível ({}ms)", elapsed_ms),
                service,
            }
        }
        Err(e) => {
            let elapsed_ms = start.elapsed().as_millis() as u64;
            ProbeResult {
                status:     ProbeStatus::Down,
                latency_ms: Some(elapsed_ms),
                message:    format!("Porta TCP {addr_str} inacessível: {e}"),
                service,
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PERFIS POR SERVER_ROLE
// Constrói a lista de probes baseada no papel do servidor (lido do .env)
// ─────────────────────────────────────────────────────────────────────────────

/// Tipos de probe de forma unificada — enum para o WatchdogEngine iterar
#[derive(Debug, Clone)]
pub enum AnyProbeConfig {
    Http(HttpProbeConfig),
    Service(ServiceProbeConfig),
    Tcp(TcpProbeConfig),
}

/// Retorna os probes padrão para cada role de servidor.
/// Probes extras são configuráveis via env: `EXTRA_HTTP_PROBES`, `EXTRA_SERVICE_PROBES`.
pub fn default_probes_for_role(role: &str) -> Vec<AnyProbeConfig> {
    match role {
        "wordpress" => vec![
            AnyProbeConfig::Service(ServiceProbeConfig { service_name: "nginx".to_string() }),
            AnyProbeConfig::Service(ServiceProbeConfig { service_name: "apache2".to_string() }),
            AnyProbeConfig::Service(ServiceProbeConfig { service_name: "mariadb".to_string() }),
            AnyProbeConfig::Service(ServiceProbeConfig { service_name: "php81-fpm".to_string() }),
            AnyProbeConfig::Tcp(TcpProbeConfig {
                service:      "mariadb-tcp".to_string(),
                host:         "127.0.0.1".to_string(),
                port:         3306,
                timeout_secs: 3,
            }),
            AnyProbeConfig::Http(HttpProbeConfig {
                service:             "nginx-http-80".to_string(),
                url:                 "http://127.0.0.1".to_string(),
                timeout_secs:        5,
                expected_status:     200,
                degraded_latency_ms: 3000,
            }),
        ],

        "wordpress-python" => vec![
            AnyProbeConfig::Service(ServiceProbeConfig { service_name: "nginx".to_string() }),
            AnyProbeConfig::Service(ServiceProbeConfig { service_name: "apache2".to_string() }),
            AnyProbeConfig::Service(ServiceProbeConfig { service_name: "mariadb".to_string() }),
            AnyProbeConfig::Service(ServiceProbeConfig { service_name: "php81-fpm".to_string() }),
            AnyProbeConfig::Tcp(TcpProbeConfig {
                service:      "mariadb-tcp".to_string(),
                host:         "127.0.0.1".to_string(),
                port:         3306,
                timeout_secs: 3,
            }),
            AnyProbeConfig::Http(HttpProbeConfig {
                service:             "nginx-http-80".to_string(),
                url:                 "http://127.0.0.1".to_string(),
                timeout_secs:        5,
                expected_status:     200,
                degraded_latency_ms: 3000,
            }),
            AnyProbeConfig::Http(HttpProbeConfig {
                service:             "python-api".to_string(),
                url:                 "http://127.0.0.1:8000/health".to_string(),
                // Se a API não tiver /health, podemos testar só a raiz ou remover
                timeout_secs:        5,
                expected_status:     200,
                degraded_latency_ms: 2000,
            }),
        ],

        "erp" => vec![
            AnyProbeConfig::Service(ServiceProbeConfig { service_name: "gunicorn".to_string() }),
            AnyProbeConfig::Service(ServiceProbeConfig { service_name: "postgresql".to_string() }),
            AnyProbeConfig::Service(ServiceProbeConfig { service_name: "nginx".to_string() }),
            AnyProbeConfig::Tcp(TcpProbeConfig {
                service:      "postgresql-tcp".to_string(),
                host:         "127.0.0.1".to_string(),
                port:         5432,
                timeout_secs: 3,
            }),
            AnyProbeConfig::Http(HttpProbeConfig {
                service:             "erp-python-api".to_string(),
                url:                 "http://127.0.0.1:8000/health".to_string(),
                timeout_secs:        5,
                expected_status:     200,
                degraded_latency_ms: 2000,
            }),
            AnyProbeConfig::Http(HttpProbeConfig {
                service:             "nextjs-frontend".to_string(),
                url:                 "http://127.0.0.1:3000".to_string(),
                timeout_secs:        5,
                expected_status:     200,
                degraded_latency_ms: 2000,
            }),
        ],

        "python-nextjs" => vec![
            AnyProbeConfig::Service(ServiceProbeConfig { service_name: "nginx".to_string() }),
            AnyProbeConfig::Service(ServiceProbeConfig { service_name: "apache2".to_string() }),
            AnyProbeConfig::Service(ServiceProbeConfig { service_name: "mariadb".to_string() }),
            AnyProbeConfig::Service(ServiceProbeConfig { service_name: "postgresql@14-main".to_string() }),
            AnyProbeConfig::Tcp(TcpProbeConfig {
                service:      "python-tcp-8000".to_string(),
                host:         "127.0.0.1".to_string(),
                port:         8000,
                timeout_secs: 3,
            }),
            AnyProbeConfig::Tcp(TcpProbeConfig {
                service:      "nextjs-tcp-3000".to_string(),
                host:         "127.0.0.1".to_string(),
                port:         3000,
                timeout_secs: 3,
            }),
            AnyProbeConfig::Http(HttpProbeConfig {
                service:             "nginx-http-80".to_string(),
                url:                 "http://127.0.0.1".to_string(),
                timeout_secs:        5,
                expected_status:     200,
                degraded_latency_ms: 2000,
            }),
        ],

        "database" => vec![
            AnyProbeConfig::Service(ServiceProbeConfig { service_name: "mysql".to_string()      }),
            AnyProbeConfig::Service(ServiceProbeConfig { service_name: "postgresql".to_string() }),
            AnyProbeConfig::Tcp(TcpProbeConfig {
                service:      "mysql-tcp".to_string(),
                host:         "127.0.0.1".to_string(),
                port:         3306,
                timeout_secs: 3,
            }),
            AnyProbeConfig::Tcp(TcpProbeConfig {
                service:      "postgresql-tcp".to_string(),
                host:         "127.0.0.1".to_string(),
                port:         5432,
                timeout_secs: 3,
            }),
        ],

        // "generic" — padrão de fallback para servidores sem role definido
        _ => vec![
            AnyProbeConfig::Service(ServiceProbeConfig { service_name: "nginx".to_string()              }),
            AnyProbeConfig::Service(ServiceProbeConfig { service_name: "docker".to_string()             }),
            AnyProbeConfig::Service(ServiceProbeConfig { service_name: "pocket-noc-agent".to_string()   }),
        ],
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TESTES UNITÁRIOS
// ─────────────────────────────────────────────────────────────────────────────

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_service_probe_formats_message() {
        // Apenas verifica a estrutura do resultado — sem chamar systemctl de verdade
        let result = run_service_probe(&ServiceProbeConfig {
            service_name: "pocket-noc-test-nonexistent".to_string(),
        });
        // Um serviço que não existe retorna "inactive" → Down
        assert_eq!(result.status, ProbeStatus::Down);
        assert!(result.message.contains("pocket-noc-test-nonexistent"));
    }

    #[test]
    fn test_tcp_probe_refused_connection() {
        // Porta 19999 quase certamente fechada — deve retornar Down
        let result = run_tcp_probe(&TcpProbeConfig {
            service:      "test-closed-port".to_string(),
            host:         "127.0.0.1".to_string(),
            port:         19999,
            timeout_secs: 1,
        });
        assert_eq!(result.status, ProbeStatus::Down);
    }

    #[test]
    fn test_default_probes_wordpress_role() {
        let probes = default_probes_for_role("wordpress");
        assert!(!probes.is_empty());
        // Deve conter pelo menos o probe HTTP
        let has_http = probes.iter().any(|p| matches!(p, AnyProbeConfig::Http(_)));
        assert!(has_http, "WordPress role deve ter pelo menos 1 HTTP probe");
    }

    #[test]
    fn test_default_probes_erp_role() {
        let probes = default_probes_for_role("erp");
        // Deve testar o ERP Python E o Next.js
        let http_probes: Vec<_> = probes.iter()
            .filter(|p| matches!(p, AnyProbeConfig::Http(_)))
            .collect();
        assert_eq!(http_probes.len(), 2, "ERP role deve ter 2 HTTP probes (ERP + Next.js)");
    }
}
