package com.pocketnoc.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketnoc.data.local.entities.ServerEntity
import com.pocketnoc.data.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LogsUiState {
    object Loading : LogsUiState()
    data class Success(val logs: String) : LogsUiState()
    data class Error(val message: String) : LogsUiState()
}

/**
 * Visualizador de logs de serviços de um servidor. Extraído do DashboardViewModel.
 */
@HiltViewModel
class LogsViewModel @Inject constructor(
    private val repository: ServerRepository
) : ViewModel() {

    private val safe = CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "Coroutine não tratada", e)
    }

    private val _logsState = MutableStateFlow<LogsUiState>(LogsUiState.Loading)
    val logsState: StateFlow<LogsUiState> = _logsState.asStateFlow()

    fun fetchLogs(server: ServerEntity, service: String = "pocket-noc-agent") {
        viewModelScope.launch(safe) {
            _logsState.value = LogsUiState.Loading
            try {
                _logsState.value = LogsUiState.Success(repository.fetchLogs(server, service).logs)
            } catch (e: Exception) {
                _logsState.value = LogsUiState.Error(e.localizedMessage ?: "Falha ao buscar logs")
            }
        }
    }

    private companion object {
        const val TAG = "LogsViewModel"
    }
}
