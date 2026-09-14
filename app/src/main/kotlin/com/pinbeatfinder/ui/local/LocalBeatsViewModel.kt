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
 * The search pipeline is a single cold flow: (query, state, district, dataVersion) →
 * debounce 250 ms → `mapLatest` (cancels a stale search when the user keeps typing) →
 * repository. `dataVersion` is bumped after every write so the list refreshes without the
 * user retyping.
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

        repository.observeStates()
            .onEach { states ->
                _state.update { s ->
                    s.copy(
                        states = states,
                        selectedState = s.selectedState?.takeIf { it in states },
                    )
                }
            }
            .launchIn(viewModelScope)

        _state.map { it.selectedState }.distinctUntilChanged()
            .flatMapLatest { repository.observeDistricts(it) }
            .onEach { districts ->
                _state.update { s ->
                    s.copy(
                        districts = districts,
                        selectedDistrict = s.selectedDistrict?.takeIf { it in districts },
                    )
                }
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
            is LocalBeatsIntent.StateSelected -> _state.update {
                it.copy(selectedState = intent.state, selectedDistrict = null)
            }
            is LocalBeatsIntent.DistrictSelected -> _state.update { it.copy(selectedDistrict = intent.district) }
            LocalBeatsIntent.ClearFilters -> _state.update { it.copy(selectedState = null, selectedDistrict = null) }

            is LocalBeatsIntent.OpenEditor -> _state.update {
                val draft = intent.record?.let(BeatDraft::from) ?: BeatDraft(
                    state = it.selectedState.orEmpty(),
                    district = it.selectedDistrict.orEmpty(),
                )
                it.copy(editor = EditorState(draft))
            }
            is LocalBeatsIntent.EditorFieldChanged -> updateEditorField(intent.field, intent.value)
            LocalBeatsIntent.SaveEditor -> saveEditor()
            LocalBeatsIntent.DismissEditor -> _state.update { it.copy(editor = null) }

            is LocalBeatsIntent.RequestDelete -> _state.update { it.copy(pendingDelete = intent.record) }
            LocalBeatsIntent.CancelDelete -> _state.update { it.copy(pendingDelete = null) }
            LocalBeatsIntent.ConfirmDelete -> confirmDelete()

            is LocalBeatsIntent.ImportFile -> importFile(intent)
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

    private fun confirmDelete() {
        val record = _state.value.pendingDelete ?: return
        viewModelScope.launch {
            try {
                repository.delete(record.id)
                dataVersion.update { it + 1 }
                _effects.send(LocalBeatsEffect.ShowMessage("Deleted ${record.localityName}."))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effects.send(LocalBeatsEffect.ShowMessage("Could not delete: ${e.message}"))
            } finally {
                _state.update { it.copy(pendingDelete = null) }
            }
        }
    }

    // ------------------------------------------------------------------ excel

    private fun importFile(intent: LocalBeatsIntent.ImportFile) = fileOperation(
        if (intent.mode == ImportMode.REPLACE_ALL) "Replacing directory…" else "Importing…",
    ) {
        try {
            val report = excel.importFrom(intent.uri, intent.mode)
            dataVersion.update { it + 1 }
            _state.update { it.copy(importReport = report) }
        } catch (e: ExcelFormatException) {
            _effects.send(LocalBeatsEffect.ShowMessage(e.message ?: "Unrecognised spreadsheet format."))
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
