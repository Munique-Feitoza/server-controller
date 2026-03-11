use axum::{
    extract::State,
    http::StatusCode,
    response::IntoResponse,
    Json,
};
use serde_json::json;
use std::sync::Arc;
use tokio::sync::Mutex;

use crate::{
    commands::CommandExecutor,
    error::Result,
    services::ServiceMonitor,
    telemetry::TelemetryCollector,
};

/// Estado compartilhado da aplicação
#[derive(Clone)]
pub struct AppState {
    pub telemetry_collector: Arc<Mutex<TelemetryCollector>>,
    pub command_executor: Arc<CommandExecutor>,
}

/// GET /health - Healthcheck simples
pub async fn health_check() -> impl IntoResponse {
    (
        StatusCode::OK,
        Json(json!({
            "status": "healthy",
            "service": "pocket-noc-agent",
            "timestamp": chrono::Utc::now().to_rfc3339(),
        })),
    )
}

/// GET /telemetry - Retorna telemetria completa do sistema
pub async fn get_telemetry(
    State(state): State<AppState>,
) -> Result<Json<serde_json::Value>> {
    let mut collector = state.telemetry_collector.lock().await;
    let telemetry = collector.collect()?;

    Ok(Json(serde_json::to_value(telemetry).unwrap()))
}

/// GET /services/:service_name - Verifica o status de um serviço
pub async fn get_service_status(
    axum::extract::Path(service_name): axum::extract::Path<String>,
) -> Result<Json<serde_json::Value>> {
    let info = ServiceMonitor::check_service(&service_name)?;
    Ok(Json(serde_json::to_value(info).unwrap()))
}

/// GET /commands - Lista todos os comandos disponíveis
pub async fn list_commands(
    State(state): State<AppState>,
) -> impl IntoResponse {
    let commands = state.command_executor.list_commands();
    Json(json!({
        "commands": commands,
    }))
}

/// POST /commands/:command_id - Executa um comando de emergência
pub async fn execute_command(
    State(state): State<AppState>,
    axum::extract::Path(command_id): axum::extract::Path<String>,
) -> Result<Json<serde_json::Value>> {
    let result = state.command_executor.execute_command(&command_id)?;
    Ok(Json(serde_json::to_value(result).unwrap()))
}

/// GET /metrics - Retorna métricas simplificadas (para compatibilidade com Prometheus)
pub async fn get_metrics(
    State(state): State<AppState>,
) -> Result<String> {
    let mut collector = state.telemetry_collector.lock().await;
    let telemetry = collector.collect()?;

    let mut output = String::new();

    // CPU metrics
    output.push_str(&format!("# HELP cpu_usage_percent CPU usage percentage\n"));
    output.push_str(&format!("# TYPE cpu_usage_percent gauge\n"));
    output.push_str(&format!("cpu_usage_percent {}\n", telemetry.cpu.usage_percent));

    for core in &telemetry.cpu.cores {
        output.push_str(&format!(
            "cpu_usage_percent{{core=\"{}\"}} {}\n",
            core.index, core.usage_percent
        ));
    }

    // Memory metrics
    output.push_str(&format!("# HELP memory_usage_percent Memory usage percentage\n"));
    output.push_str(&format!("# TYPE memory_usage_percent gauge\n"));
    output.push_str(&format!("memory_usage_percent {}\n", telemetry.memory.usage_percent));

    output.push_str(&format!("memory_used_mb {}\n", telemetry.memory.used_mb));
    output.push_str(&format!("memory_total_mb {}\n", telemetry.memory.total_mb));

    // Disk metrics
    for disk in &telemetry.disk.disks {
        output.push_str(&format!(
            "disk_usage_percent{{mount=\"{}\",fs=\"{}\"}} {}\n",
            disk.mount_point, disk.filesystem, disk.usage_percent
        ));
        output.push_str(&format!(
            "disk_used_gb{{mount=\"{}\"}} {}\n",
            disk.mount_point, disk.used_gb
        ));
        output.push_str(&format!(
            "disk_total_gb{{mount=\"{}\"}} {}\n",
            disk.mount_point, disk.total_gb
        ));
    }

    // Uptime
    output.push_str(&format!(
        "system_uptime_seconds {}\n",
        telemetry.uptime.uptime_seconds
    ));

    output.push_str(&format!(
        "system_load_average{{period=\"1m\"}} {}\n",
        telemetry.uptime.load_average[0]
    ));

    Ok(output)
}
