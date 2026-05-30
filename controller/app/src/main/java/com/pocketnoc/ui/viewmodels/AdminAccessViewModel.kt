package com.pocketnoc.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketnoc.data.local.entities.ServerEntity
import com.pocketnoc.data.models.SecurityIncident
import com.pocketnoc.data.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

/** Evento de "admin criado" ja parseado do incidente, pronto para a UI. */
data class AdminEvent(
    val incidentId: String,
    val site: String,
    val path: String,
    val userId: Long,
    val login: String,
    val email: String,
    val createdBy: String,
    val ip: String,
    val timestamp: String
)

sealed class AdminAccessUiState {
    data object Loading : AdminAccessUiState()
    data class Success(val events: List<AdminEvent>) : AdminAccessUiState()
    data object Empty : AdminAccessUiState()
    data class Error(val message: String) : AdminAccessUiState()
}

/**
 * ViewModel da tela "Acessos Admin".
 *
 * Consome `GET /security/incidents` do agente, filtra os eventos `admin_created`
 * (alimentados pelo mu-plugin WinUp Security Guard) e expoe a acao de revogacao
 * (`POST /security/revoke-admin`). Padrao UDF com StateFlow imutavel.
 */
@HiltViewModel
class AdminAccessViewModel @Inject constructor(
    private val repository: ServerRepository
) : ViewModel() {

    private val _state = MutableStateFlow<AdminAccessUiState>(AdminAccessUiState.Loading)
    val state: StateFlow<AdminAccessUiState> = _state.asStateFlow()

    /** Mensagem transitoria de feedback de uma acao (revogacao). */
    private val _actionMsg = MutableStateFlow<String?>(null)
    val actionMsg: StateFlow<String?> = _actionMsg.asStateFlow()

    fun clearActionMsg() {
        _actionMsg.value = null
    }

    /** Busca os eventos de admin criado no agente. */
    fun fetch(server: ServerEntity) {
        viewModelScope.launch {
            _state.value = AdminAccessUiState.Loading
            try {
                val resp = repository.getSecurityIncidents(server, limit = 200)
                val events = resp.incidents
                    .filter { it.eventType == "admin_created" }
                    .mapNotNull(::parseEvent)
                _state.value = if (events.isEmpty()) {
                    AdminAccessUiState.Empty
                } else {
                    AdminAccessUiState.Success(events)
                }
            } catch (e: Exception) {
                _state.value = AdminAccessUiState.Error(
                    e.localizedMessage ?: "Falha ao buscar acessos admin"
                )
            }
        }
    }

    /** Revoga (apaga) o admin do evento e rebusca a lista. */
    fun revoke(server: ServerEntity, event: AdminEvent) {
        viewModelScope.launch {
            try {
                val r = repository.revokeAdmin(server, event.path, event.userId, event.incidentId)
                _actionMsg.value = if (r.ok) {
                    "Admin '${event.login}' removido de ${event.site}"
                } else {
                    "Falha ao remover: ${r.msg ?: "erro desconhecido"}"
                }
                fetch(server)
            } catch (e: Exception) {
                _actionMsg.value = "Falha ao remover: ${e.localizedMessage}"
            }
        }
    }

    /** Converte o JSON de `details` do incidente num AdminEvent tipado. */
    private fun parseEvent(inc: SecurityIncident): AdminEvent? {
        val raw = inc.details ?: return null
        return try {
            val d = JSONObject(raw)
            AdminEvent(
                incidentId = inc.id,
                site       = d.optString("site", "?"),
                path       = d.optString("path", ""),
                userId     = d.optLong("user_id", 0L),
                login      = d.optString("user_login", "?"),
                email      = d.optString("email", ""),
                createdBy  = d.optString("created_by", "?"),
                ip         = inc.attackerIp,
                timestamp  = inc.timestamp
            )
        } catch (e: Exception) {
            null
        }
    }
}
