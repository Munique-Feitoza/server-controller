package com.pocketnoc.ui.screens

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.pocketnoc.ui.theme.*
import com.pocketnoc.utils.BiometricAuthManager

/**
 * Tela de gate biom\u00E9trico \u2014 exibida antes do app principal.
 * Auto-dispara a autentica\u00E7\u00E3o na primeira composi\u00E7\u00E3o.
 * Se o dispositivo n\u00E3o suportar biometria, pula automaticamente.
 */
@Composable
fun BiometricGateScreen(
    biometricManager: BiometricAuthManager,
    onAuthenticated: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (activity != null && biometricManager.canAuthenticate()) {
            biometricManager.authenticate(
                activity = activity,
                onSuccess = onAuthenticated,
                onError = { errorMessage = it }
            )
        } else {
            // Sem activity host ou sem biometria/credencial no device: não trava o acesso.
            onAuthenticated()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "bio_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1600, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "bio_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.Space3xl)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .shadow(24.dp, AppShapes.sheet, spotColor = colors.primary.copy(alpha = pulse))
                    .clip(AppShapes.sheet)
                    .background(colors.surfaceVariant)
                    .border(Dimens.BorderMedium, colors.primary.copy(alpha = pulse * 0.8f), AppShapes.sheet),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint = colors.primary.copy(alpha = pulse),
                    modifier = Modifier.size(Dimens.Icon3xl)
                )
            }

            Text(
                "POCKET NOC",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = colors.primary,
                letterSpacing = 4.sp
            )

            Text(
                "AUTHENTICATION REQUIRED",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = colors.outlineVariant,
                letterSpacing = 2.sp
            )

            errorMessage?.let { error ->
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusColors.critical
                )
            }

            Button(
                onClick = {
                    errorMessage = null
                    activity?.let {
                        biometricManager.authenticate(
                            activity = it,
                            onSuccess = onAuthenticated,
                            onError = { e -> errorMessage = e }
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary.copy(alpha = 0.15f)),
                border = ButtonDefaults.outlinedButtonBorder,
                shape = AppShapes.xl,
                modifier = Modifier.padding(top = Dimens.ScreenPadding)
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Dimens.SpaceMd))
                Text("AUTHENTICATE", color = colors.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }
}

/** Desembrulha o ContextWrapper até achar a FragmentActivity host (robusto contra ContextThemeWrapper). */
private fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
