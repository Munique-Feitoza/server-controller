package com.pocketnoc.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
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
import com.pocketnoc.data.local.entities.ServerEntity
import com.pocketnoc.data.models.HealthStatus
import com.pocketnoc.data.models.ServerHealth
import com.pocketnoc.ui.components.ShimmerBox
import com.pocketnoc.ui.theme.*
import com.pocketnoc.ui.viewmodels.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    viewModel: DashboardViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (Int) -> Unit,
    onNavigateToAddServer: (() -> Unit)? = null
) {
    val servers by viewModel.allServers.collectAsState()
    val serverHealthMap by viewModel.serverHealthMap.collectAsState()
    var serverToDelete by remember { mutableStateOf<ServerEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SERVIDORES", style = MaterialTheme.typography.titleLarge, color = NeonCyan, fontWeight = FontWeight.Bold)
                        Text("${servers.size} node${if (servers.size != 1) "s" else ""} configurado${if (servers.size != 1) "s" else ""}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = NeonCyan)
                    }
                },
                actions = {
                    onNavigateToAddServer?.let {
                        IconButton(onClick = it) {
                            Icon(Icons.Default.Add, contentDescription = "Adicionar servidor", tint = NeonGreen)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface.copy(alpha = 0.9f)),
                modifier = Modifier.shadow(8.dp, spotColor = NeonCyan.copy(alpha = 0.25f))
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(listOf(DarkBackground, Color(0xFF0A1428), DarkBackground))
                )
        ) {
            if (servers.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("[ ]", style = MaterialTheme.typography.displayLarge, color = NeonCyan.copy(alpha = 0.3f))
                        Text("Nenhum servidor configurado", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                        Text("Adicione um servidor pelo Dashboard.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
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

    if (serverToDelete != null) {
        AlertDialog(
            onDismissRequest = { serverToDelete = null },
            title = { Text("Remover Servidor?", color = CriticalRedHealth, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Servidor: ${serverToDelete!!.name}", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("As credenciais salvas serão removidas permanentemente.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteServer(serverToDelete!!)
                        serverToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CriticalRedHealth),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Remover", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                OutlinedButton(onClick = { serverToDelete = null }, shape = RoundedCornerShape(8.dp)) {
                    Text("Cancelar", color = NeonCyan)
                }
            },
            containerColor = DarkCard,
            modifier = Modifier.border(1.dp, CriticalRedHealth.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
        )
    }
}

@Composable
fun ServerListItem(
    server: ServerEntity,
    health: ServerHealth?,
    onDelete: () -> Unit,
    onNavigateToDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Pulso animado no indicador de status
    val infiniteTransition = rememberInfiniteTransition(label = "dot_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "dot_alpha"
    )

    val statusColor = when (health?.status) {
        HealthStatus.CRITICAL -> CriticalRedHealth
        HealthStatus.ALERT    -> AlertYellow
        HealthStatus.WARNING  -> WarningGreen
        HealthStatus.HEALTHY  -> HealthyBlue
        null                  -> TextMuted  // ainda carregando
    }

    val statusLabel = when (health?.status) {
        HealthStatus.CRITICAL -> "CRÍTICO"
        HealthStatus.ALERT    -> "ALERTA"
        HealthStatus.WARNING  -> "AVISO"
        HealthStatus.HEALTHY  -> "OK"
        null                  -> "—"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(14.dp), spotColor = statusColor.copy(alpha = 0.3f))
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(listOf(statusColor.copy(alpha = 0.07f), DarkCard))
            )
            .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .clickable(onClick = onNavigateToDetails)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Indicador de status pulsante
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(statusColor.copy(alpha = dotAlpha), RoundedCornerShape(5.dp))
            )
            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(server.name, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    // Badge de status
                    Box(
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                            .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (health != null) {
                    // Barras compactas de CPU/RAM/Disco
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MiniMetricBar("CPU", health.cpuUsage, NeonMagenta, Modifier.weight(1f))
                        MiniMetricBar("RAM", health.memoryUsage, NeonBlue, Modifier.weight(1f))
                        MiniMetricBar("DSK", health.diskUsage, NeonCyan, Modifier.weight(1f))
                    }

                    if (health.activeAlerts > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .background(CriticalRedHealth.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(modifier = Modifier.size(6.dp).background(CriticalRedHealth, RoundedCornerShape(3.dp)))
                            Text(
                                "${health.activeAlerts} alerta${if (health.activeAlerts > 1) "s" else ""} ativo${if (health.activeAlerts > 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = CriticalRedHealth
                            )
                        }
                    }
                } else {
                    // Shimmer enquanto a saúde ainda não foi carregada
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ShimmerBox(modifier = Modifier.fillMaxWidth().height(8.dp), shape = RoundedCornerShape(4.dp))
                        ShimmerBox(modifier = Modifier.fillMaxWidth(0.7f).height(8.dp), shape = RoundedCornerShape(4.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Remover", tint = CriticalRedHealth.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = NeonCyan.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun MiniMetricBar(label: String, value: Float, color: Color, modifier: Modifier = Modifier) {
    val barColor = when {
        value > 90 -> CriticalRedHealth
        value > 75 -> AlertYellow
        else       -> color
    }
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp))
            Text("${value.toInt()}%", style = MaterialTheme.typography.labelSmall, color = barColor, fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp))
        }
        Spacer(Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { (value / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
            color = barColor,
            trackColor = DarkSurface
        )
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
