package com.pinbeatfinder.ui.local

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pinbeatfinder.R
import com.pinbeatfinder.core.util.MatchHighlighter
import com.pinbeatfinder.data.excel.ExcelCodec
import com.pinbeatfinder.data.excel.ImportMode
import com.pinbeatfinder.data.excel.ImportPreview
import com.pinbeatfinder.data.excel.ImportReport
import com.pinbeatfinder.data.excel.RowError
import com.pinbeatfinder.domain.model.BeatGroup
import com.pinbeatfinder.domain.model.BeatRecord
import com.pinbeatfinder.domain.model.BeatSearchHit
import com.pinbeatfinder.domain.model.MatchKind
import com.pinbeatfinder.ui.components.EmptyState
import com.pinbeatfinder.ui.components.FilterChipsRow
import com.pinbeatfinder.ui.components.message
import com.pinbeatfinder.ui.theme.rememberHaptic

/** Overflow-menu actions surfaced by the host screen's top bar. Order = menu order. */
enum class LocalBeatsMenuAction(val labelRes: Int, val icon: ImageVector) {
    IMPORT_APPEND(R.string.menu_import_append, Icons.Default.FileUpload),
    IMPORT_REPLACE(R.string.menu_import_replace, Icons.Default.SwapHoriz),
    SHARE_BACKUP(R.string.menu_share_backup, Icons.Default.Share),
    SAVE_BACKUP(R.string.menu_save_backup, Icons.Default.Save),
    SHARE_TEMPLATE(R.string.menu_share_template, Icons.Default.Share),
    SAVE_TEMPLATE(R.string.menu_save_template, Icons.Default.Download),
}

private val XLSX_MIME_TYPES = arrayOf(
    ExcelCodec.MIME_TYPE,
    "application/vnd.ms-excel",
    "application/octet-stream", // some file managers mislabel downloads
)

@Composable
fun LocalBeatsScreen(
    viewModel: LocalBeatsViewModel,
    snackbarHostState: SnackbarHostState,
    registerMenuHandler: ((LocalBeatsMenuAction) -> Unit) -> Unit,
    launchIntent: (Intent) -> Unit,
    onLookupOnline: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = viewModel::onIntent
    val context = LocalContext.current
    val haptic = rememberHaptic()

    // ---- SAF launchers -------------------------------------------------------------------
    var pendingImportMode by remember { mutableStateOf(ImportMode.APPEND) }
    var confirmReplaceImport by remember { mutableStateOf(false) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onIntent(LocalBeatsIntent.ImportFile(it, pendingImportMode)) }
    }
    val templateSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExcelCodec.MIME_TYPE)) { uri ->
        uri?.let { onIntent(LocalBeatsIntent.SaveTemplateTo(it)) }
    }
    val backupSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExcelCodec.MIME_TYPE)) { uri ->
        uri?.let { onIntent(LocalBeatsIntent.SaveBackupTo(it)) }
    }
    val startAppendImport = {
        pendingImportMode = ImportMode.APPEND
        importPicker.launch(XLSX_MIME_TYPES)
    }

    LaunchedEffect(Unit) {
        registerMenuHandler { action ->
            when (action) {
                LocalBeatsMenuAction.IMPORT_APPEND -> startAppendImport()
                LocalBeatsMenuAction.IMPORT_REPLACE -> confirmReplaceImport = true
                LocalBeatsMenuAction.SHARE_BACKUP -> onIntent(LocalBeatsIntent.ShareBackup)
                LocalBeatsMenuAction.SAVE_BACKUP -> backupSaver.launch(ExcelCodec.backupFileName())
                LocalBeatsMenuAction.SHARE_TEMPLATE -> onIntent(LocalBeatsIntent.ShareTemplate)
                LocalBeatsMenuAction.SAVE_TEMPLATE -> templateSaver.launch(ExcelCodec.TEMPLATE_FILE_NAME)
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LocalBeatsEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.text.asString(context))
                is LocalBeatsEffect.LaunchIntent -> launchIntent(effect.intent)
                LocalBeatsEffect.Saved -> haptic(HapticFeedbackType.Confirm)
                is LocalBeatsEffect.ShowUndoDelete -> {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.msg_deleted, effect.record.localityName),
                        actionLabel = context.getString(R.string.action_undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) onIntent(LocalBeatsIntent.UndoDelete(effect.record))
                }
            }
        }
    }

    LocalBeatsContent(
        state = state,
        onIntent = onIntent,
        onLookupOnline = onLookupOnline,
        onImportClick = startAppendImport,
        onTemplateClick = { templateSaver.launch(ExcelCodec.TEMPLATE_FILE_NAME) },
    )

    // ---- dialogs & sheets ----------------------------------------------------------------
    state.editor?.let { editor ->
        BeatEditorSheet(
            editor = editor,
            knownStates = state.states,
            knownDistricts = state.districts,
            onFieldChange = { field, value -> onIntent(LocalBeatsIntent.EditorFieldChanged(field, value)) },
            onSave = { onIntent(LocalBeatsIntent.SaveEditor) },
            onDismiss = { onIntent(LocalBeatsIntent.DismissEditor) },
            onDuplicate = { onIntent(LocalBeatsIntent.DuplicateInEditor) },
            onFetchOffices = { onIntent(LocalBeatsIntent.FetchOfficesForPin) },
            onOfficeSelected = { onIntent(LocalBeatsIntent.OfficeSelected(it)) },
            onDismissOffices = { onIntent(LocalBeatsIntent.DismissFetchedOffices) },
        )
    }

    state.pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { onIntent(LocalBeatsIntent.CancelDelete) },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_message, record.localityName, record.beatNumber, record.officeDisplay)) },
            confirmButton = { TextButton(onClick = { onIntent(LocalBeatsIntent.ConfirmDelete) }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { onIntent(LocalBeatsIntent.CancelDelete) }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (state.confirmBulkDelete) {
        AlertDialog(
            onDismissRequest = { onIntent(LocalBeatsIntent.CancelBulkDelete) },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.bulk_delete_title, state.selectedIds.size)) },
            text = { Text(stringResource(R.string.bulk_delete_message)) },
            confirmButton = { TextButton(onClick = { onIntent(LocalBeatsIntent.ConfirmBulkDelete) }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { onIntent(LocalBeatsIntent.CancelBulkDelete) }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (confirmReplaceImport) {
        AlertDialog(
            onDismissRequest = { confirmReplaceImport = false },
            icon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
            title = { Text(stringResource(R.string.replace_title)) },
            text = { Text(stringResource(R.string.replace_message, state.totalRecords)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReplaceImport = false
                    pendingImportMode = ImportMode.REPLACE_ALL
                    importPicker.launch(XLSX_MIME_TYPES)
                }) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = { TextButton(onClick = { confirmReplaceImport = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    state.importPreview?.let { preview ->
        ImportPreviewDialog(
            preview = preview,
            onConfirm = { onIntent(LocalBeatsIntent.ConfirmImport) },
            onCancel = { onIntent(LocalBeatsIntent.CancelImport) },
        )
    }

    state.importReport?.let { report -> ImportReportDialog(report) { onIntent(LocalBeatsIntent.DismissImportReport) } }

    state.busyMessage?.let { message ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(message.asString()) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.please_wait))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalBeatsContent(
    state: LocalBeatsState,
    onIntent: (LocalBeatsIntent) -> Unit,
    onLookupOnline: (String) -> Unit = {},
    onImportClick: () -> Unit = {},
    onTemplateClick: () -> Unit = {},
) {
    if (state.isDirectoryEmpty) {
        OnboardingEmptyState(
            onAdd = { onIntent(LocalBeatsIntent.OpenEditor()) },
            onImport = onImportClick,
            onTemplate = onTemplateClick,
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        // ---- selection bar (long-press) ----------------------------------------------
        AnimatedVisibility(visible = state.isSelecting) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onIntent(LocalBeatsIntent.ClearSelection) }) {
                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.local_cancel_selection))
                }
                Text(
                    stringResource(R.string.local_selected, state.selectedIds.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onIntent(LocalBeatsIntent.RequestBulkDelete) }) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_delete))
                }
            }
        }

        // ---- view mode ---------------------------------------------------------------
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            LocalViewMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = state.viewMode == mode,
                    onClick = { onIntent(LocalBeatsIntent.SetViewMode(mode)) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = LocalViewMode.entries.size),
                    icon = {
                        // Default check mark when active; the mode's own icon when inactive.
                        SegmentedButtonDefaults.Icon(
                            active = state.viewMode == mode,
                            inactiveContent = {
                                Icon(
                                    if (mode == LocalViewMode.SEARCH) Icons.Default.Search else Icons.Default.Route,
                                    contentDescription = null,
                                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                )
                            },
                        )
                    },
                ) {
                    Text(stringResource(if (mode == LocalViewMode.SEARCH) R.string.local_view_search else R.string.local_view_by_beat))
                }
            }
        }

        if (state.viewMode == LocalViewMode.SEARCH) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { onIntent(LocalBeatsIntent.QueryChanged(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text(stringResource(R.string.search_local_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onIntent(LocalBeatsIntent.QueryChanged("")) }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_clear))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                supportingText = { Text(stringResource(R.string.local_supporting, state.totalRecords)) },
            )
        }

        FilterChipsRow(
            label = stringResource(R.string.filter_state),
            options = state.states,
            selected = state.selectedState,
            onSelect = { onIntent(LocalBeatsIntent.StateSelected(it)) },
        )
        FilterChipsRow(
            label = stringResource(R.string.filter_district),
            options = state.districts,
            selected = state.selectedDistrict,
            onSelect = { onIntent(LocalBeatsIntent.DistrictSelected(it)) },
        )

        if (state.isSearching && state.viewMode == LocalViewMode.SEARCH) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))

        when (state.viewMode) {
            LocalViewMode.SEARCH -> SearchResults(state, onIntent, onLookupOnline)
            LocalViewMode.BY_BEAT -> BeatGroupsList(state, onIntent, onLookupOnline)
        }
    }
}

// ------------------------------------------------------------------ search mode

@Composable
private fun SearchResults(state: LocalBeatsState, onIntent: (LocalBeatsIntent) -> Unit, onLookupOnline: (String) -> Unit) {
    if (state.hits.isEmpty() && !state.isSearching) {
        EmptyState(
            icon = Icons.Default.SearchOff,
            title = stringResource(R.string.local_no_matches_title),
            message = stringResource(if (state.hasFilters) R.string.local_no_matches_filtered else R.string.local_no_matches_hint),
            actionLabel = if (state.hasFilters) stringResource(R.string.action_clear_filters) else null,
            onAction = { onIntent(LocalBeatsIntent.ClearFilters) },
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.hits, key = { it.record.id }) { hit ->
            SwipeToDeleteRow(
                enabled = !state.isSelecting,
                onDelete = { onIntent(LocalBeatsIntent.SwipeDelete(hit.record)) },
                modifier = Modifier.animateItem(),
            ) {
                BeatRecordCard(
                    hit = hit,
                    query = state.query,
                    selecting = state.isSelecting,
                    selected = hit.record.id in state.selectedIds,
                    onEdit = { onIntent(LocalBeatsIntent.OpenEditor(hit.record)) },
                    onDelete = { onIntent(LocalBeatsIntent.RequestDelete(hit.record)) },
                    onLookupOnline = { onLookupOnline(hit.record.pincode) },
                    onToggleSelect = { onIntent(LocalBeatsIntent.ToggleSelected(hit.record.id)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteRow(enabled: Boolean, onDelete: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = enabled,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BeatRecordCard(
    hit: BeatSearchHit,
    query: String,
    selecting: Boolean,
    selected: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLookupOnline: () -> Unit,
    onToggleSelect: () -> Unit,
) {
    val record: BeatRecord = hit.record
    val haptic = rememberHaptic()
    val highlight = if (hit.matchKind == MatchKind.TEXT || hit.matchKind == MatchKind.EXACT) MatchHighlighter.range(record.localityName, query) else null
    val title = buildAnnotatedString {
        if (highlight == null) {
            append(record.localityName)
        } else {
            append(record.localityName.substring(0, highlight.first))
            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)) {
                append(record.localityName.substring(highlight.first, highlight.last + 1))
            }
            append(record.localityName.substring(highlight.last + 1))
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selecting) onToggleSelect() else onEdit() },
                onLongClick = {
                    haptic(HapticFeedbackType.LongPress)
                    onToggleSelect()
                },
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selecting) {
                    Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
                    Spacer(Modifier.width(4.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${record.district}, ${record.state}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!selecting) {
                    IconButton(onClick = onLookupOnline) { Icon(Icons.Default.TravelExplore, contentDescription = stringResource(R.string.action_look_up_online)) }
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit)) }
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SuggestionChip(onClick = onEdit, label = { Text(stringResource(R.string.chip_beat, record.beatNumber)) })
                SuggestionChip(onClick = onEdit, label = { Text(stringResource(R.string.chip_office, record.officeDisplay)) })
                if (record.accountOffice.isNotBlank()) {
                    SuggestionChip(onClick = onEdit, label = { Text(stringResource(R.string.chip_account, record.accountOffice)) })
                }
                SuggestionChip(onClick = onEdit, label = { Text(stringResource(R.string.chip_pin, record.pincode)) })
            }
            if (record.remarks.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(record.remarks, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (query.isNotBlank()) {
                when (hit.matchKind) {
                    MatchKind.EXACT -> MatchLabel(stringResource(R.string.match_exact), MaterialTheme.colorScheme.primary)
                    MatchKind.PHONETIC -> MatchLabel(stringResource(R.string.match_sounds_like, query.trim()), MaterialTheme.colorScheme.tertiary)
                    MatchKind.TEXT, MatchKind.NONE -> Unit
                }
            }
        }
    }
}

@Composable
private fun MatchLabel(text: String, color: androidx.compose.ui.graphics.Color) {
    Spacer(Modifier.height(4.dp))
    Text(text, style = MaterialTheme.typography.labelSmall, color = color)
}

// ------------------------------------------------------------------ by-beat mode

@Composable
private fun BeatGroupsList(state: LocalBeatsState, onIntent: (LocalBeatsIntent) -> Unit, onLookupOnline: (String) -> Unit) {
    if (state.beatGroups.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Route,
            title = stringResource(R.string.local_no_beats_title),
            message = stringResource(if (state.hasFilters) R.string.local_no_beats_filtered else R.string.local_loading),
            actionLabel = if (state.hasFilters) stringResource(R.string.action_clear_filters) else null,
            onAction = { onIntent(LocalBeatsIntent.ClearFilters) },
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                stringResource(R.string.local_beats_summary, state.beatGroups.size, state.beatGroups.sumOf { it.villageCount }),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(state.beatGroups, key = { it.key }) { group ->
            BeatGroupCard(
                modifier = Modifier.animateItem(),
                group = group,
                expanded = group.key in state.expandedBeats,
                onToggle = { onIntent(LocalBeatsIntent.ToggleBeatExpanded(group.key)) },
                onEdit = { onIntent(LocalBeatsIntent.OpenEditor(it)) },
                onLookupOnline = onLookupOnline,
            )
        }
    }
}

@Composable
private fun BeatGroupCard(
    group: BeatGroup,
    modifier: Modifier = Modifier,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: (BeatRecord) -> Unit,
    onLookupOnline: (String) -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onToggle,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.local_beat_title, group.beatNumber), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val accountText = if (group.accountOffice.isNotBlank()) " • " + stringResource(R.string.local_beat_subtitle_account, group.accountOffice) else ""
                    val pinText = if (group.pincodes.isNotEmpty()) " • " + stringResource(R.string.local_beat_subtitle_pin, group.pincodes.joinToString(", ")) else ""
                    Text(
                        group.officeDisplay + accountText + pinText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "${group.villageCount}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(" " + stringResource(R.string.local_villages), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = onToggle) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 8.dp, end = 12.dp)) {
                    HorizontalDivider()
                    group.records.forEach { record ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickableCompat(onClick = { onEdit(record) })
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(record.localityName, style = MaterialTheme.typography.bodyLarge)
                                if (record.remarks.isNotBlank()) {
                                    Text(record.remarks, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(record.pincode, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            IconButton(onClick = { onLookupOnline(record.pincode) }) {
                                Icon(Icons.Default.TravelExplore, contentDescription = stringResource(R.string.action_look_up_online))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier = this.combinedClickable(onClick = onClick)

// ------------------------------------------------------------------ empty state / dialogs

@Composable
private fun OnboardingEmptyState(onAdd: () -> Unit, onImport: () -> Unit, onTemplate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FileUpload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.onboarding_import))
        }
        Spacer(Modifier.height(10.dp))
        FilledTonalButton(onClick = onTemplate, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Download, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.onboarding_template))
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.onboarding_add))
        }
    }
}

@Composable
private fun ImportPreviewDialog(preview: ImportPreview, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
        title = { Text(stringResource(if (preview.mode == ImportMode.REPLACE_ALL) R.string.preview_title_replace else R.string.preview_title_import)) },
        text = {
            Column {
                if (preview.mode == ImportMode.REPLACE_ALL && preview.existingCount > 0) {
                    Text(stringResource(R.string.preview_existing_deleted, preview.existingCount), color = MaterialTheme.colorScheme.error)
                }
                Text(stringResource(R.string.preview_will_add, preview.willInsert))
                if (preview.duplicatesSkipped > 0) Text(stringResource(R.string.preview_duplicates, preview.duplicatesSkipped))
                if (preview.blankRowsSkipped > 0) Text(stringResource(R.string.preview_blank, preview.blankRowsSkipped))
                if (preview.hasErrors) Text(stringResource(R.string.preview_errors, preview.errors.size), color = MaterialTheme.colorScheme.error)
                if (preview.hasErrors) {
                    Spacer(Modifier.height(10.dp))
                    RowErrorList(preview.errors)
                }
                if (preview.isEmpty) {
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.preview_nothing), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !preview.isEmpty || preview.mode == ImportMode.REPLACE_ALL) {
                Text(if (preview.mode == ImportMode.REPLACE_ALL) stringResource(R.string.action_replace) else stringResource(R.string.action_import_n, preview.willInsert))
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun RowErrorList(errors: List<RowError>) {
    val context = LocalContext.current
    Text(stringResource(R.string.rows_with_errors), style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(4.dp))
    Box(Modifier.height(160.dp)) {
        LazyColumn {
            items(errors.take(200)) { err ->
                val detail = err.messages.entries.joinToString("; ") { (field, e) -> e.message(context, field) }
                Text(
                    stringResource(R.string.row_error, err.rowNumber, detail),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ImportReportDialog(report: ImportReport, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (report.hasErrors) Icons.Default.SearchOff else Icons.Default.Check, contentDescription = null) },
        title = { Text(stringResource(if (report.hasErrors) R.string.report_title_issues else R.string.report_title_ok)) },
        text = {
            Column {
                val parts = buildList {
                    add(stringResource(R.string.report_imported, report.inserted))
                    if (report.duplicatesSkipped > 0) add(stringResource(R.string.report_duplicates, report.duplicatesSkipped))
                    if (report.errors.isNotEmpty()) add(stringResource(R.string.report_rejected, report.errors.size))
                }
                Text(parts.joinToString(", ") + ".")
                if (report.blankRowsSkipped > 0) {
                    Text(stringResource(R.string.report_blank, report.blankRowsSkipped), style = MaterialTheme.typography.bodySmall)
                }
                if (report.hasErrors) {
                    Spacer(Modifier.height(12.dp))
                    RowErrorList(report.errors)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}
