package com.pinbeatfinder.ui.local

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
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
import com.pinbeatfinder.domain.model.BeatField

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
                Text(
                    if (editor.isNew) "Add beat record" else "Edit beat record",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                if (!editor.isNew) {
                    // Entering neighbouring villages on the same beat: keep everything but the name.
                    TextButton(onClick = onDuplicate, enabled = !editor.isSaving) { Text("Duplicate") }
                }
            }

            EditorField(BeatField.LOCALITY, draft.localityName, editor.errors, onFieldChange, capitalize = true)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EditorField(BeatField.BEAT_NUMBER, draft.beatNumber, editor.errors, onFieldChange, modifier = Modifier.weight(1f))
                EditorField(
                    BeatField.PINCODE, draft.pincode, editor.errors, onFieldChange,
                    modifier = Modifier.weight(1f), keyboardType = KeyboardType.Number,
                )
            }
            EditorField(BeatField.BRANCH_OFFICE, draft.branchOffice, editor.errors, onFieldChange, capitalize = true)
            EditorField(BeatField.SUB_POST_OFFICE, draft.subPostOffice, editor.errors, onFieldChange, capitalize = true)
            SuggestingField(BeatField.STATE, draft.state, knownStates, editor.errors, onFieldChange)
            SuggestingField(BeatField.DISTRICT, draft.district, knownDistricts, editor.errors, onFieldChange)
            EditorField(BeatField.REMARKS, draft.remarks, editor.errors, onFieldChange, singleLine = false, imeAction = ImeAction.Done)

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, enabled = !editor.isSaving) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onSave, enabled = !editor.isSaving) {
                    if (editor.isSaving) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (editor.isNew) "Add" else "Save")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EditorField(
    field: BeatField,
    value: String,
    errors: Map<BeatField, String>,
    onFieldChange: (BeatField, String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalize: Boolean = false,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
) {
    val error = errors[field]
    OutlinedTextField(
        value = value,
        onValueChange = { onFieldChange(field, it) },
        modifier = modifier.fillMaxWidth(),
        label = { Text(if (field.required) "${field.label} *" else field.label) },
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

/** Free-text field that offers already-used values (states/districts) as a dropdown. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestingField(
    field: BeatField,
    value: String,
    suggestions: List<String>,
    errors: Map<BeatField, String>,
    onFieldChange: (BeatField, String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = remember(value, suggestions) {
        if (value.isBlank()) suggestions else suggestions.filter { it.contains(value, ignoreCase = true) && !it.equals(value, ignoreCase = true) }
    }
    val error = errors[field]

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
            label = { Text("${field.label} *") },
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
