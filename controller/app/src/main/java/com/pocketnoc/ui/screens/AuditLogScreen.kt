package com.pocketnoc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketnoc.data.local.entities.ServerEntity
import com.pocketnoc.data.models.AuditEntry
import com.pocketnoc.ui.theme.*
import com.pocketnoc.ui.viewmodels.DashboardViewModel
import com.pocketnoc.utils.formatIsoToBrasilia

private fun methodColor(method: String): Color = when (method.uppercase()) {
    "GET"    -> NeonCyan
    "POST"   -> NeonGreen
    "DELETE" -> CriticalRedHealth
    "PUT"    -> AlertYellow
    "PATCH"  -> NeonMagenta
    else     -> TextSecondary
}

private fun statusColor(code: Int): Color = when {
    code in 200..299 -> NeonGreen
    code in 300..399 -> NeonCyan
    code in 400..499 -> AlertYellow
    code >= 500      -> CriticalRedHealth
    else             -> TextMuted
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(
    viewModel: DashboardViewModel,
    serverId: Int,
    onNavigateBack: () -> Unit
) {
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
                    .background(Brush.verticalGradient(listOf(Color(0xFF0A0F1E), DarkSurface.copy(alpha = 0.95f))))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(NeonPurple.copy(alpha = 0.10f))
                            .border(1.dp, NeonPurple.copy(alpha = 0.35f), RoundedCornerShape(9.dp))
                            .clickable(onClick = onNavigateBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = NeonPurple, modifier = Modifier.size(17.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "AUDIT LOG",
                            color = NeonPurple,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 3.sp
                        )
                        Text(
                            server?.name ?: "Servidor",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(NeonCyan.copy(alpha = 0.10f))
                            .border(1.dp, NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(9.dp))
                            .clickable {
                                server?.let {
                                    isLoading = true
                                    // Will be re-triggered by LaunchedEffect
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar", tint = NeonCyan, modifier = Modifier.size(17.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Brush.horizontalGradient(listOf(Color.Transparent, NeonPurple.copy(alpha = 0.6f), Color.Transparent)))
                )
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Brush.verticalGradient(listOf(DarkBackground, Color(0xFF0F0A28), DarkBackground)))
        ) {
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NeonPurple)
                    }
                }
                errorMsg != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("ERR", color = CriticalRedHealth, fontFamily = FontFamily.Monospace, fontSize = 24.sp)
                            Text(errorMsg ?: "", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                auditEntries.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(48.dp))
                            Text("Nenhum registro de auditoria", style = MaterialTheme.typography.titleMedium, color = NeonPurple)
                            Text("As requisições serão registradas automaticamente.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        item {
                            Text(
                                "${auditEntries.size} registros",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
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
    val mColor = methodColor(entry.method)
    val sColor = statusColor(entry.statusCode)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkCard)
            .border(1.dp, mColor.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Method badge
        Box(
            modifier = Modifier
                .width(48.dp)
                .background(mColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                .border(1.dp, mColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(entry.method, style = MaterialTheme.typography.labelSmall, color = mColor, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    entry.endpoint,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .background(sColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("${entry.statusCode}", style = MaterialTheme.typography.labelSmall, color = sColor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(entry.sourceIp, style = MaterialTheme.typography.labelSmall, color = TextMuted, fontFamily = FontFamily.Monospace)
                Text(
                    entry.timestamp.takeLast(19).take(8),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            entry.details?.let { details ->
                Spacer(Modifier.height(4.dp))
                Text(
                    details,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
