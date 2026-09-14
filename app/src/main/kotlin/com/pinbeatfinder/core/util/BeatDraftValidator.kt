package com.pinbeatfinder.core.util

import com.pinbeatfinder.domain.model.BeatDraft
import com.pinbeatfinder.domain.model.BeatField
import com.pinbeatfinder.domain.model.BeatRecord
import com.pinbeatfinder.domain.model.DraftValidation

/**
 * Single source of truth for what a valid beat row looks like. Used by the manual editor and
 * by the Excel importer so both paths reject exactly the same input.
 */
object BeatDraftValidator {
    private const val MAX_TEXT = 120
    private const val MAX_REMARKS = 500

    fun validate(draft: BeatDraft, now: Long = System.currentTimeMillis()): DraftValidation {
        val errors = linkedMapOf<BeatField, String>()

        fun requiredText(field: BeatField, raw: String): String {
            val v = raw.trim()
            when {
                v.isEmpty() -> errors[field] = "${field.label} is required"
                v.length > MAX_TEXT -> errors[field] = "${field.label} is too long (max $MAX_TEXT)"
            }
            return v
        }

        val locality = requiredText(BeatField.LOCALITY, draft.localityName)
        val bo = requiredText(BeatField.BRANCH_OFFICE, draft.branchOffice)
        val so = requiredText(BeatField.SUB_POST_OFFICE, draft.subPostOffice)
        val beat = requiredText(BeatField.BEAT_NUMBER, normalizeNumberish(draft.beatNumber))
        val district = requiredText(BeatField.DISTRICT, draft.district)
        val state = requiredText(BeatField.STATE, draft.state)

        val pincode = PinCodeValidator.normalize(draft.pincode)
        if (pincode == null) {
            errors[BeatField.PINCODE] = if (draft.pincode.isBlank()) {
                "Pincode is required"
            } else {
                "Pincode must be 6 digits and not start with 0"
            }
        }

        val remarks = draft.remarks.trim()
        if (remarks.length > MAX_REMARKS) {
            errors[BeatField.REMARKS] = "Remarks are too long (max $MAX_REMARKS)"
        }

        if (errors.isNotEmpty()) return DraftValidation.Invalid(errors)

        return DraftValidation.Valid(
            BeatRecord(
                id = draft.id,
                localityName = locality,
                branchOffice = bo,
                subPostOffice = so,
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
