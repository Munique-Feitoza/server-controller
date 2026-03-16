package com.pocketnoc.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pocketnoc.data.models.CommandInfo
import com.pocketnoc.ui.theme.*
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
                    Text("⚙️ ACTION CENTER", style = MaterialTheme.typography.displaySmall, color = NeonCyan)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NeonCyan)
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
            selectedServer?.let {
                Text(
                    text = "Server: ${it.name}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = NeonMagenta,
                    modifier = Modifier.padding(16.dp)
                )
            }

            when (commandsState) {
                is DashboardViewModel.CommandsUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(32.dp),
                        color = NeonCyan
                    )
                }
                is DashboardViewModel.CommandsUiState.Success -> {
                    val commands = (commandsState as DashboardViewModel.CommandsUiState.Success).commands
                    
                    if (commands.isEmpty()) {
                        Text(
                            text = "Nenhum comando disponível",
                            color = TextSecondary,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(32.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(commands) { command ->
                                CommandCard(
                                    command = command,
                                    onExecute = {
                                        selectedCommand = command
                                        showConfirmDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
                is DashboardViewModel.CommandsUiState.Error -> {
                    Text(
                        text = "Erro ao carregar comandos: ${(commandsState as DashboardViewModel.CommandsUiState.Error).message}",
                        color = Color.Red,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }

    // Confirmation Dialog
    if (showConfirmDialog && selectedCommand != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Executar Comando?", color = NeonCyan) },
            text = {
                Column {
                    Text("Comando: ${selectedCommand!!.id}", color = Color.White)
                    Text("Descrição: ${selectedCommand!!.description}", color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedServer?.let { server ->
                            viewModel.executeCommand(server, selectedCommand!!.id)
                        }
                        showConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                ) {
                    Text("Executar", color = DarkBackground)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showConfirmDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurface)
                ) {
                    Text("Cancelar", color = NeonCyan)
                }
            },
            containerColor = DarkCard,
            modifier = Modifier.border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
        )
    }
}

@Composable
fun CommandCard(
    command: CommandInfo,
    onExecute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, spotColor = NeonGreen.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = command.id,
                    style = MaterialTheme.typography.headlineSmall,
                    color = NeonGreen
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = command.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                if (command.timeout > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Timeout: ${command.timeout}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan.copy(alpha = 0.6f)
                    )
                }
            }

            Button(
                onClick = onExecute,
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Execute",
                    tint = DarkBackground,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Executar", color = DarkBackground, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
