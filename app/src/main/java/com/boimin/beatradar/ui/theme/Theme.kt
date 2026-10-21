package com.boimin.beatradar.ui.theme

import androidx.compose.ui.graphics.luminance
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.boimin.beatradar.data.ThemeMode

object Brand {
    val Indigo = Color(0xFF3D2C8D)
    /** Electric Violet primary accent in dark mode, Electric Indigo in light mode. */
    val Violet = Color(0xFF7C6CF6)
    /** The "in range" & streak highlight, identical in light and dark. */
    val Coral = Color(0xFFFF5A5F)
    val OnCoral = Color.White
    val Success = Color(0xFF10B981)
    /** "In target": the one colour for a tempo inside the user's range (red is only for destructive actions). */
    val Mint = Color(0xFF51DAA7)
    /** The day-streak flame. */
    val Flame = Color(0xFFF88F4F)
    /** Love hearts. */
    val Heart = Color(0xFFF08AA8)
    /** Mint dark enough to read on light backgrounds. */
    val MintDeep = Color(0xFF0E9F6E)
}

/** The "in target" colour for the current theme. */
@Composable
fun inTargetColor(): Color = if (isDark()) Brand.Mint else Brand.MintDeep

@Composable
private fun isDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * The design's extra tones (BeatRadar Fixes, turn 1) beyond Material's roles: a softer second text colour, a fainter
 * third, the dial ring and scale track, filled pill buttons, chips and their outlines, and the selected-chip fill.
 */
object Tone {
    val text2: Color @Composable get() = if (isDark()) Color(0xFFC9C7D4) else Color(0xFF45434E)
    val text3: Color @Composable get() = if (isDark()) Color(0xFF8D8B9A) else Color(0xFF6A6874)
    val track: Color @Composable get() = if (isDark()) Color(0xFF1E1D27) else Color(0xFFE9E8EF)
    val ring: Color @Composable get() = if (isDark()) Color(0xFF2A2935) else Color(0xFFDCDAE3)
    val pill: Color @Composable get() = if (isDark()) Color(0xFF24222F) else Color(0xFFEDEBF4)
    val chip: Color @Composable get() = if (isDark()) Color(0xFF17151F) else Color.White
    val line: Color @Composable get() = if (isDark()) Color(0xFF2F2E3B) else Color(0xFFDCDAE3)
    val chipLine: Color @Composable get() = if (isDark()) Color(0xFF3A3550) else Color(0xFFD5D0EC)
    val selected: Color @Composable get() = if (isDark()) Color(0xFF211D38) else Color(0xFFECE9FF)
    val sheet: Color @Composable get() = if (isDark()) Color(0xFF17161E) else Color.White
    val sheetLine: Color @Composable get() = if (isDark()) Color(0xFF262532) else Color(0xFFE2E1EA)
    val primarySoft: Color @Composable get() = if (isDark()) Color(0xFFB8AEF7) else Color(0xFF4A3FC2)
    val logoTile: Color @Composable get() = if (isDark()) Color(0xFF1E1B33) else Color(0xFFECE9FF)
    /** "Radar" in the wordmark: Electric Violet as on the splash, Electric Indigo on light backgrounds. */
    val wordmarkAccent: Color @Composable get() = if (isDark()) Brand.Violet else Color(0xFF5846E0)
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF5846E0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAE7FF),
    onPrimaryContainer = Color(0xFF1E1466),
    secondary = Color(0xFF656578),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFEFF7),
    onSecondaryContainer = Color(0xFF111118),
    tertiary = Brand.Coral,
    onTertiary = Color.White,
    background = Color(0xFFF7F7FB),
    onBackground = Color(0xFF111118),
    surface = Color(0xFFF7F7FB),
    onSurface = Color(0xFF111118),
    surfaceVariant = Color(0xFFEFEFF7),
    onSurfaceVariant = Color(0xFF656578),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEFEFF7),
    surfaceContainerHighest = Color(0xFFE2E2EE),
    outline = Color(0xFFE2E2EE),
    outlineVariant = Color(0xFFE8E8F2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7B6CF0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF231F42),
    onPrimaryContainer = Color(0xFFE5DEFF),
    secondary = Color(0xFF8E8EA0),
    onSecondary = Color(0xFF0B0B10),
    secondaryContainer = Color(0xFF1D1D2B),
    onSecondaryContainer = Color(0xFFF4F4F8),
    tertiary = Brand.Coral,
    onTertiary = Color.White,
    background = Color(0xFF0C0B10),
    onBackground = Color(0xFFF1F0F5),
    surface = Color(0xFF0C0B10),
    onSurface = Color(0xFFF1F0F5),
    surfaceVariant = Color(0xFF1D1D2B),
    onSurfaceVariant = Color(0xFFA09FAE),
    surfaceContainerLowest = Color(0xFF0C0B10),
    surfaceContainerLow = Color(0xFF121118),
    surfaceContainer = Color(0xFF16151D),
    surfaceContainerHigh = Color(0xFF24222F),
    surfaceContainerHighest = Color(0xFF2A2935),
    outline = Color(0xFF2F2E3B),
    outlineVariant = Color(0xFF262532),
)

@Composable
fun BeatRadarTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/**
 * Switches in the brand style: white thumb on the primary track when on. Off uses the muted text colour for the thumb
 * and border, since the theme's `outline` (Material's default for them) is a near-invisible card hairline here.
 */
@Composable
fun brandSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
)
