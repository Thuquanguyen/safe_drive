package com.mobile.safedrive.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

val LocalSdColors = staticCompositionLocalOf { LightSdColors }

/** Tailwind radius scale used by the design. */
object Radius {
    val lg = RoundedCornerShape(8.dp)       // rounded-lg
    val xl = RoundedCornerShape(12.dp)      // rounded-xl
    val xl2 = RoundedCornerShape(16.dp)     // rounded-2xl
    val sheet = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)
}

object SdTheme {
    val colors: SdColors
        @Composable @ReadOnlyComposable get() = LocalSdColors.current
}

@Composable
fun SafeDriveTheme(darkMode: Boolean = false, content: @Composable () -> Unit) {
    val colors = if (darkMode) DarkSdColors else LightSdColors
    val material = if (darkMode) {
        darkColorScheme(primary = colors.primary, background = colors.background, surface = colors.background)
    } else {
        lightColorScheme(primary = colors.primary, background = colors.background, surface = colors.background)
    }
    CompositionLocalProvider(LocalSdColors provides colors) {
        MaterialTheme(colorScheme = material, typography = SdTypography, content = content)
    }
}
