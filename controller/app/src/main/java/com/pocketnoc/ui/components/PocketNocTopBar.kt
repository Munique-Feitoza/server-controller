package com.pocketnoc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pocketnoc.ui.theme.*

/**
 * TopBar padrao do PocketNOC — reutilizada em todas as telas internas.
 * Elimina duplicacao de ~25 linhas por tela.
 */
@Composable
fun PocketNocTopBar(
    title: String,
    subtitle: String? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onBack: () -> Unit,
    onRefresh: (() -> Unit)? = null,
    extraAction: (@Composable () -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
                .padding(horizontal = Dimens.SpaceXl, vertical = Dimens.SpaceMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Botao voltar
            Box(
                Modifier.size(Dimens.TopBarButton).clip(AppShapes.large)
                    .background(accentColor.copy(alpha = 0.10f))
                    .border(Dimens.BorderThin, accentColor.copy(alpha = 0.35f), AppShapes.large)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = accentColor, modifier = Modifier.size(Dimens.IconMd))
            }

            Spacer(Modifier.width(Dimens.SpaceLg))

            // Titulo e subtitulo
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = accentColor,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }

            // Acao extra (opcional)
            extraAction?.invoke()

            // Botao refresh (opcional)
            onRefresh?.let { refresh ->
                Box(
                    Modifier.size(Dimens.TopBarButton).clip(AppShapes.large)
                        .background(colors.primary.copy(alpha = 0.10f))
                        .border(Dimens.BorderThin, colors.primary.copy(alpha = 0.35f), AppShapes.large)
                        .clickable(onClick = refresh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Refresh, null, tint = colors.primary, modifier = Modifier.size(Dimens.IconMd))
                }
            }
        }

        // Linha separadora
        Box(Modifier.fillMaxWidth().height(Dimens.BorderThin).background(accentColor.copy(alpha = 0.3f)))
    }
}
