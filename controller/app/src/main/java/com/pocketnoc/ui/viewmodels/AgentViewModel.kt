package com.pocketnoc.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketnoc.data.local.entities.ServerEntity
import com.pocketnoc.data.models.AgentRuntimeConfig
import com.pocketnoc.data.models.AuditEntry
import com.pocketnoc.data.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Configuração em runtime do agent + log de auditoria de um servidor.
 * Extraído do DashboardViewModel — cobre AgentConfigScreen e AuditLogScreen.
 */
@HiltViewModel
class AgentViewModel @Inject constructor(
    private val repository: ServerRepository
) : ViewModel() {

    private val safe = CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "Coroutine não tratada", e)
    }

    /** Suspende: a tela chama dentro de LaunchedEffect com seu próprio try/catch. */
    suspend fun fetchAgentConfig(server: ServerEntity): AgentRuntimeConfig =
        repository.getAgentConfig(server)

    /** Suspende: idem. */
    suspend fun fetchAuditLogs(server: ServerEntity): List<AuditEntry> =
        repository.getAuditLogs(server)

    fun updateAgentConfig(server: ServerEntity, config: Map<String, Any>) {
        viewModelScope.launch(safe) {
            try {
                repository.updateAgentConfig(server, config)
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao atualizar config de ${server.name}: ${e.localizedMessage}")
            }
        }
    }

    private companion object {
        const val TAG = "AgentViewModel"
    }
}
