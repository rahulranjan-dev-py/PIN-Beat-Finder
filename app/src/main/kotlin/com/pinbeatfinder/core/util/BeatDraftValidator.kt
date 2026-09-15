package com.pinbeatfinder.core.util

import com.pinbeatfinder.domain.model.BeatDraft
import com.pinbeatfinder.domain.model.BeatField
import com.pinbeatfinder.domain.model.BeatRecord
import com.pinbeatfinder.domain.model.DraftValidation
import com.pinbeatfinder.domain.model.FieldError
import com.pinbeatfinder.domain.model.OfficeType

/**
 * Single source of truth for what a valid beat row looks like. Used by the manual editor and
 * by the Excel importer so both paths reject exactly the same input.
 */
object BeatDraftValidator {
    private const val MAX_TEXT = 120
    private const val MAX_REMARKS = 500

    fun validate(draft: BeatDraft, now: Long = System.currentTimeMillis()): DraftValidation {
        val errors = linkedMapOf<BeatField, FieldError>()

        fun text(field: BeatField, raw: String, required: Boolean = true): String {
            val v = raw.trim()
            when {
                required && v.isEmpty() -> errors[field] = FieldError.REQUIRED
                v.length > MAX_TEXT -> errors[field] = FieldError.TOO_LONG
            }
            return v
        }

        val locality = text(BeatField.LOCALITY, draft.localityName)
        val officeType = OfficeType.parse(draft.officeType)
        if (officeType == null) {
            errors[BeatField.OFFICE_TYPE] = if (draft.officeType.isBlank()) FieldError.REQUIRED else FieldError.INVALID_OFFICE_TYPE
        }
        val officeName = text(BeatField.OFFICE_NAME, draft.officeName)
        val accountOffice = text(BeatField.ACCOUNT_OFFICE, draft.accountOffice, required = false)
        val beat = text(BeatField.BEAT_NUMBER, normalizeNumberish(draft.beatNumber))
        val district = text(BeatField.DISTRICT, draft.district)
        val state = text(BeatField.STATE, draft.state)

        val pincode = PinCodeValidator.normalize(draft.pincode)
        if (pincode == null) {
            errors[BeatField.PINCODE] = if (draft.pincode.isBlank()) FieldError.REQUIRED else FieldError.INVALID_PINCODE
        }

        val remarks = draft.remarks.trim()
        if (remarks.length > MAX_REMARKS) {
            errors[BeatField.REMARKS] = FieldError.TOO_LONG
        }

        if (errors.isNotEmpty()) return DraftValidation.Invalid(errors)

        return DraftValidation.Valid(
            BeatRecord(
                id = draft.id,
                localityName = locality,
                officeType = officeType!!,
                officeName = officeName,
                accountOffice = accountOffice,
                beatNumber = beat,
                district = district,
                state = state,
                pincode = pincode!!,
                remarks = remarks,
                updatedAt = now,
            ),
        )
    }

    /** Spreadsheets hand back "3.0" for a numeric beat "3"; strip the pointless fraction. */
    private fun normalizeNumberish(raw: String): String {
        val v = raw.trim()
        return if (v.matches(Regex("^\\d+\\.0+$"))) v.substringBefore('.') else v
    }
}
