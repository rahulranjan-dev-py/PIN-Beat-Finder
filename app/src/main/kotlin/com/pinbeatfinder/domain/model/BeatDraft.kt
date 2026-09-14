package com.pinbeatfinder.domain.model

/**
 * Unvalidated user/spreadsheet input for a [BeatRecord]. Every field is a raw string so the same
 * validator serves the Compose editor and the Excel importer.
 */
data class BeatDraft(
    val id: Long = 0L,
    val localityName: String = "",
    val branchOffice: String = "",
    val subPostOffice: String = "",
    val beatNumber: String = "",
    val district: String = "",
    val state: String = "",
    val pincode: String = "",
    val remarks: String = "",
) {
    companion object {
        fun from(record: BeatRecord) = BeatDraft(
            id = record.id,
            localityName = record.localityName,
            branchOffice = record.branchOffice,
            subPostOffice = record.subPostOffice,
            beatNumber = record.beatNumber,
            district = record.district,
            state = record.state,
            pincode = record.pincode,
            remarks = record.remarks,
        )
    }
}

enum class BeatField(val label: String, val required: Boolean) {
    LOCALITY("Locality/Village Name", true),
    BRANCH_OFFICE("Branch Office (BO)", true),
    SUB_POST_OFFICE("Sub Post Office (SO)", true),
    BEAT_NUMBER("Beat Number", true),
    DISTRICT("District", true),
    STATE("State", true),
    PINCODE("Pincode", true),
    REMARKS("Remarks", false),
}

/** Outcome of validating a [BeatDraft]: either a clean record or per-field messages. */
sealed interface DraftValidation {
    data class Valid(val record: BeatRecord) : DraftValidation
    data class Invalid(val errors: Map<BeatField, String>) : DraftValidation
}
