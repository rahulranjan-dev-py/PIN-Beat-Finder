package com.pinbeatfinder.data.excel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.pinbeatfinder.R
import com.pinbeatfinder.core.util.BeatDraftValidator
import com.pinbeatfinder.data.print.BeatSheetImage
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
        // What the import changes, village by village, so "replace all" is never a blind step.
        val existing = repository.getAll()
        val existingKeys = existing.map { it.dedupeKey }.toHashSet()
        val newKeys = fresh.map { it.dedupeKey }.toHashSet()
        val removed = if (mode == ImportMode.REPLACE_ALL) existing.filter { it.dedupeKey !in newKeys } else emptyList()
        val added = fresh.filter { it.dedupeKey !in existingKeys }
        val unchanged = if (mode == ImportMode.REPLACE_ALL) existing.size - removed.size else duplicates
        ImportPreview(
            mode = mode,
            records = fresh,
            duplicatesSkipped = duplicates,
            blankRowsSkipped = parsed.blankRowsSkipped,
            errors = errors,
            existingCount = if (mode == ImportMode.REPLACE_ALL) existing.size else 0,
            removedCount = removed.size,
            removedSamples = removed.take(SAMPLE_NAMES).map { it.localityName },
            addedSamples = added.take(SAMPLE_NAMES).map { it.localityName },
            unchangedCount = unchanged,
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
        val labels = BeatSheetPdf.Labels(
            serial = string(R.string.pdf_col_sl),
            locality = string(R.string.pdf_col_locality),
            beat = string(R.string.pdf_col_beat),
            pin = string(R.string.pdf_col_pin),
            remarks = string(R.string.pdf_col_remarks),
            localities = { n -> plural(R.plurals.pdf_localities, n) },
            printed = { stamp -> string(R.string.pdf_printed, stamp) },
            page = { p, n -> string(R.string.pdf_page, p, n) },
        )
        BeatSheetPdf.write(title, subtitle, records, file.outputStream().buffered(), labels = labels)
        file
    }

    /** One page per office: its beats and village names, for the branch wall. */
    suspend fun exportOfficeSummaryPdf(sections: List<BeatSheetPdf.OfficeSection>): File = withContext(ioDispatcher) {
        val file = File(exportDir, ExcelCodec.exportFileName("office summary").removeSuffix(".xlsx") + ".pdf")
        val labels = BeatSheetPdf.SummaryLabels(
            heading = string(R.string.pdf_office_summary),
            beatLine = { beat, n -> string(R.string.local_beat_title, beat) + " • " + plural(R.plurals.count_villages, n) },
            printed = { stamp -> string(R.string.pdf_printed, stamp) },
            page = { p, n -> string(R.string.pdf_page, p, n) },
        )
        BeatSheetPdf.writeOfficeSummary(sections, file.outputStream().buffered(), labels)
        file
    }

    /** A beat as a PNG for chat groups. */
    suspend fun exportImage(records: List<BeatRecord>, label: String, title: String, subtitle: String): File = withContext(ioDispatcher) {
        val file = File(exportDir, ExcelCodec.exportFileName(label).removeSuffix(".xlsx") + ".png")
        BeatSheetImage.write(
            title = title,
            subtitle = subtitle,
            records = records,
            footer = string(R.string.pdf_printed, java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.US).format(java.util.Date())),
            out = file.outputStream().buffered(),
            columnBeat = plural(R.plurals.count_villages, records.size),
            columnPin = string(R.string.pdf_col_pin),
        )
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

    /** Quantity string in the app language; [count] is also the first format argument. */
    fun plural(resId: Int, count: Int, vararg args: Any): String =
        LocaleSupport.wrapBase(appContext, languageTag()).resources.getQuantityString(resId, count, count, *args)

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
        /** How many village names an import preview lists per change kind. */
        const val SAMPLE_NAMES = 6
    }
}
