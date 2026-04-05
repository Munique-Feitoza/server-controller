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
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val state          by viewModel.state.collectAsState()
    val selectedServer by viewModel.selectedServerId.collectAsState()
    val selectedStatus by viewModel.selectedStatus.collectAsState()
    val knownServers   by viewModel.knownServers.collectAsState()

    LaunchedEffect(server) { viewModel.startPolling(server) }
    DisposableEffect(Unit) { onDispose { viewModel.stopPolling() } }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Barra de ações inline ──────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF080D1A))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "AUTO-REMEDIAÇÃO",
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
                Text(
                    server.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(WatchdogFailed.copy(alpha = 0.10f))
                        .border(1.dp, WatchdogFailed.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .clickable { viewModel.clearLogs(server) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Limpar", tint = WatchdogFailed, modifier = Modifier.size(15.dp))
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeonCyan.copy(alpha = 0.10f))
                        .border(1.dp, NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .clickable { viewModel.fetchEvents(server) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Atualizar", tint = NeonCyan, modifier = Modifier.size(15.dp))
                }
            }
        }

        HorizontalDivider(color = NeonCyan.copy(alpha = 0.08f))

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

        HorizontalDivider(color = NeonCyan.copy(alpha = 0.08f))

        Box(modifier = Modifier.weight(1f)) {
            when (val s = state) {
                is WatchdogUiState.Loading -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
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
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(DarkCard)
                                    .border(1.dp, WatchdogSuccess.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = WatchdogSuccess, modifier = Modifier.size(32.dp))
                            }
                            Text("Nenhum evento de remediação", style = MaterialTheme.typography.titleMedium, color = WatchdogSuccess)
                            Text("Todos os serviços estão saudáveis.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                is WatchdogUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
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
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("ERR", color = WatchdogFailed, fontFamily = FontFamily.Monospace, fontSize = 24.sp)
                            Text(s.message, color = WatchdogFailed.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            Button(
                                onClick = { viewModel.fetchEvents(server) },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Tentar novamente", color = NeonCyan) }
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
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            FilterChip(
                selected = selectedServer == null,
                onClick  = onClear,
                label    = { Text("Todos", fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NeonCyan,
                    containerColor         = Color.Transparent,
                    selectedLabelColor     = Color.Black
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = selectedServer == null,
                    borderColor = NeonCyan.copy(alpha = 0.3f), selectedBorderColor = NeonCyan
                ),
                modifier = Modifier.height(28.dp)
            )
        }
        items(servers) { svr ->
            val sel = selectedServer == svr
            FilterChip(
                selected = sel,
                onClick  = { onSelect(if (sel) null else svr) },
                label    = { Text(svr, fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 1) },
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NeonMagenta,
                    containerColor         = Color.Transparent,
                    selectedLabelColor     = Color.Black
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = sel,
                    borderColor = NeonMagenta.copy(alpha = 0.3f), selectedBorderColor = NeonMagenta
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
    val statusOptions = listOf(
        Triple("Success",     WatchdogSuccess, Icons.Default.CheckCircle),
        Triple("Failed",      WatchdogFailed,  Icons.Default.Error),
        Triple("CircuitOpen", WatchdogCircuit, Icons.Default.Warning),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        statusOptions.forEach { (label, color, icon) ->
            val isSel = selectedStatus == label
            FilterChip(
                selected = isSel,
                onClick  = { if (isSel) onClear() else onSelect(label) },
                label    = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(10.dp))
                        Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = color.copy(alpha = 0.2f),
                    selectedLabelColor     = color,
                    containerColor         = Color.Transparent,
                    labelColor             = TextSecondary
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .border(1.dp, NeonCyan.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(NeonCyan.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                .border(1.dp, NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Analytics, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                "RESUMO DOS SERVIDORES",
                style = MaterialTheme.typography.labelSmall,
                color = NeonCyan,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            serversSummary.forEach { (srv, count) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(srv, style = MaterialTheme.typography.bodySmall, color = Color.White, fontFamily = FontFamily.Monospace)
                    Text("$count eventos", style = MaterialTheme.typography.bodySmall, color = NeonMagenta, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$totalEvents",
                style = MaterialTheme.typography.titleLarge,
                color = NeonCyan,
                fontWeight = FontWeight.Black
            )
            Text("total", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}

@Composable
fun WatchdogEventCard(
    event: WatchdogEvent,
    onReset: () -> Unit
) {
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
            .shadow(4.dp, RoundedCornerShape(12.dp), spotColor = statusColor.copy(alpha = 0.25f))
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .border(1.dp, animatedBorder, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Dot de status pulsante
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(10.dp)
                    .background(statusColor.copy(alpha = dotAlpha), RoundedCornerShape(5.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Cabeçalho: servidor + serviço + badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text  = event.serverId,
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonMagenta,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(6.dp).background(probeColor, RoundedCornerShape(3.dp)))
                            Text(
                                text  = event.service,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                            .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
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

                Spacer(modifier = Modifier.height(8.dp))

                // Mensagem
                Text(
                    text     = event.message,
                    style    = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, lineHeight = 17.sp),
                    color    = TextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Rodapé: ação + timestamp + latência
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null, tint = NeonCyan.copy(alpha = 0.6f), modifier = Modifier.size(10.dp))
                            Text(
                                text     = event.actionTaken,
                                style    = MaterialTheme.typography.labelSmall,
                                color    = NeonCyan.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RoleBadge(event.serverRole)
                            Text(
                                event.serverHostname,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text  = formatIsoToBrasilia(event.timestampIso),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        if (event.circuitOpen) {
                            Text(
                                "CIRCUIT • ${event.attempts}x",
                                style = MaterialTheme.typography.labelSmall,
                                color = WatchdogCircuit,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            event.probeLatencyMs?.let { ms ->
                                Text(
                                    "${ms}ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (ms > 2000) WatchdogDegraded else TextMuted
                                )
                            }
                        }
                    }
                }

                // Botão de reset (só quando circuit open)
                if (event.circuitOpen) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onReset,
                        colors = ButtonDefaults.buttonColors(containerColor = WatchdogCircuit.copy(alpha = 0.15f)),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(WatchdogCircuit.copy(alpha = 0.8f), WatchdogCircuit.copy(alpha = 0.4f)))
                        ),
                        shape  = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(34.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, tint = WatchdogCircuit, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
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
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
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
