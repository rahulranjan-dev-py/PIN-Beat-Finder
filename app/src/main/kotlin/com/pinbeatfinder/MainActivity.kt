package com.pinbeatfinder

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.Settings
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.pinbeatfinder.data.prefs.ThemeMode
import com.pinbeatfinder.ui.MainScreen
import com.pinbeatfinder.ui.theme.LocalHapticsEnabled
import com.pinbeatfinder.ui.theme.LocalReduceMotion
import com.pinbeatfinder.data.prefs.LocaleSupport
import com.pinbeatfinder.ui.theme.PinBeatFinderTheme

/** AppCompatActivity (not ComponentActivity) so per-app language selection works on every API level. */
class MainActivity : AppCompatActivity() {
    /** A launcher-shortcut request ("online" / "add") waiting for the UI to act on it. */
    private var startAction by mutableStateOf<String?>(null)
    /** Mirrors the system "remove animations" setting; re-read on every resume. */
    private var reduceMotion by mutableStateOf(false)

    /** The activity's own resources follow the saved language from the first frame. */
    override fun attachBaseContext(newBase: Context) {
        migrateStoredLocale(newBase)
        super.attachBaseContext(LocaleSupport.wrapBase(newBase, newBase.appContainer.appSettingsRepository.settings.value.language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // No coloured app bar any more: status-bar icons follow the theme (light on dark, dark on light).
        applySystemBars(isDarkTheme())
        super.onCreate(savedInstanceState)
        // AppCompat only knows the platform-stored locale once its delegate is attached; retry here.
        migrateStoredLocale(this)
        applyWindowBackground()
        startAction = intent?.getStringExtra(SHORTCUT_EXTRA)
        reduceMotion = readReduceMotion()
        setContent {
            val settings by appContainer.appSettingsRepository.settings.collectAsStateWithLifecycle()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Changing the theme in Settings recomposes without recreating: restyle the bars too.
            LaunchedEffect(darkTheme) { applySystemBars(darkTheme) }
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
                        LocalReduceMotion provides reduceMotion,
                    ) {
                        MainScreen(startAction = startAction, onStartActionConsumed = { startAction = null })
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

    /** singleTop: a shortcut tapped while the app is open arrives here instead of in a new activity. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(SHORTCUT_EXTRA)?.let { startAction = it }
    }

    /** Every return to the app re-checks for a newer release (throttled inside the checker). */
    override fun onResume() {
        super.onResume()
        reduceMotion = readReduceMotion()
        lifecycleScope.launch {
            val c = appContainer
            if (c.connectivity.isOnline()) c.updateChecker.checkOnForeground()
        }
    }

    /** Whether the app is dark right now: the forced setting, or the system when set to follow it. */
    private fun isDarkTheme(): Boolean {
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return when (appContainer.appSettingsRepository.settings.value.themeMode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    }

    /** True when the user has set the animator duration scale to 0 (Accessibility → Remove animations). */
    private fun readReduceMotion(): Boolean =
        runCatching { Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)

    /** Transparent edge-to-edge bars with light icons on the dark theme and dark icons on the light one. */
    private fun applySystemBars(dark: Boolean) {
        val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    /**
     * The theme's windowBackground follows the system day/night setting, but the app can be
     * forced light or dark in Settings. Paint the window to match the Compose surface so the
     * frame between activity creation and the first composition is never a different colour.
     */
    private fun applyWindowBackground() {
        val settings = appContainer.appSettingsRepository.settings.value
        val dark = isDarkTheme()
        val colour = when {
            settings.highContrast && dark -> Color.BLACK
            settings.highContrast -> Color.WHITE
            dark -> 0xFF14161A.toInt()
            else -> 0xFFF7F7F5.toInt()
        }
        window.setBackgroundDrawable(ColorDrawable(colour))
    }

    companion object {
        /** Intent extra set by res/xml/shortcuts.xml: "online" or "add". */
        const val SHORTCUT_EXTRA = "pbf.shortcut"
    }
}
