package com.pinbeatfinder.ui.components

import android.content.Context
import androidx.annotation.StringRes
import com.pinbeatfinder.R
import com.pinbeatfinder.domain.model.BeatField
import com.pinbeatfinder.domain.model.FieldError

/** Localised labels for the editor fields; the English `BeatField.label` stays for spreadsheet headers. */
@StringRes
fun BeatField.labelRes(): Int = when (this) {
    BeatField.LOCALITY -> R.string.field_locality
    BeatField.BRANCH_OFFICE -> R.string.field_bo
    BeatField.SUB_POST_OFFICE -> R.string.field_so
    BeatField.BEAT_NUMBER -> R.string.field_beat
    BeatField.DISTRICT -> R.string.field_district
    BeatField.STATE -> R.string.field_state
    BeatField.PINCODE -> R.string.field_pincode
    BeatField.REMARKS -> R.string.field_remarks
}

fun FieldError.message(context: Context, field: BeatField): String {
    val label = context.getString(field.labelRes())
    return when (this) {
        FieldError.REQUIRED -> context.getString(R.string.field_required, label)
        FieldError.TOO_LONG -> context.getString(R.string.field_too_long, label)
        FieldError.INVALID_PINCODE -> context.getString(R.string.field_invalid_pin)
    }
}
