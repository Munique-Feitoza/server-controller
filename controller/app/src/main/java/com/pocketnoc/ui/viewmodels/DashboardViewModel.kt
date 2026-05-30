package com.pocketnoc.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketnoc.data.local.entities.ServerEntity
import com.pocketnoc.data.local.entities.AlertEntity
import com.pocketnoc.data.local.entities.TelemetryHistoryEntity
import com.pocketnoc.data.AgentError
import com.pocketnoc.data.models.*
import com.pocketnoc.data.repository.ServerRepository
import com.pocketnoc.utils.JwtUtils
import com.pocketnoc.utils.HealthStatusCalculator
import com.pocketnoc.utils.NetworkConnectivityObserver
import com.pocketnoc.utils.ConnectivityStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
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

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: ServerRepository,
    private val alertThresholdRepository: com.pocketnoc.data.local.AlertThresholdRepository,
    private val networkObserver: NetworkConnectivityObserver
) : ViewModel() {

    // PADRAO UNIDIRECIONAL DE DADOS (UDF):
    // Utilizei StateFlow para garantir que a UI reflita sempre a unica fonte de verdade.
    // O ViewModel encapsula a logica de negocio e expoe apenas estados imutaveis para as Screens.

    // Rede de segurança: um launch sem try/catch que estoure derrubaria o app inteiro
    // (não há handler global). Anexado aos launches de I/O sem tratamento próprio.
    private val safe = CoroutineExceptionHandler { _, e ->
        Log.e("DashboardViewModel", "Coroutine não tratada", e)
    }

    private val _telemetryState = MutableStateFlow<TelemetryUiState>(TelemetryUiState.Loading)
    val telemetryState: StateFlow<TelemetryUiState> = _telemetryState

    // Estado reativo da conectividade de rede do dispositivo
    val networkStatus: StateFlow<ConnectivityStatus> = networkObserver.observe()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ConnectivityStatus.Available
        )

    private val _serverHealthMap = MutableStateFlow<Map<Int, ServerHealth>>(emptyMap())
    val serverHealthMap: StateFlow<Map<Int, ServerHealth>> = _serverHealthMap.asStateFlow()

    // Historico de telemetria por servidor (mais antigo -> mais recente para renderizar o grafico)
    private val _telemetryHistory = MutableStateFlow<List<TelemetryHistoryEntity>>(emptyList())
    val telemetryHistory: StateFlow<List<TelemetryHistoryEntity>> = _telemetryHistory.asStateFlow()

    val alertHistory: StateFlow<List<AlertEntity>> = repository.getAllAlertHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allServers: StateFlow<List<ServerEntity>> = repository.getAllServers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val alertThresholds: StateFlow<com.pocketnoc.data.local.AlertThresholdConfig> = 
        alertThresholdRepository.alertThresholdsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.pocketnoc.data.local.AlertThresholdConfig())

    init {
        // syncDefaultServers removida: o BuildConfig nao carrega mais POCKET_NOC_SECRET
        // (era vetor de extracao via APK). Cada servidor agora vem 100% do LoginScreen
        // e fica em EncryptedSharedPreferences.
    }

    private fun normalizeUrl(url: String): String {
        var normalized = if (!url.startsWith("http")) "http://$url" else url
        if (!normalized.endsWith("/")) normalized += "/"
        return normalized.replace("(?<!:)/{2,}".toRegex(), "/")
    }

    // syncDefaultServers removida em 2026-05-07 — auto-popular usava
    // BuildConfig.POCKET_NOC_SECRET, vetor de extracao via APK reverso.

    fun deleteServer(server: ServerEntity) {
        viewModelScope.launch(safe) {
            repository.deleteServer(server)
        }
    }

    /**
     * Coleta telemetria em tempo real com tratamento de erro resiliente.
     *
     * Implementei aqui o conceito de 'Observabilidade Reativa'. Se a conexao falhar,
     * o sistema sinaliza visualmente via State, sem travar a navegacao do usuario.
     */
    fun fetchTelemetry(server: ServerEntity) {
        viewModelScope.launch {
            _telemetryState.value = TelemetryUiState.Loading
            try {
                val result = repository.getTelemetry(server)
                _telemetryState.value = TelemetryUiState.Success(result)

                // Persiste snapshot e atualiza o historico para os graficos.
                // Coroutine SEPARADA do try/catch externo — precisa do próprio handler,
                // senão um erro do Room (disco cheio etc.) seria fatal.
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO + safe) {
                    repository.saveTelemetrySnapshot(server.id, result)
                    val history = repository.getTelemetryHistory(server.id, limit = 2880) // 24h a cada 30s
                    _telemetryHistory.value = history.reversed() // mais antigo primeiro para o grafico
                }

                // Calcula saude do servidor
                updateServerHealth(server, result)
                
                // Reseta status ou sinaliza alerta de CPU alta
                val targetStatus = if (result.cpu.usagePercent > PocketNOCConfig.maxCpuThreshold) 1 else 0
                if (server.securityStatus != targetStatus) {
                    repository.updateServer(server.copy(securityStatus = targetStatus))
                }
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: e.message ?: "Erro desconhecido"

                // Ameaça (intrusão SSH) detectada pelo tipo, não por casamento de string.
                val newStatus = if (e is AgentError.SecurityThreat) 2 else server.securityStatus
                if (newStatus != server.securityStatus) {
                    repository.updateServer(server.copy(securityStatus = newStatus))
                }

                _telemetryState.value = TelemetryUiState.Error("ERRO [${server.name}]: $errorMsg")
                Log.e("DashboardViewModel", "fetchTelemetry falhou", e)
            }
        }
    }

    fun addServer(name: String, url: String, secret: String) {
        viewModelScope.launch(safe) {
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

    private val _eventFlow = MutableSharedFlow<String>()
    val eventFlow: SharedFlow<String> = _eventFlow.asSharedFlow()

    fun performServiceAction(server: ServerEntity, serviceName: String, action: String) {
        viewModelScope.launch {
            try {
                repository.performServiceAction(server, serviceName, action)
                _eventFlow.emit("Action '$action' on $serviceName sent!")
                // Ao alterar um servico, atualizamos a telemetria para refletir o novo status
                fetchTelemetry(server)
            } catch (e: Exception) {
                _eventFlow.emit("Failed: ${e.localizedMessage}")
                e.printStackTrace()
            }
        }
    }

    // Roda inline na coroutine de fetchTelemetry (estruturado), tudo em IO. Antes fazia
    // withContext(Default) + launch(IO) aninhado fire-and-forget, com o ServerHealth montado
    // em DOIS lugares (try/catch). Agora: 1 dispatcher, 1 construção, falha de alertas vira 0.
    private suspend fun updateServerHealth(server: ServerEntity, telemetry: SystemTelemetry) = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val health = HealthStatusCalculator.calculateStatus(telemetry)

        val activeAlerts = try {
            val alerts = repository.getAlerts(server)
            alerts.alerts.forEach { alert ->
                repository.saveAlert(
                    AlertEntity(
                        serverId = server.id,
                        serverName = server.name,
                        type = alert.alertType.name,
                        message = alert.message,
                        value = alert.currentValue,
                        threshold = alert.threshold
                    )
                )
            }
            alerts.count
        } catch (e: Exception) {
            Log.e("DashboardViewModel", "Falha ao buscar alertas no health update: ${e.message}")
            0
        }

        val serverHealth = ServerHealth(
            serverId = server.id,
            serverName = server.name,
            status = health,
            cpuUsage = telemetry.cpu.usagePercent,
            memoryUsage = telemetry.memory.usagePercent,
            diskUsage = telemetry.disk.disks.maxOfOrNull { it.usagePercent } ?: 0f,
            temperature = telemetry.temperature?.sensors?.maxOfOrNull { it.celsius },
            activeAlerts = activeAlerts,
            lastUpdate = System.currentTimeMillis()
        )
        _serverHealthMap.update { currentMap ->
            currentMap.toMutableMap().apply { this[server.id] = serverHealth }
        }
    }

    fun updateAlertSettings(config: com.pocketnoc.data.local.AlertThresholdConfig) {
        viewModelScope.launch {
            try {
                // 1. Salva localmente no DataStore do dispositivo
                alertThresholdRepository.updateAllThresholds(config)
                
                // 2. Sincroniza com todos os servidores ativos
                val servers = allServers.value
                val currentHealth = _serverHealthMap.value
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
    fun loadTelemetryHistory(serverId: Int) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO + safe) {
            val history = repository.getTelemetryHistory(serverId, limit = 2880) // 24h
            _telemetryHistory.value = history.reversed()
        }
    }

    fun clearAlertHistory() {
        viewModelScope.launch(safe) {
            repository.clearAlertHistory()
            _eventFlow.emit("Alert audit history cleared.")
        }
    }

    suspend fun fetchPhpFpmPools(server: ServerEntity): com.pocketnoc.data.models.PhpFpmResponse {
        return repository.getPhpFpmPools(server)
    }

    private val _phpFpmState = MutableStateFlow<com.pocketnoc.data.models.PhpFpmResponse?>(null)
    val phpFpmState: StateFlow<com.pocketnoc.data.models.PhpFpmResponse?> = _phpFpmState.asStateFlow()

    fun loadPhpFpmPools(server: ServerEntity) {
        viewModelScope.launch {
            try {
                _phpFpmState.value = repository.getPhpFpmPools(server)
            } catch (e: Exception) {
                android.util.Log.w("DashboardVM", "Falha ao carregar php-fpm pools: ${e.message}")
                _phpFpmState.value = null
            }
        }
    }

    suspend fun fetchSslCheck(server: ServerEntity): com.pocketnoc.data.models.SslCheckResponse {
        return repository.checkSsl(server)
    }

    fun blockIp(server: ServerEntity, ip: String) {
        viewModelScope.launch {
            try {
                _eventFlow.emit("Signaling Firewall for BAN: $ip...")
                repository.blockIp(server, ip)
                _eventFlow.emit("SENTINEL: IP $ip blocked permamently.")
                // Atualiza telemetria para verificar se o IP sumiu ou o status mudou
                fetchTelemetry(server)
            } catch (e: Exception) {
                _eventFlow.emit("Firewall Error: ${e.localizedMessage}")
                e.printStackTrace()
            }
        }
    }
}
