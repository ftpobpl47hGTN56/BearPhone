package com.bearphone.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Палитра "медведь под северным сиянием"
val BearBlue = Color(0xFF3D6BFF)
val AuroraGreen = Color(0xFF2EE6B8)
val AuroraViolet = Color(0xFF8E6BFF)
val NightSky = Color(0xFF070B1F)
val NightSurface = Color(0xFF10173A)
val CallGreen = Color(0xFF22C55E)
val CallRed = Color(0xFFEF4444)

private val Dark = darkColorScheme(
    primary = BearBlue,
    onPrimary = Color.White,
    secondary = AuroraGreen,
    onSecondary = Color(0xFF00382A),
    tertiary = AuroraViolet,
    background = NightSky,
    onBackground = Color(0xFFE6E9FF),
    surface = NightSky,
    onSurface = Color(0xFFE6E9FF),
    surfaceVariant = NightSurface,
    onSurfaceVariant = Color(0xFFA9B0D6),
    surfaceContainer = NightSurface,
    surfaceContainerHigh = Color(0xFF172051),
)

private val Light = lightColorScheme(
    primary = Color(0xFF2B55E6),
    secondary = Color(0xFF00A884),
    tertiary = Color(0xFF6A4DE0),
)

@Composable
fun BearPhoneTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
