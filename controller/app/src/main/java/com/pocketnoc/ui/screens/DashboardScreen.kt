package com.pocketnoc.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pocketnoc.data.local.entities.ServerEntity
import com.pocketnoc.ui.components.*
import com.pocketnoc.ui.navigation.AppRoute
import com.pocketnoc.ui.theme.*
import com.pocketnoc.ui.theme.LocalThemeState
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LightMode
import com.pocketnoc.ui.viewmodels.DashboardViewModel
import com.pocketnoc.ui.viewmodels.TelemetryUiState
import com.pocketnoc.ui.viewmodels.WatchdogViewModel
import com.pocketnoc.utils.ConnectivityStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: androidx.navigation.NavHostController,
    viewModel: DashboardViewModel,
    watchdogViewModel: WatchdogViewModel = hiltViewModel(),
    onNavigateToAddServer: () -> Unit
) {
    val servers by viewModel.allServers.collectAsState()
    var selectedServerIndex by remember { mutableIntStateOf(0) }
    val safeIndex = if (selectedServerIndex < servers.size) selectedServerIndex else 0
    val selectedServer = if (servers.isNotEmpty()) servers[safeIndex] else null

    val telemetryState = viewModel.telemetryState.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showRebootDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var serverToDelete  by remember { mutableStateOf<ServerEntity?>(null) }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { message ->
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "neon_pulse")
    val pulseAlpha = infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "pulse_alpha"
    )

    LaunchedEffect(selectedServer) {
        selectedServer?.let { viewModel.fetchTelemetry(it) }
    }

    LaunchedEffect(selectedServer, networkStatus) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            if (networkStatus == ConnectivityStatus.Available && selectedServer != null) {
                viewModel.fetchTelemetry(selectedServer)
            }
        }
    }

    // Diálogo de exclusão (destrutivo — pede confirmação)
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; serverToDelete = null },
            title = { Text("REMOVER SERVIDOR?", color = CriticalRedHealth, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Servidor: ${serverToDelete?.name}", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        "O servidor será removido da lista. Esta ação não pode ser desfeita.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        serverToDelete?.let { viewModel.deleteServer(it) }
                        showDeleteDialog = false
                        serverToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CriticalRedHealth),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("CONFIRMAR REMOÇÃO", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteDialog = false; serverToDelete = null },
                    border = ButtonDefaults.outlinedButtonBorder,
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Cancelar", color = NeonCyan) }
            },
            containerColor = DarkCard,
            modifier = Modifier.border(1.dp, CriticalRedHealth.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
        )
    }

    // Diálogo de reboot (destrutivo — pede confirmação)
    if (showRebootDialog) {
        AlertDialog(
            onDismissRequest = { showRebootDialog = false },
            title = { Text("REBOOT EMERGENCIAL?", color = CriticalRedHealth, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Servidor: ${selectedServer?.name}", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        "O servidor será reiniciado imediatamente. Todos os processos ativos serão encerrados.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedServer?.let { viewModel.rebootServer(it) }
                        showRebootDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CriticalRedHealth),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("CONFIRMAR REBOOT", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showRebootDialog = false },
                    border = ButtonDefaults.outlinedButtonBorder,
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Cancelar", color = NeonCyan) }
            },
            containerColor = DarkCard,
            modifier = Modifier.border(1.dp, CriticalRedHealth.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
        )
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = DarkSurface,
                    contentColor = NeonCyan,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                )
            }
        },
        containerColor = Color.Transparent,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0A0F1E), DarkSurface.copy(alpha = 0.95f))
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ── Logo / título ──────────────────────────────────
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "POCKET NOC",
                            color         = NeonCyan,
                            fontWeight    = FontWeight.Black,
                            fontSize      = 22.sp,
                            fontFamily    = FontFamily.Monospace,
                            letterSpacing = 3.sp
                        )
                        selectedServer?.let {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(HealthyGreen, androidx.compose.foundation.shape.CircleShape)
                                )
                                Text(
                                    it.name,
                                    color         = TextSecondary,
                                    fontSize      = 13.sp,
                                    fontFamily    = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }

                    // ── Ações ─────────────────────────────────────────
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Theme toggle
                        val themeState = LocalThemeState.current
                        TopBarIconButton(
                            icon    = if (themeState.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            tint    = AlertYellow,
                            onClick = { themeState.toggle() }
                        )
                        selectedServer?.let { server ->
                            TopBarIconButton(
                                icon    = Icons.Default.Delete,
                                tint    = CriticalRedHealth,
                                onClick = { serverToDelete = server; showDeleteDialog = true }
                            )
                        }
                        TopBarIconButton(
                            icon    = Icons.Default.Add,
                            tint    = NeonGreen,
                            onClick = onNavigateToAddServer
                        )
                        TopBarIconButton(
                            icon    = Icons.Default.Refresh,
                            tint    = NeonCyan,
                            onClick = { selectedServer?.let { viewModel.fetchTelemetry(it) } }
                        )
                        TopBarIconButton(
                            icon    = Icons.Default.Security,
                            tint    = NeonMagenta,
                            onClick = { navController.navigate(AppRoute.AlertHistory.route) }
                        )
                        TopBarIconButton(
                            icon    = Icons.Default.Download,
                            tint    = NeonGreen,
                            onClick = { navController.navigate(AppRoute.Export.route) }
                        )
                    }
                }

                // ── Linha neon no rodapé da barra ─────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, NeonCyan.copy(alpha = 0.6f), NeonMagenta.copy(alpha = 0.4f), Color.Transparent)
                            )
                        )
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(listOf(DarkBackground, Color(0xFF0A1428), DarkBackground))
                )
        ) {
            // Banner offline
            if (networkStatus == ConnectivityStatus.Unavailable || networkStatus == ConnectivityStatus.Lost) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CriticalRedHealth.copy(alpha = 0.85f))
                        .padding(vertical = 6.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Text("MODO OFFLINE — Dados em Cache", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }

            // ── Tabs de servidores ─────────────────────────────────────
            if (servers.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = safeIndex,
                    modifier         = Modifier.fillMaxWidth(),
                    containerColor   = Color(0xFF080D1A),
                    contentColor     = NeonCyan,
                    edgePadding      = 12.dp,
                    divider          = {},
                    indicator        = { tabPositions ->
                        if (safeIndex < tabPositions.size) {
                            Box(
                                Modifier
                                    .tabIndicatorOffset(tabPositions[safeIndex])
                                    .height(2.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color.Transparent, NeonCyan, Color.Transparent)
                                        )
                                    )
                            )
                        }
                    }
                ) {
                    servers.forEachIndexed { index, server ->
                        val selected = safeIndex == index
                        Tab(
                            selected = selected,
                            onClick  = { selectedServerIndex = index },
                            modifier = Modifier.padding(vertical = 4.dp),
                            text = {
                                Text(
                                    server.name.uppercase(),
                                    fontSize      = 13.sp,
                                    fontWeight    = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily    = FontFamily.Monospace,
                                    letterSpacing = if (selected) 1.5.sp else 0.5.sp,
                                    color         = if (selected) NeonCyan else TextSecondary
                                )
                            }
                        )
                    }
                }
                // Separador sutil entre tabs
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(NeonCyan.copy(alpha = 0.08f))
                )
            }

            // ── Pill switcher MÉTRICAS / WATCHDOG ─────────────────────
            var selectedFeatureTabIndex by remember { mutableIntStateOf(0) }
            if (servers.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF080D1A))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FeaturePill(
                        label    = "MÉTRICAS",
                        icon     = Icons.Default.Analytics,
                        selected = selectedFeatureTabIndex == 0,
                        color    = NeonCyan,
                        modifier = Modifier.weight(1f),
                        onClick  = { selectedFeatureTabIndex = 0 }
                    )
                    FeaturePill(
                        label    = "WATCHDOG",
                        icon     = Icons.Default.MonitorHeart,
                        selected = selectedFeatureTabIndex == 1,
                        color    = NeonMagenta,
                        modifier = Modifier.weight(1f),
                        onClick  = { selectedFeatureTabIndex = 1 }
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                if (selectedFeatureTabIndex == 0)
                                    listOf(NeonCyan.copy(alpha = 0.4f), Color.Transparent)
                                else
                                    listOf(Color.Transparent, NeonMagenta.copy(alpha = 0.4f))
                            )
                        )
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (selectedFeatureTabIndex == 1 && selectedServer != null) {
                    WatchdogScreen(server = selectedServer, viewModel = watchdogViewModel)
                } else when (val state = telemetryState.value) {
                    is TelemetryUiState.Loading -> {
                        // Shimmer para o dashboard também
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(22.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
                        ) {
                            item { ShimmerBox(modifier = Modifier.fillMaxWidth().height(90.dp)) }
                            item { ShimmerBox(modifier = Modifier.fillMaxWidth().height(80.dp)) }
                            item { ShimmerBox(modifier = Modifier.fillMaxWidth().height(80.dp)) }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ShimmerBox(modifier = Modifier.weight(1f).height(72.dp))
                                    ShimmerBox(modifier = Modifier.weight(1f).height(72.dp))
                                }
                            }
                            item { ShimmerBox(modifier = Modifier.fillMaxWidth().height(140.dp)) }
                            item { ShimmerBox(modifier = Modifier.fillMaxWidth().height(180.dp)) }
                        }
                    }

                    is TelemetryUiState.Success -> {
                        val telemetry = state.telemetry
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(22.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            item { StatusCard(pulseAlpha, selectedServer) }

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
                                    icon = "🧠"
                                )
                            }

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
                                        title = "LOAD",
                                        value = String.format("%.2f", telemetry.uptime.loadAverage.getOrNull(0) ?: 0f),
                                        color = NeonGreen,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            item {
                                val txMb = telemetry.network.interfaces.sumOf { it.txBytes } / 1024 / 1024
                                val rxMb = telemetry.network.interfaces.sumOf { it.rxBytes } / 1024 / 1024
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    MetricCardFuturistic(title = "TX", value = "${txMb}MB", color = NeonCyan, modifier = Modifier.weight(1f))
                                    MetricCardFuturistic(title = "RX", value = "${rxMb}MB", color = NeonGreen, modifier = Modifier.weight(1f))
                                }
                            }

                            item {
                                SecurityStatusCard(
                                    security = telemetry.security,
                                    onBlockIp = { ip -> selectedServer?.let { viewModel.blockIp(it, ip) } }
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

                            item { ProcessesCard(telemetry.processes) }

                            // Botão de reboot: abre diálogo de confirmação antes de agir
                            item {
                                Button(
                                    onClick = { showRebootDialog = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .shadow(8.dp, RoundedCornerShape(12.dp), spotColor = CriticalRedHealth.copy(alpha = 0.4f)),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A0808)),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(
                                        brush = Brush.horizontalGradient(listOf(CriticalRedHealth.copy(alpha = 0.8f), CriticalRedHealth.copy(alpha = 0.4f)))
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = CriticalRedHealth, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("REBOOT EMERGENCIAL", color = CriticalRedHealth, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                }
                            }

                            item { Spacer(modifier = Modifier.height(8.dp)) }
                        }
                    }

                    is TelemetryUiState.Error -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Text("⚠", style = MaterialTheme.typography.displayLarge, color = CriticalRedHealth)
                                Text("Sem conexão com o agente", style = MaterialTheme.typography.headlineSmall, color = NeonCyan)
                                Text(state.message, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                Button(
                                    onClick = { selectedServer?.let { viewModel.fetchTelemetry(it) } },
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
}

@Composable
private fun StatusCard(pulseAlpha: State<Float>, server: ServerEntity?) {
    val securityStatus = server?.securityStatus ?: 0
    val (shieldColor, shieldLabel) = when (securityStatus) {
        2    -> CriticalRedHealth to "THREAT"
        1    -> AlertYellow       to "BYPASS"
        else -> HealthyGreen      to "SECURE"
    }

    val infiniteTransition = rememberInfiniteTransition(label = "status_pulse")
    val dotScale by infiniteTransition.animateFloat(
        initialValue  = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse),
        label         = "dot_scale"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(DarkCard)
            .border(
                1.5.dp,
                NeonCyan.copy(alpha = pulseAlpha.value * 0.85f),
                RoundedCornerShape(20.dp)
            )
    ) {
        // Linha de scan animada
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text     = "INFRA STATUS",
                        color    = NeonCyan.copy(alpha = pulseAlpha.value),
                        style    = MaterialTheme.typography.labelSmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Dot pulsante
                        Box(
                            modifier = Modifier
                                .size((10 * dotScale).dp)
                                .background(HealthyGreen, androidx.compose.foundation.shape.CircleShape)
                        )
                        Text(
                            text       = "ONLINE",
                            fontSize   = androidx.compose.ui.unit.TextUnit(28f, androidx.compose.ui.unit.TextUnitType.Sp),
                            fontWeight = FontWeight.Black,
                            color      = HealthyGreen,
                            letterSpacing = androidx.compose.ui.unit.TextUnit(4f, androidx.compose.ui.unit.TextUnitType.Sp)
                        )
                    }
                }

                // Shield badge
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .shadow(12.dp, RoundedCornerShape(14.dp), spotColor = shieldColor.copy(alpha = 0.6f))
                            .background(DarkCard, RoundedCornerShape(14.dp))
                            .border(1.dp, shieldColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Security,
                            contentDescription = null,
                            tint               = shieldColor,
                            modifier           = Modifier.size(26.dp)
                        )
                    }
                    Text(
                        text       = shieldLabel,
                        color      = shieldColor,
                        fontWeight = FontWeight.Bold,
                        style      = MaterialTheme.typography.labelSmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }

            if (server != null) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = NeonCyan.copy(alpha = 0.12f))
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    InfoTag(label = "OS",    value = server.osInfo)
                    InfoTag(label = "STACK", value = server.stackInfo)
                    InfoTag(label = "LOCAL", value = server.locationInfo)
                }
            }
        }
    }
}

@Composable
private fun InfoTag(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text          = label,
            style         = MaterialTheme.typography.labelSmall,
            color         = NeonCyan.copy(alpha = 0.5f),
            fontFamily    = androidx.compose.ui.text.font.FontFamily.Monospace,
            letterSpacing = androidx.compose.ui.unit.TextUnit(1f, androidx.compose.ui.unit.TextUnitType.Sp)
        )
        Text(
            text       = value,
            style      = MaterialTheme.typography.bodyMedium,
            color      = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TopBarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun FeaturePill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) color.copy(alpha = 0.15f) else Color.Transparent)
            .border(1.dp, if (selected) color.copy(alpha = 0.55f) else color.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = if (selected) color else color.copy(alpha = 0.45f),
                modifier           = Modifier.size(18.dp)
            )
            Text(
                text          = label,
                fontSize      = 13.sp,
                fontWeight    = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontFamily    = FontFamily.Monospace,
                letterSpacing = 1.sp,
                color         = if (selected) color else color.copy(alpha = 0.45f)
            )
        }
    }
}
