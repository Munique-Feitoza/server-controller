package com.pocketnoc.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.pocketnoc.R
import com.pocketnoc.MainActivity
import java.text.SimpleDateFormat
import java.util.*

/**
 * Widget de status dos servidores para a home screen.
 * Exibe um resumo simplificado com o último estado conhecido.
 *
 * Para funcionar precisa do layout res/layout/widget_server_status.xml
 * e do metadata res/xml/widget_server_status_info.xml
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
    }

    companion object {
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_server_status)

            // Lê dados do SharedPreferences (gravado pelo AlertMonitoringWorker)
            val prefs = context.getSharedPreferences("widget_data", Context.MODE_PRIVATE)
            val serverCount = prefs.getInt("server_count", 0)
            val healthySvr = prefs.getInt("healthy_count", 0)
            val alertCount = prefs.getInt("alert_count", 0)
            val lastUpdate = prefs.getLong("last_update", 0L)

            views.setTextViewText(R.id.widget_title, "POCKET NOC")
            views.setTextViewText(R.id.widget_server_count, "$serverCount servers")
            views.setTextViewText(R.id.widget_healthy_count, "$healthySvr healthy")
            views.setTextViewText(R.id.widget_alert_count, if (alertCount > 0) "$alertCount alerts" else "All clear")

            val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val updateText = if (lastUpdate > 0) "Updated ${dateFormat.format(Date(lastUpdate))}" else "No data"
            views.setTextViewText(R.id.widget_last_update, updateText)

            // Tap abre o app
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
