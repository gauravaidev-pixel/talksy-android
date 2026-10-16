package com.talksy.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Colours used specifically by the conversation UI. */
data class ChatColors(
    val chatBackground: Color,
    val incomingBubble: Color,
    val incomingText: Color,
    val outgoingBubble: Color,
    val outgoingText: Color,
    val tick: Color,
)

val LightChatColors = ChatColors(
    chatBackground = LightChatBg,
    incomingBubble = LightIncoming,
    incomingText = LightOnSurface,
    outgoingBubble = LightOutgoing,
    outgoingText = Color.White,
    tick = Color(0xFFCDE9FB),
)

val DarkChatColors = ChatColors(
    chatBackground = DarkChatBg,
    incomingBubble = DarkIncoming,
    incomingText = DarkOnSurface,
    outgoingBubble = DarkOutgoing,
    outgoingText = Color(0xFFEAF1F8),
    tick = Color(0xFF7FB6DD),
)

val LocalChatColors = staticCompositionLocalOf { LightChatColors }

private val LightScheme = lightColorScheme(
    primary = Blue, onPrimary = Color.White,
    secondary = BlueDeep, tertiary = Mint,
    background = LightBg, onBackground = LightOnSurface,
    surface = LightSurface, onSurface = LightOnSurface,
    surfaceVariant = Color(0xFFF1F3F6), onSurfaceVariant = LightMuted,
    outline = LightOutline, error = Rose,
)

private val DarkScheme = darkColorScheme(
    primary = Blue, onPrimary = Color.White,
    secondary = BlueDeep, tertiary = Mint,
    background = DarkBg, onBackground = DarkOnSurface,
    surface = DarkSurface, onSurface = DarkOnSurface,
    surfaceVariant = DarkSurface, onSurfaceVariant = DarkMuted,
    outline = DarkOutline, error = Rose,
)

@Composable
fun TalksyTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val chat = if (darkTheme) DarkChatColors else LightChatColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = scheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    CompositionLocalProvider(LocalChatColors provides chat) {
        MaterialTheme(colorScheme = scheme, typography = TalksyTypography, content = content)
    }
}
