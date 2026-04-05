package com.pocketnoc.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketnoc.data.local.entities.ServerEntity
import com.pocketnoc.data.models.WatchdogEvent
import com.pocketnoc.ui.components.ShimmerBox
import com.pocketnoc.ui.theme.*
import com.pocketnoc.ui.viewmodels.WatchdogUiState
import com.pocketnoc.ui.viewmodels.WatchdogViewModel
import com.pocketnoc.utils.formatIsoToBrasilia

private val WatchdogSuccess  = Color(0xFF00E676)
private val WatchdogFailed   = Color(0xFFEF5350)
private val WatchdogCircuit  = Color(0xFFFF6D00)
private val WatchdogDegraded = Color(0xFFFDD835)

@Composable
fun WatchdogScreen(
    server: ServerEntity,
    viewModel: WatchdogViewModel
) {
    val colors = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current

    val state          by viewModel.state.collectAsState()
    val selectedServer by viewModel.selectedServerId.collectAsState()
    val selectedStatus by viewModel.selectedStatus.collectAsState()
    val knownServers   by viewModel.knownServers.collectAsState()

    LaunchedEffect(server) { viewModel.startPolling(server) }
    DisposableEffect(Unit) { onDispose { viewModel.stopPolling() } }

    Column(modifier = Modifier.fillMaxSize()) {

        // -- Barra de acoes inline --
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "AUTO-REMEDIA\u00C7\u00C3O",
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
                Text(
                    server.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.outlineVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                Box(
                    modifier = Modifier
                        .size(Dimens.ChipHeight)
                        .clip(AppShapes.medium)
                        .background(WatchdogFailed.copy(alpha = 0.10f))
                        .border(Dimens.BorderThin, WatchdogFailed.copy(alpha = 0.35f), AppShapes.medium)
                        .clickable { viewModel.clearLogs(server) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Limpar", tint = WatchdogFailed, modifier = Modifier.size(15.dp))
                }
                Box(
                    modifier = Modifier
                        .size(Dimens.ChipHeight)
                        .clip(AppShapes.medium)
                        .background(colors.primary.copy(alpha = 0.10f))
                        .border(Dimens.BorderThin, colors.primary.copy(alpha = 0.35f), AppShapes.medium)
                        .clickable { viewModel.fetchEvents(server) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Atualizar", tint = colors.primary, modifier = Modifier.size(15.dp))
                }
            }
        }

        HorizontalDivider(color = colors.primary.copy(alpha = 0.08f))

        // Filtro de servidores
        if (knownServers.isNotEmpty()) {
            ServerFilterRow(
                servers        = knownServers,
                selectedServer = selectedServer,
                onSelect       = { svr -> viewModel.filterByServer(svr, server) },
                onClear        = { viewModel.clearFilters(server) }
            )
        }

        // Filtro de status
        StatusFilterRow(
            selectedStatus = selectedStatus,
            onSelect       = { st -> viewModel.filterByStatus(st, server) },
            onClear        = { viewModel.filterByStatus(null, server) }
        )

        HorizontalDivider(color = colors.primary.copy(alpha = 0.08f))

        Box(modifier = Modifier.weight(1f)) {
            when (val s = state) {
                is WatchdogUiState.Loading -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding),
                        verticalArrangement = Arrangement.spacedBy(Dimens.RadiusLg),
                        contentPadding = PaddingValues(bottom = Dimens.ScreenPadding)
                    ) {
                        items(8) {
                            ShimmerBox(modifier = Modifier.fillMaxWidth().height(110.dp))
                        }
                    }
                }

                is WatchdogUiState.Empty -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLg)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(AppShapes.panel)
                                    .background(colors.surfaceVariant)
                                    .border(Dimens.BorderThin, WatchdogSuccess.copy(alpha = 0.4f), AppShapes.panel),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = WatchdogSuccess, modifier = Modifier.size(Dimens.IconXl))
                            }
                            Text("Nenhum evento de remedia\u00E7\u00E3o", style = MaterialTheme.typography.titleMedium, color = WatchdogSuccess)
                            Text("Todos os servi\u00E7os est\u00E3o saud\u00E1veis.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                is WatchdogUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.ScreenPadding),
                        verticalArrangement = Arrangement.spacedBy(Dimens.RadiusLg),
                        contentPadding = PaddingValues(vertical = Dimens.SpaceLg)
                    ) {
                        item {
                            WatchdogSummaryCard(
                                totalEvents    = s.totalInStore,
                                serversSummary = s.serversSummary
                            )
                        }
                        items(s.events, key = { it.id }) { event ->
                            WatchdogEventCard(
                                event  = event,
                                onReset = { viewModel.performAtomicReset(server, event.service) }
                            )
                        }
                    }
                }

                is WatchdogUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
                        ) {
                            Text("ERR", color = WatchdogFailed, fontFamily = FontFamily.Monospace, fontSize = 24.sp)
                            Text(s.message, color = WatchdogFailed.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                            Button(
                                onClick = { viewModel.fetchEvents(server) },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primary.copy(alpha = 0.15f)),
                                shape = AppShapes.medium
                            ) { Text("Tentar novamente", color = colors.primary) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerFilterRow(
    servers: List<String>,
    selectedServer: String?,
    onSelect: (String?) -> Unit,
    onClear: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.5f))
            .padding(horizontal = Dimens.SpaceLg, vertical = Dimens.SpaceSm),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
    ) {
        item {
            FilterChip(
                selected = selectedServer == null,
                onClick  = onClear,
                label    = { Text("Todos", fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.primary,
                    containerColor         = Color.Transparent,
                    selectedLabelColor     = colors.background
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = selectedServer == null,
                    borderColor = colors.primary.copy(alpha = 0.3f), selectedBorderColor = colors.primary
                ),
                modifier = Modifier.height(28.dp)
            )
        }
        items(servers) { svr ->
            val sel = selectedServer == svr
            FilterChip(
                selected = sel,
                onClick  = { onSelect(if (sel) null else svr) },
                label    = { Text(svr, fontFamily = FontFamily.Monospace, fontSize = 13.sp, maxLines = 1) },
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ext.magenta,
                    containerColor         = Color.Transparent,
                    selectedLabelColor     = colors.background
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = sel,
                    borderColor = ext.magenta.copy(alpha = 0.3f), selectedBorderColor = ext.magenta
                ),
                modifier = Modifier.height(28.dp)
            )
        }
    }
}

@Composable
private fun StatusFilterRow(
    selectedStatus: String?,
    onSelect: (String) -> Unit,
    onClear: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    val statusOptions = listOf(
        Triple("Success",     WatchdogSuccess, Icons.Default.CheckCircle),
        Triple("Failed",      WatchdogFailed,  Icons.Default.Error),
        Triple("CircuitOpen", WatchdogCircuit, Icons.Default.Warning),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SpaceLg, vertical = Dimens.SpaceSm),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
    ) {
        statusOptions.forEach { (label, color, icon) ->
            val isSel = selectedStatus == label
            FilterChip(
                selected = isSel,
                onClick  = { if (isSel) onClear() else onSelect(label) },
                label    = {
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(10.dp))
                        Text(label, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = color.copy(alpha = 0.2f),
                    selectedLabelColor     = color,
                    containerColor         = Color.Transparent,
                    labelColor             = colors.onSurfaceVariant
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = isSel,
                    borderColor = color.copy(alpha = 0.2f), selectedBorderColor = color.copy(alpha = 0.6f)
                ),
                modifier = Modifier.height(28.dp)
            )
        }
    }
}

@Composable
private fun WatchdogSummaryCard(
    totalEvents: Int,
    serversSummary: Map<String, Int>
) {
    val colors = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.xl)
            .background(colors.surfaceVariant)
            .border(Dimens.BorderThin, colors.primary.copy(alpha = 0.25f), AppShapes.xl)
            .padding(Dimens.RadiusCard),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.TopBarButton)
                .background(colors.primary.copy(alpha = 0.1f), AppShapes.large)
                .border(Dimens.BorderThin, colors.primary.copy(alpha = 0.35f), AppShapes.large),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Analytics, contentDescription = null, tint = colors.primary, modifier = Modifier.size(Dimens.IconMd))
        }

        Spacer(modifier = Modifier.width(Dimens.SpaceLg))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                "RESUMO DOS SERVIDORES",
                style = MaterialTheme.typography.labelSmall,
                color = colors.primary,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(Dimens.SpaceSm))
            serversSummary.forEach { (srv, count) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(srv, style = MaterialTheme.typography.bodySmall, color = colors.onSurface, fontFamily = FontFamily.Monospace)
                    Text("$count eventos", style = MaterialTheme.typography.bodySmall, color = ext.magenta, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.width(Dimens.SpaceLg))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$totalEvents",
                style = MaterialTheme.typography.titleLarge,
                color = colors.primary,
                fontWeight = FontWeight.Black
            )
            Text("total", style = MaterialTheme.typography.labelSmall, color = colors.outlineVariant)
        }
    }
}

@Composable
fun WatchdogEventCard(
    event: WatchdogEvent,
    onReset: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current

    val statusColor = when (event.finalStatus) {
        "Success"     -> WatchdogSuccess
        "Failed"      -> WatchdogFailed
        "CircuitOpen" -> WatchdogCircuit
        else          -> WatchdogDegraded
    }
    val statusLabel = when (event.finalStatus) {
        "Success"     -> "OK"
        "Failed"      -> "FALHOU"
        "CircuitOpen" -> "CIRCUIT"
        else          -> "DEGRADED"
    }
    val probeColor = when (event.probeResult) {
        "Down"     -> WatchdogFailed
        "Degraded" -> WatchdogDegraded
        else       -> WatchdogSuccess
    }

    val animatedBorder by animateColorAsState(
        targetValue   = statusColor.copy(alpha = 0.45f),
        animationSpec = tween(600),
        label         = "watchdog_border"
    )

    // Pulsação do dot de status
    val infiniteTransition = rememberInfiniteTransition(label = "dot_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = EaseInOutSine), RepeatMode.Reverse),
        label         = "dot_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, AppShapes.xl, spotColor = statusColor.copy(alpha = 0.25f))
            .clip(AppShapes.xl)
            .background(colors.surfaceVariant)
            .border(Dimens.BorderThin, animatedBorder, AppShapes.xl)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.RadiusCard)
        ) {
            // Dot de status pulsante
            Box(
                modifier = Modifier
                    .padding(top = Dimens.SpaceXs)
                    .size(Dimens.StatusDotLg)
                    .background(statusColor.copy(alpha = dotAlpha), AppShapes.pill)
            )
            Spacer(modifier = Modifier.width(Dimens.SpaceLg))

            Column(modifier = Modifier.weight(1f)) {
                // Cabecalho: servidor + servico + badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text  = event.serverId,
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.magenta,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(Dimens.SpaceSm).background(probeColor, AppShapes.pill))
                            Text(
                                text  = event.service,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.15f), AppShapes.pill)
                            .border(Dimens.BorderThin, statusColor.copy(alpha = 0.5f), AppShapes.pill)
                            .padding(horizontal = Dimens.SpaceMd, vertical = Dimens.ProgressSm)
                    ) {
                        Text(
                            text  = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Dimens.SpaceMd))

                // Mensagem
                Text(
                    text     = event.message,
                    style    = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, lineHeight = 17.sp),
                    color    = colors.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(Dimens.SpaceMd))

                // Rodapé: ação + timestamp + latência
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null, tint = colors.primary.copy(alpha = 0.6f), modifier = Modifier.size(10.dp))
                            Text(
                                text     = event.actionTaken,
                                style    = MaterialTheme.typography.labelSmall,
                                color    = colors.primary.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(Dimens.SpaceXxs))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RoleBadge(event.serverRole)
                            Text(
                                event.serverHostname,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.outlineVariant,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text  = formatIsoToBrasilia(event.timestampIso),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.outlineVariant
                        )
                        if (event.circuitOpen) {
                            Text(
                                "CIRCUIT \u2022 ${event.attempts}x",
                                style = MaterialTheme.typography.labelSmall,
                                color = WatchdogCircuit,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            event.probeLatencyMs?.let { ms ->
                                Text(
                                    "${ms}ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (ms > 2000) WatchdogDegraded else colors.outlineVariant
                                )
                            }
                        }
                    }
                }

                // Botão de reset (só quando circuit open)
                if (event.circuitOpen) {
                    Spacer(modifier = Modifier.height(Dimens.SpaceMd))
                    Button(
                        onClick = onReset,
                        colors = ButtonDefaults.buttonColors(containerColor = WatchdogCircuit.copy(alpha = 0.15f)),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(WatchdogCircuit.copy(alpha = 0.8f), WatchdogCircuit.copy(alpha = 0.4f)))
                        ),
                        shape  = AppShapes.medium,
                        modifier = Modifier.fillMaxWidth().height(34.dp),
                        contentPadding = PaddingValues(horizontal = Dimens.SpaceLg)
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, tint = WatchdogCircuit, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(Dimens.SpaceSm))
                        Text("RESET & RESTART", color = WatchdogCircuit, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleBadge(role: String) {
    val (label, color) = when (role) {
        "wordpress" -> "WP"  to Color(0xFF2196F3)
        "erp"       -> "ERP" to Color(0xFF9C27B0)
        "database"  -> "DB"  to Color(0xFFFF9800)
        else        -> role.take(4).uppercase() to Color(0xFF607D8B)
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), AppShapes.small)
            .border(Dimens.BorderThin, color.copy(alpha = 0.4f), AppShapes.small)
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontFamily = FontFamily.Monospace
        )
    }
}
