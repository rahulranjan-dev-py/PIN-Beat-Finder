package com.pinbeatfinder.ui.local

import android.content.Intent
import android.net.Uri
import com.pinbeatfinder.data.excel.ImportMode
import com.pinbeatfinder.data.excel.ImportPreview
import com.pinbeatfinder.data.excel.ImportReport
import com.pinbeatfinder.core.dedupe.DuplicatePair
import com.pinbeatfinder.data.local.OfficeStats
import com.pinbeatfinder.domain.model.BeatDraft
import com.pinbeatfinder.domain.model.BeatField
import com.pinbeatfinder.domain.model.BeatGroup
import com.pinbeatfinder.domain.model.BeatRecord
import com.pinbeatfinder.domain.model.BeatSearchFilters
import com.pinbeatfinder.domain.model.BeatSearchHit
import com.pinbeatfinder.domain.model.FilterFacet
import com.pinbeatfinder.domain.model.FilterOptions
import com.pinbeatfinder.domain.model.FieldError
import com.pinbeatfinder.domain.model.OfficeSummary
import com.pinbeatfinder.domain.model.OfficeType
import com.pinbeatfinder.domain.model.PostOffice
import com.pinbeatfinder.ui.components.UiText

enum class LocalViewMode { SEARCH, BY_BEAT }

data class EditorState(
    val draft: BeatDraft,
    val errors: Map<BeatField, FieldError> = emptyMap(),
    val isSaving: Boolean = false,
    /** True while the built-in directory is being asked for the offices under the typed PIN. */
    val isFetching: Boolean = false,
    /** Offices under the draft's PIN, shown in a picker; null when the picker is closed. */
    val fetchedOffices: List<PostOffice>? = null,
    /** Local records already filed under each fetched office (key: lower-cased office name). */
    val officeStats: Map<String, OfficeStats> = emptyMap(),
    /** Directory offices whose name matches what is being typed in Office Name. */
    val officeSuggestions: List<PostOffice> = emptyList(),
    /** SO/HO/GPO offices matching what is being typed in Account Office. */
    val accountSuggestions: List<PostOffice> = emptyList(),
    /** Existing local records whose locality sounds like what is being typed (near-duplicate warning). */
    val similarExisting: List<BeatSearchHit> = emptyList(),
    /** Offices ticked in the picker (by directory name); empty means single-tap mode. */
    val pickerSelection: Set<String> = emptySet(),
    /** Offices still to be entered after this one when several were ticked in the picker. */
    val queue: List<PostOffice> = emptyList(),
    /** How many offices the current batch started with (0 when not in batch mode). */
    val queueTotal: Int = 0,
) {
    val isNew: Boolean get() = draft.id == 0L
    val isBatch: Boolean get() = queueTotal > 0
    /** 1-based position of the record being entered within the batch. */
    val queuePosition: Int get() = queueTotal - queue.size
}

data class LocalBeatsState(
    val query: String = "",
    val filters: BeatSearchFilters = BeatSearchFilters(),
    /** Distinct combinations in the directory; the filter sheet derives its choices from these. */
    val facets: List<FilterFacet> = emptyList(),
    val showFilters: Boolean = false,
    /** Known values for the editor's State/District suggestions. */
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
    /** One row per office for the bulk type fixer; derived with [beatGroups]. */
    val officeSummaries: List<OfficeSummary> = emptyList(),
    val showOfficeTypes: Boolean = false,
    /** Result of the duplicate scan; null while the dialog is closed. */
    val duplicates: List<DuplicatePair>? = null,
    val isScanningDuplicates: Boolean = false,

    /** Long-press multi-select; non-empty means selection mode is active. */
    val selectedIds: Set<Long> = emptySet(),
    val confirmBulkDelete: Boolean = false,
) {
    val hasFilters: Boolean get() = !filters.isEmpty
    val filterOptions: FilterOptions get() = FilterOptions.from(facets, filters)
    val isDirectoryEmpty: Boolean get() = totalRecords == 0
    val isSelecting: Boolean get() = selectedIds.isNotEmpty()
}

sealed interface LocalBeatsIntent {
    data class QueryChanged(val query: String) : LocalBeatsIntent
    data class FiltersChanged(val filters: BeatSearchFilters) : LocalBeatsIntent
    data object ClearFilters : LocalBeatsIntent
    data object ShowFilters : LocalBeatsIntent
    data object HideFilters : LocalBeatsIntent
    data class SetViewMode(val mode: LocalViewMode) : LocalBeatsIntent
    data class ToggleBeatExpanded(val key: String) : LocalBeatsIntent

    data class OpenEditor(val record: BeatRecord? = null) : LocalBeatsIntent
    /** Open the editor pre-filled from another source (e.g. an online result). */
    data class OpenEditorWithDraft(val draft: BeatDraft) : LocalBeatsIntent
    /** Show every local record for a PIN (bridge from the online tab). */
    data class ShowPincode(val pincode: String) : LocalBeatsIntent
    data class EditorFieldChanged(val field: BeatField, val value: String) : LocalBeatsIntent
    /** Look up the offices under the draft's PIN in the built-in directory and offer them as a picker. */
    data object FetchOfficesForPin : LocalBeatsIntent
    /** The user picked one of the fetched offices; fill the office fields from it. */
    data class OfficeSelected(val office: PostOffice) : LocalBeatsIntent
    data object DismissFetchedOffices : LocalBeatsIntent
    /** Tick/untick an office in the picker for batch entry. */
    data class TogglePickerOffice(val office: PostOffice) : LocalBeatsIntent
    /** Start batch entry with the ticked offices: first one fills the form, the rest queue up. */
    data object ConfirmPickerSelection : LocalBeatsIntent
    /** Batch entry: drop the current office without saving and move to the next queued one. */
    data object SkipQueued : LocalBeatsIntent

    data object ShowOfficeTypes : LocalBeatsIntent
    data object HideOfficeTypes : LocalBeatsIntent
    /** Change the type of every record of one office at once. */
    data class SetOfficeType(val office: OfficeSummary, val type: OfficeType) : LocalBeatsIntent

    /** Share one beat (the group's records) as a spreadsheet. */
    data class ShareBeat(val group: BeatGroup) : LocalBeatsIntent
    /** Share every beat of the group's office (same name and PINs) as a spreadsheet. */
    data class ShareOffice(val group: BeatGroup) : LocalBeatsIntent
    /** Same two scopes as printable PDFs. */
    data class PrintBeat(val group: BeatGroup) : LocalBeatsIntent
    data class PrintOffice(val group: BeatGroup) : LocalBeatsIntent

    /** Scan the whole directory for probable duplicate localities. */
    data object FindDuplicates : LocalBeatsIntent
    data object DismissDuplicates : LocalBeatsIntent
    /** Keep [keep] (with the other's remarks merged in when it has none) and delete its twin. */
    data class ResolveDuplicate(val pair: DuplicatePair, val keep: BeatRecord) : LocalBeatsIntent
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
