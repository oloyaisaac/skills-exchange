package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = SignalBlue,
    onPrimary = Color.White,
    primaryContainer = SignalBlueLight,
    onPrimaryContainer = SignalBlueDark,
    secondary = IndustrialEmerald,
    onSecondary = Color.White,
    secondaryContainer = IndustrialEmeraldLight,
    onSecondaryContainer = IndustrialEmeraldDark,
    tertiary = AmberUrgent,
    onTertiary = Color.White,
    background = Slate50,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate700,
    outline = Slate200,
    outlineVariant = Slate300
)

private val DarkColorScheme = darkColorScheme(
    primary = SignalBlue,
    onPrimary = Color.White,
    primaryContainer = Slate800,
    onPrimaryContainer = SignalBlueLight,
    secondary = IndustrialEmerald,
    onSecondary = Color.White,
    secondaryContainer = Slate800,
    onSecondaryContainer = IndustrialEmeraldLight,
    tertiary = AmberUrgent,
    onTertiary = Color.White,
    background = Slate900,
    onBackground = Color.White,
    surface = Slate800,
    onSurface = Color.White,
    surfaceVariant = Slate700,
    onSurfaceVariant = Slate200,
    outline = Slate700,
    outlineVariant = Slate600
)

@Composable
fun SkillsExchangeTheme(
    themeMode: String = "LIGHT",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme()
    }
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}

