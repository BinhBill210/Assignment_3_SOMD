package com.example.assignment_3.ui.theme

import androidx.compose.ui.graphics.Color

// Honey (primary), slate (secondary) and teal (tertiary) tonal palettes. Containers are set
// explicitly; otherwise Material falls back to its baseline purple for every container colour.
val Honey10 = Color(0xFF2C1600)
val Honey40 = Color(0xFF8A5100)
val Honey80 = Color(0xFFFFB866)
val Honey90 = Color(0xFFFFDDB8)
val Honey30 = Color(0xFF693C00)

val Slate10 = Color(0xFF0B1C2B)
val Slate40 = Color(0xFF4F5B70)
val Slate80 = Color(0xFFBCC7DB)
val Slate90 = Color(0xFFD7E3F8)
val Slate30 = Color(0xFF374355)

val Teal10 = Color(0xFF00201C)
val Teal40 = Color(0xFF006B5F)
val Teal80 = Color(0xFF7FD8C9)
val Teal90 = Color(0xFF9EF2E2)
val Teal30 = Color(0xFF005047)

val Neutral99 = Color(0xFFFFFBF8)
val Neutral10 = Color(0xFF1F1B16)
val Neutral6 = Color(0xFF17130E)
val Neutral90 = Color(0xFFEAE1D9)
val NeutralVariant90 = Color(0xFFF2DFD1)
val NeutralVariant30 = Color(0xFF51443A)
val NeutralVariant80 = Color(0xFFD5C3B5)
val NeutralVariant20 = Color(0xFF3A2E25)

/**
 * The colours a user can pick for a unit. Only the index is stored in Room, so the palette can be
 * retuned without a migration. Mid-saturation tones stay legible in light and dark themes.
 */
val UnitPalette = listOf(
    Color(0xFFE57373),
    Color(0xFFFFB74D),
    Color(0xFFFFD54F),
    Color(0xFF81C784),
    Color(0xFF4DB6AC),
    Color(0xFF64B5F6),
    Color(0xFF9575CD),
    Color(0xFFF06292)
)

fun unitColor(index: Int): Color = UnitPalette[index.mod(UnitPalette.size)]
