package com.pocketnoc.data.websocket

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Gerenciador de WebSocket real para conexão persistente com o agente
 * Pode ser usado quando o agent for upgradado para suportar WebSocket
 */
class WebSocketTelemetryManager(
    private val httpClient: OkHttpClient,
    private val serverUrl: String,
    private val token: String
) : WebSocketListener() {
    
    private val tag = "WebSocketTelemetry"
    
    private val _messageFlow = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val messageFlow: SharedFlow<String> = _messageFlow.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var webSocket: WebSocket? = null
    private var isConnected = false

    /**
     * Conecta ao WebSocket do agente
     * Esperado que o agente tenha um endpoint /ws/telemetry
     */
    fun connect() {
        if (isConnected || webSocket != null) {
            Log.w(tag, "Already connected or connecting")
            return
        }

        try {
            val wsUrl = serverUrl
                .replace("http://", "ws://")
                .replace("https://", "wss://")
                .let { if (it.endsWith("/")) it else "$it/" }
                .plus("ws/telemetry")

            val request = Request.Builder()
                .url(wsUrl)
                .header("Authorization", "Bearer $token")
                .build()

            val client = httpClient.newBuilder()
                .readTimeout(0, TimeUnit.MILLISECONDS) // Importante para WebSocket
                .build()

            webSocket = client.newWebSocket(request, this)
            Log.d(tag, "WebSocket connection initiated to $wsUrl")
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize WebSocket: ${e.message}", e)
        }
    }

    /**
     * Desconecta do WebSocket
     */
    fun disconnect() {
        try {
            webSocket?.close(1000, "Client closing")
            webSocket = null
            isConnected = false
            scope.cancel() // Cancel coroutines on disconnect
            Log.d(tag, "WebSocket disconnected")
        } catch (e: Exception) {
            Log.e(tag, "Error closing WebSocket: ${e.message}", e)
        }
    }

    /**
     * Verifica se está conectado
     */
    fun isConnected(): Boolean = isConnected && webSocket != null

    /**
     * Envia uma mensagem através do WebSocket
     */
    fun send(message: String): Boolean {
        return try {
            webSocket?.send(message) ?: false
        } catch (e: Exception) {
            Log.e(tag, "Error sending message: ${e.message}", e)
            false
        }
    }

    // WebSocketListener callbacks

    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
        Log.d(tag, "WebSocket opened: ${response.code}")
        isConnected = true
        // Envia comando para iniciar streaming (depende da implementação do agent)
        send("""{"action":"stream_telemetry","interval":5000}""")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        Log.d(tag, "Message received (${text.length} chars)")
        scope.launch {
            try {
                _messageFlow.emit(text)
            } catch (e: Exception) {
                Log.e(tag, "Error emitting message: ${e.message}", e)
            }
        }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        Log.d(tag, "Binary message received (${bytes.size} bytes)")
        onMessage(webSocket, bytes.utf8())
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(tag, "WebSocket closing: $code - $reason")
        webSocket.close(code, reason)
        isConnected = false
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(tag, "WebSocket closed: $code - $reason")
        isConnected = false
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
        Log.e(tag, "WebSocket failure: ${t.message}", t)
        isConnected = false
        // Pode implementar reconexão automática se necessário
    }

}
