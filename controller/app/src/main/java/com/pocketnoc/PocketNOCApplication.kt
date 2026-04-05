package com.pocketnoc

import android.app.Application
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import com.pocketnoc.utils.AlertMonitoringManager
import javax.inject.Inject
import androidx.hilt.work.HiltWorkerFactory

@HiltAndroidApp
class PocketNOCApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        // Cria canais de notificação (FCM + alertas locais)
        com.pocketnoc.notifications.PocketNOCNotificationHelper.createNotificationChannels(this)
        // Inicia o monitoramento periódico de alertas
        AlertMonitoringManager.startMonitoring(this)
    }
}
