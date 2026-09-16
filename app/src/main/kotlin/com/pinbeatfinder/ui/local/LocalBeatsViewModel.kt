package com.pinbeatfinder.ui.local

import androidx.lifecycle.ViewModel
import com.pinbeatfinder.R
import com.pinbeatfinder.ui.components.UiText
import androidx.lifecycle.viewModelScope
import com.pinbeatfinder.core.dedupe.DuplicateFinder
import com.pinbeatfinder.core.util.BeatDraftValidator
import com.pinbeatfinder.core.util.PinCodeValidator
import com.pinbeatfinder.data.excel.ExcelFormatException
import com.pinbeatfinder.data.excel.ExcelSyncManager
import com.pinbeatfinder.data.excel.ImportMode
import com.pinbeatfinder.data.remote.LocalDirectorySource
import com.pinbeatfinder.data.repository.BeatDirectoryRepository
import com.pinbeatfinder.domain.model.BeatDraft
import com.pinbeatfinder.domain.model.BeatField
import com.pinbeatfinder.domain.model.BeatGroup
import com.pinbeatfinder.domain.model.BeatGrouping
import com.pinbeatfinder.domain.model.BeatRecord
import com.pinbeatfinder.domain.model.BeatSearchFilters
import com.pinbeatfinder.domain.model.BeatSearchHit
import com.pinbeatfinder.domain.model.MatchKind
import com.pinbeatfinder.domain.model.DraftValidation
import com.pinbeatfinder.domain.model.FieldError
import com.pinbeatfinder.domain.model.FilterOptions
import com.pinbeatfinder.domain.model.OfficeSummary
import com.pinbeatfinder.domain.model.OfficeType
import com.pinbeatfinder.domain.model.PostOffice
import com.pinbeatfinder.domain.model.withOffice
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
    /** Built-in All-India directory, used by the editor's "Fetch offices for this PIN" action. */
    private val directory: LocalDirectorySource,
    private val duplicateFinder: DuplicateFinder = DuplicateFinder(),
) : ViewModel() {

    private val _state = MutableStateFlow(LocalBeatsState())
    val state: StateFlow<LocalBeatsState> = _state.asStateFlow()

    private val _effects = Channel<LocalBeatsEffect>(Channel.BUFFERED)
    val effects: Flow<LocalBeatsEffect> = _effects.receiveAsFlow()

    private val dataVersion = MutableStateFlow(0)

    private data class SearchKey(val query: String, val filters: BeatSearchFilters, val version: Int)
    private data class GroupKey(val filters: BeatSearchFilters, val version: Int, val active: Boolean)

    init {
        combine(
            _state.map { it.query to it.filters }.distinctUntilChanged(),
            dataVersion,
        ) { (q, f), v -> SearchKey(q, f, v) }
            .debounce { key -> if (key.query.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
            .onEach { _state.update { it.copy(isSearching = true) } }
            .mapLatest { key -> repository.search(key.query, key.filters) }
            .onEach { hits -> _state.update { it.copy(hits = hits, isSearching = false) } }
            .launchIn(viewModelScope)

        combine(
            _state.map { it.filters to (it.viewMode == LocalViewMode.BY_BEAT) }.distinctUntilChanged(),
            dataVersion,
        ) { (f, active), v -> GroupKey(f, v, active) }
            .mapLatest { key ->
                if (!key.active) emptyList<BeatGroup>() to emptyList<OfficeSummary>()
                else repository.listAll(key.filters).let { BeatGrouping.group(it) to BeatGrouping.summarizeOffices(it) }
            }
            .onEach { (groups, offices) -> _state.update { it.copy(beatGroups = groups, officeSummaries = offices) } }
            .launchIn(viewModelScope)

        // Office Name suggestions: what is typed -> directory name search -> short list, PIN matches first.
        _state.map { s -> s.editor?.let { it.draft.officeName.trim() to it.draft.pincode } }
            .distinctUntilChanged()
            .debounce { if (it == null || it.first.length < SUGGEST_MIN_CHARS) 0L else SEARCH_DEBOUNCE_MS }
            .mapLatest { key ->
                if (key == null || key.first.length < SUGGEST_MIN_CHARS) return@mapLatest emptyList<PostOffice>()
                runCatching {
                    directory.search(key.first, isPincode = false)
                        .sortedBy { if (it.pincode == key.second) 0 else 1 }
                        .take(SUGGEST_LIMIT)
                }.getOrDefault(emptyList())
            }
            .onEach { suggestions -> updateEditor { it.copy(officeSuggestions = suggestions) } }
            .launchIn(viewModelScope)

        // Account Office suggestions: same lookup, restricted to SO/HO/GPO; same PIN, then same district, first.
        _state.map { s -> s.editor?.let { Triple(it.draft.accountOffice.trim(), it.draft.pincode, it.draft.district.trim().lowercase()) } }
            .distinctUntilChanged()
            .debounce { if (it == null || it.first.length < SUGGEST_MIN_CHARS) 0L else SEARCH_DEBOUNCE_MS }
            .mapLatest { key ->
                if (key == null || key.first.length < SUGGEST_MIN_CHARS) return@mapLatest emptyList<PostOffice>()
                runCatching {
                    directory.search(key.first, isPincode = false)
                        .filter { it.officeType in ACCOUNT_OFFICE_TYPES }
                        .sortedBy { if (it.pincode == key.second) 0 else if (it.district.trim().lowercase() == key.third) 1 else 2 }
                        .take(SUGGEST_LIMIT)
                }.getOrDefault(emptyList())
            }
            .onEach { suggestions -> updateEditor { it.copy(accountSuggestions = suggestions) } }
            .launchIn(viewModelScope)

        // Near-duplicate warning: what is typed as the locality vs. what the local directory already has.
        _state.map { s -> s.editor?.let { it.draft.localityName.trim() to it.draft.id } }
            .distinctUntilChanged()
            .debounce { if (it == null || it.first.length < SIMILAR_MIN_CHARS) 0L else SEARCH_DEBOUNCE_MS }
            .mapLatest { key ->
                if (key == null || key.first.length < SIMILAR_MIN_CHARS) return@mapLatest emptyList<BeatSearchHit>()
                runCatching {
                    repository.search(key.first, limit = SIMILAR_LIMIT * 2)
                        .filter { it.record.id != key.second && it.matchKind != MatchKind.NONE && it.score >= SIMILAR_MIN_SCORE }
                        .take(SIMILAR_LIMIT)
                }.getOrDefault(emptyList())
            }
            .onEach { hits -> updateEditor { it.copy(similarExisting = hits) } }
            .launchIn(viewModelScope)

        repository.observeStates()
            .onEach { states -> _state.update { s -> s.copy(states = states) } }
            .launchIn(viewModelScope)

        _state.map { it.filters.state }.distinctUntilChanged()
            .flatMapLatest { repository.observeDistricts(it) }
            .onEach { districts -> _state.update { s -> s.copy(districts = districts) } }
            .launchIn(viewModelScope)

        // Filter choices follow the data; a selection whose value was deleted or re-typed is dropped.
        repository.observeFacets()
            .onEach { facets -> _state.update { s -> s.copy(facets = facets, filters = FilterOptions.prune(facets, s.filters)) } }
            .launchIn(viewModelScope)

        repository.observeCount()
            .onEach { count -> _state.update { it.copy(totalRecords = count) } }
            .launchIn(viewModelScope)

        viewModelScope.launch { runCatching { excel.pruneOldExports() } }
    }

    fun onIntent(intent: LocalBeatsIntent) {
        when (intent) {
            is LocalBeatsIntent.QueryChanged -> _state.update { it.copy(query = intent.query) }
            is LocalBeatsIntent.FiltersChanged -> _state.update { it.copy(filters = intent.filters) }
            LocalBeatsIntent.ClearFilters -> _state.update { it.copy(filters = BeatSearchFilters()) }
            LocalBeatsIntent.ShowFilters -> _state.update { it.copy(showFilters = true) }
            LocalBeatsIntent.HideFilters -> _state.update { it.copy(showFilters = false) }
            is LocalBeatsIntent.SetViewMode -> _state.update { it.copy(viewMode = intent.mode, selectedIds = emptySet()) }
            is LocalBeatsIntent.ToggleBeatExpanded -> _state.update {
                it.copy(expandedBeats = if (intent.key in it.expandedBeats) it.expandedBeats - intent.key else it.expandedBeats + intent.key)
            }

            is LocalBeatsIntent.OpenEditor -> _state.update {
                val draft = intent.record?.let(BeatDraft::from) ?: BeatDraft(
                    state = it.filters.state.orEmpty(),
                    district = it.filters.district.orEmpty(),
                    officeType = it.filters.officeType?.code ?: BeatDraft().officeType,
                    officeName = it.filters.officeName.orEmpty(),
                    beatNumber = it.filters.beatNumber.orEmpty(),
                    pincode = it.filters.pincode.orEmpty(),
                )
                it.copy(editor = EditorState(draft))
            }
            is LocalBeatsIntent.OpenEditorWithDraft -> _state.update { it.copy(editor = EditorState(intent.draft)) }
            is LocalBeatsIntent.ShowPincode -> _state.update {
                it.copy(query = intent.pincode, filters = BeatSearchFilters(), viewMode = LocalViewMode.SEARCH)
            }
            is LocalBeatsIntent.EditorFieldChanged -> updateEditorField(intent.field, intent.value)
            LocalBeatsIntent.FetchOfficesForPin -> fetchOfficesForPin()
            is LocalBeatsIntent.OfficeSelected -> selectOffice(intent.office)
            LocalBeatsIntent.DismissFetchedOffices -> updateEditor { it.copy(fetchedOffices = null, pickerSelection = emptySet()) }
            is LocalBeatsIntent.TogglePickerOffice -> updateEditor {
                val name = intent.office.name
                it.copy(pickerSelection = if (name in it.pickerSelection) it.pickerSelection - name else it.pickerSelection + name)
            }
            LocalBeatsIntent.ConfirmPickerSelection -> confirmPickerSelection()
            LocalBeatsIntent.SkipQueued -> advanceQueue()
            is LocalBeatsIntent.ShareBeat -> shareRecords(intent.group.records, "${intent.group.officeDisplay} beat ${intent.group.beatNumber}")
            is LocalBeatsIntent.ShareOffice -> fileOperation(UiText.Res(R.string.busy_backup)) {
                shareNow(officeRecords(intent.group), intent.group.officeDisplay)
            }
            is LocalBeatsIntent.PrintBeat -> fileOperation(UiText.Res(R.string.busy_pdf)) {
                val g = intent.group
                printNow(g.records, "${g.officeDisplay} beat ${g.beatNumber}", "${g.officeDisplay} • Beat ${g.beatNumber}", subtitleFor(g))
            }
            is LocalBeatsIntent.PrintOffice -> fileOperation(UiText.Res(R.string.busy_pdf)) {
                val g = intent.group
                val records = officeRecords(g).sortedWith(compareBy<BeatRecord> { it.beatNumber.length }.thenBy { it.beatNumber }.thenBy { it.localityName.lowercase() })
                printNow(records, g.officeDisplay, "${g.officeDisplay} • all beats", subtitleFor(g))
            }
            LocalBeatsIntent.FindDuplicates -> findDuplicates()
            LocalBeatsIntent.DismissDuplicates -> _state.update { it.copy(duplicates = null) }
            is LocalBeatsIntent.ResolveDuplicate -> resolveDuplicate(intent)
            LocalBeatsIntent.ShowOfficeTypes -> _state.update { it.copy(showOfficeTypes = true) }
            LocalBeatsIntent.HideOfficeTypes -> _state.update { it.copy(showOfficeTypes = false) }
            is LocalBeatsIntent.SetOfficeType -> setOfficeType(intent)
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
            LocalBeatsIntent.ShareBackup -> fileOperation(UiText.Res(R.string.busy_backup)) {
                val file = excel.exportBackup()
                _effects.send(LocalBeatsEffect.LaunchIntent(excel.shareIntent(file, excel.string(R.string.share_backup_title))))
            }
            is LocalBeatsIntent.SaveBackupTo -> fileOperation(UiText.Res(R.string.busy_saving_backup)) {
                excel.saveBackupTo(intent.uri)
                _effects.send(LocalBeatsEffect.ShowMessage(UiText.Res(R.string.msg_backup_saved)))
            }
            LocalBeatsIntent.ShareTemplate -> fileOperation(UiText.Res(R.string.busy_template)) {
                val file = excel.exportTemplate()
                _effects.send(LocalBeatsEffect.LaunchIntent(excel.shareIntent(file, excel.string(R.string.share_template_title))))
            }
            is LocalBeatsIntent.SaveTemplateTo -> fileOperation(UiText.Res(R.string.busy_saving_template)) {
                excel.saveTemplateTo(intent.uri)
                _effects.send(LocalBeatsEffect.ShowMessage(UiText.Res(R.string.msg_template_saved)))
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
                BeatField.OFFICE_TYPE -> d.copy(officeType = value)
                BeatField.OFFICE_NAME -> d.copy(officeName = value)
                BeatField.ACCOUNT_OFFICE -> d.copy(accountOffice = value)
                BeatField.BEAT_NUMBER -> d.copy(beatNumber = value)
                BeatField.DISTRICT -> d.copy(district = value)
                BeatField.STATE -> d.copy(state = value)
                BeatField.PINCODE -> d.copy(pincode = value.filter(Char::isDigit).take(6))
                BeatField.REMARKS -> d.copy(remarks = value)
            }
            s.copy(editor = editor.copy(draft = draft, errors = editor.errors - field))
        }
    }

    private inline fun updateEditor(crossinline transform: (EditorState) -> EditorState) {
        _state.update { s -> s.editor?.let { s.copy(editor = transform(it)) } ?: s }
    }

    /**
     * Asks the bundled directory for every office under the PIN typed in the editor. The result
     * is shown as a picker; choosing an entry fills office type/name, account office, district
     * and state in one tap, so staff never have to type an office name from memory.
     */
    private fun fetchOfficesForPin() {
        val editor = _state.value.editor ?: return
        if (editor.isFetching) return
        val pin = PinCodeValidator.normalize(editor.draft.pincode)
        if (pin == null) {
            val error = if (editor.draft.pincode.isBlank()) FieldError.REQUIRED else FieldError.INVALID_PINCODE
            updateEditor { it.copy(errors = it.errors + (BeatField.PINCODE to error)) }
            return
        }
        viewModelScope.launch {
            updateEditor { it.copy(isFetching = true) }
            try {
                if (!directory.isReady()) {
                    _effects.send(LocalBeatsEffect.ShowMessage(UiText.Res(R.string.fetch_directory_not_ready)))
                    return@launch
                }
                val offices = directory.search(pin, isPincode = true)
                    .sortedWith(compareBy<PostOffice> { it.officeType.ordinal }.thenBy { it.name.lowercase() })
                if (offices.isEmpty()) {
                    _effects.send(LocalBeatsEffect.ShowMessage(UiText.Res(R.string.fetch_no_offices, pin)))
                } else {
                    val stats = repository.officeStats(pin)
                    updateEditor {
                        it.copy(
                            fetchedOffices = offices,
                            officeStats = stats,
                            draft = it.draft.copy(pincode = pin),
                            errors = it.errors - BeatField.PINCODE,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effects.send(LocalBeatsEffect.ShowMessage(UiText.Res(R.string.msg_file_failed, e.message ?: e::class.simpleName.orEmpty())))
            } finally {
                updateEditor { it.copy(isFetching = false) }
            }
        }
    }

    /** Batch entry: the first ticked office fills the form now, the rest wait in [EditorState.queue]. */
    private fun confirmPickerSelection() {
        val editor = _state.value.editor ?: return
        val offices = editor.fetchedOffices.orEmpty()
        val picked = offices.filter { it.name in editor.pickerSelection }
        if (picked.isEmpty()) return
        updateEditor {
            it.copy(
                draft = it.draft.withOffice(picked.first()),
                errors = emptyMap(),
                fetchedOffices = null,
                pickerSelection = emptySet(),
                officeSuggestions = emptyList(),
                queue = picked.drop(1),
                queueTotal = picked.size,
            )
        }
    }

    /** Opens a fresh form for the next queued office, or closes the editor when the batch is done. */
    private fun advanceQueue() {
        val editor = _state.value.editor ?: return
        val next = editor.queue.firstOrNull()
        if (next == null) {
            _state.update { it.copy(editor = null) }
            return
        }
        // Beat numbers are per office, so nothing but the office fields carries over.
        val base = BeatDraft().withOffice(next)
        _state.update {
            it.copy(editor = EditorState(draft = base, queue = editor.queue.drop(1), queueTotal = editor.queueTotal))
        }
    }

    private suspend fun officeRecords(group: BeatGroup): List<BeatRecord> {
        val pins = group.pincodes.toSet()
        return repository.listAll().filter { it.officeName.equals(group.officeName, ignoreCase = true) && it.pincode in pins }
    }

    private fun subtitleFor(group: BeatGroup): String {
        val sample = group.records.firstOrNull()
        return listOfNotNull(
            group.accountOffice.takeIf { it.isNotBlank() }?.let { "Account office $it" },
            "PIN ${group.pincodes.joinToString(", ")}",
            sample?.let { listOf(it.district, it.state).filter { s -> s.isNotBlank() }.joinToString(", ") }?.takeIf { it.isNotBlank() },
        ).joinToString(" • ")
    }

    private suspend fun printNow(records: List<BeatRecord>, label: String, title: String, subtitle: String) {
        if (records.isEmpty()) {
            _effects.send(LocalBeatsEffect.ShowMessage(UiText.Res(R.string.msg_nothing_to_share)))
            return
        }
        val file = excel.exportPdf(records, label, title, subtitle)
        _effects.send(LocalBeatsEffect.LaunchIntent(excel.shareIntent(file, excel.string(R.string.share_pdf_title), com.pinbeatfinder.data.print.BeatSheetPdf.MIME_TYPE)))
    }

    private fun findDuplicates() {
        if (_state.value.isScanningDuplicates) return
        viewModelScope.launch {
            _state.update { it.copy(isScanningDuplicates = true) }
            try {
                val pairs = duplicateFinder.find(repository.listAll())
                _state.update { it.copy(duplicates = pairs) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effects.send(LocalBeatsEffect.ShowMessage(UiText.Res(R.string.msg_file_failed, e.message ?: e::class.simpleName.orEmpty())))
            } finally {
                _state.update { it.copy(isScanningDuplicates = false) }
            }
        }
    }

    private fun resolveDuplicate(intent: LocalBeatsIntent.ResolveDuplicate) {
        val drop = if (intent.keep.id == intent.pair.first.id) intent.pair.second else intent.pair.first
        viewModelScope.launch {
            try {
                repository.save(DuplicateFinder.merged(intent.keep, drop))
                repository.delete(drop.id)
                dataVersion.update { it + 1 }
                _state.update { s ->
                    // Drop every pair that involved the deleted record; the kept one may still pair with others.
                    s.copy(duplicates = s.duplicates?.filter { it.first.id != drop.id && it.second.id != drop.id })
                }
                _effects.send(LocalBeatsEffect.ShowMessage(UiText.Res(R.string.msg_duplicate_resolved, intent.keep.localityName)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effects.send(LocalBeatsEffect.ShowMessage(UiText.Res(R.string.msg_delete_failed, e.message.orEmpty())))
            }
        }
    }

    private fun shareRecords(records: List<BeatRecord>, label: String) = fileOperation(UiText.Res(R.string.busy_backup)) {
        shareNow(records, label)
    }

    private suspend fun shareNow(records: List<BeatRecord>, label: String) {
        if (records.isEmpty()) {
            _effects.send(LocalBeatsEffect.ShowMessage(UiText.Res(R.string.msg_nothing_to_share)))
            return
        }
        val file = excel.exportRecords(records, label)
        _effects.send(LocalBeatsEffect.LaunchIntent(excel.shareIntent(file, excel.string(R.string.share_export_title))))
    }

    private fun selectOffice(office: PostOffice) {
        updateEditor { editor ->
            editor.copy(
                draft = editor.draft.withOffice(office),
                errors = editor.errors - setOf(
                    BeatField.OFFICE_TYPE, BeatField.OFFICE_NAME, BeatField.ACCOUNT_OFFICE,
                    BeatField.DISTRICT, BeatField.STATE, BeatField.PINCODE,
                ),
                fetchedOffices = null,
                officeSuggestions = emptyList(),
            )
        }
    }

    private fun setOfficeType(intent: LocalBeatsIntent.SetOfficeType) {
        viewModelScope.launch {
            try {
                val changed = repository.setOfficeType(intent.office.officeName, intent.office.pincodes, intent.type)
                dataVersion.update { it + 1 }
                _effects.send(LocalBeatsEffect.ShowMessage(UiText.Res(R.string.msg_office_type_updated, intent.type.code, changed)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effects.send(LocalBeatsEffect.ShowMessage(UiText.Res(R.string.msg_save_failed, e.message.orEmpty())))
            }
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
                    if (editor.isBatch) advanceQueue() else _state.update { it.copy(editor = null) }
                    _effects.send(LocalBeatsEffect.Saved)
                    _effects.send(LocalBeatsEffect.ShowMessage(UiText.Res(if (editor.isNew) R.string.msg_record_added else R.string.msg_record_updated)))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update { it.copy(editor = editor.copy(isSaving = false)) }
                    _effects.send(LocalBeatsEffect.ShowMessage(UiText.Res(R.string.msg_save_failed, e.message.orEmpty())))
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
                _effects.send(LocalBeatsEffect.ShowMessage(UiText.Res(R.string.msg_delete_failed, e.message.orEmpty())))
            }
        }
    }

    private fun undoDelete(record: BeatRecord) {
        viewModelScope.launch {
            try {
                repository.save(record.copy(id = 0L))
                dataVersion.update { it + 1 }
                _effects.send(LocalBeatsEffect.ShowMessage(UiText.Res(R.string.msg_restored, record.localityName)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effects.send(LocalBeatsEffect.ShowMessage(UiText.Res(R.string.msg_restore_failed, e.message.orEmpty())))
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
                _effects.send(LocalBeatsEffect.ShowMessage(UiText.Res(R.string.msg_deleted_n, ids.size)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(confirmBulkDelete = false) }
                _effects.send(LocalBeatsEffect.ShowMessage(UiText.Res(R.string.msg_delete_failed, e.message.orEmpty())))
            }
        }
    }

    // ------------------------------------------------------------------ excel

    private fun prepareImport(intent: LocalBeatsIntent.ImportFile) = fileOperation(UiText.Res(R.string.busy_reading)) {
        try {
            val preview = excel.prepareImport(intent.uri, intent.mode)
            _state.update { it.copy(importPreview = preview) }
        } catch (e: ExcelFormatException) {
            _effects.send(LocalBeatsEffect.ShowMessage(e.message?.let { UiText.Raw(it) } ?: UiText.Res(R.string.msg_bad_format)))
        }
    }

    private fun commitImport() {
        val preview = _state.value.importPreview ?: return
        _state.update { it.copy(importPreview = null) }
        fileOperation(UiText.Res(if (preview.mode == ImportMode.REPLACE_ALL) R.string.busy_replacing else R.string.busy_importing)) {
            val report = excel.commitImport(preview)
            dataVersion.update { it + 1 }
            _state.update { it.copy(importReport = report) }
        }
    }

    private fun fileOperation(busyMessage: UiText, block: suspend () -> Unit) {
        if (_state.value.busyMessage != null) return // one file job at a time
        viewModelScope.launch {
            _state.update { it.copy(busyMessage = busyMessage) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effects.send(LocalBeatsEffect.ShowMessage(UiText.Res(R.string.msg_file_failed, e.message ?: e::class.simpleName.orEmpty())))
            } finally {
                _state.update { it.copy(busyMessage = null) }
            }
        }
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
        const val SUGGEST_MIN_CHARS = 2
        const val SUGGEST_LIMIT = 8
        const val SIMILAR_MIN_CHARS = 3
        const val SIMILAR_LIMIT = 4
        /** TEXT tier or better: exact, prefix, contains, or phonetically identical. */
        const val SIMILAR_MIN_SCORE = 0.70
        val ACCOUNT_OFFICE_TYPES = setOf(OfficeType.SO, OfficeType.HO, OfficeType.GPO)
    }
}
