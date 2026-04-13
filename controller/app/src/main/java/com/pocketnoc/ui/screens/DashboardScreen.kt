package com.pocketnoc.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pocketnoc.data.local.entities.ServerEntity
import com.pocketnoc.ui.components.*
import com.pocketnoc.ui.navigation.AppRoute
import com.pocketnoc.ui.theme.*
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
    val themeState = LocalThemeState.current

    val snackbarHostState = remember { SnackbarHostState() }
    var showRebootDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var serverToDelete by remember { mutableStateOf<ServerEntity?>(null) }

    // Cores do tema (reativas ao dark/light)
    val colors = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current

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

    // ── Diálogos ─────────────────────────────────────────────────
    if (showDeleteDialog) {
        DestructiveDialog(
            title = "REMOVER SERVIDOR?",
            serverName = serverToDelete?.name,
            description = "O servidor será removido da lista. Esta ação não pode ser desfeita.",
            confirmText = "CONFIRMAR REMOÇÃO",
            confirmIcon = Icons.Default.Delete,
            onConfirm = {
                serverToDelete?.let { viewModel.deleteServer(it) }
                showDeleteDialog = false; serverToDelete = null
            },
            onDismiss = { showDeleteDialog = false; serverToDelete = null }
        )
    }

    if (showRebootDialog) {
        DestructiveDialog(
            title = "REBOOT EMERGENCIAL?",
            serverName = selectedServer?.name,
            description = "O servidor será reiniciado imediatamente. Todos os processos ativos serão encerrados.",
            confirmText = "CONFIRMAR REBOOT",
            confirmIcon = Icons.Default.PowerSettingsNew,
            onConfirm = {
                selectedServer?.let { viewModel.rebootServer(it) }
                showRebootDialog = false
            },
            onDismiss = { showRebootDialog = false }
        )
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = colors.surface,
                    contentColor = colors.primary,
                    shape = AppShapes.medium,
                    modifier = Modifier.border(Dimens.BorderThin, colors.primary.copy(alpha = 0.4f), AppShapes.medium)
                )
            }
        },
        containerColor = Color.Transparent,
        topBar = {
            var menuExpanded by remember { mutableStateOf(false) }

            Column(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(horizontal = Dimens.SpaceXl, vertical = Dimens.SpaceMd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "POCKET NOC",
                            color = colors.primary,
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 3.sp
                        )
                        selectedServer?.let {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
                            ) {
                                Box(Modifier.size(Dimens.StatusDot).background(StatusColors.success, CircleShape))
                                Text(
                                    it.name,
                                    color = colors.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // Botao de refresh direto (acao mais comum)
                    TopBarBtn(Icons.Default.Refresh, colors.primary) {
                        selectedServer?.let { viewModel.fetchTelemetry(it) }
                    }

                    Spacer(Modifier.width(Dimens.SpaceSm))

                    // Menu sanduiche
                    Box {
                        TopBarBtn(Icons.Default.Menu, colors.onSurfaceVariant) {
                            menuExpanded = true
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.background(colors.surfaceVariant)
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (themeState.isDarkTheme) "Tema Claro" else "Tema Escuro", color = colors.onSurface) },
                                onClick = { themeState.toggle(); menuExpanded = false },
                                leadingIcon = { Icon(if (themeState.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode, null, tint = StatusColors.warning) }
                            )
                            DropdownMenuItem(
                                text = { Text("Adicionar Servidor", color = colors.onSurface) },
                                onClick = { onNavigateToAddServer(); menuExpanded = false },
                                leadingIcon = { Icon(Icons.Default.Add, null, tint = ext.green) }
                            )
                            DropdownMenuItem(
                                text = { Text("Historico de Alertas", color = colors.onSurface) },
                                onClick = { navController.navigate(AppRoute.AlertHistory.route); menuExpanded = false },
                                leadingIcon = { Icon(Icons.Default.Security, null, tint = ext.magenta) }
                            )
                            DropdownMenuItem(
                                text = { Text("Exportar Dados", color = colors.onSurface) },
                                onClick = { navController.navigate(AppRoute.Export.route); menuExpanded = false },
                                leadingIcon = { Icon(Icons.Default.Download, null, tint = ext.green) }
                            )
                            DropdownMenuItem(
                                text = { Text("Seguranca Dashboard", color = StatusColors.critical) },
                                onClick = { navController.navigate(AppRoute.SecurityDashboard.route); menuExpanded = false },
                                leadingIcon = { Icon(Icons.Default.Shield, null, tint = StatusColors.critical) }
                            )
                            selectedServer?.let { s ->
                                HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
                                DropdownMenuItem(
                                    text = { Text("PHP-FPM Pools", color = colors.onSurface) },
                                    onClick = {
                                        val route = AppRoute.PhpFpm.createRoute(s.id)
                                        android.util.Log.d("MenuClick", "PHP-FPM clicado, serverId=${s.id}, route=$route")
                                        menuExpanded = false
                                        navController.navigate(route)
                                    },
                                    leadingIcon = { Icon(Icons.Default.Dns, null, tint = ext.blue) }
                                )
                                DropdownMenuItem(
                                    text = { Text("SSL Monitor", color = colors.onSurface) },
                                    onClick = {
                                        val route = AppRoute.SslCheck.createRoute(s.id)
                                        android.util.Log.d("MenuClick", "SSL clicado, serverId=${s.id}, route=$route")
                                        menuExpanded = false
                                        navController.navigate(route)
                                    },
                                    leadingIcon = { Icon(Icons.Default.Lock, null, tint = ext.green) }
                                )
                                HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
                                DropdownMenuItem(
                                    text = { Text("Analise Completa (Graficos)", color = colors.onSurface) },
                                    onClick = {
                                        menuExpanded = false
                                        navController.navigate(AppRoute.ServerDetails.createRoute(s.id))
                                    },
                                    leadingIcon = { Icon(Icons.Default.ShowChart, null, tint = ext.magenta) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Explorador de Processos", color = colors.onSurface) },
                                    onClick = {
                                        menuExpanded = false
                                        navController.navigate(AppRoute.ProcessExplorer.createRoute(s.id))
                                    },
                                    leadingIcon = { Icon(Icons.Default.Memory, null, tint = ext.blue) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Central de Acoes", color = colors.onSurface) },
                                    onClick = {
                                        menuExpanded = false
                                        navController.navigate(AppRoute.ActionCenter.createRoute(s.id))
                                    },
                                    leadingIcon = { Icon(Icons.Default.PlayArrow, null, tint = ext.green) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Logs do Sistema", color = colors.onSurface) },
                                    onClick = {
                                        menuExpanded = false
                                        navController.navigate(AppRoute.LogViewer.createRoute(s.id))
                                    },
                                    leadingIcon = { Icon(Icons.Default.Description, null, tint = colors.onSurfaceVariant) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Auditoria", color = colors.onSurface) },
                                    onClick = {
                                        menuExpanded = false
                                        navController.navigate(AppRoute.AuditLog.createRoute(s.id))
                                    },
                                    leadingIcon = { Icon(Icons.Default.History, null, tint = colors.onSurfaceVariant) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Configuracao do Agente", color = colors.onSurface) },
                                    onClick = {
                                        menuExpanded = false
                                        navController.navigate(AppRoute.AgentConfig.createRoute(s.id))
                                    },
                                    leadingIcon = { Icon(Icons.Default.Settings, null, tint = colors.onSurfaceVariant) }
                                )
                            }
                            HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
                            DropdownMenuItem(
                                text = { Text("Configuracao de Alertas", color = colors.onSurface) },
                                onClick = {
                                    menuExpanded = false
                                    navController.navigate(AppRoute.AlertSettings.route)
                                },
                                leadingIcon = { Icon(Icons.Default.NotificationsActive, null, tint = ext.magenta) }
                            )
                            selectedServer?.let { s ->
                                HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
                                DropdownMenuItem(
                                    text = { Text("Remover Servidor", color = StatusColors.critical) },
                                    onClick = { serverToDelete = s; showDeleteDialog = true; menuExpanded = false },
                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = StatusColors.critical) }
                                )
                            }
                        }
                    }
                }

                Box(Modifier.fillMaxWidth().height(Dimens.BorderThin).background(colors.primary.copy(alpha = 0.3f)))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).background(colors.background)
        ) {
            // Banner offline
            if (networkStatus == ConnectivityStatus.Unavailable || networkStatus == ConnectivityStatus.Lost) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(StatusColors.critical.copy(alpha = 0.85f))
                        .padding(vertical = Dimens.SpaceSm, horizontal = Dimens.SpaceLg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
                ) {
                    Icon(Icons.Default.CloudOff, null, tint = Color.White, modifier = Modifier.size(Dimens.IconSm))
                    Text("MODO OFFLINE — Dados em Cache", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }

            // ── Tabs de servidores ────────────────────────────────────
            if (servers.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = safeIndex,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = colors.background,
                    contentColor = colors.primary,
                    edgePadding = Dimens.SpaceLg,
                    divider = {},
                    indicator = { tabPositions ->
                        if (safeIndex < tabPositions.size) {
                            Box(
                                Modifier.tabIndicatorOffset(tabPositions[safeIndex])
                                    .height(2.dp)
                                    .background(Brush.horizontalGradient(listOf(Color.Transparent, colors.primary, Color.Transparent)))
                            )
                        }
                    }
                ) {
                    servers.forEachIndexed { index, server ->
                        val sel = safeIndex == index
                        Tab(
                            selected = sel,
                            onClick = { selectedServerIndex = index },
                            modifier = Modifier.padding(vertical = Dimens.SpaceXs),
                            text = {
                                Text(
                                    server.name.uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = if (sel) 1.5.sp else 0.5.sp,
                                    color = if (sel) colors.primary else colors.onSurfaceVariant
                                )
                            }
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(Dimens.BorderThin).background(colors.primary.copy(alpha = 0.08f)))
            }

            // ── Pills MÉTRICAS / WATCHDOG ────────────────────────────
            var featureTab by remember { mutableIntStateOf(0) }
            if (servers.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(colors.background)
                        .padding(horizontal = Dimens.SpaceXl, vertical = Dimens.SpaceMd),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
                ) {
                    FeaturePill("MÉTRICAS", Icons.Default.Analytics, featureTab == 0, colors.primary, Modifier.weight(1f)) { featureTab = 0 }
                    FeaturePill("WATCHDOG", Icons.Default.MonitorHeart, featureTab == 1, ext.magenta, Modifier.weight(1f)) { featureTab = 1 }
                }
                Box(
                    Modifier.fillMaxWidth().height(Dimens.BorderThin).background(
                        Brush.horizontalGradient(
                            if (featureTab == 0) listOf(colors.primary.copy(alpha = 0.4f), Color.Transparent)
                            else listOf(Color.Transparent, ext.magenta.copy(alpha = 0.4f))
                        )
                    )
                )
            }

            // ── Conteúdo ─────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxSize()) {
                if (featureTab == 1 && selectedServer != null) {
                    WatchdogScreen(server = selectedServer, viewModel = watchdogViewModel)
                } else when (val state = telemetryState.value) {
                    is TelemetryUiState.Loading -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXxl),
                            contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceXl)
                        ) {
                            item { ShimmerBox(modifier = Modifier.fillMaxWidth().height(90.dp)) }
                            item { ShimmerBox(modifier = Modifier.fillMaxWidth().height(80.dp)) }
                            item { ShimmerBox(modifier = Modifier.fillMaxWidth().height(80.dp)) }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceLg)) {
                                    ShimmerBox(modifier = Modifier.weight(1f).height(72.dp))
                                    ShimmerBox(modifier = Modifier.weight(1f).height(72.dp))
                                }
                            }
                        }
                    }

                    is TelemetryUiState.Success -> {
                        val t = state.telemetry
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXxl),
                            contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceXl)
                        ) {
                            item { StatusCard(pulseAlpha, selectedServer) }
                            item { FuturisticResourceCard("CPU USAGE", t.cpu.usagePercent.toInt(), ext.magenta, "⚡") }
                            item { FuturisticResourceCard("RAM USAGE", t.memory.usagePercent.toInt(), ext.blue, "🧠") }

                            item {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceLg)) {
                                    MetricCardFuturistic("UPTIME", "${t.uptime.uptimeSeconds / 3600}h", colors.primary, Modifier.weight(1f))
                                    MetricCardFuturistic("LOAD", String.format("%.2f", t.uptime.loadAverage.getOrNull(0) ?: 0f), ext.green, Modifier.weight(1f))
                                }
                            }

                            item {
                                val txMb = t.network.interfaces.sumOf { it.txBytes } / 1024 / 1024
                                val rxMb = t.network.interfaces.sumOf { it.rxBytes } / 1024 / 1024
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceLg)) {
                                    MetricCardFuturistic("TX", "${txMb}MB", colors.primary, Modifier.weight(1f))
                                    MetricCardFuturistic("RX", "${rxMb}MB", ext.green, Modifier.weight(1f))
                                }
                            }

                            item { SecurityStatusCard(security = t.security, onBlockIp = { ip -> selectedServer?.let { viewModel.blockIp(it, ip) } }) }
                            item { ServicesCard(services = t.services, onActionClick = { name, action -> selectedServer?.let { viewModel.performServiceAction(it, name, action) } }) }
                            item { ProcessesCard(t.processes) }

                            item {
                                Button(
                                    onClick = { showRebootDialog = true },
                                    modifier = Modifier.fillMaxWidth().height(Dimens.ButtonHeightLg)
                                        .shadow(8.dp, AppShapes.xl, spotColor = StatusColors.critical.copy(alpha = 0.4f)),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A0808)),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(
                                        brush = Brush.horizontalGradient(listOf(StatusColors.critical.copy(alpha = 0.8f), StatusColors.critical.copy(alpha = 0.4f)))
                                    ),
                                    shape = AppShapes.xl
                                ) {
                                    Icon(Icons.Default.PowerSettingsNew, null, tint = StatusColors.critical, modifier = Modifier.size(Dimens.IconMd))
                                    Spacer(Modifier.width(Dimens.SpaceMd))
                                    Text("REBOOT EMERGENCIAL", color = StatusColors.critical, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                }
                            }

                            item { Spacer(Modifier.height(Dimens.SpaceMd)) }
                        }
                    }

                    is TelemetryUiState.Error -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLg),
                                modifier = Modifier.padding(Dimens.Space3xl)
                            ) {
                                Text("⚠", style = MaterialTheme.typography.displayLarge, color = StatusColors.critical)
                                Text("Sem conexão com o agente", style = MaterialTheme.typography.headlineSmall, color = colors.primary)
                                Text(state.message, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                                Button(
                                    onClick = { selectedServer?.let { viewModel.fetchTelemetry(it) } },
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
}

// ═══════════════════════════════════════════════════════════════════
// Componentes privados limpos
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun DestructiveDialog(
    title: String,
    serverName: String?,
    description: String,
    confirmText: String,
    confirmIcon: ImageVector,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = StatusColors.critical, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                Text("Servidor: ${serverName ?: "—"}", color = colors.onSurface, fontWeight = FontWeight.SemiBold)
                Text(description, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = StatusColors.critical), shape = AppShapes.medium) {
                Icon(confirmIcon, null, modifier = Modifier.size(Dimens.IconSm))
                Spacer(Modifier.width(Dimens.SpaceSm))
                Text(confirmText, color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = AppShapes.medium) {
                Text("Cancelar", color = colors.primary)
            }
        },
        containerColor = colors.surfaceVariant,
        modifier = Modifier.border(Dimens.BorderThin, StatusColors.critical.copy(alpha = 0.5f), AppShapes.xl)
    )
}

@Composable
private fun StatusCard(pulseAlpha: State<Float>, server: ServerEntity?) {
    val colors = MaterialTheme.colorScheme
    val securityStatus = server?.securityStatus ?: 0
    val (shieldColor, shieldLabel) = when (securityStatus) {
        2    -> StatusColors.critical to "THREAT"
        1    -> StatusColors.warning  to "BYPASS"
        else -> StatusColors.success  to "SECURE"
    }

    val infiniteTransition = rememberInfiniteTransition(label = "status_pulse")
    val dotScale by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "dot_scale"
    )

    Box(
        modifier = Modifier.fillMaxWidth()
            .shadow(6.dp, AppShapes.sheet)
            .clip(AppShapes.sheet)
            .background(colors.surfaceVariant)
            .border(Dimens.BorderMedium, colors.primary.copy(alpha = pulseAlpha.value * 0.85f), AppShapes.sheet)
    ) {
        Column(modifier = Modifier.padding(horizontal = Dimens.SpaceXxl, vertical = 18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("INFRA STATUS", color = colors.primary.copy(alpha = pulseAlpha.value), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                    Spacer(Modifier.height(Dimens.SpaceSm))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                        Box(Modifier.size((10 * dotScale).dp).background(StatusColors.success, CircleShape))
                        Text("ONLINE", fontSize = 28.sp, fontWeight = FontWeight.Black, color = StatusColors.success, letterSpacing = 4.sp)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
                    Box(
                        modifier = Modifier.size(52.dp)
                            .shadow(12.dp, AppShapes.card, spotColor = shieldColor.copy(alpha = 0.6f))
                            .background(colors.surfaceVariant, AppShapes.card)
                            .border(Dimens.BorderThin, shieldColor.copy(alpha = 0.5f), AppShapes.card),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Security, null, tint = shieldColor, modifier = Modifier.size(Dimens.IconLg))
                    }
                    Text(shieldLabel, color = shieldColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                }
            }
            if (server != null) {
                Spacer(Modifier.height(Dimens.SpaceXl))
                HorizontalDivider(color = colors.primary.copy(alpha = 0.12f))
                Spacer(Modifier.height(Dimens.SpaceXl))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    InfoTag("OS", server.osInfo)
                    InfoTag("STACK", server.stackInfo)
                    InfoTag("LOCAL", server.locationInfo)
                }
            }
        }
    }
}

@Composable
private fun InfoTag(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXxs)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.primary.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TopBarBtn(icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(Dimens.TopBarButton).clip(AppShapes.large)
            .background(tint.copy(alpha = 0.10f))
            .border(Dimens.BorderThin, tint.copy(alpha = 0.35f), AppShapes.large)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(Dimens.IconMd))
    }
}

@Composable
private fun FeaturePill(
    label: String, icon: ImageVector, selected: Boolean, color: Color,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    Box(
        modifier = modifier.height(Dimens.PillHeight).clip(AppShapes.large)
            .background(if (selected) color.copy(alpha = 0.15f) else Color.Transparent)
            .border(Dimens.BorderThin, if (selected) color.copy(alpha = 0.55f) else color.copy(alpha = 0.18f), AppShapes.large)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
            Icon(icon, null, tint = if (selected) color else color.copy(alpha = 0.45f), modifier = Modifier.size(Dimens.IconMd))
            Text(
                label, style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontFamily = FontFamily.Monospace,
                color = if (selected) color else color.copy(alpha = 0.45f)
            )
        }
    }
}
