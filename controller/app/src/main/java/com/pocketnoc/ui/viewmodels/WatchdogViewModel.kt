package com.pocketnoc.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketnoc.data.local.entities.ServerEntity
import com.pocketnoc.data.models.WatchdogEvent
import com.pocketnoc.data.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.pocketnoc.utils.ConnectivityStatus
import com.pocketnoc.utils.NetworkConnectivityObserver
import javax.inject.Inject

// ─── State Machine para a UI ──────────────────────────────────────────────────
// Segue o padrão UDF (Unidirecional Data Flow) já adotado no projeto.
// A UI nunca acessa dados diretamente — só consome estados imutáveis do StateFlow.
sealed class WatchdogUiState {
    object Loading : WatchdogUiState()
    data class Success(
        val events: List<WatchdogEvent>,
        val totalInStore: Int,
        /** Contagem de eventos por server_id — para o resumo do dashboard */
        val serversSummary: Map<String, Int>
    ) : WatchdogUiState()
    data class Error(val message: String) : WatchdogUiState()
    /** Ainda não houve nenhum evento de remediação — tudo OK */
    object Empty : WatchdogUiState()
}

/**
 * ViewModel do Watchdog (View-Model da Clean Architecture)
 * Responsabilidade UDF (Unidirectional Data Flow): Expor Data States puramente imutáveis.
 *
 * # Arquitetura & Gestão de Recursos
 * - **Injeção de Dependências:** Hilt (`@HiltViewModel`) para componentização de ciclo de vida.
 * - **Prevenção Categórica de Memory Leaks:** Uso dogmático de `viewModelScope.launch` obriga
 *   o Runtime de Coroutines do Kotlin a cancelar estruturalmente a corrotina (Job Cancel) 
 *   quando a ViewModel é morta, evitando processos Zumbis no Heap do Android.
 * - **Observer Pattern reativo:** Modelado estritamente com `StateFlow` para evitar mutação 
 *   indesejada de UI.
 */
@HiltViewModel
class WatchdogViewModel @Inject constructor(
    private val repository: ServerRepository,
    private val networkObserver: NetworkConnectivityObserver
) : ViewModel() {

    private val _state = MutableStateFlow<WatchdogUiState>(WatchdogUiState.Loading)
    val state: StateFlow<WatchdogUiState> = _state.asStateFlow()

    private val networkStatus: StateFlow<ConnectivityStatus> = networkObserver.observe()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ConnectivityStatus.Available
        )

    // Filtros selecionados pelo usuário na UI
    private val _selectedServerId = MutableStateFlow<String?>(null)
    val selectedServerId: StateFlow<String?> = _selectedServerId.asStateFlow()

    private val _selectedStatus = MutableStateFlow<String?>(null)
    val selectedStatus: StateFlow<String?> = _selectedStatus.asStateFlow()

    // Servidores disponíveis (para o filtro de servidor na UI)
    private val _knownServers = MutableStateFlow<List<String>>(emptyList())
    val knownServers: StateFlow<List<String>> = _knownServers.asStateFlow()

    // Controle de polling
    private var pollingJob: kotlinx.coroutines.Job? = null

    /**
     * Inicia polling automático a cada 30s.
     * Se já houver um polling ativo, ele é cancelado e reiniciado para o novo servidor.
     * Isso garante reatividade total na troca de abas/servidores na UI.
     */
    fun startPolling(server: ServerEntity) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                if (networkStatus.value == ConnectivityStatus.Available) {
                    fetchEvents(server)
                }
                delay(30_000L)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /**
     * Limpa o histórico de eventos remotamente no agente.
     */
    fun clearLogs(server: ServerEntity) {
        viewModelScope.launch {
            try {
                repository.clearWatchdogEvents(server)
                _state.value = WatchdogUiState.Empty
            } catch (e: Exception) {
                _state.value = WatchdogUiState.Error("Falha ao limpar logs: ${e.message}")
            }
        }
    }

    /**
     * Reseta todos os Circuit Breakers do servidor.
     * Útil quando o administrador já resolveu os problemas e quer que o Watchdog volte ao modo normal.
     */
    fun resetCircuits(server: ServerEntity) {
        viewModelScope.launch {
            try {
                repository.resetWatchdogCircuits(server)
                fetchEvents(server) // Atualiza a lista para ver o estado fechado
            } catch (e: Exception) {
                _state.value = WatchdogUiState.Error("Falha ao resetar circuitos: ${e.message}")
            }
        }
    }

    /**
     * Reinicia um serviço e também tenta resetar os circuitos (Ação Combinada)
     */
    fun performAtomicReset(server: ServerEntity, serviceName: String) {
        viewModelScope.launch {
            _state.value = WatchdogUiState.Loading
            try {
                // 1. Reinicia o serviço via systemctl
                repository.performServiceAction(server, serviceName, "restart")
                // 2. Reseta os circuitos do watchdog (para limpar o "Circuit Open")
                repository.resetWatchdogCircuits(server)
                // 3. Atualiza eventos
                delay(1000) // Pequeno delay para o agente processar o restart
                fetchEvents(server)
            } catch (e: Exception) {
                _state.value = WatchdogUiState.Error("Falha no reset atômico: ${e.message}")
            }
        }
    }

    /** Busca os eventos do agente com os filtros atualmente selecionados */
    fun fetchEvents(server: ServerEntity) {
        viewModelScope.launch {
            _state.value = WatchdogUiState.Loading
            try {
                val response = repository.getWatchdogEvents(
                    server    = server,
                    serverId  = _selectedServerId.value,
                    status    = _selectedStatus.value,
                    limit     = 100
                )
                if (response.events.isEmpty()) {
                    _state.value = WatchdogUiState.Empty
                } else {
                    // Extrai lista de server_ids conhecidos para popul os filtros
                    _knownServers.value = response.serversSummary.keys.toList().sorted()
                    _state.value = WatchdogUiState.Success(
                        events         = response.events,
                        totalInStore   = response.totalInStore,
                        serversSummary = response.serversSummary
                    )
                }
            } catch (e: Exception) {
                _state.value = WatchdogUiState.Error(
                    e.localizedMessage ?: "Falha ao buscar eventos do Watchdog"
                )
            }
        }
    }

    /** Aplica filtro por servidor e rebusca */
    fun filterByServer(serverId: String?, server: ServerEntity) {
        _selectedServerId.value = serverId
        fetchEvents(server)
    }

    /** Aplica filtro por status e rebusca */
    fun filterByStatus(status: String?, server: ServerEntity) {
        _selectedStatus.value = status
        fetchEvents(server)
    }

    /** Limpa todos os filtros e rebusca */
    fun clearFilters(server: ServerEntity) {
        _selectedServerId.value = null
        _selectedStatus.value   = null
        fetchEvents(server)
    }
}
