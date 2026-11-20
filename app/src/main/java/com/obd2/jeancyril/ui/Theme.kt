package com.obd2.jeancyril.ui

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

object ObdColors {
    val ok = Color(0xFF66BB6A)
    val warn = Color(0xFFFFB74D)
    val bad = Color(0xFFEF5350)
    val idle = Color(0xFF90A4AE)
    val accent = Color(0xFF4DD0E1)
}

private val darkScheme = darkColorScheme(
    primary = ObdColors.accent,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF164E56),
    onPrimaryContainer = Color(0xFFB2EBF2),
    secondary = Color(0xFF9CCC65),
    onSecondary = Color(0xFF1F3700),
    secondaryContainer = Color(0xFF334D14),
    onSecondaryContainer = Color(0xFFD7F5A8),
    tertiary = Color(0xFFFFB74D),
    background = Color(0xFF0E141A),
    onBackground = Color(0xFFE3E8EC),
    surface = Color(0xFF161E26),
    onSurface = Color(0xFFE3E8EC),
    surfaceVariant = Color(0xFF22303B),
    onSurfaceVariant = Color(0xFFB8C6D1),
    outline = Color(0xFF4A5C69),
    error = ObdColors.bad,
    onError = Color(0xFF2B0000),
)

private val lightScheme = lightColorScheme(
    primary = Color(0xFF00798A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB2EBF2),
    onPrimaryContainer = Color(0xFF00363D),
    secondary = Color(0xFF4F7A28),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCEDC8),
    onSecondaryContainer = Color(0xFF1F3700),
    tertiary = Color(0xFFB45309),
    background = Color(0xFFF4F7F9),
    onBackground = Color(0xFF102027),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF102027),
    surfaceVariant = Color(0xFFE3E9ED),
    onSurfaceVariant = Color(0xFF44535E),
    outline = Color(0xFF8A9AA5),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

@Composable
fun ObdTheme(light: Boolean = false, content: @Composable () -> Unit) {
    val scheme = if (light) lightScheme else darkScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = scheme.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = scheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
        }
    }
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
