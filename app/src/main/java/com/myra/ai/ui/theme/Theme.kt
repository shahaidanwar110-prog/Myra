package com.myra.ai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = GoldPrimary,
    onPrimary = Color.Black,
    primaryContainer = PrimaryPurpleVariant,
    onPrimaryContainer = GoldLight,
    secondary = PrimaryPurple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF240046),
    onSecondaryContainer = VioletAccent,
    tertiary = GoldVariant,
    background = DarkBackground,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = GlassBorderDark,
    error = ErrorRed,
    errorContainer = ErrorContainerDark,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0AAFF),
    onPrimaryContainer = Color(0xFF240046),
    secondary = GoldPrimary,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFFFFF8E7),
    onSecondaryContainer = Color(0xFF5B4000),
    tertiary = VioletAccent,
    background = LightBackground,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = GlassBorderLight,
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun MyraTheme(
    darkTheme: Boolean = true, // Dark theme by default
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
