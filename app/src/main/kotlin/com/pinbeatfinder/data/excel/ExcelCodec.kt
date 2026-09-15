package com.pinbeatfinder.data.excel

import com.pinbeatfinder.domain.model.BeatDraft
import com.pinbeatfinder.domain.model.BeatField
import com.pinbeatfinder.domain.model.BeatRecord
import com.pinbeatfinder.domain.model.OfficeType
import org.dhatim.fastexcel.Workbook
import org.dhatim.fastexcel.reader.Cell
import org.dhatim.fastexcel.reader.CellType
import org.dhatim.fastexcel.reader.ReadableWorkbook
import org.dhatim.fastexcel.reader.Row
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One spreadsheet row lifted into an unvalidated draft, with its 1-based Excel row number. */
data class ExcelRow(val rowNumber: Int, val draft: BeatDraft)

data class ExcelParseResult(
    val rows: List<ExcelRow>,
    /** Rows that were completely blank are skipped silently; this is how many. */
    val blankRowsSkipped: Int,
)

class ExcelFormatException(message: String) : Exception(message)

/**
 * Pure (Android-free) reader/writer for the beat directory spreadsheet format.
 *
 * Kept separate from [ExcelSyncManager] so it can be unit-tested on the JVM and so the file
 * format is defined in exactly one place: [HEADERS] drives the template, the export and the
 * import's header detection.
 */
object ExcelCodec {
    const val SHEET_NAME = "Beat Directory"
    const val MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val TEMPLATE_FILE_NAME = "beat_directory_template.xlsx"

    /** Column order of the template/export. The trailing `*` marks mandatory columns. */
    val HEADERS: List<String> = BeatField.entries.map { if (it.required) "${it.label}*" else it.label }

    private val COLUMN_WIDTHS = doubleArrayOf(30.0, 12.0, 24.0, 24.0, 14.0, 20.0, 20.0, 12.0, 36.0)

    /** Allowed values of the Office Type column, in dropdown order. */
    val OFFICE_TYPE_CODES: List<String> = OfficeType.entries.map { it.code }

    /**
     * Headers of the pre-v0.10.0 template. Files that still use them import fine: the branch
     * office becomes a BO record; a row with only a sub post office becomes an SO record.
     */
    private const val LEGACY_BRANCH_OFFICE = "Branch Office (BO)"
    private const val LEGACY_SUB_POST_OFFICE = "Sub Post Office (SO)"

    /** Writes an empty template (headers + instructions sheet) to [out]. Closes the stream. */
    fun writeTemplate(out: OutputStream) = writeWorkbook(out, emptyList())

    /** Serialises [records] to a sharable workbook. Closes the stream. */
    fun writeRecords(records: List<BeatRecord>, out: OutputStream) = writeWorkbook(out, records)

    /**
     * Reads the first sheet of [input] into drafts. Header matching is tolerant: case, spacing,
     * punctuation and the `*` suffix are ignored, so "office name" and "Office Name*" are the
     * same column. Spreadsheets made with the older "Branch Office (BO)" / "Sub Post Office (SO)"
     * template are converted on the fly. Throws [ExcelFormatException] when mandatory columns
     * are missing.
     */
    fun read(input: InputStream): ExcelParseResult {
        ReadableWorkbook(input).use { workbook ->
            val sheet = workbook.firstSheet
            val rows = sheet.read()
            if (rows.isEmpty()) throw ExcelFormatException("The workbook is empty.")

            val headerRow = rows.first()
            val layout = resolveColumns(headerRow)

            val drafts = ArrayList<ExcelRow>(rows.size - 1)
            var blank = 0
            for (row in rows.drop(1)) {
                val values = BeatField.entries.associateWith { field ->
                    layout.columns[field]?.let { cellText(row, it) }.orEmpty()
                }
                val legacyBo = layout.legacyBranchOffice?.let { cellText(row, it) }.orEmpty()
                val legacySo = layout.legacySubPostOffice?.let { cellText(row, it) }.orEmpty()
                if (values.values.all { it.isBlank() } && legacyBo.isBlank() && legacySo.isBlank()) {
                    blank++
                    continue
                }
                drafts += ExcelRow(rowNumber = row.rowNum, draft = toDraft(values, legacyBo, legacySo))
            }
            return ExcelParseResult(drafts, blank)
        }
    }

    /** Builds the draft, folding the legacy BO/SO columns into type + name + account office. */
    private fun toDraft(values: Map<BeatField, String>, legacyBo: String, legacySo: String): BeatDraft {
        var type = values.getValue(BeatField.OFFICE_TYPE)
        var name = values.getValue(BeatField.OFFICE_NAME)
        var account = values.getValue(BeatField.ACCOUNT_OFFICE)
        if (name.isBlank()) {
            when {
                legacyBo.isNotBlank() -> {
                    name = OfficeType.stripSuffix(legacyBo)
                    if (type.isBlank()) type = OfficeType.BO.code
                    if (account.isBlank()) account = legacySo
                }
                legacySo.isNotBlank() -> {
                    name = OfficeType.stripSuffix(legacySo)
                    if (type.isBlank()) type = OfficeType.SO.code
                }
            }
        } else if (account.isBlank()) {
            account = legacySo
        }
        return BeatDraft(
            localityName = values.getValue(BeatField.LOCALITY),
            officeType = type,
            officeName = name,
            accountOffice = account,
            beatNumber = values.getValue(BeatField.BEAT_NUMBER),
            district = values.getValue(BeatField.DISTRICT),
            state = values.getValue(BeatField.STATE),
            pincode = values.getValue(BeatField.PINCODE),
            remarks = values.getValue(BeatField.REMARKS),
        )
    }

    fun backupFileName(now: Date = Date()): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(now)
        return "beat_directory_backup_$stamp.xlsx"
    }

    // ------------------------------------------------------------------ writing

    private fun writeWorkbook(out: OutputStream, records: List<BeatRecord>) {
        out.use { stream ->
            val workbook = Workbook(stream, "PIN Beat Finder", "1.0")
            val sheet = workbook.newWorksheet(SHEET_NAME)

            HEADERS.forEachIndexed { col, header ->
                sheet.value(0, col, header)
                sheet.style(0, col).bold().fillColor("D9D9D9").borderStyle("thin").set()
                sheet.width(col, COLUMN_WIDTHS[col])
            }
            sheet.freezePane(0, 1)

            records.forEachIndexed { i, r ->
                val row = i + 1
                sheet.value(row, 0, r.localityName)
                sheet.value(row, 1, r.officeType.code)
                sheet.value(row, 2, r.officeName)
                sheet.value(row, 3, r.accountOffice)
                sheet.value(row, 4, r.beatNumber)
                sheet.value(row, 5, r.district)
                sheet.value(row, 6, r.state)
                // PIN codes are written as text so leading digits are never reformatted.
                sheet.value(row, 7, r.pincode)
                sheet.value(row, 8, r.remarks)
            }

            val help = workbook.newWorksheet("Instructions")
            listOf(
                "How to fill the Beat Directory sheet",
                "",
                "• Columns marked with * are mandatory.",
                "• Office Type must be one of: ${OFFICE_TYPE_CODES.joinToString(", ")}.",
                "• Office Name is the serving office without the type suffix (e.g. Rampur, not Rampur BO).",
                "• Account Office is the SO/HO the office reports to (optional, e.g. Sitapur SO).",
                "• Pincode must be exactly 6 digits and cannot start with 0 (e.g. 110001).",
                "• Beat Number is free text (e.g. 1, 2A, BO-3) but should match the beat register.",
                "• One locality/village per row. Delete nothing from the header row.",
                "• Keep the file as .xlsx. Rows with errors are reported and skipped on import.",
            ).forEachIndexed { i, line -> help.value(i, 0, line) }
            help.style(0, 0).bold().set()
            help.width(0, 90.0)

            workbook.finish()
        }
    }

    // ------------------------------------------------------------------ reading

    private class ColumnLayout(
        val columns: Map<BeatField, Int>,
        val legacyBranchOffice: Int?,
        val legacySubPostOffice: Int?,
    )

    private fun resolveColumns(headerRow: Row): ColumnLayout {
        val normalisedHeaders = (0 until headerRow.cellCount).associateBy { idx ->
            normaliseHeader(cellText(headerRow, idx))
        }
        fun find(label: String): Int? {
            val key = normaliseHeader(label)
            return normalisedHeaders[key]
                ?: normalisedHeaders.entries.firstOrNull { (h, _) -> h.isNotEmpty() && (h.startsWith(key) || key.startsWith(h)) }?.value
        }
        val mapping = BeatField.entries.mapNotNull { field -> find(field.label)?.let { field to it } }.toMap()
        val legacyBo = find(LEGACY_BRANCH_OFFICE)
        val legacySo = find(LEGACY_SUB_POST_OFFICE)
        val legacyCoversOffice = legacyBo != null || legacySo != null

        val missing = BeatField.entries.filter { field ->
            field.required && field !in mapping &&
                !(legacyCoversOffice && (field == BeatField.OFFICE_TYPE || field == BeatField.OFFICE_NAME))
        }
        if (missing.isNotEmpty()) {
            throw ExcelFormatException(
                "Missing required column(s): ${missing.joinToString { it.label }}. " +
                    "Download the template and keep its header row.",
            )
        }
        return ColumnLayout(mapping, legacyBo, legacySo)
    }

    private fun normaliseHeader(raw: String): String =
        raw.lowercase(Locale.ROOT)
            .replace(Regex("\\(.*?\\)"), "")        // drop "(BO)" style hints
            .replace(Regex("[^a-z]"), "")            // drop *, /, spaces

    private fun cellText(row: Row, index: Int): String {
        if (index < 0 || index >= row.cellCount) return ""
        val cell: Cell = row.getCell(index) ?: return ""
        return when (cell.type) {
            CellType.NUMBER -> cell.asNumber().stripTrailingZeros().toPlainString()
            CellType.EMPTY -> ""
            CellType.BOOLEAN -> cell.asBoolean().toString()
            else -> cell.text.orEmpty()
        }.trim()
    }
}
