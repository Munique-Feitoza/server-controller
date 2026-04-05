package com.pocketnoc.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketnoc.data.api.DashboardApiService
import com.pocketnoc.data.api.RetrofitClient
import com.pocketnoc.data.models.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SecurityViewModel @Inject constructor() : ViewModel() {

    companion object {
        private const val TAG = "SecurityVM"
        private const val DASHBOARD_BASE_URL = "https://api.winup.network/api/v1/pocketnoc/"
    }

    private val nocToken = com.pocketnoc.config.PocketNOCConfig.dashboardNocToken
    private val baseUrl = com.pocketnoc.config.PocketNOCConfig.dashboardApiUrl

    private val dashboardApi: DashboardApiService by lazy {
        RetrofitClient.getInstance(baseUrl, "")
            .create(DashboardApiService::class.java)
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
                val incidentsResp = dashboardApi.getIncidents(
                    token = nocToken,
                    limit = 200,
                    days = days
                )
                _incidents.value = incidentsResp.incidents

                val statsResp = dashboardApi.getIncidentStats(
                    token = nocToken,
                    days = days
                )
                _stats.value = statsResp

                Log.d(TAG, "Carregados ${incidentsResp.total} incidentes e stats")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar dados de seguranca: ${e.message}")
                _errorMsg.value = e.message
            }
            _isLoading.value = false
        }
    }
}
