package com.pinbeatfinder.data.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

/** Progress of fetching one release's APK into the app cache. */
sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val tag: String, val bytes: Long, val total: Long) : DownloadState {
        /** 0..1, or null while the size is unknown. */
        val fraction: Float? get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else null
    }
    data class Verifying(val tag: String) : DownloadState
    /** Downloaded and, when the release ships a checksum file, hash-verified. */
    data class Ready(val tag: String, val file: File, val verified: Boolean) : DownloadState
    data class Failed(val tag: String, val reason: String) : DownloadState
}

/**
 * Downloads a release APK to `cacheDir/updates/<tag>.apk` with progress, then checks it against
 * the release's `SHA256SUMS.txt` when one is published. Network responses are never cached: an
 * APK is far bigger than the HTTP cache and must always come fresh from the release.
 *
 * [client] must have no call timeout: a 10 MB APK on a slow mobile link takes minutes, and the
 * short timeouts of the lookup client would abort it (see `NetworkModule.downloadClient`).
 * The transfer runs in [scope], not in any screen's composition, so rotating the phone or
 * navigating to Settings does not lose the job; [cancel] aborts it.
 */
class ApkDownloader(
    private val client: OkHttpClient,
    private val cacheDir: File,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private var job: Job? = null

    private val dir: File get() = File(cacheDir, DIR).apply { mkdirs() }

    /** True while a transfer for any tag is in flight. */
    val hasActiveDownload: Boolean get() = job?.isActive == true

    /** Starts (or restarts) fetching [release] in the downloader's own scope. */
    fun start(release: ReleaseInfo) {
        job?.cancel()
        job = scope.launch { download(release) }
    }

    /** Aborts an in-flight transfer, if any, and returns the state to idle. */
    fun cancel() {
        job?.cancel()
        job = null
    }

    /** The file a finished download for [tag] would be at; exists only if a previous run completed. */
    fun fileFor(tag: String): File = File(dir, "${tag.replace(Regex("[^A-Za-z0-9._-]"), "_")}.apk")

    /**
     * Fetches [release]'s APK. Safe to call again after a failure. Cancelling the calling
     * coroutine aborts the transfer and removes the partial file.
     */
    suspend fun download(release: ReleaseInfo) = withContext(ioDispatcher) {
        val url = release.apkUrl ?: run {
            _state.value = DownloadState.Failed(release.tag, "This release has no APK file.")
            return@withContext
        }
        val target = fileFor(release.tag)
        val partial = partialOf(target)
        try {
            _state.value = DownloadState.Downloading(release.tag, 0, -1)
            val request = Request.Builder().url(url).cacheControl(CacheControl.FORCE_NETWORK).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Server replied ${response.code}.")
                val body = response.body ?: throw IOException("Empty response.")
                val total = body.contentLength()
                var done = 0L
                var lastReport = 0L
                body.byteStream().use { input ->
                    partial.outputStream().buffered(1 shl 16).use { out ->
                        val buf = ByteArray(1 shl 16)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf); if (n < 0) break
                            out.write(buf, 0, n); done += n
                            if (done - lastReport >= REPORT_EVERY_BYTES) {
                                lastReport = done
                                _state.value = DownloadState.Downloading(release.tag, done, total)
                            }
                        }
                    }
                }
                if (total > 0 && done != total) throw IOException("Download stopped early ($done of $total bytes).")
            }
            _state.value = DownloadState.Verifying(release.tag)
            // When the release publishes checksums, the APK is only installable once it matches
            // one: a missing or unreadable checksum file is a failure the user can retry, never
            // a silent downgrade to "unverified".
            val expected = release.checksumsUrl?.let { checksumsUrl ->
                fetchExpectedHash(checksumsUrl, Checksums.fileNameOf(url))
                    ?: throw IOException("Could not read the release checksum. Please retry.")
            }
            if (expected != null) {
                val actual = Checksums.sha256(partial)
                if (!actual.equals(expected, ignoreCase = true)) {
                    partial.delete()
                    throw IOException("The downloaded file does not match the release checksum.")
                }
            }
            if (target.exists()) target.delete()
            if (!partial.renameTo(target)) throw IOException("Could not save the downloaded file.")
            // Remember that this file passed the checksum so a later restore can say so honestly.
            val marker = verifiedMarker(target)
            if (expected != null) marker.writeText(expected) else marker.delete()
            _state.value = DownloadState.Ready(release.tag, target, verified = expected != null)
        } catch (e: CancellationException) {
            partial.delete()
            _state.value = DownloadState.Idle
            throw e
        } catch (e: Exception) {
            partial.delete()
            _state.value = DownloadState.Failed(release.tag, e.message ?: e::class.simpleName.orEmpty())
        }
    }

    /**
     * Reuses a finished download for [tag] if it is still on disk (e.g. after the settings
     * detour). Hashes the file, so it runs on [ioDispatcher]. Does nothing while a transfer is
     * in flight or a state for this tag is already showing.
     */
    suspend fun restoreIfDownloaded(tag: String): Boolean = withContext(ioDispatcher) {
        if (hasActiveDownload || _state.value !is DownloadState.Idle) return@withContext false
        val f = fileFor(tag)
        if (!f.exists() || f.length() == 0L) return@withContext false
        val marker = verifiedMarker(f)
        val stillVerified = marker.exists() && Checksums.sha256(f).equals(marker.readText().trim(), ignoreCase = true)
        if (_state.value is DownloadState.Idle) _state.value = DownloadState.Ready(tag, f, verified = stillVerified)
        true
    }

    /**
     * The file of a [DownloadState.Ready] state, or null (and back to idle) when the system has
     * since cleared the cache: the installer would otherwise open a missing file.
     */
    fun readyFile(): File? {
        val ready = _state.value as? DownloadState.Ready ?: return null
        if (ready.file.exists() && ready.file.length() > 0L) return ready.file
        verifiedMarker(ready.file).delete()
        _state.value = DownloadState.Idle
        return null
    }

    fun reset() { cancel(); _state.value = DownloadState.Idle }

    /**
     * Deletes every downloaded APK except the one for [keepTag] and its partial file. Skipped
     * while a transfer is in flight so the file being written is never pulled away.
     */
    suspend fun prune(keepTag: String?) = withContext(ioDispatcher) {
        if (hasActiveDownload) return@withContext
        val keep = keepTag?.let { fileFor(it) }?.let { setOf(it, verifiedMarker(it), partialOf(it)) }.orEmpty()
        dir.listFiles()?.forEach { f -> if (f !in keep) f.delete() }
    }

    private fun partialOf(apk: File) = File(apk.path + ".part")

    private fun verifiedMarker(apk: File) = File(apk.path + ".sha256")

    private fun fetchExpectedHash(checksumsUrl: String, fileName: String): String? {
        val request = Request.Builder().url(checksumsUrl).cacheControl(CacheControl.FORCE_NETWORK).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return Checksums.parse(response.body?.string().orEmpty())[fileName]
        }
    }

    companion object {
        const val DIR = "updates"
        private const val REPORT_EVERY_BYTES = 128L * 1024
    }
}
