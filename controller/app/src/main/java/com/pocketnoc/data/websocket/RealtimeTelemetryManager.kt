package com.pocketnoc.data.websocket

import android.util.Log
import com.pocketnoc.data.api.AgentApiService
import com.pocketnoc.data.models.SystemTelemetry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Gerenciador de telemetria em tempo real usando streaming eficiente
 * 
 * Nota: Esta implementação usa polling otimizado ao invés de WebSocket puro
 * para compatibilidade com arquitetura minimalista do agent.
 * O agente pode ser upgradado para WebSocket mantendo compatibilidade.
 */
class RealtimeTelemetryManager(
    private val apiService: AgentApiService
) {
    private val tag = "RealtimeTelemetry"

    private val _telemetryFlow = MutableSharedFlow<SystemTelemetry>(
        replay = 1,
        extraBufferCapacity = 10
    )
    val telemetryFlow: SharedFlow<SystemTelemetry> = _telemetryFlow.asSharedFlow()

    private var streamingJob: Job? = null
    private var isStreaming = false

    /**
     * Inicia o streaming de telemetria em tempo real
     * @param intervalMs Intervalo entre requisições em milissegundos (padrão 10 segundos)
     */
    fun startStreaming(intervalMs: Long = 10000L) {
        if (isStreaming) return

        streamingJob = CoroutineScope(Dispatchers.IO + Job()).launch {
            isStreaming = true
            try {
                while (isActive && isStreaming) {
                    try {
                        val telemetry = apiService.getTelemetry()
                        _telemetryFlow.emit(telemetry)
                        Log.d(tag, "Telemetry streamed: CPU=${telemetry.cpu.usagePercent}%")
                    } catch (e: Exception) {
                        Log.e(tag, "Error fetching telemetry: ${e.message}", e)
                    }

                    delay(intervalMs)
                }
            } finally {
                isStreaming = false
            }
        }
    }

    /**
     * Para o streaming de telemetria
     */
    fun stopStreaming() {
        isStreaming = false
        streamingJob?.cancel()
        streamingJob = null
    }

    /**
     * Verifica se o streaming está ativo
     */
    fun isStreamingActive(): Boolean = isStreaming && streamingJob?.isActive == true

    /**
     * Aguarda por uma única métrica e para
     */
    suspend fun fetchOnce(): SystemTelemetry? {
        return try {
            apiService.getTelemetry()
        } catch (e: Exception) {
            Log.e(tag, "Error fetching telemetry: ${e.message}", e)
            null
        }
    }
}
