package com.pocketnoc.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketnoc.data.local.entities.ServerEntity
import com.pocketnoc.data.models.CommandInfo
import com.pocketnoc.data.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class CommandsUiState {
    object Loading : CommandsUiState()
    data class Success(val commands: List<CommandInfo>) : CommandsUiState()
    data class Error(val message: String) : CommandsUiState()
}

/**
 * Action Center: lista e executa os comandos de emergência de um servidor.
 * Extraído do DashboardViewModel.
 */
@HiltViewModel
class CommandsViewModel @Inject constructor(
    private val repository: ServerRepository
) : ViewModel() {

    private val safe = CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "Coroutine não tratada", e)
    }

    private val _commandsState = MutableStateFlow<CommandsUiState>(CommandsUiState.Loading)
    val commandsState: StateFlow<CommandsUiState> = _commandsState.asStateFlow()

    fun fetchCommands(server: ServerEntity) {
        viewModelScope.launch(safe) {
            _commandsState.value = CommandsUiState.Loading
            try {
                val commands = repository.listCommands(server).map { cmd ->
                    CommandInfo(id = cmd.id, description = cmd.description, command = cmd.command, args = cmd.args)
                }
                _commandsState.value = CommandsUiState.Success(commands)
            } catch (e: Exception) {
                _commandsState.value = CommandsUiState.Error(e.localizedMessage ?: "Falha ao carregar comandos")
            }
        }
    }

    fun executeCommand(server: ServerEntity, commandId: String) {
        viewModelScope.launch(safe) {
            try {
                repository.executeCommand(server, commandId)
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao executar $commandId: ${e.localizedMessage}")
            }
        }
    }

    private companion object {
        const val TAG = "CommandsViewModel"
    }
}
