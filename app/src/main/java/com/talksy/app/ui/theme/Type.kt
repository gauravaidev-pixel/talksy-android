package com.talksy.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.talksy.app.R

/** Inter, the same typeface the web UI uses. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val TalksyTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Bold,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Bold,
        fontSize = 20.sp, lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 15.5.sp, lineHeight = 21.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 15.5.sp, lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 19.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp, lineHeight = 15.sp,
    ),
)
