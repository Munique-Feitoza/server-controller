package com.pocketnoc.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pocketnoc.R

/**
 * Gerencia notificações de segurança do sistema HackerSec
 */
class SecurityNotificationManager(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "security_alerts"
        private const val CHANNEL_NAME = "Alertas de Segurança"
        private const val CHANNEL_DESC = "Notificações de tentativas de intrusão e falhas de segurança"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Dispara um alerta de tentativa de intrusão
     */
    fun sendIntrusionAlert(serverName: String, failureCount: Int) {
        val title = "🚨 ALERTA DE SEGURANÇA"
        val message = "Múltiplas falhas ($failureCount) detectadas no servidor $serverName. Possível tentativa de intrusão!"
        
        showNotification(title, message, 1001)
    }

    /**
     * Dispara um aviso de modo de emergência ativado
     */
    fun sendEmergencyModeWarning(serverName: String) {
        val title = "⚠️ Modo de Emergência Ativo"
        val message = "Conexão com $serverName estabelecida sem segurança total (Bypass ativo)."
        
        showNotification(title, message, 1002)
    }

    /**
     * Dispara um aviso de carga alta de CPU
     */
    fun sendHighCpuAlert(serverName: String, cpuUsage: Double) {
        val title = "⚡ ALTA CARGA DETECTADA"
        val message = "O servidor $serverName está com uso de CPU em ${String.format("%.1f", cpuUsage)}%!"
        
        showNotification(title, message, 1003)
    }

    private fun showNotification(title: String, message: String, notificationId: Int) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_security) // Assumindo que este ícone existe ou será criado
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
    }
}
