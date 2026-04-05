package com.pocketnoc.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.pocketnoc.data.local.entities.AlertEntity
import com.pocketnoc.data.local.entities.TelemetryHistoryEntity
import com.pocketnoc.ui.theme.*
import com.pocketnoc.ui.viewmodels.DashboardViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    viewModel: DashboardViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val alertHistory by viewModel.alertHistory.collectAsState()
    val telemetryHistory by viewModel.telemetryHistory.collectAsState()
    var exporting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFF0A0F1E), DarkSurface.copy(alpha = 0.95f))))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(NeonGreen.copy(alpha = 0.10f))
                            .border(1.dp, NeonGreen.copy(alpha = 0.35f), RoundedCornerShape(9.dp))
                            .clickable(onClick = onNavigateBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = NeonGreen, modifier = Modifier.size(17.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "EXPORT CENTER",
                            color = NeonGreen,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 3.sp
                        )
                        Text("Exportar dados e relatórios", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Brush.horizontalGradient(listOf(Color.Transparent, NeonGreen.copy(alpha = 0.6f), Color.Transparent)))
                )
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Brush.verticalGradient(listOf(DarkBackground, Color(0xFF0A1428), DarkBackground)))
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    ExportOptionCard(
                        title = "ALERT HISTORY",
                        subtitle = "${alertHistory.size} alertas registrados",
                        icon = Icons.Default.Security,
                        color = NeonMagenta,
                        formats = listOf("CSV", "TXT"),
                        onExport = { format ->
                            exporting = true
                            exportAlertHistory(context, alertHistory, format)
                            exporting = false
                        }
                    )
                }

                item {
                    ExportOptionCard(
                        title = "TELEMETRY DATA",
                        subtitle = "${telemetryHistory.size} snapshots",
                        icon = Icons.Default.Analytics,
                        color = NeonCyan,
                        formats = listOf("CSV", "TXT"),
                        onExport = { format ->
                            exporting = true
                            exportTelemetryHistory(context, telemetryHistory, format)
                            exporting = false
                        }
                    )
                }

                item {
                    ExportOptionCard(
                        title = "FULL REPORT",
                        subtitle = "Relatório completo do sistema",
                        icon = Icons.Default.Description,
                        color = NeonGreen,
                        formats = listOf("TXT"),
                        onExport = { _ ->
                            exporting = true
                            exportFullReport(context, alertHistory, telemetryHistory)
                            exporting = false
                        }
                    )
                }
            }

            if (exporting) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NeonCyan)
                }
            }
        }
    }
}

@Composable
private fun ExportOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    formats: List<String>,
    onExport: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, spotColor = color.copy(alpha = 0.2f)),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(color.copy(alpha = 0.1f))
                        .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                formats.forEach { format ->
                    Button(
                        onClick = { onExport(format) },
                        modifier = Modifier.weight(1f).height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.12f)),
                        border = ButtonDefaults.outlinedButtonBorder,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(format, color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

private fun exportAlertHistory(context: Context, alerts: List<AlertEntity>, format: String) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)
    val fileName = "pocketnoc_alerts_${dateFormat.format(Date())}.${ format.lowercase() }"

    val content = when (format) {
        "CSV" -> buildString {
            appendLine("timestamp,server,type,message,value,threshold")
            alerts.forEach { a ->
                appendLine("${a.timestamp},${a.serverName},${a.type},\"${a.message}\",${a.value},${a.threshold}")
            }
        }
        else -> buildString {
            appendLine("=== PocketNOC Alert History Report ===")
            appendLine("Generated: ${Date()}")
            appendLine("Total Alerts: ${alerts.size}")
            appendLine("=" .repeat(50))
            alerts.forEach { a ->
                appendLine("\n[${a.type}] ${a.serverName}")
                appendLine("  ${a.message}")
                appendLine("  Value: ${a.value} | Threshold: ${a.threshold}")
                appendLine("  Time: ${Date(a.timestamp)}")
            }
        }
    }

    shareFile(context, fileName, content)
}

private fun exportTelemetryHistory(context: Context, history: List<TelemetryHistoryEntity>, format: String) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)
    val fileName = "pocketnoc_telemetry_${dateFormat.format(Date())}.${format.lowercase()}"

    val content = when (format) {
        "CSV" -> buildString {
            appendLine("timestamp,server_id,cpu_percent,ram_percent,disk_percent,load_avg_1m")
            history.forEach { t ->
                appendLine("${t.timestamp},${t.serverId},${t.cpuPercent},${t.ramPercent},${t.diskPercent},${t.loadAvg1m}")
            }
        }
        else -> buildString {
            appendLine("=== PocketNOC Telemetry History ===")
            appendLine("Generated: ${Date()}")
            appendLine("Snapshots: ${history.size}")
            appendLine("=".repeat(50))
            history.forEach { t ->
                appendLine("\nServer #${t.serverId} @ ${Date(t.timestamp)}")
                appendLine("  CPU: ${String.format("%.1f", t.cpuPercent)}% | RAM: ${String.format("%.1f", t.ramPercent)}%")
                appendLine("  Disk: ${String.format("%.1f", t.diskPercent)}% | Load 1m: ${String.format("%.2f", t.loadAvg1m)}")
            }
        }
    }

    shareFile(context, fileName, content)
}

private fun exportFullReport(context: Context, alerts: List<AlertEntity>, history: List<TelemetryHistoryEntity>) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)
    val fileName = "pocketnoc_report_${dateFormat.format(Date())}.txt"

    val content = buildString {
        appendLine("╔══��═══════════════════════════════════════╗")
        appendLine("║       POCKET NOC — SYSTEM REPORT        ║")
        appendLine("╚══════���═══════════════════════════════════╝")
        appendLine("Generated: ${Date()}")
        appendLine()

        appendLine("── ALERT SUMMARY ──────────────────────────")
        appendLine("Total Alerts: ${alerts.size}")
        val grouped = alerts.groupBy { it.type }
        grouped.forEach { (type, list) ->
            appendLine("  $type: ${list.size} occurrences")
        }
        appendLine()

        appendLine("── TELEMETRY SUMMARY ──────────────────────")
        appendLine("Snapshots: ${history.size}")
        if (history.isNotEmpty()) {
            val avgCpu = history.map { it.cpuPercent }.average()
            val avgRam = history.map { it.ramPercent }.average()
            val avgDisk = history.map { it.diskPercent }.average()
            val maxCpu = history.maxOf { it.cpuPercent }
            val maxRam = history.maxOf { it.ramPercent }
            appendLine("  Avg CPU: ${String.format("%.1f", avgCpu)}% (peak: ${String.format("%.1f", maxCpu)}%)")
            appendLine("  Avg RAM: ${String.format("%.1f", avgRam)}% (peak: ${String.format("%.1f", maxRam)}%)")
            appendLine("  Avg Disk: ${String.format("%.1f", avgDisk)}%")
        }
        appendLine()

        appendLine("── RECENT ALERTS ──────────────────────────")
        alerts.takeLast(20).forEach { a ->
            appendLine("  [${a.type}] ${a.serverName}: ${a.message}")
        }
        appendLine()
        appendLine("── END OF REPORT ───────────────���──────────")
    }

    shareFile(context, fileName, content)
}

private fun shareFile(context: Context, fileName: String, content: String) {
    try {
        val file = File(context.cacheDir, fileName)
        file.writeText(content)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (fileName.endsWith(".csv")) "text/csv" else "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "PocketNOC Export: $fileName")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Exportar $fileName"))
    } catch (e: Exception) {
        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
