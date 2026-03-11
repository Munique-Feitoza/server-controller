use axum::{
    middleware,
    routing::{get, post},
    Router,
};
use pocket_noc_agent::{
    api::{handlers::*, AppState},
    auth::JwtConfig,
    commands::default_emergency_commands,
    telemetry::TelemetryCollector,
};
use std::sync::Arc;
use tokio::sync::Mutex;
use tracing_subscriber;
use tower_http::cors::CorsLayer;

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

    let jwt_config = Arc::new(JwtConfig::new(jwt_secret, 3600)
        .expect("Failed to create JWT config"));

    tracing::info!("✅ JWT configured - protected routes active");

    // Configuração da aplicação
    let collector = Arc::new(Mutex::new(TelemetryCollector::new()));
    let command_executor = Arc::new(pocket_noc_agent::commands::CommandExecutor::new(
        default_emergency_commands(),
    ));

    let state = AppState {
        telemetry_collector: collector,
        command_executor,
    };

    // ========== ROTAS PÚBLICAS ==========
    let public_routes = Router::new()
        .route("/health", get(health_check))
        .with_state(state.clone());

    // ========== ROTAS PROTEGIDAS COM JWT ==========
    let protected_routes = Router::new()
        .route("/telemetry", get(get_telemetry))
        .route("/services/:service_name", get(get_service_status))
        .route("/commands", get(list_commands))
        .route("/commands/:command_id", post(execute_command))
        .route("/metrics", get(get_metrics))
        .layer(middleware::from_fn_with_state(
            jwt_config.clone(),
            pocket_noc_agent::api::middleware::jwt_middleware,
        ))
        .with_state(state);

    // ========== MONTAGEM FINAL ==========
    let app = Router::new()
        .merge(public_routes)
        .merge(protected_routes)
        .layer(CorsLayer::permissive())
        .layer(middleware::from_fn(pocket_noc_agent::api::middleware::logging_middleware));

    // Bind and serve
    let listener = tokio::net::TcpListener::bind("0.0.0.0:9443")
        .await
        .map_err(|e| format!("Failed to bind to port 9443: {}", e))?;

    tracing::info!("🔐 HTTP listening on http://0.0.0.0:9443 (use for testing)");
    tracing::info!("📋 Public routes:");
    tracing::info!("   GET /health - No auth required");
    tracing::info!("🔒 Protected routes (JWT Required - Bearer token):");
    tracing::info!("   GET /telemetry - System metrics");
    tracing::info!("   GET /services/<name> - Service status");
    tracing::info!("   GET /commands - List commands");
    tracing::info!("   POST /commands/<id> - Execute command");
    tracing::info!("   GET /metrics - Prometheus format");

    axum::serve(listener, app)
        .await
        .map_err(|e| format!("Server error: {}", e).into())
}
