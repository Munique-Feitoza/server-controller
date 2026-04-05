package com.pocketnoc.workers

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pocketnoc.data.repository.ServerRepository
import com.pocketnoc.utils.SecurityNotificationManager
import com.pocketnoc.widget.ServerStatusWidget
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
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
            var healthyCount = 0
            var checkedCount = 0

            // Grava contagem de servidores imediatamente (dados locais, sempre funciona)
            updateWidgetData(servers.size, servers.size, 0)

            for (server in servers) {
                try {
                    val alerts = repository.getAlerts(server)
                    checkedCount++

                    if (alerts.count == 0) {
                        healthyCount++
                    }
                    totalAlerts += alerts.count

                    for (alert in alerts.alerts) {
                        notificationManager.sendAlert(server.name, alert)
                    }
                } catch (e: Exception) {
                    // Servidor offline — conta como nao-saudavel mas nao falha o worker
                    checkedCount++
                    Log.w("AlertWorker", "Falha ao checar ${server.name}: ${e.message}")
                }
            }

            // Atualiza widget com dados reais apos checar todos
            if (checkedCount > 0) {
                updateWidgetData(servers.size, healthyCount, totalAlerts)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("AlertWorker", "Falha geral: ${e.message}")
            Result.retry()
        }
    }

    private fun updateWidgetData(serverCount: Int, healthyCount: Int, alertCount: Int) {
        applicationContext.getSharedPreferences("widget_data", Context.MODE_PRIVATE)
            .edit()
            .putInt("server_count", serverCount)
            .putInt("healthy_count", healthyCount)
            .putInt("alert_count", alertCount)
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
}
