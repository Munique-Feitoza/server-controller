package com.pocketnoc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketnoc.data.local.entities.AlertEntity
import com.pocketnoc.ui.theme.*
import com.pocketnoc.ui.viewmodels.DashboardViewModel
import com.pocketnoc.utils.formatAlertTimestamp

private data class AlertStyle(val color: Color, val icon: ImageVector, val label: String)

private fun alertStyle(type: String): AlertStyle {
    val t = type.uppercase()
    return when {
        t.contains("SECURITY") || t.contains("THREAT") ->
            AlertStyle(CriticalRedHealth, Icons.Default.Security, "SEGURANÇA")
        t.contains("CPU") ->
            AlertStyle(NeonMagenta, Icons.Default.Speed, "CPU")
        t.contains("MEMORY") || t.contains("MEM") ->
            AlertStyle(NeonBlue, Icons.Default.Memory, "MEMÓRIA")
        t.contains("DISK") ->
            AlertStyle(NeonCyan, Icons.Default.Storage, "DISCO")
        t.contains("TEMP") ->
            AlertStyle(WarningOrange, Icons.Default.Thermostat, "TEMPERATURA")
        t.contains("REBOOT") ->
            AlertStyle(NeonGreen, Icons.Default.Refresh, "REBOOT")
        else ->
            AlertStyle(AlertYellow, Icons.Default.Warning, "ALERTA")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertHistoryScreen(
    viewModel: DashboardViewModel,
    onNavigateBack: () -> Unit
) {
    val alertHistory by viewModel.alertHistory.collectAsState()

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
                    // Botão voltar
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(NeonMagenta.copy(alpha = 0.10f))
                            .border(1.dp, NeonMagenta.copy(alpha = 0.35f), RoundedCornerShape(9.dp))
                            .clickable(onClick = onNavigateBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = NeonMagenta, modifier = Modifier.size(17.dp))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "ALERT AUDIT",
                            color = NeonMagenta,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 3.sp
                        )
                        Text(
                            "${alertHistory.size} eventos registrados",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (alertHistory.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(CriticalRedHealth.copy(alpha = 0.10f))
                                .border(1.dp, CriticalRedHealth.copy(alpha = 0.35f), RoundedCornerShape(9.dp))
                                .clickable { viewModel.clearAlertHistory() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Limpar histórico", tint = CriticalRedHealth, modifier = Modifier.size(17.dp))
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, NeonMagenta.copy(alpha = 0.6f), NeonCyan.copy(alpha = 0.4f), Color.Transparent)
                            )
                        )
                )
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(listOf(DarkBackground, Color(0xFF140F1F), DarkBackground))
                )
        ) {
            if (alertHistory.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("✓", style = MaterialTheme.typography.displayLarge, color = NeonGreen)
                        Text("Nenhum alerta registrado", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                        Text("O sistema está operando normalmente.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    items(alertHistory) { alert ->
                        AlertHistoryCard(alert = alert)
                    }
                }
            }
        }
    }
}

@Composable
fun AlertHistoryCard(alert: AlertEntity) {
    val style = alertStyle(alert.type)
    val unit = when {
        alert.type.uppercase().let { it.contains("CPU") || it.contains("MEM") || it.contains("DISK") } -> "%"
        alert.type.uppercase().contains("REBOOT") -> "min"
        else -> ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .border(1.dp, style.color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Ícone por tipo
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DarkCard)
                .border(1.dp, style.color.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(style.icon, contentDescription = null, tint = style.color, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tipo + servidor
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        style.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = style.color,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text("·", color = TextMuted, fontSize = 10.sp)
                    Text(
                        alert.serverName.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan,
                        letterSpacing = 0.8.sp
                    )
                }
                Text(
                    formatAlertTimestamp(alert.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = alert.message,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "${String.format("%.1f", alert.value.toDouble())}$unit",
                    style = MaterialTheme.typography.labelMedium,
                    color = style.color,
                    fontWeight = FontWeight.Black
                )
                Text("→ limite ${alert.threshold.toInt()}$unit", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }
    }
}
