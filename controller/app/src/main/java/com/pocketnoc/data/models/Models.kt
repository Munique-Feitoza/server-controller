package com.pocketnoc.data.models

import com.google.gson.annotations.SerializedName

// ==================== Telemetria ====================

data class SystemTelemetry(
    val cpu: CpuMetrics,
    val memory: MemoryMetrics,
    val disk: DiskMetrics,
    val temperature: TemperatureMetrics?,
    val network: NetworkMetrics,
    val security: SecurityMetrics,
    val processes: ProcessMetrics,
    val uptime: UptimeInfo,
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
    @SerializedName("suspicious_activities")
    val suspiciousActivities: List<String>
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

// ==================== Serviços ====================

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
    val commands: Map<String, List<EmergencyCommand>>
)

// Alias para compatibilidade
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

// ==================== Health Check ====================

data class HealthCheckResponse(
    val status: String,
    val service: String,
    val timestamp: String
)

// ==================== Servidor ====================

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

// ==================== Status de Saúde ====================

enum class HealthStatus {
    HEALTHY,      // Azul - Tudo OK
    WARNING,      // Verde - Um pouco de uso demais
    ALERT,        // Amarelo - Alerta
    CRITICAL      // Vermelho - Crítico
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

// ==================== Respostas Genéricas ====================

data class GenericResponse(
    val status: String,
    val message: String,
    @SerializedName("current_config")
    val currentConfig: Map<String, Any>? = null
)
