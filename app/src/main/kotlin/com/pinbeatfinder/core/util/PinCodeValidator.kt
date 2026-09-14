package com.pinbeatfinder.core.util

/** Indian PIN codes are six digits and never start with 0. */
object PinCodeValidator {
    val REGEX = Regex("^[1-9][0-9]{5}$")

    fun isValid(value: String?): Boolean = value != null && REGEX.matches(value.trim())

    /**
     * Accepts values as they come out of spreadsheets ("110001", "110001.0", " 110001 ") and
     * returns the canonical 6-digit string, or null when it is not a valid PIN.
     */
    fun normalize(value: String?): String? {
        if (value == null) return null
        var v = value.trim()
        if (v.endsWith(".0")) v = v.dropLast(2)
        return v.takeIf { REGEX.matches(it) }
    }
}
