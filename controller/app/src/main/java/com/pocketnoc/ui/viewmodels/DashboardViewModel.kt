package com.pocketnoc.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketnoc.data.local.entities.ServerEntity
import com.pocketnoc.data.local.entities.AlertEntity
import com.pocketnoc.data.models.*
import com.pocketnoc.data.repository.ServerRepository
import com.pocketnoc.utils.JwtUtils
import com.pocketnoc.utils.HealthStatusCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

sealed class CommandsUiState {
    object Loading : CommandsUiState()
    data class Success(val commands: List<CommandInfo>) : CommandsUiState()
    data class Error(val message: String) : CommandsUiState()
}

sealed class ProcessesUiState {
    object Loading : ProcessesUiState()
    data class Success(val processes: List<ProcessInfo>) : ProcessesUiState()
    data class Error(val message: String) : ProcessesUiState()
}

sealed class LogsUiState {
    object Loading : LogsUiState()
    data class Success(val logs: String) : LogsUiState()
    data class Error(val message: String) : LogsUiState()
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: ServerRepository,
    private val alertThresholdRepository: com.pocketnoc.data.local.AlertThresholdRepository
) : ViewModel() {

    // PADRÃO UNIDIRECIAL DE DADOS (UDF):
    // Utilizei StateFlow para garantir que a UI reflita sempre a única fonte de verdade.
    // O ViewModel encapsula a lógica de negócio e expõe apenas estados imutáveis para as Screens.

    private val _telemetryState = MutableStateFlow<TelemetryUiState>(TelemetryUiState.Loading)
    val telemetryState: StateFlow<TelemetryUiState> = _telemetryState

    private val _commandsState = MutableStateFlow<CommandsUiState>(CommandsUiState.Loading)
    val commandsState: StateFlow<CommandsUiState> = _commandsState

    private val _serverHealthMap = MutableStateFlow<Map<Int, ServerHealth>>(emptyMap())
    val serverHealthMap: StateFlow<Map<Int, ServerHealth>> = _serverHealthMap.asStateFlow()

    private val _processesState = MutableStateFlow<ProcessesUiState>(ProcessesUiState.Loading)
    val processesState: StateFlow<ProcessesUiState> = _processesState

    private val _logsState = MutableStateFlow<LogsUiState>(LogsUiState.Loading)
    val logsState: StateFlow<LogsUiState> = _logsState

    val alertHistory: StateFlow<List<AlertEntity>> = repository.getAllAlertHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allServers: StateFlow<List<ServerEntity>> = repository.getAllServers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val alertThresholds: StateFlow<com.pocketnoc.data.local.AlertThresholdConfig> = 
        alertThresholdRepository.alertThresholdsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.pocketnoc.data.local.AlertThresholdConfig())

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

    /**
     * Coleta telemetria em tempo real com tratamento de erro resiliente.
     * 
     * Implementei aqui o conceito de 'Observabilidade Reativa'. Se a conexão falhar,
     * o sistema sinaliza visualmente via State, sem travar a navegação do usuário.
     */
    fun fetchTelemetry(server: ServerEntity) {
        viewModelScope.launch {
            _telemetryState.value = TelemetryUiState.Loading
            try {
                val result = repository.getTelemetry(server)
                _telemetryState.value = TelemetryUiState.Success(result)
                
                // Calcula saúde do servidor
                updateServerHealth(server, result)
                
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

    fun fetchCommands(server: ServerEntity) {
        viewModelScope.launch {
            _commandsState.value = CommandsUiState.Loading
            try {
                val commands = repository.listCommands(server).values.flatten().map { cmd ->
                    CommandInfo(
                        id = cmd.id,
                        description = cmd.description,
                        command = cmd.command,
                        args = cmd.args
                    )
                }
                _commandsState.value = CommandsUiState.Success(commands)
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: "Failed to load commands"
                _commandsState.value = CommandsUiState.Error(errorMsg)
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

    private suspend fun updateServerHealth(server: ServerEntity, telemetry: SystemTelemetry) = withContext(kotlinx.coroutines.Dispatchers.Default) {
        try {
            val health = HealthStatusCalculator.calculateStatus(telemetry)
            
            // Operações de I/O (Alertas e DB) movidas para o dispatcher correto
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val alerts = repository.getAlerts(server)

                    if (alerts.alerts.isNotEmpty()) {
                        alerts.alerts.forEach { alert ->
                            repository.saveAlert(
                                com.pocketnoc.data.local.entities.AlertEntity(
                                    serverId = server.id,
                                    serverName = server.name,
                                    type = alert.alertType.name,
                                    message = alert.message,
                                    value = alert.currentValue,
                                    threshold = alert.threshold
                                )
                            )
                        }
                    }

                    val serverHealth = ServerHealth(
                        serverId = server.id,
                        serverName = server.name,
                        status = health,
                        cpuUsage = telemetry.cpu.usagePercent,
                        memoryUsage = telemetry.memory.usagePercent,
                        diskUsage = telemetry.disk.disks.maxOfOrNull { it.usagePercent } ?: 0f,
                        temperature = telemetry.temperature?.sensors?.maxOfOrNull { it.celsius },
                        activeAlerts = alerts.count,
                        lastUpdate = System.currentTimeMillis()
                    )

                    // Atualização atômica do mapa de saúde
                    _serverHealthMap.update { currentMap ->
                        currentMap.toMutableMap().apply {
                            this[server.id] = serverHealth
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DashboardViewModel", "Failed to fetch alerts during health update: ${e.message}")
                    
                    // Fallback para exibir saúde sem alertas se falhar
                    val serverHealth = ServerHealth(
                        serverId = server.id,
                        serverName = server.name,
                        status = health,
                        cpuUsage = telemetry.cpu.usagePercent,
                        memoryUsage = telemetry.memory.usagePercent,
                        diskUsage = telemetry.disk.disks.maxOfOrNull { it.usagePercent } ?: 0f,
                        temperature = telemetry.temperature?.sensors?.maxOfOrNull { it.celsius },
                        activeAlerts = 0,
                        lastUpdate = System.currentTimeMillis()
                    )
                    
                    _serverHealthMap.update { currentMap ->
                        currentMap.toMutableMap().apply {
                            this[server.id] = serverHealth
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateAlertSettings(config: com.pocketnoc.data.local.AlertThresholdConfig) {
        viewModelScope.launch {
            try {
                // 1. Salva localmente no DataStore
                alertThresholdRepository.updateAllThresholds(config)
                
                // 2. Sincroniza com todos os servidores ativos
                // Sync cada servidor ativo
                val servers = allServers.value // Get all servers once
                val currentHealth = _serverHealthMap.value // Use _serverHealthMap.value
                _eventFlow.emit("Propagating thresholds to ${currentHealth.size} active nodes...")

                if (currentHealth.isNotEmpty()) {
                    for ((_, health) in currentHealth) {
                        val server = servers.find { it.id == health.serverId }
                        if (server != null) {
                            try {
                                repository.updateAlertConfig(server, config)
                            } catch (e: Exception) {
                                _eventFlow.emit("Failed to sync ${server.name}: ${e.message}")
                            }
                        }
                    }
                }
                
                _eventFlow.emit("Global sync complete!")
            } catch (e: Exception) {
                _eventFlow.emit("Local save failed: ${e.localizedMessage}")
                e.printStackTrace()
            }
        }
    }
    fun fetchProcesses(server: ServerEntity) {
        viewModelScope.launch {
            _processesState.value = ProcessesUiState.Loading
            try {
                val processes = repository.listProcesses(server)
                _processesState.value = ProcessesUiState.Success(processes)
            } catch (e: Exception) {
                _processesState.value = ProcessesUiState.Error(e.localizedMessage ?: "Failed to fetch processes")
            }
        }
    }

    fun killProcess(server: ServerEntity, pid: Long) {
        viewModelScope.launch {
            try {
                _eventFlow.emit("Signaling termination for PID $pid...")
                repository.killProcess(server, pid)
                _eventFlow.emit("Process $pid terminated.")
                // Refresh list
                fetchProcesses(server)
            } catch (e: Exception) {
                _eventFlow.emit("Kill failed: ${e.localizedMessage}")
            }
        }
    }

    fun fetchLogs(server: ServerEntity, service: String = "pocket-noc-agent") {
        viewModelScope.launch {
            _logsState.value = LogsUiState.Loading
            try {
                val response = repository.fetchLogs(server, service)
                _logsState.value = LogsUiState.Success(response.logs)
            } catch (e: Exception) {
                _logsState.value = LogsUiState.Error(e.localizedMessage ?: "Failed to fetch logs")
            }
        }
    }

    fun clearAlertHistory() {
        viewModelScope.launch {
            repository.clearAlertHistory()
            _eventFlow.emit("Alert audit history cleared.")
        }
    }

    fun blockIp(server: ServerEntity, ip: String) {
        viewModelScope.launch {
            try {
                _eventFlow.emit("Signaling Firewall for BAN: $ip...")
                repository.blockIp(server, ip)
                _eventFlow.emit("SENTINEL: IP $ip blocked permamently.")
                // Atualiza telemetria para ver se o IP some ou status muda
                fetchTelemetry(server)
            } catch (e: Exception) {
                _eventFlow.emit("Firewall Error: ${e.localizedMessage}")
                e.printStackTrace()
            }
        }
    }
}
