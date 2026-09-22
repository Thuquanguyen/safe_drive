package com.mobile.safedrive.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Tailwind type scale (font-size / line-height) used by the Stitch screens.
 * Design font is Inter / system sans; Android renders it with the platform sans.
 */
object TextSize {
    val xxs = 10.sp      // text-[10px] / text-xxs
    val xs = 12.sp       // text-xs
    val sm = 14.sp       // text-sm
    val s15 = 15.sp      // text-[15px]
    val base = 16.sp     // text-base
    val lg = 18.sp       // text-lg
    val xl = 20.sp       // text-xl
    val xl2 = 24.sp      // text-2xl
    val xl3 = 30.sp      // text-3xl
    val xl4 = 36.sp      // text-4xl
}

private fun lineHeightFor(size: TextUnit): TextUnit = when (size.value) {
    10f -> 14.sp
    12f -> 16.sp
    14f -> 20.sp
    15f -> 22.sp
    16f -> 24.sp
    18f -> 28.sp
    20f -> 28.sp
    24f -> 32.sp
    30f -> 36.sp
    else -> (size.value * 1.25f).sp
}

fun sdText(
    size: TextUnit,
    weight: FontWeight = FontWeight.Normal,
    lineHeight: TextUnit = lineHeightFor(size),
    letterSpacing: TextUnit = 0.em,
) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = size,
    fontWeight = weight,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing,
)

val SdTypography = Typography(
    bodyLarge = sdText(TextSize.base),
    bodyMedium = sdText(TextSize.sm),
    bodySmall = sdText(TextSize.xs),
)
