package io.github.gobi12b.reclaimlife.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The serif "second voice" for the greeting and the numbers that matter. One constant, so it can
 * fall back to [FontFamily.Default] in one line if an OEM's system serif looks off.
 */
val DisplayFamily: FontFamily = FontFamily.Serif

// Styles not listed keep Material 3 defaults; labelMedium stays default because the NavigationBar uses it.
val Typography = Typography(
    displayMedium = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 56.sp,
        lineHeight = 60.sp,
        letterSpacing = (-0.5).sp
    ),
    displaySmall = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 44.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    // Up from 12/16: notes and footers are read, not skimmed.
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    )
)
