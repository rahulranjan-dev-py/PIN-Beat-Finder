package com.pinbeatfinder.domain.model

/** One beat of one office, with the villages it delivers. */
data class BeatGroup(
    val officeType: OfficeType,
    val officeName: String,
    val beatNumber: String,
    val accountOffice: String,
    val pincodes: List<String>,
    val records: List<BeatRecord>,
) {
    val key: String get() = "$officeName|$beatNumber"
    val villageCount: Int get() = records.size
    val officeDisplay: String get() = "$officeName ${officeType.code}"
}

/** One office as seen across the whole local directory, for the bulk office-type fixer. */
data class OfficeSummary(
    val officeName: String,
    /** Majority type across the office's records; a mixed office is the thing the fixer exists to repair. */
    val officeType: OfficeType,
    val isMixed: Boolean,
    val pincodes: List<String>,
    val recordCount: Int,
    val beatCount: Int,
) {
    val key: String get() = officeName.lowercase() + "|" + pincodes.joinToString(",")
    val officeDisplay: String get() = "$officeName ${officeType.code}"
}

object BeatGrouping {
    /** The records that belong to [office]: same name (case-insensitive) and one of its PINs. */
    fun recordsOf(records: List<BeatRecord>, office: OfficeSummary): List<BeatRecord> {
        val pins = office.pincodes.toSet()
        return records.filter { it.officeName.trim().equals(office.officeName, ignoreCase = true) && it.pincode in pins }
    }

    /**
     * One summary per (office name, PIN), ordered by name. The PIN is part of the identity so two
     * "Rampur" offices in different districts are never merged into one row.
     */
    fun summarizeOffices(records: List<BeatRecord>): List<OfficeSummary> =
        records.groupBy { it.officeName.trim().lowercase() to it.pincode }
            .values
            .map { rows ->
                val types = rows.groupingBy { it.officeType }.eachCount()
                OfficeSummary(
                    officeName = rows.first().officeName.trim(),
                    officeType = types.maxByOrNull { it.value }!!.key,
                    isMixed = types.size > 1,
                    pincodes = rows.map { it.pincode }.distinct().sorted(),
                    recordCount = rows.size,
                    beatCount = rows.map { it.beatNumber.trim().lowercase() }.distinct().size,
                )
            }
            .sortedWith(compareBy<OfficeSummary> { it.officeName.lowercase() }.thenBy { it.pincodes.first() })

    /**
     * Groups rows by (office name, beat number). Groups are ordered by office then by beat
     * number using natural ordering, so "Beat 2" comes before "Beat 10" and "2A" after "2".
     */
    fun group(records: List<BeatRecord>): List<BeatGroup> =
        records.groupBy { it.officeName.trim().lowercase() to it.beatNumber.trim().lowercase() }
            .values
            .map { rows ->
                val sorted = rows.sortedBy { it.localityName.lowercase() }
                BeatGroup(
                    officeType = rows.groupingBy { it.officeType }.eachCount().maxByOrNull { it.value }!!.key,
                    officeName = rows.first().officeName.trim(),
                    beatNumber = rows.first().beatNumber.trim(),
                    accountOffice = rows.groupingBy { it.accountOffice.trim() }.eachCount().maxByOrNull { it.value }?.key.orEmpty(),
                    pincodes = rows.map { it.pincode }.distinct().sorted(),
                    records = sorted,
                )
            }
            .sortedWith(compareBy<BeatGroup> { it.officeName.lowercase() }.thenComparator { a, b -> naturalCompare(a.beatNumber, b.beatNumber) })

    /** "2" < "2A" < "10" < "BO-3": digit runs compare numerically, everything else case-insensitively. */
    fun naturalCompare(a: String, b: String): Int {
        val ta = tokenize(a)
        val tb = tokenize(b)
        for (i in 0 until minOf(ta.size, tb.size)) {
            val x = ta[i]
            val y = tb[i]
            val c = if (x.all(Char::isDigit) && y.all(Char::isDigit)) {
                x.trimStart('0').ifEmpty { "0" }.let { xs -> y.trimStart('0').ifEmpty { "0" }.let { ys -> compareValuesBy(xs, ys, { it.length }, { it }) } }
            } else {
                x.compareTo(y, ignoreCase = true)
            }
            if (c != 0) return c
        }
        return ta.size - tb.size
    }

    private fun tokenize(s: String): List<String> = Regex("\\d+|\\D+").findAll(s.trim()).map { it.value }.toList()
}
