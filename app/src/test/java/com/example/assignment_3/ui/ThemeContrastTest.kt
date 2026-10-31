package com.example.assignment_3.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.assignment_3.ui.theme.DarkColorScheme
import com.example.assignment_3.ui.theme.LightColorScheme
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 2.1 AA: normal text needs a contrast ratio of at least 4.5:1. Checks every text/background
 * pairing the screens actually use, in both themes.
 */
class ThemeContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance() + 0.05, b.luminance() + 0.05).sortedDescending()
        return (light / dark).toDouble()
    }

    private fun textPairs(s: ColorScheme) = mapOf(
        "onSurface / surface" to (s.onSurface to s.surface),
        "onSurfaceVariant / surface" to (s.onSurfaceVariant to s.surface),
        "onSurface / card (surfaceContainerHighest)" to (s.onSurface to s.surfaceContainerHighest),
        "onSurfaceVariant / card" to (s.onSurfaceVariant to s.surfaceContainerHighest),
        "primary / surface" to (s.primary to s.surface),
        "primary / card" to (s.primary to s.surfaceContainerHighest),
        "error / surface" to (s.error to s.surface),
        "error / card" to (s.error to s.surfaceContainerHighest),
        "error / primaryContainer (dashboard summary)" to (s.error to s.primaryContainer),
        "onPrimary / primary" to (s.onPrimary to s.primary),
        "onPrimaryContainer / primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
        "onSecondaryContainer / secondaryContainer" to (s.onSecondaryContainer to s.secondaryContainer),
        "onTertiaryContainer / tertiaryContainer" to (s.onTertiaryContainer to s.tertiaryContainer),
        "onSurfaceVariant / surfaceVariant" to (s.onSurfaceVariant to s.surfaceVariant),
        "onErrorContainer / errorContainer" to (s.onErrorContainer to s.errorContainer),
    )

    private fun assertAllPass(name: String, scheme: ColorScheme) {
        val failures = textPairs(scheme)
            .mapValues { (_, pair) -> contrast(pair.first, pair.second) }
            .filterValues { it < 4.5 }
        assertTrue("$name theme pairs below 4.5:1: $failures", failures.isEmpty())
    }

    @Test
    fun lightTheme_textMeetsWcagAA() = assertAllPass("Light", LightColorScheme)

    @Test
    fun darkTheme_textMeetsWcagAA() = assertAllPass("Dark", DarkColorScheme)

    @Test
    fun contrastFormula_matchesKnownValues() {
        assertTrue(contrast(Color.Black, Color.White) in 20.9..21.1)
        assertTrue(contrast(Color.White, Color.White) in 0.99..1.01)
    }
}
