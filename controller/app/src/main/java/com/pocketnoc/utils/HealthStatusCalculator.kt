package com.pocketnoc.utils

import com.pocketnoc.data.models.HealthStatus
import com.pocketnoc.data.models.SystemTelemetry

/**
 * Calcula o status de saúde do servidor baseado na telemetria
 */
object HealthStatusCalculator {

    /**
     * Calcula o status agregado do servidor
     *
     * Lógica:
     * - HEALTHY (Azul): CPU < 60%, RAM < 70%, Disco < 80%
     * - WARNING (Verde): CPU 60-75%, RAM 70-80%, Disco 80-85%
     * - ALERT (Amarelo): CPU 75-85%, RAM 80-90%, Disco 85-90%
     * - CRITICAL (Vermelho): CPU > 85%, RAM > 90%, Disco > 90%, ou temperatura > 75°C
     */
    fun calculateStatus(telemetry: SystemTelemetry): HealthStatus {
        val cpuStatus = classifyMetric(telemetry.cpu.usagePercent, 60f, 75f, 85f)
        val memoryStatus = classifyMetric(telemetry.memory.usagePercent, 70f, 80f, 90f)
        val diskStatus = classifyMetricForDisk(telemetry.disk)
        val tempStatus = telemetry.temperature?.let {
            classifyTemperature(it.sensors.maxOfOrNull { sensor -> sensor.celsius } ?: 50f)
        } ?: HealthStatus.HEALTHY

        // Retorna o pior status encontrado
        return listOf(cpuStatus, memoryStatus, diskStatus, tempStatus).maxByOrNull { it.priority }
            ?: HealthStatus.HEALTHY
    }

    /**
     * Classifica uma métrica em 4 faixas
     */
    private fun classifyMetric(
        value: Float,
        healthyThreshold: Float,
        warningThreshold: Float,
        alertThreshold: Float
    ): HealthStatus {
        return when {
            value < healthyThreshold -> HealthStatus.HEALTHY
            value < warningThreshold -> HealthStatus.WARNING
            value < alertThreshold -> HealthStatus.ALERT
            else -> HealthStatus.CRITICAL
        }
    }

    /**
     * Classifica disco especificamente (maior entre todos os discos)
     */
    private fun classifyMetricForDisk(disk: com.pocketnoc.data.models.DiskMetrics): HealthStatus {
        val maxDiskUsage = disk.disks.maxOfOrNull { it.usagePercent } ?: 0f
        return classifyMetric(maxDiskUsage, 80f, 85f, 90f)
    }

    /**
     * Classifica temperatura
     */
    private fun classifyTemperature(celsius: Float): HealthStatus {
        return when {
            celsius < 60f -> HealthStatus.HEALTHY
            celsius < 70f -> HealthStatus.WARNING
            celsius < 75f -> HealthStatus.ALERT
            else -> HealthStatus.CRITICAL
        }
    }
}

// Extension para acessar a prioridade do status
internal val HealthStatus.priority: Int
    get() = when (this) {
        HealthStatus.HEALTHY -> 0
        HealthStatus.WARNING -> 1
        HealthStatus.ALERT -> 2
        HealthStatus.CRITICAL -> 3
    }
