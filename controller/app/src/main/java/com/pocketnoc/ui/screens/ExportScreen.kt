package com.pocketnoc.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
    val colors = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current

    val context = LocalContext.current
    val alertHistory by viewModel.alertHistory.collectAsState()
    val telemetryHistory by viewModel.telemetryHistory.collectAsState()
    var exporting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.RadiusLg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(AppShapes.medium)
                            .background(colors.tertiary.copy(alpha = 0.10f))
                            .border(Dimens.BorderThin, colors.tertiary.copy(alpha = 0.35f), AppShapes.medium)
                            .clickable(onClick = onNavigateBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = colors.tertiary, modifier = Modifier.size(17.dp))
                    }
                    Spacer(modifier = Modifier.width(Dimens.SpaceLg))
                    Column {
                        Text(
                            "EXPORT CENTER",
                            color = colors.tertiary,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 3.sp
                        )
                        Text("Exportar dados e relat\u00F3rios", style = MaterialTheme.typography.labelSmall, color = colors.outlineVariant)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimens.BorderThin)
                        .background(colors.primary.copy(alpha = 0.3f))
                )
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(colors.background)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.RadiusCard),
                contentPadding = PaddingValues(vertical = Dimens.ScreenPadding)
            ) {
                item {
                    ExportOptionCard(
                        title = "ALERT HISTORY",
                        subtitle = "${alertHistory.size} alertas registrados",
                        icon = Icons.Default.Security,
                        color = ext.magenta,
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
                        color = colors.primary,
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
                        subtitle = "Relat\u00F3rio completo do sistema",
                        icon = Icons.Default.Description,
                        color = colors.tertiary,
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
                    CircularProgressIndicator(color = colors.primary)
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
    val colors = MaterialTheme.colorScheme

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, spotColor = color.copy(alpha = 0.2f)),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
        shape = AppShapes.card
    ) {
        Column(modifier = Modifier.padding(Dimens.ScreenPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(AppShapes.xl)
                        .background(color.copy(alpha = 0.1f))
                        .border(Dimens.BorderThin, color.copy(alpha = 0.4f), AppShapes.xl),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(Dimens.SpaceLg))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.outlineVariant)
                }
            }
            Spacer(Modifier.height(Dimens.RadiusCard))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.RadiusLg)
            ) {
                formats.forEach { format ->
                    Button(
                        onClick = { onExport(format) },
                        modifier = Modifier.weight(1f).height(Dimens.TopBarButton),
                        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.12f)),
                        border = ButtonDefaults.outlinedButtonBorder,
                        shape = AppShapes.medium
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(Dimens.SpaceSm))
                        Text(format, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
        appendLine("\u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2557")
        appendLine("\u2551       POCKET NOC \u2014 SYSTEM REPORT        \u2551")
        appendLine("\u255A\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255D")
        appendLine("Generated: ${Date()}")
        appendLine()

        appendLine("\u2500\u2500 ALERT SUMMARY \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500")
        appendLine("Total Alerts: ${alerts.size}")
        val grouped = alerts.groupBy { it.type }
        grouped.forEach { (type, list) ->
            appendLine("  $type: ${list.size} occurrences")
        }
        appendLine()

        appendLine("\u2500\u2500 TELEMETRY SUMMARY \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500")
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

        appendLine("\u2500\u2500 RECENT ALERTS \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500")
        alerts.takeLast(20).forEach { a ->
            appendLine("  [${a.type}] ${a.serverName}: ${a.message}")
        }
        appendLine()
        appendLine("\u2500\u2500 END OF REPORT \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500")
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
        Toast.makeText(context, "Falha na exportacao: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
