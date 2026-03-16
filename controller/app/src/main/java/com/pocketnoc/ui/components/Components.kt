package com.pocketnoc.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocketnoc.data.models.EmergencyCommand
import com.pocketnoc.data.models.ServiceInfo
import com.pocketnoc.data.models.ServiceStatus
import com.pocketnoc.data.models.HealthStatus
import com.pocketnoc.ui.theme.*

/**
 * Status Indicator Futurista com animação neon pulsante
 */
@Composable
fun StatusTrafficLight(
    status: TrafficLightStatus,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "status_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                spotColor = status.color.copy(alpha = glowAlpha),
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        DarkCard.copy(alpha = 0.9f),
                        DarkCard
                    )
                )
            )
            .border(
                width = 2.dp,
                color = status.color.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .shadow(
                    elevation = 20.dp,
                    spotColor = status.color.copy(alpha = glowAlpha),
                    shape = RoundedCornerShape(20.dp)
                )
                .clip(RoundedCornerShape(20.dp))
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            status.color.copy(alpha = 0.8f),
                            status.color.copy(alpha = 0.2f)
                        )
                    )
                )
                .border(
                    width = 2.dp,
                    color = status.color,
                    shape = RoundedCornerShape(20.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (status == TrafficLightStatus.HEALTHY) Icons.Default.Check else Icons.Default.Close,
                contentDescription = status.label,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = status.label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = status.color
        )
    }
}

enum class TrafficLightStatus(val color: Color, val label: String) {
    HEALTHY(HealthyGreen, "Healthy"),
    WARNING(WarningOrange, "Warning"),
    CRITICAL(CriticalRed, "Critical")
}

/**
 * Card de métrica futurista com gradiente
 */
@Composable
fun MetricCard(
    title: String,
    value: String,
    unit: String = "",
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                spotColor = NeonCyan.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.displaySmall,
                        color = NeonCyan
                    )

                    if (unit.isNotEmpty()) {
                        Text(
                            text = unit,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Barra de progresso futurista com gradient
 */
@Composable
fun PercentageBar(
    label: String,
    percentage: Float,
    modifier: Modifier = Modifier,
    color: Color = NeonGreen
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )

            Text(
                text = "${percentage.toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(DarkSurface)
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percentage / 100f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                color.copy(alpha = 0.5f),
                                color,
                                color.copy(alpha = 0.5f)
                            )
                        )
                    )
                    .shadow(
                        elevation = 4.dp,
                        spotColor = color.copy(alpha = 0.5f)
                    )
            )
        }
    }
}

/**
 * Botão de ação de emergência com estilo futurista
 */
@Composable
fun EmergencyActionButton(
    label: String,
    description: String,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .shadow(
                elevation = 16.dp,
                spotColor = CriticalRed.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp)
            ),
        colors = ButtonDefaults.buttonColors(
            containerColor = CriticalRed
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

// ========== COMPONENTES FUTURISTICOS ESPECIALIZADOS ==========

@Composable
fun FuturisticResourceCard(
    title: String,
    percentage: Int,
    color: Color,
    icon: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                spotColor = color.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = color.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, style = MaterialTheme.typography.displaySmall)
            }

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Text(
                    "$percentage%",
                    style = MaterialTheme.typography.displaySmall,
                    color = color
                )
            }

            // Progress Indicator
            CircularProgressIndicator(
                progress = percentage / 100f,
                modifier = Modifier.size(56.dp),
                color = color,
                trackColor = DarkSurface,
                strokeWidth = 4.dp
            )
        }

        // Linear progress com gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            color.copy(alpha = 0.2f),
                            color,
                            color.copy(alpha = 0.2f)
                        )
                    )
                ),
            content = {}
        )
    }
}

@Composable
fun MetricCardFuturistic(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                spotColor = color.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                color = color
            )
        }
    }
}

@Composable
fun EmergencyActionButtonFuturistic(
    label: String,
    description: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .shadow(
                elevation = 12.dp,
                spotColor = CriticalRed.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            ),
        colors = ButtonDefaults.buttonColors(containerColor = CriticalRed),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Power,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
                Text(
                    description,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}
@Composable
fun SecurityStatusCard(security: com.pocketnoc.data.models.SecurityMetrics) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, spotColor = NeonGreen.copy(alpha = 0.4f), shape = RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("SECURITY STATUS", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(if (security.activeSshSessions > 0) WarningOrange else HealthyGreen, RoundedCornerShape(6.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "SSH Sessions: ${security.activeSshSessions}",
                    color = if (security.activeSshSessions > 0) WarningOrange else HealthyGreen
                )
            }
        }
    }
}

@Composable
fun ProcessesCard(processes: com.pocketnoc.data.models.ProcessMetrics) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, spotColor = NeonMagenta.copy(alpha = 0.4f), shape = RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("TOP PROCESSES", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(modifier = Modifier.height(12.dp))
            processes.topProcesses.take(5).forEach { process ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(process.name, style = MaterialTheme.typography.bodyMedium, color = Color.White, modifier = Modifier.weight(1f))
                    Text("${process.cpuUsage.toInt()}% CPU", color = NeonMagenta, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${process.memoryMb}MB", color = NeonBlue, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun ServicesCard(
    services: List<ServiceInfo>,
    onActionClick: (String, String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, spotColor = NeonCyan.copy(alpha = 0.4f), shape = RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("SERVICE MANAGER", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(modifier = Modifier.height(12.dp))
            
            if (services.isEmpty()) {
                Text("No critical services detected", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }

            services.forEach { service ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(service.name, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (service.status == ServiceStatus.ACTIVE) HealthyGreen else CriticalRed,
                                        RoundedCornerShape(4.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                service.status.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (service.status == ServiceStatus.ACTIVE) HealthyGreen else CriticalRed
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Restart Button
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurface)
                                .border(1.2.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                .clickable { onActionClick(service.name, "restart") }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Restart",
                                tint = NeonCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        val isRunning = service.status == ServiceStatus.ACTIVE
                        val actionColor = if (isRunning) CriticalRed else HealthyGreen
                        
                        // Start/Stop Button
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurface)
                                .border(1.2.dp, actionColor.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                .clickable { onActionClick(service.name, if (isRunning) "stop" else "start") }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (isRunning) "Stop" else "Start",
                                tint = actionColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                Divider(color = NeonCyan.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
fun CommandsCard(
    commands: Map<String, List<EmergencyCommand>>,
    onCommandClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, spotColor = NeonMagenta.copy(alpha = 0.4f), shape = RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("ACTION CENTER", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(modifier = Modifier.height(12.dp))

            if (commands.isEmpty()) {
                Text("Fetching emergency protocols...", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }

            commands.forEach { (category, cmdList) ->
                Text(category.uppercase(), style = MaterialTheme.typography.labelSmall, color = NeonMagenta.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(8.dp))
                
                cmdList.forEach { command ->
                    ElevatedButton(
                        onClick = { onCommandClick(command.id) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = DarkSurface,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonMagenta.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(command.id, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text(command.description, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            }
                            Text("EXEC", color = NeonMagenta, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

// ==================== Widget de Saúde do Servidor ====================

/**
 * Widget visual que mostra o status de saúde do servidor com cores
 * Azul = Saudável
 * Verde = Aviso (uso moderado)
 * Amarelo = Alerta
 * Vermelho = Crítico
 */
@Composable
fun ServerHealthWidget(
    serverName: String,
    status: HealthStatus,
    cpuUsage: Float,
    memoryUsage: Float,
    diskUsage: Float,
    activeAlerts: Int = 0,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val (backgroundColor, borderColor, statusColor, statusLabel) = when (status) {
        HealthStatus.HEALTHY -> {
            // Azul
            Tuple4(
                Color(0xFF1a3a52).copy(alpha = 0.6f),
                Color(0xFF4DB8FF),
                Color(0xFF4DB8FF),
                "OK"
            )
        }
        HealthStatus.WARNING -> {
            // Verde
            Tuple4(
                Color(0xFF1a4d2e).copy(alpha = 0.6f),
                Color(0xFF66BB6A),
                Color(0xFF66BB6A),
                "AVISO"
            )
        }
        HealthStatus.ALERT -> {
            // Amarelo
            Tuple4(
                Color(0xFF6b5b1a).copy(alpha = 0.6f),
                Color(0xFFFDD835),
                Color(0xFFFDD835),
                "ALERTA"
            )
        }
        HealthStatus.CRITICAL -> {
            // Vermelho
            Tuple4(
                Color(0xFF4d1a1a).copy(alpha = 0.6f),
                Color(0xFFEF5350),
                Color(0xFFEF5350),
                "CRÍTICO"
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        // Header com nome do servidor e status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = serverName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            // Status Badge
            Row(
                modifier = Modifier
                    .background(statusColor.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                    .border(1.dp, statusColor, RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(statusColor, shape = RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Métricas em progresso horizontal
        MetricRow(
            label = "CPU",
            value = cpuUsage,
            icon = "⚡",
            color = statusColor
        )

        Spacer(modifier = Modifier.height(8.dp))

        MetricRow(
            label = "RAM",
            value = memoryUsage,
            icon = "🧠",
            color = statusColor
        )

        Spacer(modifier = Modifier.height(8.dp))

        MetricRow(
            label = "DISCO",
            value = diskUsage,
            icon = "💾",
            color = statusColor
        )

        // Alertas ativos (se houver)
        if (activeAlerts > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFEF5350).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFEF5350).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🚨", modifier = Modifier.padding(end = 6.dp))
                Text(
                    text = "$activeAlerts alerta${if (activeAlerts > 1) "s" else ""} ativo${if (activeAlerts > 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFEF5350)
                )
            }
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: Float,
    icon: String,
    color: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, modifier = Modifier.padding(end = 6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            Text(
                text = String.format("%.1f%%", value),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        LinearProgressIndicator(
            progress = (value / 100f).coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = Color.White.copy(alpha = 0.1f)
        )
    }
}

// Data class auxiliar para retornar múltiplos valores
private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

