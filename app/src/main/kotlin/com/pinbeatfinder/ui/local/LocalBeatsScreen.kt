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

/** Overflow-menu actions surfaced by the host screen's top bar. Order = menu order. */
enum class LocalBeatsMenuAction(val label: String, val icon: ImageVector) {
    IMPORT_APPEND("Import from Excel (add rows)", Icons.Default.FileUpload),
    IMPORT_REPLACE("Import from Excel (replace all)", Icons.Default.SwapHoriz),
    SHARE_BACKUP("Share backup (.xlsx)", Icons.Default.Share),
    SAVE_BACKUP("Save backup to device…", Icons.Default.Save),
    SHARE_TEMPLATE("Share blank template", Icons.Default.Share),
    SAVE_TEMPLATE("Save blank template to device…", Icons.Default.Download),
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
                is LocalBeatsEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.text)
                is LocalBeatsEffect.LaunchIntent -> launchIntent(effect.intent)
                is LocalBeatsEffect.ShowUndoDelete -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Deleted ${effect.record.localityName}",
                        actionLabel = "Undo",
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
        )
    }

    state.pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { onIntent(LocalBeatsIntent.CancelDelete) },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Delete record?") },
            text = { Text("\"${record.localityName}\" (Beat ${record.beatNumber}, ${record.branchOffice}) will be removed. You can undo right after.") },
            confirmButton = { TextButton(onClick = { onIntent(LocalBeatsIntent.ConfirmDelete) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { onIntent(LocalBeatsIntent.CancelDelete) }) { Text("Cancel") } },
        )
    }

    if (state.confirmBulkDelete) {
        AlertDialog(
            onDismissRequest = { onIntent(LocalBeatsIntent.CancelBulkDelete) },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Delete ${state.selectedIds.size} record(s)?") },
            text = { Text("The selected villages will be removed from the local directory. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { onIntent(LocalBeatsIntent.ConfirmBulkDelete) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { onIntent(LocalBeatsIntent.CancelBulkDelete) }) { Text("Cancel") } },
        )
    }

    if (confirmReplaceImport) {
        AlertDialog(
            onDismissRequest = { confirmReplaceImport = false },
            icon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
            title = { Text("Replace entire directory?") },
            text = { Text("All ${state.totalRecords} existing record(s) will be deleted and replaced by the rows in the selected spreadsheet. You will see a preview before anything is written.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReplaceImport = false
                    pendingImportMode = ImportMode.REPLACE_ALL
                    importPicker.launch(XLSX_MIME_TYPES)
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { confirmReplaceImport = false }) { Text("Cancel") } },
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
            title = { Text(message) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(16.dp))
                    Text("Please wait…")
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
                    Icon(Icons.Default.Clear, contentDescription = "Cancel selection")
                }
                Text(
                    "${state.selectedIds.size} selected",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onIntent(LocalBeatsIntent.RequestBulkDelete) }) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Delete")
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
                    Text(if (mode == LocalViewMode.SEARCH) "Search" else "By beat")
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
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                supportingText = {
                    Text("${state.totalRecords} record(s) offline • also matches beat number or PIN • long-press to select")
                },
            )
        }

        FilterChipsRow(
            label = "State",
            options = state.states,
            selected = state.selectedState,
            onSelect = { onIntent(LocalBeatsIntent.StateSelected(it)) },
        )
        FilterChipsRow(
            label = "District",
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
            title = "No matches",
            message = if (state.hasFilters) "Nothing matched with the current State/District filters." else "Try a different spelling — phonetic matching handles most transliteration variants.",
            actionLabel = if (state.hasFilters) "Clear filters" else null,
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
private fun SwipeToDeleteRow(enabled: Boolean, onDelete: () -> Unit, content: @Composable () -> Unit) {
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
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
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
                onLongClick = onToggleSelect,
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
                    IconButton(onClick = onLookupOnline) { Icon(Icons.Default.TravelExplore, contentDescription = "Look up online") }
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SuggestionChip(onClick = onEdit, label = { Text("Beat ${record.beatNumber}") })
                SuggestionChip(onClick = onEdit, label = { Text("BO: ${record.branchOffice}") })
                SuggestionChip(onClick = onEdit, label = { Text("SO: ${record.subPostOffice}") })
                SuggestionChip(onClick = onEdit, label = { Text("PIN ${record.pincode}") })
            }
            if (record.remarks.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(record.remarks, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (query.isNotBlank()) {
                when (hit.matchKind) {
                    MatchKind.EXACT -> MatchLabel("Exact match", MaterialTheme.colorScheme.primary)
                    MatchKind.PHONETIC -> MatchLabel("Sounds like “${query.trim()}”", MaterialTheme.colorScheme.tertiary)
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
            title = "No beats to show",
            message = if (state.hasFilters) "No records match the current State/District filters." else "Loading…",
            actionLabel = if (state.hasFilters) "Clear filters" else null,
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
                "${state.beatGroups.size} beat(s) • ${state.beatGroups.sumOf { it.villageCount }} village(s)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(state.beatGroups, key = { it.key }) { group ->
            BeatGroupCard(
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
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: (BeatRecord) -> Unit,
    onLookupOnline: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Beat ${group.beatNumber}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        buildString {
                            append(group.branchOffice)
                            if (group.subPostOffice.isNotBlank()) append(" • SO ").append(group.subPostOffice)
                            if (group.pincodes.isNotEmpty()) append(" • PIN ").append(group.pincodes.joinToString(", "))
                        },
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
                Text(" villages", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = onToggle) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (expanded) "Collapse" else "Expand")
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
                                Icon(Icons.Default.TravelExplore, contentDescription = "Look up online")
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
        Text("Set up your beat directory", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Map every village to its beat and branch office once, then find it offline in seconds — even with a misspelled name.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FileUpload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Import spreadsheet (.xlsx)")
        }
        Spacer(Modifier.height(10.dp))
        FilledTonalButton(onClick = onTemplate, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Download, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Get the blank template first")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add the first village by hand")
        }
    }
}

@Composable
private fun ImportPreviewDialog(preview: ImportPreview, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
        title = { Text(if (preview.mode == ImportMode.REPLACE_ALL) "Replace directory?" else "Import these rows?") },
        text = {
            Column {
                if (preview.mode == ImportMode.REPLACE_ALL && preview.existingCount > 0) {
                    Text("• ${preview.existingCount} existing record(s) will be deleted first", color = MaterialTheme.colorScheme.error)
                }
                Text("• ${preview.willInsert} row(s) will be added")
                if (preview.duplicatesSkipped > 0) Text("• ${preview.duplicatesSkipped} duplicate(s) will be skipped")
                if (preview.blankRowsSkipped > 0) Text("• ${preview.blankRowsSkipped} blank row(s) ignored")
                if (preview.hasErrors) Text("• ${preview.errors.size} row(s) have errors and will be skipped", color = MaterialTheme.colorScheme.error)
                if (preview.hasErrors) {
                    Spacer(Modifier.height(10.dp))
                    RowErrorList(preview.errors)
                }
                if (preview.isEmpty) {
                    Spacer(Modifier.height(10.dp))
                    Text("Nothing to import.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !preview.isEmpty || preview.mode == ImportMode.REPLACE_ALL) {
                Text(if (preview.mode == ImportMode.REPLACE_ALL) "Replace" else "Import ${preview.willInsert}")
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun RowErrorList(errors: List<RowError>) {
    Text("Rows with errors:", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(4.dp))
    Box(Modifier.height(160.dp)) {
        LazyColumn {
            items(errors.take(200)) { err ->
                Text(err.describe(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun ImportReportDialog(report: ImportReport, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (report.hasErrors) Icons.Default.SearchOff else Icons.Default.Check, contentDescription = null) },
        title = { Text(if (report.hasErrors) "Import finished with issues" else "Import complete") },
        text = {
            Column {
                Text(report.summary())
                if (report.blankRowsSkipped > 0) {
                    Text("${report.blankRowsSkipped} blank row(s) ignored.", style = MaterialTheme.typography.bodySmall)
                }
                if (report.hasErrors) {
                    Spacer(Modifier.height(12.dp))
                    RowErrorList(report.errors)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
