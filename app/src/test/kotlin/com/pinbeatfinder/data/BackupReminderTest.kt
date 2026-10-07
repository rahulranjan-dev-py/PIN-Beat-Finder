package com.pinbeatfinder.data

import com.pinbeatfinder.data.prefs.BackupReminder
import com.pinbeatfinder.data.prefs.KeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupReminderTest {
    private class MemoryStore : KeyValueStore {
        val map = HashMap<String, String>()
        override fun read(key: String) = map[key]
        override fun write(key: String, value: String) { map[key] = value }
    }

    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `never backed up asks after ten edits`() {
        var now = 1_000_000L
        val r = BackupReminder(MemoryStore()) { now }
        repeat(9) { r.recordEdits() }
        assertFalse(r.state.value.isDue(now))
        r.recordEdits()
        assertTrue(r.state.value.isDue(now))
    }

    @Test
    fun `a backup resets the count and a recent backup needs many edits`() {
        var now = 1_000_000L
        val r = BackupReminder(MemoryStore()) { now }
        r.recordEdits(30)
        r.recordBackup()
        assertEquals(0, r.state.value.editsSinceBackup)
        assertEquals(now, r.state.value.lastBackupAt)
        r.recordEdits(24)
        assertFalse(r.state.value.isDue(now))
        r.recordEdits()
        assertTrue(r.state.value.isDue(now))
    }

    @Test
    fun `an old backup with any edits is due, snooze hides it for a week`() {
        var now = 1_000_000L
        val r = BackupReminder(MemoryStore()) { now }
        r.recordBackup()
        r.recordEdits()
        now += 31 * day
        assertTrue(r.state.value.isDue(now))
        r.snooze()
        assertFalse(r.state.value.isDue(now))
        assertFalse(r.state.value.isDue(now + 6 * day))
        assertTrue(r.state.value.isDue(now + 8 * day))
    }

    @Test
    fun `state survives a restart through the store`() {
        val store = MemoryStore()
        var now = 5_000L
        BackupReminder(store) { now }.apply { recordBackup(); recordEdits(3) }
        val again = BackupReminder(store) { now }
        assertEquals(3, again.state.value.editsSinceBackup)
        assertEquals(5_000L, again.state.value.lastBackupAt)
        assertNull(again.state.value.snoozedUntil)
    }
}
