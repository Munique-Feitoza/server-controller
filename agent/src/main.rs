use axum::{
    middleware,
    routing::{delete, get, post},
    Router,
};
use pocket_noc_agent::{
    api::middleware::SecurityState,
    api::{handlers::*, RateLimiter},
    audit::AuditLog,
    auth::JwtConfig,
    commands::default_emergency_commands,
    notifications::NtfyClient,
    security::ThreatTracker,
    telemetry::{AlertManager, AlertType, TelemetryCollector},
    watchdog::{
        event::WatchdogEventStore, remediation::RemediationEngine, WatchdogConfig, WatchdogEngine,
    },
};
use std::sync::Arc;
use tokio::sync::Mutex;
use tower_http::cors::CorsLayer;

/// O motor principal do Agente.
/// Utilizei 'tokio' para I/O assíncrono de alta performance, permitindo que o servidor
/// lide com centenas de requisições de telemetria simultâneas sem bloquear as threads do SO.
/// O uso de 'Arc<Mutex<T>>' garante que o estado compartilhado seja acessado de forma
/// segura entre threads, prevenindo corrupção de dados.
#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    // Inicializa logging
    tracing_subscriber::fmt()
        .with_max_level(tracing::Level::INFO)
        .init();

    tracing::info!("🔒 Starting Pocket NOC Agent with JWT Security");

    // Configuração do JWT (CRÍTICO: use POCKET_NOC_SECRET em produção!)
    let jwt_secret = std::env::var("POCKET_NOC_SECRET")
        .map(|s| s.trim().to_string())
        .unwrap_or_else(|_| {
            tracing::error!(
                "❌ POCKET_NOC_SECRET nao definido! Defina em /etc/pocket-noc-agent.env"
            );
            tracing::error!("   Gere com: openssl rand -base64 32");
            std::process::exit(1);
        });

    let jwt_config =
        Arc::new(JwtConfig::new(jwt_secret.clone(), 3600).expect("Failed to create JWT config"));

    let masked_secret = if jwt_secret.len() >= 4 {
        format!("{}****", &jwt_secret[..4])
    } else {
        "****".to_string()
    };
    tracing::info!(
        "✅ JWT configured - secret loaded (mask: {})",
        masked_secret
    );

    let collector = Arc::new(Mutex::new(TelemetryCollector::new()));
    let command_executor = Arc::new(pocket_noc_agent::commands::CommandExecutor::new(
        default_emergency_commands(),
    ));
    let alert_manager = Arc::new(Mutex::new(AlertManager::with_defaults()));

    // Configuração do Watchdog (lida do .env)
    let config = WatchdogConfig::from_env();

    // Store compartilhado de eventos do Watchdog (ring buffer 500 eventos)
    let watchdog_store = Arc::new(Mutex::new(WatchdogEventStore::with_defaults()));

    // Motor de remediação compartilhado entre API e WatchdogEngine
    let remediation_engine = Arc::new(Mutex::new(RemediationEngine::new(
        config.max_failures,
        config.cooldown_secs,
    )));

    // Audit Log (ring buffer 1000 entradas)
    let audit_log = Arc::new(Mutex::new(AuditLog::with_defaults()));

    // Rate Limiter (configuravel via env)
    let rate_limiter = RateLimiter::from_env();
    tracing::info!(
        "⚡ Rate limiter: {} req/min per IP",
        rate_limiter.max_requests
    );

    // Rastreador de ameacas (zip bomb + auto-ban apos 5 falhas)
    let threat_tracker = Arc::new(Mutex::new(ThreatTracker::new()));
    tracing::info!("🛡️ Defesa ativa: zip bomb + auto-ban apos 5 tentativas falhas");

    // Estado de seguranca unificado (JWT + threat tracking)
    let security_state = SecurityState {
        jwt_config: jwt_config.clone(),
        threat_tracker: threat_tracker.clone(),
    };

    // Configuracao do ntfy
    let ntfy_topic =
        std::env::var("NTFY_TOPIC").unwrap_or_else(|_| format!("pocket_noc_{}", &jwt_secret[..8]));
    let ntfy_client = Arc::new(NtfyClient::new(&ntfy_topic));

    tracing::info!("🔔 Ntfy notifications active on topic: {}", ntfy_topic);

    // Store de incidentes de seguranca (webhook do dashboard + deteccoes locais)
    let incident_store = Arc::new(Mutex::new(
        pocket_noc_agent::security::incidents::IncidentStore::new(),
    ));

    let state = AppState {
        telemetry_collector: collector.clone(),
        command_executor,
        alert_manager: alert_manager.clone(),
        watchdog_event_store: watchdog_store.clone(),
        remediation_engine: remediation_engine.clone(),
        audit_log: audit_log.clone(),
        incident_store: incident_store.clone(),
    };

    // ========== MONTAGEM FINAL ==========
    let app = Router::new()
        .route("/health", get(health_check))
        .route("/telemetry", get(get_telemetry))
        .route("/alerts", get(get_alerts))
        .route("/alerts/config", post(update_alert_config))
        .route("/processes", get(get_top_processes))
        .route("/processes/:pid", delete(kill_process))
        .route("/logs", get(get_service_logs))
        .route("/services/:service_name", get(get_service_status))
        .route("/commands", get(list_commands))
        .route("/commands/:command_id", post(execute_command))
        .route("/security/block-ip", post(block_ip))
        .route("/metrics", get(get_metrics))
        // ─── Endpoints do Watchdog ──────────────────────────────────────────
        .route("/watchdog/events", get(get_watchdog_events))
        .route("/watchdog/events", delete(clear_watchdog_events))
        .route("/watchdog/reset", post(reset_watchdog))
        .route("/watchdog/breakers", get(get_watchdog_breakers))
        // ─── Endpoints do Log de Auditoria ──────────────────────────────────
        .route("/audit/logs", get(get_audit_logs))
        .route("/audit/logs", delete(clear_audit_logs))
        // ─── Monitoramento Docker ───────────────────────────────────────────
        .route("/docker/containers", get(get_docker_containers))
        // ─── Verificacao SSL de todos os dominios ─────────────────────────────
        .route("/ssl/check", get(check_ssl))
        // ─── PHP-FPM pools por site ─────────────────────────────────────────
        .route("/phpfpm/pools", get(get_phpfpm_pools))
        // ─── Status de Backup ───────────────────────────────────────────────
        .route("/backups/status", get(get_backup_status))
        // ─── Configuração do Agente ─────────────────────────────────────────
        .route("/config", get(get_config))
        .route("/config", post(update_config))
        // ─── Seguranca: webhook do dashboard + consulta de incidentes ────
        .route("/webhook/security", post(receive_security_webhook))
        .route("/security/incidents", get(get_security_incidents))
        // ─── Pilha de Middleware ──────────────────────────────────────────────
        .layer(middleware::from_fn_with_state(
            rate_limiter.clone(),
            pocket_noc_agent::api::rate_limit::rate_limit_middleware,
        ))
        .layer(middleware::from_fn_with_state(
            security_state.clone(),
            pocket_noc_agent::api::middleware::security_middleware,
        ))
        .with_state(state)
        .layer(
            CorsLayer::new()
                .allow_origin([
                    "http://localhost".parse().unwrap(),
                    "http://127.0.0.1".parse().unwrap(),
                ])
                .allow_methods([
                    axum::http::Method::GET,
                    axum::http::Method::POST,
                    axum::http::Method::DELETE,
                ])
                .allow_headers(tower_http::cors::Any),
        )
        .layer(middleware::from_fn(
            pocket_noc_agent::api::middleware::logging_middleware,
        ));

    // Configuração da porta
    let port = std::env::var("POCKET_NOC_PORT").unwrap_or_else(|_| "9443".to_string());

    // Seguranca: bind em 127.0.0.1 para forcar acesso via tunel SSH
    let addr = format!("127.0.0.1:{}", port);

    let listener = tokio::net::TcpListener::bind(&addr)
        .await
        .map_err(|e| format!("Failed to bind to {}: {}", addr, e))?;

    tracing::info!("🔐 HTTP listening on http://{}", addr);
    tracing::info!("🛡️ Defesa ativa: honeypot + zip bomb + auto-ban (5 tentativas)");
    tracing::info!("📋 Rotas protegidas por JWT + rate limiting + threat tracking");

    // Inicializa tarefas em background
    spawn_background_tasks(
        collector,
        alert_manager,
        ntfy_client.clone(),
        watchdog_store,
        remediation_engine,
    );

    axum::serve(listener, app)
        .await
        .map_err(|e| format!("Server error: {}", e).into())
}

/// Inicializa as tarefas de background (telemetria + watchdog)
fn spawn_background_tasks(
    collector: Arc<Mutex<TelemetryCollector>>,
    alert_manager: Arc<Mutex<AlertManager>>,
    ntfy_client: Arc<NtfyClient>,
    watchdog_store: Arc<Mutex<WatchdogEventStore>>,
    remediation_engine: Arc<Mutex<RemediationEngine>>,
) {
    // Inicializa tarefa em background — Telemetria
    let collector_task = collector.clone();
    let alert_manager_task = alert_manager.clone();
    let ntfy_client_task = ntfy_client.clone();

    tokio::spawn(async move {
        let mut last_notified_alerts: std::collections::HashMap<(AlertType, Option<String>), i64> =
            std::collections::HashMap::new();

        loop {
            let telemetry_result = {
                let mut coll = collector_task.lock().await;
                coll.collect()
            };

            if let Ok(telemetry) = telemetry_result {
                let alerts_result = {
                    let alert_mgr = alert_manager_task.lock().await;
                    alert_mgr.analyze(&telemetry)
                };

                if let Ok(alerts) = alerts_result {
                    let now = chrono::Utc::now().timestamp();

                    let active_keys: Vec<(AlertType, Option<String>)> = alerts
                        .iter()
                        .map(|a| (a.alert_type.clone(), a.component.clone()))
                        .collect();
                    last_notified_alerts.retain(|k, _| active_keys.contains(k));

                    for alert in alerts {
                        let key = (alert.alert_type.clone(), alert.component.clone());

                        if let Some(last_ts) = last_notified_alerts.get(&key) {
                            if now - last_ts < 1800 {
                                continue;
                            }
                        }

                        let _ = ntfy_client_task
                            .send_alert(
                                alert.alert_type.label(),
                                &alert.message,
                                alert.alert_type.ntfy_priority(),
                                alert.alert_type.ntfy_tags(),
                            )
                            .await;

                        last_notified_alerts.insert(key, now);
                    }
                }
            }

            tokio::time::sleep(tokio::time::Duration::from_secs(60)).await;
        }
    });

    // ─── Inicializa tarefa em background — WatchdogEngine ───────────────────
    {
        let watchdog_engine = WatchdogEngine::from_env(watchdog_store.clone(), ntfy_client.clone());
        let rem_clone = remediation_engine.clone();
        tokio::spawn(async move {
            watchdog_engine.run(rem_clone).await;
        });
        tracing::info!("🐕 WatchdogEngine spawned — auto-remediation ativa");
    }

    // ─── Verificacao SSL periodica (a cada 6 horas) ──────────────────────
    {
        let ntfy_ssl = ntfy_client.clone();
        tokio::spawn(async move {
            // Aguarda 2 minutos antes da primeira verificacao
            tokio::time::sleep(tokio::time::Duration::from_secs(120)).await;

            loop {
                let result = pocket_noc_agent::telemetry::ssl::check_all_ssl();

                for cert in &result.certs {
                    match cert.status.as_str() {
                        "expired" => {
                            tracing::error!(
                                "🔴 SSL EXPIRADO: {} (expirou ha {} dias)",
                                cert.domain,
                                cert.days_remaining.abs()
                            );
                            let _ = ntfy_ssl
                                .send_alert(
                                    "SSL Expirado",
                                    &format!(
                                        "O certificado de {} expirou ha {} dias!",
                                        cert.domain,
                                        cert.days_remaining.abs()
                                    ),
                                    5, // prioridade maxima
                                    "rotating_light,lock",
                                )
                                .await;
                        }
                        "expiring" => {
                            tracing::warn!(
                                "🟡 SSL EXPIRANDO: {} ({} dias restantes)",
                                cert.domain,
                                cert.days_remaining
                            );
                            let _ = ntfy_ssl
                                .send_alert(
                                    "SSL Expirando",
                                    &format!(
                                        "O certificado de {} expira em {} dias. Renove agora!",
                                        cert.domain, cert.days_remaining
                                    ),
                                    4, // prioridade alta
                                    "warning,lock",
                                )
                                .await;
                        }
                        "wrong_cert" => {
                            tracing::error!(
                                "🔴 SSL ERRADO: {} (servindo cert de {})",
                                cert.domain,
                                cert.subject
                            );
                            let _ = ntfy_ssl
                                .send_alert(
                                    "SSL Certificado Errado",
                                    &format!(
                                        "{} esta servindo o certificado de outro dominio: {}",
                                        cert.domain, cert.subject
                                    ),
                                    4,
                                    "warning,lock",
                                )
                                .await;
                        }
                        "no_cert" | "error" => {
                            tracing::warn!(
                                "🟠 SSL ERRO: {} (sem certificado ou inacessivel)",
                                cert.domain
                            );
                            let motivo = if cert.status == "no_cert" {
                                "nao tem certificado instalado"
                            } else {
                                "esta inacessivel na porta 443"
                            };
                            let _ = ntfy_ssl
                                .send_alert(
                                    "SSL Falhou",
                                    &format!(
                                        "{} {} — a emissao/renovacao provavelmente falhou",
                                        cert.domain, motivo
                                    ),
                                    4,
                                    "warning,lock",
                                )
                                .await;
                        }
                        _ => {}
                    }
                }

                if result.expired_count > 0 || result.expiring_count > 0 {
                    tracing::warn!(
                        "🔒 SSL Check: {}/{} OK | {} expirando | {} expirados | {} erros",
                        result.ok_count,
                        result.total_domains,
                        result.expiring_count,
                        result.expired_count,
                        result.error_count
                    );
                }

                // Verifica a cada 6 horas
                tokio::time::sleep(tokio::time::Duration::from_secs(6 * 3600)).await;
            }
        });
        tracing::info!("🔒 SSL monitor spawned — verificacao a cada 6 horas");
    }
}
