use axum::{
    middleware,
    routing::{get, post, delete},
    Router,
};
use pocket_noc_agent::{
    api::{handlers::*, AppState},
    auth::JwtConfig,
    commands::default_emergency_commands,
    telemetry::{TelemetryCollector, AlertManager, AlertType},
    notifications::NtfyClient,
    watchdog::{WatchdogEngine, event::WatchdogEventStore},
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
        .unwrap_or_else(|_| {
            tracing::warn!("⚠️  POCKET_NOC_SECRET não definido - usando padrão inseguro (TESTE APENAS)");
            "test-insecure-secret-key-minimum-32-bytes-required-prodctn".to_string()
        });

    let jwt_config = Arc::new(JwtConfig::new(jwt_secret.clone(), 3600)
        .expect("Failed to create JWT config"));

    tracing::info!("✅ JWT configured - protected routes active");

    let collector = Arc::new(Mutex::new(TelemetryCollector::new()));
    let command_executor = Arc::new(pocket_noc_agent::commands::CommandExecutor::new(
        default_emergency_commands(),
    ));
    let alert_manager = Arc::new(Mutex::new(AlertManager::with_defaults()));

    // Store compartilhado de eventos do Watchdog (ring buffer 500 eventos)
    let watchdog_store = Arc::new(Mutex::new(WatchdogEventStore::with_defaults()));

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
    // Segurança: bind em 127.0.0.1 para forçar acesso via túnel SSH
    let addr = format!("127.0.0.1:{}", port);

    // Bind and serve
    let listener = tokio::net::TcpListener::bind(&addr)
        .await
        .map_err(|e| format!("Failed to bind to {}: {}", addr, e))?;

    tracing::info!("🔐 HTTP listening on http://{} (use for testing)", addr);
    tracing::info!("📋 Public routes:");
    tracing::info!("   GET /health - No auth required");
    tracing::info!("🔒 Protected routes (JWT Required - Bearer token):");
    tracing::info!("   GET /telemetry - System metrics");
    tracing::info!("   GET /alerts - Current system alerts");
    tracing::info!("   GET /services/<name> - Service status");
    tracing::info!("   GET /commands - List commands");
    tracing::info!("   POST /commands/<id> - Execute command");
    tracing::info!("   GET /metrics - Prometheus format");

    tracing::info!("   GET /watchdog/events - Watchdog remediation events (filter: ?server=&status=)");

    // Spawn background task — Telemetria (existente)
    let collector_task = collector.clone();
    let alert_manager_task = alert_manager.clone();
    let ntfy_client_task = ntfy_client.clone();
    
    tokio::spawn(async move {
        let mut last_notified_alerts: std::collections::HashMap<AlertType, String> = std::collections::HashMap::new();
        
        loop {
            // Coleta telemetria
            let mut coll = collector_task.lock().await;
            if let Ok(telemetry) = coll.collect() {
                // Analisa alertas
                let alert_mgr = alert_manager_task.lock().await;
                if let Ok(alerts) = alert_mgr.analyze(&telemetry) {
                    // Limpa do histórico alertas que não estão mais ativos
                    let active_types: Vec<AlertType> = alerts.iter().map(|a| a.alert_type.clone()).collect();
                    last_notified_alerts.retain(|k, _| active_types.contains(k));

                    for alert in alerts {
                        // DEDUPLICAÇÃO: Só notifica se a mensagem mudou ou se é um novo alerta
                        if let Some(last_msg) = last_notified_alerts.get(&alert.alert_type) {
                            if last_msg == &alert.message {
                                continue;
                            }
                        }

                        // Envia notificação ntfy para cada novo alerta
                        let priority = match alert.alert_type {
                            AlertType::HighCpu | 
                            AlertType::HighDisk |
                            AlertType::HighTemperature |
                            AlertType::SecurityThreat => 4, // High
                            _ => 3, // Default
                        };
                        
                        let tags = match alert.alert_type {
                            AlertType::HighCpu => "cpu,warning",
                            AlertType::SecurityThreat => "lock,skull,danger",
                            AlertType::RecentReboot => "reboot,info",
                            _ => "warning",
                        };

                        let _ = ntfy_client_task.send_alert(
                            alert.alert_type.label(),
                            &alert.message,
                            priority,
                            tags
                        ).await;

                        // Atualiza o histórico
                        last_notified_alerts.insert(alert.alert_type, alert.message);
                    }
                }
                drop(alert_mgr);
            }
            drop(coll);
            tokio::time::sleep(tokio::time::Duration::from_secs(60)).await;
        }
    });

    // ─── Spawn background task — WatchdogEngine ────────────────────────────
    {
        let watchdog_engine = WatchdogEngine::from_env(
            watchdog_store.clone(),
            ntfy_client.clone(),
        );
        tokio::spawn(async move {
            watchdog_engine.run().await;
        });
        tracing::info!("🐕 WatchdogEngine spawned — auto-remediation ativa");
    }

    axum::serve(listener, app)
        .await
        .map_err(|e| format!("Server error: {}", e).into())
}
