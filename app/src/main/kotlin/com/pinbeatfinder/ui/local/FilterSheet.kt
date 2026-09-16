package com.pinbeatfinder.ui.local

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pinbeatfinder.R
import com.pinbeatfinder.domain.model.BeatSearchFilters
import com.pinbeatfinder.domain.model.FilterOptions
import com.pinbeatfinder.domain.model.OfficeType

/**
 * One-line filter bar: a "Filters" button with the active count, followed by one removable
 * chip per active filter. Replaces the old always-visible State/District rows.
 */
@Composable
fun FilterBar(
    filters: BeatSearchFilters,
    onOpen: () -> Unit,
    onChange: (BeatSearchFilters) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistChip(
            onClick = onOpen,
            label = { Text(if (filters.isEmpty) stringResource(R.string.filter_button) else stringResource(R.string.filter_button_n, filters.count)) },
            leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
            colors = if (filters.isEmpty) AssistChipDefaults.assistChipColors() else AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        )
        filters.state?.let { ActiveChip(it) { onChange(filters.copy(state = null, district = null)) } }
        filters.district?.let { ActiveChip(it) { onChange(filters.copy(district = null)) } }
        filters.officeType?.let { ActiveChip(it.code) { onChange(filters.copy(officeType = null)) } }
        filters.officeName?.let { ActiveChip(it) { onChange(filters.copy(officeName = null)) } }
        filters.beatNumber?.let { ActiveChip(stringResource(R.string.chip_beat, it)) { onChange(filters.copy(beatNumber = null)) } }
        filters.pincode?.let { ActiveChip(it) { onChange(filters.copy(pincode = null)) } }
        if (!filters.isEmpty) {
            TextButton(onClick = onClear) { Text(stringResource(R.string.action_clear_filters)) }
        }
    }
}

@Composable
private fun ActiveChip(label: String, onRemove: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(label) },
        trailingIcon = { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_clear), modifier = Modifier.size(16.dp)) },
    )
}

/**
 * Bottom sheet with one chip group per filter. Choices cascade (districts of the chosen state,
 * offices of the chosen district…), and each change applies immediately so the list behind the
 * sheet updates as you narrow down.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    filters: BeatSearchFilters,
    options: FilterOptions,
    onChange: (BeatSearchFilters) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.filter_sheet_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (!filters.isEmpty) TextButton(onClick = onClear) { Text(stringResource(R.string.action_clear_all)) }
            }

            ChipGroup(stringResource(R.string.filter_state), options.states, filters.state) { onChange(filters.copy(state = it, district = null)) }
            ChipGroup(stringResource(R.string.filter_district), options.districts, filters.district) { onChange(filters.copy(district = it)) }
            ChipGroup(
                stringResource(R.string.filter_office_type),
                options.officeTypes.map { it.code },
                filters.officeType?.code,
                labelOf = { code -> OfficeType.parse(code)?.let { "${it.code} • ${it.fullName}" } ?: code },
            ) { onChange(filters.copy(officeType = OfficeType.parse(it))) }
            ChipGroup(stringResource(R.string.filter_office), options.offices, filters.officeName) { onChange(filters.copy(officeName = it)) }
            ChipGroup(stringResource(R.string.filter_beat), options.beats, filters.beatNumber) { onChange(filters.copy(beatNumber = it)) }
            ChipGroup(stringResource(R.string.filter_pincode), options.pincodes, filters.pincode) { onChange(filters.copy(pincode = it)) }

            Spacer(Modifier.height(8.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_done)) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** "All" + one chip per option, wrapping onto as many lines as needed. Hidden when there is nothing to choose. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(
    title: String,
    options: List<String>,
    selected: String?,
    labelOf: (String) -> String = { it },
    onSelect: (String?) -> Unit,
) {
    if (options.isEmpty() || (options.size == 1 && selected == null)) return
    Spacer(Modifier.height(12.dp))
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(stringResource(R.string.chip_all)) })
        options.forEach { option ->
            val isSelected = option.equals(selected, ignoreCase = true)
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(if (isSelected) null else option) },
                label = { Text(labelOf(option)) },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.width(FilterChipDefaults.IconSize)) }
                } else null,
            )
        }
    }
}
