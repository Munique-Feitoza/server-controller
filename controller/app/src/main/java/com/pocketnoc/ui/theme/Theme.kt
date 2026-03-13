package com.pocketnoc.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ========== CORES FUTURISTAS NEON ==========
// Paleta Cyberpunk-Inspired com degradês RGB

// Cores Primárias - Neon Cyan/Magenta
val NeonCyan = Color(0xFF00F0FF)
val NeonMagenta = Color(0xFFFF00FF)
val NeonPurple = Color(0xFF8B00FF)
val NeonBlue = Color(0xFF0080FF)
val NeonGreen = Color(0xFF00FF88)
val NeonPink = Color(0xFFFF1493)

// Cores de Fundo - Ultra Dark com tom azulado
val DarkBackground = Color(0xFF0A0E27)  // Quase black com tom azul
val DarkSurface = Color(0xFF111B3D)     // Azul escuro profundo
val DarkCard = Color(0xFF1A2551)        // Azul escuro médio

// Cores de Status
val CriticalRed = Color(0xFFFF0040)
val WarningOrange = Color(0xFFFF6B00)
val HealthyGreen = Color(0xFF00FF88)

// Cores de Texto
val TextPrimary = Color(0xFFE3F2FD)
val TextSecondary = Color(0xFFB0BEC5)
val TextMuted = Color(0xFF78909C)

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = DarkBackground,
    primaryContainer = NeonBlue,
    onPrimaryContainer = NeonCyan,
    
    secondary = NeonMagenta,
    onSecondary = DarkBackground,
    secondaryContainer = NeonPurple,
    onSecondaryContainer = NeonMagenta,
    
    tertiary = NeonGreen,
    onTertiary = DarkBackground,
    tertiaryContainer = NeonPink,
    onTertiaryContainer = NeonGreen,
    
    background = DarkBackground,
    onBackground = TextPrimary,
    
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkCard,
    onSurfaceVariant = TextSecondary,
    
    error = CriticalRed,
    onError = DarkBackground,
    errorContainer = Color(0xFF9B0020),
    onErrorContainer = CriticalRed,
    
    outline = NeonCyan.copy(alpha = 0.3f),
    outlineVariant = TextMuted,
    scrim = Color.Black.copy(alpha = 0.8f),
)

// ========== TIPOGRAFIA FUTURISTA ==========
private val FuturisticTypography = Typography(
    // Display - Títulos Gigantes
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.6.sp,
        color = NeonCyan
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.2.sp,
        color = NeonCyan
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = NeonMagenta
    ),
    
    // Headline
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        color = NeonCyan
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        color = TextPrimary
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = TextPrimary
    ),
    
    // Title
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        color = NeonGreen
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = TextPrimary
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = TextSecondary
    ),
    
    // Body
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.5.sp,
        color = TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.4.sp,
        color = TextSecondary
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 16.sp,
        color = TextMuted
    ),
    
    // Label
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp,
        color = NeonCyan
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
        color = TextSecondary
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 8.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.4.sp,
        color = TextMuted
    ),
)

@Composable
fun PocketNOCTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = FuturisticTypography,
        content = content
    )
}
