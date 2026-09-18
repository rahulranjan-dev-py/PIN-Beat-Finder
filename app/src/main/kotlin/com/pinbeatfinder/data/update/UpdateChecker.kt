package com.pinbeatfinder.data.update

import com.pinbeatfinder.data.prefs.KeyValueStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

data class ReleaseInfo(
    val tag: String,
    val pageUrl: String,
    val apkUrl: String?,
    val notes: String,
    /** `SHA256SUMS.txt` asset of the release, when published. */
    val checksumsUrl: String? = null,
)

data class UpdateState(
    val checking: Boolean = false,
    /** Newest release that is newer than the installed version, if any. */
    val available: ReleaseInfo? = null,
    val lastCheckedAt: Long? = null,
    val error: String? = null,
    /** Tag the user dismissed; the banner stays hidden for that version. */
    val dismissedTag: String? = null,
    /** Tag the user postponed with "Later"; hidden until the app next comes to the foreground. */
    val snoozedTag: String? = null,
) {
    val shouldShowBanner: Boolean get() = available != null && available.tag != dismissedTag && available.tag != snoozedTag
}

/**
 * Sideloaded apps have no store to tell users about updates, so the app asks the repository's
 * GitHub Releases feed itself (public, unauthenticated). Pre-releases count: every pilot build
 * is one. Checks are throttled to once per [minIntervalMs] unless forced.
 */
class UpdateChecker(
    private val currentVersion: String,
    private val store: KeyValueStore,
    private val fetchReleases: suspend () -> JsonElement,
    private val clock: () -> Long = System::currentTimeMillis,
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
) {
    private val _state = MutableStateFlow(
        UpdateState(
            lastCheckedAt = store.read(KEY_LAST_CHECK)?.toLongOrNull(),
            dismissedTag = store.read(KEY_DISMISSED),
        ),
    )
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Returns the newer release if one exists. Skips the network when checked recently unless [force]. */
    suspend fun check(force: Boolean = false): ReleaseInfo? {
        val last = _state.value.lastCheckedAt
        if (!force && last != null && clock() - last < minIntervalMs) return _state.value.available
        _state.update { it.copy(checking = true, error = null) }
        return try {
            val newest = parseReleases(fetchReleases()).firstOrNull { VersionCompare.isNewer(it.tag, currentVersion) }
            val now = clock()
            store.write(KEY_LAST_CHECK, now.toString())
            _state.update { it.copy(checking = false, available = newest, lastCheckedAt = now) }
            newest
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(checking = false, error = e.message ?: e::class.simpleName) }
            null
        }
    }

    fun dismiss(tag: String) {
        store.write(KEY_DISMISSED, tag)
        _state.update { it.copy(dismissedTag = tag) }
    }

    /** Hide the banner for now; [checkOnForeground] brings it back on the next app open. */
    fun later(tag: String) { _state.update { it.copy(snoozedTag = tag) } }

    /**
     * Called every time the app comes to the foreground: clears a "Later" and re-checks, throttled
     * to once per [FOREGROUND_MIN_INTERVAL_MS] so flicking between apps does not hammer GitHub.
     */
    suspend fun checkOnForeground(): ReleaseInfo? {
        _state.update { it.copy(snoozedTag = null) }
        val last = _state.value.lastCheckedAt
        if (last != null && clock() - last < FOREGROUND_MIN_INTERVAL_MS) return _state.value.available
        return check(force = true)
    }

    companion object {
        const val KEY_LAST_CHECK = "update_last_check"
        const val KEY_DISMISSED = "update_dismissed_tag"
        const val DEFAULT_MIN_INTERVAL_MS = 6L * 60 * 60 * 1000
        const val FOREGROUND_MIN_INTERVAL_MS = 60L * 1000

        /** Newest first, drafts skipped. Tolerates missing fields. */
        fun parseReleases(root: JsonElement): List<ReleaseInfo> {
            val arr = root as? JsonArray ?: return emptyList()
            return arr.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                if (o["draft"]?.jsonPrimitive?.booleanOrNull == true) return@mapNotNull null
                val tag = (o["tag_name"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                val assetUrls = (o["assets"] as? JsonArray)
                    ?.mapNotNull { a -> ((a as? JsonObject)?.get("browser_download_url") as? JsonPrimitive)?.content }
                    .orEmpty()
                ReleaseInfo(
                    tag = tag,
                    pageUrl = (o["html_url"] as? JsonPrimitive)?.content.orEmpty(),
                    apkUrl = assetUrls.firstOrNull { it.endsWith(".apk", ignoreCase = true) },
                    notes = (o["body"] as? JsonPrimitive)?.content.orEmpty(),
                    checksumsUrl = assetUrls.firstOrNull { it.endsWith("SHA256SUMS.txt", ignoreCase = true) },
                )
            }.sortedWith { a, b -> VersionCompare.compare(b.tag, a.tag) }
        }
    }
}
