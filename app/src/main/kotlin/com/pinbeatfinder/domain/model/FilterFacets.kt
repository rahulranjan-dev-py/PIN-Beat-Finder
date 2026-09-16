package com.pinbeatfinder.domain.model

/** One distinct (state, district, office type, office, beat, PIN) combination present in the directory. */
data class FilterFacet(
    val state: String,
    val district: String,
    val officeType: OfficeType,
    val officeName: String,
    val beatNumber: String,
    val pincode: String,
)

/**
 * The choices to offer for each filter given what is already selected: districts are those of
 * the chosen state, offices those of the chosen state/district/type, and so on. Each list is
 * computed ignoring its own filter so the current choice stays visible and can be changed.
 */
data class FilterOptions(
    val states: List<String>,
    val districts: List<String>,
    val officeTypes: List<OfficeType>,
    val offices: List<String>,
    val beats: List<String>,
    val pincodes: List<String>,
) {
    companion object {
        val EMPTY = FilterOptions(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

        fun from(facets: List<FilterFacet>, f: BeatSearchFilters): FilterOptions {
            fun rows(ignore: (BeatSearchFilters) -> BeatSearchFilters) = facets.filter { it.matches(ignore(f)) }
            val byName = compareBy<String> { it.lowercase() }
            return FilterOptions(
                // District depends on state, so switching state must not be blocked by the current district.
                states = rows { it.copy(state = null, district = null) }.map { it.state }.distinct().sortedWith(byName),
                districts = rows { it.copy(district = null) }.map { it.district }.distinct().sortedWith(byName),
                officeTypes = rows { it.copy(officeType = null) }.map { it.officeType }.distinct().sortedBy { it.ordinal },
                offices = rows { it.copy(officeName = null) }.map { it.officeName }.distinct().sortedWith(byName),
                beats = rows { it.copy(beatNumber = null) }.map { it.beatNumber }.distinct().sortedWith { a, b -> BeatGrouping.naturalCompare(a, b) },
                pincodes = rows { it.copy(pincode = null) }.map { it.pincode }.distinct().sorted(),
            )
        }

        /** Drops any selection that no longer exists in the directory (after a delete or import). */
        fun prune(facets: List<FilterFacet>, f: BeatSearchFilters): BeatSearchFilters {
            if (f.isEmpty || facets.isEmpty()) return if (facets.isEmpty()) BeatSearchFilters() else f
            var out = f
            if (out.state != null && facets.none { it.state.equals(out.state, true) }) out = out.copy(state = null)
            if (out.district != null && facets.none { it.district.equals(out.district, true) }) out = out.copy(district = null)
            if (out.officeName != null && facets.none { it.officeName.equals(out.officeName, true) }) out = out.copy(officeName = null)
            if (out.beatNumber != null && facets.none { it.beatNumber.equals(out.beatNumber, true) }) out = out.copy(beatNumber = null)
            if (out.pincode != null && facets.none { it.pincode == out.pincode }) out = out.copy(pincode = null)
            return out
        }
    }
}

fun FilterFacet.matches(f: BeatSearchFilters): Boolean =
    (f.state == null || state.equals(f.state, ignoreCase = true)) &&
        (f.district == null || district.equals(f.district, ignoreCase = true)) &&
        (f.officeType == null || officeType == f.officeType) &&
        (f.officeName == null || officeName.equals(f.officeName, ignoreCase = true)) &&
        (f.beatNumber == null || beatNumber.equals(f.beatNumber, ignoreCase = true)) &&
        (f.pincode == null || pincode == f.pincode)
