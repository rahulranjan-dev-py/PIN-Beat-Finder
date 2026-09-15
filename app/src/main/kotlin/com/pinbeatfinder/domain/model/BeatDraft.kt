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

/** Why a field failed validation. The UI turns these into localised text; Excel reports use [describe]. */
enum class FieldError {
    REQUIRED, TOO_LONG, INVALID_PINCODE;

    fun describe(field: BeatField): String = when (this) {
        REQUIRED -> "${field.label} is required"
        TOO_LONG -> "${field.label} is too long"
        INVALID_PINCODE -> "Pincode must be 6 digits and not start with 0"
    }
}

/** Outcome of validating a [BeatDraft]: either a clean record or per-field error codes. */
sealed interface DraftValidation {
    data class Valid(val record: BeatRecord) : DraftValidation
    data class Invalid(val errors: Map<BeatField, FieldError>) : DraftValidation
}
