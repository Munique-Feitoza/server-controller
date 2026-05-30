package com.pocketnoc

import android.app.Application
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import com.pocketnoc.utils.AlertMonitoringManager
import com.pocketnoc.data.local.ServerSeed
import com.pocketnoc.data.repository.ServerRepository
import javax.inject.Inject
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class PocketNOCApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var serverRepository: ServerRepository

    // SupervisorJob isola falhas entre filhos, mas NÃO captura exceções — sem um
    // CoroutineExceptionHandler, qualquer throw aqui mata o processo no startup.
    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, e -> Log.e(TAG, "Falha no appScope", e) }
    )

    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        com.pocketnoc.notifications.PocketNOCNotificationHelper.createNotificationChannels(this)
        // Seed dos 4 servidores do local.properties (idempotente — só roda se DB vazio)
        appScope.launch { ServerSeed.seedIfEmpty(serverRepository) }
        // WorkManager é seguro de agendar em background.
        runCatching { AlertMonitoringManager.startMonitoring(this) }
            .onFailure { Log.e(TAG, "Falha ao agendar monitoramento de alertas", it) }
        // NÃO iniciar o NtfySubscriberService aqui: Application.onCreate roda também em
        // background (WorkManager, widget, restart do serviço) e, no Android 12+,
        // startForegroundService() em background lança ForegroundServiceStartNotAllowedException
        // — era a causa do "app fecha do nada". O serviço passa a ser iniciado pela
        // MainActivity (foreground garantido).
    }

    private companion object {
        const val TAG = "PocketNOCApp"
    }
}
