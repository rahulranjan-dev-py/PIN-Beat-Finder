package com.pinbeatfinder.data.crash

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.pinbeatfinder.data.prefs.KeyValueStore
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Minimal on-device crash capture: an uncaught-exception handler writes a plain-text report
 * into `filesDir/crashes/` (last [MAX_REPORTS] kept), then hands over to the previous handler
 * so Android still shows its own dialog. No network, no third-party SDK; the user shares the
 * file from Settings when they choose to.
 */
class CrashReporter(
    context: Context,
    private val store: KeyValueStore,
    private val versionName: String,
) {
    private val appContext = context.applicationContext
    private val dir: File get() = File(appContext.filesDir, DIR).apply { mkdirs() }

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Newest report first. */
    fun reports(): List<File> = dir.listFiles { f -> f.name.endsWith(".txt") }?.sortedByDescending { it.lastModified() }.orEmpty()

    fun latest(): File? = reports().firstOrNull()

    /** True once after a crash, until [acknowledge] is called; drives a one-time snackbar. */
    fun hasUnacknowledgedCrash(): Boolean = store.read(KEY_UNACKNOWLEDGED) == "1"

    fun acknowledge() = store.write(KEY_UNACKNOWLEDGED, "0")

    fun deleteAll() = reports().forEach { it.delete() }

    fun shareIntent(file: File, title: String): Intent {
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "PIN Beat Finder crash report ${file.nameWithoutExtension}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun write(thread: Thread, throwable: Throwable) {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val text = buildString {
            appendLine("PIN Beat Finder $versionName")
            appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(trace)
        }
        File(dir, "crash_$stamp.txt").writeText(text)
        reports().drop(MAX_REPORTS).forEach { it.delete() }
        store.write(KEY_UNACKNOWLEDGED, "1")
    }

    companion object {
        const val DIR = "crashes"
        const val MAX_REPORTS = 5
        const val KEY_UNACKNOWLEDGED = "crash_unacknowledged"
    }
}
