package com.pocketnoc.ui.viewmodels

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

    // Token para acessar a API do dashboard (mesmo do sync de sheets)
    // Em producao, puxar do BuildConfig ou SecureTokenManager
    private val dashboardToken = com.pocketnoc.BuildConfig.POCKET_NOC_SECRET

    private val dashboardApi: DashboardApiService by lazy {
        RetrofitClient.getInstance("https://api.winup.network/api/v1/pocketnoc/", "")
            .create(DashboardApiService::class.java)
    }

    private val _incidents = MutableStateFlow<List<DashboardIncident>>(emptyList())
    val incidents: StateFlow<List<DashboardIncident>> = _incidents

    private val _stats = MutableStateFlow<DashboardStatsResponse?>(null)
    val stats: StateFlow<DashboardStatsResponse?> = _stats

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun loadSecurityData(hours: Int = 24) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val incidentsResp = dashboardApi.getIncidents(
                    token = dashboardToken,
                    limit = 100,
                    hours = hours
                )
                _incidents.value = incidentsResp.incidents

                val statsResp = dashboardApi.getIncidentStats(
                    token = dashboardToken,
                    hours = hours
                )
                _stats.value = statsResp
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _isLoading.value = false
        }
    }
}
