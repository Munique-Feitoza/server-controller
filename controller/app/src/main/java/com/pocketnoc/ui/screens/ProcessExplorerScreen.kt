package com.pocketnoc.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
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
import com.pocketnoc.data.models.ProcessInfo
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
                    Text("💀 PROCESS EXPLORER", style = MaterialTheme.typography.displaySmall, color = NeonMagenta)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NeonMagenta)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface.copy(alpha = 0.8f))
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(DarkBackground, Color(0xFF1A0F1F), DarkBackground)
                    )
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                selectedServer?.let {
                    Text(
                        text = "Monitoring: ${it.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonCyan,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                when (val state = processesState) {
                    is ProcessesUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = NeonMagenta)
                        }
                    }
                    is ProcessesUiState.Success -> {
                        val processes = state.processes
                        ProcessList(
                            processes = processes,
                            onKillRequest = { showKillConfirmation = it }
                        )
                    }
                    is ProcessesUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = state.message,
                                color = Color.Red,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal de Confirmação de Kill
    if (showKillConfirmation != null) {
        AlertDialog(
            onDismissRequest = { showKillConfirmation = null },
            title = { Text("TERMINATE PROCESS?", color = Color.Red, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("PID: ${showKillConfirmation!!.pid}", color = Color.White)
                    Text("Name: ${showKillConfirmation!!.name}", color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("This action is irreversible and may cause system instability.", color = TextSecondary)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedServer?.let { server ->
                            viewModel.killProcess(server, showKillConfirmation!!.pid.toLong())
                        }
                        showKillConfirmation = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("KILL", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showKillConfirmation = null }) {
                    Text("CANCEL", color = NeonCyan)
                }
            },
            containerColor = DarkSurface,
            modifier = Modifier.border(2.dp, Color.Red.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
        )
    }
}

@Composable
fun ProcessList(
    processes: List<ProcessInfo>,
    onKillRequest: (ProcessInfo) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(processes) { process ->
            ProcessCard(process = process, onKill = { onKillRequest(process) })
        }
    }
}

@Composable
fun ProcessCard(
    process: ProcessInfo,
    onKill: () -> Unit
) {
    val isHeavy = process.cpuUsage > 50f
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isHeavy) 8.dp else 2.dp, spotColor = if (isHeavy) Color.Red else NeonPurple),
        colors = CardDefaults.cardColors(containerColor = DarkCard.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isHeavy) Color.Red.copy(alpha = 0.6f) else NeonPurple.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "[${process.pid}]",
                        style = MaterialTheme.typography.labelMedium,
                        color = NeonCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = process.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MetricLabel(label = "CPU", value = "${String.format("%.1f", process.cpuUsage.toDouble())}%", color = if (isHeavy) Color.Red else NeonGreen)
                    MetricLabel(label = "MEM", value = "${process.memoryMb}MB", color = NeonCyan)
                }
            }

            IconButton(
                onClick = onKill,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isHeavy) Color.Red.copy(alpha = 0.2f) else DarkSurface)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Kill",
                    tint = if (isHeavy) Color.Red else Color.Gray,
                    modifier = Modifier.size(20.dp)
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
