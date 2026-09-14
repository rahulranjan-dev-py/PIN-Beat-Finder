package com.pinbeatfinder.data.excel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.pinbeatfinder.core.util.BeatDraftValidator
import com.pinbeatfinder.data.repository.BeatDirectoryRepository
import com.pinbeatfinder.domain.model.BeatRecord
import com.pinbeatfinder.domain.model.DraftValidation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Android-facing orchestration of .xlsx import, export, template download and sharing.
 *
 * File format concerns live in [ExcelCodec]; this class only deals with `ContentResolver`
 * streams (Storage Access Framework), the app cache directory and `FileProvider` intents.
 */
class ExcelSyncManager(
    private val context: Context,
    private val repository: BeatDirectoryRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val appContext = context.applicationContext
    private val authority = "${appContext.packageName}.fileprovider"

    /** Must match `res/xml/file_paths.xml`. */
    private val exportDir: File get() = File(appContext.cacheDir, EXPORT_DIR).apply { mkdirs() }

    // ------------------------------------------------------------------ import

    /**
     * Parses the user-selected workbook at [uri], validates every row, and writes the valid
     * rows to Room in a single transaction. Invalid rows are reported, never partially saved.
     */
    suspend fun importFrom(uri: Uri, mode: ImportMode): ImportReport = withContext(ioDispatcher) {
        val stream = appContext.contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException("Could not open the selected file.")
        val parsed = stream.use { ExcelCodec.read(it) }

        val valid = ArrayList<BeatRecord>(parsed.rows.size)
        val errors = ArrayList<RowError>()
        val now = System.currentTimeMillis()
        for (row in parsed.rows) {
            when (val v = BeatDraftValidator.validate(row.draft, now)) {
                is DraftValidation.Valid -> valid += v.record
                is DraftValidation.Invalid -> errors += RowError(row.rowNumber, v.errors)
            }
        }

        val outcome = repository.importRecords(valid, replaceExisting = mode == ImportMode.REPLACE_ALL)
        ImportReport(
            inserted = outcome.inserted,
            duplicatesSkipped = outcome.duplicatesSkipped,
            blankRowsSkipped = parsed.blankRowsSkipped,
            errors = errors,
        )
    }

    // ------------------------------------------------------------------ export / template

    /** Writes a full backup into the app cache and returns the file (ready for [shareIntent]). */
    suspend fun exportBackup(): File = withContext(ioDispatcher) {
        val records = repository.getAll()
        val file = File(exportDir, ExcelCodec.backupFileName())
        file.outputStream().buffered().use { ExcelCodec.writeRecords(records, it) }
        file
    }

    /** Writes the blank template into the app cache and returns the file. */
    suspend fun exportTemplate(): File = withContext(ioDispatcher) {
        val file = File(exportDir, ExcelCodec.TEMPLATE_FILE_NAME)
        file.outputStream().buffered().use { ExcelCodec.writeTemplate(it) }
        file
    }

    /** Streams the template straight into a location the user picked via ACTION_CREATE_DOCUMENT. */
    suspend fun saveTemplateTo(uri: Uri) = withContext(ioDispatcher) {
        val out = appContext.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Could not write to the selected location.")
        out.buffered().use { ExcelCodec.writeTemplate(it) }
    }

    /** Streams a full backup straight into a location the user picked via ACTION_CREATE_DOCUMENT. */
    suspend fun saveBackupTo(uri: Uri) = withContext(ioDispatcher) {
        val records = repository.getAll()
        val out = appContext.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Could not write to the selected location.")
        out.buffered().use { ExcelCodec.writeRecords(records, it) }
    }

    /**
     * Builds a share-sheet intent (WhatsApp, Gmail, Drive, …) for a file produced by
     * [exportBackup] or [exportTemplate]. The URI is served by `FileProvider`, so no storage
     * permission is required on any supported API level.
     */
    fun shareIntent(file: File, title: String = "Share beat directory"): Intent {
        val uri = FileProvider.getUriForFile(appContext, authority, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = ExcelCodec.MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Removes exports older than [maxAgeMillis]; call opportunistically at start-up. */
    suspend fun pruneOldExports(maxAgeMillis: Long = 7L * 24 * 60 * 60 * 1000) = withContext(ioDispatcher) {
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        exportDir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    companion object {
        const val EXPORT_DIR = "exports"
    }
}
