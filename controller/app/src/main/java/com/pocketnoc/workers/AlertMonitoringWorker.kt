package com.pocketnoc.workers

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pocketnoc.data.local.entities.ServerEntity
import com.pocketnoc.data.models.AlertsResponse
import com.pocketnoc.data.repository.ServerRepository
import com.pocketnoc.utils.SecurityNotificationManager
import com.pocketnoc.widget.ServerStatusWidget
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Worker que monitora alertas de todos os servidores periodicamente.
 * Tambem atualiza os dados do widget na home screen.
 */
@HiltWorker
class AlertMonitoringWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: ServerRepository,
    private val notificationManager: SecurityNotificationManager
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val servers = repository.getAllServers().first()
            var totalAlerts = 0
            var onlineCount = 0
            val recentAlerts = mutableListOf<Pair<Long, String>>()

            for (server in servers) {
                val alerts = fetchAlertsWithRetry(server)
                if (alerts != null) {
                    onlineCount++
                    totalAlerts += alerts.count
                    for (alert in alerts.alerts) {
                        notificationManager.sendAlert(server.name, alert)
                        recentAlerts += alert.timestamp to "${server.name.uppercase()}: ${alert.message}"
                    }
                }
            }

            // Ordena por timestamp desc e pega os 3 mais recentes
            val top3 = recentAlerts
                .sortedByDescending { it.first }
                .take(3)
                .joinToString("\n") { it.second }

            // Só publica no widget depois de ter checado todos os servidores
            // (com retries), para não expor estados parciais de falha transitória.
            updateWidgetData(servers.size, onlineCount, totalAlerts, top3)

            Result.success()
        } catch (e: Exception) {
            Log.e("AlertWorker", "Falha geral: ${e.message}")
            Result.retry()
        }
    }

    private suspend fun fetchAlertsWithRetry(server: ServerEntity): AlertsResponse? {
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return repository.getAlerts(server)
            } catch (e: Exception) {
                Log.w(
                    "AlertWorker",
                    "Tentativa ${attempt + 1}/$MAX_ATTEMPTS falhou para ${server.name}: ${e.message}"
                )
                if (attempt < MAX_ATTEMPTS - 1) delay(RETRY_DELAY_MS)
            }
        }
        return null
    }

    private fun updateWidgetData(serverCount: Int, onlineCount: Int, alertCount: Int, recentAlerts: String) {
        applicationContext.getSharedPreferences("widget_data", Context.MODE_PRIVATE)
            .edit()
            .putInt("server_count", serverCount)
            .putInt("healthy_count", onlineCount)
            .putInt("alert_count", alertCount)
            .putString("recent_alerts", recentAlerts)
            .putLong("last_update", System.currentTimeMillis())
            .apply()

        // Notifica todos os widgets para se atualizarem
        try {
            val manager = AppWidgetManager.getInstance(applicationContext)
            val ids = manager.getAppWidgetIds(
                ComponentName(applicationContext, ServerStatusWidget::class.java)
            )
            for (id in ids) {
                ServerStatusWidget.updateWidget(applicationContext, manager, id)
            }
        } catch (e: Exception) {
            Log.w("AlertWorker", "Falha ao atualizar widget: ${e.message}")
        }
    }

    companion object {
        private const val MAX_ATTEMPTS = 2
        private const val RETRY_DELAY_MS = 2_000L
    }
}
