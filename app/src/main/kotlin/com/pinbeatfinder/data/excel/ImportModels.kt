package com.pinbeatfinder.data.excel

import com.pinbeatfinder.domain.model.BeatField

enum class ImportMode {
    /** Add rows; skip any that duplicate an existing (locality, BO, beat, PIN) tuple. */
    APPEND,

    /** Wipe the directory and load the spreadsheet as the new truth. */
    REPLACE_ALL,
}

data class RowError(
    val rowNumber: Int,
    val messages: Map<BeatField, String>,
) {
    fun describe(): String = "Row $rowNumber: " + messages.values.joinToString("; ")
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
