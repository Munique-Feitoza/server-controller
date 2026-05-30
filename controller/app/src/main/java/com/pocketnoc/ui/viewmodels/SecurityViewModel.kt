package com.pocketnoc.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketnoc.data.models.*
import com.pocketnoc.data.repository.DashboardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val repository: DashboardRepository
) : ViewModel() {

    companion object {
        private const val TAG = "SecurityVM"
    }

    private val _incidents = MutableStateFlow<List<DashboardIncident>>(emptyList())
    val incidents: StateFlow<List<DashboardIncident>> = _incidents

    private val _stats = MutableStateFlow<DashboardStatsResponse?>(null)
    val stats: StateFlow<DashboardStatsResponse?> = _stats

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMsg = MutableStateFlow<String?>(null)
    val errorMsg: StateFlow<String?> = _errorMsg

    fun loadSecurityData(days: Int = 7) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMsg.value = null
            try {
                val incidentsResp = repository.getIncidents(days = days)
                _incidents.value = incidentsResp.incidents
                _stats.value = repository.getStats(days = days)
                Log.d(TAG, "Carregados ${incidentsResp.total} incidentes e stats")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar dados de seguranca: ${e.message}")
                _errorMsg.value = e.message
            }
            _isLoading.value = false
        }
    }
}
