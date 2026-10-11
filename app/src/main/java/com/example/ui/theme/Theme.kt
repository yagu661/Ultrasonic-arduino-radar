package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RadarDarkColorScheme = darkColorScheme(
    primary = RadarGreenBright,
    onPrimary = Color.Black,
    primaryContainer = RadarSurfaceElevated,
    onPrimaryContainer = RadarGreenBright,
    secondary = RadarGreenSecondary,
    onSecondary = Color.Black,
    secondaryContainer = RadarBorderGreen,
    onSecondaryContainer = TextPhosphorWhite,
    tertiary = RadarCyanInfo,
    onTertiary = Color.Black,
    background = RadarBlack,
    onBackground = TextPhosphorWhite,
    surface = RadarSurfaceDark,
    onSurface = TextPhosphorWhite,
    surfaceVariant = RadarSurfaceElevated,
    onSurfaceVariant = TextPhosphorMuted,
    error = RadarRedDetection,
    onError = Color.White,
    outline = RadarBorderGreen
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Ultrasonic Radar intentionally uses a tactical high-contrast phosphor green-on-black palette
    MaterialTheme(
        colorScheme = RadarDarkColorScheme,
        typography = Typography,
        content = content
    )
}
