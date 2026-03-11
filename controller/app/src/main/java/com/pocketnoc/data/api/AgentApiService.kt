package com.pocketnoc.data.api

import com.pocketnoc.data.models.*
import retrofit2.http.*

interface AgentApiService {

    // Health & Status
    @GET("/health")
    suspend fun healthCheck(): HealthCheckResponse

    // Telemetry
    @GET("/telemetry")
    suspend fun getTelemetry(
        @Header("Authorization") token: String
    ): SystemTelemetry

    // Services
    @GET("/services/{service_name}")
    suspend fun getServiceStatus(
        @Path("service_name") serviceName: String,
        @Header("Authorization") token: String
    ): ServiceInfo

    // Commands
    @GET("/commands")
    suspend fun listCommands(
        @Header("Authorization") token: String
    ): Map<String, List<EmergencyCommand>>

    @POST("/commands/{command_id}")
    suspend fun executeCommand(
        @Path("command_id") commandId: String,
        @Header("Authorization") token: String
    ): CommandResult

    // Metrics (Prometheus compatible)
    @GET("/metrics")
    suspend fun getMetrics(
        @Header("Authorization") token: String
    ): String
}
