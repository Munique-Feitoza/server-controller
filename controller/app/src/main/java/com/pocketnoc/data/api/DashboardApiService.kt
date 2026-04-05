package com.pocketnoc.data.api

import com.pocketnoc.data.models.*
import retrofit2.http.*

/**
 * API do Dashboard ERP (Winup) — consome dados de seguranca.
 * Base URL: https://api.winup.network/api/v1/pocketnoc/
 */
interface DashboardApiService {

    @GET("incidents")
    suspend fun getIncidents(
        @Query("token") token: String,
        @Query("limit") limit: Int = 50,
        @Query("severity") severity: String? = null,
        @Query("hours") hours: Int = 24
    ): DashboardIncidentsResponse

    @GET("incidents/stats")
    suspend fun getIncidentStats(
        @Query("token") token: String,
        @Query("hours") hours: Int = 24
    ): DashboardStatsResponse
}
