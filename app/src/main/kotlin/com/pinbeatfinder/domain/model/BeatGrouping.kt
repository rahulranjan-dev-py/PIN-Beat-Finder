package com.pinbeatfinder.domain.model

/** One beat of one branch office, with the villages it delivers. */
data class BeatGroup(
    val branchOffice: String,
    val beatNumber: String,
    val subPostOffice: String,
    val pincodes: List<String>,
    val records: List<BeatRecord>,
) {
    val key: String get() = "$branchOffice|$beatNumber"
    val villageCount: Int get() = records.size
}

object BeatGrouping {
    /**
     * Groups rows by (branch office, beat number). Groups are ordered by branch office then by
     * beat number using natural ordering, so "Beat 2" comes before "Beat 10" and "2A" after "2".
     */
    fun group(records: List<BeatRecord>): List<BeatGroup> =
        records.groupBy { it.branchOffice.trim().lowercase() to it.beatNumber.trim().lowercase() }
            .values
            .map { rows ->
                val sorted = rows.sortedBy { it.localityName.lowercase() }
                BeatGroup(
                    branchOffice = rows.first().branchOffice.trim(),
                    beatNumber = rows.first().beatNumber.trim(),
                    subPostOffice = rows.groupingBy { it.subPostOffice.trim() }.eachCount().maxByOrNull { it.value }?.key.orEmpty(),
                    pincodes = rows.map { it.pincode }.distinct().sorted(),
                    records = sorted,
                )
            }
            .sortedWith(compareBy<BeatGroup> { it.branchOffice.lowercase() }.thenComparator { a, b -> naturalCompare(a.beatNumber, b.beatNumber) })

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
