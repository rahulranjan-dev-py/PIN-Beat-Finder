package com.pinbeatfinder.data.prefs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class TextScale(val factor: Float) { NORMAL(1.0f), LARGE(1.2f) }
enum class DefaultTab { ONLINE, LOCAL }

@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val textScale: TextScale = TextScale.NORMAL,
    val defaultTab: DefaultTab = DefaultTab.ONLINE,
    /** Empty means "use the key compiled into BuildConfig". */
    val dataGovInApiKey: String = "",
    val hapticsEnabled: Boolean = true,
    /** Pure black/white surfaces with darker/lighter accents for sunlight readability. */
    val highContrast: Boolean = false,
    /** BCP-47 tag of the app language ("hi", "en"); blank follows the phone. */
    val language: String = "",
)

/** User preferences, persisted as one JSON blob. */
class AppSettingsRepository(
    private val store: KeyValueStore,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        runCatching { store.write(KEY, json.encodeToString(AppSettings.serializer(), next)) }
    }

    private fun load(): AppSettings =
        runCatching { store.read(KEY)?.let { json.decodeFromString(AppSettings.serializer(), it) } }
            .getOrNull() ?: AppSettings()

    companion object {
        const val KEY = "app_settings_v1"
    }
}
