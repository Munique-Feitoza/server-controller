package com.pocketnoc.data.repository

import com.pocketnoc.data.AgentError
import com.pocketnoc.data.toAgentError
import com.pocketnoc.data.api.AgentApiService
import com.pocketnoc.data.api.AgentAuthInterceptor
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

    suspend fun addServer(server: ServerEntity): Long {
        val newId = serverDao.insertServer(server)
        val id = newId.toInt()
        // Salva credenciais sob o ID REAL gerado pelo Room (server.id no objeto vem 0
        // quando autoGenerate; antes do fix, todas as creds caiam no slot id=0 e umas
        // sobrescreviam as outras, derrubando SSH/JWT/secret de todos exceto o último).
        secureTokenManager.saveToken(id, server.token)
        server.secret?.let { secureTokenManager.saveSecret(id, it) }
        server.sshKeyPath?.let { secureTokenManager.saveSshKey(id, it) }
        return newId
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

    /**
     * Centraliza I/O + tratamento de erro de toda chamada ao agent. Roda em Dispatchers.IO
     * (getApiService sobe o túnel SSH e lê EncryptedSharedPreferences — ambos bloqueantes) e
     * converte qualquer falha em [AgentError] tipado. Elimina o try/catch + withContext
     * repetido em ~20 métodos.
     */
    private suspend fun <T> agentCall(block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            try {
                block()
            } catch (e: Exception) {
                throw e.toAgentError()
            }
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

                // Intrusão: o SshTunnelManager sinaliza com "ALERTA DE SEGURANÇA"
                if (errorMsg.contains("ALERTA DE SEGURANÇA")) {
                    val failures = SshTunnelManager.getAuthFailures(server.id)
                    securityNotifications.sendIntrusionAlert(server.name, failures)
                    throw AgentError.SecurityThreat(server.name, failures)
                }

                throw AgentError.Tunnel("Falha no túnel SSH: $errorMsg")
            }
        }

        val url = if (server.localPort != null) "http://localhost:${server.localPort}" else server.url

        // Cada servidor mantem seu proprio secret em EncryptedSharedPreferences.
        // Sem secret cadastrado, nao geramos token automaticamente — forca user a configurar.
        val currentSecret = server.secret
            ?: throw AgentError.Misconfigured("Servidor '${server.name}' sem secret cadastrado. Re-adicione pelo Login.")

        // O token é gerado/renovado pelo interceptor a cada request (nunca fica preso/expirado).
        return apiCache.getOrPut(server.id) {
            RetrofitClient.create(url, AgentAuthInterceptor(currentSecret)).create(AgentApiService::class.java)
        }
    }

    suspend fun getTelemetry(server: ServerEntity): SystemTelemetry = agentCall {
        val telemetry = getApiService(server).getTelemetry()
        // Verifica o uso de CPU para disparar alertas de seguranca
        if (telemetry.cpu.usagePercent > PocketNOCConfig.maxCpuThreshold) {
            securityNotifications.sendHighCpuAlert(server.name, telemetry.cpu.usagePercent.toDouble())
        }
        telemetry
    }

    suspend fun rebootServer(server: ServerEntity) = agentCall {
        getApiService(server).reboot()
    }

    suspend fun performServiceAction(server: ServerEntity, serviceName: String, action: String): CommandResult = agentCall {
        getApiService(server).performServiceAction(serviceName, action)
    }

    suspend fun executeCommand(server: ServerEntity, commandId: String): CommandResult = agentCall {
        getApiService(server).executeCommand(commandId)
    }

    suspend fun listCommands(server: ServerEntity): List<EmergencyCommand> = agentCall {
        getApiService(server).listCommands().commands
    }

    suspend fun getAlerts(server: ServerEntity): AlertsResponse = agentCall {
        getApiService(server).getAlerts()
    }

    suspend fun updateAlertConfig(server: ServerEntity, config: com.pocketnoc.data.local.AlertThresholdConfig) = agentCall {
        // Mapeia configuracao local para o modelo da API
        val apiConfig = com.pocketnoc.data.models.AlertThresholdConfig(
            limitCpu = config.cpuThresholdPercent,
            limitMemory = config.memoryThresholdPercent,
            limitDisk = config.diskThresholdPercent,
            limitTemp = config.temperatureThresholdCelsius
        )
        getApiService(server).updateAlertConfig(apiConfig)
    }

    suspend fun listProcesses(server: ServerEntity): List<com.pocketnoc.data.models.ProcessInfo> = agentCall {
        getApiService(server).getTopProcesses().processes
    }

    suspend fun killProcess(server: ServerEntity, pid: Long) = agentCall {
        getApiService(server).killProcess(pid)
    }

    suspend fun fetchLogs(server: ServerEntity, service: String, lines: Int = 100): com.pocketnoc.data.models.LogResponse = agentCall {
        getApiService(server).getLogs(service, lines)
    }

    suspend fun blockIp(server: ServerEntity, ip: String) = agentCall {
        getApiService(server).blockIp(mapOf("ip" to ip))
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
    ): com.pocketnoc.data.models.WatchdogEventsResponse = agentCall {
        getApiService(server).getWatchdogEvents(serverId = serverId, status = status, limit = limit)
    }

    /**
     * Busca os incidentes de seguranca do agente (inclui eventos `admin_created`).
     */
    suspend fun getSecurityIncidents(
        server: ServerEntity,
        limit: Int = 100
    ): com.pocketnoc.data.models.SecurityIncidentsResponse = agentCall {
        getApiService(server).getSecurityIncidents(limit = limit)
    }

    /**
     * Revoga (apaga) um administrador WordPress via agente.
     */
    suspend fun revokeAdmin(
        server: ServerEntity,
        path: String,
        userId: Long,
        incidentId: String
    ): com.pocketnoc.data.models.RevokeAdminResult = agentCall {
        getApiService(server).revokeAdmin(
            com.pocketnoc.data.models.RevokeAdminRequest(
                path = path,
                userId = userId,
                incidentId = incidentId
            )
        )
    }

    /**
     * Limpa remotamente o histórico de eventos no agente.
     */
    suspend fun clearWatchdogEvents(server: ServerEntity) = agentCall {
        getApiService(server).deleteWatchdogEvents()
    }

    /**
     * Reseta manualmente todos os Circuit Breakers no agente remoto.
     */
    suspend fun resetWatchdogCircuits(server: ServerEntity) = agentCall {
        getApiService(server).resetWatchdog()
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
    suspend fun getAuditLogs(server: ServerEntity, limit: Int = 100): List<com.pocketnoc.data.models.AuditEntry> = agentCall {
        getApiService(server).getAuditLogs(limit = limit).entries
    }

    // Monitoramento Docker
    suspend fun getDockerContainers(server: ServerEntity): com.pocketnoc.data.models.DockerMetrics = agentCall {
        getApiService(server).getDockerContainers()
    }

    // PHP-FPM Pools
    suspend fun getPhpFpmPools(server: ServerEntity): com.pocketnoc.data.models.PhpFpmResponse = agentCall {
        getApiService(server).getPhpFpmPools()
    }

    // SSL Check
    suspend fun checkSsl(server: ServerEntity): com.pocketnoc.data.models.SslCheckResponse = agentCall {
        getApiService(server).checkSsl()
    }

    // Status de Backup
    suspend fun getBackupStatus(server: ServerEntity): com.pocketnoc.data.models.BackupStatus = agentCall {
        getApiService(server).getBackupStatus()
    }

    // Configuracao do Agente
    suspend fun getAgentConfig(server: ServerEntity): com.pocketnoc.data.models.AgentRuntimeConfig = agentCall {
        getApiService(server).getAgentConfig()
    }

    suspend fun updateAgentConfig(server: ServerEntity, config: Map<String, Any>) = agentCall {
        getApiService(server).updateAgentConfig(config)
    }
}
