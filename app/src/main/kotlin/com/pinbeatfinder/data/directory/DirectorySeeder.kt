package com.pinbeatfinder.data.directory

import android.content.Context
import androidx.room.withTransaction
import com.pinbeatfinder.data.prefs.KeyValueStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.zip.GZIPInputStream

sealed interface SeedState {
    data object NotStarted : SeedState
    data class Seeding(val progress: Float) : SeedState
    data class Ready(val rows: Int, val version: String) : SeedState
    data class Failed(val message: String) : SeedState
}

/**
 * Loads the bundled directory asset into [DirectoryDatabase] once per asset version.
 * Safe to call on every launch: it returns immediately when the stored version matches.
 */
class DirectorySeeder(
    context: Context,
    private val database: DirectoryDatabase,
    private val store: KeyValueStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val _state = MutableStateFlow<SeedState>(SeedState.NotStarted)
    val state: StateFlow<SeedState> = _state.asStateFlow()

    val isReady: Boolean get() = _state.value is SeedState.Ready

    suspend fun ensureSeeded() = withContext(ioDispatcher) {
        mutex.withLock {
            if (_state.value is SeedState.Ready) return@withLock
            try {
                val meta = DirectoryAsset.parseMeta(appContext.assets.open(DirectoryAsset.META_NAME).bufferedReader().readText())
                val dao = database.directoryDao()
                val seededVersion = store.read(KEY_VERSION)
                if (seededVersion == meta.version && dao.count() >= meta.rows) {
                    _state.value = SeedState.Ready(dao.count(), meta.version)
                    return@withLock
                }
                _state.value = SeedState.Seeding(0f)
                dao.deleteAll()
                var inserted = 0
                GZIPInputStream(appContext.assets.open(DirectoryAsset.TSV_NAME), 1 shl 16).bufferedReader(Charsets.UTF_8).use { reader ->
                    DirectoryAsset.readBatches(reader, BATCH) { batch ->
                        // readBatches is inline, so this suspends the seeding coroutine itself.
                        database.withTransaction { dao.insertAll(batch) }
                        inserted += batch.size
                        _state.value = SeedState.Seeding((inserted.toFloat() / meta.rows).coerceIn(0f, 0.99f))
                    }
                }
                store.write(KEY_VERSION, meta.version)
                _state.value = SeedState.Ready(inserted, meta.version)
            } catch (e: Exception) {
                _state.value = SeedState.Failed(e.message ?: e::class.simpleName.orEmpty())
            }
        }
    }

    companion object {
        const val KEY_VERSION = "india_post_directory_version"
        const val BATCH = 2000
    }
}
