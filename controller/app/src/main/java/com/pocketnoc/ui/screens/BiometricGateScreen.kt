package com.pocketnoc.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
 * Tela de gate biométrico — exibida antes do app principal.
 * Auto-dispara a autenticação na primeira composição.
 * Se o dispositivo não suportar biometria, pula automaticamente.
 */
@Composable
fun BiometricGateScreen(
    biometricManager: BiometricAuthManager,
    onAuthenticated: () -> Unit
) {
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (biometricManager.canAuthenticate()) {
            biometricManager.authenticate(
                activity = context as FragmentActivity,
                onSuccess = onAuthenticated,
                onError = { errorMessage = it }
            )
        } else {
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
            .background(
                Brush.verticalGradient(
                    listOf(DarkBackground, Color(0xFF060F28), DarkBackground)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .shadow(24.dp, RoundedCornerShape(24.dp), spotColor = NeonCyan.copy(alpha = pulse))
                    .clip(RoundedCornerShape(24.dp))
                    .background(DarkCard)
                    .border(1.5.dp, NeonCyan.copy(alpha = pulse * 0.8f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint = NeonCyan.copy(alpha = pulse),
                    modifier = Modifier.size(52.dp)
                )
            }

            Text(
                "POCKET NOC",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = NeonCyan,
                letterSpacing = 4.sp
            )

            Text(
                "AUTHENTICATION REQUIRED",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = TextMuted,
                letterSpacing = 2.sp
            )

            errorMessage?.let { error ->
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = CriticalRedHealth
                )
            }

            Button(
                onClick = {
                    errorMessage = null
                    biometricManager.authenticate(
                        activity = context as FragmentActivity,
                        onSuccess = onAuthenticated,
                        onError = { errorMessage = it }
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.15f)),
                border = ButtonDefaults.outlinedButtonBorder,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("AUTHENTICATE", color = NeonCyan, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }
}
