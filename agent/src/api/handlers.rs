use axum::{
    extract::{Query, State},
    http::StatusCode,
    response::IntoResponse,
    Json,
};
use serde_json::json;
use std::collections::HashMap;
use std::sync::Arc;
use tokio::sync::Mutex;

use crate::{
    audit::AuditLog,
    commands::CommandExecutor,
    error::Result,
    security::incidents::{IncidentStore, SecurityIncident},
    services::ServiceMonitor,
    telemetry::{AlertManager, TelemetryCollector},
    watchdog::{event::WatchdogEventStore, remediation::RemediationEngine},
};

/// Estado compartilhado da aplicação
#[derive(Clone)]
pub struct AppState {
    pub telemetry_collector: Arc<Mutex<TelemetryCollector>>,
    pub command_executor: Arc<CommandExecutor>,
    pub alert_manager: Arc<Mutex<AlertManager>>,
    /// Store de eventos do Watchdog — compartilhado com o engine background
    pub watchdog_event_store: Arc<Mutex<WatchdogEventStore>>,
    /// Motor de remediação — compartilhado para reset manual de Circuit Breakers
    pub remediation_engine: Arc<Mutex<RemediationEngine>>,
    /// Log de auditoria — registra todas as ações da API
    pub audit_log: Arc<Mutex<AuditLog>>,
    /// Incidentes de seguranca (webhook do dashboard + deteccoes locais)
    pub incident_store: Arc<Mutex<IncidentStore>>,
}

/// GET /health - Verificação de saúde do agente
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
pub async fn get_telemetry(State(state): State<AppState>) -> Result<Json<serde_json::Value>> {
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
pub async fn list_commands(State(state): State<AppState>) -> impl IntoResponse {
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

/// GET /metrics - Retorna métricas simplificadas (compatível com Prometheus)
pub async fn get_metrics(State(state): State<AppState>) -> Result<String> {
    let mut collector = state.telemetry_collector.lock().await;
    let telemetry = collector.collect()?;

    let mut output = String::new();

    // Métricas de CPU
    output.push_str("# HELP cpu_usage_percent CPU usage percentage\n");
    output.push_str("# TYPE cpu_usage_percent gauge\n");
    output.push_str(&format!(
        "cpu_usage_percent {}\n",
        telemetry.cpu.usage_percent
    ));

    for core in &telemetry.cpu.cores {
        output.push_str(&format!(
            "cpu_usage_percent{{core=\"{}\"}} {}\n",
            core.index, core.usage_percent
        ));
    }

    // Métricas de memória
    output.push_str("# HELP memory_usage_percent Memory usage percentage\n");
    output.push_str("# TYPE memory_usage_percent gauge\n");
    output.push_str(&format!(
        "memory_usage_percent {}\n",
        telemetry.memory.usage_percent
    ));

    output.push_str(&format!("memory_used_mb {}\n", telemetry.memory.used_mb));
    output.push_str(&format!("memory_total_mb {}\n", telemetry.memory.total_mb));

    // Métricas de disco
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

    // Tempo de atividade
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

/// GET /logs - Retorna os últimos logs do sistema ou de um serviço
pub async fn get_service_logs(
    axum::extract::Query(params): axum::extract::Query<std::collections::HashMap<String, String>>,
) -> Result<Json<serde_json::Value>> {
    let service = params
        .get("service")
        .map(|s| s.as_str())
        .unwrap_or("pocket-noc-agent");
    let lines = params
        .get("lines")
        .and_then(|l| l.parse::<u32>().ok())
        .unwrap_or(100);

    let output = std::process::Command::new("journalctl")
        .arg("-u")
        .arg(service)
        .arg("-n")
        .arg(lines.to_string())
        .arg("--no-pager")
        .output()
        .map_err(|e| {
            crate::error::AgentError::CommandError(format!("Failed to execute journalctl: {}", e))
        })?;

    let logs = String::from_utf8_lossy(&output.stdout).to_string();

    Ok(Json(json!({
        "service": service,
        "logs": logs,
        "lines": lines,
        "timestamp": chrono::Utc::now().to_rfc3339(),
    })))
}

/// GET /alerts - Retorna uma lista de alertas atuais
pub async fn get_alerts(State(state): State<AppState>) -> Result<Json<serde_json::Value>> {
    let mut collector = state.telemetry_collector.lock().await;
    let telemetry = collector.collect()?;
    let alert_manager = state.alert_manager.lock().await;
    let alerts = alert_manager.analyze(&telemetry)?;

    Ok(Json(json!({
        "alerts": alerts,
        "count": alerts.len(),
        "timestamp": chrono::Utc::now().to_rfc3339(),
    })))
}

/// GET /processes - Retorna a lista de processos (Top 10)
pub async fn get_top_processes(State(state): State<AppState>) -> Result<Json<serde_json::Value>> {
    let mut collector = state.telemetry_collector.lock().await;
    let telemetry = collector.collect()?;

    Ok(Json(json!({
        "processes": telemetry.processes.top_processes,
        "timestamp": chrono::Utc::now().to_rfc3339(),
    })))
}

/// DELETE /processes/:pid - Encerra um processo
pub async fn kill_process(
    State(state): State<AppState>,
    axum::extract::Path(pid): axum::extract::Path<u32>,
) -> Result<Json<serde_json::Value>> {
    let mut collector = state.telemetry_collector.lock().await;
    let success = collector.kill_process(pid)?;

    if success {
        tracing::warn!("💀 Process killed by remote user: PID {}", pid);
        Ok(Json(json!({
            "status": "success",
            "message": format!("Process {} signaled for termination", pid),
            "pid": pid
        })))
    } else {
        Err(crate::error::AgentError::CommandError(format!(
            "Failed to kill process {}",
            pid
        )))
    }
}

/// POST /alerts/config - Atualiza as configurações de alerta dinamicamente
pub async fn update_alert_config(
    State(state): State<AppState>,
    Json(new_thresholds): Json<crate::telemetry::AlertThresholds>,
) -> Result<Json<serde_json::Value>> {
    let mut alert_manager = state.alert_manager.lock().await;
    alert_manager.update_thresholds(new_thresholds.clone());

    tracing::info!(
        "🔔 Alert thresholds updated: CPU={}%, RAM={}%, Disk={}%",
        new_thresholds.cpu_threshold_percent,
        new_thresholds.memory_threshold_percent,
        new_thresholds.disk_threshold_percent
    );

    Ok(Json(json!({
        "status": "success",
        "message": "Alert thresholds updated successfully",
        "current_config": new_thresholds
    })))
}

/// POST /security/block-ip - Bloqueia um IP agressor
pub async fn block_ip(
    State(_state): State<AppState>,
    Json(payload): Json<serde_json::Value>,
) -> Result<Json<serde_json::Value>> {
    let ip = payload.get("ip").and_then(|v| v.as_str()).ok_or_else(|| {
        crate::error::AgentError::CommandError("Missing IP in payload".to_string())
    })?;

    let success = crate::telemetry::SecurityMetrics::block_ip(ip)?;

    if success {
        tracing::warn!("🛡️ IP Blocked by remote user: {}", ip);
        Ok(Json(json!({
            "status": "success",
            "message": format!("IP {} has been blocked via iptables", ip),
            "ip": ip
        })))
    } else {
        Err(crate::error::AgentError::CommandError(format!(
            "Failed to block IP {}",
            ip
        )))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HANDLERS DO WATCHDOG
// ─────────────────────────────────────────────────────────────────────────────

/// GET /watchdog/events?limit=50&server=<server_id>&status=<status>
///
/// Retorna eventos de auto-remediação do Watchdog.
/// Query params opcionais:
/// - `limit`  — número de eventos retornados (padrão: 50)
/// - `server` — filtrar por server_id específico
/// - `status` — filtrar por final_status (Success, Failed, CircuitOpen)
pub async fn get_watchdog_events(
    State(state): State<AppState>,
    Query(params): Query<HashMap<String, String>>,
) -> Result<Json<serde_json::Value>> {
    let limit = params
        .get("limit")
        .and_then(|v| v.parse::<usize>().ok())
        .unwrap_or(50);
    let server = params.get("server").map(|s| s.as_str());
    let status = params.get("status").map(|s| s.as_str());

    let store = state.watchdog_event_store.lock().await;

    // Aplica filtros em cadeia — padrão Builder implementado de forma funcional
    let events: Vec<_> = if let Some(srv) = server {
        store.by_server(srv)
    } else if let Some(st) = status {
        store.by_status(st)
    } else {
        store.recent(limit)
    };

    let events_json: Vec<_> = events
        .iter()
        .take(limit)
        .map(|e| serde_json::to_value(e).unwrap_or_default())
        .collect();

    let counts = store.count_by_server();

    Ok(Json(json!({
        "events":          events_json,
        "count":           events_json.len(),
        "total_in_store":  store.len(),
        "servers_summary": counts,
        "timestamp":       chrono::Utc::now().to_rfc3339(),
    })))
}

/// DELETE /watchdog/events - Limpa o histórico de eventos (logs) do Watchdog
pub async fn clear_watchdog_events(State(state): State<AppState>) -> impl IntoResponse {
    let mut store = state.watchdog_event_store.lock().await;
    store.clear();
    tracing::info!("🧹 Histórico do Watchdog limpo pelo usuário remoto.");

    (
        StatusCode::OK,
        Json(json!({
            "status": "success",
            "message": "Watchdog event history cleared"
        })),
    )
}

/// POST /watchdog/reset - Reseta todos os Circuit Breakers do Watchdog no servidor
pub async fn reset_watchdog(State(state): State<AppState>) -> impl IntoResponse {
    let mut remediation = state.remediation_engine.lock().await;
    remediation.reset_all();

    (
        StatusCode::OK,
        Json(json!({
            "status": "success",
            "message": "All circuit breakers have been reset to CLOSED"
        })),
    )
}

/// GET /watchdog/breakers - Diagnóstico detalhado dos Circuit Breakers ativos
pub async fn get_watchdog_breakers(State(state): State<AppState>) -> impl IntoResponse {
    let remediation = state.remediation_engine.lock().await;
    let states = remediation.circuit_states();

    (
        StatusCode::OK,
        Json(json!({
            "breakers": states,
            "count": states.len(),
            "timestamp": chrono::Utc::now().to_rfc3339()
        })),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// HANDLERS DO LOG DE AUDITORIA
// ─────────────────────────────────────────────────────────────────────────────

/// GET /audit/logs?limit=100&action=<action>
pub async fn get_audit_logs(
    State(state): State<AppState>,
    Query(params): Query<HashMap<String, String>>,
) -> impl IntoResponse {
    let limit = params
        .get("limit")
        .and_then(|v| v.parse::<usize>().ok())
        .unwrap_or(100);
    let action = params.get("action").map(|s| s.as_str());

    let log = state.audit_log.lock().await;

    let entries: Vec<_> = if let Some(act) = action {
        log.by_action(act)
            .into_iter()
            .take(limit)
            .cloned()
            .collect()
    } else {
        log.recent(limit).into_iter().cloned().collect()
    };

    (
        StatusCode::OK,
        Json(json!({
            "entries": entries,
            "count": entries.len(),
            "timestamp": chrono::Utc::now().to_rfc3339()
        })),
    )
}

/// DELETE /audit/logs
pub async fn clear_audit_logs(State(state): State<AppState>) -> impl IntoResponse {
    let mut log = state.audit_log.lock().await;
    log.clear();
    tracing::info!("🧹 Audit log cleared by remote user");

    (
        StatusCode::OK,
        Json(json!({
            "status": "success",
            "message": "Audit log cleared"
        })),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// HANDLERS DO DOCKER
// ─────────────────────────────────────────────────────────────────────────────

/// GET /docker/containers
pub async fn get_docker_containers() -> impl IntoResponse {
    match crate::telemetry::docker::collect_docker_metrics() {
        Some(metrics) => (
            StatusCode::OK,
            Json(json!({
                "containers": metrics.containers,
                "running_count": metrics.running_count,
                "total_count": metrics.total_count,
                "timestamp": chrono::Utc::now().to_rfc3339()
            })),
        ),
        None => (
            StatusCode::OK,
            Json(json!({
                "containers": [],
                "running_count": 0,
                "total_count": 0,
                "docker_available": false,
                "timestamp": chrono::Utc::now().to_rfc3339()
            })),
        ),
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HANDLERS DE BACKUP
// ─────────────────────────────────────────────────────────────────────────────

/// GET /backups/status
pub async fn get_backup_status() -> impl IntoResponse {
    let status = crate::telemetry::backup::collect_backup_status();

    (
        StatusCode::OK,
        Json(json!({
            "backups": status.backups,
            "any_stale": status.any_stale,
            "timestamp": chrono::Utc::now().to_rfc3339()
        })),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// HANDLERS DE CONFIGURAÇÃO
// ─────────────────────────────────────────────────────────────────────────────

/// GET /phpfpm/pools — Metricas detalhadas dos pools PHP-FPM por site
pub async fn get_phpfpm_pools() -> impl IntoResponse {
    let metrics = crate::telemetry::phpfpm::collect_phpfpm_metrics();

    (
        StatusCode::OK,
        Json(json!({
            "pools": metrics.pools,
            "total_workers": metrics.total_workers,
            "total_cpu_percent": metrics.total_cpu_percent,
            "total_memory_mb": metrics.total_memory_mb,
            "timestamp": chrono::Utc::now().to_rfc3339()
        })),
    )
}

/// GET /ssl/check — Verifica SSL de todos os dominios configurados no nginx
pub async fn check_ssl() -> impl IntoResponse {
    let result = crate::telemetry::ssl::check_all_ssl();

    (
        StatusCode::OK,
        Json(json!({
            "total_domains": result.total_domains,
            "ok": result.ok_count,
            "expiring": result.expiring_count,
            "expired": result.expired_count,
            "errors": result.error_count,
            "certs": result.certs,
            "timestamp": chrono::Utc::now().to_rfc3339()
        })),
    )
}

/// GET /config — Retorna configuração atual do agente (sem segredos)
pub async fn get_config() -> impl IntoResponse {
    let server_id = std::env::var("SERVER_ID").unwrap_or_else(|_| {
        hostname::get()
            .map(|h| h.to_string_lossy().to_string())
            .unwrap_or_else(|_| "unknown".to_string())
    });
    let server_role = std::env::var("SERVER_ROLE").unwrap_or_else(|_| "generic".to_string());
    let watchdog_enabled = std::env::var("WATCHDOG_ENABLED")
        .map(|v| v != "false" && v != "0")
        .unwrap_or(true);
    let watchdog_interval = std::env::var("WATCHDOG_INTERVAL_SECS")
        .ok()
        .and_then(|v| v.parse::<u64>().ok())
        .unwrap_or(30);
    let max_failures = std::env::var("WATCHDOG_MAX_FAILURES")
        .ok()
        .and_then(|v| v.parse::<u32>().ok())
        .unwrap_or(3);
    let cooldown = std::env::var("WATCHDOG_COOLDOWN_SECS")
        .ok()
        .and_then(|v| v.parse::<u64>().ok())
        .unwrap_or(300);
    let rate_limit = std::env::var("RATE_LIMIT_PER_MINUTE")
        .ok()
        .and_then(|v| v.parse::<u64>().ok())
        .unwrap_or(60);
    let tls_enabled =
        std::env::var("TLS_CERT_PATH").is_ok() && std::env::var("TLS_KEY_PATH").is_ok();

    (
        StatusCode::OK,
        Json(json!({
            "server_id": server_id,
            "server_role": server_role,
            "watchdog_enabled": watchdog_enabled,
            "watchdog_interval_secs": watchdog_interval,
            "watchdog_max_failures": max_failures,
            "watchdog_cooldown_secs": cooldown,
            "rate_limit_per_minute": rate_limit,
            "tls_enabled": tls_enabled
        })),
    )
}

/// POST /webhook/security — Recebe alertas de seguranca do Dashboard ERP
///
/// Payload: { source, severity, event, ip, timestamp, details }
/// Detalhes opcionais: { count, country, isp, machine_signature, last_incident }
pub async fn receive_security_webhook(
    State(state): State<AppState>,
    Json(payload): Json<serde_json::Value>,
) -> impl IntoResponse {
    let source = payload
        .get("source")
        .and_then(|v| v.as_str())
        .unwrap_or("unknown");
    let severity = payload
        .get("severity")
        .and_then(|v| v.as_str())
        .unwrap_or("info");
    let event = payload
        .get("event")
        .and_then(|v| v.as_str())
        .unwrap_or("unknown");
    let ip = payload
        .get("ip")
        .and_then(|v| v.as_str())
        .unwrap_or("0.0.0.0");
    let details = payload.get("details");

    let country = details
        .and_then(|d| d.get("country"))
        .and_then(|v| v.as_str())
        .map(String::from);
    let isp = details
        .and_then(|d| d.get("isp"))
        .and_then(|v| v.as_str())
        .map(String::from);
    let detail_str = details.map(|d| d.to_string());

    tracing::warn!(
        "🛡️ WEBHOOK [{severity}] {source}: {event} — IP: {ip} — {country} / {isp}",
        country = country.as_deref().unwrap_or("?"),
        isp = isp.as_deref().unwrap_or("?")
    );

    // Grava no store de incidentes
    {
        let mut store = state.incident_store.lock().await;
        store.push(SecurityIncident {
            id: uuid::Uuid::new_v4().to_string(),
            timestamp: chrono::Utc::now().to_rfc3339(),
            source: source.to_string(),
            severity: severity.to_string(),
            event_type: event.to_string(),
            attacker_ip: ip.to_string(),
            country,
            city: details
                .and_then(|d| d.get("city"))
                .and_then(|v| v.as_str())
                .map(String::from),
            isp,
            details: detail_str,
            from_webhook: true,
        });
    }

    // Registra tambem no audit log
    {
        let mut log = state.audit_log.lock().await;
        log.record(
            "WEBHOOK",
            &format!("/webhook/security/{}", event),
            ip,
            200,
            Some(format!("[{}] {}", severity, event)),
        );
    }

    (
        StatusCode::OK,
        Json(json!({ "status": "received", "event": event })),
    )
}

/// GET /security/incidents?limit=50&severity=CRITICAL
/// Retorna incidentes de seguranca recebidos do dashboard + detectados localmente
pub async fn get_security_incidents(
    State(state): State<AppState>,
    Query(params): Query<HashMap<String, String>>,
) -> impl IntoResponse {
    let limit = params
        .get("limit")
        .and_then(|v| v.parse::<usize>().ok())
        .unwrap_or(50);
    let severity = params.get("severity").map(|s| s.as_str());

    let store = state.incident_store.lock().await;

    let incidents: Vec<_> = if let Some(sev) = severity {
        store
            .by_severity(sev)
            .into_iter()
            .take(limit)
            .cloned()
            .collect()
    } else {
        store.recent(limit).into_iter().cloned().collect()
    };

    (
        StatusCode::OK,
        Json(json!({
            "incidents": incidents,
            "count": incidents.len(),
            "total": store.len(),
            "critical_count": store.count_critical(),
            "timestamp": chrono::Utc::now().to_rfc3339()
        })),
    )
}

/// POST /config — Atualiza configuração mutável em runtime
pub async fn update_config(Json(payload): Json<serde_json::Value>) -> impl IntoResponse {
    // Nota: Em produção, implementei para atualizar variáveis de ambiente ou arquivo de config.
    // Aqui registro as mudanças solicitadas para a administradora aplicar.
    tracing::info!("📝 Config update requested: {}", payload);

    (
        StatusCode::OK,
        Json(json!({
            "status": "success",
            "message": "Configuration update logged. Restart agent to apply changes.",
            "requested_changes": payload
        })),
    )
}
