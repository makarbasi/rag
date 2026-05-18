package com.qualcomm.rag.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Inter is bundled as a downloadable font via Google Fonts in XML (res/font).
// For simplicity in a demo, we fall back to the system default sans-serif.
val RagTypography = Typography(
    displayLarge = TextStyle(
        fontFamily   = FontFamily.SansSerif,
        fontWeight   = FontWeight.Bold,
        fontSize     = 32.sp,
        lineHeight   = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily   = FontFamily.SansSerif,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 22.sp,
        lineHeight   = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily   = FontFamily.SansSerif,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 18.sp,
        lineHeight   = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily   = FontFamily.SansSerif,
        fontWeight   = FontWeight.Medium,
        fontSize     = 15.sp,
        lineHeight   = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily   = FontFamily.SansSerif,
        fontWeight   = FontWeight.Normal,
        fontSize     = 15.sp,
        lineHeight   = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily   = FontFamily.SansSerif,
        fontWeight   = FontWeight.Normal,
        fontSize     = 13.sp,
        lineHeight   = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily   = FontFamily.SansSerif,
        fontWeight   = FontWeight.Normal,
        fontSize     = 11.sp,
        lineHeight   = 16.sp,
    ),
    labelMedium = TextStyle(
        fontFamily   = FontFamily.SansSerif,
        fontWeight   = FontWeight.Medium,
        fontSize     = 12.sp,
        lineHeight   = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)