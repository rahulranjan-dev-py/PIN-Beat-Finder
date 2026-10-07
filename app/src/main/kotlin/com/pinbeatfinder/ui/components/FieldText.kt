package com.pinbeatfinder.ui.components

import android.content.Context
import androidx.annotation.StringRes
import com.pinbeatfinder.R
import com.pinbeatfinder.domain.model.BeatField
import com.pinbeatfinder.domain.model.FieldError
import com.pinbeatfinder.domain.model.OfficeType

/** Localised labels for the editor fields; the English `BeatField.label` stays for spreadsheet headers. */
@StringRes
fun BeatField.labelRes(): Int = when (this) {
    BeatField.LOCALITY -> R.string.field_locality
    BeatField.OFFICE_TYPE -> R.string.field_office_type
    BeatField.OFFICE_NAME -> R.string.field_office_name
    BeatField.ACCOUNT_OFFICE -> R.string.field_account_office
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
        FieldError.INVALID_OFFICE_TYPE -> context.getString(R.string.field_invalid_office_type)
    }
}

/** Localised full name of an office kind; `OfficeType.fullName` stays English for spreadsheets. */
@StringRes
fun OfficeType.labelRes(): Int = when (this) {
    OfficeType.GPO -> R.string.office_type_gpo
    OfficeType.HO -> R.string.office_type_ho
    OfficeType.IDC -> R.string.office_type_idc
    OfficeType.SO -> R.string.office_type_so
    OfficeType.BO -> R.string.office_type_bo
}

/** Directory type labels ("Branch Office", "B.O", "SO"…) in the UI language; unknown text passes through. */
fun branchTypeLabel(context: Context, raw: String): String =
    OfficeType.parse(raw)?.let { context.getString(it.labelRes()) } ?: raw

/** "Delivery" / "Non-Delivery" from the directory in the UI language; unknown text passes through. */
fun deliveryLabel(context: Context, raw: String): String = when (raw.trim().lowercase()) {
    "delivery" -> context.getString(R.string.delivery_yes)
    "non-delivery", "non delivery", "nondelivery" -> context.getString(R.string.delivery_no)
    else -> raw
}
