package com.pinbeatfinder

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import com.pinbeatfinder.data.prefs.LocaleSupport
import com.pinbeatfinder.ui.theme.PinBeatFinderTheme

/** AppCompatActivity (not ComponentActivity) so per-app language selection works on every API level. */
class MainActivity : AppCompatActivity() {
    /** The activity's own resources follow the saved language from the first frame. */
    override fun attachBaseContext(newBase: Context) {
        migrateStoredLocale(newBase)
        super.attachBaseContext(LocaleSupport.wrapBase(newBase, newBase.appContainer.appSettingsRepository.settings.value.language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app bar is always post-box red, so status-bar icons must always be light.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        // AppCompat only knows the platform-stored locale once its delegate is attached; retry here.
        migrateStoredLocale(this)
        applyWindowBackground()
        setContent {
            val settings by appContainer.appSettingsRepository.settings.collectAsStateWithLifecycle()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Language is applied here, in composition: changing it recomposes the strings in place.
            val activityContext = LocalContext.current
            val systemConfiguration = LocalConfiguration.current
            val localized = remember(settings.language, systemConfiguration) { LocaleSupport.wrapForCompose(activityContext, settings.language) }
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides localized.resources.configuration,
            ) {
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
    }

    /**
     * Versions up to 0.16.0 stored the language through AppCompat's per-app locale API. Copy it
     * into the setting once so nobody loses their choice; the platform value is left alone (the
     * in-app override always wins) and is no longer written.
     */
    private fun migrateStoredLocale(context: Context) {
        val repo = context.appContainer.appSettingsRepository
        if (repo.settings.value.language.isNotBlank()) return
        val stored = AppCompatDelegate.getApplicationLocales()
        if (stored.isEmpty) return
        val tag = stored.toLanguageTags().substringBefore(',').substringBefore('-')
        if (tag.isNotBlank()) repo.update { it.copy(language = tag) }
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
