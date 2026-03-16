package com.pocketnoc.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pocketnoc.data.models.HealthStatus
import com.pocketnoc.ui.components.FuturisticResourceCard
import com.pocketnoc.ui.components.MetricCardFuturistic
import com.pocketnoc.ui.theme.*
import com.pocketnoc.ui.viewmodels.DashboardViewModel
import com.pocketnoc.ui.viewmodels.TelemetryUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerDetailsScreen(
    viewModel: DashboardViewModel,
    serverId: Int,
    onNavigateBack: () -> Unit,
    onNavigateToActionCenter: (Int) -> Unit,
    onNavigateToProcessExplorer: (Int) -> Unit,
    onNavigateToLogs: (Int, String) -> Unit
) {
    val servers by viewModel.allServers.collectAsState()
    val selectedServer = servers.find { it.id == serverId }
    val serverHealth by viewModel.serverHealthMap.collectAsState()
    val telemetryState by viewModel.telemetryState.collectAsState()

    LaunchedEffect(selectedServer) {
        selectedServer?.let { viewModel.fetchTelemetry(it) }
    }

    // Auto-refresh a cada 30 segundos
    LaunchedEffect(selectedServer) {
        while (true) {
            kotlinx.coroutines.delay(30000)
            selectedServer?.let { viewModel.fetchTelemetry(it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("📊 ${selectedServer?.name ?: "Servidor"}", style = MaterialTheme.typography.displaySmall, color = NeonCyan)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NeonCyan)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { selectedServer?.let { viewModel.fetchTelemetry(it) } }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = NeonGreen)
                    }
                    IconButton(
                        onClick = { onNavigateToActionCenter(serverId) }
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Action Center", tint = NeonMagenta)
                    }
                    IconButton(
                        onClick = { onNavigateToLogs(serverId, "pocket-noc-agent") }
                    ) {
                        Icon(androidx.compose.material.icons.filled.List, contentDescription = "Logs", tint = NeonGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface.copy(alpha = 0.8f)),
                modifier = Modifier.shadow(8.dp, spotColor = NeonCyan.copy(alpha = 0.3f))
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(DarkBackground, Color(0xFF0F1A35), DarkBackground)
                    )
                )
        ) {
            when (val state = telemetryState) {
                is TelemetryUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(32.dp),
                        color = NeonCyan
                    )
                }
                is TelemetryUiState.Success -> {
                    val telemetry = state.telemetry
                    val health = serverHealth[serverId]

                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Health Status Overview
                        item {
                            HealthStatusCard(
                                status = health?.status ?: HealthStatus.HEALTHY,
                                cpuUsage = health?.cpuUsage ?: 0,
                                memoryUsage = health?.memoryUsage ?: 0,
                                diskUsage = health?.diskUsage ?: 0
                            )
                        }

                        // CPU and Memory Charts
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                MetricChartCard(
                                    title = "CPU",
                                    percentage = telemetry.cpu.usagePercent.toInt(),
                                    color = NeonMagenta,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricChartCard(
                                    title = "MEMÓRIA",
                                    percentage = telemetry.memory.usagePercent.toInt(),
                                    color = NeonBlue,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Disk Usage
                        item {
                            DiskUsageCard(telemetry.disk)
                        }

                        // Network Metrics
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val txMb = telemetry.network.interfaces.sumOf { it.txBytes } / 1024 / 1024
                                val rxMb = telemetry.network.interfaces.sumOf { it.rxBytes } / 1024 / 1024
                                MetricCardFuturistic(
                                    title = "TX (MB)",
                                    value = "$txMb",
                                    color = NeonCyan,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCardFuturistic(
                                    title = "RX (MB)",
                                    value = "$rxMb",
                                    color = NeonGreen,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // System Info
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                MetricCardFuturistic(
                                    title = "UPTIME",
                                    value = "${telemetry.uptime.uptimeSeconds / 3600}h",
                                    color = NeonCyan,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCardFuturistic(
                                    title = "LOAD AVG",
                                    value = String.format("%.2f", telemetry.uptime.loadAverage.getOrNull(0) ?: 0f),
                                    color = NeonGreen,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Temperature
                        if (telemetry.temperature.sensors.isNotEmpty()) {
                            item {
                                TemperatureCard(telemetry.temperature)
                            }
                        }

                        // Top Processes
                        item {
                            TopProcessesCard(
                                processes = telemetry.processes.topProcesses.take(5),
                                onSeeAllClick = { onNavigateToProcessExplorer(serverId) }
                            )
                        }
                    }
                }
                is TelemetryUiState.Error -> {
                    Text(
                        text = "Erro ao carregar dados: ${state.message}",
                        color = Color.Red,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HealthStatusCard(
    status: HealthStatus,
    cpuUsage: Int,
    memoryUsage: Int,
    diskUsage: Int,
    modifier: Modifier = Modifier
) {
    val statusColor = when (status) {
        HealthStatus.HEALTHY -> HealthyBlue
        HealthStatus.WARNING -> WarningGreen
        HealthStatus.ALERT -> AlertYellow
        HealthStatus.CRITICAL -> CriticalRedHealth
    }

    val statusLabel = when (status) {
        HealthStatus.HEALTHY -> "✓ SAUDÁVEL"
        HealthStatus.WARNING -> "⚠ AVISO"
        HealthStatus.ALERT -> "⚠️ ALERTA"
        HealthStatus.CRITICAL -> "🚨 CRÍTICO"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, spotColor = statusColor.copy(alpha = 0.4f)),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = androidx.compose.material3.CardDefaults.outlinedCardBorder().let {
            androidx.compose.foundation.BorderStroke(2.dp, statusColor.copy(alpha = 0.6f))
        }
    ) {
        Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.headlineSmall,
                color = statusColor,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(3) { index ->
                    val label = when (index) {
                        0 -> "CPU"
                        1 -> "RAM"
                        else -> "DISK"
                    }
                    val value = when (index) {
                        0 -> cpuUsage
                        1 -> memoryUsage
                        else -> diskUsage
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF1A2551), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text("$value%", style = MaterialTheme.typography.headlineSmall, color = NeonCyan)
                    }
                }
            }
        }
    }
}

@Composable
fun MetricChartCard(
    title: String,
    percentage: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .shadow(8.dp, spotColor = color.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = color)
            Spacer(modifier = Modifier.height(8.dp))
            
            // Circular Progress
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                CircularProgressIndicator(
                    progress = { percentage / 100f },
                    modifier = Modifier.fillMaxSize(),
                    color = color,
                    trackColor = DarkSurface,
                    strokeWidth = 4.dp
                )
                Text(
                    text = "$percentage%",
                    style = MaterialTheme.typography.headlineSmall,
                    color = color,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
fun DiskUsageCard(disk: com.pocketnoc.data.models.DiskMetrics, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, spotColor = NeonCyan.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text("DISCOS", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
            Spacer(modifier = Modifier.height(12.dp))

            disk.filesystems.forEach { filesystem ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = filesystem.mountPoint,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (filesystem.usedPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = when {
                                filesystem.usedPercent > 90 -> CriticalRedHealth
                                filesystem.usedPercent > 80 -> AlertYellow
                                filesystem.usedPercent > 70 -> WarningGreen
                                else -> NeonGreen
                            }
                        )
                    }
                    Text(
                        text = "${filesystem.usedPercent.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TemperatureCard(temperature: com.pocketnoc.data.models.TemperatureMetrics, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, spotColor = NeonMagenta.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text("TEMPERATURA", style = MaterialTheme.typography.labelSmall, color = NeonMagenta)
            Spacer(modifier = Modifier.height(12.dp))

            temperature.sensors.forEach { sensor ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sensor.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "${sensor.temperature}°C",
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            sensor.temperature > 80 -> CriticalRedHealth
                            sensor.temperature > 70 -> AlertYellow
                            else -> NeonGreen
                        }
                    )
                }
            }
        }
    }
}

fun TopProcessesCard(
    processes: List<com.pocketnoc.data.models.ProcessInfo>,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, spotColor = NeonBlue.copy(alpha = 0.3f)),
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = process.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                        Text(
                            text = "PID: ${process.pid}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${process.cpuPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonMagenta
                        )
                        Text(
                            text = "${process.memoryMb}MB",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonBlue
                        )
                    }
                }
            }
        }
    }
}
