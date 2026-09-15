package com.pinbeatfinder.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Whether the user wants vibration feedback (Settings). */
val LocalHapticsEnabled = compositionLocalOf { true }

/** Returns a function that performs haptic feedback only when enabled in Settings. */
@Composable
fun rememberHaptic(): (HapticFeedbackType) -> Unit {
    val enabled = LocalHapticsEnabled.current
    val haptics = LocalHapticFeedback.current
    return { type -> if (enabled) haptics.performHapticFeedback(type) }
}
