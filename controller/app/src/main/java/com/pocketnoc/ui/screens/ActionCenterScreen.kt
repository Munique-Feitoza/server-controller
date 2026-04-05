package com.pocketnoc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    val colors = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current

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
                        Text("ACTION CENTER", style = MaterialTheme.typography.titleLarge, color = colors.tertiary, fontWeight = FontWeight.Bold)
                        selectedServer?.let {
                            Text(it.name, style = MaterialTheme.typography.labelSmall, color = colors.outlineVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = colors.tertiary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface.copy(alpha = 0.9f)),
                modifier = Modifier.shadow(Dimens.SpaceMd, spotColor = colors.tertiary.copy(alpha = 0.25f))
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
            when (val state = commandsState) {
                is CommandsUiState.Loading -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding),
                        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLg - Dimens.SpaceXxs),
                        contentPadding = PaddingValues(bottom = Dimens.ScreenPadding)
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
                            Text("Nenhum comando disponível", color = colors.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.ScreenPadding),
                            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLg - Dimens.SpaceXxs),
                            contentPadding = PaddingValues(vertical = Dimens.SpaceXl - Dimens.SpaceXxs)
                        ) {
                            item {
                                Text(
                                    "PROTOCOLOS DISPONÍVEIS — ${commands.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.outlineVariant,
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
                            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
                        ) {
                            Text("⚠", style = MaterialTheme.typography.displayLarge, color = StatusColors.critical)
                            Text(state.message, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            Button(
                                onClick = { selectedServer?.let { viewModel.fetchCommands(it) } },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.tertiary.copy(alpha = 0.15f)),
                                shape = AppShapes.medium
                            ) { Text("Tentar novamente", color = colors.tertiary) }
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = colors.tertiary, modifier = Modifier.size(Dimens.IconMd))
                    Text("EXECUTAR PROTOCOLO?", color = colors.tertiary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                        Text("ID:", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Text(cmd.id, color = colors.tertiary, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Text(cmd.description, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedServer?.let { server -> viewModel.executeCommand(server, cmd.id) }
                        showConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.tertiary),
                    shape = AppShapes.medium
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.background, modifier = Modifier.size(Dimens.IconSm))
                    Spacer(modifier = Modifier.width(Dimens.SpaceXs))
                    Text("EXECUTAR", color = colors.background, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmDialog = false }, shape = AppShapes.medium) {
                    Text("Cancelar", color = colors.primary)
                }
            },
            containerColor = colors.surfaceVariant,
            modifier = Modifier.border(Dimens.BorderThin, colors.tertiary.copy(alpha = 0.4f), AppShapes.xl)
        )
    }
}

@Composable
fun CommandCard(command: CommandInfo, onExecute: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.xl)
            .background(
                Brush.horizontalGradient(listOf(colors.tertiary.copy(alpha = 0.05f), colors.surfaceVariant))
            )
            .border(Dimens.BorderThin, colors.tertiary.copy(alpha = 0.25f), AppShapes.xl)
            .padding(horizontal = Dimens.SpaceXl, vertical = Dimens.SpaceXl - Dimens.SpaceXxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Indicador de status
        Box(
            modifier = Modifier
                .size(Dimens.StatusDot)
                .background(colors.tertiary, AppShapes.pill)
        )
        Spacer(modifier = Modifier.width(Dimens.SpaceLg))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = command.id,
                style = MaterialTheme.typography.labelMedium,
                color = colors.tertiary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(Dimens.SpaceXxs))
            Text(
                text = command.description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(Dimens.SpaceLg))

        Button(
            onClick = onExecute,
            colors = ButtonDefaults.buttonColors(containerColor = colors.tertiary.copy(alpha = 0.15f)),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = Brush.horizontalGradient(listOf(colors.tertiary.copy(alpha = 0.8f), colors.tertiary.copy(alpha = 0.4f)))
            ),
            shape = AppShapes.medium,
            modifier = Modifier.height(Dimens.ActionButton),
            contentPadding = PaddingValues(horizontal = Dimens.SpaceLg)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = colors.tertiary, modifier = Modifier.size(Dimens.IconXs + Dimens.SpaceXxs))
            Spacer(modifier = Modifier.width(Dimens.SpaceXs))
            Text("EXEC", color = colors.tertiary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}
