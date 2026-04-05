package com.pocketnoc.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ═══════════════════════════════════════════════════════════════════
// RAW COLORS — uso interno apenas. Screens devem usar AppColors.*
// ═══════════════════════════════════════════════════════════════════

// Neon (tema escuro)
internal val NeonCyan    = Color(0xFF00F0FF)
internal val NeonMagenta = Color(0xFFFF00FF)
internal val NeonPurple  = Color(0xFF8B00FF)
internal val NeonBlue    = Color(0xFF0080FF)
internal val NeonGreen   = Color(0xFF00FF88)
internal val NeonPink    = Color(0xFFFF1493)

// Superficies escuras
internal val DarkBackground = Color(0xFF0A0E27)
internal val DarkSurface    = Color(0xFF111B3D)
internal val DarkCard       = Color(0xFF1A2551)

// Superficies claras
internal val LightBackground = Color(0xFFF5F7FA)
internal val LightSurface    = Color(0xFFFFFFFF)
internal val LightCard       = Color(0xFFE8EDF5)

// Status (fixos — não mudam com o tema)
internal val CriticalRed      = Color(0xFFFF0040)
internal val CriticalRedHealth = Color(0xFFEF5350)
internal val WarningOrange     = Color(0xFFFF6B00)
internal val HealthyGreen      = Color(0xFF00FF88)
internal val HealthyBlue       = Color(0xFF4DB8FF)
internal val WarningGreen      = Color(0xFF66BB6A)
internal val AlertYellow       = Color(0xFFFDD835)

// Texto
internal val TextPrimary   = Color(0xFFE3F2FD)
internal val TextSecondary = Color(0xFFB0BEC5)
internal val TextMuted     = Color(0xFF78909C)

// ═══════════════════════════════════════════════════════════════════
// COLOR SCHEMES
// ═══════════════════════════════════════════════════════════════════

private val DarkColorScheme = darkColorScheme(
    primary             = NeonCyan,
    onPrimary           = DarkBackground,
    primaryContainer    = NeonBlue,
    onPrimaryContainer  = NeonCyan,
    secondary           = NeonMagenta,
    onSecondary         = DarkBackground,
    secondaryContainer  = NeonPurple,
    onSecondaryContainer= NeonMagenta,
    tertiary            = NeonGreen,
    onTertiary          = DarkBackground,
    background          = DarkBackground,
    onBackground        = TextPrimary,
    surface             = DarkSurface,
    onSurface           = TextPrimary,
    surfaceVariant      = DarkCard,
    onSurfaceVariant    = TextSecondary,
    error               = CriticalRed,
    onError             = DarkBackground,
    outline             = NeonCyan.copy(alpha = 0.3f),
    outlineVariant      = TextMuted,
    scrim               = Color.Black.copy(alpha = 0.8f),
)

private val LightColorScheme = lightColorScheme(
    primary             = Color(0xFF0077B6),
    onPrimary           = Color.White,
    primaryContainer    = Color(0xFF1565C0),
    onPrimaryContainer  = Color(0xFF003459),
    secondary           = Color(0xFF7B2FBE),
    onSecondary         = Color.White,
    secondaryContainer  = Color(0xFFE8DEF8),
    onSecondaryContainer= Color(0xFF4A0080),
    tertiary            = Color(0xFF00875A),
    onTertiary          = Color.White,
    background          = LightBackground,
    onBackground        = Color(0xFF1A1A2E),
    surface             = LightSurface,
    onSurface           = Color(0xFF1A1A2E),
    surfaceVariant      = LightCard,
    onSurfaceVariant    = Color(0xFF546E7A),
    error               = Color(0xFFB00020),
    onError             = Color.White,
    outline             = Color(0xFF79747E),
    outlineVariant      = Color(0xFF90A4AE),
    scrim               = Color.Black.copy(alpha = 0.3f),
)

// ═══════════════════════════════════════════════════════════════════
// TYPOGRAPHY — sem cores hardcoded, herda do colorScheme
// ═══════════════════════════════════════════════════════════════════

private val AppTypography = Typography(
    displayLarge  = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,     fontSize = 40.sp, lineHeight = 48.sp),
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,     fontSize = 32.sp, lineHeight = 40.sp),
    displaySmall  = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 34.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,     fontSize = 28.sp, lineHeight = 36.sp),
    headlineMedium= TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleLarge    = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium   = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,   fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall    = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,   fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge     = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,   fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium    = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall     = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,   fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge    = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium   = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,   fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall    = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,   fontSize = 12.sp, lineHeight = 16.sp),
)

// ═══════════════════════════════════════════════════════════════════
// THEME STATE
// ═══════════════════════════════════════════════════════════════════

class ThemeState {
    var isDarkTheme by mutableStateOf(true)
    fun toggle() { isDarkTheme = !isDarkTheme }
}

val LocalThemeState = compositionLocalOf { ThemeState() }

// ═══════════════════════════════════════════════════════════════════
// THEME COMPOSABLE
// ═══════════════════════════════════════════════════════════════════

@Composable
fun PocketNOCTheme(
    themeState: ThemeState = LocalThemeState.current,
    content: @Composable () -> Unit
) {
    val isDark = themeState.isDarkTheme
    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val extended = if (isDark) DarkExtended else LightExtended

    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
