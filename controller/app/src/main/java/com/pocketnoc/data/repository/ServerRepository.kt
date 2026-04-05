package com.pocketnoc.data.repository

import com.pocketnoc.data.api.AgentApiService
import com.pocketnoc.data.api.RetrofitClient
import com.pocketnoc.data.local.dao.ServerDao
import com.pocketnoc.data.local.dao.TelemetryHistoryDao
import com.pocketnoc.data.local.SecureTokenManager
import com.pocketnoc.data.local.entities.ServerEntity
import com.pocketnoc.data.local.entities.TelemetryHistoryEntity
import com.pocketnoc.data.models.CommandResult
import com.pocketnoc.data.models.EmergencyCommand
import com.pocketnoc.data.models.SystemTelemetry
import com.pocketnoc.data.models.AlertsResponse
import com.pocketnoc.config.PocketNOCConfig
import com.pocketnoc.ssh.SshTunnelManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.*
import com.pocketnoc.utils.SecurityNotificationManager
import javax.inject.Inject
import javax.inject.Singleton
import com.pocketnoc.data.local.dao.AlertDao
import com.pocketnoc.data.local.entities.AlertEntity
import java.util.concurrent.ConcurrentHashMap

@Singleton
class ServerRepository @Inject constructor(
    private val serverDao: ServerDao,
    private val alertDao: AlertDao,
    private val telemetryHistoryDao: TelemetryHistoryDao,
    private val securityNotifications: SecurityNotificationManager,
    private val secureTokenManager: SecureTokenManager
) {
    private val apiCache = ConcurrentHashMap<Int, AgentApiService>()
    fun getAllServers(): Flow<List<ServerEntity>> = serverDao.getAllServers()

    suspend fun getServerById(id: Int): ServerEntity? = serverDao.getServerById(id)

    suspend fun addServer(server: ServerEntity) = serverDao.insertServer(server).also {
        // Salva credenciais de forma segura no armazenamento criptografado
        secureTokenManager.saveToken(server.id, server.token)
        server.secret?.let { secret ->
            secureTokenManager.saveSecret(server.id, secret)
        }
        server.sshKeyPath?.let { keyContent ->
            secureTokenManager.saveSshKey(server.id, keyContent)
        }
    }

    suspend fun updateServer(server: ServerEntity) = serverDao.updateServer(server).also {
        // Atualiza credenciais de forma segura no armazenamento criptografado
        secureTokenManager.saveToken(server.id, server.token)
        server.secret?.let { secret ->
            secureTokenManager.saveSecret(server.id, secret)
        }
        server.sshKeyPath?.let { keyContent ->
            secureTokenManager.saveSshKey(server.id, keyContent)
        }
        // Invalida cache da API
        apiCache.remove(server.id)
    }

    suspend fun deleteServer(server: ServerEntity) {
        serverDao.deleteServer(server)
        // Remove credenciais armazenadas do servidor
        secureTokenManager.clearServerCredentials(server.id)
        // Invalida cache da API
        apiCache.remove(server.id)
    }

    private suspend fun getApiService(server: ServerEntity): AgentApiService {
        // Recupera a chave SSH do gerenciador de credenciais seguro
        val sshKeyContent = secureTokenManager.getSshKey(server.id)
        
        // Garante que o tunel SSH esta ativo se tivermos dados configurados
        if (server.sshHost != null && server.sshUser != null && sshKeyContent != null && server.localPort != null) {
            val tunnelResult = SshTunnelManager.startTunnel(
                serverId = server.id,
                host = server.sshHost,
                user = server.sshUser,
                privateKeyContent = sshKeyContent,
                localPort = server.localPort,
                sshPort = server.sshPort ?: 22,
                remotePort = server.remotePort ?: 9443
            )
            
            if (tunnelResult.isFailure) {
                val errorMsg = tunnelResult.exceptionOrNull()?.message ?: "Unknown SSH Error"
                
                // Trata alertas de seguranca do SshTunnelManager
                if (errorMsg.contains("ALERTA DE SEGURANÇA")) {
                    val failures = SshTunnelManager.getAuthFailures(server.id)
                    securityNotifications.sendIntrusionAlert(server.name, failures)
                }

                throw Exception("SSH Tunnel Failure: $errorMsg")
            }
        }
        
        val url = if (server.localPort != null) "http://localhost:${server.localPort}" else server.url
        
        // Geracao dinamica de token para garantir sincronia com local.properties e mitigar expiracao
        val currentSecret = server.secret ?: PocketNOCConfig.secret
        val dynamicToken = com.pocketnoc.utils.JwtUtils.generateToken(currentSecret)
        
        return apiCache.getOrPut(server.id) {
            RetrofitClient.getInstance(url, dynamicToken).create(AgentApiService::class.java)
        }
    }

    suspend fun getTelemetry(server: ServerEntity): SystemTelemetry = withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val apiService = getApiService(server)
            val telemetry = apiService.getTelemetry()
            
            // Verifica o uso de CPU para disparar alertas de seguranca
            if (telemetry.cpu.usagePercent > PocketNOCConfig.maxCpuThreshold) {
                securityNotifications.sendHighCpuAlert(server.name, telemetry.cpu.usagePercent.toDouble())
            }
            
            telemetry
        } catch (e: Exception) {
            throw Exception("Agent Connection Error: ${e.message}")
        }
    }

    suspend fun rebootServer(server: ServerEntity) {
        val apiService = getApiService(server)
        apiService.reboot()
    }

    suspend fun performServiceAction(server: ServerEntity, serviceName: String, action: String): CommandResult {
        val apiService = getApiService(server)
        return apiService.performServiceAction(serviceName, action)
    }

    suspend fun executeCommand(server: ServerEntity, commandId: String): CommandResult {
        val apiService = getApiService(server)
        return apiService.executeCommand(commandId)
    }

    suspend fun listCommands(server: ServerEntity): Map<String, List<EmergencyCommand>> {
        val apiService = getApiService(server)
        return apiService.listCommands().commands
    }

    suspend fun getAlerts(server: ServerEntity): AlertsResponse = withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val apiService = getApiService(server)
            apiService.getAlerts()
        } catch (e: Exception) {
            throw Exception("Failed to fetch alerts: ${e.message}")
        }
    }

    suspend fun updateAlertConfig(server: ServerEntity, config: com.pocketnoc.data.local.AlertThresholdConfig) = withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val apiService = getApiService(server)
            // Mapeia configuracao local para o modelo da API
            val apiConfig = com.pocketnoc.data.models.AlertThresholdConfig(
                limitCpu = config.cpuThresholdPercent,
                limitMemory = config.memoryThresholdPercent,
                limitDisk = config.diskThresholdPercent,
                limitTemp = config.temperatureThresholdCelsius
            )
            apiService.updateAlertConfig(apiConfig)
        } catch (e: Exception) {
            throw Exception("Failed to sync alerts with server: ${e.message}")
        }
    }

    suspend fun listProcesses(server: ServerEntity): List<com.pocketnoc.data.models.ProcessInfo> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val apiService = getApiService(server)
            apiService.getTopProcesses().processes
        } catch (e: Exception) {
            throw Exception("Failed to fetch processes: ${e.message}")
        }
    }

    suspend fun killProcess(server: ServerEntity, pid: Long) {
        try {
            val apiService = getApiService(server)
            apiService.killProcess(pid)
        } catch (e: Exception) {
            throw Exception("Failed to kill process $pid: ${e.message}")
        }
    }

    suspend fun fetchLogs(server: ServerEntity, service: String, lines: Int = 100): com.pocketnoc.data.models.LogResponse {
        try {
            val apiService = getApiService(server)
            return apiService.getLogs(service, lines)
        } catch (e: Exception) {
            throw Exception("Failed to fetch logs: ${e.message}")
        }
    }

    suspend fun blockIp(server: ServerEntity, ip: String) {
        try {
            val apiService = getApiService(server)
            apiService.blockIp(mapOf("ip" to ip))
        } catch (e: Exception) {
            throw Exception("Failed to block IP $ip: ${e.message}")
        }
    }

    /**
     * Busca os eventos do Watchdog do agente remoto.
     *
     * @param serverId filtro por server_id (null = todos)
     * @param status   filtro por final_status (null = todos)
     * @param limit    número máximo de eventos retornados
     */
    suspend fun getWatchdogEvents(
        server:   ServerEntity,
        serverId: String? = null,
        status:   String? = null,
        limit:    Int     = 50
    ): com.pocketnoc.data.models.WatchdogEventsResponse = withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val apiService = getApiService(server)
            apiService.getWatchdogEvents(serverId = serverId, status = status, limit = limit)
        } catch (e: Exception) {
            throw Exception("Failed to fetch watchdog events: ${e.message}")
        }
    }

    /**
     * Limpa remotamente o histórico de eventos no agente.
     */
    suspend fun clearWatchdogEvents(server: ServerEntity) = withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val apiService = getApiService(server)
            apiService.deleteWatchdogEvents()
        } catch (e: Exception) {
            throw Exception("Failed to clear watchdog logs: ${e.message}")
        }
    }

    /**
     * Reseta manualmente todos os Circuit Breakers no agente remoto.
     */
    suspend fun resetWatchdogCircuits(server: ServerEntity) = withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val apiService = getApiService(server)
            apiService.resetWatchdog()
        } catch (e: Exception) {
            throw Exception("Failed to reset watchdog circuits: ${e.message}")
        }
    }

    // Historico de Telemetria
    suspend fun saveTelemetrySnapshot(serverId: Int, telemetry: SystemTelemetry) {
        val entry = TelemetryHistoryEntity(
            serverId = serverId,
            timestamp = System.currentTimeMillis(),
            cpuPercent = telemetry.cpu.usagePercent,
            ramPercent = telemetry.memory.usagePercent,
            diskPercent = telemetry.disk.disks.maxOfOrNull { it.usagePercent } ?: 0f,
            pingLatencyMs = null,
            loadAvg1m = telemetry.uptime.loadAverage.getOrNull(0)?.toFloat() ?: 0f
        )
        telemetryHistoryDao.insert(entry)
        // Mantem apenas as ultimas 24 horas de dados por servidor
        telemetryHistoryDao.pruneOldEntries(serverId, System.currentTimeMillis() - 86_400_000L)
    }

    suspend fun getTelemetryHistory(serverId: Int, limit: Int = 60): List<TelemetryHistoryEntity> {
        return telemetryHistoryDao.getRecentHistory(serverId, limit)
    }

    // Historico de Alertas
    suspend fun saveAlert(alert: AlertEntity) {
        alertDao.insertAlert(alert)
    }

    fun getAllAlertHistory(): Flow<List<AlertEntity>> {
        return alertDao.getAllAlerts()
    }

    fun getAlertHistoryByServer(serverId: Int): Flow<List<AlertEntity>> {
        return alertDao.getAlertsByServer(serverId)
    }

    suspend fun clearAlertHistory() {
        alertDao.deleteAll()
    }

    // Log de Auditoria
    suspend fun getAuditLogs(server: ServerEntity, limit: Int = 100): List<com.pocketnoc.data.models.AuditEntry> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val apiService = getApiService(server)
            apiService.getAuditLogs(limit = limit).entries
        } catch (e: Exception) {
            throw Exception("Failed to fetch audit logs: ${e.message}")
        }
    }

    // Monitoramento Docker
    suspend fun getDockerContainers(server: ServerEntity): com.pocketnoc.data.models.DockerMetrics = withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val apiService = getApiService(server)
            apiService.getDockerContainers()
        } catch (e: Exception) {
            throw Exception("Failed to fetch Docker containers: ${e.message}")
        }
    }

    // PHP-FPM Pools
    suspend fun getPhpFpmPools(server: ServerEntity): com.pocketnoc.data.models.PhpFpmResponse = withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val apiService = getApiService(server)
            apiService.getPhpFpmPools()
        } catch (e: Exception) {
            throw Exception("Failed to fetch PHP-FPM pools: ${e.message}")
        }
    }

    // Status de Backup
    suspend fun getBackupStatus(server: ServerEntity): com.pocketnoc.data.models.BackupStatus = withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val apiService = getApiService(server)
            apiService.getBackupStatus()
        } catch (e: Exception) {
            throw Exception("Failed to fetch backup status: ${e.message}")
        }
    }

    // Configuracao do Agente
    suspend fun getAgentConfig(server: ServerEntity): com.pocketnoc.data.models.AgentRuntimeConfig = withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val apiService = getApiService(server)
            apiService.getAgentConfig()
        } catch (e: Exception) {
            throw Exception("Failed to fetch agent config: ${e.message}")
        }
    }

    suspend fun updateAgentConfig(server: ServerEntity, config: Map<String, Any>) = withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val apiService = getApiService(server)
            apiService.updateAgentConfig(config)
        } catch (e: Exception) {
            throw Exception("Failed to update agent config: ${e.message}")
        }
    }
}
