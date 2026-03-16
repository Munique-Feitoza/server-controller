package com.pocketnoc

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.pocketnoc.utils.AlertMonitoringManager

@HiltAndroidApp
class PocketNOCApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Inicia o monitoramento periódico de alertas
        AlertMonitoringManager.startMonitoring(this)
    }
}
