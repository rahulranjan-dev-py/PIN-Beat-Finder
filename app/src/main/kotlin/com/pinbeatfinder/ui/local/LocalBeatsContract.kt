package com.pinbeatfinder.ui.local

import android.content.Intent
import android.net.Uri
import com.pinbeatfinder.data.excel.ImportMode
import com.pinbeatfinder.data.excel.ImportPreview
import com.pinbeatfinder.data.excel.ImportReport
import com.pinbeatfinder.domain.model.BeatDraft
import com.pinbeatfinder.domain.model.BeatField
import com.pinbeatfinder.domain.model.BeatGroup
import com.pinbeatfinder.domain.model.BeatRecord
import com.pinbeatfinder.domain.model.BeatSearchHit
import com.pinbeatfinder.domain.model.FieldError
import com.pinbeatfinder.ui.components.UiText

enum class LocalViewMode { SEARCH, BY_BEAT }

data class EditorState(
    val draft: BeatDraft,
    val errors: Map<BeatField, FieldError> = emptyMap(),
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
    val importPreview: ImportPreview? = null,
    val importReport: ImportReport? = null,
    /** Non-null while a long-running file operation blocks the screen (e.g. "Importing…"). */
    val busyMessage: UiText? = null,

    val viewMode: LocalViewMode = LocalViewMode.SEARCH,
    val beatGroups: List<BeatGroup> = emptyList(),
    val expandedBeats: Set<String> = emptySet(),

    /** Long-press multi-select; non-empty means selection mode is active. */
    val selectedIds: Set<Long> = emptySet(),
    val confirmBulkDelete: Boolean = false,
) {
    val hasFilters: Boolean get() = selectedState != null || selectedDistrict != null
    val isDirectoryEmpty: Boolean get() = totalRecords == 0
    val isSelecting: Boolean get() = selectedIds.isNotEmpty()
}

sealed interface LocalBeatsIntent {
    data class QueryChanged(val query: String) : LocalBeatsIntent
    data class StateSelected(val state: String?) : LocalBeatsIntent
    data class DistrictSelected(val district: String?) : LocalBeatsIntent
    data object ClearFilters : LocalBeatsIntent
    data class SetViewMode(val mode: LocalViewMode) : LocalBeatsIntent
    data class ToggleBeatExpanded(val key: String) : LocalBeatsIntent

    data class OpenEditor(val record: BeatRecord? = null) : LocalBeatsIntent
    /** Open the editor pre-filled from another source (e.g. an online result). */
    data class OpenEditorWithDraft(val draft: BeatDraft) : LocalBeatsIntent
    /** Show every local record for a PIN (bridge from the online tab). */
    data class ShowPincode(val pincode: String) : LocalBeatsIntent
    data class EditorFieldChanged(val field: BeatField, val value: String) : LocalBeatsIntent
    data object SaveEditor : LocalBeatsIntent
    data object DismissEditor : LocalBeatsIntent
    /** Turn the record being edited into a new one on the same beat with a blank locality. */
    data object DuplicateInEditor : LocalBeatsIntent

    data class RequestDelete(val record: BeatRecord) : LocalBeatsIntent
    data object ConfirmDelete : LocalBeatsIntent
    data object CancelDelete : LocalBeatsIntent
    /** Delete immediately (swipe) and offer Undo via effect. */
    data class SwipeDelete(val record: BeatRecord) : LocalBeatsIntent
    data class UndoDelete(val record: BeatRecord) : LocalBeatsIntent

    data class ToggleSelected(val id: Long) : LocalBeatsIntent
    data object ClearSelection : LocalBeatsIntent
    data object RequestBulkDelete : LocalBeatsIntent
    data object ConfirmBulkDelete : LocalBeatsIntent
    data object CancelBulkDelete : LocalBeatsIntent

    data class ImportFile(val uri: Uri, val mode: ImportMode) : LocalBeatsIntent
    data object ConfirmImport : LocalBeatsIntent
    data object CancelImport : LocalBeatsIntent
    data object DismissImportReport : LocalBeatsIntent
    data object ShareBackup : LocalBeatsIntent
    data class SaveBackupTo(val uri: Uri) : LocalBeatsIntent
    data object ShareTemplate : LocalBeatsIntent
    data class SaveTemplateTo(val uri: Uri) : LocalBeatsIntent
}

sealed interface LocalBeatsEffect {
    data class ShowMessage(val text: UiText) : LocalBeatsEffect
    data class LaunchIntent(val intent: Intent) : LocalBeatsEffect
    /** Snackbar with an Undo action that re-inserts [record]. */
    data class ShowUndoDelete(val record: BeatRecord) : LocalBeatsEffect
    /** A record was saved; the screen may vibrate briefly. */
    data object Saved : LocalBeatsEffect
}
