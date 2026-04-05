package com.pocketnoc.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocketnoc.data.models.ServiceInfo
import com.pocketnoc.data.models.ServiceStatus
import com.pocketnoc.ui.theme.*

// ========== COMPONENTES FUTURISTICOS ESPECIALIZADOS ==========

@Composable
fun FuturisticResourceCard(
    title: String,
    percentage: Int,
    color: Color,
    icon: String
) {
    val pct = (percentage / 100f).coerceIn(0f, 1f)

    // Cor dinâmica: verde → amarelo → vermelho conforme carga
    val dynamicColor = when {
        percentage > 80 -> CriticalRedHealth
        percentage > 60 -> AlertYellow
        else            -> color
    }

    val infiniteTransition = rememberInfiniteTransition(label = "res_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.35f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOutSine), RepeatMode.Reverse),
        label         = "res_glow_alpha"
    )
    val animPct by animateFloatAsState(
        targetValue   = pct,
        animationSpec = tween(1400, easing = FastOutSlowInEasing),
        label         = "pct_anim"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(DarkCard)
            .border(
                1.5.dp,
                dynamicColor.copy(alpha = glowAlpha * 0.85f),
                RoundedCornerShape(20.dp)
            )
    ) {
        Row(
            modifier           = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp),
            verticalAlignment  = Alignment.CenterVertically
        ) {
            // ─── Esquerda: rótulo + número gigante ───────────────────────
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment       = Alignment.CenterVertically,
                    horizontalArrangement   = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(dynamicColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .border(1.dp, dynamicColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(icon, fontSize = androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp))
                    }
                    Text(
                        text       = title,
                        color      = dynamicColor,
                        fontWeight = FontWeight.Bold,
                        style      = MaterialTheme.typography.labelMedium,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Número principal
                androidx.compose.foundation.layout.Box {
                    Text(
                        text       = "$percentage",
                        fontSize   = androidx.compose.ui.unit.TextUnit(64f, androidx.compose.ui.unit.TextUnitType.Sp),
                        fontWeight = FontWeight.Black,
                        color      = Color.White,
                        lineHeight = androidx.compose.ui.unit.TextUnit(64f, androidx.compose.ui.unit.TextUnitType.Sp),
                        letterSpacing = androidx.compose.ui.unit.TextUnit(-2f, androidx.compose.ui.unit.TextUnitType.Sp)
                    )
                }

                Text(
                    text          = "PERCENT",
                    color         = dynamicColor.copy(alpha = 0.65f),
                    style         = MaterialTheme.typography.labelSmall,
                    fontFamily    = androidx.compose.ui.text.font.FontFamily.Monospace,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(3f, androidx.compose.ui.unit.TextUnitType.Sp)
                )
            }

            // ─── Direita: arco grande ────────────────────────────────────
            Box(modifier = Modifier.size(148.dp), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = 16.dp.toPx()
                    val inset    = strokePx / 2
                    val arcSize  = androidx.compose.ui.geometry.Size(size.width - strokePx, size.height - strokePx)
                    val offset   = androidx.compose.ui.geometry.Offset(inset, inset)
                    val sweep    = 240f * animPct

                    // Halo radial atrás do arco
                    drawCircle(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            listOf(dynamicColor.copy(alpha = 0.08f), Color.Transparent)
                        ),
                        radius = size.minDimension / 2
                    )
                    // Track
                    drawArc(
                        color      = DarkSurface,
                        startAngle = 150f, sweepAngle = 240f, useCenter = false,
                        style      = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePx, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                        topLeft    = offset, size = arcSize
                    )
                    // Fill com sweep gradient
                    if (sweep > 0f) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                0.0f to dynamicColor.copy(alpha = 0.4f),
                                0.67f to dynamicColor
                            ),
                            startAngle = 150f, sweepAngle = sweep, useCenter = false,
                            style   = Stroke(width = strokePx, cap = StrokeCap.Round),
                            topLeft = offset, size = arcSize
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text       = "$percentage",
                        fontSize   = androidx.compose.ui.unit.TextUnit(26f, androidx.compose.ui.unit.TextUnitType.Sp),
                        fontWeight = FontWeight.Black,
                        color      = dynamicColor
                    )
                    Text(
                        text  = "%",
                        style = MaterialTheme.typography.labelSmall,
                        color = dynamicColor.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // ─── Barra de progresso na base ──────────────────────────────────
        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Box(
                modifier = Modifier.fillMaxWidth().height(4.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize().background(DarkSurface))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animPct)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(listOf(dynamicColor.copy(0.5f), dynamicColor))
                        )
                )
            }
        }
    }
}

@Composable
fun MetricCardFuturistic(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.spacedBy(6.dp)
        ) {
            Spacer(Modifier.height(2.dp))
            Text(
                text          = title,
                style         = MaterialTheme.typography.labelSmall,
                color         = color.copy(alpha = 0.8f),
                fontFamily    = androidx.compose.ui.text.font.FontFamily.Monospace,
                letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp)
            )
            Text(
                text          = value,
                fontSize      = androidx.compose.ui.unit.TextUnit(28f, androidx.compose.ui.unit.TextUnitType.Sp),
                fontWeight    = FontWeight.Black,
                color         = Color.White,
                letterSpacing = androidx.compose.ui.unit.TextUnit(-0.5f, androidx.compose.ui.unit.TextUnitType.Sp)
            )
        }
    }
}

@Composable
fun SecurityStatusCard(
    security: com.pocketnoc.data.models.SecurityMetrics,
    onBlockIp: (String) -> Unit = {}
) {
    val hasThreat   = security.failedLoginAttempts > 10
    val accentColor = if (hasThreat) CriticalRedHealth else HealthyGreen

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(DarkCard)
            .border(1.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Cabeçalho
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                            .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        "SENTINEL SECURITY",
                        color      = accentColor,
                        fontWeight = FontWeight.Bold,
                        style      = MaterialTheme.typography.labelMedium,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                if (security.failedLoginAttempts > 0) {
                    Box(
                        modifier = Modifier
                            .background(CriticalRedHealth.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                            .border(1.dp, CriticalRedHealth.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "${security.failedLoginAttempts} THREATS",
                            color      = CriticalRedHealth,
                            style      = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // SSH sessions row
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurface.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val sshColor = if (security.activeSshSessions > 0) WarningOrange else HealthyGreen
                    Box(modifier = Modifier.size(8.dp).background(sshColor, androidx.compose.foundation.shape.CircleShape))
                    Text("SSH ACTIVE", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
                Text(
                    "${security.activeSshSessions}",
                    fontSize   = androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp),
                    fontWeight = FontWeight.Black,
                    color      = if (security.activeSshSessions > 0) WarningOrange else HealthyGreen
                )
            }

            if (security.failedLogins.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Brush.horizontalGradient(listOf(Color.Transparent, CriticalRedHealth.copy(alpha = 0.3f), Color.Transparent)))
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "FAILED LOGINS BY IP",
                    style      = MaterialTheme.typography.labelSmall,
                    color      = CriticalRedHealth.copy(alpha = 0.7f),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(1f, androidx.compose.ui.unit.TextUnitType.Sp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                security.failedLogins.take(5).forEach { login ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CriticalRedHealth.copy(alpha = 0.06f))
                            .border(1.dp, CriticalRedHealth.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                login.ip,
                                style      = MaterialTheme.typography.bodyMedium,
                                color      = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                            Text(
                                "${login.count}x · ${login.lastAttempt}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                        Button(
                            onClick        = { onBlockIp(login.ip) },
                            colors         = ButtonDefaults.buttonColors(containerColor = CriticalRedHealth.copy(alpha = 0.15f)),
                            modifier       = Modifier.height(30.dp).border(1.dp, CriticalRedHealth.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            shape          = RoundedCornerShape(8.dp)
                        ) {
                            Text("BAN", style = MaterialTheme.typography.labelSmall, color = CriticalRedHealth, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProcessesCard(processes: com.pocketnoc.data.models.ProcessMetrics) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(DarkCard)
            .border(1.dp, NeonMagenta.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(NeonMagenta.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .border(1.dp, NeonMagenta.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = NeonMagenta, modifier = Modifier.size(18.dp))
                }
                Text(
                    "TOP PROCESSES",
                    color      = NeonMagenta,
                    fontWeight = FontWeight.Bold,
                    style      = MaterialTheme.typography.labelMedium,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            processes.topProcesses.take(5).forEachIndexed { idx, process ->
                val cpuColor = when {
                    process.cpuUsage > 50 -> CriticalRedHealth
                    process.cpuUsage > 20 -> AlertYellow
                    else                  -> NeonMagenta
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurface.copy(alpha = if (idx % 2 == 0) 0.6f else 0.3f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${idx + 1}",
                            style      = MaterialTheme.typography.labelSmall,
                            color      = NeonMagenta.copy(alpha = 0.5f),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                        Text(
                            text      = process.name,
                            style     = MaterialTheme.typography.bodySmall,
                            color     = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines  = 1,
                            overflow  = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "${process.cpuUsage.toInt()}%",
                            style      = MaterialTheme.typography.labelSmall,
                            color      = cpuColor,
                            fontWeight = FontWeight.Black,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                        Text(
                            "${process.memoryMb}MB",
                            style      = MaterialTheme.typography.labelSmall,
                            color      = NeonBlue.copy(alpha = 0.8f),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(DarkCard)
            .border(1.dp, NeonCyan.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // ── Cabeçalho ──────────────────────────────────────────────────
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(NeonCyan.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .border(1.dp, NeonCyan.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Dns, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                }
                Column {
                    Text(
                        "SERVICE MANAGER",
                        color      = NeonCyan,
                        fontWeight = FontWeight.Bold,
                        style      = MaterialTheme.typography.labelMedium,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                    Text(
                        "${services.size} service${if (services.size != 1) "s" else ""} monitorado${if (services.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── Separador neon ──────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, NeonCyan.copy(alpha = 0.4f), Color.Transparent)))
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (services.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurface.copy(alpha = 0.5f))
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Nenhum serviço crítico detectado", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }

            // ── Linhas de serviço ───────────────────────────────────────────
            services.forEachIndexed { idx, service ->
                val isRunning   = service.status == ServiceStatus.ACTIVE
                val statusColor = if (isRunning) HealthyGreen else CriticalRedHealth
                val actionColor = if (isRunning) CriticalRedHealth else HealthyGreen

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurface.copy(alpha = if (idx % 2 == 0) 0.6f else 0.3f))
                        .border(1.dp, statusColor.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Info
                    Row(
                        modifier              = Modifier.weight(1f),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(statusColor, androidx.compose.foundation.shape.CircleShape)
                        )
                        Column {
                            Text(
                                service.name,
                                style      = MaterialTheme.typography.bodySmall,
                                color      = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                maxLines   = 1,
                                overflow   = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                service.status.name.lowercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor.copy(alpha = 0.85f)
                            )
                        }
                    }

                    // Botões
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(NeonCyan.copy(alpha = 0.10f))
                                .border(1.dp, NeonCyan.copy(alpha = 0.50f), RoundedCornerShape(8.dp))
                                .clickable { onActionClick(service.name, "restart") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Restart", tint = NeonCyan, modifier = Modifier.size(16.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(actionColor.copy(alpha = 0.10f))
                                .border(1.dp, actionColor.copy(alpha = 0.50f), RoundedCornerShape(8.dp))
                                .clickable { onActionClick(service.name, if (isRunning) "stop" else "start") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector        = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (isRunning) "Stop" else "Start",
                                tint               = actionColor,
                                modifier           = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== SHIMMER LOADING ====================

/**
 * Caixa com efeito shimmer para estados de carregamento.
 * Substitui CircularProgressIndicator com skeleton cards futuristas.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp)
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -600f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )
    val brush = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(
            DarkCard,
            Color(0xFF1C2333).copy(alpha = 0.9f),
            Color(0xFF242B3D).copy(alpha = 0.7f),
            Color(0xFF1C2333).copy(alpha = 0.9f),
            DarkCard,
        ),
        start = androidx.compose.ui.geometry.Offset(translateAnim - 300f, 0f),
        end = androidx.compose.ui.geometry.Offset(translateAnim + 300f, 0f)
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
    )
}

/**
 * Skeleton completo da ServerDetailsScreen enquanto carrega.
 */
@Composable
fun ServerDetailsShimmer() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status card skeleton
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(100.dp))
        // 3 gauge skeletons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(3) {
                ShimmerBox(modifier = Modifier.weight(1f).height(120.dp), shape = RoundedCornerShape(16.dp))
            }
        }
        // Chart skeleton
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(180.dp))
        // Disk skeleton
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(90.dp))
        // Two metric cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(2) {
                ShimmerBox(modifier = Modifier.weight(1f).height(72.dp))
            }
        }
        // Process list skeleton
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(160.dp))
    }
}

// ==================== ARC GAUGE ====================

/**
 * Medidor em arco estilo dashboard futurista.
 * Desenha um arco de 240° com faixa de cor degradê.
 */
@Composable
fun ArcGauge(
    label: String,
    value: Float,           // 0..100
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .border(1.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier.size(96.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 10.dp.toPx()
                val inset = stroke / 2
                val sweep = 240f * (value / 100f).coerceIn(0f, 1f)

                // Track
                drawArc(
                    color = DarkSurface,
                    startAngle = 150f,
                    sweepAngle = 240f,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = stroke,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                )
                // Fill
                if (sweep > 0f) {
                    drawArc(
                        brush = androidx.compose.ui.graphics.Brush.sweepGradient(
                            colorStops = arrayOf(
                                0.0f to color.copy(alpha = 0.5f),
                                1.0f to color
                            )
                        ),
                        startAngle = 150f,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = stroke,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        ),
                        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${value.toInt()}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "%",
                    style = MaterialTheme.typography.labelSmall,
                    color = color.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ==================== LINE CHART ====================

/**
 * Gráfico de linha histórico para CPU e RAM.
 * Dados: lista de pontos ordenados do mais antigo (esquerda) ao mais recente (direita).
 */
@Composable
fun TelemetryLineChart(
    cpuPoints: List<Float>,
    ramPoints: List<Float>,
    modifier: Modifier = Modifier
) {
    if (cpuPoints.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DarkCard)
                .border(1.dp, NeonCyan.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Acumulando histórico...",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .border(1.dp, NeonCyan.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("HISTÓRICO", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(NeonMagenta, RoundedCornerShape(2.dp)))
                    Text("CPU", style = MaterialTheme.typography.labelSmall, color = NeonMagenta)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(NeonBlue, RoundedCornerShape(2.dp)))
                    Text("RAM", style = MaterialTheme.typography.labelSmall, color = NeonBlue)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            val w = size.width
            val h = size.height
            val count = maxOf(cpuPoints.size, ramPoints.size).coerceAtLeast(2)

            // Grid lines horizontais (0%, 50%, 100%)
            val gridColor = Color.White.copy(alpha = 0.06f)
            listOf(0f, 0.5f, 1f).forEach { frac ->
                val y = h * (1f - frac)
                drawLine(gridColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(w, y), strokeWidth = 1.dp.toPx())
            }

            fun List<Float>.toPath(): androidx.compose.ui.graphics.Path {
                val path = androidx.compose.ui.graphics.Path()
                forEachIndexed { i, v ->
                    val x = if (count <= 1) w / 2f else i.toFloat() / (count - 1) * w
                    val y = h * (1f - (v / 100f).coerceIn(0f, 1f))
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                return path
            }

            fun List<Float>.toFillPath(): androidx.compose.ui.graphics.Path {
                val path = toPath()
                val lastX = if (count <= 1) w / 2f else (size - 1).toFloat() / (count - 1) * w
                path.lineTo(lastX, h)
                path.lineTo(0f, h)
                path.close()
                return path
            }

            // Fill RAM
            if (ramPoints.isNotEmpty()) {
                drawPath(
                    ramPoints.toFillPath(),
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(NeonBlue.copy(alpha = 0.25f), Color.Transparent)
                    )
                )
                drawPath(
                    ramPoints.toPath(),
                    color = NeonBlue,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )
            }

            // Fill CPU
            if (cpuPoints.isNotEmpty()) {
                drawPath(
                    cpuPoints.toFillPath(),
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(NeonMagenta.copy(alpha = 0.25f), Color.Transparent)
                    )
                )
                drawPath(
                    cpuPoints.toPath(),
                    color = NeonMagenta,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )
                // Ponto atual (mais recente)
                val lastCpu = cpuPoints.last()
                val lastX = if (cpuPoints.size <= 1) w / 2f else (cpuPoints.size - 1).toFloat() / (count - 1) * w
                val lastY = h * (1f - (lastCpu / 100f).coerceIn(0f, 1f))
                drawCircle(NeonMagenta, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(lastX, lastY))
                drawCircle(Color.White, radius = 2.dp.toPx(), center = androidx.compose.ui.geometry.Offset(lastX, lastY))
            }
        }

        // Eixo de tempo
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${(cpuPoints.size * 30 / 60).coerceAtLeast(1)}min atrás",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Text("agora", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}

