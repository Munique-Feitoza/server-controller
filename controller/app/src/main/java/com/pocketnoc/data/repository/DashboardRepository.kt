package com.pocketnoc.data.repository

import com.pocketnoc.config.PocketNOCConfig
import com.pocketnoc.data.api.DashboardApiService
import com.pocketnoc.data.api.RetrofitClient
import com.pocketnoc.data.models.DashboardIncidentsResponse
import com.pocketnoc.data.models.DashboardStatsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Acesso à API de segurança do dashboard ERP. Mantém a montagem do Retrofit FORA do ViewModel
 * (presentation não deve conhecer infra HTTP). Autentica via query param `noc_token`.
 */
@Singleton
class DashboardRepository @Inject constructor() {

    private val nocToken = PocketNOCConfig.dashboardNocToken

    private val api: DashboardApiService by lazy {
        RetrofitClient.create(PocketNOCConfig.dashboardApiUrl).create(DashboardApiService::class.java)
    }

    suspend fun getIncidents(days: Int, limit: Int = 200): DashboardIncidentsResponse =
        withContext(Dispatchers.IO) { api.getIncidents(token = nocToken, limit = limit, days = days) }

    suspend fun getStats(days: Int): DashboardStatsResponse =
        withContext(Dispatchers.IO) { api.getIncidentStats(token = nocToken, days = days) }
}
