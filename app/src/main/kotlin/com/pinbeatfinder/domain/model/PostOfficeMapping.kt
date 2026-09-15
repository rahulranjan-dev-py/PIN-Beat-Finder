package com.pinbeatfinder.domain.model

private val OFFICE_SUFFIX = Regex("""\s*\(?\b(B\.?O|S\.?O|H\.?O|G\.?P\.?O)\.?\)?\s*$""", RegexOption.IGNORE_CASE)

/** Strips the trailing office-type marker: "Rampur B.O" -> "Rampur", "Sitapur S.O" -> "Sitapur". */
fun PostOffice.plainName(): String = name.replace(OFFICE_SUFFIX, "").trim().ifEmpty { name }

/**
 * Pre-fills a local directory draft from an online result. A branch office becomes the BO
 * column; a sub/head office becomes the SO column. The beat number is unknown online, so it
 * is left for the user.
 */
fun PostOffice.toBeatDraft(): BeatDraft {
    val isBranch = branchType.contains("branch", ignoreCase = true)
    return BeatDraft(
        localityName = plainName(),
        branchOffice = if (isBranch) name else "",
        subPostOffice = if (isBranch) "" else name,
        district = district,
        state = state,
        pincode = pincode,
    )
}
