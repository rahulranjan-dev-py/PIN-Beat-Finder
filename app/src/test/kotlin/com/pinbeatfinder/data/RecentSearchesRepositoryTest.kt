package com.pinbeatfinder.data

import com.pinbeatfinder.data.prefs.KeyValueStore
import com.pinbeatfinder.data.prefs.RecentSearchesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentSearchesRepositoryTest {
    private class MemoryStore : KeyValueStore {
        val map = HashMap<String, String>()
        override fun read(key: String) = map[key]
        override fun write(key: String, value: String) { map[key] = value }
    }

    @Test
    fun `records are most-recent first, merged case-insensitively, and persisted`() {
        val store = MemoryStore()
        val repo = RecentSearchesRepository(store)
        repo.record("Rampur", now = 1)
        repo.record("110001", now = 2)
        repo.record("rampur", now = 3)
        assertEquals(listOf("Rampur", "110001"), repo.items.value.map { it.query })
        assertEquals(2, repo.items.value.first().useCount)

        val reloaded = RecentSearchesRepository(store)
        assertEquals(listOf("Rampur", "110001"), reloaded.items.value.map { it.query })
    }

    @Test
    fun `pinned entries stay on top and survive clear`() {
        val repo = RecentSearchesRepository(MemoryStore())
        repo.record("A", now = 1); repo.record("B", now = 2); repo.record("C", now = 3)
        repo.togglePin("A")
        assertEquals(listOf("A", "C", "B"), repo.items.value.map { it.query })
        repo.clearUnpinned()
        assertEquals(listOf("A"), repo.items.value.map { it.query })
        repo.togglePin("A")
        assertTrue(repo.items.value.none { it.pinned })
    }

    @Test
    fun `unpinned list is capped and remove works`() {
        val repo = RecentSearchesRepository(MemoryStore(), maxUnpinned = 3)
        (1..5).forEach { repo.record("q$it", now = it.toLong()) }
        assertEquals(listOf("q5", "q4", "q3"), repo.items.value.map { it.query })
        repo.remove("q4")
        assertEquals(listOf("q5", "q3"), repo.items.value.map { it.query })
    }

    @Test
    fun `corrupt storage is ignored`() {
        val store = MemoryStore().apply { map[RecentSearchesRepository.KEY] = "not json" }
        assertTrue(RecentSearchesRepository(store).items.value.isEmpty())
    }
}
