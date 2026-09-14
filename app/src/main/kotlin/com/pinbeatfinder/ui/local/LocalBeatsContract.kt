package com.pinbeatfinder.ui.local

import android.content.Intent
import android.net.Uri
import com.pinbeatfinder.data.excel.ImportMode
import com.pinbeatfinder.data.excel.ImportReport
import com.pinbeatfinder.domain.model.BeatDraft
import com.pinbeatfinder.domain.model.BeatField
import com.pinbeatfinder.domain.model.BeatRecord
import com.pinbeatfinder.domain.model.BeatSearchHit

data class EditorState(
    val draft: BeatDraft,
    val errors: Map<BeatField, String> = emptyMap(),
    val isSaving: Boolean = false,
) {
    val isNew: Boolean get() = draft.id == 0L
}

data class LocalBeatsState(
    val query: String = "",
    val selectedState: String? = null,
    val selectedDistrict: String? = null,
    val states: List<String> = emptyList(),
    val districts: List<String> = emptyList(),
    val hits: List<BeatSearchHit> = emptyList(),
    val isSearching: Boolean = false,
    val totalRecords: Int = 0,
    val editor: EditorState? = null,
    val pendingDelete: BeatRecord? = null,
    val importReport: ImportReport? = null,
    /** Non-null while a long-running file operation blocks the screen (e.g. "Importing…"). */
    val busyMessage: String? = null,
) {
    val hasFilters: Boolean get() = selectedState != null || selectedDistrict != null
    val isDirectoryEmpty: Boolean get() = totalRecords == 0
}

sealed interface LocalBeatsIntent {
    data class QueryChanged(val query: String) : LocalBeatsIntent
    data class StateSelected(val state: String?) : LocalBeatsIntent
    data class DistrictSelected(val district: String?) : LocalBeatsIntent
    data object ClearFilters : LocalBeatsIntent

    data class OpenEditor(val record: BeatRecord? = null) : LocalBeatsIntent
    data class EditorFieldChanged(val field: BeatField, val value: String) : LocalBeatsIntent
    data object SaveEditor : LocalBeatsIntent
    data object DismissEditor : LocalBeatsIntent

    data class RequestDelete(val record: BeatRecord) : LocalBeatsIntent
    data object ConfirmDelete : LocalBeatsIntent
    data object CancelDelete : LocalBeatsIntent

    data class ImportFile(val uri: Uri, val mode: ImportMode) : LocalBeatsIntent
    data object DismissImportReport : LocalBeatsIntent
    data object ShareBackup : LocalBeatsIntent
    data class SaveBackupTo(val uri: Uri) : LocalBeatsIntent
    data object ShareTemplate : LocalBeatsIntent
    data class SaveTemplateTo(val uri: Uri) : LocalBeatsIntent
}

sealed interface LocalBeatsEffect {
    data class ShowMessage(val text: String) : LocalBeatsEffect
    data class LaunchIntent(val intent: Intent) : LocalBeatsEffect
}
