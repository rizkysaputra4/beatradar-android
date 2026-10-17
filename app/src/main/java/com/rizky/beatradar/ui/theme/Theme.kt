package com.rizky.beatradar.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.rizky.beatradar.data.ThemeMode

object Brand {
    val Indigo = Color(0xFF3D2C8D)
    /** The "in range" highlight, identical in light and dark. */
    val Coral = Color(0xFFFF5A5F)
    val OnCoral = Color.White
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF5140B8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5DEFF),
    onPrimaryContainer = Color(0xFF170965),
    secondary = Color(0xFF5F5C71),
    secondaryContainer = Color(0xFFE5DFF9),
    onSecondaryContainer = Color(0xFF1C192B),
    tertiary = Color(0xFFB3272F),
    background = Color(0xFFFCF8FF),
    surface = Color(0xFFFCF8FF),
    surfaceContainer = Color(0xFFF1ECF8),
    surfaceContainerHigh = Color(0xFFEBE6F3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC8BFFF),
    onPrimary = Color(0xFF29148A),
    primaryContainer = Brand.Indigo,
    onPrimaryContainer = Color(0xFFE5DEFF),
    secondary = Color(0xFFC9C3DC),
    secondaryContainer = Color(0xFF474459),
    onSecondaryContainer = Color(0xFFE5DFF9),
    tertiary = Color(0xFFFFB3B0),
    background = Color(0xFF131218),
    surface = Color(0xFF131218),
    surfaceContainer = Color(0xFF1F1E25),
    surfaceContainerHigh = Color(0xFF2A2830),
)

@Composable
fun BeatRadarTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
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
