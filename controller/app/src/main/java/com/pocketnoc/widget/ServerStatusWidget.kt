package com.pocketnoc.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.pocketnoc.R
import com.pocketnoc.MainActivity
import com.pocketnoc.utils.AlertMonitoringManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * Widget de status dos servidores para a tela inicial.
 * Mostra contagem de servidores, saudaveis e alertas ativos.
 * Dados gravados pelo AlertMonitoringWorker no SharedPreferences.
 */
class ServerStatusWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }

        // Dispara o Worker imediatamente para preencher os dados
        AlertMonitoringManager.forceCheckNow(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Primeiro widget adicionado — forca coleta de dados
        AlertMonitoringManager.forceCheckNow(context)
    }

    companion object {
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_server_status)

            val prefs = context.getSharedPreferences("widget_data", Context.MODE_PRIVATE)
            val serverCount = prefs.getInt("server_count", 0)
            val healthyCount = prefs.getInt("healthy_count", 0)
            val alertCount = prefs.getInt("alert_count", 0)
            val recentAlerts = prefs.getString("recent_alerts", "") ?: ""
            val lastUpdate = prefs.getLong("last_update", 0L)

            views.setTextViewText(R.id.widget_title, "POCKET NOC")
            views.setTextViewText(R.id.widget_server_count, "$serverCount")
            views.setTextViewText(R.id.widget_healthy_count, "$healthyCount")
            views.setTextViewText(R.id.widget_alert_count, "$alertCount")
            views.setTextViewText(
                R.id.widget_recent_alerts,
                if (recentAlerts.isEmpty()) "sem alertas ativos" else recentAlerts
            )

            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val timeText = if (lastUpdate > 0) timeFormat.format(Date(lastUpdate)) else "--:--"
            views.setTextViewText(R.id.widget_last_update, timeText)

            // Toque abre o app
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = android.app.PendingIntent.getActivity(
                context, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
