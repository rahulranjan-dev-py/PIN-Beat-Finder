package com.pinbeatfinder.ui.local

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinbeatfinder.core.util.BeatDraftValidator
import com.pinbeatfinder.data.excel.ExcelFormatException
import com.pinbeatfinder.data.excel.ExcelSyncManager
import com.pinbeatfinder.data.excel.ImportMode
import com.pinbeatfinder.data.repository.BeatDirectoryRepository
import com.pinbeatfinder.domain.model.BeatDraft
import com.pinbeatfinder.domain.model.BeatField
import com.pinbeatfinder.domain.model.BeatGrouping
import com.pinbeatfinder.domain.model.BeatRecord
import com.pinbeatfinder.domain.model.BeatSearchFilters
import com.pinbeatfinder.domain.model.DraftValidation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI ViewModel for the offline directory tab.
 *
 * Two read pipelines share the filter state and a `dataVersion` counter that is bumped after
 * every write:
 *  - search: (query, state, district) → debounce 250 ms → `mapLatest` → ranked hits
 *  - by-beat: (state, district) → all rows → grouped by (BO, beat)
 */
class LocalBeatsViewModel(
    private val repository: BeatDirectoryRepository,
    private val excel: ExcelSyncManager,
) : ViewModel() {

    private val _state = MutableStateFlow(LocalBeatsState())
    val state: StateFlow<LocalBeatsState> = _state.asStateFlow()

    private val _effects = Channel<LocalBeatsEffect>(Channel.BUFFERED)
    val effects: Flow<LocalBeatsEffect> = _effects.receiveAsFlow()

    private val dataVersion = MutableStateFlow(0)

    private data class SearchKey(val query: String, val state: String?, val district: String?, val version: Int)
    private data class GroupKey(val state: String?, val district: String?, val version: Int, val active: Boolean)

    init {
        combine(
            _state.map { Triple(it.query, it.selectedState, it.selectedDistrict) }.distinctUntilChanged(),
            dataVersion,
        ) { (q, s, d), v -> SearchKey(q, s, d, v) }
            .debounce { key -> if (key.query.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
            .onEach { _state.update { it.copy(isSearching = true) } }
            .mapLatest { key -> repository.search(key.query, BeatSearchFilters(key.state, key.district)) }
            .onEach { hits -> _state.update { it.copy(hits = hits, isSearching = false) } }
            .launchIn(viewModelScope)

        combine(
            _state.map { Triple(it.selectedState, it.selectedDistrict, it.viewMode == LocalViewMode.BY_BEAT) }.distinctUntilChanged(),
            dataVersion,
        ) { (s, d, active), v -> GroupKey(s, d, v, active) }
            .mapLatest { key ->
                if (!key.active) emptyList()
                else BeatGrouping.group(repository.listAll(BeatSearchFilters(key.state, key.district)))
            }
            .onEach { groups -> _state.update { it.copy(beatGroups = groups) } }
            .launchIn(viewModelScope)

        repository.observeStates()
            .onEach { states ->
                _state.update { s -> s.copy(states = states, selectedState = s.selectedState?.takeIf { it in states }) }
            }
            .launchIn(viewModelScope)

        _state.map { it.selectedState }.distinctUntilChanged()
            .flatMapLatest { repository.observeDistricts(it) }
            .onEach { districts ->
                _state.update { s -> s.copy(districts = districts, selectedDistrict = s.selectedDistrict?.takeIf { it in districts }) }
            }
            .launchIn(viewModelScope)

        repository.observeCount()
            .onEach { count -> _state.update { it.copy(totalRecords = count) } }
            .launchIn(viewModelScope)

        viewModelScope.launch { runCatching { excel.pruneOldExports() } }
    }

    fun onIntent(intent: LocalBeatsIntent) {
        when (intent) {
            is LocalBeatsIntent.QueryChanged -> _state.update { it.copy(query = intent.query) }
            is LocalBeatsIntent.StateSelected -> _state.update { it.copy(selectedState = intent.state, selectedDistrict = null) }
            is LocalBeatsIntent.DistrictSelected -> _state.update { it.copy(selectedDistrict = intent.district) }
            LocalBeatsIntent.ClearFilters -> _state.update { it.copy(selectedState = null, selectedDistrict = null) }
            is LocalBeatsIntent.SetViewMode -> _state.update { it.copy(viewMode = intent.mode, selectedIds = emptySet()) }
            is LocalBeatsIntent.ToggleBeatExpanded -> _state.update {
                it.copy(expandedBeats = if (intent.key in it.expandedBeats) it.expandedBeats - intent.key else it.expandedBeats + intent.key)
            }

            is LocalBeatsIntent.OpenEditor -> _state.update {
                val draft = intent.record?.let(BeatDraft::from) ?: BeatDraft(
                    state = it.selectedState.orEmpty(),
                    district = it.selectedDistrict.orEmpty(),
                )
                it.copy(editor = EditorState(draft))
            }
            is LocalBeatsIntent.OpenEditorWithDraft -> _state.update { it.copy(editor = EditorState(intent.draft)) }
            is LocalBeatsIntent.ShowPincode -> _state.update {
                it.copy(query = intent.pincode, selectedState = null, selectedDistrict = null, viewMode = LocalViewMode.SEARCH)
            }
            is LocalBeatsIntent.EditorFieldChanged -> updateEditorField(intent.field, intent.value)
            LocalBeatsIntent.SaveEditor -> saveEditor()
            LocalBeatsIntent.DismissEditor -> _state.update { it.copy(editor = null) }
            LocalBeatsIntent.DuplicateInEditor -> _state.update { s ->
                val d = s.editor?.draft ?: return@update s
                s.copy(editor = EditorState(d.copy(id = 0L, localityName = "", remarks = "")))
            }

            is LocalBeatsIntent.RequestDelete -> _state.update { it.copy(pendingDelete = intent.record) }
            LocalBeatsIntent.CancelDelete -> _state.update { it.copy(pendingDelete = null) }
            LocalBeatsIntent.ConfirmDelete -> confirmDelete()
            is LocalBeatsIntent.SwipeDelete -> swipeDelete(intent.record)
            is LocalBeatsIntent.UndoDelete -> undoDelete(intent.record)

            is LocalBeatsIntent.ToggleSelected -> _state.update {
                it.copy(selectedIds = if (intent.id in it.selectedIds) it.selectedIds - intent.id else it.selectedIds + intent.id)
            }
            LocalBeatsIntent.ClearSelection -> _state.update { it.copy(selectedIds = emptySet(), confirmBulkDelete = false) }
            LocalBeatsIntent.RequestBulkDelete -> _state.update { it.copy(confirmBulkDelete = it.selectedIds.isNotEmpty()) }
            LocalBeatsIntent.CancelBulkDelete -> _state.update { it.copy(confirmBulkDelete = false) }
            LocalBeatsIntent.ConfirmBulkDelete -> bulkDelete()

            is LocalBeatsIntent.ImportFile -> prepareImport(intent)
            LocalBeatsIntent.ConfirmImport -> commitImport()
            LocalBeatsIntent.CancelImport -> _state.update { it.copy(importPreview = null) }
            LocalBeatsIntent.DismissImportReport -> _state.update { it.copy(importReport = null) }
            LocalBeatsIntent.ShareBackup -> fileOperation("Preparing backup…") {
                val file = excel.exportBackup()
                _effects.send(LocalBeatsEffect.LaunchIntent(excel.shareIntent(file, "Share beat directory backup")))
            }
            is LocalBeatsIntent.SaveBackupTo -> fileOperation("Saving backup…") {
                excel.saveBackupTo(intent.uri)
                _effects.send(LocalBeatsEffect.ShowMessage("Backup saved."))
            }
            LocalBeatsIntent.ShareTemplate -> fileOperation("Preparing template…") {
                val file = excel.exportTemplate()
                _effects.send(LocalBeatsEffect.LaunchIntent(excel.shareIntent(file, "Share import template")))
            }
            is LocalBeatsIntent.SaveTemplateTo -> fileOperation("Saving template…") {
                excel.saveTemplateTo(intent.uri)
                _effects.send(LocalBeatsEffect.ShowMessage("Template saved."))
            }
        }
    }

    // ------------------------------------------------------------------ editor

    private fun updateEditorField(field: BeatField, value: String) {
        _state.update { s ->
            val editor = s.editor ?: return@update s
            val d = editor.draft
            val draft = when (field) {
                BeatField.LOCALITY -> d.copy(localityName = value)
                BeatField.BRANCH_OFFICE -> d.copy(branchOffice = value)
                BeatField.SUB_POST_OFFICE -> d.copy(subPostOffice = value)
                BeatField.BEAT_NUMBER -> d.copy(beatNumber = value)
                BeatField.DISTRICT -> d.copy(district = value)
                BeatField.STATE -> d.copy(state = value)
                BeatField.PINCODE -> d.copy(pincode = value.filter(Char::isDigit).take(6))
                BeatField.REMARKS -> d.copy(remarks = value)
            }
            s.copy(editor = editor.copy(draft = draft, errors = editor.errors - field))
        }
    }

    private fun saveEditor() {
        val editor = _state.value.editor ?: return
        when (val validation = BeatDraftValidator.validate(editor.draft)) {
            is DraftValidation.Invalid -> _state.update { it.copy(editor = editor.copy(errors = validation.errors)) }
            is DraftValidation.Valid -> viewModelScope.launch {
                _state.update { it.copy(editor = editor.copy(isSaving = true)) }
                try {
                    repository.save(validation.record)
                    dataVersion.update { it + 1 }
                    _state.update { it.copy(editor = null) }
                    _effects.send(LocalBeatsEffect.ShowMessage(if (editor.isNew) "Record added." else "Record updated."))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update { it.copy(editor = editor.copy(isSaving = false)) }
                    _effects.send(LocalBeatsEffect.ShowMessage("Could not save: ${e.message}"))
                }
            }
        }
    }

    // ------------------------------------------------------------------ delete

    private fun confirmDelete() {
        val record = _state.value.pendingDelete ?: return
        _state.update { it.copy(pendingDelete = null) }
        swipeDelete(record)
    }

    private fun swipeDelete(record: BeatRecord) {
        viewModelScope.launch {
            try {
                repository.delete(record.id)
                dataVersion.update { it + 1 }
                _state.update { it.copy(selectedIds = it.selectedIds - record.id) }
                _effects.send(LocalBeatsEffect.ShowUndoDelete(record))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effects.send(LocalBeatsEffect.ShowMessage("Could not delete: ${e.message}"))
            }
        }
    }

    private fun undoDelete(record: BeatRecord) {
        viewModelScope.launch {
            try {
                repository.save(record.copy(id = 0L))
                dataVersion.update { it + 1 }
                _effects.send(LocalBeatsEffect.ShowMessage("Restored ${record.localityName}."))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effects.send(LocalBeatsEffect.ShowMessage("Could not restore: ${e.message}"))
            }
        }
    }

    private fun bulkDelete() {
        val ids = _state.value.selectedIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.deleteMany(ids)
                dataVersion.update { it + 1 }
                _state.update { it.copy(selectedIds = emptySet(), confirmBulkDelete = false) }
                _effects.send(LocalBeatsEffect.ShowMessage("Deleted ${ids.size} record(s)."))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(confirmBulkDelete = false) }
                _effects.send(LocalBeatsEffect.ShowMessage("Could not delete: ${e.message}"))
            }
        }
    }

    // ------------------------------------------------------------------ excel

    private fun prepareImport(intent: LocalBeatsIntent.ImportFile) = fileOperation("Reading spreadsheet…") {
        try {
            val preview = excel.prepareImport(intent.uri, intent.mode)
            _state.update { it.copy(importPreview = preview) }
        } catch (e: ExcelFormatException) {
            _effects.send(LocalBeatsEffect.ShowMessage(e.message ?: "Unrecognised spreadsheet format."))
        }
    }

    private fun commitImport() {
        val preview = _state.value.importPreview ?: return
        _state.update { it.copy(importPreview = null) }
        fileOperation(if (preview.mode == ImportMode.REPLACE_ALL) "Replacing directory…" else "Importing…") {
            val report = excel.commitImport(preview)
            dataVersion.update { it + 1 }
            _state.update { it.copy(importReport = report) }
        }
    }

    private fun fileOperation(busyMessage: String, block: suspend () -> Unit) {
        if (_state.value.busyMessage != null) return // one file job at a time
        viewModelScope.launch {
            _state.update { it.copy(busyMessage = busyMessage) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effects.send(LocalBeatsEffect.ShowMessage("File operation failed: ${e.message ?: e::class.simpleName}"))
            } finally {
                _state.update { it.copy(busyMessage = null) }
            }
        }
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
