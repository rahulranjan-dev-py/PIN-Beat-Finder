package com.pinbeatfinder.ui.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pinbeatfinder.domain.model.PostOffice
import com.pinbeatfinder.ui.components.LabeledValue

/**
 * Full record for one online result plus the actions staff actually take with it:
 * copy the PIN, share it (WhatsApp is the real distribution channel), add it to the offline
 * directory, or jump to the local beats already recorded for that PIN.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text(office.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "PIN ${office.pincode}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                if (office.branchType.isNotBlank()) {
                    Text("•", style = MaterialTheme.typography.bodyMedium)
                    Text(office.branchType, style = MaterialTheme.typography.bodyMedium)
                }
                if (office.deliveryStatus.isNotBlank()) {
                    Text("•", style = MaterialTheme.typography.bodyMedium)
                    Text(office.deliveryStatus, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(16.dp))
            LabeledValue("District", office.district)
            LabeledValue("State", office.state)
            LabeledValue("Block", office.block)
            LabeledValue("Division", office.division)
            LabeledValue("Region", office.region)
            LabeledValue("Circle", office.circle)
            if (office.source.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("Source: ${office.source}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onCopyPin, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Copy PIN")
                }
                OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Share")
                }
            }
            Spacer(Modifier.height(10.dp))
            if (localCount > 0) {
                FilledTonalButton(onClick = onShowLocalBeats, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Storage, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Show $localCount local beat record(s) for this PIN")
                }
                Spacer(Modifier.height(10.dp))
            }
            Button(onClick = onAddToLocal, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add to local directory")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
