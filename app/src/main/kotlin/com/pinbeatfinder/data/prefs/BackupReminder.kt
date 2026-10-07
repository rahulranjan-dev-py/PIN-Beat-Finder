package com.pinbeatfinder.data.prefs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Counts edits since the last backup so the app can nudge the user to save one. Stored in the
 * plain key-value store; a "Later" snoozes the nudge for a week.
 */
class BackupReminder(
    private val store: KeyValueStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Status(
        val editsSinceBackup: Int = 0,
        val lastBackupAt: Long? = null,
        val snoozedUntil: Long? = null,
    ) {
        /** True when a reminder should show now: enough unsaved edits, or an old backup with changes since. */
        fun isDue(now: Long): Boolean {
            if (editsSinceBackup <= 0) return false
            if (snoozedUntil != null && now < snoozedUntil) return false
            return when {
                lastBackupAt == null -> editsSinceBackup >= FIRST_BACKUP_EDITS
                editsSinceBackup >= EDIT_THRESHOLD -> true
                else -> now - lastBackupAt >= MAX_AGE_MS
            }
        }
    }

    private val _state = MutableStateFlow(
        Status(
            editsSinceBackup = store.read(KEY_EDITS)?.toIntOrNull() ?: 0,
            lastBackupAt = store.read(KEY_LAST_BACKUP)?.toLongOrNull(),
            snoozedUntil = store.read(KEY_SNOOZE)?.toLongOrNull(),
        ),
    )
    val state: StateFlow<Status> = _state.asStateFlow()

    /** Call after any write to the beat directory; [count] rows changed. */
    fun recordEdits(count: Int = 1) {
        if (count <= 0) return
        _state.update { it.copy(editsSinceBackup = it.editsSinceBackup + count) }
        store.write(KEY_EDITS, _state.value.editsSinceBackup.toString())
    }

    /** Call after a backup was shared or saved. */
    fun recordBackup() {
        val now = clock()
        _state.update { it.copy(editsSinceBackup = 0, lastBackupAt = now, snoozedUntil = null) }
        store.write(KEY_EDITS, "0")
        store.write(KEY_LAST_BACKUP, now.toString())
        store.write(KEY_SNOOZE, "")
    }

    /** Hide the nudge for a week. */
    fun snooze() {
        val until = clock() + SNOOZE_MS
        _state.update { it.copy(snoozedUntil = until) }
        store.write(KEY_SNOOZE, until.toString())
    }

    companion object {
        const val KEY_EDITS = "backup_edits_since"
        const val KEY_LAST_BACKUP = "backup_last_at"
        const val KEY_SNOOZE = "backup_snoozed_until"
        const val FIRST_BACKUP_EDITS = 10
        const val EDIT_THRESHOLD = 25
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
        const val SNOOZE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
