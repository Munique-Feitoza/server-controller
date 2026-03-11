package com.pocketnoc.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketnoc.data.models.SystemTelemetry
import com.pocketnoc.data.repository.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class TelemetryUiState {
    object Loading : TelemetryUiState()
    data class Success(val telemetry: SystemTelemetry) : TelemetryUiState()
    data class Error(val message: String) : TelemetryUiState()
}

class DashboardViewModel(private val repository: ServerRepository) : ViewModel() {

    private val _telemetryState = MutableStateFlow<TelemetryUiState>(TelemetryUiState.Loading)
    val telemetryState: StateFlow<TelemetryUiState> = _telemetryState

    fun fetchTelemetry(serverUrl: String, token: String) {
        viewModelScope.launch {
            _telemetryState.value = TelemetryUiState.Loading
            val result = repository.getTelemetry(token)
            _telemetryState.value = result.fold(
                onSuccess = { TelemetryUiState.Success(it) },
                onFailure = { TelemetryUiState.Error(it.message ?: "Unknown error") }
            )
        }
    }

    fun refreshTelemetry(token: String) {
        fetchTelemetry("", token)
    }
}
