package com.pocketnoc.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
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
    onNavigateToAgentConfig: (Int) -> Unit = {}
) {
    val servers by viewModel.allServers.collectAsState()
    val selectedServer = servers.find { it.id == serverId }
    val serverHealth by viewModel.serverHealthMap.collectAsState()
    val telemetryState by viewModel.telemetryState.collectAsState()
    val history by viewModel.telemetryHistory.collectAsState()

    // Carrega dados na abertura da tela
    LaunchedEffect(selectedServer) {
        selectedServer?.let {
            viewModel.fetchTelemetry(it)
            viewModel.loadTelemetryHistory(it.id)
        }
    }

    // Auto-refresh a cada 30 segundos
    LaunchedEffect(selectedServer) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            selectedServer?.let { viewModel.fetchTelemetry(it) }
        }
    }

    val health = serverHealth[serverId]
    val statusColor = when (health?.status) {
        HealthStatus.CRITICAL -> CriticalRedHealth
        HealthStatus.ALERT    -> AlertYellow
        HealthStatus.WARNING  -> WarningGreen
        else                  -> HealthyBlue
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = selectedServer?.name ?: "Servidor",
                            style = MaterialTheme.typography.titleLarge,
                            color = NeonCyan
                        )
                        selectedServer?.let {
                            Text(
                                text = it.locationInfo,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = NeonCyan)
                    }
                },
                actions = {
                    IconButton(onClick = { selectedServer?.let { viewModel.fetchTelemetry(it) } }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar", tint = NeonGreen)
                    }
                    IconButton(onClick = { onNavigateToActionCenter(serverId) }) {
                        Icon(Icons.Default.Build, contentDescription = "Action Center", tint = NeonMagenta)
                    }
                    IconButton(onClick = { onNavigateToLogs(serverId, "pocket-noc-agent") }) {
                        Icon(Icons.Default.Terminal, contentDescription = "Logs", tint = NeonCyan)
                    }
                    IconButton(onClick = { onNavigateToWatchdog(serverId) }) {
                        Icon(Icons.Default.MonitorHeart, contentDescription = "Watchdog", tint = NeonMagenta)
                    }
                    IconButton(onClick = { onNavigateToAuditLog(serverId) }) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = "Audit", tint = NeonPurple)
                    }
                    IconButton(onClick = { onNavigateToAgentConfig(serverId) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Config", tint = NeonGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface.copy(alpha = 0.9f)),
                modifier = Modifier.shadow(8.dp, spotColor = statusColor.copy(alpha = 0.3f))
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(DarkBackground, Color(0xFF0A1428), DarkBackground)
                    )
                )
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
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text("⚠", style = MaterialTheme.typography.displayLarge, color = CriticalRedHealth)
                            Text(
                                text = "Falha na conexão",
                                style = MaterialTheme.typography.headlineSmall,
                                color = NeonCyan
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Button(
                                onClick = { selectedServer?.let { viewModel.fetchTelemetry(it) } },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.15f)),
                                border = ButtonDefaults.outlinedButtonBorder,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Tentar novamente", color = NeonCyan)
                            }
                        }
                    }
                }

                is TelemetryUiState.Success -> {
                    val t = state.telemetry

                    // Pontos para o gráfico histórico (oldest → newest)
                    val cpuPoints = history.map { it.cpuPercent }
                    val ramPoints = history.map { it.ramPercent }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
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
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ArcGauge(
                                    label = "CPU",
                                    value = t.cpu.usagePercent,
                                    color = NeonMagenta,
                                    modifier = Modifier.weight(1f)
                                )
                                ArcGauge(
                                    label = "RAM",
                                    value = t.memory.usagePercent,
                                    color = NeonBlue,
                                    modifier = Modifier.weight(1f)
                                )
                                ArcGauge(
                                    label = "DISCO",
                                    value = t.disk.disks.maxOfOrNull { it.usagePercent } ?: 0f,
                                    color = NeonCyan,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // ─── 3. GRÁFICO HISTÓRICO ─────────────────────────────────
                        item {
                            TelemetryLineChart(
                                cpuPoints = cpuPoints,
                                ramPoints = ramPoints,
                                modifier = Modifier.fillMaxWidth()
                            )
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
                        item { Spacer(Modifier.height(16.dp)) }
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
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(statusColor.copy(alpha = 0.12f), DarkCard, DarkCard)
                )
            )
            .border(1.5.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .shadow(6.dp, RoundedCornerShape(6.dp), spotColor = statusColor)
                    .background(statusColor.copy(alpha = pulse), RoundedCornerShape(6.dp))
            )
            Column {
                Text(statusLabel, style = MaterialTheme.typography.labelMedium, color = statusColor, fontWeight = FontWeight.Bold)
                Text("Atualizado às $formattedTime", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }
        if (activeAlerts > 0) {
            Box(
                modifier = Modifier
                    .background(CriticalRedHealth.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                    .border(1.dp, CriticalRedHealth.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("$activeAlerts ALERTA${if (activeAlerts > 1) "S" else ""}", style = MaterialTheme.typography.labelSmall, color = CriticalRedHealth, fontWeight = FontWeight.Bold)
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
    val hours = uptimeSeconds / 3600
    val days  = hours / 24
    val uptimeLabel = if (days > 0) "${days}d ${hours % 24}h" else "${hours}h ${(uptimeSeconds % 3600) / 60}m"

    Card(
        modifier = modifier.fillMaxWidth().shadow(8.dp, spotColor = NeonGreen.copy(alpha = 0.2f)),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("SISTEMA", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SystemMetricPill("UPTIME", uptimeLabel, NeonGreen, Modifier.weight(1f))
                SystemMetricPill("LOAD 1m", String.format("%.2f", loadAvg.getOrNull(0) ?: 0f), NeonCyan, Modifier.weight(1f))
                SystemMetricPill("LOAD 5m", String.format("%.2f", loadAvg.getOrNull(1) ?: 0f), NeonBlue, Modifier.weight(1f))
                pingLatencyMs?.let {
                    val pingColor = when {
                        it > 100 -> AlertYellow
                        it > 200 -> CriticalRedHealth
                        else     -> NeonGreen
                    }
                    SystemMetricPill("PING", "${it.toInt()}ms", pingColor, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SystemMetricPill(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NetworkCard(txBytes: Long, rxBytes: Long, pingMs: Double?, modifier: Modifier = Modifier) {
    fun Long.toMb() = this / 1024 / 1024

    Card(
        modifier = modifier.fillMaxWidth().shadow(8.dp, spotColor = NeonCyan.copy(alpha = 0.2f)),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("REDE", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SystemMetricPill("TX", "${txBytes.toMb()} MB", NeonCyan, Modifier.weight(1f))
                SystemMetricPill("RX", "${rxBytes.toMb()} MB", NeonGreen, Modifier.weight(1f))
                pingMs?.let {
                    val c = if (it > 150) AlertYellow else NeonGreen
                    SystemMetricPill("PING", "${it.toInt()}ms", c, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun DiskUsageCard(disk: com.pocketnoc.data.models.DiskMetrics, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().shadow(8.dp, spotColor = NeonCyan.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text("DISCOS", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
            Spacer(modifier = Modifier.height(12.dp))
            disk.disks.forEach { diskInfo ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(diskInfo.mountPoint, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (diskInfo.usagePercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = when {
                                diskInfo.usagePercent > 90 -> CriticalRedHealth
                                diskInfo.usagePercent > 80 -> AlertYellow
                                diskInfo.usagePercent > 70 -> WarningGreen
                                else -> NeonGreen
                            },
                            trackColor = DarkSurface
                        )
                    }
                    Column(
                        modifier = Modifier.padding(start = 10.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text("${diskInfo.usagePercent.toInt()}%", style = MaterialTheme.typography.labelSmall, color = NeonCyan, fontWeight = FontWeight.Bold)
                        Text("${diskInfo.usedGb.toInt()}/${diskInfo.totalGb.toInt()}GB", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
fun TemperatureCard(temperature: com.pocketnoc.data.models.TemperatureMetrics, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().shadow(8.dp, spotColor = NeonMagenta.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text("TEMPERATURA", style = MaterialTheme.typography.labelSmall, color = NeonMagenta)
            Spacer(modifier = Modifier.height(12.dp))
            temperature.sensors.forEach { sensor ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(sensor.name, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text(
                        "${sensor.celsius}°C",
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            sensor.celsius > 80 -> CriticalRedHealth
                            sensor.celsius > 70 -> AlertYellow
                            else -> NeonGreen
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun TopProcessesCard(
    processes: List<com.pocketnoc.data.models.ProcessInfo>,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().shadow(8.dp, spotColor = NeonBlue.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("TOP PROCESSOS", style = MaterialTheme.typography.labelSmall, color = NeonBlue)
                TextButton(onClick = onSeeAllClick) {
                    Text("VER TUDO", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            processes.forEach { process ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(process.name, style = MaterialTheme.typography.bodySmall, color = Color.White, fontWeight = FontWeight.Medium)
                        Text("PID: ${process.pid}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                    // Mini bar CPU
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(80.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${process.cpuUsage.toInt()}%", style = MaterialTheme.typography.labelSmall, color = NeonMagenta)
                            Text("${process.memoryMb}MB", style = MaterialTheme.typography.labelSmall, color = NeonBlue)
                        }
                        Spacer(Modifier.height(2.dp))
                        LinearProgressIndicator(
                            progress = { (process.cpuUsage / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                            color = if (process.cpuUsage > 50) CriticalRedHealth else NeonMagenta,
                            trackColor = DarkSurface
                        )
                    }
                }
            }
        }
    }
}
