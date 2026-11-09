package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AiServerColorScheme = darkColorScheme(
    primary = CyanNeon,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = CyanGlow,
    secondary = MintEmerald,
    onSecondary = Color(0xFF003918),
    secondaryContainer = Color(0xFF005326),
    onSecondaryContainer = MintGlow,
    tertiary = VioletAccent,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = DarkSurfaceHighlight,
    outlineVariant = Color(0xFF1E293B),
    error = RoseError,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use our sleek custom AI server palette
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AiServerColorScheme,
        typography = Typography,
        content = content
    )
}
