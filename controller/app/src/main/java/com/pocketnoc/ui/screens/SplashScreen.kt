package com.pocketnoc.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.pocketnoc.ui.navigation.AppRoute
import com.pocketnoc.data.local.entities.ServerEntity
import kotlinx.coroutines.delay
import com.pocketnoc.ui.theme.*

@Composable
fun SplashScreen(
    navController: NavController,
    servers: List<ServerEntity>
) {
    var loadingText by remember { mutableStateOf("INITIALIZING SECURE KERNEL...") }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        // Sequência HackerSec de boot
        delay(500)
        loadingText = "ESTABLISHING ENCRYPTED TUNNEL..."
        progress = 0.3f
        delay(600)
        loadingText = "VERIFYING NOC NODE..."
        progress = 0.6f
        delay(500)
        loadingText = "DECRYPTING PAYLOAD..."
        progress = 0.9f
        delay(400)
        progress = 1.0f
        
        // Decisão de Rota
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

    // UI da Splash Screen
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo / Icon
        Box(
            modifier = Modifier
                .size(120.dp)
                .shadow(elevation = 24.dp, spotColor = NeonCyan.copy(alpha = 0.6f), shape = RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp))
                .background(DarkCard)
                .border(2.dp, NeonCyan, RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = "Logo",
                tint = NeonCyan,
                modifier = Modifier.size(64.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "POCKET NOC",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp
        )
        Text(
            text = "ZER0 TRUST MOBILE EDITION",
            color = NeonCyan.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(64.dp))

        // Cyber Progress Bar
        Column(
            modifier = Modifier.fillMaxWidth(0.7f),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = loadingText,
                color = NeonCyan,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(DarkSurface)
            ) {
                val animatedProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = tween(400, easing = LinearEasing),
                    label = "progress"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(NeonCyan)
                        .shadow(elevation = 8.dp, spotColor = NeonCyan)
                )
            }
        }
    }
}
