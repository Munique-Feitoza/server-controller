package com.pocketnoc.data.api

import com.pocketnoc.data.models.*
import retrofit2.http.*

interface AgentApiService {

    // Saude e Status
    @GET("health")
    suspend fun healthCheck(): HealthCheckResponse

    // Telemetria
    @GET("telemetry")
    suspend fun getTelemetry(): SystemTelemetry

    // Alertas
    @GET("alerts")
    suspend fun getAlerts(): AlertsResponse

    // Servicos
    @GET("services/{service_name}")
    suspend fun getServiceStatus(
        @Path("service_name") serviceName: String
    ): ServiceInfo

    // Comandos
    @GET("commands")
    suspend fun listCommands(): CommandListResponse

    @POST("commands/{command_id}")
    suspend fun executeCommand(
        @Path("command_id") commandId: String
    ): CommandResult

    // Comando de reboot especifico
    @POST("reboot")
    suspend fun reboot(): CommandResult

    // Acoes de servico
    @POST("services/{service_name}/{action}")
    suspend fun performServiceAction(
        @Path("service_name") serviceName: String,
        @Path("action") action: String
    ): CommandResult

    // Metricas (compativel com Prometheus)
    @GET("metrics")
    suspend fun getMetrics(): String

    // Configuracao dinamica de alertas
    @POST("alerts/config")
    suspend fun updateAlertConfig(
        @Body config: AlertThresholdConfig
    ): GenericResponse

    // Processos
    @GET("processes")
    suspend fun getTopProcesses(): ProcessListResponse

    @DELETE("processes/{pid}")
    suspend fun killProcess(
        @Path("pid") pid: Long
    ): GenericResponse

    // Logs do sistema
    @GET("logs")
    suspend fun getLogs(
        @Query("service") service: String,
        @Query("lines") lines: Int = 100
    ): LogResponse

    // Seguranca
    @POST("security/block-ip")
    suspend fun blockIp(
        @Body payload: Map<String, String>
    ): GenericResponse

    // ─── Watchdog / Auto-Remediacao ─────────────────────────────────────────
    /**
     * Retorna eventos de auto-remediacao do Watchdog.
     * Filtros opcionais:
     * - server: filtra por server_id (ex: "vps-deploy-01")
     * - status: filtra por final_status (ex: "CircuitOpen", "Failed")
     * - limit: numero maximo de eventos retornados (padrao: 50)
     */
    @GET("watchdog/events")
    suspend fun getWatchdogEvents(
        @Query("server") serverId: String? = null,
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = 50
    ): WatchdogEventsResponse

    @DELETE("watchdog/events")
    suspend fun deleteWatchdogEvents(): GenericResponse

    @POST("watchdog/reset")
    suspend fun resetWatchdog(): GenericResponse

    // ─── Audit Log ─────────────────────────────────────────────────────
    @GET("audit/logs")
    suspend fun getAuditLogs(
        @Query("limit") limit: Int = 100,
        @Query("action") action: String? = null
    ): AuditLogResponse

    @DELETE("audit/logs")
    suspend fun clearAuditLogs(): GenericResponse

    // ─── Monitoramento Docker ─────────────────────────────────────────────
    @GET("docker/containers")
    suspend fun getDockerContainers(): DockerMetrics

    // ─── PHP-FPM Pools por site ─────────────────────────────────────────────
    @GET("phpfpm/pools")
    suspend fun getPhpFpmPools(): PhpFpmResponse

    // ─── Verificacao SSL ───────────────────────────────────────────────────
    @GET("ssl/check")
    suspend fun checkSsl(): SslCheckResponse

    // ─── Status de Backup ─────────────────────────────────────────────────
    @GET("backups/status")
    suspend fun getBackupStatus(): BackupStatus

    // ─── Configuracao do Agente ───────────────────────────────────────────
    @GET("config")
    suspend fun getAgentConfig(): AgentRuntimeConfig

    @POST("config")
    suspend fun updateAgentConfig(@Body config: Map<String, @JvmSuppressWildcards Any>): GenericResponse
}
