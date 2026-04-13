package com.pocketnoc.data.models

import com.google.gson.annotations.SerializedName

// ==================== Telemetria do Sistema ====================

data class SystemTelemetry(
    val cpu: CpuMetrics,
    val memory: MemoryMetrics,
    val disk: DiskMetrics,
    val temperature: TemperatureMetrics?,
    val network: NetworkMetrics,
    val security: SecurityMetrics,
    val processes: ProcessMetrics,
    val uptime: UptimeInfo,
    val services: List<ServiceInfo>,
    val timestamp: Long
)

data class NetworkMetrics(
    val interfaces: List<InterfaceMetrics>
)

data class InterfaceMetrics(
    val name: String,
    @SerializedName("rx_bytes")
    val rxBytes: Long,
    @SerializedName("tx_bytes")
    val txBytes: Long,
    @SerializedName("rx_packets")
    val rxPackets: Long,
    @SerializedName("tx_packets")
    val txPackets: Long,
    @SerializedName("rx_errors")
    val rxErrors: Long,
    @SerializedName("tx_errors")
    val txErrors: Long
)

data class ProcessMetrics(
    @SerializedName("top_processes")
    val topProcesses: List<ProcessInfo>
)

data class ProcessInfo(
    val pid: Int,
    val name: String,
    @SerializedName("cpu_usage")
    val cpuUsage: Float,
    @SerializedName("memory_mb")
    val memoryMb: Long
)

data class SecurityMetrics(
    @SerializedName("active_ssh_sessions")
    val activeSshSessions: Int,
    @SerializedName("failed_login_attempts")
    val failedLoginAttempts: Int,
    @SerializedName("failed_logins")
    val failedLogins: List<FailedLogin>,
    @SerializedName("suspicious_activities")
    val suspiciousActivities: List<String>
)

data class FailedLogin(
    val ip: String,
    val count: Int,
    @SerializedName("last_attempt")
    val lastAttempt: String
)

data class ProcessListResponse(
    val processes: List<ProcessInfo>,
    val timestamp: String
)

data class LogResponse(
    val service: String,
    val logs: String,
    val lines: Int,
    val timestamp: String
)

data class CpuMetrics(
    @SerializedName("usage_percent")
    val usagePercent: Float,
    @SerializedName("core_count")
    val coreCount: Int,
    val cores: List<CoreMetrics>,
    @SerializedName("frequency_mhz")
    val frequencyMhz: Long
)

data class CoreMetrics(
    val index: Int,
    @SerializedName("usage_percent")
    val usagePercent: Float
)

data class MemoryMetrics(
    @SerializedName("usage_percent")
    val usagePercent: Float,
    @SerializedName("used_mb")
    val usedMb: Long,
    @SerializedName("total_mb")
    val totalMb: Long,
    @SerializedName("swap_used_mb")
    val swapUsedMb: Long,
    @SerializedName("swap_total_mb")
    val swapTotalMb: Long
)

data class DiskMetrics(
    val disks: List<DiskInfo>
)

data class DiskInfo(
    @SerializedName("mount_point")
    val mountPoint: String,
    @SerializedName("used_gb")
    val usedGb: Double,
    @SerializedName("total_gb")
    val totalGb: Double,
    @SerializedName("usage_percent")
    val usagePercent: Float,
    val filesystem: String
)

data class TemperatureMetrics(
    val sensors: List<TemperatureSensor>
)

data class TemperatureSensor(
    val name: String,
    val celsius: Float
)

data class UptimeInfo(
    @SerializedName("uptime_seconds")
    val uptimeSeconds: Long,
    @SerializedName("load_average")
    val loadAverage: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UptimeInfo

        if (uptimeSeconds != other.uptimeSeconds) return false
        if (!loadAverage.contentEquals(other.loadAverage)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = uptimeSeconds.hashCode()
        result = 31 * result + loadAverage.contentHashCode()
        return result
    }
}

// ==================== Servicos ====================

data class ServiceInfo(
    val name: String,
    val status: ServiceStatus,
    val description: String?,
    val pid: Long?
)

enum class ServiceStatus {
    @SerializedName("active")
    ACTIVE,
    @SerializedName("inactive")
    INACTIVE,
    @SerializedName("unknown")
    UNKNOWN
}

// ==================== Comandos ====================

data class EmergencyCommand(
    val id: String,
    val description: String,
    val command: String,
    val args: List<String>
)

data class CommandListResponse(
    val commands: List<EmergencyCommand>
)

// Alias mantido para compatibilidade
data class CommandInfo(
    val id: String,
    val description: String,
    val command: String = "",
    val args: List<String> = emptyList(),
    val timeout: Int = 0
)

data class CommandResult(
    @SerializedName("command_id")
    val commandId: String,
    @SerializedName("exit_code")
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timestamp: Long
)

// ==================== Verificacao de Saude ====================

data class HealthCheckResponse(
    val status: String,
    val service: String,
    val timestamp: String
)

// ==================== Configuracao do Servidor ====================

data class ServerConfig(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 9443,
    val token: String,
    val isFavorite: Boolean = false
)
// ==================== Alertas ====================

enum class AlertType {
    @SerializedName("highcpu")
    HIGH_CPU,
    @SerializedName("highmemory")
    HIGH_MEMORY,
    @SerializedName("highdisk")
    HIGH_DISK,
    @SerializedName("hightemperature")
    HIGH_TEMPERATURE,
    @SerializedName("securitythreat")
    SECURITY_THREAT,
    @SerializedName("recentreboot")
    RECENT_REBOOT
}

data class Alert(
    @SerializedName("alert_type")
    val alertType: AlertType,
    val message: String,
    @SerializedName("current_value")
    val currentValue: Float,
    val threshold: Float,
    val timestamp: Long,
    val component: String? = null
)

data class AlertsResponse(
    val alerts: List<Alert>,
    val count: Int,
    val timestamp: String
)

data class AlertThresholdConfig(
    @SerializedName("limit_cpu")
    val limitCpu: Float,
    @SerializedName("limit_memory")
    val limitMemory: Float,
    @SerializedName("limit_disk")
    val limitDisk: Float,
    @SerializedName("limit_temp")
    val limitTemp: Float
)

// ==================== Status de Saúde ====================

enum class HealthStatus {
    HEALTHY,      // Azul — tudo OK
    WARNING,      // Verde — uso elevado
    ALERT,        // Amarelo — alerta
    CRITICAL      // Vermelho — critico
}

data class ServerHealth(
    val serverId: Int,
    val serverName: String,
    val status: HealthStatus,
    val cpuUsage: Float,
    val memoryUsage: Float,
    val diskUsage: Float,
    val temperature: Float?,
    val activeAlerts: Int,
    val lastUpdate: Long
)

// ==================== Respostas Genericas ====================

data class GenericResponse(
    val status: String,
    val message: String,
    @SerializedName("current_config")
    val currentConfig: Map<String, Any>? = null
)

// ==================== Audit Log ====================

data class AuditEntry(
    val id: String,
    val timestamp: String,
    val action: String,
    @SerializedName("source_ip")
    val sourceIp: String,
    val endpoint: String,
    val method: String,
    @SerializedName("status_code")
    val statusCode: Int,
    val details: String? = null
)

data class AuditLogResponse(
    val entries: List<AuditEntry>,
    val count: Int,
    val timestamp: String
)

// ==================== Docker ====================

data class DockerContainer(
    val id: String,
    val name: String,
    val image: String,
    val status: String,
    val state: String,
    val created: String,
    val ports: List<String> = emptyList()
)

data class DockerMetrics(
    val containers: List<DockerContainer>,
    @SerializedName("running_count")
    val runningCount: Int,
    @SerializedName("total_count")
    val totalCount: Int
)

// ==================== Backup Status ====================

data class BackupInfo(
    val path: String,
    @SerializedName("last_modified")
    val lastModified: String,
    @SerializedName("age_hours")
    val ageHours: Double,
    @SerializedName("size_bytes")
    val sizeBytes: Long,
    @SerializedName("is_stale")
    val isStale: Boolean
)

data class BackupStatus(
    val backups: List<BackupInfo>,
    @SerializedName("any_stale")
    val anyStale: Boolean
)

// ==================== Dashboard ERP — Incidentes de Seguranca ====================

data class DashboardIncident(
    val id: Int,
    val severity: String,
    @SerializedName("incident_type")
    val type: String,
    @SerializedName("action_taken")
    val action: String?,
    @SerializedName("ip_address")
    val ip: String,
    val country: String?,
    val city: String?,
    val isp: String?,
    val path: String,
    val method: String,
    @SerializedName("is_banned")
    val isBanned: Boolean,
    @SerializedName("user_agent")
    val userAgent: String?,
    @SerializedName("machine_signature")
    val machineSignature: String?,
    @SerializedName("created_at")
    val createdAt: String?
)

data class DashboardIncidentsResponse(
    val data: List<DashboardIncident>,
    val total: Int,
    val limit: Int,
    val offset: Int
) {
    // Alias para manter compatibilidade com a tela
    val incidents: List<DashboardIncident> get() = data
    val count: Int get() = data.size
}

data class TopAttackerIp(
    val ip: String,
    val count: Int,
    val country: String?,
    val city: String? = null,
    val isp: String? = null,
    @SerializedName("last_type")
    val lastType: String? = null,
    @SerializedName("is_banned")
    val isBanned: Boolean = false,
    @SerializedName("known_user")
    val knownUser: Map<String, Any?>? = null
)

data class DashboardStatsResponse(
    @SerializedName("total_incidents")
    val total: Int,
    @SerializedName("unique_ips")
    val uniqueIps: Int = 0,
    @SerializedName("banned_ips")
    val bannedCount: Int,
    @SerializedName("by_severity")
    val bySeverity: Map<String, Int>,
    @SerializedName("by_type")
    val byType: Map<String, Int>,
    @SerializedName("by_action")
    val byAction: Map<String, Int> = emptyMap(),
    @SerializedName("by_country")
    val byCountry: Map<String, Int> = emptyMap(),
    @SerializedName("top_ips")
    val topIps: List<TopAttackerIp>,
    @SerializedName("top_paths")
    val topPaths: List<Map<String, Any>> = emptyList(),
    val daily: List<Map<String, Any>> = emptyList()
)

// ==================== PHP-FPM Pools ====================

data class PhpFpmPool(
    @SerializedName("pool_name")
    val poolName: String,
    @SerializedName("cpu_percent")
    val cpuPercent: Float,
    @SerializedName("memory_mb")
    val memoryMb: Float,
    @SerializedName("worker_count")
    val workerCount: Int
)

data class PhpFpmResponse(
    val pools: List<PhpFpmPool>,
    @SerializedName("total_workers")
    val totalWorkers: Int,
    @SerializedName("total_cpu_percent")
    val totalCpuPercent: Float,
    @SerializedName("total_memory_mb")
    val totalMemoryMb: Float
)

// ==================== SSL Check ====================

data class SslCertStatus(
    val domain: String,
    val valid: Boolean,
    @SerializedName("days_remaining")
    val daysRemaining: Int,
    val issuer: String,
    val subject: String,
    @SerializedName("expiry_date")
    val expiryDate: String,
    val status: String  // "ok", "expiring", "expired", "wrong_cert", "error", "no_cert"
)

data class SslCheckResponse(
    @SerializedName("total_domains")
    val totalDomains: Int,
    val ok: Int,
    val expiring: Int,
    val expired: Int,
    val errors: Int,
    val certs: List<SslCertStatus>
)

// ==================== Agent Runtime Config ====================

data class AgentRuntimeConfig(
    @SerializedName("server_id")
    val serverId: String,
    @SerializedName("server_role")
    val serverRole: String,
    @SerializedName("watchdog_enabled")
    val watchdogEnabled: Boolean,
    @SerializedName("watchdog_interval_secs")
    val watchdogIntervalSecs: Long,
    @SerializedName("watchdog_max_failures")
    val watchdogMaxFailures: Int,
    @SerializedName("watchdog_cooldown_secs")
    val watchdogCooldownSecs: Long,
    @SerializedName("rate_limit_per_minute")
    val rateLimitPerMinute: Int,
    @SerializedName("tls_enabled")
    val tlsEnabled: Boolean
)

// ==================== Watchdog / Auto-Remediação ====================

/**
 * Espelho exato do struct `WatchdogEvent` do agente Rust.
 * Cada campo usa `@SerializedName` para mapear o snake_case do JSON.
 *
 * Campos-chave para multi-servidor:
 * - `serverId`   -> identifica qual maquina gerou o evento
 * - `serverRole` -> perfil (wordpress/erp/database/generic)
 */
data class WatchdogEvent(
    val id: String,
    val timestamp: Long,
    @SerializedName("timestamp_iso")
    val timestampIso: String,

    // Identidade do servidor — critico para multi-servidor
    @SerializedName("server_id")
    val serverId: String,
    @SerializedName("server_role")
    val serverRole: String,
    @SerializedName("server_hostname")
    val serverHostname: String,

    // Diagnostico do probe
    val service: String,
    @SerializedName("probe_result")
    val probeResult: String,   // "Healthy" | "Degraded" | "Down"
    @SerializedName("probe_latency_ms")
    val probeLatencyMs: Long?,

    // Remediacao
    @SerializedName("action_taken")
    val actionTaken: String,   // "RestartService(nginx)" | "EscalateToHuman" | ...
    @SerializedName("final_status")
    val finalStatus: String,   // "Success" | "Failed" | "CircuitOpen" | "NotNeeded"
    val attempts: Int,
    @SerializedName("circuit_open")
    val circuitOpen: Boolean,

    val message: String
)

/** Resposta do endpoint `GET /watchdog/events`. */
data class WatchdogEventsResponse(
    val events: List<WatchdogEvent>,
    val count: Int,
    @SerializedName("total_in_store")
    val totalInStore: Int,
    @SerializedName("servers_summary")
    val serversSummary: Map<String, Int>,
    val timestamp: String
)
