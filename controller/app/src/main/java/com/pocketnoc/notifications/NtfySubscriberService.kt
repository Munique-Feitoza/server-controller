package com.pocketnoc.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.JsonParser
import com.pocketnoc.R
import com.pocketnoc.data.local.entities.ServerEntity
import com.pocketnoc.data.repository.ServerRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Foreground service que mantém conexão streaming com ntfy.sh por servidor cadastrado,
 * recebendo push real-time sempre que o agent dispara um alerta.
 *
 * Topic ntfy é descoberto via GET /config do agent (campo `ntfy_topic`).
 * Reconecta com exp backoff em caso de falha de rede.
 */
@AndroidEntryPoint
class NtfySubscriberService : Service() {

    @Inject lateinit var repository: ServerRepository

    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + supervisor)
    private val activeSubs = mutableMapOf<Int, Job>()

    /** Cliente compartilhado com timeouts longos pra long-polling do ntfy. */
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // streaming infinito
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    override fun onCreate() {
        super.onCreate()
        createForegroundChannel()
        startForeground(FG_NOTIFICATION_ID, buildPersistentNotification("Conectando..."))
        observeServers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        supervisor.cancel()
    }

    private fun observeServers() {
        scope.launch {
            repository.getAllServers().distinctUntilChanged().collect { servers ->
                syncSubscriptions(servers)
                updatePersistentNotification(servers.size)
            }
        }
    }

    private fun syncSubscriptions(servers: List<ServerEntity>) {
        val activeIds = servers.map { it.id }.toSet()

        // Cancela subs de servers removidos
        val toRemove = activeSubs.keys.filter { it !in activeIds }
        for (id in toRemove) {
            activeSubs.remove(id)?.cancel()
        }

        // Inicia subs novas
        for (server in servers) {
            if (server.id !in activeSubs) {
                activeSubs[server.id] = scope.launch { runSubscription(server) }
            }
        }
    }

    private suspend fun runSubscription(server: ServerEntity) {
        var backoffMs = 5_000L
        while (currentCoroutineContext().isActive) {
            val topic = fetchTopic(server)
            if (topic == null) {
                Log.w(TAG, "ntfy_topic vazio pra ${server.name}, retry em 60s")
                delay(60_000)
                continue
            }
            try {
                Log.i(TAG, "Subscrevendo ${server.name} no topic $topic")
                streamTopic(server.name, topic)
                backoffMs = 5_000L
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Sub ${server.name} caiu: ${e.message}. Retry em ${backoffMs}ms")
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(120_000L)
            }
        }
    }

    private suspend fun fetchTopic(server: ServerEntity): String? {
        return try {
            withContext(Dispatchers.IO) {
                repository.getAgentConfig(server).ntfyTopic?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao buscar /config de ${server.name}: ${e.message}")
            null
        }
    }

    private suspend fun streamTopic(serverName: String, topic: String) {
        val url = "https://ntfy.sh/$topic/json"
        val request = Request.Builder().url(url).build()

        withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("ntfy retornou ${response.code}")
                }
                val source = response.body?.source() ?: return@use
                while (!source.exhausted() && currentCoroutineContext().isActive) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank()) continue
                    handleNtfyMessage(serverName, line)
                }
            }
        }
    }

    private fun handleNtfyMessage(serverName: String, json: String) {
        try {
            val obj = JsonParser.parseString(json).asJsonObject
            val event = obj.get("event")?.asString ?: return
            // Eventos relevantes: "message" (push real). "open"/"keepalive" ignoramos.
            if (event != "message") return

            val title = obj.get("title")?.asString ?: "Alerta $serverName"
            val message = obj.get("message")?.asString ?: ""
            val priority = obj.get("priority")?.asInt ?: 3
            val id = obj.get("id")?.asString?.hashCode() ?: System.currentTimeMillis().toInt()

            showAlertNotification(id, "$title — $serverName", message, priority)
        } catch (e: Exception) {
            Log.w(TAG, "Mensagem ntfy invalida: ${e.message}")
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun showAlertNotification(id: Int, title: String, message: String, priority: Int) {
        val importance = if (priority >= 4) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT
        val channel = if (priority >= 4) CHANNEL_ID_ALERT_HIGH else CHANNEL_ID_ALERT_NORMAL

        val notif = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_security)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(importance)
            .setAutoCancel(true)
            .build()

        getNotificationManager().notify(id, notif)
    }

    private fun createForegroundChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getNotificationManager()

        val fg = NotificationChannel(
            CHANNEL_ID_FG, "Conexão de alertas",
            NotificationManager.IMPORTANCE_MIN
        ).apply { description = "Mantém o PocketNOC conectado pra receber alertas em tempo real" }
        nm.createNotificationChannel(fg)

        val high = NotificationChannel(
            CHANNEL_ID_ALERT_HIGH, "Alertas críticos",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alertas CRITICAL/HIGH recebidos via push em tempo real"
            enableVibration(true)
        }
        nm.createNotificationChannel(high)

        val normal = NotificationChannel(
            CHANNEL_ID_ALERT_NORMAL, "Alertas",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Alertas WARNING/INFO recebidos via push" }
        nm.createNotificationChannel(normal)
    }

    private fun buildPersistentNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID_FG)
            .setSmallIcon(R.drawable.ic_security)
            .setContentTitle("PocketNOC ativo")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun updatePersistentNotification(serverCount: Int) {
        val text = if (serverCount == 0) "Aguardando servidores"
        else "Conectado a $serverCount servidor${if (serverCount > 1) "es" else ""}"
        getNotificationManager().notify(FG_NOTIFICATION_ID, buildPersistentNotification(text))
    }

    private fun getNotificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val TAG = "NtfySubscriber"
        private const val FG_NOTIFICATION_ID = 9001
        private const val CHANNEL_ID_FG = "ntfy_subscriber_fg"
        private const val CHANNEL_ID_ALERT_HIGH = "ntfy_alerts_high"
        private const val CHANNEL_ID_ALERT_NORMAL = "ntfy_alerts_normal"

        fun start(context: Context) {
            val intent = Intent(context, NtfySubscriberService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NtfySubscriberService::class.java))
        }
    }
}
