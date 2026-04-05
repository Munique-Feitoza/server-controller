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
        @Query("noc_token") token: String,
        @Query("limit") limit: Int = 200,
        @Query("days") days: Int = 7,
        @Query("severity") severity: String? = null
    ): DashboardIncidentsResponse

    @GET("stats")
    suspend fun getIncidentStats(
        @Query("noc_token") token: String,
        @Query("days") days: Int = 7
    ): DashboardStatsResponse
}
