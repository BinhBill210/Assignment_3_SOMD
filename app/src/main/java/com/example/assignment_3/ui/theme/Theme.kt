package com.example.assignment_3.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val LightColorScheme = lightColorScheme(
    primary = Honey40,
    onPrimary = Neutral99,
    primaryContainer = Honey90,
    onPrimaryContainer = Honey10,
    secondary = Slate40,
    secondaryContainer = Slate90,
    onSecondaryContainer = Slate10,
    tertiary = Teal40,
    tertiaryContainer = Teal90,
    onTertiaryContainer = Teal10,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = NeutralVariant90,
    onSurfaceVariant = NeutralVariant30,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF2EB),
    surfaceContainer = Color(0xFFF5ECE5),
    surfaceContainerHigh = Color(0xFFF0E6DF),
    surfaceContainerHighest = Neutral90,
    outline = Color(0xFF84746A),
    outlineVariant = Color(0xFFD7C3B6),
    inverseSurface = Color(0xFF352F2A),
    inverseOnSurface = Color(0xFFF9EFE7),
    inversePrimary = Honey80
)

internal val DarkColorScheme = darkColorScheme(
    primary = Honey80,
    onPrimary = Honey10,
    primaryContainer = Honey30,
    onPrimaryContainer = Honey90,
    secondary = Slate80,
    secondaryContainer = Slate30,
    onSecondaryContainer = Slate90,
    tertiary = Teal80,
    tertiaryContainer = Teal30,
    onTertiaryContainer = Teal90,
    background = Neutral6,
    onBackground = Neutral90,
    surface = Neutral6,
    onSurface = Neutral90,
    surfaceVariant = NeutralVariant20,
    onSurfaceVariant = NeutralVariant80,
    surfaceContainerLowest = Color(0xFF120D09),
    surfaceContainerLow = Color(0xFF201B16),
    surfaceContainer = Color(0xFF241F1A),
    surfaceContainerHigh = Color(0xFF2F2924),
    surfaceContainerHighest = Color(0xFF3A342E),
    outline = Color(0xFF9E8E82),
    outlineVariant = NeutralVariant30,
    inverseSurface = Neutral90,
    inverseOnSurface = Color(0xFF352F2A),
    inversePrimary = Honey40
)

/**
 * Dynamic colour is deliberately not used: unit colours are user-chosen identifiers, and a
 * wallpaper-derived scheme could make them clash with or blend into the surrounding UI.
 */
@Composable
fun StudyHiveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
