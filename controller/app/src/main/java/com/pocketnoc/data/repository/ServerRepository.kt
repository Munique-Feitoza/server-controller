package com.pocketnoc.data.repository

import com.pocketnoc.data.api.AgentApiService
import com.pocketnoc.data.api.RetrofitClient
import com.pocketnoc.data.local.dao.ServerDao
import com.pocketnoc.data.local.entities.ServerEntity
import com.pocketnoc.data.models.CommandResult
import com.pocketnoc.data.models.EmergencyCommand
import com.pocketnoc.data.models.SystemTelemetry
import com.pocketnoc.config.PocketNOCConfig
import com.pocketnoc.ssh.SshTunnelManager
import kotlinx.coroutines.flow.Flow
import com.pocketnoc.utils.SecurityNotificationManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerRepository @Inject constructor(
    private val serverDao: ServerDao,
    private val securityNotifications: SecurityNotificationManager
) {
    fun getAllServers(): Flow<List<ServerEntity>> = serverDao.getAllServers()

    suspend fun getServerById(id: Int): ServerEntity? = serverDao.getServerById(id)

    suspend fun addServer(server: ServerEntity) = serverDao.insertServer(server)

    suspend fun updateServer(server: ServerEntity) = serverDao.updateServer(server)

    suspend fun deleteServer(server: ServerEntity) = serverDao.deleteServer(server)

    private suspend fun getApiService(server: ServerEntity): AgentApiService {
        // Garante que o túnel está ativo se tivermos dados de SSH
        if (server.sshHost != null && server.sshUser != null && server.sshKeyPath != null && server.localPort != null) {
            val tunnelResult = SshTunnelManager.startTunnel(
                serverId = server.id,
                host = server.sshHost,
                user = server.sshUser,
                privateKeyContent = server.sshKeyPath ?: "",
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

    suspend fun getTelemetry(server: ServerEntity): SystemTelemetry {
        try {
            val apiService = getApiService(server)
            val telemetry = apiService.getTelemetry()
            
            // Verifica o uso de CPU para alertas (HackerSec Core)
            if (telemetry.cpu.usagePercent > PocketNOCConfig.maxCpuThreshold) {
                securityNotifications.sendHighCpuAlert(server.name, telemetry.cpu.usagePercent.toDouble())
            }
            
            return telemetry
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
}
