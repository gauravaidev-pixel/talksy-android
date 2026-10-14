package com.talksy.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightScheme = lightColorScheme(
    primary = Indigo, onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = Cyan, tertiary = Mint,
    background = LightBg, onBackground = LightOnSurface,
    surface = LightSurface, onSurface = LightOnSurface,
    surfaceVariant = LightBg, onSurfaceVariant = LightMuted,
    outline = LightOutline, error = Rose,
)

private val DarkScheme = darkColorScheme(
    primary = Indigo, onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = Cyan, tertiary = Mint,
    background = DarkBg, onBackground = DarkOnSurface,
    surface = DarkSurface, onSurface = DarkOnSurface,
    surfaceVariant = DarkSurface, onSurfaceVariant = DarkMuted,
    outline = DarkOutline, error = Rose,
)

@Composable
fun TalksyTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = scheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(colorScheme = scheme, typography = TalksyTypography, content = content)
}
