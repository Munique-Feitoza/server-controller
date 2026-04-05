package com.pocketnoc

import com.pocketnoc.data.models.*
import org.junit.Assert.*
import org.junit.Test

class ModelsTest {

    @Test
    fun `WatchdogEvent creates correctly`() {
        val event = WatchdogEvent(
            id = "test-id",
            timestamp = 1234567890L,
            timestampIso = "2024-01-01T00:00:00Z",
            serverId = "server-1",
            serverRole = "wordpress",
            serverHostname = "host1",
            service = "nginx",
            probeResult = "Down",
            probeLatencyMs = 5000L,
            actionTaken = "RestartService(nginx)",
            finalStatus = "Success",
            attempts = 2,
            circuitOpen = false,
            message = "nginx restarted"
        )
        assertEquals("server-1", event.serverId)
        assertEquals("nginx", event.service)
        assertFalse(event.circuitOpen)
    }

    @Test
    fun `AuditEntry creates correctly`() {
        val entry = AuditEntry(
            id = "audit-1",
            timestamp = "2024-01-01T00:00:00Z",
            action = "GET /telemetry",
            sourceIp = "127.0.0.1",
            endpoint = "/telemetry",
            method = "GET",
            statusCode = 200,
            details = null
        )
        assertEquals(200, entry.statusCode)
        assertEquals("GET", entry.method)
        assertNull(entry.details)
    }

    @Test
    fun `DockerContainer creates correctly`() {
        val container = DockerContainer(
            id = "abc123",
            name = "nginx-proxy",
            image = "nginx:latest",
            status = "Up 2 hours",
            state = "running",
            created = "2024-01-01",
            ports = listOf("80/tcp", "443/tcp")
        )
        assertEquals("running", container.state)
        assertEquals(2, container.ports.size)
    }

    @Test
    fun `BackupInfo stale detection`() {
        val stale = BackupInfo(
            path = "/var/backups/db.sql.gz",
            lastModified = "2024-01-01T00:00:00Z",
            ageHours = 48.0,
            sizeBytes = 1024 * 1024,
            isStale = true
        )
        assertTrue(stale.isStale)

        val fresh = BackupInfo(
            path = "/var/backups/db.sql.gz",
            lastModified = "2024-01-01T00:00:00Z",
            ageHours = 12.0,
            sizeBytes = 1024 * 1024,
            isStale = false
        )
        assertFalse(fresh.isStale)
    }

    @Test
    fun `AgentRuntimeConfig creates correctly`() {
        val config = AgentRuntimeConfig(
            serverId = "vps-01",
            serverRole = "wordpress",
            watchdogEnabled = true,
            watchdogIntervalSecs = 30,
            watchdogMaxFailures = 3,
            watchdogCooldownSecs = 300,
            rateLimitPerMinute = 60,
            tlsEnabled = false
        )
        assertEquals("vps-01", config.serverId)
        assertTrue(config.watchdogEnabled)
        assertEquals(60, config.rateLimitPerMinute)
    }

    @Test
    fun `UptimeInfo equals and hashCode`() {
        val u1 = UptimeInfo(86400, floatArrayOf(0.5f, 0.3f, 0.2f))
        val u2 = UptimeInfo(86400, floatArrayOf(0.5f, 0.3f, 0.2f))
        assertEquals(u1, u2)
        assertEquals(u1.hashCode(), u2.hashCode())
    }

    @Test
    fun `AlertType enum values`() {
        assertEquals(6, AlertType.values().size)
        assertNotNull(AlertType.HIGH_CPU)
        assertNotNull(AlertType.HIGH_MEMORY)
        assertNotNull(AlertType.SECURITY_THREAT)
    }
}
