package com.pocketnoc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.pocketnoc.data.models.SslCertStatus
import com.pocketnoc.data.models.SslCheckResponse
import com.pocketnoc.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SslCheckScreen(
    sslData: SslCheckResponse?,
    serverName: String,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current

    Scaffold(
        topBar = {
            com.pocketnoc.ui.components.PocketNocTopBar(
                title = "SSL MONITOR",
                subtitle = serverName,
                accentColor = ext.green,
                onBack = onNavigateBack,
                onRefresh = onRefresh
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues).background(colors.background)) {
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ext.green)
                }
            } else if (sslData == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Erro ao carregar dados SSL", color = StatusColors.critical)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
                    contentPadding = PaddingValues(vertical = Dimens.SpaceXl)
                ) {
                    // Resumo
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                            SslStatCard("OK", "${sslData.ok}", StatusColors.success, Modifier.weight(1f), colors)
                            SslStatCard("EXPIRANDO", "${sslData.expiring}", StatusColors.warning, Modifier.weight(1f), colors)
                            SslStatCard("EXPIRADO", "${sslData.expired}", StatusColors.critical, Modifier.weight(1f), colors)
                            SslStatCard("ERRO", "${sslData.errors}", colors.onSurfaceVariant, Modifier.weight(1f), colors)
                        }
                    }

                    // Problemas primeiro
                    val problems = sslData.certs.filter { it.status != "ok" }
                    val ok = sslData.certs.filter { it.status == "ok" }

                    if (problems.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(Dimens.SpaceMd))
                            Text("PROBLEMAS", style = MaterialTheme.typography.labelMedium, color = StatusColors.critical, fontFamily = FontFamily.Monospace)
                        }
                        items(problems) { cert -> SslCertCard(cert, colors, ext) }
                    }

                    if (ok.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(Dimens.SpaceMd))
                            Text("OK (${ok.size} dominios)", style = MaterialTheme.typography.labelMedium, color = StatusColors.success, fontFamily = FontFamily.Monospace)
                        }
                        items(ok) { cert -> SslCertCard(cert, colors, ext) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SslStatCard(label: String, value: String, color: Color, modifier: Modifier, colors: ColorScheme) {
    Box(
        modifier.clip(AppShapes.card).background(colors.surfaceVariant)
            .border(Dimens.BorderThin, color.copy(alpha = 0.4f), AppShapes.card)
            .padding(Dimens.SpaceMd),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black, color = color, fontFamily = FontFamily.Monospace)
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun SslCertCard(cert: SslCertStatus, colors: ColorScheme, ext: ExtendedColors) {
    val statusColor = when (cert.status) {
        "ok" -> StatusColors.success
        "expiring" -> StatusColors.warning
        "expired" -> StatusColors.critical
        "wrong_cert" -> StatusColors.warning
        else -> colors.onSurfaceVariant
    }

    val statusLabel = when (cert.status) {
        "ok" -> "OK"
        "expiring" -> "EXPIRANDO"
        "expired" -> "EXPIRADO"
        "wrong_cert" -> "CERT ERRADO"
        "no_cert" -> "SEM CERT"
        "error" -> "ERRO"
        else -> cert.status.uppercase()
    }

    Row(
        Modifier.fillMaxWidth().clip(AppShapes.card).background(colors.surfaceVariant)
            .border(Dimens.BorderThin, statusColor.copy(alpha = 0.3f), AppShapes.card)
            .padding(Dimens.SpaceLg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(Dimens.StatusDotLg).background(statusColor, CircleShape))

        Spacer(Modifier.width(Dimens.SpaceLg))

        Column(Modifier.weight(1f)) {
            Text(cert.domain, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)

            if (cert.status == "wrong_cert") {
                Text("Servindo: ${cert.subject}", style = MaterialTheme.typography.bodySmall, color = StatusColors.warning, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else if (cert.valid) {
                Text("Expira em ${cert.daysRemaining} dias", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }

        Box(
            Modifier.background(statusColor.copy(alpha = 0.15f), AppShapes.pill)
                .padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceXxs)
        ) {
            Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}
