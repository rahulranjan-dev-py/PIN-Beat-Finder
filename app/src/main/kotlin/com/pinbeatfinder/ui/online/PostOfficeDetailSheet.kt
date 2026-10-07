package com.pinbeatfinder.ui.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pinbeatfinder.R
import com.pinbeatfinder.ui.components.PrimaryButton
import com.pinbeatfinder.domain.model.PostOffice
import com.pinbeatfinder.ui.components.AppBottomSheet
import com.pinbeatfinder.ui.components.LabeledValue
import com.pinbeatfinder.ui.components.branchTypeLabel
import com.pinbeatfinder.ui.components.deliveryLabel
import com.pinbeatfinder.ui.components.displayCase
import com.pinbeatfinder.ui.components.displayRegion

/**
 * Full record for one online result plus the actions staff actually take with it:
 * copy the PIN, share it (WhatsApp is the real distribution channel), add it to the offline
 * directory, or jump to the local beats already recorded for that PIN.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PostOfficeDetailSheet(
    office: PostOffice,
    localCount: Int,
    onDismiss: () -> Unit,
    onCopyPin: () -> Unit,
    onShare: () -> Unit,
    onAddToLocal: () -> Unit,
    onShowLocalBeats: () -> Unit,
) {
    AppBottomSheet(onDismissRequest = onDismiss) { bottomInset ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = bottomInset),
        ) {
            Text(office.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            // Whole items wrap to the next line at large font sizes; a word is never split.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.detail_pin, office.pincode),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold,
                )
                val context = LocalContext.current
                if (office.branchType.isNotBlank()) {
                    Text("• " + branchTypeLabel(context, office.branchType), style = MaterialTheme.typography.bodyMedium)
                }
                if (office.deliveryStatus.isNotBlank()) {
                    Text("• " + deliveryLabel(context, office.deliveryStatus), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(16.dp))
            LabeledValue(stringResource(R.string.label_district), office.district.displayCase())
            LabeledValue(stringResource(R.string.label_state), office.state.displayCase())
            LabeledValue(stringResource(R.string.label_block), office.block.displayCase())
            LabeledValue(stringResource(R.string.label_division), office.division.displayCase())
            LabeledValue(stringResource(R.string.label_region), displayRegion(office.region)?.displayCase().orEmpty())
            LabeledValue(stringResource(R.string.label_circle), office.circle.displayCase())
            if (office.source.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.detail_source, office.source) + if (office.fromCache) " • ${stringResource(R.string.online_cached)}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onCopyPin, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_copy_pin))
                }
                OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_share))
                }
            }
            Spacer(Modifier.height(10.dp))
            if (localCount > 0) {
                FilledTonalButton(onClick = onShowLocalBeats, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Storage, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(pluralStringResource(R.plurals.detail_show_local, localCount, localCount))
                }
                Spacer(Modifier.height(10.dp))
            }
            PrimaryButton(onClick = onAddToLocal, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.detail_add_local))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
