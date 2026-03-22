package com.pocketnoc.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketnoc.data.local.entities.ServerEntity
import com.pocketnoc.data.models.WatchdogEvent
import com.pocketnoc.ui.theme.*
import com.pocketnoc.ui.viewmodels.WatchdogUiState
import com.pocketnoc.ui.viewmodels.WatchdogViewModel

// ─── Paleta de cores do Watchdog ─────────────────────────────────────────────
private val ColorSuccess    = Color(0xFF00E676) // Verde neon
private val ColorFailed     = Color(0xFFFF5252) // Vermelho
private val ColorCircuit    = Color(0xFFFF6D00) // Laranja — Circuit Breaker aberto
private val ColorDegraded   = Color(0xFFFFD600) // Amarelo — Degraded
private val ColorBackground = Color(0xFF060D1F)

/**
 * Tela de Auto-Remediação do Pocket NOC
 *
 * Exibe uma timeline dos eventos do Watchdog com:
 * - Filtros por servidor e status
 * - Destaque visual por severidade
 * - Identificação explícita de qual servidor está com problema
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchdogScreen(
    server: ServerEntity,
    viewModel: WatchdogViewModel
) {
    val state          by viewModel.state.collectAsState()
    val selectedServer by viewModel.selectedServerId.collectAsState()
    val selectedStatus by viewModel.selectedStatus.collectAsState()
    val knownServers   by viewModel.knownServers.collectAsState()

    // Inicia polling ao entrar na tela
    LaunchedEffect(server) {
        viewModel.startPolling(server)
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.stopPolling() }
    }

    Scaffold(
        containerColor = ColorBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "🐕 WATCHDOG",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = NeonCyan
                        )
                        Text(
                            "Auto-Remediação • ${server.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchEvents(server) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = NeonCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(listOf(ColorBackground, Color(0xFF0A1628)))
                )
        ) {
            // ─── Filtro por servidor ──────────────────────────────────────────
            if (knownServers.isNotEmpty()) {
                ServerFilterRow(
                    servers        = knownServers,
                    selectedServer = selectedServer,
                    onSelect       = { svr -> viewModel.filterByServer(svr, server) },
                    onClear        = { viewModel.clearFilters(server) }
                )
            }

            // ─── Filtro por status ────────────────────────────────────────────
            StatusFilterRow(
                selectedStatus = selectedStatus,
                onSelect       = { st -> viewModel.filterByStatus(st, server) },
                onClear        = { viewModel.filterByStatus(null, server) }
            )

            // ─── Conteúdo principal ───────────────────────────────────────────
            Box(modifier = Modifier.fillMaxSize()) {
                when (val s = state) {
                    is WatchdogUiState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = NeonCyan
                        )
                    }

                    is WatchdogUiState.Empty -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("✅", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Nenhum evento de remediação",
                                color = ColorSuccess,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "Todos os serviços estão saudáveis",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    is WatchdogUiState.Success -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            // Resumo no topo
                            item {
                                WatchdogSummaryCard(
                                    totalEvents    = s.totalInStore,
                                    serversSummary = s.serversSummary
                                )
                            }

                            // Timeline de eventos
                            items(s.events, key = { it.id }) { event ->
                                WatchdogEventCard(event = event)
                            }
                        }
                    }

                    is WatchdogUiState.Error -> {
                        Text(
                            text = "⚠️ ${s.message}",
                            color = ColorFailed,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

// ─── Filtro de servidores ─────────────────────────────────────────────────────

@Composable
private fun ServerFilterRow(
    servers:        List<String>,
    selectedServer: String?,
    onSelect:       (String?) -> Unit,
    onClear:        () -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected  = selectedServer == null,
                onClick   = onClear,
                label     = { Text("Todos") },
                colors    = FilterChipDefaults.filterChipColors(
                    selectedContainerColor     = NeonCyan.copy(alpha = 0.2f),
                    selectedLabelColor         = NeonCyan,
                    containerColor             = DarkCard,
                    labelColor                 = TextSecondary
                )
            )
        }
        items(servers) { svr ->
            FilterChip(
                selected  = selectedServer == svr,
                onClick   = { onSelect(if (selectedServer == svr) null else svr) },
                label     = { Text(svr, maxLines = 1) },
                colors    = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NeonMagenta.copy(alpha = 0.2f),
                    selectedLabelColor     = NeonMagenta,
                    containerColor         = DarkCard,
                    labelColor             = TextSecondary
                )
            )
        }
    }
}

// ─── Filtro de status ─────────────────────────────────────────────────────────

@Composable
private fun StatusFilterRow(
    selectedStatus: String?,
    onSelect:       (String) -> Unit,
    onClear:        () -> Unit
) {
    val statusOptions = listOf(
        "Success"     to ColorSuccess,
        "Failed"      to ColorFailed,
        "CircuitOpen" to ColorCircuit,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        statusOptions.forEach { (label, color) ->
            val isSelected = selectedStatus == label
            FilterChip(
                selected = isSelected,
                onClick  = { if (isSelected) onClear() else onSelect(label) },
                label    = { Text(label, fontSize = 11.sp) },
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = color.copy(alpha = 0.2f),
                    selectedLabelColor     = color,
                    containerColor         = DarkCard,
                    labelColor             = TextSecondary
                )
            )
        }
    }
}

// ─── Card de resumo ───────────────────────────────────────────────────────────

@Composable
private fun WatchdogSummaryCard(
    totalEvents:    Int,
    serversSummary: Map<String, Int>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard.copy(alpha = 0.7f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "📊 Resumo dos Servidores",
                style = MaterialTheme.typography.labelMedium,
                color = NeonCyan
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier            = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment   = Alignment.CenterVertically
            ) {
                Text(
                    "Total na store: $totalEvents eventos",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Um chip por servidor com contagem de eventos
            serversSummary.forEach { (server, count) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "🖥 $server",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "$count eventos",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonMagenta
                    )
                }
            }
        }
    }
}

// ─── Card de evento individual — coração da timeline ─────────────────────────

@Composable
fun WatchdogEventCard(event: WatchdogEvent) {
    val statusColor = when (event.finalStatus) {
        "Success"     -> ColorSuccess
        "Failed"      -> ColorFailed
        "CircuitOpen" -> ColorCircuit
        else          -> ColorDegraded
    }

    val statusEmoji = when (event.finalStatus) {
        "Success"     -> "✅"
        "Failed"      -> "❌"
        "CircuitOpen" -> "🔴"  // Circuit Breaker aberto — requer humano
        else          -> "⚠️"
    }

    val probeEmoji = when (event.probeResult) {
        "Down"     -> "💀"
        "Degraded" -> "⚡"
        else       -> "✅"
    }

    val animatedBorder by animateColorAsState(
        targetValue = statusColor.copy(alpha = 0.5f),
        animationSpec = tween(600),
        label = "border_color"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, animatedBorder, RoundedCornerShape(12.dp)),
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard.copy(alpha = 0.8f))
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            // ─── Dot de status na timeline ────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
                    .align(Alignment.Top)
                    .offset(y = 4.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // ─── Cabeçalho: servidor + serviço ───────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        // Servidor — DESTAQUE para multi-servidor
                        Text(
                            text = "🖥 ${event.serverId}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = NeonMagenta,
                            fontWeight = FontWeight.Bold
                        )
                        // Serviço afetado
                        Text(
                            text = "$probeEmoji ${event.service}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    // Badge de status
                    Surface(
                        shape  = RoundedCornerShape(20.dp),
                        color  = statusColor.copy(alpha = 0.15f),
                        modifier = Modifier.border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    ) {
                        Text(
                            text  = "$statusEmoji ${event.finalStatus}",
                            color = statusColor,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ─── Mensagem ─────────────────────────────────────────────────
                Text(
                    text  = event.message,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    ),
                    color   = TextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ─── Rodapé: ação + timestamp + latência ─────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text  = "🔧 ${event.actionTaken}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan.copy(alpha = 0.8f),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text  = event.timestampIso.take(19).replace("T", " "),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        if (event.circuitOpen) {
                            Text(
                                "🔴 Circuit Open • ${event.attempts} tentativas",
                                style = MaterialTheme.typography.labelSmall,
                                color = ColorCircuit
                            )
                        } else {
                            event.probeLatencyMs?.let { ms ->
                                Text(
                                    "${ms}ms latência",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (ms > 2000) ColorDegraded else TextSecondary
                                )
                            }
                        }
                    }
                }

                // ─── Badge do role do servidor ────────────────────────────────
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RoleBadge(event.serverRole)
                    Text(
                        event.serverHostname,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary.copy(alpha = 0.6f),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/** Badge colorido mostrando o role do servidor */
@Composable
private fun RoleBadge(role: String) {
    val (emoji, color) = when (role) {
        "wordpress" -> "🌐" to Color(0xFF2196F3)  // Azul WordPress
        "erp"       -> "🏭" to Color(0xFF9C27B0)  // Roxo ERP
        "database"  -> "🗄️" to Color(0xFFFF9800)  // Laranja DB
        else        -> "⚙️" to Color(0xFF607D8B)  // Cinza generic
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.15f),
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
    ) {
        Text(
            text = "$emoji $role",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
