package com.pocketnoc.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Cores de status fixas — iguais em dark e light.
 * Vermelho e sempre vermelho, independente do tema.
 */
object StatusColors {
    val critical = Color(0xFFEF5350)
    val warning  = Color(0xFFFDD835)
    val caution  = Color(0xFF66BB6A)
    val healthy  = Color(0xFF4DB8FF)
    val success  = Color(0xFF00FF88)
}

/**
 * Cores neon/accent que variam entre dark e light.
 * Disponibilizadas via CompositionLocal pelo PocketNOCTheme.
 */
data class ExtendedColors(
    val cyan: Color,
    val magenta: Color,
    val blue: Color,
    val green: Color,
    val purple: Color,
    val pink: Color,
)

val DarkExtended = ExtendedColors(
    cyan    = Color(0xFF00F0FF),
    magenta = Color(0xFFFF00FF),
    blue    = Color(0xFF0080FF),
    green   = Color(0xFF00FF88),
    purple  = Color(0xFF8B00FF),
    pink    = Color(0xFFFF1493),
)

val LightExtended = ExtendedColors(
    cyan    = Color(0xFF0077B6),
    magenta = Color(0xFF9C27B0),
    blue    = Color(0xFF1565C0),
    green   = Color(0xFF00875A),
    purple  = Color(0xFF6A1B9A),
    pink    = Color(0xFFC2185B),
)

val LocalExtendedColors = staticCompositionLocalOf { DarkExtended }

/**
 * Acessores semanticos que delegam para MaterialTheme.colorScheme.
 * Usar estes em vez de cores hardcoded para dark/light funcionar.
 */
object AppColors {
    val primary: Color       @Composable get() = MaterialTheme.colorScheme.primary
    val secondary: Color     @Composable get() = MaterialTheme.colorScheme.secondary
    val tertiary: Color      @Composable get() = MaterialTheme.colorScheme.tertiary
    val accent: Color        @Composable get() = MaterialTheme.colorScheme.primaryContainer

    val background: Color    @Composable get() = MaterialTheme.colorScheme.background
    val surface: Color       @Composable get() = MaterialTheme.colorScheme.surface
    val card: Color          @Composable get() = MaterialTheme.colorScheme.surfaceVariant

    val onBackground: Color  @Composable get() = MaterialTheme.colorScheme.onBackground
    val onSurface: Color     @Composable get() = MaterialTheme.colorScheme.onSurface
    val onSurfaceAlt: Color  @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val muted: Color         @Composable get() = MaterialTheme.colorScheme.outlineVariant
    val outline: Color       @Composable get() = MaterialTheme.colorScheme.outline

    val error: Color         @Composable get() = MaterialTheme.colorScheme.error

    // Cores neon estendidas
    val cyan: Color    @Composable get() = LocalExtendedColors.current.cyan
    val magenta: Color @Composable get() = LocalExtendedColors.current.magenta
    val blue: Color    @Composable get() = LocalExtendedColors.current.blue
    val green: Color   @Composable get() = LocalExtendedColors.current.green
    val purple: Color  @Composable get() = LocalExtendedColors.current.purple
}

/**
 * Gradientes comuns que respeitam o tema.
 */
object AppGradients {
    @Composable
    fun background() = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.background,
        )
    )

    @Composable
    fun topBar() = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        )
    )

    @Composable
    fun neonLine() = Brush.horizontalGradient(
        listOf(
            Color.Transparent,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
            Color.Transparent,
        )
    )
}
