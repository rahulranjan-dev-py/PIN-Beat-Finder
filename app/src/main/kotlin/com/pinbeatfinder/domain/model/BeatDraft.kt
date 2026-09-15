package com.pinbeatfinder.domain.model

/**
 * Unvalidated user/spreadsheet input for a [BeatRecord]. Every field is a raw string so the same
 * validator serves the Compose editor and the Excel importer.
 */
data class BeatDraft(
    val id: Long = 0L,
    val localityName: String = "",
    /** Office type code as typed/selected (BO, SO, HO, GPO, IDC); validated by [OfficeType.parse]. */
    val officeType: String = OfficeType.BO.code,
    val officeName: String = "",
    val accountOffice: String = "",
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
            officeType = record.officeType.code,
            officeName = record.officeName,
            accountOffice = record.accountOffice,
            beatNumber = record.beatNumber,
            district = record.district,
            state = record.state,
            pincode = record.pincode,
            remarks = record.remarks,
        )
    }
}

/** Columns of the spreadsheet / fields of the editor. `label` is the English spreadsheet header. */
enum class BeatField(val label: String, val required: Boolean) {
    LOCALITY("Locality/Village Name", true),
    OFFICE_TYPE("Office Type", true),
    OFFICE_NAME("Office Name", true),
    ACCOUNT_OFFICE("Account Office (SO/HO)", false),
    BEAT_NUMBER("Beat Number", true),
    DISTRICT("District", true),
    STATE("State", true),
    PINCODE("Pincode", true),
    REMARKS("Remarks", false),
}

/** Why a field failed validation. The UI turns these into localised text; Excel reports use [describe]. */
enum class FieldError {
    REQUIRED, TOO_LONG, INVALID_PINCODE, INVALID_OFFICE_TYPE;

    fun describe(field: BeatField): String = when (this) {
        REQUIRED -> "${field.label} is required"
        TOO_LONG -> "${field.label} is too long"
        INVALID_PINCODE -> "Pincode must be 6 digits and not start with 0"
        INVALID_OFFICE_TYPE -> "Office Type must be one of GPO, HO, IDC, SO, BO"
    }
}

/** Outcome of validating a [BeatDraft]: either a clean record or per-field error codes. */
sealed interface DraftValidation {
    data class Valid(val record: BeatRecord) : DraftValidation
    data class Invalid(val errors: Map<BeatField, FieldError>) : DraftValidation
}
