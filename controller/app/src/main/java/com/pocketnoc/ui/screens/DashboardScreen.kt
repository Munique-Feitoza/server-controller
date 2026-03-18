package com.pocketnoc.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.pocketnoc.ui.components.*
import com.pocketnoc.ui.theme.*
import com.pocketnoc.data.local.entities.ServerEntity
import com.pocketnoc.ui.navigation.AppRoute
import com.pocketnoc.ui.viewmodels.DashboardViewModel
import com.pocketnoc.ui.viewmodels.TelemetryUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: androidx.navigation.NavHostController,
    viewModel: DashboardViewModel,
    onNavigateToAddServer: () -> Unit
) {
    val servers by viewModel.allServers.collectAsState()
    var selectedServerIndex by remember { mutableStateOf(0) }
    
    // Garantir que o index é válido
    val safeIndex = if (selectedServerIndex < servers.size) selectedServerIndex else 0
    val selectedServer = if (servers.isNotEmpty()) servers[safeIndex] else null
    
    val telemetryState = viewModel.telemetryState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    
    // Coleta eventos do flow para mostrar Snackerbar
    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }
    
    // Animação neon pulsante
    val infiniteTransition = rememberInfiniteTransition(label = "neon_pulse")
    val pulseAlpha = infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    // Fetch telemetry e comandos quando o servidor selecionado muda
    LaunchedEffect(selectedServer) {
        selectedServer?.let {
            viewModel.fetchTelemetry(it)
        }
    }

    // Auto-refresh a cada 30 segundos
    LaunchedEffect(selectedServer) {
        while (true) {
            kotlinx.coroutines.delay(30000)
            selectedServer?.let {
                viewModel.fetchTelemetry(it)
            }
        }
    }

    Scaffold(
        snackbarHost = { 
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = DarkSurface,
                    contentColor = NeonCyan,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                )
            }
        },
        containerColor = Color.Transparent, // O fundo já está no Column
        topBar = {
            TopAppBar(
                title = {
                    Text("◆ POCKET NOC ◆", style = MaterialTheme.typography.displaySmall, color = NeonCyan)
                },
                actions = {
                    // Botão de deletar servidor selecionado
                    selectedServer?.let { server ->
                        IconButton(onClick = { viewModel.deleteServer(server) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Server", tint = Color.Red.copy(alpha = 0.7f))
                        }
                    }
                    
                    IconButton(onClick = onNavigateToAddServer) {
                        Icon(Icons.Default.Add, contentDescription = "Add Server", tint = NeonCyan)
                    }
                    IconButton(
                        onClick = { 
                            selectedServer?.let { viewModel.fetchTelemetry(it) }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = NeonGreen)
                    }
                    IconButton(onClick = { navController.navigate(AppRoute.AlertHistory.route) }) {
                        Icon(Icons.Default.Security, contentDescription = "Alert Audit", tint = NeonMagenta)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface.copy(alpha = 0.8f)),
                modifier = Modifier.shadow(8.dp, spotColor = NeonCyan.copy(alpha = 0.3f))
            )
        }
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
            // ========== SERVER TABS ==========
            if (servers.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = safeIndex,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = DarkSurface.copy(alpha = 0.7f),
                    contentColor = NeonMagenta,
                    edgePadding = 16.dp,
                    divider = { HorizontalDivider(color = NeonCyan.copy(alpha = 0.2f)) },
                    indicator = { tabPositions ->
                        if (safeIndex < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[safeIndex]),
                                color = NeonCyan
                            )
                        }
                    }
                ) {
                    servers.forEachIndexed { index, server ->
                        Tab(
                            selected = safeIndex == index,
                            onClick = { selectedServerIndex = index },
                            text = { Text(server.name) },
                            selectedContentColor = NeonCyan,
                            unselectedContentColor = TextSecondary
                        )
                    }
                }
            }

            // ========== CONTENT ==========
            Box(modifier = Modifier.fillMaxSize()) {
                when (val state = telemetryState.value) {
                    is TelemetryUiState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = NeonCyan
                        )
                    }
                    is TelemetryUiState.Success -> {
                        val telemetry = state.telemetry
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Removida a lista redundante de servidores.
                            // Agora o Dashboard foca 100% no servidor selecionado na TabRow.

                            item {
                                StatusCard(pulseAlpha, selectedServer)
                            }
                            item {
                                FuturisticResourceCard(
                                    title = "CPU USAGE",
                                    percentage = telemetry.cpu.usagePercent.toInt(),
                                    color = NeonMagenta,
                                    icon = "⚡"
                                )
                            }
                            item {
                                FuturisticResourceCard(
                                    title = "RAM USAGE",
                                    percentage = telemetry.memory.usagePercent.toInt(),
                                    color = NeonBlue,
                                    icon = "💾"
                                )
                            }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    MetricCardFuturistic(
                                        title = "UPTIME",
                                        value = "${telemetry.uptime.uptimeSeconds / 3600}h",
                                        color = NeonCyan,
                                        modifier = Modifier.weight(1f)
                                    )
                                    MetricCardFuturistic(
                                        title = "LOAD",
                                        value = String.format("%.2f", telemetry.uptime.loadAverage.getOrNull(0) ?: 0f),
                                        color = NeonGreen,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    val txMb = telemetry.network.interfaces.sumOf { it.txBytes } / 1024 / 1024
                                    val rxMb = telemetry.network.interfaces.sumOf { it.rxBytes } / 1024 / 1024
                                    MetricCardFuturistic(
                                        title = "NETWORK TX",
                                        value = "${txMb}MB",
                                        color = NeonCyan,
                                        modifier = Modifier.weight(1f)
                                    )
                                    MetricCardFuturistic(
                                        title = "NETWORK RX",
                                        value = "${rxMb}MB",
                                        color = NeonGreen,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            item {
                                SecurityStatusCard(
                                    security = telemetry.security,
                                    onBlockIp = { ip ->
                                        selectedServer?.let { viewModel.blockIp(it, ip) }
                                    }
                                )
                            }
                            item {
                                ServicesCard(
                                    services = telemetry.services,
                                    onActionClick = { name, action ->
                                        selectedServer?.let { viewModel.performServiceAction(it, name, action) }
                                    }
                                )
                            }

                            item {
                                ProcessesCard(telemetry.processes)
                            }
                            item {
                                IconButton(
                                    onClick = { selectedServer?.let { viewModel.rebootServer(it) } },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                        .background(Color(0xFF440000), RoundedCornerShape(12.dp))
                                        .border(1.dp, Color.Red, RoundedCornerShape(12.dp))
                                ) {
                                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = Color.Red)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("EMERGENCY REBOOT", color = Color.Red)
                                }
                            }
                        }
                    }
                    is TelemetryUiState.Error -> {
                        Text(
                            text = "ERROR: ${state.message}",
                            color = Color.Red,
                            modifier = Modifier.align(Alignment.Center).padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusCard(pulseAlpha: State<Float>, server: ServerEntity?) {
    val securityStatus = server?.securityStatus ?: 0
    val shieldColor = when(securityStatus) {
        0 -> HealthyGreen
        1 -> Color.Yellow
        2 -> Color.Red
        else -> HealthyGreen
    }
    
    val shieldLabel = when(securityStatus) {
        0 -> "SECURE"
        1 -> "BYPASS"
        2 -> "THREAT"
        else -> "SECURE"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, spotColor = NeonCyan.copy(alpha = 0.5f), shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkCard.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "INFRA STATUS", 
                        color = NeonCyan, 
                        modifier = Modifier.graphicsLayer(alpha = pulseAlpha.value),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("ONLINE", style = MaterialTheme.typography.headlineMedium, color = HealthyGreen)
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Security, 
                        contentDescription = "Security Status",
                        tint = shieldColor,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(shieldLabel, style = MaterialTheme.typography.labelSmall, color = shieldColor)
                }
            }
            
            if (server != null) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = NeonCyan.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    InfoTag(label = "OS", value = server.osInfo)
                    InfoTag(label = "STACK", value = server.stackInfo)
                    InfoTag(label = "LOC", value = server.locationInfo)
                }
            }
        }
    }
}

@Composable
fun InfoTag(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = NeonCyan.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = Color.White)
    }
}
