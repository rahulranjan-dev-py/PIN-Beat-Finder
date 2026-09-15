package com.pinbeatfinder.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pinbeatfinder.domain.model.OfficeType

/**
 * Read-only dropdown of the five office kinds (GPO, HO, IDC, SO, BO). Shows the code in the
 * field and code + full name in the menu. [value] may be any text; an unparseable value is
 * displayed as typed so a bad spreadsheet import is visible rather than silently replaced.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficeTypeDropdown(
    value: String,
    onSelect: (OfficeType) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = OfficeType.parse(value)

    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.code ?: value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            label = label?.let { { Text(it) } },
            isError = error != null,
            supportingText = if (compact) null else ({ if (error != null) Text(error) }),
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            OfficeType.entries.forEach { type ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(type.code, style = MaterialTheme.typography.bodyLarge)
                            Text(type.fullName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = {
                        onSelect(type)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
