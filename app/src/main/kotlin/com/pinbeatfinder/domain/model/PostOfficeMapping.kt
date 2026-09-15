package com.pinbeatfinder.domain.model

/** Strips the trailing office-type marker: "Rampur B.O" -> "Rampur", "Sitapur S.O" -> "Sitapur". */
fun PostOffice.plainName(): String = OfficeType.stripSuffix(name)

/**
 * Pre-fills a local directory draft from a directory result. The office type and name come from
 * the result; the account office is known when the result came from the bundled directory. The
 * beat number is never known, so it is left for the user.
 */
fun PostOffice.toBeatDraft(): BeatDraft = BeatDraft(localityName = plainName()).withOffice(this)

/**
 * Copies the office fields of [office] into this draft (type, name, account office, district,
 * state, PIN) and keeps everything the user typed about the village itself.
 */
fun BeatDraft.withOffice(office: PostOffice): BeatDraft = copy(
    officeType = office.officeType.code,
    officeName = office.plainName(),
    accountOffice = office.accountOffice.trim(),
    district = tidyCase(office.district),
    state = tidyCase(office.state),
    pincode = office.pincode,
)

/**
 * The government dataset shouts district and state names ("DHANBAD", "JHARKHAND"); the beat
 * directory filters are case-sensitive on distinct values, so bring all-caps names to
 * title case. Mixed-case input is returned untouched.
 */
fun tidyCase(raw: String): String {
    val v = raw.trim()
    if (v.isEmpty() || v.any { it.isLowerCase() }) return v
    return v.split(' ').joinToString(" ") { word ->
        if (word.length <= 2 && word.all { it.isLetter() }) word // keep "NW", "UP"-style tokens as typed
        else word.lowercase().replaceFirstChar { it.uppercase() }
    }
}
