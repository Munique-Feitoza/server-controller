package com.pocketnoc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocketnoc.data.models.ProcessInfo
import com.pocketnoc.ui.components.ShimmerBox
import com.pocketnoc.ui.theme.*
import com.pocketnoc.ui.viewmodels.DashboardViewModel
import com.pocketnoc.ui.viewmodels.ProcessesUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessExplorerScreen(
    viewModel: DashboardViewModel,
    serverId: Int,
    onNavigateBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current

    val servers by viewModel.allServers.collectAsState()
    val selectedServer = servers.find { it.id == serverId }
    val processesState by viewModel.processesState.collectAsState()
    var showKillConfirmation by remember { mutableStateOf<ProcessInfo?>(null) }

    LaunchedEffect(selectedServer) {
        selectedServer?.let { viewModel.fetchProcesses(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("PROCESS EXPLORER", style = MaterialTheme.typography.titleLarge, color = ext.magenta, fontWeight = FontWeight.Bold)
                        selectedServer?.let {
                            Text(it.name, style = MaterialTheme.typography.labelSmall, color = colors.outlineVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = ext.magenta)
                    }
                },
                actions = {
                    IconButton(onClick = { selectedServer?.let { viewModel.fetchProcesses(it) } }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar", tint = colors.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface.copy(alpha = 0.9f)),
                modifier = Modifier.shadow(8.dp, spotColor = ext.magenta.copy(alpha = 0.3f))
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
            when (val state = processesState) {
                is ProcessesUiState.Loading -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding),
                        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
                        contentPadding = PaddingValues(bottom = Dimens.ScreenPadding)
                    ) {
                        items(10) {
                            ShimmerBox(modifier = Modifier.fillMaxWidth().height(72.dp))
                        }
                    }
                }

                is ProcessesUiState.Success -> {
                    val processes = state.processes
                    if (processes.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Nenhum processo encontrado", color = colors.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.ScreenPadding),
                            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
                            contentPadding = PaddingValues(vertical = Dimens.RadiusCard)
                        ) {
                            // Header da tabela
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(AppShapes.medium)
                                        .background(colors.surface)
                                        .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceMd),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("PROCESSO", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                                    Text("CPU", style = MaterialTheme.typography.labelSmall, color = ext.magenta, modifier = Modifier.width(56.dp))
                                    Text("MEM", style = MaterialTheme.typography.labelSmall, color = ext.blue, modifier = Modifier.width(56.dp))
                                    Spacer(modifier = Modifier.width(40.dp))
                                }
                            }
                            items(processes) { process ->
                                ProcessCard(process = process, onKill = { showKillConfirmation = process })
                            }
                        }
                    }
                }

                is ProcessesUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                            Text("\u26A0", style = MaterialTheme.typography.displayLarge, color = StatusColors.critical)
                            Text(state.message, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            Button(
                                onClick = { selectedServer?.let { viewModel.fetchProcesses(it) } },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primary.copy(alpha = 0.15f)),
                                shape = AppShapes.medium
                            ) { Text("Tentar novamente", color = colors.primary) }
                        }
                    }
                }
            }
        }
    }

    // Diálogo de kill
    if (showKillConfirmation != null) {
        val proc = showKillConfirmation!!
        AlertDialog(
            onDismissRequest = { showKillConfirmation = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = StatusColors.critical, modifier = Modifier.size(Dimens.IconMd))
                    Text("ENCERRAR PROCESSO?", color = StatusColors.critical, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                        Text("PID:", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Text("[${proc.pid}]", color = colors.primary, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                        Text("Nome:", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Text(proc.name, color = colors.onSurface, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(Dimens.SpaceXs))
                    Text(
                        "Esta ação é irreversível. O processo receberá um sinal SIGKILL.",
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedServer?.let { server ->
                            viewModel.killProcess(server, proc.pid.toLong())
                        }
                        showKillConfirmation = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusColors.critical),
                    shape = AppShapes.medium
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(Dimens.SpaceXs))
                    Text("KILL", color = colors.onSurface, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showKillConfirmation = null }, shape = AppShapes.medium) {
                    Text("Cancelar", color = colors.primary)
                }
            },
            containerColor = colors.surfaceVariant,
            modifier = Modifier.border(Dimens.BorderThin, StatusColors.critical.copy(alpha = 0.4f), AppShapes.xl)
        )
    }
}

@Composable
fun ProcessCard(process: ProcessInfo, onKill: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current

    val isHeavy = process.cpuUsage > 50f
    val borderColor = when {
        isHeavy              -> StatusColors.critical.copy(alpha = 0.7f)
        process.cpuUsage > 20 -> StatusColors.warning.copy(alpha = 0.5f)
        else                  -> ext.purple.copy(alpha = 0.2f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.large)
            .background(if (isHeavy) StatusColors.critical.copy(alpha = 0.06f) else colors.surfaceVariant.copy(alpha = 0.9f))
            .border(Dimens.BorderThin, borderColor, AppShapes.large)
            .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceLg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                Text(
                    text = "[${process.pid}]",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = process.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // CPU bar
        Column(modifier = Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${String.format("%.1f", process.cpuUsage.toDouble())}%",
                style = MaterialTheme.typography.labelSmall,
                color = if (isHeavy) StatusColors.critical else ext.magenta,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(Dimens.SpaceXxs))
            LinearProgressIndicator(
                progress = { (process.cpuUsage / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(Dimens.ProgressSm).clip(AppShapes.small),
                color = if (isHeavy) StatusColors.critical else ext.magenta,
                trackColor = colors.surface
            )
        }

        Spacer(modifier = Modifier.width(Dimens.SpaceMd))

        // RAM
        Text(
            "${process.memoryMb}MB",
            style = MaterialTheme.typography.labelSmall,
            color = ext.blue,
            modifier = Modifier.width(56.dp),
            fontWeight = FontWeight.Medium
        )

        // Botao de encerrar processo
        Box(
            modifier = Modifier
                .size(Dimens.ChipHeight)
                .clip(AppShapes.medium)
                .background(
                    if (isHeavy) StatusColors.critical.copy(alpha = 0.2f)
                    else colors.surface.copy(alpha = 0.6f)
                )
                .border(
                    Dimens.BorderThin,
                    if (isHeavy) StatusColors.critical.copy(alpha = 0.5f) else colors.onSurface.copy(alpha = 0.08f),
                    AppShapes.medium
                ),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = onKill, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Kill",
                    tint = if (isHeavy) StatusColors.critical else colors.outlineVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun MetricLabel(label: String, value: String, color: Color) {
    val colors = MaterialTheme.colorScheme
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Bold)
    }
}
