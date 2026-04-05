package com.pocketnoc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketnoc.data.models.PhpFpmPool
import com.pocketnoc.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhpFpmScreen(
    pools: List<PhpFpmPool>,
    totalWorkers: Int,
    totalCpu: Float,
    totalMemory: Float,
    serverName: String,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(horizontal = Dimens.SpaceXl, vertical = Dimens.SpaceMd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(Dimens.TopBarButton).clip(AppShapes.large)
                            .background(ext.cyan.copy(alpha = 0.10f))
                            .border(Dimens.BorderThin, ext.cyan.copy(alpha = 0.35f), AppShapes.large)
                            .clickable(onClick = onNavigateBack),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = ext.cyan, modifier = Modifier.size(Dimens.IconMd)) }

                    Spacer(Modifier.width(Dimens.SpaceLg))

                    Column(Modifier.weight(1f)) {
                        Text("PHP-FPM POOLS", color = ext.cyan, fontWeight = FontWeight.Black, fontSize = 20.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                        Text(serverName, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }

                    Box(
                        Modifier.size(Dimens.TopBarButton).clip(AppShapes.large)
                            .background(colors.primary.copy(alpha = 0.10f))
                            .border(Dimens.BorderThin, colors.primary.copy(alpha = 0.35f), AppShapes.large)
                            .clickable(onClick = onRefresh),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Refresh, null, tint = colors.primary, modifier = Modifier.size(Dimens.IconMd)) }
                }
                Box(Modifier.fillMaxWidth().height(Dimens.BorderThin).background(ext.cyan.copy(alpha = 0.3f)))
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues).background(colors.background)) {
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ext.cyan)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
                    contentPadding = PaddingValues(vertical = Dimens.SpaceXl)
                ) {
                    // Resumo geral
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                            SummaryCard("WORKERS", "$totalWorkers", ext.cyan, Modifier.weight(1f), colors)
                            SummaryCard("CPU", "${totalCpu.toInt()}%", if (totalCpu > 200) StatusColors.critical else if (totalCpu > 100) StatusColors.warning else StatusColors.success, Modifier.weight(1f), colors)
                            SummaryCard("RAM", "${totalMemory.toInt()} MB", ext.blue, Modifier.weight(1f), colors)
                        }
                    }

                    item {
                        Spacer(Modifier.height(Dimens.SpaceMd))
                        Text("SITES POR CONSUMO DE CPU", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                    }

                    // Lista de pools
                    itemsIndexed(pools) { idx, pool ->
                        PoolRow(pool, idx, colors, ext)
                    }

                    if (pools.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = Dimens.Space4xl), contentAlignment = Alignment.Center) {
                                Text("Nenhum pool PHP-FPM ativo", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, color: Color, modifier: Modifier, colors: ColorScheme) {
    Box(
        modifier.clip(AppShapes.card).background(colors.surfaceVariant)
            .border(Dimens.BorderThin, color.copy(alpha = 0.4f), AppShapes.card)
            .padding(Dimens.SpaceLg),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black, color = color, fontFamily = FontFamily.Monospace)
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun PoolRow(pool: PhpFpmPool, idx: Int, colors: ColorScheme, ext: ExtendedColors) {
    val cpuColor = when {
        pool.cpuPercent > 50 -> StatusColors.critical
        pool.cpuPercent > 20 -> StatusColors.warning
        pool.cpuPercent > 5  -> ext.cyan
        else                 -> colors.onSurfaceVariant
    }

    Row(
        Modifier.fillMaxWidth()
            .clip(AppShapes.large)
            .background(colors.surfaceVariant.copy(alpha = if (idx % 2 == 0) 1f else 0.7f))
            .padding(horizontal = Dimens.SpaceLg, vertical = Dimens.SpaceMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Nome do pool/site
        Column(Modifier.weight(1f)) {
            Text(
                pool.poolName,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${pool.workerCount} worker${if (pool.workerCount > 1) "s" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }

        // CPU
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(70.dp)) {
            Text("${pool.cpuPercent.toInt()}%", style = MaterialTheme.typography.bodyMedium, color = cpuColor, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            LinearProgressIndicator(
                progress = { (pool.cpuPercent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(Dimens.ProgressSm).clip(AppShapes.pill),
                color = cpuColor,
                trackColor = colors.surface
            )
        }

        Spacer(Modifier.width(Dimens.SpaceLg))

        // RAM
        Text("${pool.memoryMb.toInt()}MB", style = MaterialTheme.typography.bodySmall, color = ext.blue, fontFamily = FontFamily.Monospace, modifier = Modifier.width(55.dp))
    }
}
