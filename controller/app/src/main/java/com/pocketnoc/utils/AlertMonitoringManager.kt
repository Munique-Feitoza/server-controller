package com.pocketnoc.utils

import android.content.Context
import androidx.work.*
import com.pocketnoc.workers.AlertMonitoringWorker
import java.util.concurrent.TimeUnit

/**
 * Gerencia o agendamento e execução do monitoramento periódico de alertas
 */
object AlertMonitoringManager {

    private const val ALERT_MONITORING_WORK_TAG = "alert_monitoring"
    private const val ALERT_CHECK_INTERVAL_MINUTES = 5L // Verifica a cada 5 minutos
    private const val MIN_BACKOFF_MILLIS = 15000L // 15 segundos (padrão do WorkManager)

    /**
     * Inicia o monitoramento periódico de alertas
     */
    fun startMonitoring(context: Context) {
        val alertMonitoringRequest = PeriodicWorkRequestBuilder<AlertMonitoringWorker>(
            ALERT_CHECK_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        )
            .addTag(ALERT_MONITORING_WORK_TAG)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ALERT_MONITORING_WORK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            alertMonitoringRequest
        )
    }

    /**
     * Para o monitoramento de alertas
     */
    fun stopMonitoring(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(ALERT_MONITORING_WORK_TAG)
    }

    /**
     * Força uma execução imediata do monitoramento de alertas
     */
    fun forceCheckNow(context: Context) {
        val immediateWork = OneTimeWorkRequestBuilder<AlertMonitoringWorker>()
            .addTag("${ALERT_MONITORING_WORK_TAG}_immediate")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "${ALERT_MONITORING_WORK_TAG}_immediate",
            ExistingWorkPolicy.REPLACE,
            immediateWork
        )
    }
}
