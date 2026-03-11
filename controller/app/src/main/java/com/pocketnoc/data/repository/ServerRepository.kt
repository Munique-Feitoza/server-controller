package com.pocketnoc.data.repository

import com.pocketnoc.data.api.AgentApiService
import com.pocketnoc.data.models.*

class ServerRepository(private val apiService: AgentApiService) {

    suspend fun healthCheck(): Result<HealthCheckResponse> = try {
        Result.success(apiService.healthCheck())
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getTelemetry(token: String): Result<SystemTelemetry> = try {
        val authHeader = "Bearer $token"
        Result.success(apiService.getTelemetry(authHeader))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getServiceStatus(
        serviceName: String,
        token: String
    ): Result<ServiceInfo> = try {
        val authHeader = "Bearer $token"
        Result.success(apiService.getServiceStatus(serviceName, authHeader))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun listCommands(token: String): Result<List<EmergencyCommand>> = try {
        val authHeader = "Bearer $token"
        val response = apiService.listCommands(authHeader)
        Result.success(response["commands"] ?: emptyList())
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun executeCommand(
        commandId: String,
        token: String
    ): Result<CommandResult> = try {
        val authHeader = "Bearer $token"
        Result.success(apiService.executeCommand(commandId, authHeader))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getMetrics(token: String): Result<String> = try {
        val authHeader = "Bearer $token"
        Result.success(apiService.getMetrics(authHeader))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
