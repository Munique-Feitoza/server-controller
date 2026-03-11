package com.pocketnoc.data.models

import com.google.gson.annotations.SerializedName

// ==================== Telemetria ====================

data class SystemTelemetry(
    val cpu: CpuMetrics,
    val memory: MemoryMetrics,
    val disk: DiskMetrics,
    val temperature: TemperatureMetrics?,
    val uptime: UptimeInfo,
    val timestamp: Long
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
    ACTIVE, INACTIVE, UNKNOWN
}

// ==================== Comandos ====================

data class EmergencyCommand(
    val id: String,
    val description: String,
    val command: String,
    val args: List<String>
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
