package com.pocketnoc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketnoc.data.models.DashboardIncident
import com.pocketnoc.data.models.DashboardStatsResponse
import com.pocketnoc.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityDashboardScreen(
    incidents: List<DashboardIncident>,
    stats: DashboardStatsResponse?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(horizontal = Dimens.SpaceXl, vertical = Dimens.SpaceMd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(Dimens.TopBarButton).clip(AppShapes.large)
                            .background(StatusColors.critical.copy(alpha = 0.10f))
                            .border(Dimens.BorderThin, StatusColors.critical.copy(alpha = 0.35f), AppShapes.large)
                            .clickable(onClick = onNavigateBack),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = StatusColors.critical, modifier = Modifier.size(Dimens.IconMd)) }

                    Spacer(Modifier.width(Dimens.SpaceLg))

                    Column(Modifier.weight(1f)) {
                        Text("SEGURANCA", color = StatusColors.critical, fontWeight = FontWeight.Black, fontSize = 20.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                        Text("Dashboard ERP", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }

                    Box(
                        Modifier.size(Dimens.TopBarButton).clip(AppShapes.large)
                            .background(colors.primary.copy(alpha = 0.10f))
                            .border(Dimens.BorderThin, colors.primary.copy(alpha = 0.35f), AppShapes.large)
                            .clickable(onClick = onRefresh),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Refresh, null, tint = colors.primary, modifier = Modifier.size(Dimens.IconMd)) }
                }
                Box(Modifier.fillMaxWidth().height(Dimens.BorderThin).background(StatusColors.critical.copy(alpha = 0.3f)))
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues).background(colors.background)) {
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = StatusColors.critical)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLg),
                    contentPadding = PaddingValues(vertical = Dimens.SpaceXl)
                ) {
                    // Cards de resumo
                    if (stats != null) {
                        item { StatsOverview(stats, colors, ext) }
                        item { SeverityBreakdown(stats, colors) }
                        item { TopAttackers(stats, colors, ext) }
                        item { AttackTypes(stats, colors, ext) }
                    }

                    // Lista de incidentes
                    item {
                        Text("INCIDENTES RECENTES", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                    }

                    items(incidents) { incident ->
                        IncidentCard(incident, colors, ext)
                    }

                    if (incidents.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = Dimens.Space4xl), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Shield, null, tint = StatusColors.success, modifier = Modifier.size(Dimens.Icon2xl))
                                    Spacer(Modifier.height(Dimens.SpaceLg))
                                    Text("Nenhum incidente nas ultimas 24h", style = MaterialTheme.typography.bodyMedium, color = StatusColors.success)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsOverview(stats: DashboardStatsResponse, colors: ColorScheme, ext: ExtendedColors) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
        StatCard("TOTAL", "${stats.total}", StatusColors.warning, Modifier.weight(1f), colors)
        StatCard("BANIDOS", "${stats.bannedCount}", StatusColors.critical, Modifier.weight(1f), colors)
        StatCard("CRITICOS", "${stats.bySeverity.getOrDefault("critical", 0)}", ext.magenta, Modifier.weight(1f), colors)
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier, colors: ColorScheme) {
    Box(
        modifier.clip(AppShapes.card).background(colors.surfaceVariant)
            .border(Dimens.BorderThin, color.copy(alpha = 0.4f), AppShapes.card)
            .padding(Dimens.SpaceLg),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Black, color = color, fontFamily = FontFamily.Monospace)
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun SeverityBreakdown(stats: DashboardStatsResponse, colors: ColorScheme) {
    val severityColors = mapOf(
        "critical" to StatusColors.critical,
        "high" to Color(0xFFFF6B00),
        "medium" to StatusColors.warning,
        "low" to StatusColors.healthy
    )
    val total = stats.total.coerceAtLeast(1)

    Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant), shape = AppShapes.card) {
        Column(Modifier.padding(Dimens.SpaceXl)) {
            Text("SEVERIDADE", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(Dimens.SpaceLg))

            stats.bySeverity.entries.sortedByDescending { it.value }.forEach { (severity, count) ->
                val color = severityColors[severity] ?: colors.onSurfaceVariant
                val pct = count.toFloat() / total

                Row(Modifier.fillMaxWidth().padding(vertical = Dimens.SpaceXs), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(Dimens.StatusDot).background(color, CircleShape))
                    Spacer(Modifier.width(Dimens.SpaceMd))
                    Text(severity.uppercase(), style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                    LinearProgressIndicator(
                        progress = { pct },
                        modifier = Modifier.weight(1f).height(Dimens.ProgressMd).clip(AppShapes.pill),
                        color = color,
                        trackColor = colors.surface
                    )
                    Spacer(Modifier.width(Dimens.SpaceMd))
                    Text("$count", style = MaterialTheme.typography.bodySmall, color = colors.onSurface, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TopAttackers(stats: DashboardStatsResponse, colors: ColorScheme, ext: ExtendedColors) {
    if (stats.topIps.isEmpty()) return

    Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant), shape = AppShapes.card) {
        Column(Modifier.padding(Dimens.SpaceXl)) {
            Text("TOP ATACANTES", style = MaterialTheme.typography.labelMedium, color = StatusColors.critical, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(Dimens.SpaceLg))

            stats.topIps.forEachIndexed { idx, attacker ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Dimens.SpaceXs)
                        .clip(AppShapes.large).background(colors.surface.copy(alpha = if (idx % 2 == 0) 0.6f else 0.3f))
                        .padding(horizontal = Dimens.SpaceLg, vertical = Dimens.SpaceMd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${idx + 1}", style = MaterialTheme.typography.labelSmall, color = StatusColors.critical.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace, modifier = Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(attacker.ip, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        attacker.country?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant) }
                    }
                    Text("${attacker.count}x", style = MaterialTheme.typography.bodyMedium, color = StatusColors.critical, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun AttackTypes(stats: DashboardStatsResponse, colors: ColorScheme, ext: ExtendedColors) {
    if (stats.byType.isEmpty()) return

    Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant), shape = AppShapes.card) {
        Column(Modifier.padding(Dimens.SpaceXl)) {
            Text("TIPOS DE ATAQUE", style = MaterialTheme.typography.labelMedium, color = ext.magenta, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(Dimens.SpaceLg))

            stats.byType.entries.sortedByDescending { it.value }.forEach { (type, count) ->
                Row(Modifier.fillMaxWidth().padding(vertical = Dimens.SpaceXs), verticalAlignment = Alignment.CenterVertically) {
                    Text(type.replace("_", " ").uppercase(), style = MaterialTheme.typography.bodySmall, color = colors.onSurface, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$count", style = MaterialTheme.typography.bodySmall, color = ext.magenta, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun IncidentCard(incident: DashboardIncident, colors: ColorScheme, ext: ExtendedColors) {
    val severityColor = when (incident.severity) {
        "critical" -> StatusColors.critical
        "high" -> Color(0xFFFF6B00)
        "medium" -> StatusColors.warning
        else -> StatusColors.healthy
    }

    Row(
        Modifier.fillMaxWidth().clip(AppShapes.card).background(colors.surfaceVariant)
            .border(Dimens.BorderThin, severityColor.copy(alpha = 0.3f), AppShapes.card)
            .padding(Dimens.SpaceLg),
        verticalAlignment = Alignment.Top
    ) {
        // Dot de severidade
        Box(Modifier.padding(top = Dimens.SpaceXs).size(Dimens.StatusDotLg).background(severityColor, CircleShape))

        Spacer(Modifier.width(Dimens.SpaceLg))

        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(incident.type.replace("_", " ").uppercase(), style = MaterialTheme.typography.labelSmall, color = severityColor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                if (incident.isBanned) {
                    Box(Modifier.background(StatusColors.critical.copy(alpha = 0.15f), AppShapes.pill).padding(horizontal = Dimens.SpaceSm, vertical = Dimens.SpaceXxs)) {
                        Text("BANIDO", style = MaterialTheme.typography.labelSmall, color = StatusColors.critical, fontWeight = FontWeight.Black, fontSize = 10.sp)
                    }
                }
            }

            Spacer(Modifier.height(Dimens.SpaceXs))

            // IP + localizacao
            Text(incident.ip, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)

            val location = listOfNotNull(incident.city, incident.country).joinToString(", ")
            if (location.isNotEmpty()) {
                Text("$location — ${incident.isp ?: ""}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Spacer(Modifier.height(Dimens.SpaceXs))

            // Path tentado
            Text("${incident.method} ${incident.path}", style = MaterialTheme.typography.bodySmall, color = ext.cyan, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)

            // Horario
            incident.createdAt?.let {
                Text(it.take(19).replace("T", " "), style = MaterialTheme.typography.labelSmall, color = colors.outlineVariant)
            }
        }
    }
}
