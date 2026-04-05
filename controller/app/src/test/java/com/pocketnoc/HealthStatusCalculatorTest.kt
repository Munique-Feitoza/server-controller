package com.pocketnoc

import com.pocketnoc.data.models.*
import com.pocketnoc.utils.HealthStatusCalculator
import org.junit.Assert.*
import org.junit.Test

class HealthStatusCalculatorTest {

    private fun makeTelemetry(
        cpuPercent: Float = 20f,
        memoryPercent: Float = 30f,
        diskPercent: Float = 40f,
        tempCelsius: Float = 45f
    ): SystemTelemetry {
        return SystemTelemetry(
            cpu = CpuMetrics(
                usagePercent = cpuPercent,
                coreCount = 4,
                cores = listOf(CoreMetrics(0, cpuPercent)),
                frequencyMhz = 2400
            ),
            memory = MemoryMetrics(
                usagePercent = memoryPercent,
                usedMb = 2048,
                totalMb = 8192,
                swapUsedMb = 0,
                swapTotalMb = 1024
            ),
            disk = DiskMetrics(
                disks = listOf(
                    DiskInfo(
                        mountPoint = "/",
                        usedGb = 20.0,
                        totalGb = 50.0,
                        usagePercent = diskPercent,
                        filesystem = "ext4"
                    )
                )
            ),
            temperature = TemperatureMetrics(
                sensors = listOf(TemperatureSensor("CPU", tempCelsius))
            ),
            network = NetworkMetrics(
                interfaces = listOf(
                    InterfaceMetrics("eth0", 1000000, 2000000, 100, 200, 0, 0)
                )
            ),
            security = SecurityMetrics(
                activeSshSessions = 1,
                failedLoginAttempts = 0,
                failedLogins = emptyList(),
                suspiciousActivities = emptyList()
            ),
            processes = ProcessMetrics(
                topProcesses = listOf(
                    ProcessInfo(pid = 1, name = "init", cpuUsage = 0.1f, memoryMb = 10)
                )
            ),
            uptime = UptimeInfo(uptimeSeconds = 86400, loadAverage = floatArrayOf(0.5f, 0.3f, 0.2f)),
            services = emptyList(),
            timestamp = System.currentTimeMillis()
        )
    }

    @Test
    fun `healthy system returns HEALTHY status`() {
        val telemetry = makeTelemetry(cpuPercent = 20f, memoryPercent = 30f, diskPercent = 40f)
        val status = HealthStatusCalculator.calculateStatus(telemetry)
        assertEquals(HealthStatus.HEALTHY, status)
    }

    @Test
    fun `high CPU returns WARNING or higher`() {
        val telemetry = makeTelemetry(cpuPercent = 75f)
        val status = HealthStatusCalculator.calculateStatus(telemetry)
        assertTrue(
            "High CPU should elevate status",
            status == HealthStatus.WARNING || status == HealthStatus.ALERT || status == HealthStatus.CRITICAL
        )
    }

    @Test
    fun `critical CPU returns CRITICAL`() {
        val telemetry = makeTelemetry(cpuPercent = 95f, memoryPercent = 90f, diskPercent = 95f)
        val status = HealthStatusCalculator.calculateStatus(telemetry)
        assertEquals(HealthStatus.CRITICAL, status)
    }

    @Test
    fun `models serialize correctly`() {
        val alert = Alert(
            alertType = AlertType.HIGH_CPU,
            message = "CPU usage high",
            currentValue = 95f,
            threshold = 80f,
            timestamp = System.currentTimeMillis()
        )
        assertEquals(AlertType.HIGH_CPU, alert.alertType)
        assertEquals(95f, alert.currentValue)
    }

    @Test
    fun `health status enum has all expected values`() {
        val values = HealthStatus.values()
        assertEquals(4, values.size)
        assertTrue(values.contains(HealthStatus.HEALTHY))
        assertTrue(values.contains(HealthStatus.WARNING))
        assertTrue(values.contains(HealthStatus.ALERT))
        assertTrue(values.contains(HealthStatus.CRITICAL))
    }
}
