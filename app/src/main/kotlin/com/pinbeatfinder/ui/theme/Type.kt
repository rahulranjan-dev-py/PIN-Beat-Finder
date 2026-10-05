package com.pinbeatfinder.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pinbeatfinder.R

/*
 * Anek (EkType, SIL Open Font License 1.1): the Latin family for English and the Devanagari
 * family, which also carries Latin glyphs, for Hindi. Static instances at 400/500/600/700,
 * normal width, built from the variable fonts published on Google Fonts.
 */
val AnekLatin = FontFamily(
    Font(R.font.anek_latin_regular, FontWeight.Normal),
    Font(R.font.anek_latin_medium, FontWeight.Medium),
    Font(R.font.anek_latin_semibold, FontWeight.SemiBold),
    Font(R.font.anek_latin_bold, FontWeight.Bold),
)

val AnekDevanagari = FontFamily(
    Font(R.font.anek_devanagari_regular, FontWeight.Normal),
    Font(R.font.anek_devanagari_medium, FontWeight.Medium),
    Font(R.font.anek_devanagari_semibold, FontWeight.SemiBold),
    Font(R.font.anek_devanagari_bold, FontWeight.Bold),
)

/** Material type scale on the given family. Devanagari needs a little more line height. */
fun appTypography(family: FontFamily, devanagari: Boolean): Typography {
    val lh = if (devanagari) 1.15f else 1f
    fun style(size: Int, line: Int, weight: FontWeight, letterSpacing: Float = 0f) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (line * lh).sp,
        letterSpacing = letterSpacing.sp,
    )
    return Typography(
        displayLarge = style(57, 64, FontWeight.Normal, -0.25f),
        displayMedium = style(45, 52, FontWeight.Normal),
        displaySmall = style(36, 44, FontWeight.Normal),
        headlineLarge = style(32, 40, FontWeight.Bold),
        headlineMedium = style(28, 36, FontWeight.Bold),
        headlineSmall = style(24, 32, FontWeight.SemiBold),
        titleLarge = style(22, 28, FontWeight.SemiBold),
        titleMedium = style(16, 24, FontWeight.SemiBold, 0.15f),
        titleSmall = style(14, 20, FontWeight.SemiBold, 0.1f),
        bodyLarge = style(16, 24, FontWeight.Normal, 0.5f),
        bodyMedium = style(14, 20, FontWeight.Normal, 0.25f),
        bodySmall = style(12, 16, FontWeight.Normal, 0.4f),
        labelLarge = style(14, 20, FontWeight.Medium, 0.1f),
        labelMedium = style(12, 16, FontWeight.Medium, 0.5f),
        labelSmall = style(11, 16, FontWeight.Medium, 0.5f),
    )
}
