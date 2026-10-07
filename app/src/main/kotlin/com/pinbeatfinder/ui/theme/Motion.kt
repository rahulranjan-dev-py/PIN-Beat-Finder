package com.pinbeatfinder.ui.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * True when the user has turned animations off in the system accessibility settings (animator
 * duration scale 0). Screens then skip their own slide/fade animations and switch states directly.
 */
val LocalReduceMotion = compositionLocalOf { false }
