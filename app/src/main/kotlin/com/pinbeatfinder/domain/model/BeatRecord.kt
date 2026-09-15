package com.pinbeatfinder.domain.model

/**
 * A village/locality mapped to the beat that delivers it. This is the domain view of a
 * `local_beat_directory` row; phonetic keys are an implementation detail of the data layer.
 */
data class BeatRecord(
    val id: Long = 0L,
    val localityName: String,
    val branchOffice: String,
    val subPostOffice: String,
    val beatNumber: String,
    val district: String,
    val state: String,
    val pincode: String,
    val remarks: String = "",
    val updatedAt: Long = 0L,
) {
    /** Natural key used to detect duplicates on import. */
    val dedupeKey: String
        get() = listOf(localityName, branchOffice, beatNumber, pincode)
            .joinToString("|") { it.trim().lowercase() }
}

/** Optional narrowing applied to a local directory search. */
data class BeatSearchFilters(
    val state: String? = null,
    val district: String? = null,
)

/** Why a row matched, so the UI can explain it (bold substring vs. "sounds like"). */
enum class MatchKind {
    /** Matched the beat number or PIN exactly, or the name exactly. */
    EXACT,
    /** The query text occurs in the locality name. */
    TEXT,
    /** Only the phonetic keys matched (misspelling tolerated). */
    PHONETIC,
    /** Browse mode, no query. */
    NONE,
    ;

    companion object {
        fun fromScore(score: Double, hasQuery: Boolean): MatchKind = when {
            !hasQuery -> NONE
            score >= 0.99 -> EXACT
            score >= 0.80 -> TEXT
            score >= 0.55 -> PHONETIC
            else -> PHONETIC
        }
    }
}

/** A locally matched row plus its relevance so the UI can show "best match" affordances. */
data class BeatSearchHit(
    val record: BeatRecord,
    val score: Double,
    val matchKind: MatchKind = MatchKind.NONE,
)
