package com.mobile.safedrive.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Raw Tailwind palette used by the Stitch design (exact hex values). */
object Palette {
    val White = Color(0xFFFFFFFF)

    val Gray50 = Color(0xFFF9FAFB)
    val Gray100 = Color(0xFFF3F4F6)
    val Gray200 = Color(0xFFE5E7EB)
    val Gray300 = Color(0xFFD1D5DB)
    val Gray400 = Color(0xFF9CA3AF)
    val Gray500 = Color(0xFF6B7280)
    val Gray600 = Color(0xFF4B5563)
    val Gray700 = Color(0xFF374151)
    val Gray800 = Color(0xFF1F2937)
    val Gray900 = Color(0xFF111827)

    val Slate50 = Color(0xFFF8FAFC)
    val Slate100 = Color(0xFFF1F5F9)
    val Slate500 = Color(0xFF64748B)
    val Slate600 = Color(0xFF475569)
    val Slate800 = Color(0xFF1E293B)

    val Blue50 = Color(0xFFEFF6FF)
    val Blue100 = Color(0xFFDBEAFE)
    val Blue200 = Color(0xFFBFDBFE)
    val Blue400 = Color(0xFF60A5FA)
    val Blue500 = Color(0xFF3B82F6)
    val Blue600 = Color(0xFF2563EB)
    val Blue700 = Color(0xFF1D4ED8)
    val Blue800 = Color(0xFF1E40AF)
    val Blue900 = Color(0xFF1E3A8A)
    val Blue950 = Color(0xFF172554)
    val Sky100 = Color(0xFFE0F2FE)
    val SkyTop = Color(0xFFF0F7FF)

    val Green50 = Color(0xFFF0FDF4)
    val Green500 = Color(0xFF22C55E)
    val Emerald50 = Color(0xFFECFDF5)
    val Emerald100 = Color(0xFFD1FAE5)
    val Emerald400 = Color(0xFF34D399)
    val Emerald700 = Color(0xFF047857)
    val Teal50 = Color(0xFFF0FDFA)
    val Teal500 = Color(0xFF14B8A6)

    val Red500 = Color(0xFFEF4444)
    val Orange400 = Color(0xFFFB923C)
    val Orange500 = Color(0xFFF97316)
}

/**
 * Semantic design tokens. Every screen reads colors from here so the optional
 * dark mode (Settings → Dark mode) swaps the whole UI consistently.
 */
@Immutable
data class SdColors(
    val background: Color,
    val pageMuted: Color,
    val surfaceMuted: Color,
    val card: Color,
    val border: Color,
    val borderStrong: Color,
    val track: Color,
    val ringTrack: Color,
    val textPrimary: Color,
    val textStrong: Color,
    val textBody: Color,
    val textMuted: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textOnPrimary: Color,
    val textOnPrimarySoft: Color,
    val primary: Color,
    val primaryPressed: Color,
    val primaryDeep: Color,
    val accent: Color,
    val primaryTint: Color,
    val primaryTint2: Color,
    val primaryTint3: Color,
    val success: Color,
    val successTint: Color,
    val emeraldTint: Color,
    val emeraldBorder: Color,
    val emeraldText: Color,
    val emeraldLine: Color,
    val tealTint: Color,
    val teal: Color,
    val danger: Color,
    val warning: Color,
    val warningSoft: Color,
    val slateTint: Color,
    val slateText: Color,
    val slateTextMuted: Color,
    val slateTextStrong: Color,
    val navInactive: Color,
    val dotInactive: Color,
    val homeIndicator: Color,
    val isDark: Boolean,
)

val LightSdColors = SdColors(
    background = Palette.White,
    pageMuted = Palette.Gray50.copy(alpha = 0.5f),
    surfaceMuted = Palette.Gray50,
    card = Palette.White,
    border = Palette.Gray100,
    borderStrong = Palette.Gray200,
    track = Palette.Gray200,
    ringTrack = Palette.Gray100,
    textPrimary = Palette.Gray900,
    textStrong = Palette.Gray800,
    textBody = Palette.Gray700,
    textMuted = Palette.Gray600,
    textSecondary = Palette.Gray500,
    textTertiary = Palette.Gray400,
    textOnPrimary = Palette.White,
    textOnPrimarySoft = Palette.Blue100,
    primary = Palette.Blue600,
    primaryPressed = Palette.Blue700,
    primaryDeep = Palette.Blue700,
    accent = Palette.Blue500,
    primaryTint = Palette.Blue50,
    primaryTint2 = Palette.Blue100,
    primaryTint3 = Palette.Blue200,
    success = Palette.Green500,
    successTint = Palette.Green50,
    emeraldTint = Palette.Emerald50,
    emeraldBorder = Palette.Emerald100,
    emeraldText = Palette.Emerald700,
    emeraldLine = Palette.Emerald400,
    tealTint = Palette.Teal50,
    teal = Palette.Teal500,
    danger = Palette.Red500,
    warning = Palette.Orange500,
    warningSoft = Palette.Orange400,
    slateTint = Palette.Slate100,
    slateText = Palette.Slate600,
    slateTextMuted = Palette.Slate500,
    slateTextStrong = Palette.Slate800,
    navInactive = Palette.Gray400,
    dotInactive = Palette.Gray300,
    homeIndicator = Palette.Gray300,
    isDark = false,
)

val DarkSdColors = SdColors(
    background = Color(0xFF0B1120),
    pageMuted = Color(0xFF0B1120),
    surfaceMuted = Palette.Gray900,
    card = Palette.Gray900,
    border = Palette.Gray800,
    borderStrong = Palette.Gray700,
    track = Palette.Gray700,
    ringTrack = Palette.Gray800,
    textPrimary = Palette.Gray50,
    textStrong = Palette.Gray100,
    textBody = Palette.Gray200,
    textMuted = Palette.Gray300,
    textSecondary = Palette.Gray400,
    textTertiary = Palette.Gray500,
    textOnPrimary = Palette.White,
    textOnPrimarySoft = Palette.Blue100,
    primary = Palette.Blue600,
    primaryPressed = Palette.Blue700,
    primaryDeep = Palette.Blue500,
    accent = Palette.Blue400,
    primaryTint = Palette.Blue950,
    primaryTint2 = Palette.Blue900,
    primaryTint3 = Palette.Blue800,
    success = Palette.Green500,
    successTint = Color(0xFF052E16),
    emeraldTint = Color(0xFF022C22),
    emeraldBorder = Color(0xFF065F46),
    emeraldText = Palette.Emerald400,
    emeraldLine = Palette.Emerald400,
    tealTint = Color(0xFF042F2E),
    teal = Palette.Teal500,
    danger = Palette.Red500,
    warning = Palette.Orange500,
    warningSoft = Palette.Orange400,
    slateTint = Palette.Slate800,
    slateText = Palette.Gray300,
    slateTextMuted = Palette.Gray400,
    slateTextStrong = Palette.Gray100,
    navInactive = Palette.Gray500,
    dotInactive = Palette.Gray600,
    homeIndicator = Palette.Gray600,
    isDark = true,
)
