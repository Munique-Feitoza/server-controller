package com.pocketnoc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
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
                        Text("PROCESS EXPLORER", style = MaterialTheme.typography.titleLarge, color = NeonMagenta, fontWeight = FontWeight.Bold)
                        selectedServer?.let {
                            Text(it.name, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = NeonMagenta)
                    }
                },
                actions = {
                    IconButton(onClick = { selectedServer?.let { viewModel.fetchProcesses(it) } }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar", tint = NeonCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface.copy(alpha = 0.9f)),
                modifier = Modifier.shadow(8.dp, spotColor = NeonMagenta.copy(alpha = 0.3f))
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(listOf(DarkBackground, Color(0xFF140A1F), DarkBackground))
                )
        ) {
            when (val state = processesState) {
                is ProcessesUiState.Loading -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
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
                            Text("Nenhum processo encontrado", color = TextSecondary)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 14.dp)
                        ) {
                            // Header da tabela
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkSurface)
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("PROCESSO", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.weight(1f))
                                    Text("CPU", style = MaterialTheme.typography.labelSmall, color = NeonMagenta, modifier = Modifier.width(56.dp))
                                    Text("MEM", style = MaterialTheme.typography.labelSmall, color = NeonBlue, modifier = Modifier.width(56.dp))
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("⚠", style = MaterialTheme.typography.displayLarge, color = CriticalRedHealth)
                            Text(state.message, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            Button(
                                onClick = { selectedServer?.let { viewModel.fetchProcesses(it) } },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Tentar novamente", color = NeonCyan) }
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = CriticalRedHealth, modifier = Modifier.size(20.dp))
                    Text("ENCERRAR PROCESSO?", color = CriticalRedHealth, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("PID:", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Text("[${proc.pid}]", color = NeonCyan, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Nome:", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Text(proc.name, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Esta ação é irreversível. O processo receberá um sinal SIGKILL.",
                        color = TextSecondary,
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
                    colors = ButtonDefaults.buttonColors(containerColor = CriticalRedHealth),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("KILL", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showKillConfirmation = null }, shape = RoundedCornerShape(8.dp)) {
                    Text("Cancelar", color = NeonCyan)
                }
            },
            containerColor = DarkCard,
            modifier = Modifier.border(1.dp, CriticalRedHealth.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
        )
    }
}

@Composable
fun ProcessCard(process: ProcessInfo, onKill: () -> Unit) {
    val isHeavy = process.cpuUsage > 50f
    val borderColor = when {
        isHeavy              -> CriticalRedHealth.copy(alpha = 0.7f)
        process.cpuUsage > 20 -> AlertYellow.copy(alpha = 0.5f)
        else                  -> NeonPurple.copy(alpha = 0.2f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isHeavy) CriticalRedHealth.copy(alpha = 0.06f) else DarkCard.copy(alpha = 0.9f))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "[${process.pid}]",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = process.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // CPU bar
        Column(modifier = Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${String.format("%.1f", process.cpuUsage.toDouble())}%",
                style = MaterialTheme.typography.labelSmall,
                color = if (isHeavy) CriticalRedHealth else NeonMagenta,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            LinearProgressIndicator(
                progress = { (process.cpuUsage / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                color = if (isHeavy) CriticalRedHealth else NeonMagenta,
                trackColor = DarkSurface
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // RAM
        Text(
            "${process.memoryMb}MB",
            style = MaterialTheme.typography.labelSmall,
            color = NeonBlue,
            modifier = Modifier.width(56.dp),
            fontWeight = FontWeight.Medium
        )

        // Kill button — só visível em processos pesados ou sempre clicável
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isHeavy) CriticalRedHealth.copy(alpha = 0.2f)
                    else DarkSurface.copy(alpha = 0.6f)
                )
                .border(
                    1.dp,
                    if (isHeavy) CriticalRedHealth.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f),
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = onKill, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Kill",
                    tint = if (isHeavy) CriticalRedHealth else TextMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun MetricLabel(label: String, value: String, color: Color) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Bold)
    }
}
