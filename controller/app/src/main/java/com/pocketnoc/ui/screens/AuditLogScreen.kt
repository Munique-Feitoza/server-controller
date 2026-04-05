package com.pocketnoc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.pocketnoc.data.models.AuditEntry
import com.pocketnoc.ui.theme.*
import com.pocketnoc.ui.viewmodels.DashboardViewModel

@Composable
private fun methodColor(method: String): Color {
    val colors = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current
    return when (method.uppercase()) {
        "GET"    -> colors.primary
        "POST"   -> colors.tertiary
        "DELETE" -> StatusColors.critical
        "PUT"    -> StatusColors.warning
        "PATCH"  -> ext.magenta
        else     -> colors.onSurfaceVariant
    }
}

@Composable
private fun statusColor(code: Int): Color {
    val colors = MaterialTheme.colorScheme
    return when {
        code in 200..299 -> colors.tertiary
        code in 300..399 -> colors.primary
        code in 400..499 -> StatusColors.warning
        code >= 500      -> StatusColors.critical
        else             -> colors.outlineVariant
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(
    viewModel: DashboardViewModel,
    serverId: Int,
    onNavigateBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current

    val servers by viewModel.allServers.collectAsState()
    val server = servers.find { it.id == serverId }
    var auditEntries by remember { mutableStateOf<List<AuditEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(server) {
        server?.let {
            isLoading = true
            try {
                val response = viewModel.fetchAuditLogs(it)
                auditEntries = response
                errorMsg = null
            } catch (e: Exception) {
                errorMsg = e.message
            }
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.RadiusLg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(AppShapes.medium)
                            .background(ext.purple.copy(alpha = 0.10f))
                            .border(Dimens.BorderThin, ext.purple.copy(alpha = 0.35f), AppShapes.medium)
                            .clickable(onClick = onNavigateBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = ext.purple, modifier = Modifier.size(17.dp))
                    }
                    Spacer(modifier = Modifier.width(Dimens.SpaceLg))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "AUDIT LOG",
                            color = ext.purple,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 3.sp
                        )
                        Text(
                            server?.name ?: "Servidor",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.outlineVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(AppShapes.medium)
                            .background(colors.primary.copy(alpha = 0.10f))
                            .border(Dimens.BorderThin, colors.primary.copy(alpha = 0.35f), AppShapes.medium)
                            .clickable {
                                server?.let {
                                    isLoading = true
                                    // Sera re-disparado pelo LaunchedEffect
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar", tint = colors.primary, modifier = Modifier.size(17.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimens.BorderThin)
                        .background(colors.primary.copy(alpha = 0.3f))
                )
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(colors.background)
        ) {
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ext.purple)
                    }
                }
                errorMsg != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                            Text("ERR", color = StatusColors.critical, fontFamily = FontFamily.Monospace, fontSize = 24.sp)
                            Text(errorMsg ?: "", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                auditEntries.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLg)) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = ext.purple, modifier = Modifier.size(Dimens.Icon2xl + Dimens.SpaceMd))
                            Text("Nenhum registro de auditoria", style = MaterialTheme.typography.titleMedium, color = ext.purple)
                            Text("As requisi\u00E7\u00F5es ser\u00E3o registradas automaticamente.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.ScreenPadding),
                        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
                        contentPadding = PaddingValues(vertical = Dimens.RadiusCard)
                    ) {
                        item {
                            Text(
                                "${auditEntries.size} registros",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.outlineVariant,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        items(auditEntries) { entry ->
                            AuditEntryCard(entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditEntryCard(entry: AuditEntry) {
    val colors = MaterialTheme.colorScheme
    val mColor = methodColor(entry.method)
    val sColor = statusColor(entry.statusCode)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.large)
            .background(colors.surfaceVariant)
            .border(Dimens.BorderThin, mColor.copy(alpha = 0.2f), AppShapes.large)
            .padding(Dimens.SpaceLg),
        verticalAlignment = Alignment.Top
    ) {
        // Badge do metodo HTTP
        Box(
            modifier = Modifier
                .width(Dimens.Icon2xl + Dimens.SpaceMd)
                .background(mColor.copy(alpha = 0.12f), AppShapes.small)
                .border(Dimens.BorderThin, mColor.copy(alpha = 0.4f), AppShapes.small)
                .padding(vertical = Dimens.SpaceXs),
            contentAlignment = Alignment.Center
        ) {
            Text(entry.method, style = MaterialTheme.typography.labelSmall, color = mColor, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        }

        Spacer(Modifier.width(Dimens.RadiusLg))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    entry.endpoint,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurface,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .background(sColor.copy(alpha = 0.15f), AppShapes.xl)
                        .padding(horizontal = Dimens.SpaceSm, vertical = Dimens.SpaceXxs)
                ) {
                    Text("${entry.statusCode}", style = MaterialTheme.typography.labelSmall, color = sColor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }

            Spacer(Modifier.height(Dimens.SpaceXs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(entry.sourceIp, style = MaterialTheme.typography.labelSmall, color = colors.outlineVariant, fontFamily = FontFamily.Monospace)
                Text(
                    entry.timestamp.takeLast(19).take(8),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.outlineVariant
                )
            }

            entry.details?.let { details ->
                Spacer(Modifier.height(Dimens.SpaceXs))
                Text(
                    details,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
