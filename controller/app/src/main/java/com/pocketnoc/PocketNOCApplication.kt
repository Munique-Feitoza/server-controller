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
        // Cria canais de notificacao (FCM + alertas locais)
        com.pocketnoc.notifications.PocketNOCNotificationHelper.createNotificationChannels(this)
        // Inicia o monitoramento periodico de alertas (fallback de polling 15min)
        AlertMonitoringManager.startMonitoring(this)
        // Inicia subscriber ntfy.sh em foreground (push real-time)
        com.pocketnoc.notifications.NtfySubscriberService.start(this)
    }
}
