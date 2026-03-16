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

    // Alerts
    @GET("alerts")
    suspend fun getAlerts(): AlertsResponse

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

    // Dynamic Alert Configuration
    @POST("alerts/config")
    suspend fun updateAlertConfig(
        @Body config: AlertThresholdConfig
    ): GenericResponse

    // Processes
    @GET("processes")
    suspend fun getTopProcesses(): ProcessListResponse

    @DELETE("processes/{pid}")
    suspend fun killProcess(
        @Path("pid") pid: Long
    ): GenericResponse

    // Logs
    @GET("logs")
    suspend fun getLogs(
        @Query("service") service: String,
        @Query("lines") lines: Int = 100
    ): LogResponse
}
