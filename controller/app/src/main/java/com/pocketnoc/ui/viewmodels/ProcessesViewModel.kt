package com.pocketnoc.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketnoc.data.local.entities.ServerEntity
import com.pocketnoc.data.models.ProcessInfo
import com.pocketnoc.data.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ProcessesUiState {
    object Loading : ProcessesUiState()
    data class Success(val processes: List<ProcessInfo>) : ProcessesUiState()
    data class Error(val message: String) : ProcessesUiState()
}

/**
 * Explorador de processos de um servidor. Extraído do DashboardViewModel (god-object)
 * — escopo único: listar e encerrar processos.
 */
@HiltViewModel
class ProcessesViewModel @Inject constructor(
    private val repository: ServerRepository
) : ViewModel() {

    private val safe = CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "Coroutine não tratada", e)
    }

    private val _processesState = MutableStateFlow<ProcessesUiState>(ProcessesUiState.Loading)
    val processesState: StateFlow<ProcessesUiState> = _processesState.asStateFlow()

    fun fetchProcesses(server: ServerEntity) {
        viewModelScope.launch(safe) {
            _processesState.value = ProcessesUiState.Loading
            try {
                _processesState.value = ProcessesUiState.Success(repository.listProcesses(server))
            } catch (e: Exception) {
                _processesState.value = ProcessesUiState.Error(e.localizedMessage ?: "Falha ao buscar processos")
            }
        }
    }

    fun killProcess(server: ServerEntity, pid: Long) {
        viewModelScope.launch(safe) {
            try {
                repository.killProcess(server, pid)
                fetchProcesses(server) // refresh
            } catch (e: Exception) {
                _processesState.value = ProcessesUiState.Error(e.localizedMessage ?: "Falha ao encerrar PID $pid")
            }
        }
    }

    private companion object {
        const val TAG = "ProcessesViewModel"
    }
}
