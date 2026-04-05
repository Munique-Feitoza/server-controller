package com.pocketnoc.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.pocketnoc.data.local.entities.ServerEntity
import com.pocketnoc.data.models.HealthStatus
import com.pocketnoc.data.models.ServerHealth
import com.pocketnoc.ui.components.ShimmerBox
import com.pocketnoc.ui.theme.*
import com.pocketnoc.ui.viewmodels.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    viewModel: DashboardViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (Int) -> Unit,
    onNavigateToAddServer: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current

    val servers by viewModel.allServers.collectAsState()
    val serverHealthMap by viewModel.serverHealthMap.collectAsState()
    var serverToDelete by remember { mutableStateOf<ServerEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SERVIDORES", style = MaterialTheme.typography.titleLarge, color = colors.primary, fontWeight = FontWeight.Bold)
                        Text("${servers.size} node${if (servers.size != 1) "s" else ""} configurado${if (servers.size != 1) "s" else ""}", style = MaterialTheme.typography.labelSmall, color = colors.outlineVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = colors.primary)
                    }
                },
                actions = {
                    onNavigateToAddServer?.let {
                        IconButton(onClick = it) {
                            Icon(Icons.Default.Add, contentDescription = "Adicionar servidor", tint = colors.tertiary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface.copy(alpha = 0.9f)),
                modifier = Modifier.shadow(Dimens.SpaceMd, spotColor = colors.primary.copy(alpha = 0.25f))
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(colors.background)
        ) {
            if (servers.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLg)
                    ) {
                        Text("[ ]", style = MaterialTheme.typography.displayLarge, color = colors.primary.copy(alpha = 0.3f))
                        Text("Nenhum servidor configurado", style = MaterialTheme.typography.titleMedium, color = colors.primary)
                        Text("Adicione um servidor pelo Dashboard.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLg),
                    contentPadding = PaddingValues(vertical = Dimens.SpaceXl - Dimens.SpaceXxs)
                ) {
                    items(servers) { server ->
                        val health = serverHealthMap[server.id]
                        ServerListItem(
                            server = server,
                            health = health,
                            onDelete = { serverToDelete = server },
                            onNavigateToDetails = { onNavigateToDetails(server.id) }
                        )
                    }
                }
            }
        }
    }

    if (serverToDelete != null) {
        AlertDialog(
            onDismissRequest = { serverToDelete = null },
            title = { Text("Remover Servidor?", color = StatusColors.critical, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
                    Text("Servidor: ${serverToDelete!!.name}", color = colors.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("As credenciais salvas serão removidas permanentemente.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteServer(serverToDelete!!)
                        serverToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusColors.critical),
                    shape = AppShapes.medium
                ) { Text("Remover", color = colors.onSurface, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                OutlinedButton(onClick = { serverToDelete = null }, shape = AppShapes.medium) {
                    Text("Cancelar", color = colors.primary)
                }
            },
            containerColor = colors.surfaceVariant,
            modifier = Modifier.border(Dimens.BorderThin, StatusColors.critical.copy(alpha = 0.4f), AppShapes.xl)
        )
    }
}

@Composable
fun ServerListItem(
    server: ServerEntity,
    health: ServerHealth?,
    onDelete: () -> Unit,
    onNavigateToDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current

    // Pulso animado no indicador de status
    val infiniteTransition = rememberInfiniteTransition(label = "dot_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "dot_alpha"
    )

    val statusColor = when (health?.status) {
        HealthStatus.CRITICAL -> StatusColors.critical
        HealthStatus.ALERT    -> StatusColors.warning
        HealthStatus.WARNING  -> StatusColors.caution
        HealthStatus.HEALTHY  -> StatusColors.healthy
        null                  -> colors.outlineVariant  // ainda carregando
    }

    val statusLabel = when (health?.status) {
        HealthStatus.CRITICAL -> "CRÍTICO"
        HealthStatus.ALERT    -> "ALERTA"
        HealthStatus.WARNING  -> "AVISO"
        HealthStatus.HEALTHY  -> "OK"
        null                  -> "—"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(Dimens.SpaceSm, AppShapes.card, spotColor = statusColor.copy(alpha = 0.3f))
            .clip(AppShapes.card)
            .background(
                Brush.horizontalGradient(listOf(statusColor.copy(alpha = 0.07f), colors.surfaceVariant))
            )
            .border(Dimens.BorderThin, statusColor.copy(alpha = 0.4f), AppShapes.card)
            .clickable(onClick = onNavigateToDetails)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SpaceXl),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Indicador de status pulsante
            Box(
                modifier = Modifier
                    .size(Dimens.StatusDotLg)
                    .background(statusColor.copy(alpha = dotAlpha), AppShapes.pill)
            )
            Spacer(modifier = Modifier.width(Dimens.SpaceXl - Dimens.SpaceXxs))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(server.name, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, fontWeight = FontWeight.Bold)
                    // Badge de status
                    Box(
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.15f), AppShapes.pill)
                            .border(Dimens.BorderThin, statusColor.copy(alpha = 0.5f), AppShapes.pill)
                            .padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceXxs)
                    ) {
                        Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(Dimens.SpaceMd))

                if (health != null) {
                    // Barras compactas de CPU/RAM/Disco
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceLg - Dimens.SpaceXxs)
                    ) {
                        MiniMetricBar("CPU", health.cpuUsage, ext.magenta, Modifier.weight(1f))
                        MiniMetricBar("RAM", health.memoryUsage, ext.blue, Modifier.weight(1f))
                        MiniMetricBar("DSK", health.diskUsage, colors.primary, Modifier.weight(1f))
                    }

                    if (health.activeAlerts > 0) {
                        Spacer(modifier = Modifier.height(Dimens.SpaceSm))
                        Row(
                            modifier = Modifier
                                .background(StatusColors.critical.copy(alpha = 0.1f), AppShapes.small)
                                .padding(horizontal = Dimens.SpaceSm, vertical = Dimens.ProgressSm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)
                        ) {
                            Box(modifier = Modifier.size(Dimens.SpaceSm).background(StatusColors.critical, AppShapes.pill))
                            Text(
                                "${health.activeAlerts} alerta${if (health.activeAlerts > 1) "s" else ""} ativo${if (health.activeAlerts > 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusColors.critical
                            )
                        }
                    }
                } else {
                    // Shimmer enquanto a saúde ainda não foi carregada
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
                        ShimmerBox(modifier = Modifier.fillMaxWidth().height(Dimens.SpaceMd), shape = AppShapes.small)
                        ShimmerBox(modifier = Modifier.fillMaxWidth(0.7f).height(Dimens.SpaceMd), shape = AppShapes.small)
                    }
                }
            }

            Spacer(modifier = Modifier.width(Dimens.SpaceMd))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)
            ) {
                IconButton(onClick = onDelete, modifier = Modifier.size(Dimens.ChipHeight)) {
                    Icon(Icons.Default.Delete, contentDescription = "Remover", tint = StatusColors.critical.copy(alpha = 0.5f), modifier = Modifier.size(Dimens.IconMd - Dimens.SpaceXxs))
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.primary.copy(alpha = 0.5f), modifier = Modifier.size(Dimens.IconMd - Dimens.SpaceXxs))
            }
        }
    }
}

@Composable
private fun MiniMetricBar(label: String, value: Float, color: Color, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme

    val barColor = when {
        value > 90 -> StatusColors.critical
        value > 75 -> StatusColors.warning
        else       -> color
    }
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.outlineVariant, fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp))
            Text("${value.toInt()}%", style = MaterialTheme.typography.labelSmall, color = barColor, fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp))
        }
        Spacer(Modifier.height(Dimens.SpaceXxs))
        LinearProgressIndicator(
            progress = { (value / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(Dimens.ProgressSm).clip(AppShapes.small),
            color = barColor,
            trackColor = colors.surface
        )
    }
}

@Composable
fun StatBadge(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), AppShapes.small)
            .padding(Dimens.SpaceSm)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
