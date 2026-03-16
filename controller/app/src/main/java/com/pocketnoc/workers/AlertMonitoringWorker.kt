package com.pocketnoc.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pocketnoc.data.repository.ServerRepository
import com.pocketnoc.utils.SecurityNotificationManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Worker que monitora alertas de todos os servidores periodicamente
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

            // Monitora alertas para cada servidor
            for (server in servers) {
                try {
                    val alerts = repository.getAlerts(server)

                    // Dispara notificação para cada alerta
                    for (alert in alerts.alerts) {
                        notificationManager.sendAlert(server.name, alert)
                    }

                } catch (e: Exception) {
                    // Log do erro, mas não falha o work inteiro
                    e.printStackTrace()
                }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            // Retry ao falhar
            Result.retry()
        }
    }
}
