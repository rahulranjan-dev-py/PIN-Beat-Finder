package com.pinbeatfinder.ui.local

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.pinbeatfinder.R
import com.pinbeatfinder.domain.model.BeatField
import com.pinbeatfinder.domain.model.BeatRecord
import com.pinbeatfinder.domain.model.BeatSearchHit
import com.pinbeatfinder.domain.model.FieldError
import com.pinbeatfinder.data.local.OfficeStats
import com.pinbeatfinder.domain.model.PostOffice
import com.pinbeatfinder.domain.model.plainName
import com.pinbeatfinder.ui.components.OfficeTypeDropdown
import com.pinbeatfinder.ui.components.labelRes
import com.pinbeatfinder.ui.components.message

/**
 * Add/Edit form for one directory row. Validation errors come from the ViewModel (which
 * runs the shared [com.pinbeatfinder.core.util.BeatDraftValidator]) so the sheet stays dumb.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeatEditorSheet(
    editor: EditorState,
    knownStates: List<String>,
    knownDistricts: List<String>,
    onFieldChange: (BeatField, String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onDuplicate: () -> Unit = {},
    onFetchOffices: () -> Unit = {},
    onOfficeSelected: (PostOffice) -> Unit = {},
    onDismissOffices: () -> Unit = {},
    onTogglePickerOffice: (PostOffice) -> Unit = {},
    onConfirmPickerSelection: () -> Unit = {},
    onSkipQueued: () -> Unit = {},
    onOpenExisting: (BeatRecord) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val draft = editor.draft

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (editor.isNew) R.string.editor_add_title else R.string.editor_edit_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (editor.isBatch) {
                        Text(
                            stringResource(R.string.editor_batch_progress, editor.queuePosition, editor.queueTotal),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (editor.isBatch) {
                    // Drop this office (nothing to file under it) and move on to the next queued one.
                    TextButton(onClick = onSkipQueued, enabled = !editor.isSaving) { Text(stringResource(R.string.action_skip)) }
                }
                if (!editor.isNew) {
                    // Entering neighbouring villages on the same beat: keep everything but the name.
                    TextButton(onClick = onDuplicate, enabled = !editor.isSaving) { Text(stringResource(R.string.action_duplicate)) }
                }
            }

            EditorField(BeatField.LOCALITY, draft.localityName, editor.errors, onFieldChange, capitalize = true)
            if (editor.similarExisting.isNotEmpty()) {
                SimilarExistingList(editor.similarExisting, onOpenExisting)
            }
            EditorField(BeatField.BEAT_NUMBER, draft.beatNumber, editor.errors, onFieldChange)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                EditorField(
                    BeatField.PINCODE, draft.pincode, editor.errors, onFieldChange,
                    modifier = Modifier.weight(1f), keyboardType = KeyboardType.Number,
                )
                // Aligns with the text field body (the label sits 8dp above the outline).
                OutlinedButton(
                    onClick = onFetchOffices,
                    enabled = !editor.isFetching && !editor.isSaving,
                    modifier = Modifier.padding(top = 8.dp).height(56.dp),
                ) {
                    if (editor.isFetching) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.action_fetch))
                    }
                }
            }
            Text(
                stringResource(R.string.editor_fetch_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                OfficeTypeDropdown(
                    value = draft.officeType,
                    onSelect = { onFieldChange(BeatField.OFFICE_TYPE, it.code) },
                    modifier = Modifier.width(132.dp),
                    label = stringResource(BeatField.OFFICE_TYPE.labelRes()) + " *",
                    error = editor.errors[BeatField.OFFICE_TYPE]?.message(LocalContext.current, BeatField.OFFICE_TYPE),
                )
                DirectorySuggestField(
                    field = BeatField.OFFICE_NAME,
                    value = draft.officeName,
                    suggestions = editor.officeSuggestions,
                    errors = editor.errors,
                    onFieldChange = onFieldChange,
                    onPick = onOfficeSelected,
                    modifier = Modifier.weight(1f),
                )
            }
            DirectorySuggestField(
                field = BeatField.ACCOUNT_OFFICE,
                value = draft.accountOffice,
                suggestions = editor.accountSuggestions,
                errors = editor.errors,
                onFieldChange = onFieldChange,
                onPick = { onFieldChange(BeatField.ACCOUNT_OFFICE, it.name) },
            )
            SuggestingField(BeatField.STATE, draft.state, knownStates, editor.errors, onFieldChange)
            SuggestingField(BeatField.DISTRICT, draft.district, knownDistricts, editor.errors, onFieldChange)
            EditorField(BeatField.REMARKS, draft.remarks, editor.errors, onFieldChange, singleLine = false, imeAction = ImeAction.Done)

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, enabled = !editor.isSaving) { Text(stringResource(R.string.action_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onSave, enabled = !editor.isSaving) {
                    if (editor.isSaving) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        stringResource(
                            when {
                                editor.isBatch && editor.queue.isNotEmpty() -> R.string.action_save_next
                                editor.isNew -> R.string.action_add
                                else -> R.string.action_save
                            },
                        ),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // Composed after the sheet so it is drawn above it whatever the dialog / sheet window order.
    editor.fetchedOffices?.let { offices ->
        OfficePickerDialog(
            pincode = draft.pincode,
            offices = offices,
            stats = editor.officeStats,
            selection = editor.pickerSelection,
            onSelect = onOfficeSelected,
            onToggle = onTogglePickerOffice,
            onConfirmSelection = onConfirmPickerSelection,
            onDismiss = onDismissOffices,
        )
    }
}

@Composable
private fun EditorField(
    field: BeatField,
    value: String,
    errors: Map<BeatField, FieldError>,
    onFieldChange: (BeatField, String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalize: Boolean = false,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
) {
    val context = LocalContext.current
    val error = errors[field]?.message(context, field)
    val label = stringResource(field.labelRes())
    OutlinedTextField(
        value = value,
        onValueChange = { onFieldChange(field, it) },
        modifier = modifier.fillMaxWidth(),
        label = { Text(if (field.required) "$label *" else label) },
        isError = error != null,
        supportingText = { if (error != null) Text(error) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = if (capitalize) KeyboardCapitalization.Words else KeyboardCapitalization.None,
            imeAction = imeAction,
        ),
    )
}

/**
 * Warns that a locality like the one being typed is already in the directory. Tapping a row
 * abandons the new draft and opens that record instead — the usual fix for a near-duplicate.
 */
@Composable
private fun SimilarExistingList(hits: List<BeatSearchHit>, onOpen: (BeatRecord) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            stringResource(R.string.editor_similar_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        hits.forEach { hit ->
            val r = hit.record
            Text(
                stringResource(R.string.editor_similar_row, r.localityName, r.beatNumber, r.officeDisplay, r.pincode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(r) }
                    .padding(vertical = 4.dp),
            )
        }
    }
}

/**
 * Text field with live suggestions from the built-in directory. Used for Office Name (picking
 * fills every office field) and Account Office (picking inserts the office's full name).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectorySuggestField(
    field: BeatField,
    value: String,
    suggestions: List<PostOffice>,
    errors: Map<BeatField, FieldError>,
    onFieldChange: (BeatField, String) -> Unit,
    onPick: (PostOffice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val label = stringResource(field.labelRes())
    val error = errors[field]?.message(context, field)
    var expanded by remember { mutableStateOf(false) }
    val open = expanded && suggestions.isNotEmpty()

    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onFieldChange(field, it)
                expanded = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable),
            label = { Text(if (field.required) "$label *" else label) },
            isError = error != null,
            supportingText = { if (error != null) Text(error) },
            singleLine = true,
            trailingIcon = { if (suggestions.isNotEmpty()) ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { expanded = false }) {
            suggestions.forEach { office ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(office.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.office_suggestion_subtitle, office.pincode, office.district, office.state),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onPick(office)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/** Offices under the typed PIN from the built-in directory; one tap fills the office fields. */
@Composable
private fun OfficePickerDialog(
    pincode: String,
    offices: List<PostOffice>,
    stats: Map<String, OfficeStats>,
    selection: Set<String>,
    onSelect: (PostOffice) -> Unit,
    onToggle: (PostOffice) -> Unit,
    onConfirmSelection: () -> Unit,
    onDismiss: () -> Unit,
) {
    val batch = selection.isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fetch_offices_title, pincode)) },
        text = {
            Column {
                Text(
                    stringResource(if (batch) R.string.fetch_offices_batch_hint else R.string.fetch_offices_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(offices) { office ->
                        val ticked = office.name in selection
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                // With boxes ticked, a tap on the row toggles it too; otherwise it is the one-shot pick.
                                .clickable { if (batch) onToggle(office) else onSelect(office) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(office.name, style = MaterialTheme.typography.bodyLarge)
                                val account = if (office.accountOffice.isBlank()) "" else stringResource(R.string.fetch_account_office, office.accountOffice)
                                val details = listOf(office.officeType.fullName, office.deliveryStatus, account).filter { it.isNotBlank() }
                                Text(details.joinToString(" • "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                stats[office.plainName().lowercase()]?.let { local ->
                                    Text(
                                        stringResource(R.string.picker_local_stats, local.beats, local.villages),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Checkbox(checked = ticked, onCheckedChange = { onToggle(office) })
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            if (batch) {
                Button(onClick = onConfirmSelection) { Text(stringResource(R.string.action_add_n_offices, selection.size)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Free-text field that offers already-used values (states/districts) as a dropdown. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestingField(
    field: BeatField,
    value: String,
    suggestions: List<String>,
    errors: Map<BeatField, FieldError>,
    onFieldChange: (BeatField, String) -> Unit,
) {
    val context = LocalContext.current
    val label = stringResource(field.labelRes())
    var expanded by remember { mutableStateOf(false) }
    val filtered = remember(value, suggestions) {
        if (value.isBlank()) suggestions else suggestions.filter { it.contains(value, ignoreCase = true) && !it.equals(value, ignoreCase = true) }
    }
    val error = errors[field]?.message(context, field)

    ExposedDropdownMenuBox(expanded = expanded && filtered.isNotEmpty(), onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onFieldChange(field, it)
                expanded = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable),
            label = { Text("$label *") },
            isError = error != null,
            supportingText = { if (error != null) Text(error) },
            singleLine = true,
            trailingIcon = { if (suggestions.isNotEmpty()) ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        ExposedDropdownMenu(expanded = expanded && filtered.isNotEmpty(), onDismissRequest = { expanded = false }) {
            filtered.take(12).forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onFieldChange(field, option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
