package com.pocketnoc.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketnoc.data.local.entities.ServerEntity
import com.pocketnoc.data.models.CommandResult
import com.pocketnoc.data.models.EmergencyCommand
import com.pocketnoc.data.models.HealthCheckResponse
import com.pocketnoc.data.models.SystemTelemetry
import com.pocketnoc.data.repository.ServerRepository
import com.pocketnoc.utils.JwtUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.pocketnoc.config.PocketNOCConfig
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class TelemetryUiState {
    object Loading : TelemetryUiState()
    data class Success(val telemetry: SystemTelemetry) : TelemetryUiState()
    data class Error(val message: String) : TelemetryUiState()
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: ServerRepository
) : ViewModel() {

    private val _telemetryState = MutableStateFlow<TelemetryUiState>(TelemetryUiState.Loading)
    val telemetryState: StateFlow<TelemetryUiState> = _telemetryState

    val allServers: StateFlow<List<ServerEntity>> = repository.getAllServers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        syncDefaultServers()
    }

    private fun normalizeUrl(url: String): String {
        var normalized = if (!url.startsWith("http")) "http://$url" else url
        if (!normalized.endsWith("/")) normalized += "/"
        return normalized.replace("(?<!:)/{2,}".toRegex(), "/")
    }

    private fun syncDefaultServers() {
        viewModelScope.launch {
            val secret = PocketNOCConfig.secret
            if (secret.isNotEmpty()) {
                val currentServers = repository.getAllServers().first()
                for (i in 1..4) {
                    val name = PocketNOCConfig.getServerName(i)
                    val ip = when(i) {
                        1 -> PocketNOCConfig.server1
                        2 -> PocketNOCConfig.server2
                        3 -> PocketNOCConfig.server3
                        4 -> PocketNOCConfig.server4
                        else -> ""
                    }
                    
                    if (ip.isNotEmpty()) {
                        val sshUser = PocketNOCConfig.getSshUser(i)
                        val sshHost = PocketNOCConfig.getSshHost(i)
                        val sshPort = PocketNOCConfig.getSshPort(i)
                        val remoteAgentPort = PocketNOCConfig.getRemotePort(i)
                        val sshKeyContent = PocketNOCConfig.sshKeyContent
                        val localPort = PocketNOCConfig.getLocalPort(i)
                        
                        // A URL final será o localhost se houver túnel configurado
                        val url = if (localPort != 0) "http://localhost:$localPort/" else normalizeUrl("$ip:9443/")
                        
                        // Tenta encontrar por nome atual ou nome legado "Winup i"
                        val existing = currentServers.find { it.name == name || it.name == "Winup $i" }
                        
                        if (existing == null) {
                            val token = JwtUtils.generateToken(secret)
                            repository.addServer(ServerEntity(
                                name = name,
                                url = url,
                                token = token,
                                secret = secret,
                                sshUser = sshUser,
                                sshHost = sshHost,
                                sshKeyPath = sshKeyContent, // Reusando campo para conteúdo
                                sshPort = sshPort,
                                remotePort = remoteAgentPort,
                                localPort = localPort,
                                osInfo = "Ubuntu 22.04",
                                stackInfo = "Nginx",
                                locationInfo = "Canada"
                            ))
                        } else {
                            // Atualiza configurações se mudarem no local.properties
                            repository.updateServer(existing.copy(
                                url = url,
                                sshUser = sshUser,
                                sshHost = sshHost,
                                sshKeyPath = sshKeyContent,
                                sshPort = sshPort,
                                remotePort = remoteAgentPort,
                                localPort = localPort,
                                name = name, // Atualiza para o novo nome se mudou
                                osInfo = "Ubuntu 22.04",
                                stackInfo = "Nginx",
                                locationInfo = "Canada"
                            ))
                        }
                    }
                }
            }
        }
    }

    fun deleteServer(server: ServerEntity) {
        viewModelScope.launch {
            repository.deleteServer(server)
        }
    }

    fun fetchTelemetry(server: ServerEntity) {
        viewModelScope.launch {
            _telemetryState.value = TelemetryUiState.Loading
            try {
                val result = repository.getTelemetry(server)
                _telemetryState.value = TelemetryUiState.Success(result)
                
                // Reseta status ou sinaliza warning de CPU alta
                val targetStatus = if (result.cpu.usagePercent > PocketNOCConfig.maxCpuThreshold) 1 else 0
                if (server.securityStatus != targetStatus) {
                    repository.updateServer(server.copy(securityStatus = targetStatus))
                }
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: e.message ?: "Unknown error"
                
                // Atualiza status de segurança com base no erro
                val newStatus = when {
                    errorMsg.contains("ALERTA DE SEGURANÇA", ignoreCase = true) -> 2 // Threat
                    PocketNOCConfig.emergencyMode -> 1 // Warning/Bypass
                    else -> server.securityStatus
                }
                
                if (newStatus != server.securityStatus) {
                    repository.updateServer(server.copy(securityStatus = newStatus))
                }

                _telemetryState.value = TelemetryUiState.Error("ERROR [${server.name}]: $errorMsg")
                e.printStackTrace()
            }
        }
    }

    fun addServer(name: String, url: String, secret: String) {
        viewModelScope.launch {
            val normalizedUrl = normalizeUrl(url)
            val token = JwtUtils.generateToken(secret)
            repository.addServer(ServerEntity(
                name = name, 
                url = normalizedUrl, 
                token = token, 
                secret = secret
            ))
        }
    }
    
    fun rebootServer(server: ServerEntity) {
        viewModelScope.launch {
            try {
                _eventFlow.emit("Sending emergency reboot to ${server.name}...")
                repository.rebootServer(server)
                _eventFlow.emit("Reboot signal accepted by node.")
            } catch (e: Exception) {
                _eventFlow.emit("Reboot failed: ${e.message}")
            }
        }
    }

    private val _commandResult = MutableStateFlow<CommandResult?>(null)
    val commandResult: StateFlow<CommandResult?> = _commandResult

    private val _availableCommands = MutableStateFlow<Map<String, List<EmergencyCommand>>>(emptyMap())
    val availableCommands: StateFlow<Map<String, List<EmergencyCommand>>> = _availableCommands

    private val _eventFlow = MutableSharedFlow<String>()
    val eventFlow: SharedFlow<String> = _eventFlow.asSharedFlow()

    fun performServiceAction(server: ServerEntity, serviceName: String, action: String) {
        viewModelScope.launch {
            try {
                val result = repository.performServiceAction(server, serviceName, action)
                _commandResult.value = result
                _eventFlow.emit("Action '$action' on $serviceName sent!")
                // Ao alterar um serviço, atualizamos a telemetria para refletir o novo status
                fetchTelemetry(server)
            } catch (e: Exception) {
                _eventFlow.emit("Failed: ${e.localizedMessage}")
                e.printStackTrace()
            }
        }
    }

    fun executeCommand(server: ServerEntity, commandId: String) {
        viewModelScope.launch {
            try {
                _eventFlow.emit("Executing $commandId...")
                val result = repository.executeCommand(server, commandId)
                _commandResult.value = result
                _eventFlow.emit("Command $commandId completed!")
            } catch (e: Exception) {
                _eventFlow.emit("Protocol failed: ${e.localizedMessage}")
                e.printStackTrace()
            }
        }
    }

    fun loadCommands(server: ServerEntity) {
        viewModelScope.launch {
            try {
                _eventFlow.emit("Syncing Action Center protocols...")
                val commands = repository.listCommands(server)
                _availableCommands.value = commands
                if (commands.isEmpty()) {
                    _eventFlow.emit("Action Center: No protocols found.")
                } else {
                    _eventFlow.emit("Action Center synchronized.")
                }
            } catch (e: Exception) {
                _eventFlow.emit("Sync Error: ${e.localizedMessage}")
                e.printStackTrace()
            }
        }
    }
}
