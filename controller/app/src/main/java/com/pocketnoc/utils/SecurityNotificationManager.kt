package com.pocketnoc.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pocketnoc.R
import com.pocketnoc.data.models.Alert
import com.pocketnoc.data.models.AlertType

/**
 * Gerencia notificações de segurança e alertas de telemetria do sistema
 */
class SecurityNotificationManager(private val context: Context) {

    companion object {
        private const val CHANNEL_ID_SECURITY = "security_alerts"
        private const val CHANNEL_NAME_SECURITY = "Alertas de Segurança"
        private const val CHANNEL_DESC_SECURITY = "Notificações de tentativas de intrusão e falhas de segurança"

        private const val CHANNEL_ID_TELEMETRY = "telemetry_alerts"
        private const val CHANNEL_NAME_TELEMETRY = "Alertas de Telemetria"
        private const val CHANNEL_DESC_TELEMETRY = "Notificações de anomalias de sistema (CPU, memória, disco, temperatura)"

        private const val CHANNEL_ID_SYSTEM = "system_alerts"
        private const val CHANNEL_NAME_SYSTEM = "Alertas do Sistema"
        private const val CHANNEL_DESC_SYSTEM = "Notificações de eventos do sistema"
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val securityChannel = NotificationChannel(
                CHANNEL_ID_SECURITY,
                CHANNEL_NAME_SECURITY,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC_SECURITY
                enableVibration(true)
            }

            val telemetryChannel = NotificationChannel(
                CHANNEL_ID_TELEMETRY,
                CHANNEL_NAME_TELEMETRY,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESC_TELEMETRY
                enableVibration(true)
            }

            val systemChannel = NotificationChannel(
                CHANNEL_ID_SYSTEM,
                CHANNEL_NAME_SYSTEM,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESC_SYSTEM
                enableVibration(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(securityChannel)
            notificationManager.createNotificationChannel(telemetryChannel)
            notificationManager.createNotificationChannel(systemChannel)
        }
    }

    // ============== Alertas de Segurança ==============

    /**
     * Dispara um alerta de tentativa de intrusão
     */
    fun sendIntrusionAlert(serverName: String, failureCount: Int) {
        val title = "🚨 ALERTA DE SEGURANÇA"
        val message = "Múltiplas falhas ($failureCount) detectadas no servidor $serverName. Possível tentativa de intrusão!"

        showNotification(title, message, 1001, CHANNEL_ID_SECURITY)
    }

    /**
     * Dispara um aviso de modo de emergência ativado
     */
    fun sendEmergencyModeWarning(serverName: String) {
        val title = "⚠️ Modo de Emergência Ativo"
        val message = "Conexão com $serverName estabelecida sem segurança total (Bypass ativo)."

        showNotification(title, message, 1002, CHANNEL_ID_SECURITY)
    }

    // ============== Alertas de Telemetria ==============

    /**
     * Dispara um aviso de carga alta de CPU
     */
    fun sendHighCpuAlert(serverName: String, cpuUsage: Double) {
        val title = "⚡ ALTA CARGA DETECTADA"
        val message = "O servidor $serverName está com uso de CPU em ${String.format("%.1f", cpuUsage)}%!"

        showNotification(title, message, 1003, CHANNEL_ID_TELEMETRY)
    }

    /**
     * Dispara um alerta de memória alta
     */
    fun sendHighMemoryAlert(serverName: String, memoryUsage: Double) {
        val title = "🧠 MEMÓRIA ALTA"
        val message = "O servidor $serverName está com ${String.format("%.1f", memoryUsage)}% de memória em uso!"

        showNotification(title, message, 1004, CHANNEL_ID_TELEMETRY)
    }

    /**
     * Dispara um alerta de disco cheio
     */
    fun sendHighDiskAlert(serverName: String, mountPoint: String, diskUsage: Double) {
        val title = "💾 ESPAÇO EM DISCO CRÍTICO"
        val message = "Disco $mountPoint do servidor $serverName está com ${String.format("%.1f", diskUsage)}% de uso!"

        showNotification(title, message, 1005, CHANNEL_ID_TELEMETRY)
    }

    /**
     * Dispara um alerta de temperatura elevada
     */
    fun sendHighTemperatureAlert(serverName: String, temperature: Double) {
        val title = "🌡️ TEMPERATURA ELEVADA"
        val message = "O servidor $serverName está com temperatura de ${String.format("%.1f", temperature)}°C!"

        showNotification(title, message, 1006, CHANNEL_ID_TELEMETRY)
    }

    /**
     * Dispara um alerta de reboot recente
     */
    fun sendRecentRebootAlert(serverName: String, uptimeMinutes: Long) {
        val title = "🔄 SERVIDOR REINICIADO"
        val message = "O servidor $serverName foi reiniciado há pouco (uptime: $uptimeMinutes min)"

        showNotification(title, message, 1007, CHANNEL_ID_SYSTEM)
    }

    /**
     * Dispara um alerta de ameaça de segurança
     */
    fun sendSecurityThreatAlert(serverName: String, failedAttempts: Int) {
        val title = "🚨 AMEAÇA DE SEGURANÇA"
        val message = "Servidor $serverName detectou $failedAttempts tentativas de login não autorizado!"

        showNotification(title, message, 1008, CHANNEL_ID_SECURITY)
    }

    /**
     * Processa um alerta genérico e dispara a notificação apropriada
     */
    fun sendAlert(serverName: String, alert: Alert) {
        val notificationId = (alert.timestamp % Int.MAX_VALUE).toInt()
        val emoji = when(alert.alertType) {
            AlertType.HIGH_CPU -> "⚡"
            AlertType.HIGH_MEMORY -> "🧠"
            AlertType.HIGH_DISK -> "💾"
            AlertType.HIGH_TEMPERATURE -> "🌡️"
            AlertType.SECURITY_THREAT -> "🚨"
            AlertType.RECENT_REBOOT -> "🔄"
        }
        val label = when(alert.alertType) {
            AlertType.HIGH_CPU -> "ALTA CARGA DE CPU"
            AlertType.HIGH_MEMORY -> "MEMÓRIA ALTA"
            AlertType.HIGH_DISK -> "ESPAÇO EM DISCO CRÍTICO"
            AlertType.HIGH_TEMPERATURE -> "TEMPERATURA ELEVADA"
            AlertType.SECURITY_THREAT -> "AMEAÇA DE SEGURANÇA"
            AlertType.RECENT_REBOOT -> "SERVIDOR REINICIADO"
        }
        val title = "$emoji $label — $serverName"

        showNotification(title, alert.message, notificationId, CHANNEL_ID_TELEMETRY)
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun showNotification(title: String, message: String, notificationId: Int, channelId: String) {
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_security) // Assumindo que este ícone existe ou será criado
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
    }
}

