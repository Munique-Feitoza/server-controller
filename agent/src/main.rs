use axum::{
    middleware,
    routing::{get, post, delete},
    Router,
};
use pocket_noc_agent::{
    api::{handlers::*, RateLimiter},
    audit::AuditLog,
    auth::JwtConfig,
    commands::default_emergency_commands,
    telemetry::{TelemetryCollector, AlertManager, AlertType},
    notifications::NtfyClient,
    watchdog::{WatchdogEngine, WatchdogConfig, event::WatchdogEventStore, remediation::RemediationEngine},
};
use std::sync::Arc;
use tokio::sync::Mutex;
use tracing_subscriber;
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
            tracing::warn!("⚠️  POCKET_NOC_SECRET não definido - usando padrão inseguro (TESTE APENAS)");
            "super-secret-key-change-me-123".to_string()
        });

    let jwt_config = Arc::new(JwtConfig::new(jwt_secret.clone(), 3600)
        .expect("Failed to create JWT config"));

    let masked_secret = if jwt_secret.len() >= 4 {
        format!("{}****", &jwt_secret[..4])
    } else {
        "****".to_string()
    };
    tracing::info!("✅ JWT configured - secret loaded (mask: {})", masked_secret);

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

    // Rate Limiter (configurável via env)
    let rate_limiter = RateLimiter::from_env();
    tracing::info!("⚡ Rate limiter: {} req/min per IP", rate_limiter.max_requests);

    // Configuração do ntfy (Tópico dinâmico baseado no segredo para evitar stalkers)
    let ntfy_topic = std::env::var("NTFY_TOPIC")
        .unwrap_or_else(|_| format!("pocket_noc_{}", &jwt_secret[..8]));
    let ntfy_client = Arc::new(NtfyClient::new(&ntfy_topic));

    tracing::info!("🔔 Ntfy notifications active on topic: {}", ntfy_topic);

    let state = AppState {
        telemetry_collector:  collector.clone(),
        command_executor,
        alert_manager:        alert_manager.clone(),
        watchdog_event_store: watchdog_store.clone(),
        remediation_engine:   remediation_engine.clone(),
        audit_log:            audit_log.clone(),
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
        // ─── Watchdog endpoints ─────────────────────────────────────────────
        .route("/watchdog/events", get(get_watchdog_events))
        .route("/watchdog/events", delete(clear_watchdog_events))
        .route("/watchdog/reset", post(reset_watchdog))
        .route("/watchdog/breakers", get(get_watchdog_breakers))
        // ─── Audit Log endpoints ────────────────────────────────────────────
        .route("/audit/logs", get(get_audit_logs))
        .route("/audit/logs", delete(clear_audit_logs))
        // ─── Docker monitoring ──────────────────────────────────────────────
        .route("/docker/containers", get(get_docker_containers))
        // ─── Backup status ──────────────────────────────────────────────────
        .route("/backups/status", get(get_backup_status))
        // ─── Agent configuration ────────────────────────────────────────────
        .route("/config", get(get_config))
        .route("/config", post(update_config))
        // ─── Middleware stack ────────────────────────────────────────────────
        .layer(middleware::from_fn_with_state(
            rate_limiter.clone(),
            pocket_noc_agent::api::rate_limit::rate_limit_middleware,
        ))
        .layer(middleware::from_fn_with_state(
            jwt_config.clone(),
            pocket_noc_agent::api::middleware::jwt_middleware,
        ))
        .with_state(state)
        .layer(CorsLayer::permissive())
        .layer(middleware::from_fn(pocket_noc_agent::api::middleware::logging_middleware));

    // Port configuration
    let port = std::env::var("POCKET_NOC_PORT")
        .unwrap_or_else(|_| "9443".to_string());

    // ─── TLS Support ────────────────────────────────────────────────────────
    let tls_cert = std::env::var("TLS_CERT_PATH").ok();
    let tls_key = std::env::var("TLS_KEY_PATH").ok();

    if let (Some(cert_path), Some(key_path)) = (tls_cert, tls_key) {
        // HTTPS mode
        let addr = format!("0.0.0.0:{}", port);
        tracing::info!("🔐 TLS enabled — loading cert from {} and key from {}", cert_path, key_path);

        // Carrega certificado e chave
        let cert = std::fs::read(&cert_path)
            .map_err(|e| format!("Failed to read TLS cert: {}", e))?;
        let key = std::fs::read(&key_path)
            .map_err(|e| format!("Failed to read TLS key: {}", e))?;

        let certs = rustls_pemfile::certs(&mut cert.as_slice())
            .filter_map(|c| c.ok())
            .map(|c| rustls::Certificate(c.to_vec()))
            .collect::<Vec<_>>();

        let key = rustls_pemfile::private_key(&mut key.as_slice())
            .ok()
            .flatten()
            .map(|k| rustls::PrivateKey(k.secret_der().to_vec()))
            .ok_or("Failed to parse TLS private key")?;

        let tls_config = rustls::ServerConfig::builder()
            .with_safe_defaults()
            .with_no_client_auth()
            .with_single_cert(certs, key)
            .map_err(|e| format!("TLS config error: {}", e))?;

        let tls_acceptor = tokio_rustls::TlsAcceptor::from(Arc::new(tls_config));
        let listener = tokio::net::TcpListener::bind(&addr).await
            .map_err(|e| format!("Failed to bind to {}: {}", addr, e))?;

        tracing::info!("🔒 HTTPS listening on https://{}", addr);

        // Spawn background tasks before entering serve loop
        spawn_background_tasks(collector, alert_manager, ntfy_client.clone(), watchdog_store, remediation_engine);

        loop {
            let (stream, _addr) = listener.accept().await?;
            let acceptor = tls_acceptor.clone();
            let app = app.clone();

            tokio::spawn(async move {
                match acceptor.accept(stream).await {
                    Ok(tls_stream) => {
                        let io = hyper_util::rt::TokioIo::new(tls_stream);
                        let service = hyper::service::service_fn(move |req| {
                            let app = app.clone();
                            async move {
                                app.into_service().call(req).await
                            }
                        });
                        let _ = hyper::server::conn::http1::Builder::new()
                            .serve_connection(io, service)
                            .await;
                    }
                    Err(e) => {
                        tracing::error!("TLS handshake error: {}", e);
                    }
                }
            });
        }
    } else {
        // Plain HTTP mode (current behavior)
        let addr = format!("127.0.0.1:{}", port);

        let listener = tokio::net::TcpListener::bind(&addr)
            .await
            .map_err(|e| format!("Failed to bind to {}: {}", addr, e))?;

        tracing::info!("🔐 HTTP listening on http://{} (use SSH tunnel for security)", addr);
        tracing::info!("📋 Public routes:");
        tracing::info!("   GET /health - No auth required");
        tracing::info!("🔒 Protected routes (JWT Required):");
        tracing::info!("   GET  /telemetry, /alerts, /processes, /logs, /metrics");
        tracing::info!("   POST /commands/<id>, /security/block-ip, /alerts/config");
        tracing::info!("   GET  /watchdog/events, /watchdog/breakers");
        tracing::info!("   GET  /audit/logs, /docker/containers, /backups/status, /config");

        // Spawn background tasks
        spawn_background_tasks(collector, alert_manager, ntfy_client.clone(), watchdog_store, remediation_engine);

        axum::serve(listener, app)
            .await
            .map_err(|e| format!("Server error: {}", e).into())
    }
}

/// Spawna as tasks de background (telemetria + watchdog)
fn spawn_background_tasks(
    collector: Arc<Mutex<TelemetryCollector>>,
    alert_manager: Arc<Mutex<AlertManager>>,
    ntfy_client: Arc<NtfyClient>,
    watchdog_store: Arc<Mutex<WatchdogEventStore>>,
    remediation_engine: Arc<Mutex<RemediationEngine>>,
) {
    // Spawn background task — Telemetria
    let collector_task = collector.clone();
    let alert_manager_task = alert_manager.clone();
    let ntfy_client_task = ntfy_client.clone();

    tokio::spawn(async move {
        let mut last_notified_alerts: std::collections::HashMap<(AlertType, Option<String>), i64> = std::collections::HashMap::new();

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

                    let active_keys: Vec<(AlertType, Option<String>)> = alerts.iter()
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

                        let _ = ntfy_client_task.send_alert(
                            alert.alert_type.label(),
                            &alert.message,
                            alert.alert_type.ntfy_priority(),
                            alert.alert_type.ntfy_tags()
                        ).await;

                        last_notified_alerts.insert(key, now);
                    }
                }
            }

            tokio::time::sleep(tokio::time::Duration::from_secs(60)).await;
        }
    });

    // ─── Spawn background task — WatchdogEngine ────────────────────────────
    {
        let watchdog_engine = WatchdogEngine::from_env(
            watchdog_store.clone(),
            ntfy_client.clone(),
        );
        let rem_clone = remediation_engine.clone();
        tokio::spawn(async move {
            watchdog_engine.run(rem_clone).await;
        });
        tracing::info!("🐕 WatchdogEngine spawned — auto-remediation ativa");
    }
}
