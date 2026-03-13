package com.pocketnoc.data.api

import com.pocketnoc.data.models.*
import retrofit2.http.*

interface AgentApiService {

    // Health & Status
    @GET("health")
    suspend fun healthCheck(): HealthCheckResponse

    // Telemetry
    @GET("telemetry")
    suspend fun getTelemetry(): SystemTelemetry

    // Services
    @GET("services/{service_name}")
    suspend fun getServiceStatus(
        @Path("service_name") serviceName: String
    ): ServiceInfo

    // Commands
    @GET("commands")
    suspend fun listCommands(): CommandListResponse

    @POST("commands/{command_id}")
    suspend fun executeCommand(
        @Path("command_id") commandId: String
    ): CommandResult

    // Specific Reboot Command
    @POST("reboot")
    suspend fun reboot(): CommandResult

    // Service Actions
    @POST("services/{service_name}/{action}")
    suspend fun performServiceAction(
        @Path("service_name") serviceName: String,
        @Path("action") action: String
    ): CommandResult

    // Metrics (Prometheus compatible)
    @GET("metrics")
    suspend fun getMetrics(): String
}
