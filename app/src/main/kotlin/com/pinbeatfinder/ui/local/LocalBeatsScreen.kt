package com.pinbeatfinder.ui.local

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pinbeatfinder.R
import com.pinbeatfinder.data.excel.ExcelCodec
import com.pinbeatfinder.data.excel.ImportMode
import com.pinbeatfinder.data.excel.ImportReport
import com.pinbeatfinder.domain.model.BeatRecord
import com.pinbeatfinder.domain.model.BeatSearchHit
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

    LaunchedEffect(Unit) {
        registerMenuHandler { action ->
            when (action) {
                LocalBeatsMenuAction.IMPORT_APPEND -> {
                    pendingImportMode = ImportMode.APPEND
                    importPicker.launch(XLSX_MIME_TYPES)
                }
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
            }
        }
    }

    LocalBeatsContent(state = state, onIntent = onIntent)

    // ---- dialogs & sheets ----------------------------------------------------------------
    state.editor?.let { editor ->
        BeatEditorSheet(
            editor = editor,
            knownStates = state.states,
            knownDistricts = state.districts,
            onFieldChange = { field, value -> onIntent(LocalBeatsIntent.EditorFieldChanged(field, value)) },
            onSave = { onIntent(LocalBeatsIntent.SaveEditor) },
            onDismiss = { onIntent(LocalBeatsIntent.DismissEditor) },
        )
    }

    state.pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { onIntent(LocalBeatsIntent.CancelDelete) },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Delete record?") },
            text = { Text("\"${record.localityName}\" (Beat ${record.beatNumber}, ${record.branchOffice}) will be removed from the local directory.") },
            confirmButton = { TextButton(onClick = { onIntent(LocalBeatsIntent.ConfirmDelete) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { onIntent(LocalBeatsIntent.CancelDelete) }) { Text("Cancel") } },
        )
    }

    if (confirmReplaceImport) {
        AlertDialog(
            onDismissRequest = { confirmReplaceImport = false },
            icon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
            title = { Text("Replace entire directory?") },
            text = { Text("All ${state.totalRecords} existing record(s) will be deleted and replaced by the rows in the selected spreadsheet. Consider sharing a backup first.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReplaceImport = false
                    pendingImportMode = ImportMode.REPLACE_ALL
                    importPicker.launch(XLSX_MIME_TYPES)
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { confirmReplaceImport = false }) { Text("Cancel") } },
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

@Composable
fun LocalBeatsContent(state: LocalBeatsState, onIntent: (LocalBeatsIntent) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { onIntent(LocalBeatsIntent.QueryChanged(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                Text(
                    if (state.isDirectoryEmpty) "Directory is empty — add a record or import an Excel file."
                    else "${state.totalRecords} record(s) offline • also matches beat number or PIN",
                )
            },
        )

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

        if (state.isSearching) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))

        when {
            state.isDirectoryEmpty -> EmptyState(
                icon = Icons.Default.Inventory2,
                title = "No local beat records yet",
                message = "Tap “Add beat record” to enter villages manually, or use the ⋮ menu to import an .xlsx file. A blank template is available from the same menu.",
            )
            state.hits.isEmpty() && !state.isSearching -> EmptyState(
                icon = Icons.Default.SearchOff,
                title = "No matches",
                message = if (state.hasFilters) "Nothing matched with the current State/District filters." else "Try a different spelling — phonetic matching handles most transliteration variants.",
                actionLabel = if (state.hasFilters) "Clear filters" else null,
                onAction = { onIntent(LocalBeatsIntent.ClearFilters) },
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.hits, key = { it.record.id }) { hit ->
                    BeatRecordCard(
                        hit = hit,
                        showScore = state.query.isNotBlank(),
                        onEdit = { onIntent(LocalBeatsIntent.OpenEditor(hit.record)) },
                        onDelete = { onIntent(LocalBeatsIntent.RequestDelete(hit.record)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BeatRecordCard(
    hit: BeatSearchHit,
    showScore: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val record: BeatRecord = hit.record
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(record.localityName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${record.district}, ${record.state}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
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
            if (showScore && hit.score >= 0.99) {
                Spacer(Modifier.height(4.dp))
                Text("Exact match", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            } else if (showScore && hit.score in 0.55..0.79) {
                Spacer(Modifier.height(4.dp))
                Text("Sounds-like match", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
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
                    Text("Rejected rows (fix and re-import):", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.height(200.dp)) {
                        LazyColumn {
                            items(report.errors.take(200)) { err ->
                                Text(err.describe(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
