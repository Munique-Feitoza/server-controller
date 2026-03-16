package com.pocketnoc.data.repository

import com.pocketnoc.data.api.AgentApiService
import com.pocketnoc.data.api.RetrofitClient
import com.pocketnoc.data.local.dao.ServerDao
import com.pocketnoc.data.local.SecureTokenManager
import com.pocketnoc.data.local.entities.ServerEntity
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
import com.pocketnoc.data.local.dao.AlertDao // Added import for AlertDao
import com.pocketnoc.data.local.entities.AlertEntity // Added import for AlertEntity

@Singleton
class ServerRepository @Inject constructor(
    private val serverDao: ServerDao,
    private val alertDao: AlertDao, // Added AlertDao injection
    private val securityNotifications: SecurityNotificationManager,
    private val secureTokenManager: SecureTokenManager
) {
    fun getAllServers(): Flow<List<ServerEntity>> = serverDao.getAllServers()

    suspend fun getServerById(id: Int): ServerEntity? = serverDao.getServerById(id)

    suspend fun addServer(server: ServerEntity) = serverDao.insertServer(server).also {
        // Salva credenciais de forma segura
        secureTokenManager.saveToken(server.id, server.token)
        server.secret?.let { secret ->
            secureTokenManager.saveSecret(server.id, secret)
        }
        server.sshKeyPath?.let { keyContent ->
            secureTokenManager.saveSshKey(server.id, keyContent)
        }
    }

    suspend fun updateServer(server: ServerEntity) = serverDao.updateServer(server).also {
        // Atualiza credenciais de forma segura
        secureTokenManager.saveToken(server.id, server.token)
        server.secret?.let { secret ->
            secureTokenManager.saveSecret(server.id, secret)
        }
        server.sshKeyPath?.let { keyContent ->
            secureTokenManager.saveSshKey(server.id, keyContent)
        }
    }

    suspend fun deleteServer(server: ServerEntity) {
        serverDao.deleteServer(server)
        // Remove credenciais armazenadas
        secureTokenManager.clearServerCredentials(server.id)
    }

    private suspend fun getApiService(server: ServerEntity): AgentApiService {
        // Recupera a chave SSH do gerenciador seguro
        val sshKeyContent = secureTokenManager.getSshKey(server.id)
        
        // Garante que o túnel está ativo se tivermos dados de SSH
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
                
                // Trata alertas de segurança do SshTunnelManager (HackerSec Core)
                if (errorMsg.contains("ALERTA DE SEGURANÇA")) {
                    val failures = SshTunnelManager.getAuthFailures(server.id)
                    securityNotifications.sendIntrusionAlert(server.name, failures)
                }

                throw Exception("SSH Tunnel Failure: $errorMsg")
            }
        }
        
        val url = if (server.localPort != null) "http://localhost:${server.localPort}" else server.url
        return RetrofitClient.getInstance(url, server.token).create(AgentApiService::class.java)
    }

    suspend fun getTelemetry(server: ServerEntity): SystemTelemetry = withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val apiService = getApiService(server)
            val telemetry = apiService.getTelemetry()
            
            // Verifica o uso de CPU para alertas (HackerSec Core)
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
            // Map local config to API model
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

    // Alert History
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
}
