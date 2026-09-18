package com.pinbeatfinder

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.pinbeatfinder.data.prefs.ThemeMode
import com.pinbeatfinder.ui.MainScreen
import com.pinbeatfinder.ui.theme.LocalHapticsEnabled
import com.pinbeatfinder.ui.theme.PinBeatFinderTheme

/** AppCompatActivity (not ComponentActivity) so per-app language selection works on every API level. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The app bar is always post-box red, so status-bar icons must always be light.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        applyWindowBackground()
        setContent {
            val settings by appContainer.appSettingsRepository.settings.collectAsStateWithLifecycle()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            PinBeatFinderTheme(darkTheme = darkTheme, highContrast = settings.highContrast) {
                val base = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(base.density, base.fontScale * settings.textScale.factor),
                    LocalHapticsEnabled provides settings.hapticsEnabled,
                ) {
                    MainScreen()
                }
            }
        }
    }

    /** Every return to the app re-checks for a newer release (throttled inside the checker). */
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val c = appContainer
            if (c.connectivity.isOnline()) c.updateChecker.checkOnForeground()
        }
    }

    /**
     * The theme's windowBackground follows the system day/night setting, but the app can be
     * forced light or dark in Settings. Paint the window to match the Compose surface so the
     * frame between activity creation and the first composition is never a different colour.
     */
    private fun applyWindowBackground() {
        val settings = appContainer.appSettingsRepository.settings.value
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val dark = when (settings.themeMode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        val colour = when {
            settings.highContrast && dark -> Color.BLACK
            settings.highContrast -> Color.WHITE
            dark -> 0xFF1A1110.toInt()
            else -> 0xFFFFF8F7.toInt()
        }
        window.setBackgroundDrawable(ColorDrawable(colour))
    }
}
