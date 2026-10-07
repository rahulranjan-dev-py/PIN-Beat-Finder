package com.pinbeatfinder.data.excel

import com.pinbeatfinder.domain.model.BeatField
import com.pinbeatfinder.domain.model.FieldError

enum class ImportMode {
    /** Add rows; skip any that duplicate an existing (locality, BO, beat, PIN) tuple. */
    APPEND,

    /** Wipe the directory and load the spreadsheet as the new truth. */
    REPLACE_ALL,
}

data class RowError(
    val rowNumber: Int,
    val messages: Map<BeatField, FieldError>,
) {
    fun describe(): String = "Row $rowNumber: " + messages.entries.joinToString("; ") { (field, err) -> err.describe(field) }
}

/** What an import *would* do; shown to the user before anything is written. */
data class ImportPreview(
    val mode: ImportMode,
    /** Rows that will be inserted (already de-duplicated). */
    val records: List<com.pinbeatfinder.domain.model.BeatRecord>,
    val duplicatesSkipped: Int,
    val blankRowsSkipped: Int,
    val errors: List<RowError>,
    /** Rows that will be deleted first when [mode] is REPLACE_ALL. */
    val existingCount: Int,
    /** REPLACE_ALL: existing villages that are not in the spreadsheet and so disappear. */
    val removedCount: Int = 0,
    val removedSamples: List<String> = emptyList(),
    /** Villages that are new to the directory (a few names, for the preview). */
    val addedSamples: List<String> = emptyList(),
    /** Villages present both in the directory and the spreadsheet. */
    val unchangedCount: Int = 0,
) {
    val willInsert: Int get() = records.size
    val hasErrors: Boolean get() = errors.isNotEmpty()
    val isEmpty: Boolean get() = records.isEmpty()
}

data class ImportReport(
    val inserted: Int,
    val duplicatesSkipped: Int,
    val blankRowsSkipped: Int,
    val errors: List<RowError>,
) {
    val hasErrors: Boolean get() = errors.isNotEmpty()

    fun summary(): String = buildString {
        append("$inserted record(s) imported")
        if (duplicatesSkipped > 0) append(", $duplicatesSkipped duplicate(s) skipped")
        if (errors.isNotEmpty()) append(", ${errors.size} row(s) rejected")
        append('.')
    }
}
