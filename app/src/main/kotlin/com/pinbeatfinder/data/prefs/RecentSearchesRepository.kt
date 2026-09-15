package com.pinbeatfinder.data.prefs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class RecentSearch(
    val query: String,
    val lastUsedAt: Long,
    val useCount: Int = 1,
    val pinned: Boolean = false,
)

/** Minimal persistence seam so the repository logic is testable without Android. */
interface KeyValueStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
}

/**
 * Recent + pinned online searches. Kept in a tiny JSON blob (SharedPreferences on Android)
 * instead of a Room table so adding the feature needs no database migration on devices that
 * already hold directory data.
 *
 * Ordering rule: pinned entries first, then the rest — both most-recently-used first. Unpinned
 * entries beyond [maxUnpinned] fall off the end.
 */
class RecentSearchesRepository(
    private val store: KeyValueStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val maxUnpinned: Int = DEFAULT_MAX_UNPINNED,
) {
    private val serializer = ListSerializer(RecentSearch.serializer())
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<RecentSearch>> = _items.asStateFlow()

    /** Records a successful search; merges with an existing entry of the same text (case-insensitive). */
    fun record(query: String, now: Long = System.currentTimeMillis()) {
        val q = query.trim()
        if (q.isEmpty()) return
        update { list ->
            val existing = list.firstOrNull { it.query.equals(q, ignoreCase = true) }
            val updated = existing?.copy(lastUsedAt = now, useCount = existing.useCount + 1)
                ?: RecentSearch(query = q, lastUsedAt = now)
            list.filterNot { it.query.equals(q, ignoreCase = true) } + updated
        }
    }

    fun togglePin(query: String) = update { list ->
        list.map { if (it.query.equals(query, ignoreCase = true)) it.copy(pinned = !it.pinned) else it }
    }

    fun remove(query: String) = update { list -> list.filterNot { it.query.equals(query, ignoreCase = true) } }

    fun clearUnpinned() = update { list -> list.filter { it.pinned } }

    private fun update(transform: (List<RecentSearch>) -> List<RecentSearch>) {
        val next = normalise(transform(_items.value))
        _items.value = next
        runCatching { store.write(KEY, json.encodeToString(serializer, next)) }
    }

    private fun normalise(list: List<RecentSearch>): List<RecentSearch> {
        val pinned = list.filter { it.pinned }.sortedByDescending { it.lastUsedAt }
        val recent = list.filterNot { it.pinned }.sortedByDescending { it.lastUsedAt }.take(maxUnpinned)
        return pinned + recent
    }

    private fun load(): List<RecentSearch> =
        runCatching { store.read(KEY)?.let { json.decodeFromString(serializer, it) } }
            .getOrNull()
            ?.let(::normalise)
            .orEmpty()

    companion object {
        const val KEY = "recent_searches_v1"
        const val DEFAULT_MAX_UNPINNED = 20
    }
}
