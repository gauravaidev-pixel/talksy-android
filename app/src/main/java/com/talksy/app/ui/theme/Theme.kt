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

/** Conversation-specific colours. */
data class ChatColors(
    val chatBackground: Color,
    val incomingBubble: Color,
    val incomingText: Color,
    val outgoingBubble: Color,
    val outgoingText: Color,
    val tick: Color,
    val accent: Color,
    val onAccent: Color,
    val panel: Color,
    val inputBg: Color,
    val t2: Color,
    val t3: Color,
    val badge: Color,
)

val NightChatColors = ChatColors(
    chatBackground = NightBg,
    incomingBubble = NightIn,
    incomingText = NightInText,
    outgoingBubble = NightOut,
    outgoingText = NightOutText,
    tick = NightTick,
    accent = NightAccent,
    onAccent = NightOnAccent,
    panel = NightPanel,
    inputBg = NightInput,
    t2 = NightT2,
    t3 = NightT3,
    badge = NightBadge,
)

val DayChatColors = ChatColors(
    chatBackground = DayChat,
    incomingBubble = DayIn,
    incomingText = DayInText,
    outgoingBubble = DayOut,
    outgoingText = DayOutText,
    tick = DayTick,
    accent = DayAccent,
    onAccent = DayOnAccent,
    panel = DayPanel,
    inputBg = DayInput,
    t2 = DayT2,
    t3 = DayT3,
    badge = DayBadge,
)

val LocalChatColors = staticCompositionLocalOf { NightChatColors }

private val NightScheme = darkColorScheme(
    primary = NightAccent, onPrimary = NightOnAccent,
    secondary = NightAccent, tertiary = Ok,
    background = NightBg, onBackground = NightT1,
    surface = NightPanel, onSurface = NightT1,
    surfaceVariant = NightPanel2, onSurfaceVariant = NightT2,
    outline = Color(0xFF2A3746), error = Danger,
)

private val DayScheme = lightColorScheme(
    primary = DayAccent, onPrimary = DayOnAccent,
    secondary = DayAccent, tertiary = Ok,
    background = DayBg, onBackground = DayT1,
    surface = DayPanel, onSurface = DayT1,
    surfaceVariant = DayPanel2, onSurfaceVariant = DayT2,
    outline = Color(0xFFE3E6E8), error = Danger,
)

@Composable
fun TalksyTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = if (darkTheme) NightScheme else DayScheme
    val chat = if (darkTheme) NightChatColors else DayChatColors
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
