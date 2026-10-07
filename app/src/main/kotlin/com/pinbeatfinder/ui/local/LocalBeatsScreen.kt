package com.pinbeatfinder.ui.local

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pinbeatfinder.R
import com.pinbeatfinder.ui.components.PrimaryButton
import kotlinx.coroutines.launch
import com.pinbeatfinder.core.util.MatchHighlighter
import com.pinbeatfinder.data.excel.ExcelCodec
import com.pinbeatfinder.data.excel.ImportMode
import com.pinbeatfinder.data.excel.ImportPreview
import com.pinbeatfinder.data.excel.ImportReport
import com.pinbeatfinder.data.excel.RowError
import com.pinbeatfinder.core.dedupe.DuplicatePair
import com.pinbeatfinder.domain.model.BeatGroup
import com.pinbeatfinder.domain.model.BeatRecord
import com.pinbeatfinder.domain.model.BeatSearchHit
import com.pinbeatfinder.domain.model.MatchKind
import com.pinbeatfinder.domain.model.OfficeSummary
import com.pinbeatfinder.domain.model.OfficeType
import com.pinbeatfinder.ui.components.AppCard
import com.pinbeatfinder.ui.components.OfficeTypeDropdown
import com.pinbeatfinder.ui.components.PillLabel
import com.pinbeatfinder.ui.components.PillSearchField
import com.pinbeatfinder.ui.components.RoundIconButton
import com.pinbeatfinder.ui.components.ScreenTitleBar
import com.pinbeatfinder.ui.components.CollapsingHeader
import com.pinbeatfinder.ui.components.EmptyState
import com.pinbeatfinder.ui.components.MotionVisibility
import com.pinbeatfinder.ui.components.VoiceSearchButton
import com.pinbeatfinder.data.prefs.BackupReminder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.pinbeatfinder.ui.components.LocalListBottomPadding
import com.pinbeatfinder.ui.components.message
import com.pinbeatfinder.ui.components.placeLine
import com.pinbeatfinder.ui.theme.rememberHaptic

/** Overflow-menu actions surfaced by the host screen's top bar. Order = menu order. */
enum class LocalBeatsMenuAction(val labelRes: Int, val icon: ImageVector) {
    IMPORT_APPEND(R.string.menu_import_append, Icons.Default.FileUpload),
    IMPORT_REPLACE(R.string.menu_import_replace, Icons.Default.SwapHoriz),
    SHARE_BACKUP(R.string.menu_share_backup, Icons.Default.Share),
    SAVE_BACKUP(R.string.menu_save_backup, Icons.Default.Save),
    SHARE_TEMPLATE(R.string.menu_share_template, Icons.Default.Share),
    SAVE_TEMPLATE(R.string.menu_save_template, Icons.Default.Download),
    OFFICE_SUMMARY_PDF(R.string.menu_office_summary, Icons.Default.Summarize),
    FIND_DUPLICATES(R.string.menu_find_duplicates, Icons.Default.FindReplace),
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
    launchIntent: (Intent) -> Unit,
    onLookupOnline: (String) -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = viewModel::onIntent
    val context = LocalContext.current
    val haptic = rememberHaptic()

    // ---- SAF launchers -------------------------------------------------------------------
    var pendingImportMode by rememberSaveable { mutableStateOf(ImportMode.APPEND) }
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
    // The overflow menu sits in this screen's own title row now.
    val onMenuAction: (LocalBeatsMenuAction) -> Unit = { action ->
        when (action) {
            LocalBeatsMenuAction.IMPORT_APPEND -> startAppendImport()
            LocalBeatsMenuAction.IMPORT_REPLACE -> confirmReplaceImport = true
            LocalBeatsMenuAction.SHARE_BACKUP -> onIntent(LocalBeatsIntent.ShareBackup)
            LocalBeatsMenuAction.SAVE_BACKUP -> backupSaver.launch(ExcelCodec.backupFileName())
            LocalBeatsMenuAction.SHARE_TEMPLATE -> onIntent(LocalBeatsIntent.ShareTemplate)
            LocalBeatsMenuAction.SAVE_TEMPLATE -> templateSaver.launch(ExcelCodec.TEMPLATE_FILE_NAME)
            LocalBeatsMenuAction.OFFICE_SUMMARY_PDF -> onIntent(LocalBeatsIntent.PrintOfficeSummary)
            LocalBeatsMenuAction.FIND_DUPLICATES -> onIntent(LocalBeatsIntent.FindDuplicates)
        }
    }

    LaunchedEffect(viewModel) {
        // Snackbars suspend until they are dismissed; each runs in its own coroutine so a share
        // sheet or a save haptic queued behind one is never delayed by it.
        viewModel.effects.collect { effect ->
            when (effect) {
                is LocalBeatsEffect.ShowMessage -> launch { snackbarHostState.showSnackbar(effect.text.asString(context)) }
                is LocalBeatsEffect.LaunchIntent -> launchIntent(effect.intent)
                LocalBeatsEffect.Saved -> haptic(HapticFeedbackType.Confirm)
                is LocalBeatsEffect.ShowUndoChange -> launch {
                    val result = snackbarHostState.showSnackbar(
                        message = effect.text.asString(context),
                        actionLabel = context.getString(R.string.action_undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) onIntent(LocalBeatsIntent.UndoLastChange)
                }
                is LocalBeatsEffect.ShowUndoDelete -> launch {
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
        onOpenSettings = onOpenSettings,
        onMenuAction = onMenuAction,
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
            onTogglePickerOffice = { onIntent(LocalBeatsIntent.TogglePickerOffice(it)) },
            onConfirmPickerSelection = { onIntent(LocalBeatsIntent.ConfirmPickerSelection) },
            onSkipQueued = { onIntent(LocalBeatsIntent.SkipQueued) },
            onOpenExisting = { onIntent(LocalBeatsIntent.OpenEditor(it)) },
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
            title = { Text(pluralStringResource(R.plurals.bulk_delete_title, state.selectedIds.size, state.selectedIds.size)) },
            text = { Text(stringResource(R.string.bulk_delete_message)) },
            confirmButton = { TextButton(onClick = { onIntent(LocalBeatsIntent.ConfirmBulkDelete) }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { onIntent(LocalBeatsIntent.CancelBulkDelete) }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (state.showMoveToBeat) {
        val selected = state.records.filter { it.id in state.selectedIds }
        val beats = state.beatGroups
            .filter { g -> selected.any { r -> r.officeName.equals(g.officeName, true) && r.pincode in g.pincodes } }
            .map { it.beatNumber }.distinct()
        BeatPickerDialog(
            title = pluralStringResource(R.plurals.move_beat_title, state.selectedIds.size, state.selectedIds.size),
            message = null,
            beats = beats,
            confirmLabel = stringResource(R.string.action_move),
            onConfirm = { onIntent(LocalBeatsIntent.MoveSelectedToBeat(it)) },
            onDismiss = { onIntent(LocalBeatsIntent.CancelMoveToBeat) },
        )
    }

    state.mergeSource?.let { source ->
        val beats = (state.officeGroups.ifEmpty { state.beatGroups })
            .filter { it.key != source.key && it.officeName.equals(source.officeName, true) && it.pincodes.any { p -> p in source.pincodes } }
            .map { it.beatNumber }.distinct()
        BeatPickerDialog(
            title = stringResource(R.string.merge_beat_title, source.beatNumber),
            message = stringResource(R.string.merge_beat_message, source.beatNumber),
            beats = beats,
            confirmLabel = stringResource(R.string.action_merge),
            onConfirm = { onIntent(LocalBeatsIntent.MergeBeat(source, it)) },
            onDismiss = { onIntent(LocalBeatsIntent.CancelMergeBeat) },
        )
    }

    if (state.showRenameOffice) {
        state.openOffice?.let { office ->
            RenameOfficeDialog(
                office = office,
                onConfirm = { onIntent(LocalBeatsIntent.RenameOffice(office, it)) },
                onDismiss = { onIntent(LocalBeatsIntent.CancelRenameOffice) },
            )
        }
    }

    state.healthIssue?.let { issue ->
        HealthIssueDialog(
            issue = issue,
            report = state.health ?: HealthReport(),
            onEdit = { onIntent(LocalBeatsIntent.ClearHealthIssue); onIntent(LocalBeatsIntent.OpenEditor(it)) },
            onDismiss = { onIntent(LocalBeatsIntent.ClearHealthIssue) },
        )
    }

    state.duplicates?.let { pairs ->
        DuplicatesDialog(
            pairs = pairs,
            onKeep = { pair, keep -> onIntent(LocalBeatsIntent.ResolveDuplicate(pair, keep)) },
            onEdit = { onIntent(LocalBeatsIntent.OpenEditor(it)) },
            onDismiss = { onIntent(LocalBeatsIntent.DismissDuplicates) },
        )
    }

    if (state.showFilters) {
        FilterSheet(
            filters = state.filters,
            options = state.filterOptions,
            onChange = { onIntent(LocalBeatsIntent.FiltersChanged(it)) },
            onClear = { onIntent(LocalBeatsIntent.ClearFilters) },
            onDismiss = { onIntent(LocalBeatsIntent.HideFilters) },
        )
    }

    if (state.showOfficeTypes) {
        OfficeTypesDialog(
            offices = state.officeSummaries,
            onSet = { office, type -> onIntent(LocalBeatsIntent.SetOfficeType(office, type)) },
            onDismiss = { onIntent(LocalBeatsIntent.HideOfficeTypes) },
        )
    }

    if (confirmReplaceImport) {
        AlertDialog(
            onDismissRequest = { confirmReplaceImport = false },
            icon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
            title = { Text(stringResource(R.string.replace_title)) },
            text = { Text(pluralStringResource(R.plurals.replace_message, state.totalRecords, state.totalRecords)) },
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

@Composable
fun LocalBeatsContent(
    state: LocalBeatsState,
    onIntent: (LocalBeatsIntent) -> Unit,
    onLookupOnline: (String) -> Unit = {},
    onImportClick: () -> Unit = {},
    onTemplateClick: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onMenuAction: (LocalBeatsMenuAction) -> Unit = {},
) {
    if (state.isDirectoryEmpty) {
        Column(Modifier.fillMaxSize()) {
            LocalTitleBar(onOpenSettings = onOpenSettings, onMenuAction = onMenuAction)
            OnboardingEmptyState(
                onAdd = { onIntent(LocalBeatsIntent.OpenEditor()) },
                onImport = onImportClick,
                onTemplate = onTemplateClick,
            )
        }
        return
    }

    // The office cards keep their scroll position while an office screen is open on top.
    val officeListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    val openOffice = state.openOffice
    if (openOffice != null) BackHandler { onIntent(LocalBeatsIntent.CloseOffice) }

    // Tablets and landscape phones: the office list on the left, the open office on the right.
    val twoPane = LocalConfiguration.current.screenWidthDp >= TWO_PANE_MIN_WIDTH_DP
    if (twoPane) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(0.42f).fillMaxSize()) {
                MainList(state, officeListState, onIntent, onLookupOnline, onOpenSettings, onMenuAction)
            }
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.weight(0.58f).fillMaxSize()) {
                if (openOffice != null) {
                    OfficeScreen(office = openOffice, state = state, onIntent = onIntent, onLookupOnline = onLookupOnline)
                } else {
                    EmptyState(
                        icon = Icons.Default.Route,
                        title = stringResource(R.string.local_no_offices_title),
                        message = stringResource(R.string.tablet_pick_office),
                    )
                }
            }
        }
        return
    }

    if (openOffice != null) {
        OfficeScreen(office = openOffice, state = state, onIntent = onIntent, onLookupOnline = onLookupOnline)
        return
    }
    MainList(state, officeListState, onIntent, onLookupOnline, onOpenSettings, onMenuAction)
}

/** Width from which Local Beats shows the office list and the open office side by side. */
private const val TWO_PANE_MIN_WIDTH_DP = 720

/** Title, collapsing search header and the office cards / search results / beat groups. */
@Composable
private fun MainList(
    state: LocalBeatsState,
    officeListState: LazyListState,
    onIntent: (LocalBeatsIntent) -> Unit,
    onLookupOnline: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onMenuAction: (LocalBeatsMenuAction) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LocalTitleBar(onOpenSettings = onOpenSettings, onMenuAction = onMenuAction)
        CollapsingHeader(
            modifier = Modifier.fillMaxSize(),
            revealKey = listOf(state.query, state.filters, state.viewMode, state.hits.size, state.records.size, state.backupDue),
            header = { LocalHeader(state, onIntent) },
        ) {
            Column(Modifier.fillMaxSize()) {
                if (state.isSearching && state.viewMode == LocalViewMode.SEARCH) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(2.dp))
                when {
                    state.showsOfficeCards -> OfficeCardsList(state, officeListState, onIntent)
                    state.viewMode == LocalViewMode.SEARCH -> SearchResults(state, onIntent, onLookupOnline)
                    else -> BeatGroupsList(state, onIntent, onLookupOnline)
                }
            }
        }
    }
}

/** "Local Beats" title with the Settings button and the overflow menu (import, backup, template, duplicates). */
@Composable
private fun LocalTitleBar(onOpenSettings: () -> Unit, onMenuAction: (LocalBeatsMenuAction) -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    ScreenTitleBar(
        title = stringResource(R.string.tab_local),
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more_options))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    LocalBeatsMenuAction.entries.forEachIndexed { index, action ->
                        if (index == 2 || index == 4 || index == 6) HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(action.labelRes), maxLines = 1) },
                            leadingIcon = { Icon(action.icon, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onMenuAction(action)
                            },
                        )
                    }
                }
            }
        },
    )
}

// ------------------------------------------------------------------ office cards (search box empty)

@Composable
private fun OfficeCardsList(state: LocalBeatsState, listState: LazyListState, onIntent: (LocalBeatsIntent) -> Unit) {
    if (state.officeSummaries.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Route,
            title = stringResource(R.string.local_no_offices_title),
            message = stringResource(if (state.hasFilters) R.string.local_no_beats_filtered else R.string.local_loading),
            actionLabel = if (state.hasFilters) stringResource(R.string.action_clear_filters) else null,
            onAction = { onIntent(LocalBeatsIntent.ClearFilters) },
        )
        return
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = LocalListBottomPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.health?.let { report ->
            item(key = "health") { HealthCard(report, onIntent) }
        }
        items(state.officeSummaries, key = { it.key }) { office ->
            OfficeCard(
                office = office,
                accountOffice = state.records.firstOrNull { it.officeName.equals(office.officeName, true) && it.pincode in office.pincodes }?.accountOffice.orEmpty(),
                selected = state.openOffice?.key == office.key,
            ) { onIntent(LocalBeatsIntent.OpenOffice(office)) }
        }
    }
}

/**
 * Directory health: one line per finding with a Fix action, or a single "no problems" line.
 * Mixed office types go to the existing bulk fixer; the other two open a list of the villages.
 */
@Composable
private fun HealthCard(report: HealthReport, onIntent: (LocalBeatsIntent) -> Unit) {
    if (report.isClean) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.health_title) + ": " + stringResource(R.string.health_all_good),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.health_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (report.blankAccount.isNotEmpty()) {
                HealthLine(pluralStringResource(R.plurals.health_blank_account_n, report.blankAccount.size, report.blankAccount.size)) {
                    onIntent(LocalBeatsIntent.ShowHealthIssue(HealthIssue.BLANK_ACCOUNT))
                }
            }
            if (report.mixedOffices > 0) {
                HealthLine(pluralStringResource(R.plurals.health_mixed_offices_n, report.mixedOffices, report.mixedOffices)) {
                    onIntent(LocalBeatsIntent.ShowOfficeTypes)
                }
            }
            if (report.unknownPinRecords.isNotEmpty()) {
                HealthLine(pluralStringResource(R.plurals.health_unknown_pins_n, report.unknownPinRecords.size, report.unknownPinRecords.size)) {
                    onIntent(LocalBeatsIntent.ShowHealthIssue(HealthIssue.UNKNOWN_PIN))
                }
            }
        }
    }
}

@Composable
private fun HealthLine(text: String, onFix: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onFix, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)) {
            Text(stringResource(R.string.health_fix), fontWeight = FontWeight.Bold)
        }
    }
}

/** The villages behind one health finding; tapping one opens it in the editor. */
@Composable
private fun HealthIssueDialog(issue: HealthIssue, report: HealthReport, onEdit: (BeatRecord) -> Unit, onDismiss: () -> Unit) {
    val records = when (issue) {
        HealthIssue.BLANK_ACCOUNT -> report.blankAccount
        HealthIssue.UNKNOWN_PIN -> report.unknownPinRecords
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.HealthAndSafety, contentDescription = null) },
        title = {
            Text(stringResource(if (issue == HealthIssue.BLANK_ACCOUNT) R.string.health_issue_blank_account_title else R.string.health_issue_unknown_pin_title))
        },
        text = {
            Column {
                Text(stringResource(R.string.health_issue_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.heightIn(max = 360.dp)) {
                    LazyColumn {
                        items(records, key = { it.id }) { r ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickableCompat(onClick = { onEdit(r) })
                                    .heightIn(min = 48.dp)
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.localityName, style = MaterialTheme.typography.bodyLarge)
                                    Text(r.officeDisplay + " · " + stringResource(R.string.local_beat_title, r.beatNumber), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                PinText(r.pincode, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

/** A PIN in the accent colour; screen readers hear "PIN 828201" instead of six digits. */
@Composable
private fun PinText(pincode: String, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.a11y_pin, pincode)
    Text(
        pincode,
        style = style,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = modifier.semantics { contentDescription = label },
    )
}

/** Name; PIN and account office; beat and village counts. Nothing on the right: the whole card opens the office. */
@Composable
private fun OfficeCard(office: OfficeSummary, accountOffice: String, selected: Boolean = false, onOpen: () -> Unit) {
    val description = stringResource(R.string.a11y_office_card, office.officeDisplay, office.pincodes.joinToString(", "), beatsAndVillages(office.beatCount, office.recordCount))
    AppCard(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
        onClick = onOpen,
        containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(office.officeDisplay, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            PinAndAccount(pincodes = office.pincodes, accountOffice = accountOffice)
            Spacer(Modifier.height(4.dp))
            Text(
                beatsAndVillages(office.beatCount, office.recordCount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

/** "828201  Account Dhanbad HO": the PIN in the accent colour, the account office in secondary text. */
@Composable
private fun PinAndAccount(pincodes: List<String>, accountOffice: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        PinText(pincodes.joinToString(", "), style = MaterialTheme.typography.titleSmall)
        if (accountOffice.isNotBlank()) {
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.local_beat_subtitle_account, accountOffice),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ------------------------------------------------------------------ office screen

/** One office: its villages grouped by beat, with add/edit/delete/select and share for the office or a beat. */
@Composable
private fun OfficeScreen(
    office: OfficeSummary,
    state: LocalBeatsState,
    onIntent: (LocalBeatsIntent) -> Unit,
    onLookupOnline: (String) -> Unit,
) {
    val sample = state.officeGroups.firstOrNull()?.records?.firstOrNull()
    val anyGroup = state.officeGroups.firstOrNull()
    var shareMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ScreenTitleBar(
            title = office.officeDisplay,
            navigationIcon = {
                IconButton(onClick = { onIntent(LocalBeatsIntent.CloseOffice) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
            subtitle = { PinAndAccount(pincodes = office.pincodes, accountOffice = sample?.accountOffice.orEmpty()) },
            actions = {
                if (anyGroup != null) {
                    Box {
                        IconButton(onClick = { shareMenu = true }) { Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share)) }
                        DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_whole_office), maxLines = 1) },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = { shareMenu = false; onIntent(LocalBeatsIntent.ShareOffice(anyGroup)) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.print_whole_office), maxLines = 1) },
                                leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                                onClick = { shareMenu = false; onIntent(LocalBeatsIntent.PrintOffice(anyGroup)) },
                            )
                        }
                    }
                }
                Box {
                    IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.office_overflow)) }
                    DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_rename_office), maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { moreMenu = false; onIntent(LocalBeatsIntent.RequestRenameOffice) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.office_types_title), maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                            onClick = { moreMenu = false; onIntent(LocalBeatsIntent.ShowOfficeTypes) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_find_duplicates), maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.FindReplace, contentDescription = null) },
                            onClick = { moreMenu = false; onIntent(LocalBeatsIntent.FindDuplicates) },
                        )
                    }
                }
            },
        )
        MotionVisibility(visible = state.isSelecting) { SelectionBar(state, onIntent) }
        if (state.officeGroups.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Route,
                title = stringResource(R.string.office_no_records_title),
                message = stringResource(R.string.office_no_records_message),
                actionLabel = stringResource(R.string.action_add),
                onAction = { onIntent(LocalBeatsIntent.OpenEditor()) },
            )
        } else LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = LocalListBottomPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            state.officeGroups.forEach { group ->
                item(key = "beat|" + group.key) {
                    BeatHeaderRow(group = group, onIntent = onIntent)
                }
                // One rounded card per beat holding its compact village rows.
                item(key = "rows|" + group.key) {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        group.records.forEachIndexed { index, record ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            SwipeToDeleteRow(
                                enabled = !state.isSelecting,
                                onDelete = { onIntent(LocalBeatsIntent.RequestDelete(record)) },
                                shape = RectangleShape,
                            ) {
                                VillageRow(
                                    record = record,
                                    selecting = state.isSelecting,
                                    selected = record.id in state.selectedIds,
                                    onEdit = { onIntent(LocalBeatsIntent.OpenEditor(record)) },
                                    onDelete = { onIntent(LocalBeatsIntent.RequestDelete(record)) },
                                    onLookupOnline = { onLookupOnline(record.pincode) },
                                    onToggleSelect = { onIntent(LocalBeatsIntent.ToggleSelected(record.id)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Long-press selection bar: cancel, count, delete selected. */
@Composable
private fun SelectionBar(state: LocalBeatsState, onIntent: (LocalBeatsIntent) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onIntent(LocalBeatsIntent.ClearSelection) }) {
            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.local_cancel_selection), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text(
            stringResource(R.string.local_selected, state.selectedIds.size),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onIntent(LocalBeatsIntent.RequestMoveToBeat) }) {
            Icon(Icons.Default.SwapHoriz, contentDescription = stringResource(R.string.action_move_to_beat), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        IconButton(onClick = { onIntent(LocalBeatsIntent.RequestBulkDelete) }) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

/** Beat chooser for "move to beat" and "merge beat": existing beats as chips plus a free number. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BeatPickerDialog(
    title: String,
    message: String?,
    beats: List<String>,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var beat by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (message != null) {
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                }
                if (beats.isNotEmpty()) {
                    Text(stringResource(R.string.move_beat_pick), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        beats.forEach { b ->
                            FilterChip(selected = b == beat, onClick = { beat = b }, label = { Text(stringResource(R.string.local_beat_title, b)) })
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                OutlinedTextField(
                    value = beat,
                    onValueChange = { beat = it },
                    label = { Text(stringResource(R.string.field_target_beat)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(beat) }, enabled = beat.isNotBlank()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun RenameOfficeDialog(office: OfficeSummary, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(office.officeName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Edit, contentDescription = null) },
        title = { Text(stringResource(R.string.rename_office_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.rename_office_message, office.officeDisplay),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_new_office_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank() && name.trim() != office.officeName) { Text(stringResource(R.string.action_rename)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * Compact 56 dp village row inside an office: name, PIN in the accent colour, a look-up button
 * and an overflow with Edit and Delete. Tap edits, long-press selects, swipe asks to delete.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VillageRow(
    record: BeatRecord,
    selecting: Boolean,
    selected: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLookupOnline: () -> Unit,
    onToggleSelect: () -> Unit,
) {
    val haptic = rememberHaptic()
    var menu by remember { mutableStateOf(false) }
    val selectedLabel = stringResource(R.string.a11y_selected)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(
                onClick = { if (selecting) onToggleSelect() else onEdit() },
                onLongClick = {
                    haptic(HapticFeedbackType.LongPress)
                    onToggleSelect()
                },
            )
            .semantics { if (selected) stateDescription = selectedLabel }
            .heightIn(min = 56.dp)
            .padding(start = 18.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(record.localityName, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (record.remarks.isNotBlank()) {
                Text(record.remarks, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        PinText(record.pincode, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp))
        IconButton(onClick = onLookupOnline) {
            Icon(Icons.Default.TravelExplore, contentDescription = stringResource(R.string.action_look_up_online), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.record_overflow), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit), maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = { menu = false; onEdit() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete), maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

/** "Beat 2" pill, the village count and a share menu for that beat. */
@Composable
private fun BeatHeaderRow(group: BeatGroup, onIntent: (LocalBeatsIntent) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        PillLabel(stringResource(R.string.local_beat_title, group.beatNumber))
        Spacer(Modifier.width(12.dp))
        Text(
            pluralStringResource(R.plurals.count_villages, group.villageCount, group.villageCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share), modifier = Modifier.size(20.dp)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.share_this_beat), maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                    onClick = { menu = false; onIntent(LocalBeatsIntent.ShareBeat(group)) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.print_this_beat), maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                    onClick = { menu = false; onIntent(LocalBeatsIntent.PrintBeat(group)) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_share_image), maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                    onClick = { menu = false; onIntent(LocalBeatsIntent.ShareBeatImage(group)) },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_merge_beat), maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.CallMerge, contentDescription = null) },
                    onClick = { menu = false; onIntent(LocalBeatsIntent.RequestMergeBeat(group)) },
                )
            }
        }
    }
}

/**
 * Under the title: the selection bar, the pill search field with the round Filters button (or
 * the Office types chip in By beat), the active filter chips, and the summary line with the
 * view switch. Slides away as the list scrolls.
 */
@Composable
private fun LocalHeader(state: LocalBeatsState, onIntent: (LocalBeatsIntent) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        MotionVisibility(visible = state.isSelecting) { SelectionBar(state, onIntent) }
        if (state.backupDue) {
            state.backupStatus?.let { status ->
                BackupNudge(status, onBackup = { onIntent(LocalBeatsIntent.ShareBackup) }, onLater = { onIntent(LocalBeatsIntent.SnoozeBackupReminder) })
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.viewMode == LocalViewMode.SEARCH) {
                PillSearchField(
                    value = state.query,
                    onValueChange = { onIntent(LocalBeatsIntent.QueryChanged(it)) },
                    placeholder = stringResource(R.string.search_local_hint),
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    trailing = { VoiceSearchButton(onResult = { onIntent(LocalBeatsIntent.QueryChanged(it)) }) },
                )
            } else {
                AssistChip(
                    onClick = { onIntent(LocalBeatsIntent.ShowOfficeTypes) },
                    label = { Text(stringResource(R.string.action_fix_office_types), maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.width(10.dp))
            RoundIconButton(
                onClick = { onIntent(LocalBeatsIntent.ShowFilters) },
                icon = Icons.Default.FilterList,
                contentDescription = stringResource(R.string.filter_button),
                active = state.hasFilters,
            )
        }
        state.addressQuery?.let { parsed ->
            // The query was read as a pasted address: say which words and PIN were searched.
            val parts = parsed.candidates.take(3).toMutableList()
            parsed.pincode?.let { parts += stringResource(R.string.local_beat_subtitle_pin, it) }
            Text(
                stringResource(R.string.local_address_hint, parts.joinToString(", ")),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
            )
        }
        if (state.viewMode == LocalViewMode.SEARCH && state.query.isBlank()) {
            Text(
                pluralStringResource(R.plurals.local_supporting, state.totalRecords, state.totalRecords),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
            )
        }

        // Active filters as removable chips; the round button above opens the sheet.
        FilterBar(
            filters = state.filters,
            onOpen = { onIntent(LocalBeatsIntent.ShowFilters) },
            onChange = { onIntent(LocalBeatsIntent.FiltersChanged(it)) },
            onClear = { onIntent(LocalBeatsIntent.ClearFilters) },
            showButton = false,
        )

        // Summary and the view switch live in the header so they collapse with it.
        val summary = when {
            state.showsOfficeCards && state.officeSummaries.isNotEmpty() ->
                pluralStringResource(R.plurals.count_offices, state.officeSummaries.size, state.officeSummaries.size) + " · " +
                    pluralStringResource(R.plurals.count_villages, state.records.size, state.records.size)
            state.viewMode == LocalViewMode.BY_BEAT && state.beatGroups.isNotEmpty() ->
                beatsAndVillages(state.beatGroups.size, state.beatGroups.sumOf { it.villageCount })
            else -> ""
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val toBeats = state.viewMode == LocalViewMode.SEARCH
            TextButton(
                onClick = { onIntent(LocalBeatsIntent.SetViewMode(if (toBeats) LocalViewMode.BY_BEAT else LocalViewMode.SEARCH)) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.tertiary),
            ) {
                Text(stringResource(if (toBeats) R.string.local_switch_to_beats else R.string.local_switch_to_offices), fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

/** "Back up your directory": shown once enough edits have piled up since the last backup. */
@Composable
private fun BackupNudge(status: BackupReminder.Status, onBackup: () -> Unit, onLater: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val message = status.lastBackupAt?.let { last ->
        stringResource(R.string.backup_due_old, status.editsSinceBackup, SimpleDateFormat("d MMM yyyy", locale).format(Date(last)))
    } ?: stringResource(R.string.backup_due_never, status.editsSinceBackup)
    AppCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(Modifier.padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.backup_due_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(top = 4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onLater, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) {
                    Text(stringResource(R.string.backup_later))
                }
                TextButton(onClick = onBackup, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) {
                    Text(stringResource(R.string.backup_now), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** "3 beats · 42 villages", each half pluralised on its own. */
@Composable
private fun beatsAndVillages(beats: Int, villages: Int): String =
    pluralStringResource(R.plurals.count_beats, beats, beats) + " · " + pluralStringResource(R.plurals.count_villages, villages, villages)

// ------------------------------------------------------------------ search mode

@Composable
private fun SearchResults(state: LocalBeatsState, onIntent: (LocalBeatsIntent) -> Unit, onLookupOnline: (String) -> Unit) {
    if (state.hits.isEmpty() && !state.isSearching) {
        val quickAddName = (state.addressQuery?.candidates?.firstOrNull() ?: state.query).trim()
        EmptyState(
            icon = Icons.Default.SearchOff,
            title = stringResource(R.string.local_no_matches_title),
            message = stringResource(if (state.hasFilters) R.string.local_no_matches_filtered else R.string.local_no_matches_hint),
            actionLabel = if (state.hasFilters) stringResource(R.string.action_clear_filters) else null,
            onAction = { onIntent(LocalBeatsIntent.ClearFilters) },
            secondaryLabel = quickAddName.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.quick_add_village, it) },
            onSecondary = { onIntent(LocalBeatsIntent.AddFromQuery) },
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = LocalListBottomPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.hits, key = { it.record.id }) { hit ->
            SwipeToDeleteRow(
                enabled = !state.isSelecting,
                onDelete = { onIntent(LocalBeatsIntent.RequestDelete(hit.record)) },
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
                    onOpenOffice = { onIntent(LocalBeatsIntent.OpenOfficeOf(hit.record)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteRow(
    enabled: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable () -> Unit,
) {
    // A full swipe only asks; the row springs back and the confirm dialog decides. Returning
    // false keeps the card in place so a "Cancel" needs no undo.
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDelete()
            false
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
                    .background(MaterialTheme.colorScheme.errorContainer, shape)
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
    /** Opens the record's office screen; null when already on that screen. */
    onOpenOffice: (() -> Unit)?,
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

    // Cross-office search result: the chips (beat, office, account, PIN) say where the village belongs.
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selecting) onToggleSelect() else onEdit() },
                onLongClick = {
                    haptic(HapticFeedbackType.LongPress)
                    onToggleSelect()
                },
            ),
        containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(start = 18.dp, top = 12.dp, end = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selecting) {
                    Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
                    Spacer(Modifier.width(4.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        placeLine(record.district, record.state),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!selecting) {
                    val tint = MaterialTheme.colorScheme.onSurfaceVariant
                    IconButton(onClick = onLookupOnline) { Icon(Icons.Default.TravelExplore, contentDescription = stringResource(R.string.action_look_up_online), tint = tint) }
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit), tint = tint) }
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = tint) }
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
                // The office chip is the way from a found village to its office screen.
                SuggestionChip(
                    onClick = onOpenOffice ?: onEdit,
                    label = { Text(stringResource(R.string.chip_office, record.officeDisplay)) },
                    icon = if (onOpenOffice != null) {
                        { Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                )
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

// ------------------------------------------------------------------ duplicates

/** Probable duplicate pairs with "Keep this one" on each side; tapping a name opens it for editing. */
@Composable
private fun DuplicatesDialog(
    pairs: List<DuplicatePair>,
    onKeep: (DuplicatePair, BeatRecord) -> Unit,
    onEdit: (BeatRecord) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.FindReplace, contentDescription = null) },
        title = { Text(stringResource(R.string.duplicates_title)) },
        text = {
            if (pairs.isEmpty()) {
                Text(stringResource(R.string.duplicates_none))
            } else {
                Column {
                    Text(
                        pluralStringResource(R.plurals.duplicates_hint, pairs.size, pairs.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                        items(pairs, key = { it.key }) { pair ->
                            Column(Modifier.padding(vertical = 6.dp)) {
                                Text(
                                    stringResource(R.string.duplicates_context, pair.first.beatNumber, pair.first.officeDisplay, pair.first.pincode) +
                                        " • " + stringResource(
                                        when (pair.reason) {
                                            DuplicatePair.Reason.SAME_NAME -> R.string.duplicates_reason_same
                                            DuplicatePair.Reason.SOUNDS_ALIKE -> R.string.duplicates_reason_sounds
                                            DuplicatePair.Reason.NEAR_SPELLING -> R.string.duplicates_reason_spelling
                                        },
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                listOf(pair.first, pair.second).forEach { record ->
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(
                                            Modifier
                                                .weight(1f)
                                                .combinedClickableCompat(onClick = { onEdit(record) })
                                                .padding(vertical = 4.dp),
                                        ) {
                                            Text(record.localityName, style = MaterialTheme.typography.bodyLarge)
                                            if (record.remarks.isNotBlank()) {
                                                Text(record.remarks, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        TextButton(onClick = { onKeep(pair, record) }) { Text(stringResource(R.string.action_keep_this)) }
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )
}

// ------------------------------------------------------------------ by-beat mode

/**
 * Every office in the (filtered) directory with its record/beat counts and a type dropdown.
 * Changing the dropdown re-types all of that office's records at once; mixed offices (records
 * disagreeing on the type) are flagged so they are the first thing to fix.
 */
@Composable
private fun OfficeTypesDialog(
    offices: List<OfficeSummary>,
    onSet: (OfficeSummary, OfficeType) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Tune, contentDescription = null) },
        title = { Text(stringResource(R.string.office_types_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.office_types_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(offices, key = { it.key }) { office ->
                        // Text on its own lines and the dropdown below: the summary always has the full width to wrap.
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(office.officeName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                pluralStringResource(R.plurals.count_villages, office.recordCount, office.recordCount) + " • " +
                                    pluralStringResource(R.plurals.count_beats, office.beatCount, office.beatCount) + " • " +
                                    stringResource(R.string.local_beat_subtitle_pin, office.pincodes.joinToString(", ")),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (office.isMixed) {
                                Text(
                                    stringResource(R.string.office_summary_mixed),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            OfficeTypeDropdown(
                                value = office.officeType.code,
                                onSelect = { type -> if (type != office.officeType || office.isMixed) onSet(office, type) },
                                modifier = Modifier.width(140.dp),
                                compact = true,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )
}

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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = LocalListBottomPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.beatGroups, key = { it.key }) { group ->
            BeatGroupCard(
                modifier = Modifier.animateItem(),
                group = group,
                expanded = group.key in state.expandedBeats,
                onToggle = { onIntent(LocalBeatsIntent.ToggleBeatExpanded(group.key)) },
                onEdit = { onIntent(LocalBeatsIntent.OpenEditor(it)) },
                onLookupOnline = onLookupOnline,
                onShareBeat = { onIntent(LocalBeatsIntent.ShareBeat(group)) },
                onShareOffice = { onIntent(LocalBeatsIntent.ShareOffice(group)) },
                onPrintBeat = { onIntent(LocalBeatsIntent.PrintBeat(group)) },
                onPrintOffice = { onIntent(LocalBeatsIntent.PrintOffice(group)) },
                onMergeBeat = { onIntent(LocalBeatsIntent.RequestMergeBeat(group)) },
                onShareImage = { onIntent(LocalBeatsIntent.ShareBeatImage(group)) },
            )
        }
    }
}

/** "Baliapur SO, Beat 1" with a one-line subtitle; expands to compact village rows. */
@Composable
private fun BeatGroupCard(
    group: BeatGroup,
    modifier: Modifier = Modifier,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: (BeatRecord) -> Unit,
    onLookupOnline: (String) -> Unit,
    onShareBeat: () -> Unit,
    onShareOffice: () -> Unit,
    onPrintBeat: () -> Unit,
    onPrintOffice: () -> Unit,
    onMergeBeat: () -> Unit = {},
    onShareImage: () -> Unit = {},
) {
    var shareMenu by remember { mutableStateOf(false) }
    AppCard(modifier = modifier.fillMaxWidth(), onClick = onToggle) {
        Column(Modifier.padding(start = 18.dp, top = 12.dp, end = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        group.officeDisplay + ", " + stringResource(R.string.local_beat_title, group.beatNumber),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val pinText = if (group.pincodes.isNotEmpty()) " · " + stringResource(R.string.local_beat_subtitle_pin, group.pincodes.joinToString(", ")) else ""
                    Text(
                        pluralStringResource(R.plurals.count_villages, group.villageCount, group.villageCount) + pinText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box {
                    IconButton(onClick = { shareMenu = true }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_this_beat), maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = { shareMenu = false; onShareBeat() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_whole_office), maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = { shareMenu = false; onShareOffice() },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.print_this_beat), maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                            onClick = { shareMenu = false; onPrintBeat() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.print_whole_office), maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                            onClick = { shareMenu = false; onPrintOffice() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_share_image), maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                            onClick = { shareMenu = false; onShareImage() },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_merge_beat), maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.CallMerge, contentDescription = null) },
                            onClick = { shareMenu = false; onMergeBeat() },
                        )
                    }
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            MotionVisibility(visible = expanded) {
                Column(Modifier.padding(top = 8.dp, end = 12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    group.records.forEach { record ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickableCompat(onClick = { onEdit(record) })
                                .heightIn(min = 48.dp)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(record.localityName, style = MaterialTheme.typography.bodyLarge)
                                if (record.remarks.isNotBlank()) {
                                    Text(record.remarks, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            PinText(record.pincode, style = MaterialTheme.typography.labelLarge)
                            IconButton(onClick = { onLookupOnline(record.pincode) }) {
                                Icon(Icons.Default.TravelExplore, contentDescription = stringResource(R.string.action_look_up_online), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
        PrimaryButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
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
                    Text(pluralStringResource(R.plurals.preview_existing_deleted, preview.existingCount, preview.existingCount), color = MaterialTheme.colorScheme.error)
                }
                Text(pluralStringResource(R.plurals.preview_will_add, preview.willInsert, preview.willInsert))
                if (preview.duplicatesSkipped > 0) Text(pluralStringResource(R.plurals.preview_duplicates, preview.duplicatesSkipped, preview.duplicatesSkipped))
                if (preview.blankRowsSkipped > 0) Text(pluralStringResource(R.plurals.preview_blank, preview.blankRowsSkipped, preview.blankRowsSkipped))
                if (preview.hasErrors) Text(pluralStringResource(R.plurals.preview_errors, preview.errors.size, preview.errors.size), color = MaterialTheme.colorScheme.error)
                ImportDiff(preview)
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

/** What the import changes village by village: removed (replace all), kept, and a few new names. */
@Composable
private fun ImportDiff(preview: ImportPreview) {
    val hasDiff = preview.removedCount > 0 || preview.unchangedCount > 0 || preview.addedSamples.isNotEmpty()
    if (!hasDiff) return
    Spacer(Modifier.height(10.dp))
    Text(stringResource(R.string.preview_section_changes), style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(4.dp))
    if (preview.removedCount > 0) {
        Text(pluralStringResource(R.plurals.preview_removed, preview.removedCount, preview.removedCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        Text(stringResource(R.string.preview_removed_samples, sampleNames(preview.removedSamples, preview.removedCount)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (preview.unchangedCount > 0) {
        Text(pluralStringResource(R.plurals.preview_unchanged_n, preview.unchangedCount, preview.unchangedCount), style = MaterialTheme.typography.bodySmall)
    }
    if (preview.addedSamples.isNotEmpty()) {
        Text(stringResource(R.string.preview_added_samples, sampleNames(preview.addedSamples, preview.willInsert)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Ambona, Baliapur, Rampur and 12 more". */
@Composable
private fun sampleNames(samples: List<String>, total: Int): String {
    val more = total - samples.size
    return if (more > 0) samples.joinToString(", ") + " " + stringResource(R.string.preview_more, more) else samples.joinToString(", ")
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
                    add(pluralStringResource(R.plurals.report_imported, report.inserted, report.inserted))
                    if (report.duplicatesSkipped > 0) add(pluralStringResource(R.plurals.report_duplicates, report.duplicatesSkipped, report.duplicatesSkipped))
                    if (report.errors.isNotEmpty()) add(pluralStringResource(R.plurals.report_rejected, report.errors.size, report.errors.size))
                }
                Text(parts.joinToString(", ") + ".")
                if (report.blankRowsSkipped > 0) {
                    Text(pluralStringResource(R.plurals.report_blank, report.blankRowsSkipped, report.blankRowsSkipped), style = MaterialTheme.typography.bodySmall)
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
