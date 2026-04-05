package com.pocketnoc.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.pocketnoc.data.local.entities.ServerEntity
import com.pocketnoc.ui.navigation.AppRoute
import com.pocketnoc.ui.theme.*
import kotlinx.coroutines.delay

private val bootSequence = listOf(
    "INITIALIZING SECURE KERNEL...",
    "LOADING CRYPTO MODULE...",
    "ESTABLISHING ZERO-TRUST LAYER...",
    "VERIFYING NOC NODES...",
    "SYSTEM READY."
)

@Composable
fun SplashScreen(
    navController: NavController,
    servers: List<ServerEntity>
) {
    val colors = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current

    var stepIndex by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var bootDone by remember { mutableStateOf(false) }

    // Animação de scan horizontal (linha que percorre de cima para baixo)
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val scanY by infiniteTransition.animateFloat(
        initialValue = -0.05f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "scan_y"
    )
    // Pulso no ícone
    val iconGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "icon_glow"
    )

    // Roda a sequência de boot
    LaunchedEffect(Unit) {
        bootSequence.forEachIndexed { i, _ ->
            stepIndex = i
            progress = (i + 1).toFloat() / bootSequence.size
            delay(if (i == bootSequence.lastIndex) 350L else 480L)
        }
        bootDone = true
    }

    // Navega assim que a animação terminar E o Room tiver carregado
    LaunchedEffect(bootDone, servers) {
        if (!bootDone) return@LaunchedEffect
        // Aguarda o Room emitir pelo menos uma vez (lista não nula inicialmente = Room carregou)
        // Room emite lista vazia se não há servidores, mas emite imediatamente após o load
        if (servers.isNotEmpty()) {
            navController.navigate(AppRoute.Dashboard.route) {
                popUpTo(AppRoute.Splash.route) { inclusive = true }
            }
        } else {
            navController.navigate(AppRoute.Login.route) {
                popUpTo(AppRoute.Splash.route) { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Scan line – linha de luz horizontal animada
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.SpaceXxs)
                .offset(y = with(androidx.compose.ui.platform.LocalDensity.current) {
                    (scanY * 900).dp
                })
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, colors.primary.copy(alpha = 0.6f), Color.Transparent)
                    )
                )
        )

        // Conteúdo central
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.Icon2xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // Logo box com brilho pulsante
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .shadow(
                        elevation = Dimens.Space4xl,
                        spotColor = colors.primary.copy(alpha = iconGlow),
                        shape = AppShapes.sheet
                    )
                    .clip(AppShapes.sheet)
                    .background(colors.surfaceVariant)
                    .border(
                        Dimens.BorderMedium,
                        Brush.linearGradient(
                            listOf(colors.primary.copy(alpha = iconGlow), ext.magenta.copy(alpha = iconGlow * 0.6f))
                        ),
                        AppShapes.sheet
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = colors.primary.copy(alpha = iconGlow),
                    modifier = Modifier.size(60.dp)
                )
            }

            Spacer(modifier = Modifier.height(Dimens.Space4xl))

            Text(
                text = "POCKET NOC",
                color = colors.onSurface,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp
            )
            Spacer(modifier = Modifier.height(Dimens.SpaceXs))
            Text(
                text = "ZERO TRUST MOBILE EDITION",
                color = colors.primary.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 3.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(72.dp))

            // Boot log — cada etapa aparece como linha de terminal
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXxs)
            ) {
                bootSequence.forEachIndexed { i, line ->
                    val isActive = i == stepIndex
                    val isDone = i < stepIndex
                    val alpha = when {
                        isDone   -> 0.45f
                        isActive -> 1f
                        else     -> 0.15f
                    }
                    val color = when {
                        isDone   -> colors.tertiary
                        isActive -> colors.primary
                        else     -> colors.outlineVariant
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
                    ) {
                        Text(
                            text = if (isDone) "✓" else if (isActive) ">" else " ",
                            color = color.copy(alpha = alpha),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                        Text(
                            text = line,
                            color = color.copy(alpha = alpha),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.Space3xl))

            // Barra de progresso
            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = tween(380, easing = FastOutSlowInEasing),
                label = "progress"
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimens.ProgressSm)
                        .clip(AppShapes.small)
                        .background(colors.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .fillMaxHeight()
                            .clip(AppShapes.small)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(colors.primary, ext.magenta.copy(alpha = 0.8f))
                                )
                            )
                    )
                }
                Spacer(modifier = Modifier.height(Dimens.SpaceSm))
                Text(
                    text = "${(animatedProgress * 100).toInt()}%",
                    color = colors.primary.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }

        // Versão no rodapé
        Text(
            text = "v0.3.0 — Munux Security",
            color = colors.outlineVariant.copy(alpha = 0.4f),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Dimens.Space3xl)
        )
    }
}
