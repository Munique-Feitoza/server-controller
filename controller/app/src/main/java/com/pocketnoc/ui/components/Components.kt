package com.pocketnoc.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketnoc.data.models.ProcessMetrics
import com.pocketnoc.data.models.SecurityMetrics
import com.pocketnoc.data.models.ServiceInfo
import com.pocketnoc.data.models.ServiceStatus
import com.pocketnoc.ui.theme.*

// ═══════════════════════════════════════════════════════════════════
// COMPONENTES REUTILIZÁVEIS — Design System PocketNOC
// Todas as cores via MaterialTheme.colorScheme / LocalExtendedColors.
// Todas as dimensoes via Dimens / AppShapes.
// ═══════════════════════════════════════════════════════════════════

@Composable
fun FuturisticResourceCard(title: String, percentage: Int, color: Color, icon: String) {
    val colors = MaterialTheme.colorScheme
    val pct = (percentage / 100f).coerceIn(0f, 1f)
    val dynamicColor = when {
        percentage > 80 -> StatusColors.critical
        percentage > 60 -> StatusColors.warning
        else            -> color
    }

    val infiniteTransition = rememberInfiniteTransition(label = "res_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "res_glow_alpha"
    )
    val animPct by animateFloatAsState(pct, tween(1400, easing = FastOutSlowInEasing), label = "pct_anim")

    Box(
        modifier = Modifier.fillMaxWidth()
            .shadow(6.dp, AppShapes.sheet).clip(AppShapes.sheet)
            .background(colors.surfaceVariant)
            .border(Dimens.BorderMedium, dynamicColor.copy(alpha = glowAlpha * 0.85f), AppShapes.sheet)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceXxl, vertical = Dimens.SpaceXxl), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                    Box(
                        Modifier.size(30.dp).background(dynamicColor.copy(alpha = 0.15f), AppShapes.medium)
                            .border(Dimens.BorderThin, dynamicColor.copy(alpha = 0.45f), AppShapes.medium),
                        contentAlignment = Alignment.Center
                    ) { Text(icon, fontSize = 14.sp) }
                    Text(title, color = dynamicColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(Dimens.SpaceLg))
                Text("$percentage", fontSize = 64.sp, fontWeight = FontWeight.Black, color = colors.onSurface, lineHeight = 64.sp, letterSpacing = (-2).sp)
                Text("PERCENT", color = dynamicColor.copy(alpha = 0.65f), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, letterSpacing = 3.sp)
            }

            Box(Modifier.size(148.dp), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val strokePx = 16.dp.toPx()
                    val inset = strokePx / 2
                    val arcSize = Size(size.width - strokePx, size.height - strokePx)
                    val offset = Offset(inset, inset)
                    val sweep = 240f * animPct

                    drawCircle(brush = Brush.radialGradient(listOf(dynamicColor.copy(alpha = 0.08f), Color.Transparent)), radius = size.minDimension / 2)
                    drawArc(color = colors.surface, startAngle = 150f, sweepAngle = 240f, useCenter = false, style = Stroke(strokePx, cap = StrokeCap.Round), topLeft = offset, size = arcSize)
                    if (sweep > 0f) {
                        drawArc(brush = Brush.sweepGradient(0.0f to dynamicColor.copy(alpha = 0.4f), 0.67f to dynamicColor), startAngle = 150f, sweepAngle = sweep, useCenter = false, style = Stroke(strokePx, cap = StrokeCap.Round), topLeft = offset, size = arcSize)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$percentage", fontSize = 26.sp, fontWeight = FontWeight.Black, color = dynamicColor)
                    Text("%", style = MaterialTheme.typography.labelSmall, color = dynamicColor.copy(alpha = 0.7f))
                }
            }
        }

        Column(Modifier.align(Alignment.BottomStart)) {
            Box(Modifier.fillMaxWidth().height(Dimens.ProgressMd)) {
                Box(Modifier.fillMaxSize().background(colors.surface))
                Box(Modifier.fillMaxWidth(animPct).fillMaxHeight().background(Brush.horizontalGradient(listOf(dynamicColor.copy(0.5f), dynamicColor))))
            }
        }
    }
}

@Composable
fun MetricCardFuturistic(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier.shadow(4.dp, AppShapes.panel).clip(AppShapes.panel)
            .background(colors.surfaceVariant)
            .border(Dimens.BorderThin, color.copy(alpha = 0.55f), AppShapes.panel)
    ) {
        Column(Modifier.fillMaxWidth().padding(Dimens.SpaceLg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
            Spacer(Modifier.height(Dimens.SpaceXxs))
            Text(title, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.8f), fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp)
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Black, color = colors.onSurface, letterSpacing = (-0.5).sp)
        }
    }
}

@Composable
fun SecurityStatusCard(security: SecurityMetrics, onBlockIp: (String) -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    val hasThreat = security.failedLoginAttempts > 10
    val accentColor = if (hasThreat) StatusColors.critical else StatusColors.success

    Box(
        Modifier.fillMaxWidth().shadow(6.dp, AppShapes.sheet).clip(AppShapes.sheet)
            .background(colors.surfaceVariant)
            .border(Dimens.BorderThin, accentColor.copy(alpha = 0.55f), AppShapes.sheet)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd), verticalAlignment = Alignment.CenterVertically) {
                    CardIcon(accentColor, Icons.Default.Security)
                    Text("SENTINEL SECURITY", color = accentColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                }
                if (security.failedLoginAttempts > 0) {
                    Badge(StatusColors.critical, "${security.failedLoginAttempts} THREATS")
                }
            }

            Spacer(Modifier.height(Dimens.SpaceLg))

            Row(
                Modifier.fillMaxWidth().clip(AppShapes.large).background(colors.surface.copy(alpha = 0.5f))
                    .padding(horizontal = Dimens.SpaceLg, vertical = Dimens.SpaceMd),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd), verticalAlignment = Alignment.CenterVertically) {
                    val sshColor = if (security.activeSshSessions > 0) StatusColors.warning else StatusColors.success
                    Box(Modifier.size(Dimens.StatusDot).background(sshColor, CircleShape))
                    Text("SSH ACTIVE", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                }
                Text("${security.activeSshSessions}", fontSize = 20.sp, fontWeight = FontWeight.Black, color = if (security.activeSshSessions > 0) StatusColors.warning else StatusColors.success)
            }

            if (security.failedLogins.isNotEmpty()) {
                Spacer(Modifier.height(Dimens.SpaceLg))
                Box(Modifier.fillMaxWidth().height(Dimens.BorderThin).background(Brush.horizontalGradient(listOf(Color.Transparent, StatusColors.critical.copy(alpha = 0.3f), Color.Transparent))))
                Spacer(Modifier.height(Dimens.SpaceLg))
                Text("FAILED LOGINS BY IP", style = MaterialTheme.typography.labelSmall, color = StatusColors.critical.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                Spacer(Modifier.height(Dimens.SpaceMd))

                security.failedLogins.take(5).forEach { login ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = Dimens.SpaceXs)
                            .clip(AppShapes.large).background(StatusColors.critical.copy(alpha = 0.06f))
                            .border(Dimens.BorderThin, StatusColors.critical.copy(alpha = 0.2f), AppShapes.large)
                            .padding(horizontal = Dimens.SpaceLg, vertical = Dimens.SpaceMd),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(login.ip, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text("${login.count}x · ${login.lastAttempt}", style = MaterialTheme.typography.labelSmall, color = colors.outlineVariant)
                        }
                        Button(
                            onClick = { onBlockIp(login.ip) },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusColors.critical.copy(alpha = 0.15f)),
                            modifier = Modifier.height(30.dp).border(Dimens.BorderThin, StatusColors.critical.copy(alpha = 0.6f), AppShapes.medium),
                            contentPadding = PaddingValues(horizontal = Dimens.SpaceLg), shape = AppShapes.medium
                        ) { Text("BAN", style = MaterialTheme.typography.labelSmall, color = StatusColors.critical, fontWeight = FontWeight.Black) }
                    }
                }
            }
        }
    }
}

@Composable
fun ProcessesCard(processes: ProcessMetrics) {
    val colors = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current

    Box(
        Modifier.fillMaxWidth().shadow(6.dp, AppShapes.sheet).clip(AppShapes.sheet)
            .background(colors.surfaceVariant)
            .border(Dimens.BorderThin, ext.magenta.copy(alpha = 0.5f), AppShapes.sheet)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                CardIcon(ext.magenta, Icons.AutoMirrored.Filled.List)
                Text("TOP PROCESSES", color = ext.magenta, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(Dimens.SpaceLg))

            processes.topProcesses.take(5).forEachIndexed { idx, process ->
                val cpuColor = when { process.cpuUsage > 50 -> StatusColors.critical; process.cpuUsage > 20 -> StatusColors.warning; else -> ext.magenta }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Dimens.SpaceXxs)
                        .clip(AppShapes.large).background(colors.surface.copy(alpha = if (idx % 2 == 0) 0.6f else 0.3f))
                        .padding(horizontal = Dimens.SpaceLg, vertical = Dimens.SpaceMd),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd), verticalAlignment = Alignment.CenterVertically) {
                        Text("${idx + 1}", style = MaterialTheme.typography.labelSmall, color = ext.magenta.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace)
                        Text(process.name, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                        Text("${process.cpuUsage.toInt()}%", style = MaterialTheme.typography.labelSmall, color = cpuColor, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                        Text("${process.memoryMb}MB", style = MaterialTheme.typography.labelSmall, color = ext.blue.copy(alpha = 0.8f), fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
fun ServicesCard(services: List<ServiceInfo>, onActionClick: (String, String) -> Unit) {
    val colors = MaterialTheme.colorScheme

    Box(
        Modifier.fillMaxWidth().shadow(6.dp, AppShapes.sheet).clip(AppShapes.sheet)
            .background(colors.surfaceVariant)
            .border(Dimens.BorderThin, colors.primary.copy(alpha = 0.45f), AppShapes.sheet)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                CardIcon(colors.primary, Icons.Default.Dns)
                Column {
                    Text("SERVICE MANAGER", color = colors.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                    Text("${services.size} serviço${if (services.size != 1) "s" else ""}", style = MaterialTheme.typography.labelSmall, color = colors.outlineVariant)
                }
            }
            Spacer(Modifier.height(Dimens.SpaceLg))
            Box(Modifier.fillMaxWidth().height(Dimens.BorderThin).background(Brush.horizontalGradient(listOf(Color.Transparent, colors.primary.copy(alpha = 0.4f), Color.Transparent))))
            Spacer(Modifier.height(Dimens.SpaceLg))

            if (services.isEmpty()) {
                Box(Modifier.fillMaxWidth().clip(AppShapes.large).background(colors.surface.copy(alpha = 0.5f)).padding(vertical = Dimens.SpaceLg), contentAlignment = Alignment.Center) {
                    Text("Nenhum serviço detectado", color = colors.outlineVariant, style = MaterialTheme.typography.bodySmall)
                }
            }

            services.forEachIndexed { idx, service ->
                val isRunning = service.status == ServiceStatus.ACTIVE
                val statusColor = if (isRunning) StatusColors.success else StatusColors.critical
                val actionColor = if (isRunning) StatusColors.critical else StatusColors.success

                Row(
                    Modifier.fillMaxWidth().padding(vertical = Dimens.SpaceXxs)
                        .clip(AppShapes.xl).background(colors.surface.copy(alpha = if (idx % 2 == 0) 0.6f else 0.3f))
                        .border(Dimens.BorderThin, statusColor.copy(alpha = 0.18f), AppShapes.xl)
                        .padding(horizontal = Dimens.SpaceLg, vertical = Dimens.SpaceMd),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                        Box(Modifier.size(Dimens.StatusDot).background(statusColor, CircleShape))
                        Column {
                            Text(service.name, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(service.status.name.lowercase(), style = MaterialTheme.typography.labelSmall, color = statusColor.copy(alpha = 0.85f))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                        ServiceActionBtn(colors.primary, Icons.Default.Refresh, "Restart") { onActionClick(service.name, "restart") }
                        ServiceActionBtn(actionColor, if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow, if (isRunning) "Stop" else "Start") {
                            onActionClick(service.name, if (isRunning) "stop" else "start")
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// SHIMMER
// ═══════════════════════════════════════════════════════════════════

@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = AppShapes.xl) {
    val colors = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(-600f, 1200f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "shimmer_translate")
    val brush = Brush.linearGradient(
        colors = listOf(colors.surfaceVariant, colors.surfaceVariant.copy(alpha = 0.7f), colors.surface.copy(alpha = 0.5f), colors.surfaceVariant.copy(alpha = 0.7f), colors.surfaceVariant),
        start = Offset(translateAnim - 300f, 0f), end = Offset(translateAnim + 300f, 0f)
    )
    Box(modifier.clip(shape).background(brush))
}

@Composable
fun ServerDetailsShimmer() {
    Column(Modifier.fillMaxSize().padding(Dimens.SpaceXl), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXl)) {
        ShimmerBox(Modifier.fillMaxWidth().height(100.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceLg)) { repeat(3) { ShimmerBox(Modifier.weight(1f).height(120.dp), AppShapes.panel) } }
        ShimmerBox(Modifier.fillMaxWidth().height(180.dp))
        ShimmerBox(Modifier.fillMaxWidth().height(90.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceLg)) { repeat(2) { ShimmerBox(Modifier.weight(1f).height(72.dp)) } }
        ShimmerBox(Modifier.fillMaxWidth().height(160.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════
// ARC GAUGE
// ═══════════════════════════════════════════════════════════════════

@Composable
fun ArcGauge(label: String, value: Float, color: Color, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.clip(AppShapes.panel).background(colors.surfaceVariant)
            .border(Dimens.BorderMedium, color.copy(alpha = 0.4f), AppShapes.panel)
            .padding(Dimens.SpaceLg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(Dimens.SpaceMd))
        Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val stroke = 10.dp.toPx()
                val inset = stroke / 2
                val sweep = 240f * (value / 100f).coerceIn(0f, 1f)
                drawArc(color = colors.surface, startAngle = 150f, sweepAngle = 240f, useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round), topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke))
                if (sweep > 0f) drawArc(brush = Brush.sweepGradient(0.0f to color.copy(alpha = 0.5f), 1.0f to color), startAngle = 150f, sweepAngle = sweep, useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round), topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${value.toInt()}", style = MaterialTheme.typography.headlineMedium, color = color, fontWeight = FontWeight.Bold)
                Text("%", style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// LINE CHART
// ═══════════════════════════════════════════════════════════════════

@Composable
fun TelemetryLineChart(cpuPoints: List<Float>, ramPoints: List<Float>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current

    if (cpuPoints.isEmpty()) {
        Box(modifier.fillMaxWidth().height(160.dp).clip(AppShapes.xl).background(colors.surfaceVariant).border(Dimens.BorderThin, colors.primary.copy(alpha = 0.2f), AppShapes.xl), contentAlignment = Alignment.Center) {
            Text("Acumulando histórico...", style = MaterialTheme.typography.labelSmall, color = colors.outlineVariant)
        }
        return
    }

    Column(modifier.fillMaxWidth().clip(AppShapes.xl).background(colors.surfaceVariant).border(Dimens.BorderThin, colors.primary.copy(alpha = 0.25f), AppShapes.xl).padding(Dimens.SpaceXl)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("HISTÓRICO", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXl)) {
                ChartLegend("CPU", ext.magenta)
                ChartLegend("RAM", ext.blue)
            }
        }
        Spacer(Modifier.height(Dimens.SpaceLg))
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val w = size.width; val h = size.height
            val count = maxOf(cpuPoints.size, ramPoints.size).coerceAtLeast(2)
            val gridColor = Color.White.copy(alpha = 0.06f)
            listOf(0f, 0.5f, 1f).forEach { frac -> drawLine(gridColor, Offset(0f, h * (1f - frac)), Offset(w, h * (1f - frac)), 1.dp.toPx()) }

            fun List<Float>.toPath(): Path { val p = Path(); forEachIndexed { i, v -> val x = if (count <= 1) w / 2f else i.toFloat() / (count - 1) * w; val y = h * (1f - (v / 100f).coerceIn(0f, 1f)); if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }; return p }
            fun List<Float>.toFillPath(): Path { val p = toPath(); val lastX = if (count <= 1) w / 2f else (size - 1).toFloat() / (count - 1) * w; p.lineTo(lastX, h); p.lineTo(0f, h); p.close(); return p }

            if (ramPoints.isNotEmpty()) {
                drawPath(ramPoints.toFillPath(), Brush.verticalGradient(listOf(ext.blue.copy(alpha = 0.25f), Color.Transparent)))
                drawPath(ramPoints.toPath(), ext.blue, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (cpuPoints.isNotEmpty()) {
                drawPath(cpuPoints.toFillPath(), Brush.verticalGradient(listOf(ext.magenta.copy(alpha = 0.25f), Color.Transparent)))
                drawPath(cpuPoints.toPath(), ext.magenta, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                val lastX = if (cpuPoints.size <= 1) w / 2f else (cpuPoints.size - 1).toFloat() / (count - 1) * w
                val lastY = h * (1f - (cpuPoints.last() / 100f).coerceIn(0f, 1f))
                drawCircle(ext.magenta, 4.dp.toPx(), Offset(lastX, lastY))
                drawCircle(Color.White, 2.dp.toPx(), Offset(lastX, lastY))
            }
        }
        Spacer(Modifier.height(Dimens.SpaceXs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${(cpuPoints.size * 30 / 60).coerceAtLeast(1)}min atrás", style = MaterialTheme.typography.labelSmall, color = colors.outlineVariant)
            Text("agora", style = MaterialTheme.typography.labelSmall, color = colors.outlineVariant)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// HELPERS PRIVADOS
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun CardIcon(color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        Modifier.size(Dimens.ActionButton).background(color.copy(alpha = 0.15f), AppShapes.large)
            .border(Dimens.BorderThin, color.copy(alpha = 0.45f), AppShapes.large),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = color, modifier = Modifier.size(Dimens.IconMd)) }
}

@Composable
private fun Badge(color: Color, text: String) {
    Box(Modifier.background(color.copy(alpha = 0.15f), AppShapes.pill).border(Dimens.BorderThin, color.copy(alpha = 0.5f), AppShapes.pill).padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceXxs)) {
        Text(text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ServiceActionBtn(color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(Dimens.ActionButton).clip(AppShapes.medium)
            .background(color.copy(alpha = 0.10f))
            .border(Dimens.BorderThin, color.copy(alpha = 0.50f), AppShapes.medium)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = color, modifier = Modifier.size(Dimens.IconSm)) }
}

@Composable
private fun ChartLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
        Box(Modifier.size(Dimens.StatusDot).background(color, AppShapes.small))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
