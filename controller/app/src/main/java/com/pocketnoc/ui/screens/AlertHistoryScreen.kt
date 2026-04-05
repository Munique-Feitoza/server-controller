package com.pocketnoc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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

@Composable
private fun alertStyle(type: String): AlertStyle {
    val ext = LocalExtendedColors.current
    val colors = MaterialTheme.colorScheme
    val t = type.uppercase()
    return when {
        t.contains("SECURITY") || t.contains("THREAT") ->
            AlertStyle(StatusColors.critical, Icons.Default.Security, "SEGURAN\u00C7A")
        t.contains("CPU") ->
            AlertStyle(ext.magenta, Icons.Default.Speed, "CPU")
        t.contains("MEMORY") || t.contains("MEM") ->
            AlertStyle(ext.blue, Icons.Default.Memory, "MEM\u00D3RIA")
        t.contains("DISK") ->
            AlertStyle(colors.primary, Icons.Default.Storage, "DISCO")
        t.contains("TEMP") ->
            AlertStyle(StatusColors.warning, Icons.Default.Thermostat, "TEMPERATURA")
        t.contains("REBOOT") ->
            AlertStyle(colors.tertiary, Icons.Default.Refresh, "REBOOT")
        else ->
            AlertStyle(StatusColors.warning, Icons.Default.Warning, "ALERTA")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertHistoryScreen(
    viewModel: DashboardViewModel,
    onNavigateBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current

    val alertHistory by viewModel.alertHistory.collectAsState()

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
                    // Bot\u00E3o voltar
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(AppShapes.medium)
                            .background(ext.magenta.copy(alpha = 0.10f))
                            .border(Dimens.BorderThin, ext.magenta.copy(alpha = 0.35f), AppShapes.medium)
                            .clickable(onClick = onNavigateBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = ext.magenta, modifier = Modifier.size(17.dp))
                    }

                    Spacer(modifier = Modifier.width(Dimens.SpaceLg))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "ALERT AUDIT",
                            color = ext.magenta,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 3.sp
                        )
                        Text(
                            "${alertHistory.size} eventos registrados",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.outlineVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (alertHistory.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(AppShapes.medium)
                                .background(StatusColors.critical.copy(alpha = 0.10f))
                                .border(Dimens.BorderThin, StatusColors.critical.copy(alpha = 0.35f), AppShapes.medium)
                                .clickable { viewModel.clearAlertHistory() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Limpar hist\u00F3rico", tint = StatusColors.critical, modifier = Modifier.size(17.dp))
                        }
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
            if (alertHistory.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLg)
                    ) {
                        Text("\u2713", style = MaterialTheme.typography.displayLarge, color = colors.tertiary)
                        Text("Nenhum alerta registrado", style = MaterialTheme.typography.titleMedium, color = colors.primary)
                        Text("O sistema est\u00E1 operando normalmente.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(Dimens.RadiusLg),
                    contentPadding = PaddingValues(vertical = Dimens.RadiusCard)
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
    val colors = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current
    val style = alertStyle(alert.type)

    val unit = when {
        alert.type.uppercase().let { it.contains("CPU") || it.contains("MEM") || it.contains("DISK") } -> "%"
        alert.type.uppercase().contains("REBOOT") -> "min"
        else -> ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, AppShapes.xl)
            .clip(AppShapes.xl)
            .background(colors.surfaceVariant)
            .border(Dimens.BorderThin, style.color.copy(alpha = 0.3f), AppShapes.xl)
            .padding(Dimens.RadiusCard),
        verticalAlignment = Alignment.Top
    ) {
        // \u00CDcone por tipo
        Box(
            modifier = Modifier
                .size(Dimens.TopBarButton)
                .clip(AppShapes.large)
                .background(colors.surfaceVariant)
                .border(Dimens.BorderThin, style.color.copy(alpha = 0.4f), AppShapes.large),
            contentAlignment = Alignment.Center
        ) {
            Icon(style.icon, contentDescription = null, tint = style.color, modifier = Modifier.size(Dimens.IconMd))
        }

        Spacer(modifier = Modifier.width(Dimens.SpaceLg))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tipo + servidor
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        style.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = style.color,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text("\u00B7", color = colors.outlineVariant, fontSize = 13.sp)
                    Text(
                        alert.serverName.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primary,
                        letterSpacing = 0.8.sp
                    )
                }
                Text(
                    formatAlertTimestamp(alert.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.outlineVariant
                )
            }

            Spacer(modifier = Modifier.height(Dimens.SpaceXs))

            Text(
                text = alert.message,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurface,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(Dimens.SpaceSm))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                Text(
                    text = "${String.format("%.1f", alert.value.toDouble())}$unit",
                    style = MaterialTheme.typography.labelMedium,
                    color = style.color,
                    fontWeight = FontWeight.Black
                )
                Text("\u2192 limite ${alert.threshold.toInt()}$unit", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}
