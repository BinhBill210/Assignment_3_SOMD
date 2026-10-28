package com.example.assignment_3.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
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
    surface = Neutral99,
    surfaceVariant = NeutralVariant90,
    onSurfaceVariant = NeutralVariant30
)

private val DarkColorScheme = darkColorScheme(
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
    surface = Neutral6,
    onSurface = Neutral90,
    surfaceVariant = NeutralVariant20,
    onSurfaceVariant = NeutralVariant80
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
