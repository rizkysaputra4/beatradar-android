package com.boimin.beatradar.ui.theme

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
    primary = Color(0xFF7C6CF6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF231F42),
    onPrimaryContainer = Color(0xFFE5DEFF),
    secondary = Color(0xFF8E8EA0),
    onSecondary = Color(0xFF0B0B10),
    secondaryContainer = Color(0xFF1D1D2B),
    onSecondaryContainer = Color(0xFFF4F4F8),
    tertiary = Brand.Coral,
    onTertiary = Color.White,
    background = Color(0xFF0B0B10),
    onBackground = Color(0xFFF4F4F8),
    surface = Color(0xFF0B0B10),
    onSurface = Color(0xFFF4F4F8),
    surfaceVariant = Color(0xFF1D1D2B),
    onSurfaceVariant = Color(0xFF8E8EA0),
    surfaceContainerLowest = Color(0xFF0B0B10),
    surfaceContainerLow = Color(0xFF111119),
    surfaceContainer = Color(0xFF14141D),
    surfaceContainerHigh = Color(0xFF1D1D2B),
    surfaceContainerHighest = Color(0xFF262636),
    outline = Color(0xFF262636),
    outlineVariant = Color(0xFF1F1F2E),
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
