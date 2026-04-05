package com.pocketnoc.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pocketnoc.MainActivity
import com.pocketnoc.R

/**
 * Servico de notificacoes push via Firebase Cloud Messaging.
 *
 * Para ativar, adicione o google-services.json ao projeto e descomente
 * a heranca de FirebaseMessagingService.
 *
 * Tipos de notificacao suportados:
 * - alert: Alertas de CPU/RAM/Disk
 * - watchdog: Eventos de auto-remediacao
 * - security: Ameacas de seguranca
 *
 * O agente Rust pode enviar notificacoes via Firebase Admin SDK
 * ou via HTTP v1 API diretamente.
 */
object PocketNOCNotificationHelper {

    private const val CHANNEL_ALERTS = "pocket_noc_alerts"
    private const val CHANNEL_WATCHDOG = "pocket_noc_watchdog"
    private const val CHANNEL_SECURITY = "pocket_noc_security"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Server Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas criticos do servidor (CPU, RAM, Disco)"
                enableVibration(true)
            }

            val watchdogChannel = NotificationChannel(
                CHANNEL_WATCHDOG,
                "Watchdog Events",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Eventos de auto-remediacao do WatchdogEngine"
            }

            val securityChannel = NotificationChannel(
                CHANNEL_SECURITY,
                "Security Alerts",
                NotificationManager.IMPORTANCE_MAX
            ).apply {
                description = "Ameacas de seguranca e tentativas de intrusao"
                enableVibration(true)
                enableLights(true)
            }

            manager.createNotificationChannels(listOf(alertChannel, watchdogChannel, securityChannel))
        }
    }

    fun showNotification(
        context: Context,
        title: String,
        message: String,
        type: String = "alert",
        notificationId: Int = System.currentTimeMillis().toInt()
    ) {
        val channelId = when (type) {
            "security" -> CHANNEL_SECURITY
            "watchdog" -> CHANNEL_WATCHDOG
            else -> CHANNEL_ALERTS
        }

        val priority = when (type) {
            "security" -> NotificationCompat.PRIORITY_MAX
            "alert" -> NotificationCompat.PRIORITY_HIGH
            else -> NotificationCompat.PRIORITY_DEFAULT
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }
}

/**
 * Stub para Firebase Cloud Messaging.
 *
 * Para ativar FCM:
 * 1. Adicione google-services.json em controller/app/
 * 2. Adicione plugin: id("com.google.gms.google-services") no build.gradle.kts
 * 3. Adicione dep: implementation("com.google.firebase:firebase-messaging-ktx:23.4.0")
 * 4. Descomente a classe abaixo e substitua este stub:
 *
 * class PocketNOCFirebaseService : FirebaseMessagingService() {
 *     override fun onMessageReceived(message: RemoteMessage) {
 *         val data = message.data
 *         val type = data["type"] ?: "alert"
 *         val title = data["title"] ?: message.notification?.title ?: "PocketNOC"
 *         val body = data["body"] ?: message.notification?.body ?: ""
 *         PocketNOCNotificationHelper.showNotification(this, title, body, type)
 *     }
 *
 *     override fun onNewToken(token: String) {
 *         // Envia token para o backend para registrar este dispositivo
 *         Log.d("FCM", "Novo token: $token")
 *     }
 * }
 */
