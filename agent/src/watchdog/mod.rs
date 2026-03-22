/// Modulo Watchdog -- Auto-Remediacao com Circuit Breaker
///
/// # Arquitetura:
/// ```text
/// WatchdogEngine
///   +-- probes.rs          -- Sensores (HTTP, systemctl, TCP)
///   +-- circuit_breaker.rs -- Anti-loop (Closed/Open/HalfOpen)
///   +-- remediation.rs     -- Executor de cura (systemctl restart/reload)
///   +-- event.rs           -- Telemetria estruturada -> Kotlin app
/// ```
pub mod probes;
pub mod circuit_breaker;
pub mod remediation;
pub mod event;

use std::sync::Arc;
use tokio::sync::Mutex;
use tracing::{info, warn};

use crate::notifications::NtfyClient;
use probes::{
    AnyProbeConfig, ProbeStatus,
    run_http_probe, run_service_probe, run_tcp_probe,
    default_probes_for_role,
};
use remediation::{RemediationEngine, RemediationStatus};
use event::{WatchdogEvent, WatchdogEventStore};

// ─────────────────────────────────────────────────────────────────────────────
// CONFIGURAÇÃO DO ENGINE
// ─────────────────────────────────────────────────────────────────────────────

/// Configuração do WatchdogEngine — lida do `.env` na inicialização
#[derive(Debug, Clone)]
pub struct WatchdogConfig {
    /// ID único do servidor (ex: "vps-runcloud-01")
    pub server_id:       String,
    /// Papel do servidor (ex: "wordpress", "erp", "database", "generic")
    pub server_role:     String,
    /// Hostname da máquina (resultado de `hostname`)
    pub server_hostname: String,
    /// Intervalo de ciclo em segundos (padrão: 30)
    pub cycle_secs:      u64,
    /// Máximo de falhas antes de abrir o Circuit Breaker (padrão: 3)
    pub max_failures:    u32,
    /// Cooldown em segundos após abrir o Circuit Breaker (padrão: 300)
    pub cooldown_secs:   u64,
    /// URL do webhook para enviar eventos JSON (opcional)
    pub webhook_url:     Option<String>,
}

impl WatchdogConfig {
    /// Lê toda a configuração das variáveis de ambiente
    pub fn from_env() -> Self {
        let server_hostname = std::process::Command::new("hostname")
            .output()
            .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
            .unwrap_or_else(|_| "unknown-host".to_string());

        Self {
            server_id: std::env::var("SERVER_ID")
                .unwrap_or_else(|_| {
                    warn!("⚠️  SERVER_ID não definido — usando hostname como fallback");
                    server_hostname.clone()
                }),
            server_role: std::env::var("SERVER_ROLE")
                .unwrap_or_else(|_| {
                    warn!("⚠️  SERVER_ROLE não definido — usando perfil 'generic'");
                    "generic".to_string()
                }),
            server_hostname,
            cycle_secs: std::env::var("WATCHDOG_INTERVAL_SECS")
                .ok()
                .and_then(|v| v.parse().ok())
                .unwrap_or(30),
            max_failures: std::env::var("WATCHDOG_MAX_FAILURES")
                .ok()
                .and_then(|v| v.parse().ok())
                .unwrap_or(3),
            cooldown_secs: std::env::var("WATCHDOG_COOLDOWN_SECS")
                .ok()
                .and_then(|v| v.parse().ok())
                .unwrap_or(300),
            webhook_url: std::env::var("WATCHDOG_WEBHOOK_URL").ok(),
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WATCHDOG ENGINE — Orchestrador principal
// ─────────────────────────────────────────────────────────────────────────────

/// Motor principal do Watchdog — roda como background task em tokio
///
/// # Arquitetura de Concorrência e Rust Safety
/// O `WatchdogEventStore` é compartilhado com a API via `Arc<Mutex<>>`, garantindo `Thread-Safety`.
/// 
/// O `RemediationEngine` vive *exclusivamente* dentro da Task principal (não obedece ao padrão 
/// de state compartilhado) pois é o único que escreve no `HashMap<service, CircuitBreaker>`.
/// Isolando o estado mutável do Circuit Breaker em uma única Thread Local, evitamos contenção de Locks 
/// de alta sobrecarga (Lock Contention) e garantimos máxima performance analítica `O(1)`.
pub struct WatchdogEngine {
    config:      WatchdogConfig,
    probes:      Vec<AnyProbeConfig>,
    event_store: Arc<Mutex<WatchdogEventStore>>,
    ntfy_client: Arc<NtfyClient>,
}

impl WatchdogEngine {
    /// Cria o engine a partir do `.env` (role → probes padrão)
    pub fn from_env(
        event_store: Arc<Mutex<WatchdogEventStore>>,
        ntfy_client: Arc<NtfyClient>,
    ) -> Self {
        let config = WatchdogConfig::from_env();
        let probes = default_probes_for_role(&config.server_role);

        info!(
            "🐕 WatchdogEngine iniciado | servidor: {} | role: {} | probes: {} | ciclo: {}s",
            config.server_id, config.server_role, probes.len(), config.cycle_secs
        );

        Self { config, probes, event_store, ntfy_client }
    }

    /// Loop principal do Watchdog — roda indefinidamente (spawn via tokio::spawn)
    ///
    /// Fluxo por ciclo:
    /// 1. Executa todos os probes configurados
    /// 2. Para cada probe Down → chama RemediationEngine
    /// 3. Persiste o WatchdogEvent no ring buffer
    /// 4. Dispara notificação ntfy para o app
    /// 5. (Opcional) Envia evento JSON via webhook HTTP
    /// 6. Dorme pelo `cycle_secs` configurado
    pub async fn run(self) {
        let config = self.config.clone();
        let probes = self.probes.clone();
        let event_store = self.event_store;
        let ntfy = self.ntfy_client;
        let webhook_url = config.webhook_url.clone();

        // RemediationEngine — vive dentro do loop (não compartilhado externamente)
        let mut remediation = RemediationEngine::new(config.max_failures, config.cooldown_secs);
        // HTTP client compartilhado para webhook (reutilizamos conexões TCP via pool)
        let http_client = reqwest::Client::new();

        loop {
            info!(
                "🔍 [{}] Iniciando ciclo de verificação ({} probes)...",
                config.server_id, probes.len()
            );

            for probe in &probes {
                let probe_result = match probe {
                    AnyProbeConfig::Http(cfg)    => run_http_probe(cfg).await,
                    AnyProbeConfig::Service(cfg) => {
                        // Probes de systemctl são síncronas — evitamos bloquear o runtime
                        // movendo para uma thread de bloqueio gerenciada pelo tokio
                        let cfg_clone = cfg.clone();
                        tokio::task::spawn_blocking(move || run_service_probe(&cfg_clone))
                            .await
                            .unwrap_or_else(|e| probes::ProbeResult {
                                status:     ProbeStatus::Down,
                                latency_ms: None,
                                message:    format!("Falha ao executar probe em thread: {e}"),
                                service:    cfg.service_name.clone(),
                            })
                    }
                    AnyProbeConfig::Tcp(cfg) => {
                        let cfg_clone = cfg.clone();
                        tokio::task::spawn_blocking(move || run_tcp_probe(&cfg_clone))
                            .await
                            .unwrap_or_else(|e| probes::ProbeResult {
                                status:     ProbeStatus::Down,
                                latency_ms: None,
                                message:    format!("Falha ao executar TCP probe em thread: {e}"),
                                service:    cfg.service.clone(),
                            })
                    }
                };

                // Serviço saudável — apenas loga em nível debug, sem evento
                if probe_result.is_healthy() {
                    info!(
                        "✅ [{}] {} — OK ({}ms)",
                        config.server_id,
                        probe_result.service,
                        probe_result.latency_ms.map(|l| l.to_string()).unwrap_or("-".to_string())
                    );
                    continue;
                }

                // ─── Serviço Down ou Degraded → Remediação ───────────────────
                warn!(
                    "⚠️  [{}] {} — {} | {}",
                    config.server_id, probe_result.service,
                    probe_result.status, probe_result.message
                );

                let action = RemediationEngine::recommended_action(&probe_result.service);
                let rem_result = remediation.execute(&probe_result.service, action.clone());

                // ─── Monta o WatchdogEvent ────────────────────────────────────
                let event = WatchdogEvent::new(
                    &config.server_id,
                    &config.server_role,
                    &config.server_hostname,
                    &probe_result.service,
                    &probe_result.status,
                    probe_result.latency_ms,
                    &rem_result.action,
                    &rem_result.status,
                    rem_result.attempts,
                    rem_result.circuit_open,
                    rem_result.message.clone(),
                );

                // ─── Persiste no ring buffer ──────────────────────────────────
                {
                    let mut store = event_store.lock().await;
                    store.push(event.clone());
                }

                // ─── Notificação ntfy (para o celular) ───────────────────────
                let (priority, tags) = ntfy_priority_for(&rem_result.status, &probe_result.status);
                let ntfy_title = format!(
                    "[{}] {} {}",
                    config.server_id,
                    probe_result.service,
                    probe_result.status
                );
                let _ = ntfy.send_alert(&ntfy_title, &rem_result.message, priority, tags).await;

                // ─── Webhook para o servidor controlador (opcional) ───────────
                if let Some(url) = &webhook_url {
                    if let Ok(json) = serde_json::to_string(&event) {
                        let _ = http_client
                            .post(url)
                            .header("Content-Type", "application/json")
                            .body(json)
                            .send()
                            .await;
                    }
                }
            }

            tokio::time::sleep(tokio::time::Duration::from_secs(config.cycle_secs)).await;
        }
    }
}

/// Retorna (priority, tags) para o ntfy baseado no resultado da remediação
fn ntfy_priority_for(status: &RemediationStatus, probe: &ProbeStatus) -> (u8, &'static str) {
    match status {
        RemediationStatus::CircuitOpen  => (5, "rotating_light,skull,danger"),   // urgent
        RemediationStatus::Failed       => (4, "warning,tools"),                  // high
        RemediationStatus::Success      => (3, "white_check_mark,tools"),         // default
        RemediationStatus::NotNeeded    => {
            match probe {
                ProbeStatus::Degraded => (3, "warning"),
                _                     => (2, "info"),
            }
        }
    }
}
