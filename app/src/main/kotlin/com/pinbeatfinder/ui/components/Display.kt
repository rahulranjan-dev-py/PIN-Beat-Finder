package com.pinbeatfinder.ui.components

import androidx.compose.ui.unit.dp

/**
 * Display-only formatting. Nothing here changes what is stored, bundled, filtered or exported:
 * the values shown on screen are derived at render time from the raw data.
 */

/** Bottom padding for every list the floating "Add beat record" button can cover. */
val LocalListBottomPadding = 112.dp

/** Bottom padding for lists without a floating button over them. */
val PlainListBottomPadding = 32.dp

/** Value some datasets put in the Region column when the region is unknown. */
const val REGION_PLACEHOLDER = "DivReportingCircle"

/** The region to show, or null when the dataset carries a placeholder or nothing. */
fun displayRegion(region: String): String? =
    region.trim().takeIf { it.isNotBlank() && !it.equals(REGION_PLACEHOLDER, ignoreCase = true) }

/**
 * "BOKARO STEEL CITY" -> "Bokaro Steel City". Only text written entirely in capitals is changed;
 * mixed-case names ("McCluskieganj") and scripts without case (Devanagari) come back untouched.
 */
fun String.displayCase(): String {
    if (isBlank()) return this
    var hasUpper = false
    for (c in this) {
        if (c.isLowerCase()) return this
        if (c.isUpperCase()) hasUpper = true
    }
    if (!hasUpper) return this
    val out = StringBuilder(length)
    var startOfWord = true
    for (c in this) {
        out.append(if (startOfWord) c.uppercaseChar() else c.lowercaseChar())
        startOfWord = !c.isLetterOrDigit() && c != '\''
    }
    return out.toString()
}

/** "district, state" with both parts title-cased and blanks dropped. */
fun placeLine(district: String, state: String): String =
    listOf(district, state).map { it.displayCase() }.filter { it.isNotBlank() }.joinToString(", ")
