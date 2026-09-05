package com.us.copilot.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

private val defaultFont = FontFamily.Default

private val readableLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/** M3 Expressive-leaning scale: larger display sizes, generous line height for long reflections. */
val UsTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = defaultFont, fontWeight = FontWeight.Bold,
        fontSize = 54.sp, lineHeight = 62.sp, letterSpacing = (-0.5).sp,
        lineHeightStyle = readableLineHeight,
    ),
    displayMedium = TextStyle(
        fontFamily = defaultFont, fontWeight = FontWeight.Bold,
        fontSize = 42.sp, lineHeight = 50.sp, letterSpacing = (-0.25).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = defaultFont, fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp, lineHeight = 42.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = defaultFont, fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp, lineHeight = 38.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = defaultFont, fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp, lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = defaultFont, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = defaultFont, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = defaultFont, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = defaultFont, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = defaultFont, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 26.sp, letterSpacing = 0.5.sp,
        lineHeightStyle = readableLineHeight,
    ),
    bodyMedium = TextStyle(
        fontFamily = defaultFont, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = defaultFont, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = defaultFont, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = defaultFont, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = defaultFont, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
    ),
)
