package com.pinbeatfinder.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import com.pinbeatfinder.R
import com.pinbeatfinder.ui.theme.rememberHaptic
import androidx.compose.ui.unit.dp

/**
 * Horizontally scrolling single-select chip row ("All" + one chip per option). Renders nothing
 * when there is only one option or none, since a filter with one choice is noise.
 */
@Composable
fun FilterChipsRow(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    hideWhenSingle: Boolean = false,
) {
    if (options.isEmpty() || (hideWhenSingle && options.size == 1 && selected == null)) return
    val haptic = rememberHaptic()
    val select: (String?) -> Unit = { value ->
        haptic(HapticFeedbackType.SegmentTick)
        onSelect(value)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilterChip(
            selected = selected == null,
            onClick = { select(null) },
            label = { Text(stringResource(R.string.chip_all)) },
        )
        options.forEach { option ->
            val isSelected = option == selected
            FilterChip(
                selected = isSelected,
                onClick = { select(if (isSelected) null else option) },
                label = { Text(option) },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.width(FilterChipDefaults.IconSize)) }
                } else null,
            )
        }
    }
}
