package com.pinbeatfinder

import android.graphics.Color
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
        setContent {
            val settings by appContainer.appSettingsRepository.settings.collectAsStateWithLifecycle()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            PinBeatFinderTheme(darkTheme = darkTheme) {
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
}
