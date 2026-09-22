package com.pinbeatfinder.data.excel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.pinbeatfinder.R
import com.pinbeatfinder.core.util.BeatDraftValidator
import com.pinbeatfinder.data.print.BeatSheetPdf
import com.pinbeatfinder.data.repository.BeatDirectoryRepository
import com.pinbeatfinder.domain.model.BeatRecord
import com.pinbeatfinder.domain.model.DraftValidation
import com.pinbeatfinder.data.prefs.LocaleSupport
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
    /** Current app-language tag, so share-sheet titles match the UI language. */
    private val languageTag: () -> String = { "" },
) {
    private val appContext = context.applicationContext
    private val authority = "${appContext.packageName}.fileprovider"

    /** Must match `res/xml/file_paths.xml`. */
    private val exportDir: File get() = File(appContext.cacheDir, EXPORT_DIR).apply { mkdirs() }

    // ------------------------------------------------------------------ import

    /**
     * Phase 1 of an import: parse the workbook at [uri], validate every row and work out what
     * would be inserted or skipped — without touching the database. The result is shown to the
     * user for confirmation and then handed to [commitImport].
     */
    suspend fun prepareImport(uri: Uri, mode: ImportMode): ImportPreview = withContext(ioDispatcher) {
        // A beat directory is a few hundred KB; a workbook far above that is the wrong file (or a
        // zip bomb) and would be inflated whole into memory by the reader.
        val size = runCatching { appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull() ?: -1L
        if (size > MAX_IMPORT_BYTES) {
            throw ExcelFormatException(string(R.string.msg_import_too_large, MAX_IMPORT_BYTES / (1024 * 1024)))
        }
        val stream = appContext.contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException("Could not open the selected file.")
        val parsed = try {
            stream.use { ExcelCodec.read(it) }
        } catch (e: OutOfMemoryError) {
            throw ExcelFormatException(string(R.string.msg_import_too_large, MAX_IMPORT_BYTES / (1024 * 1024)))
        }
        if (parsed.rows.size > MAX_IMPORT_ROWS) {
            throw ExcelFormatException(string(R.string.msg_import_too_many_rows, MAX_IMPORT_ROWS))
        }

        val valid = ArrayList<BeatRecord>(parsed.rows.size)
        val errors = ArrayList<RowError>()
        val now = System.currentTimeMillis()
        for (row in parsed.rows) {
            when (val v = BeatDraftValidator.validate(row.draft, now)) {
                is DraftValidation.Valid -> valid += v.record
                is DraftValidation.Invalid -> errors += RowError(row.rowNumber, v.errors)
            }
        }
        val (fresh, duplicates) = repository.partitionForImport(valid, replaceExisting = mode == ImportMode.REPLACE_ALL)
        ImportPreview(
            mode = mode,
            records = fresh,
            duplicatesSkipped = duplicates,
            blankRowsSkipped = parsed.blankRowsSkipped,
            errors = errors,
            existingCount = if (mode == ImportMode.REPLACE_ALL) repository.getAll().size else 0,
        )
    }

    /** Phase 2: write the previewed rows in one transaction. */
    suspend fun commitImport(preview: ImportPreview): ImportReport = withContext(ioDispatcher) {
        val outcome = repository.importRecords(preview.records, replaceExisting = preview.mode == ImportMode.REPLACE_ALL)
        ImportReport(
            inserted = outcome.inserted,
            duplicatesSkipped = preview.duplicatesSkipped + outcome.duplicatesSkipped,
            blankRowsSkipped = preview.blankRowsSkipped,
            errors = preview.errors,
        )
    }

    /** One-shot import (preview + commit) for callers that do not need confirmation. */
    suspend fun importFrom(uri: Uri, mode: ImportMode): ImportReport = commitImport(prepareImport(uri, mode))

    // ------------------------------------------------------------------ export / template

    /** Writes a full backup into the app cache and returns the file (ready for [shareIntent]). */
    suspend fun exportBackup(): File = withContext(ioDispatcher) {
        val records = repository.getAll()
        val file = File(exportDir, ExcelCodec.backupFileName())
        file.outputStream().buffered().use { ExcelCodec.writeRecords(records, it) }
        file
    }

    /** Writes just [records] (one beat, one office…) into the app cache under a [label]-based name. */
    suspend fun exportRecords(records: List<BeatRecord>, label: String): File = withContext(ioDispatcher) {
        val file = File(exportDir, ExcelCodec.exportFileName(label))
        file.outputStream().buffered().use { ExcelCodec.writeRecords(records, it) }
        file
    }

    /** Renders [records] as a printable PDF (see [BeatSheetPdf]) into the app cache. */
    suspend fun exportPdf(records: List<BeatRecord>, label: String, title: String, subtitle: String): File = withContext(ioDispatcher) {
        val file = File(exportDir, ExcelCodec.exportFileName(label).removeSuffix(".xlsx") + ".pdf")
        BeatSheetPdf.write(title, subtitle, records, file.outputStream().buffered())
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
    fun shareIntent(file: File, title: String = "Share beat directory", mimeType: String = ExcelCodec.MIME_TYPE): Intent {
        val uri = FileProvider.getUriForFile(appContext, authority, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Localised string lookup for callers without a Context (share-sheet titles). */
    fun string(resId: Int, vararg args: Any): String = LocaleSupport.wrapBase(appContext, languageTag()).getString(resId, *args)

    /** Removes exports older than [maxAgeMillis]; call opportunistically at start-up. */
    suspend fun pruneOldExports(maxAgeMillis: Long = 7L * 24 * 60 * 60 * 1000) = withContext(ioDispatcher) {
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        exportDir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    companion object {
        /** Largest workbook accepted for import (20 MB): a real beat directory is well under 1 MB. */
        const val MAX_IMPORT_BYTES = 20L * 1024 * 1024
        const val MAX_IMPORT_ROWS = 50_000
        const val EXPORT_DIR = "exports"
    }
}
