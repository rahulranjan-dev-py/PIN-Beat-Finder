package com.pinbeatfinder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/*
 * "Night Mail": dark is the reference design, light is derived from it, and the two
 * high-contrast schemes are for bright sunlight. Colours live in Color.kt, type in Type.kt.
 */

/** Corner sizes: 8 dp fields and chips, 12 dp small cards, 20 dp cards, 28 dp sheets and dialogs. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun PinBeatFinderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    highContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        highContrast && darkTheme -> DarkHighContrast
        highContrast -> LightHighContrast
        darkTheme -> DarkColors
        else -> LightColors
    }
    val extra = when {
        highContrast && darkTheme -> NightHighContrastExtra
        highContrast -> DayHighContrastExtra
        darkTheme -> NightExtra
        else -> DayExtra
    }
    // The composition's configuration already carries the in-app language (see MainActivity).
    val devanagari = LocalConfiguration.current.locales[0]?.language == "hi"
    val typography = remember(devanagari) { appTypography(if (devanagari) AnekDevanagari else AnekLatin, devanagari) }

    CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(colorScheme = colorScheme, typography = typography, shapes = AppShapes, content = content)
    }
}
