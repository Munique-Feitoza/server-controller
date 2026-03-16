package com.pocketnoc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pocketnoc.data.models.HealthStatus
import com.pocketnoc.ui.components.ServerHealthWidget
import com.pocketnoc.ui.theme.*
import com.pocketnoc.ui.viewmodels.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    viewModel: DashboardViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (Int) -> Unit
) {
    val servers by viewModel.allServers.collectAsState()
    val serverHealthMap by viewModel.serverHealthMap.collectAsState()
    var serverToDelete by remember { mutableStateOf<com.pocketnoc.data.local.entities.ServerEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("📋 SERVIDORES", style = MaterialTheme.typography.displaySmall, color = NeonCyan)
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
                .padding(16.dp)
        ) {
            if (servers.isEmpty()) {
                Text(
                    text = "Nenhum servidor configurado",
                    color = TextSecondary,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(32.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(servers) { server ->
                        val health = serverHealthMap[server.id]
                        ServerListItem(
                            server = server,
                            health = health,
                            onDelete = { serverToDelete = server },
                            onNavigateToDetails = { onNavigateToDetails(server.id) }
                        )
                    }
                }
            }
        }
    }

    // Confirmation Dialog for Delete
    if (serverToDelete != null) {
        AlertDialog(
            onDismissRequest = { serverToDelete = null },
            title = { Text("Deletar Servidor?", color = NeonCyan) },
            text = {
                Text(
                    "Você tem certeza que deseja deletar ${serverToDelete!!.name}?",
                    color = Color.White
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteServer(serverToDelete!!)
                        serverToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Deletar", color = Color.White)
                }
            },
            dismissButton = {
                Button(
                    onClick = { serverToDelete = null },
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
fun ServerListItem(
    server: com.pocketnoc.data.local.entities.ServerEntity,
    health: com.pocketnoc.data.models.ServerHealth?,
    onDelete: () -> Unit,
    onNavigateToDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, spotColor = NeonCyan.copy(alpha = 0.3f))
            .clickable(onClick = onNavigateToDetails),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            when (health?.status) {
                HealthStatus.HEALTHY -> HealthyBlue.copy(alpha = 0.5f)
                HealthStatus.WARNING -> WarningGreen.copy(alpha = 0.5f)
                HealthStatus.ALERT -> AlertYellow.copy(alpha = 0.5f)
                HealthStatus.CRITICAL -> CriticalRedHealth.copy(alpha = 0.5f)
                else -> NeonCyan.copy(alpha = 0.3f)
            }
        )
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
                    text = server.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = NeonMagenta
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                if (health != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        StatBadge(label = "CPU", value = "${health.cpuUsage}%", color = NeonMagenta)
                        StatBadge(label = "RAM", value = "${health.memoryUsage}%", color = NeonBlue)
                        StatBadge(label = "DISK", value = "${health.diskUsage}%", color = NeonCyan)
                    }
                    
                    if (health.activeAlerts > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "⚠️ ${health.activeAlerts} alertas ativos",
                            style = MaterialTheme.typography.labelSmall,
                            color = AlertYellow
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f))
                }
                Icon(Icons.Default.ChevronRight, contentDescription = "Details", tint = NeonCyan)
            }
        }
    }
}

@Composable
fun StatBadge(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
            .padding(6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
