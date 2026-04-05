package com.pocketnoc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
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
import com.pocketnoc.data.models.CommandInfo
import com.pocketnoc.ui.components.ShimmerBox
import com.pocketnoc.ui.theme.*
import com.pocketnoc.ui.viewmodels.CommandsUiState
import com.pocketnoc.ui.viewmodels.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionCenterScreen(
    viewModel: DashboardViewModel,
    serverId: Int,
    onNavigateBack: () -> Unit
) {
    val servers by viewModel.allServers.collectAsState()
    val selectedServer = servers.find { it.id == serverId }
    val commandsState by viewModel.commandsState.collectAsState()
    var selectedCommand by remember { mutableStateOf<CommandInfo?>(null) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(selectedServer) {
        selectedServer?.let { viewModel.fetchCommands(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ACTION CENTER", style = MaterialTheme.typography.titleLarge, color = NeonGreen, fontWeight = FontWeight.Bold)
                        selectedServer?.let {
                            Text(it.name, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = NeonGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface.copy(alpha = 0.9f)),
                modifier = Modifier.shadow(8.dp, spotColor = NeonGreen.copy(alpha = 0.25f))
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(listOf(DarkBackground, Color(0xFF091A0F), DarkBackground))
                )
        ) {
            when (val state = commandsState) {
                is CommandsUiState.Loading -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(6) {
                            ShimmerBox(modifier = Modifier.fillMaxWidth().height(78.dp))
                        }
                    }
                }

                is CommandsUiState.Success -> {
                    val commands = state.commands
                    if (commands.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Nenhum comando disponível", color = TextSecondary)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 14.dp)
                        ) {
                            item {
                                Text(
                                    "PROTOCOLOS DISPONÍVEIS — ${commands.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            items(commands) { command ->
                                CommandCard(command = command, onExecute = {
                                    selectedCommand = command
                                    showConfirmDialog = true
                                })
                            }
                        }
                    }
                }

                is CommandsUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("⚠", style = MaterialTheme.typography.displayLarge, color = CriticalRedHealth)
                            Text(state.message, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            Button(
                                onClick = { selectedServer?.let { viewModel.fetchCommands(it) } },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Tentar novamente", color = NeonGreen) }
                        }
                    }
                }
            }
        }
    }

    if (showConfirmDialog && selectedCommand != null) {
        val cmd = selectedCommand!!
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(20.dp))
                    Text("EXECUTAR PROTOCOLO?", color = NeonGreen, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ID:", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Text(cmd.id, color = NeonGreen, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Text(cmd.description, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedServer?.let { server -> viewModel.executeCommand(server, cmd.id) }
                        showConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("EXECUTAR", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmDialog = false }, shape = RoundedCornerShape(8.dp)) {
                    Text("Cancelar", color = NeonCyan)
                }
            },
            containerColor = DarkCard,
            modifier = Modifier.border(1.dp, NeonGreen.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
        )
    }
}

@Composable
fun CommandCard(command: CommandInfo, onExecute: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(listOf(NeonGreen.copy(alpha = 0.05f), DarkCard))
            )
            .border(1.dp, NeonGreen.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Indicador de status
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(NeonGreen, RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = command.id,
                style = MaterialTheme.typography.labelMedium,
                color = NeonGreen,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = command.description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Button(
            onClick = onExecute,
            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen.copy(alpha = 0.15f)),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = Brush.horizontalGradient(listOf(NeonGreen.copy(alpha = 0.8f), NeonGreen.copy(alpha = 0.4f)))
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.height(36.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("EXEC", color = NeonGreen, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}
