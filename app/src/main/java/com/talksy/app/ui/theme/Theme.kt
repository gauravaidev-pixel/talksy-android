package com.talksy.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity

/** Colours that Material's ColorScheme has no slot for (chat bubbles, wallpaper, ticks...). */
@Immutable
data class TalksyExtras(
    val bubbleIn: Color,
    val bubbleOut: Color,
    val onBubbleIn: Color,
    val onBubbleOut: Color,
    val metaIn: Color,
    val metaOut: Color,
    val wallpaperTop: Color,
    val wallpaperBottom: Color,
    val tick: Color,
    val link: Color,
    val online: Color,
    val badge: Color,
    val badgeMuted: Color,
    val field: Color,
    val isDark: Boolean,
)

private val LightExtras = TalksyExtras(
    bubbleIn = Color(0xFFFFFFFF), bubbleOut = Color(0xFFEEFFDE),
    onBubbleIn = Color(0xFF111111), onBubbleOut = Color(0xFF111111),
    metaIn = Color(0xFF9AA1A6), metaOut = Color(0xFF5DB85B),
    wallpaperTop = Color(0xFFD3E5C4), wallpaperBottom = Color(0xFFB9D6A9),
    tick = Color(0xFF4FAE4E), link = Color(0xFF2A7BD0), online = Color(0xFF4FC977),
    badge = Color(0xFF3390EC), badgeMuted = Color(0xFFC4C9CC), field = LightField, isDark = false,
)

private val DarkExtras = TalksyExtras(
    bubbleIn = Color(0xFF182533), bubbleOut = Color(0xFF2B5278),
    onBubbleIn = Color(0xFFE9EEF4), onBubbleOut = Color(0xFFFFFFFF),
    metaIn = Color(0xFF6D8296), metaOut = Color(0xFF8FB4D9),
    wallpaperTop = Color(0xFF0E1621), wallpaperBottom = Color(0xFF111D2A),
    tick = Color(0xFF8FD1FF), link = Color(0xFF6AB7FF), online = Color(0xFF4FC977),
    badge = Color(0xFF3D9BE0), badgeMuted = Color(0xFF4A5A6A), field = DarkField, isDark = true,
)

private val LocalExtras = staticCompositionLocalOf { LightExtras }

val MaterialTheme.extras: TalksyExtras
    @Composable @ReadOnlyComposable get() = LocalExtras.current

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF3390EC), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFB), onPrimaryContainer = Color(0xFF0B4A8A),
    secondary = Cyan, tertiary = Mint,
    background = LightBg, onBackground = LightOnSurface,
    surface = LightSurface, onSurface = LightOnSurface,
    surfaceVariant = LightField, onSurfaceVariant = LightMuted,
    surfaceContainer = LightSurface, surfaceContainerHigh = LightSurface,
    outline = LightOutline, outlineVariant = LightOutline, error = Rose,
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF5EB5F7), onPrimary = Color(0xFF06223A),
    primaryContainer = Color(0xFF1E3A56), onPrimaryContainer = Color(0xFFCFE6FB),
    secondary = Cyan, tertiary = Mint,
    background = DarkBg, onBackground = DarkOnSurface,
    surface = DarkSurface, onSurface = DarkOnSurface,
    surfaceVariant = DarkField, onSurfaceVariant = DarkMuted,
    surfaceContainer = DarkSurface, surfaceContainerHigh = DarkSurface,
    outline = DarkOutline, outlineVariant = DarkOutline, error = Color(0xFFFF6B75),
)

/** @param mode "system" | "light" | "dark" */
@Composable
fun TalksyTheme(mode: String = "system", content: @Composable () -> Unit) {
    val dark = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalExtras provides (if (dark) DarkExtras else LightExtras)) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = TalksyTypography,
            content = content,
        )
    }
}
