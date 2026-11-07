// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.ukpratik.founderguru.data.prefs.ThemePref

/** Direction A "Morning brief" tokens (design-system §2). Names follow the design's CSS variables. */
@Immutable
data class FgColors(
    val bg: Color,
    val surface: Color,
    val overlay: Color,
    val surface2: Color,
    val ink: Color,
    val ink2: Color,
    /** `ink-3`: meta text (source · time, supporting text, nav labels). No exact M3 role. */
    val meta: Color,
    val accent: Color,
    val accentSoft: Color,
    val outline: Color,
    val rule: Color,
    val inv: Color,
    val invOn: Color,
    val invAccent: Color,
    val isDark: Boolean,
)

val LightFg = FgColors(
    bg = Color(0xFFF7F4EE), surface = Color(0xFFFFFDF9), overlay = Color(0xFFFFFDF9), surface2 = Color(0xFFEFEADF),
    ink = Color(0xFF1C1A17), ink2 = Color(0xFF4F4A42), meta = Color(0xFF6B655B),
    accent = Color(0xFFA3361C), accentSoft = Color(0xFFF4E2D9), outline = Color(0xFF8A8377), rule = Color(0xFFDDD6CA),
    inv = Color(0xFF1C1A17), invOn = Color(0xFFF7F4EE), invAccent = Color(0xFFF0906F), isDark = false,
)

val DarkFg = FgColors(
    bg = Color(0xFF141311), surface = Color(0xFF1C1B18), overlay = Color(0xFF2A2824), surface2 = Color(0xFF262420),
    ink = Color(0xFFEDE8DF), ink2 = Color(0xFFC4BDB1), meta = Color(0xFF9D968A),
    accent = Color(0xFFF0906F), accentSoft = Color(0xFF3B241B), outline = Color(0xFF77716A), rule = Color(0xFF302D28),
    inv = Color(0xFFEDE8DF), invOn = Color(0xFF1C1A17), invAccent = Color(0xFFA3361C), isDark = true,
)

/** design-system §2.3 mapping. `surfaceTint` = background so elevated surfaces are never tinted brick. */
private fun scheme(c: FgColors) = (if (c.isDark) darkColorScheme() else lightColorScheme()).copy(
    primary = c.accent, onPrimary = if (c.isDark) Color(0xFF1C1A17) else Color.White,
    primaryContainer = c.accentSoft, onPrimaryContainer = c.accent,
    secondary = c.ink, onSecondary = c.bg,
    secondaryContainer = c.accentSoft, onSecondaryContainer = c.accent,
    tertiary = c.ink2, onTertiary = c.bg,
    background = c.bg, onBackground = c.ink, surface = c.bg, onSurface = c.ink,
    surfaceVariant = c.surface2, onSurfaceVariant = c.ink2,
    surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
    surfaceContainerHigh = c.overlay, surfaceContainerHighest = if (c.isDark) c.overlay else c.surface2,
    outline = c.outline, outlineVariant = c.rule,
    error = c.accent, onError = if (c.isDark) Color(0xFF1C1A17) else Color.White,
    errorContainer = c.accentSoft, onErrorContainer = c.accent,
    inverseSurface = c.inv, inverseOnSurface = c.invOn, inversePrimary = c.invAccent,
    scrim = Color.Black, surfaceTint = c.bg,
)

private val LightScheme = scheme(LightFg)
private val DarkScheme = scheme(DarkFg)

val FgShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

val LocalFgColors = staticCompositionLocalOf { LightFg }

/** The theme the user picked, resolved against the system (Settings › Theme may override it). */
@Composable
fun ThemePref.isDark(): Boolean = when (this) {
    ThemePref.SYSTEM -> isSystemInDarkTheme()
    ThemePref.DARK -> true
    ThemePref.LIGHT -> false
}

@Composable
fun FounderGuruTheme(dark: Boolean, content: @Composable () -> Unit) {
    val fg = if (dark) DarkFg else LightFg
    CompositionLocalProvider(LocalFgColors provides fg) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = FgTypography,
            shapes = FgShapes,
            content = content,
        )
    }
}

object Fg {
    val colors: FgColors @Composable get() = LocalFgColors.current
}
