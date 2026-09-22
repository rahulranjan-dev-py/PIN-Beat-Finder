package com.pinbeatfinder.data.directory

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.pinbeatfinder.data.prefs.KeyValueStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream

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
    /** Opens a named asset; overridable so tests can feed a small directory. */
    private val openAsset: (String) -> InputStream = { name -> context.applicationContext.assets.open(name) },
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
                val meta = DirectoryAsset.parseMeta(openAsset(DirectoryAsset.META_NAME).bufferedReader().use { it.readText() })
                val dao = database.directoryDao()
                val seededVersion = store.read(KEY_VERSION)
                if (seededVersion == meta.version && dao.count() >= meta.rows) {
                    _state.value = SeedState.Ready(dao.count(), meta.version)
                    return@withLock
                }
                _state.value = SeedState.Seeding(0f)
                dao.deleteAll()
                var inserted = 0
                var skipped = 0
                DirectoryAsset.openMaybeGzip(openAsset(DirectoryAsset.TSV_NAME)).bufferedReader(Charsets.UTF_8).use { reader ->
                    skipped = DirectoryAsset.readBatches(reader, BATCH) { batch ->
                        // readBatches is inline, so this suspends the seeding coroutine itself.
                        database.withTransaction { dao.insertAll(batch) }
                        inserted += batch.size
                        _state.value = SeedState.Seeding((inserted.toFloat() / meta.rows).coerceIn(0f, 0.99f))
                    }
                }
                if (skipped > 0) Log.w(TAG, "Directory asset: $skipped malformed line(s) skipped out of ${inserted + skipped}.")
                if (inserted == 0) throw IllegalStateException("The bundled directory has no readable rows.")
                store.write(KEY_VERSION, meta.version)
                _state.value = SeedState.Ready(inserted, meta.version)
            } catch (e: CancellationException) {
                // Half a directory must not count as seeded: the next launch starts over.
                _state.value = SeedState.NotStarted
                throw e
            } catch (e: Exception) {
                // FileNotFoundException's message is just the file name; include the type.
                _state.value = SeedState.Failed("${e::class.simpleName}: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "DirectorySeeder"
        const val KEY_VERSION = "india_post_directory_version"
        const val BATCH = 2000
    }
}
