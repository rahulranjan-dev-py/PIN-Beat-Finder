package com.pinbeatfinder.core.dedupe

import com.pinbeatfinder.core.phonetic.IndianPhoneticNormalizer
import com.pinbeatfinder.core.phonetic.PhoneticSearchEngine
import com.pinbeatfinder.domain.model.BeatRecord

/** Two records that look like the same locality filed twice on the same beat. */
data class DuplicatePair(val first: BeatRecord, val second: BeatRecord, val reason: Reason) {
    enum class Reason { SAME_NAME, SOUNDS_ALIKE, NEAR_SPELLING }

    val key: String get() = "${first.id}|${second.id}"
}

/**
 * Finds probable duplicates: records on the same beat of the same office and PIN whose
 * localities are the same after normalisation, share phonetic keys, or are within a small
 * spelling distance. Records on different beats are never paired — the same village name on
 * two beats is a real situation (a large village split between two postmen). Names that differ
 * only by a number or a single letter ("Ward 1" / "Ward 2", "Sector A" / "Sector B") are
 * different places, not duplicates, even though they normalise to the same text.
 */
class DuplicateFinder(private val engine: PhoneticSearchEngine = PhoneticSearchEngine()) {

    fun find(records: List<BeatRecord>): List<DuplicatePair> {
        val pairs = ArrayList<DuplicatePair>()
        records
            .groupBy { Triple(it.officeName.trim().lowercase(), it.beatNumber.trim().lowercase(), it.pincode) }
            .values
            .forEach { group ->
                if (group.size < 2) return@forEach
                val prepared = group.map {
                    Prepared(it, IndianPhoneticNormalizer.normalize(it.localityName), engine.encode(it.localityName), IndianPhoneticNormalizer.qualifiers(it.localityName))
                }
                for (i in prepared.indices) for (j in i + 1 until prepared.size) {
                    val a = prepared[i]; val b = prepared[j]
                    if (a.qualifiers != b.qualifiers) continue
                    val reason = when {
                        a.norm.isNotEmpty() && a.norm == b.norm -> DuplicatePair.Reason.SAME_NAME
                        !a.keys.isEmpty && engine.phoneticallyEqual(a.keys, b.keys) -> DuplicatePair.Reason.SOUNDS_ALIKE
                        PhoneticSearchEngine.jaroWinkler(a.norm, b.norm) >= NEAR_SPELLING -> DuplicatePair.Reason.NEAR_SPELLING
                        else -> null
                    }
                    if (reason != null) pairs += DuplicatePair(a.record, b.record, reason)
                }
            }
        return pairs.sortedWith(compareBy({ it.first.officeName.lowercase() }, { it.first.beatNumber }, { it.first.localityName.lowercase() }))
    }

    private class Prepared(val record: BeatRecord, val norm: String, val keys: com.pinbeatfinder.core.phonetic.PhoneticKeys, val qualifiers: List<String>)

    companion object {
        /** Jaro–Winkler on normalised names; 0.92 pairs "rampur kalan"/"rampur kala" but not "rampur"/"raipur". */
        const val NEAR_SPELLING = 0.92

        /** The record to keep when resolving: the longer remarks, then the older row. */
        fun merged(keep: BeatRecord, drop: BeatRecord): BeatRecord =
            if (keep.remarks.isBlank() && drop.remarks.isNotBlank()) keep.copy(remarks = drop.remarks) else keep
    }
}
