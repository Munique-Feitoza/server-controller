package com.pocketnoc.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocketnoc.data.models.HealthStatus
import com.pocketnoc.ui.components.*
import com.pocketnoc.ui.theme.*
import com.pocketnoc.ui.viewmodels.DashboardViewModel
import com.pocketnoc.ui.viewmodels.TelemetryUiState
import com.pocketnoc.utils.formatTimeOnly

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerDetailsScreen(
    viewModel: DashboardViewModel,
    serverId: Int,
    onNavigateBack: () -> Unit,
    onNavigateToActionCenter: (Int) -> Unit,
    onNavigateToProcessExplorer: (Int) -> Unit,
    onNavigateToLogs: (Int, String) -> Unit,
    onNavigateToWatchdog: (Int) -> Unit = {},
    onNavigateToAuditLog: (Int) -> Unit = {},
    onNavigateToAgentConfig: (Int) -> Unit = {},
    onNavigateToPhpFpm: (Int) -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current

    val servers by viewModel.allServers.collectAsState()
    val selectedServer = servers.find { it.id == serverId }
    val serverHealth by viewModel.serverHealthMap.collectAsState()
    val telemetryState by viewModel.telemetryState.collectAsState()
    val history by viewModel.telemetryHistory.collectAsState()
    val phpFpm by viewModel.phpFpmState.collectAsState()

    // Carrega dados na abertura da tela
    LaunchedEffect(selectedServer) {
        selectedServer?.let {
            viewModel.fetchTelemetry(it)
            viewModel.loadTelemetryHistory(it.id)
            viewModel.loadPhpFpmPools(it)
        }
    }

    // Auto-refresh a cada 30 segundos
    LaunchedEffect(selectedServer) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            selectedServer?.let {
                viewModel.fetchTelemetry(it)
                viewModel.loadPhpFpmPools(it)
            }
        }
    }

    val health = serverHealth[serverId]
    val statusColor = when (health?.status) {
        HealthStatus.CRITICAL -> StatusColors.critical
        HealthStatus.ALERT    -> StatusColors.warning
        HealthStatus.WARNING  -> StatusColors.caution
        else                  -> StatusColors.healthy
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = selectedServer?.name ?: "Servidor",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.primary
                        )
                        selectedServer?.let {
                            Text(
                                text = it.locationInfo,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.outlineVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = colors.primary)
                    }
                },
                actions = {
                    var menuExpanded by remember { mutableStateOf(false) }

                    IconButton(onClick = { selectedServer?.let { viewModel.fetchTelemetry(it) } }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar", tint = colors.tertiary)
                    }

                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = colors.onSurfaceVariant)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.background(colors.surfaceVariant)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Action Center") },
                                onClick = { onNavigateToActionCenter(serverId); menuExpanded = false },
                                leadingIcon = { Icon(Icons.Default.Build, null, tint = ext.magenta) }
                            )
                            DropdownMenuItem(
                                text = { Text("Logs") },
                                onClick = { onNavigateToLogs(serverId, "pocket-noc-agent"); menuExpanded = false },
                                leadingIcon = { Icon(Icons.Default.Terminal, null, tint = colors.primary) }
                            )
                            DropdownMenuItem(
                                text = { Text("Watchdog") },
                                onClick = { onNavigateToWatchdog(serverId); menuExpanded = false },
                                leadingIcon = { Icon(Icons.Default.MonitorHeart, null, tint = ext.magenta) }
                            )
                            DropdownMenuItem(
                                text = { Text("PHP-FPM Pools") },
                                onClick = { onNavigateToPhpFpm(serverId); menuExpanded = false },
                                leadingIcon = { Icon(Icons.Default.Dns, null, tint = ext.blue) }
                            )
                            DropdownMenuItem(
                                text = { Text("Audit Log") },
                                onClick = { onNavigateToAuditLog(serverId); menuExpanded = false },
                                leadingIcon = { Icon(Icons.Default.ReceiptLong, null, tint = ext.purple) }
                            )
                            DropdownMenuItem(
                                text = { Text("Configuracao") },
                                onClick = { onNavigateToAgentConfig(serverId); menuExpanded = false },
                                leadingIcon = { Icon(Icons.Default.Settings, null, tint = colors.tertiary) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface.copy(alpha = 0.9f)),
                modifier = Modifier.shadow(Dimens.SpaceMd, spotColor = statusColor.copy(alpha = 0.3f))
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(paddingValues)
        ) {
            when (val state = telemetryState) {
                is TelemetryUiState.Loading -> {
                    // Shimmer skeleton — muito mais informativo que um spinner
                    ServerDetailsShimmer()
                }

                is TelemetryUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLg),
                            modifier = Modifier.padding(Dimens.Space3xl)
                        ) {
                            Text("⚠", style = MaterialTheme.typography.displayLarge, color = StatusColors.critical)
                            Text(
                                text = "Falha na conexão",
                                style = MaterialTheme.typography.headlineSmall,
                                color = colors.primary
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                            Button(
                                onClick = { selectedServer?.let { viewModel.fetchTelemetry(it) } },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primary.copy(alpha = 0.15f)),
                                border = ButtonDefaults.outlinedButtonBorder,
                                shape = AppShapes.medium
                            ) {
                                Text("Tentar novamente", color = colors.primary)
                            }
                        }
                    }
                }

                is TelemetryUiState.Success -> {
                    val t = state.telemetry

                    // Pontos para o gráfico histórico (oldest → newest) com timestamps
                    val cpuSamples = history.map { it.timestamp to it.cpuPercent }
                    val ramSamples = history.map { it.timestamp to it.ramPercent }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.ScreenPadding),
                        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXl - Dimens.SpaceXxs),
                        contentPadding = PaddingValues(vertical = Dimens.SpaceXl)
                    ) {

                        // ─── 1. STATUS HEADER ────────────────────────────────────
                        item {
                            LiveStatusHeader(
                                healthStatus = health?.status ?: HealthStatus.HEALTHY,
                                statusColor = statusColor,
                                activeAlerts = health?.activeAlerts ?: 0,
                                lastUpdate = health?.lastUpdate
                            )
                        }

                        // ─── 2. GAUGES CPU / RAM / DISCO ─────────────────────────
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceLg - Dimens.SpaceXxs)
                            ) {
                                ArcGauge(
                                    label = "CPU",
                                    value = t.cpu.usagePercent,
                                    color = ext.magenta,
                                    modifier = Modifier.weight(1f)
                                )
                                ArcGauge(
                                    label = "RAM",
                                    value = t.memory.usagePercent,
                                    color = ext.blue,
                                    modifier = Modifier.weight(1f)
                                )
                                ArcGauge(
                                    label = "DISCO",
                                    value = t.disk.disks.maxOfOrNull { it.usagePercent } ?: 0f,
                                    color = colors.primary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // ─── 3. GRÁFICO HISTÓRICO ─────────────────────────────────
                        item {
                            TelemetryLineChart(
                                cpuSamples = cpuSamples,
                                ramSamples = ramSamples,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // ─── 3b. PICOS DE CPU ─────────────────────────────────────
                        item {
                            CpuPeaksCard(samples = cpuSamples)
                        }

                        // ─── 3c. TOP SITES POR CPU ────────────────────────────────
                        phpFpm?.let { resp ->
                            if (resp.pools.isNotEmpty()) {
                                item {
                                    TopSitesCpuCard(pools = resp.pools)
                                }
                            }
                        }

                        // ─── 4. CARGA DO SISTEMA ──────────────────────────────────
                        item {
                            SystemLoadCard(
                                uptimeSeconds = t.uptime.uptimeSeconds,
                                loadAvg = t.uptime.loadAverage,
                                pingLatencyMs = null
                            )
                        }

                        // ─── 5. DISCOS ────────────────────────────────────────────
                        item {
                            DiskUsageCard(t.disk)
                        }

                        // ─── 6. REDE ──────────────────────────────────────────────
                        item {
                            NetworkCard(
                                txBytes = t.network.interfaces.sumOf { it.txBytes },
                                rxBytes = t.network.interfaces.sumOf { it.rxBytes },
                                pingMs = null
                            )
                        }

                        // ─── 7. TEMPERATURA ───────────────────────────────────────
                        if (t.temperature != null && t.temperature.sensors.isNotEmpty()) {
                            item { TemperatureCard(t.temperature) }
                        }

                        // ─── 8. TOP PROCESSOS ─────────────────────────────────────
                        item {
                            TopProcessesCard(
                                processes = t.processes.topProcesses.take(5),
                                onSeeAllClick = { onNavigateToProcessExplorer(serverId) }
                            )
                        }

                        // Espaço extra no final
                        item { Spacer(Modifier.height(Dimens.SpaceXl)) }
                    }
                }
            }
        }
    }
}

// ─── Componentes locais ─────────────────────────────────────────────────────

@Composable
private fun LiveStatusHeader(
    healthStatus: HealthStatus,
    statusColor: Color,
    activeAlerts: Int,
    lastUpdate: Long?,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "pulse_alpha"
    )

    val statusLabel = when (healthStatus) {
        HealthStatus.HEALTHY  -> "ONLINE — SAUDÁVEL"
        HealthStatus.WARNING  -> "ONLINE — AVISO"
        HealthStatus.ALERT    -> "ONLINE — ALERTA"
        HealthStatus.CRITICAL -> "CRÍTICO"
    }

    val formattedTime = lastUpdate?.let { formatTimeOnly(it) } ?: "--:--:--"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.card)
            .background(
                Brush.horizontalGradient(
                    listOf(statusColor.copy(alpha = 0.12f), colors.surfaceVariant, colors.surfaceVariant)
                )
            )
            .border(Dimens.BorderMedium, statusColor.copy(alpha = 0.5f), AppShapes.card)
            .padding(horizontal = Dimens.SpaceXl, vertical = Dimens.SpaceXl - Dimens.SpaceXxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceLg - Dimens.SpaceXxs)) {
            Box(
                modifier = Modifier
                    .size(Dimens.SpaceLg)
                    .shadow(Dimens.SpaceSm, AppShapes.pill, spotColor = statusColor)
                    .background(statusColor.copy(alpha = pulse), AppShapes.pill)
            )
            Column {
                Text(statusLabel, style = MaterialTheme.typography.labelMedium, color = statusColor, fontWeight = FontWeight.Bold)
                Text("Atualizado às $formattedTime", style = MaterialTheme.typography.labelSmall, color = colors.outlineVariant)
            }
        }
        if (activeAlerts > 0) {
            Box(
                modifier = Modifier
                    .background(StatusColors.critical.copy(alpha = 0.2f), AppShapes.pill)
                    .border(Dimens.BorderThin, StatusColors.critical.copy(alpha = 0.6f), AppShapes.pill)
                    .padding(horizontal = Dimens.SpaceLg - Dimens.SpaceXxs, vertical = Dimens.SpaceXs)
            ) {
                Text("$activeAlerts ALERTA${if (activeAlerts > 1) "S" else ""}", style = MaterialTheme.typography.labelSmall, color = StatusColors.critical, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SystemLoadCard(
    uptimeSeconds: Long,
    loadAvg: FloatArray,
    pingLatencyMs: Double?,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current

    val hours = uptimeSeconds / 3600
    val days  = hours / 24
    val uptimeLabel = if (days > 0) "${days}d ${hours % 24}h" else "${hours}h ${(uptimeSeconds % 3600) / 60}m"

    Card(
        modifier = modifier.fillMaxWidth().shadow(Dimens.SpaceMd, spotColor = colors.tertiary.copy(alpha = 0.2f)),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
        shape = AppShapes.card
    ) {
        Column(modifier = Modifier.padding(Dimens.SpaceXl)) {
            Text("SISTEMA", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(Dimens.SpaceLg))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceLg - Dimens.SpaceXxs)) {
                SystemMetricPill("UPTIME", uptimeLabel, colors.tertiary, Modifier.weight(1f))
                SystemMetricPill("LOAD 1m", String.format("%.2f", loadAvg.getOrNull(0) ?: 0f), colors.primary, Modifier.weight(1f))
                SystemMetricPill("LOAD 5m", String.format("%.2f", loadAvg.getOrNull(1) ?: 0f), ext.blue, Modifier.weight(1f))
                pingLatencyMs?.let {
                    val pingColor = when {
                        it > 100 -> StatusColors.warning
                        it > 200 -> StatusColors.critical
                        else     -> colors.tertiary
                    }
                    SystemMetricPill("PING", "${it.toInt()}ms", pingColor, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SystemMetricPill(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .clip(AppShapes.large)
            .background(color.copy(alpha = 0.08f))
            .border(Dimens.BorderThin, color.copy(alpha = 0.3f), AppShapes.large)
            .padding(vertical = Dimens.SpaceMd, horizontal = Dimens.SpaceSm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.outlineVariant)
        Spacer(Modifier.height(Dimens.SpaceXxs))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NetworkCard(txBytes: Long, rxBytes: Long, pingMs: Double?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme

    fun Long.toMb() = this / 1024 / 1024

    Card(
        modifier = modifier.fillMaxWidth().shadow(Dimens.SpaceMd, spotColor = colors.primary.copy(alpha = 0.2f)),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
        shape = AppShapes.card
    ) {
        Column(modifier = Modifier.padding(Dimens.SpaceXl)) {
            Text("REDE", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(Dimens.SpaceLg))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceLg - Dimens.SpaceXxs)) {
                SystemMetricPill("TX", "${txBytes.toMb()} MB", colors.primary, Modifier.weight(1f))
                SystemMetricPill("RX", "${rxBytes.toMb()} MB", colors.tertiary, Modifier.weight(1f))
                pingMs?.let {
                    val c = if (it > 150) StatusColors.warning else colors.tertiary
                    SystemMetricPill("PING", "${it.toInt()}ms", c, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun DiskUsageCard(disk: com.pocketnoc.data.models.DiskMetrics, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme

    Card(
        modifier = modifier.fillMaxWidth().shadow(Dimens.SpaceMd, spotColor = colors.primary.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
        shape = AppShapes.xl
    ) {
        Column(modifier = Modifier.padding(Dimens.SpaceXl).fillMaxWidth()) {
            Text("DISCOS", style = MaterialTheme.typography.labelSmall, color = colors.primary)
            Spacer(modifier = Modifier.height(Dimens.SpaceLg))
            disk.disks.forEach { diskInfo ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.SpaceMd),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                            Text(diskInfo.mountPoint, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                            Text(diskInfo.filesystem, style = MaterialTheme.typography.labelSmall, color = colors.outlineVariant)
                        }
                        Spacer(modifier = Modifier.height(Dimens.SpaceXs))
                        LinearProgressIndicator(
                            progress = { (diskInfo.usagePercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(Dimens.ProgressLg).clip(AppShapes.small),
                            color = when {
                                diskInfo.usagePercent > 90 -> StatusColors.critical
                                diskInfo.usagePercent > 80 -> StatusColors.warning
                                diskInfo.usagePercent > 70 -> StatusColors.caution
                                else -> StatusColors.success
                            },
                            trackColor = colors.surface
                        )
                    }
                    Column(
                        modifier = Modifier.padding(start = Dimens.SpaceLg - Dimens.SpaceXxs),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text("%.1f%%".format(diskInfo.usagePercent), style = MaterialTheme.typography.labelSmall, color = colors.primary, fontWeight = FontWeight.Bold)
                        Text("${formatSize(diskInfo.usedGb)} / ${formatSize(diskInfo.totalGb)}", style = MaterialTheme.typography.labelSmall, color = colors.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun TemperatureCard(temperature: com.pocketnoc.data.models.TemperatureMetrics, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current

    Card(
        modifier = modifier.fillMaxWidth().shadow(Dimens.SpaceMd, spotColor = ext.magenta.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
        shape = AppShapes.xl
    ) {
        Column(modifier = Modifier.padding(Dimens.SpaceXl).fillMaxWidth()) {
            Text("TEMPERATURA", style = MaterialTheme.typography.labelSmall, color = ext.magenta)
            Spacer(modifier = Modifier.height(Dimens.SpaceLg))
            temperature.sensors.forEach { sensor ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.SpaceMd),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(sensor.name, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    Text(
                        "${sensor.celsius}°C",
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            sensor.celsius > 80 -> StatusColors.critical
                            sensor.celsius > 70 -> StatusColors.warning
                            else -> StatusColors.success
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun formatSize(gb: Double): String = when {
    gb >= 1.0 -> "%.1f GB".format(gb)
    gb >= 0.001 -> "%.0f MB".format(gb * 1024)
    else -> "0 MB"
}

@Composable
fun TopProcessesCard(
    processes: List<com.pocketnoc.data.models.ProcessInfo>,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current

    Card(
        modifier = modifier.fillMaxWidth().shadow(Dimens.SpaceMd, spotColor = ext.blue.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
        shape = AppShapes.xl
    ) {
        Column(modifier = Modifier.padding(Dimens.SpaceXl).fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("TOP PROCESSOS", style = MaterialTheme.typography.labelSmall, color = ext.blue)
                TextButton(onClick = onSeeAllClick) {
                    Text("VER TUDO", style = MaterialTheme.typography.labelSmall, color = colors.primary)
                }
            }
            Spacer(modifier = Modifier.height(Dimens.SpaceMd))
            processes.forEach { process ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.SpaceMd),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(process.name, style = MaterialTheme.typography.bodySmall, color = colors.onSurface, fontWeight = FontWeight.Medium)
                        Text("PID: ${process.pid}", style = MaterialTheme.typography.labelSmall, color = colors.outlineVariant)
                    }
                    // Mini bar CPU
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(80.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${process.cpuUsage.toInt()}%", style = MaterialTheme.typography.labelSmall, color = ext.magenta)
                            Text("${process.memoryMb}MB", style = MaterialTheme.typography.labelSmall, color = ext.blue)
                        }
                        Spacer(Modifier.height(Dimens.SpaceXxs))
                        LinearProgressIndicator(
                            progress = { (process.cpuUsage / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(Dimens.ProgressSm).clip(AppShapes.small),
                            color = if (process.cpuUsage > 50) StatusColors.critical else ext.magenta,
                            trackColor = colors.surface
                        )
                    }
                }
            }
        }
    }
}
