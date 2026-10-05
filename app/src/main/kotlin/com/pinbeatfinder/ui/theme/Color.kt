package com.pinbeatfinder.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * "Night Mail" palette. Dark is the reference design; light is derived from it, and both
 * high-contrast schemes push surfaces to pure black/white and text to full strength.
 *
 * Role mapping used throughout the app:
 *   surface / background      screen background
 *   surfaceContainer*         cards, sheets, dialogs, menus
 *   tertiary                  PIN accent (amber)
 *   secondary                 counts ("1 beat · 9 villages")
 *   secondaryContainer        active navigation pill, selected chips
 *   primaryContainer          the filled "primary button" (#C62F25 / #B3261E, white content)
 *   primary                   links, text buttons, selection controls (a readable red per scheme)
 *   outline / outlineVariant  thin outlines / dividers
 */

// ---- dark (reference) -----------------------------------------------------------------------
val NightBackground = Color(0xFF14161A)
val NightCard = Color(0xFF1E2127)
val NightBottomBar = Color(0xFF191C21)
val NightOutline = Color(0xFF3A3F4A)
val NightDivider = Color(0xFF2A2E36)
val NightText = Color(0xFFECEDEF)
val NightTextSecondary = Color(0xFFA6ABB3)
val NightPin = Color(0xFFF2B632)
val NightCounts = Color(0xFFFF9A8B)
val NightPrimary = Color(0xFFC62F25)
val NightNavPill = Color(0xFF4A1D19)
val NightOnNavPill = Color(0xFFFFB4A9)

// ---- light ----------------------------------------------------------------------------------
val DayBackground = Color(0xFFF7F7F5)
val DayCard = Color(0xFFFFFFFF)
val DaySecondarySurface = Color(0xFFECEDEF)
val DayText = Color(0xFF16181C)
val DayTextSecondary = Color(0xFF555A63)
val DayPin = Color(0xFF7A5600)
val DayCounts = Color(0xFFB3261E)
val DayPrimary = Color(0xFFB3261E)
val DayNavPill = Color(0xFFFFDAD6)
val DayOnNavPill = Color(0xFF8C1D18)

val DarkColors: ColorScheme = darkColorScheme(
    primary = NightOnNavPill,          // #C62F25 reads at only 2.9:1 on the card; text uses this light red
    onPrimary = Color(0xFF3A0B06),
    primaryContainer = NightPrimary,   // the filled button
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = NightCounts,
    onSecondary = Color(0xFF3A0B06),
    secondaryContainer = NightNavPill,
    onSecondaryContainer = NightOnNavPill,
    tertiary = NightPin,
    onTertiary = Color(0xFF2B2200),
    tertiaryContainer = Color(0xFF4A3A00),
    onTertiaryContainer = Color(0xFFFFE08A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = NightBackground,
    onBackground = NightText,
    surface = NightBackground,
    onSurface = NightText,
    surfaceVariant = NightDivider,
    onSurfaceVariant = NightTextSecondary,
    outline = NightOutline,
    outlineVariant = NightDivider,
    surfaceDim = NightBackground,
    surfaceBright = Color(0xFF2E333B),
    surfaceContainerLowest = Color(0xFF0F1114),
    surfaceContainerLow = NightCard,
    surfaceContainer = NightCard,
    surfaceContainerHigh = Color(0xFF262A31),
    surfaceContainerHighest = Color(0xFF2E333B),
    inverseSurface = NightText,
    inverseOnSurface = NightDivider,
    inversePrimary = DayPrimary,
    scrim = Color(0xFF000000),
)

val LightColors: ColorScheme = lightColorScheme(
    primary = DayPrimary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = DayPrimary,     // the filled button
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = DayCounts,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = DayNavPill,
    onSecondaryContainer = DayOnNavPill,
    tertiary = DayPin,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE08A),
    onTertiaryContainer = Color(0xFF261A00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = DayBackground,
    onBackground = DayText,
    surface = DayBackground,
    onSurface = DayText,
    surfaceVariant = DaySecondarySurface,
    onSurfaceVariant = DayTextSecondary,
    outline = Color(0xFFC3C6CC),
    outlineVariant = Color(0xFFE2E4E8),
    surfaceDim = Color(0xFFE6E6E4),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = DayCard,
    surfaceContainer = DayCard,
    surfaceContainerHigh = Color(0xFFF2F2F0),
    surfaceContainerHighest = DaySecondarySurface,
    inverseSurface = Color(0xFF2B2E33),
    inverseOnSurface = Color(0xFFF2F2F0),
    inversePrimary = Color(0xFFFFB4AB),
    scrim = Color(0xFF000000),
)

/* High contrast: pure black/white surfaces, text at full strength, outlines that read as lines. */
val DarkHighContrast: ColorScheme = DarkColors.copy(
    primary = Color(0xFFFFD9D4),
    onPrimary = Color(0xFF000000),
    primaryContainer = NightPrimary,
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFFFB7AA),
    secondaryContainer = Color(0xFF6B2A24),
    onSecondaryContainer = Color(0xFFFFD9D4),
    tertiary = Color(0xFFFFCC55),
    tertiaryContainer = Color(0xFF6E5A00),
    onTertiaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2A2E36),
    onSurfaceVariant = Color(0xFFD9DCE2),
    outline = Color(0xFFFFFFFF),
    outlineVariant = Color(0xFF6F747E),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF15181D),
    surfaceContainer = Color(0xFF15181D),
    surfaceContainerHigh = Color(0xFF1F2329),
    surfaceContainerHighest = Color(0xFF2A2E36),
)

val LightHighContrast: ColorScheme = LightColors.copy(
    primary = Color(0xFF8C1D18),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF8C1D18),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF8C1D18),
    secondaryContainer = Color(0xFFFFB4A9),
    onSecondaryContainer = Color(0xFF410002),
    tertiary = Color(0xFF4E3700),
    tertiaryContainer = Color(0xFFFFD54A),
    onTertiaryContainer = Color(0xFF000000),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFE6E6E6),
    onSurfaceVariant = Color(0xFF2B2E33),
    outline = Color(0xFF000000),
    outlineVariant = Color(0xFF8A8A8A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F2F0),
    surfaceContainer = Color(0xFFF2F2F0),
    surfaceContainerHigh = Color(0xFFE6E6E6),
    surfaceContainerHighest = Color(0xFFDADADA),
)

/** Colours the Material scheme has no role for. */
@Immutable
data class ExtraColors(
    /** Bottom navigation bar container. */
    val bottomBar: Color,
    /** Border drawn around cards and the search field; null when the surfaces separate on their own. */
    val cardBorder: Color?,
)

val NightExtra = ExtraColors(bottomBar = NightBottomBar, cardBorder = null)
val DayExtra = ExtraColors(bottomBar = DaySecondarySurface, cardBorder = null)
val NightHighContrastExtra = ExtraColors(bottomBar = Color(0xFF0E1013), cardBorder = Color(0xFF6F747E))
val DayHighContrastExtra = ExtraColors(bottomBar = Color(0xFFE6E6E6), cardBorder = Color(0xFF000000))

val LocalExtraColors = staticCompositionLocalOf { NightExtra }
