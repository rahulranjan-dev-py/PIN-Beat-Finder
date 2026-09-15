package com.pinbeatfinder.ui.online

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pinbeatfinder.R
import com.pinbeatfinder.core.util.AppError
import com.pinbeatfinder.domain.model.BeatDraft
import com.pinbeatfinder.domain.model.PostOffice
import com.pinbeatfinder.domain.model.toBeatDraft
import com.pinbeatfinder.ui.components.EmptyState
import com.pinbeatfinder.ui.components.FilterChipsRow
import com.pinbeatfinder.ui.components.LabeledValue
import kotlinx.coroutines.launch

/** Cross-tab actions the host screen fulfils (switching tabs, opening the local editor). */
class OnlineBridge(
    val showLocalBeatsFor: (pincode: String) -> Unit,
    val addToLocal: (BeatDraft) -> Unit,
)

@Composable
fun OnlineSearchScreen(
    viewModel: OnlineSearchViewModel,
    snackbarHostState: SnackbarHostState,
    bridge: OnlineBridge,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    OnlineSearchContent(state = state, onIntent = viewModel::onIntent, bridge = bridge)

    state.selectedOffice?.let { office ->
        PostOfficeDetailSheet(
            office = office,
            localCount = state.localCountFor(office),
            onDismiss = { viewModel.onIntent(OnlineSearchIntent.DismissDetail) },
            onCopyPin = {
                clipboard.setText(AnnotatedString(office.pincode))
                scope.launch { snackbarHostState.showSnackbar("PIN ${office.pincode} copied") }
            },
            onShare = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "${office.name} – PIN ${office.pincode}")
                    putExtra(Intent.EXTRA_TEXT, office.shareText())
                }
                context.startActivity(Intent.createChooser(send, "Share post office"))
            },
            onAddToLocal = {
                viewModel.onIntent(OnlineSearchIntent.DismissDetail)
                bridge.addToLocal(office.toBeatDraft())
            },
            onShowLocalBeats = {
                viewModel.onIntent(OnlineSearchIntent.DismissDetail)
                bridge.showLocalBeatsFor(office.pincode)
            },
        )
    }
}

@Composable
fun OnlineSearchContent(
    state: OnlineSearchState,
    onIntent: (OnlineSearchIntent) -> Unit,
    bridge: OnlineBridge,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val isNumeric = state.query.all(Char::isDigit) && state.query.isNotEmpty()

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { onIntent(OnlineSearchIntent.QueryChanged(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            placeholder = { Text(stringResource(R.string.search_online_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onIntent(OnlineSearchIntent.Clear) }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isNumeric) KeyboardType.Number else KeyboardType.Text,
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(onSearch = {
                keyboard?.hide()
                onIntent(OnlineSearchIntent.Submit)
            }),
            supportingText = { Text("Enter a 6-digit PIN or a post office name, then press Search.") },
        )

        if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())

        // Post-office names repeat across India ("Govindapur" exists in five states); let the
        // user narrow the fetched list without another network call.
        if (state.results.isNotEmpty()) {
            FilterChipsRow(
                label = "State",
                options = state.states,
                selected = state.stateFilter,
                onSelect = { onIntent(OnlineSearchIntent.StateFilterSelected(it)) },
                hideWhenSingle = true,
            )
            FilterChipsRow(
                label = "District",
                options = state.districts,
                selected = state.districtFilter,
                onSelect = { onIntent(OnlineSearchIntent.DistrictFilterSelected(it)) },
                modifier = Modifier.padding(top = 4.dp),
                hideWhenSingle = true,
            )
        }

        when {
            state.error != null -> EmptyState(
                icon = if (state.error is AppError.Offline) Icons.Default.CloudOff else Icons.Default.SearchOff,
                title = if (state.error is AppError.NotFound) "No results" else "Lookup failed",
                message = state.error.userMessage(),
                actionLabel = if (state.error is AppError.NotFound) null else "Retry",
                onAction = { onIntent(OnlineSearchIntent.Retry) },
            )
            !state.hasSearched && state.recents.isNotEmpty() -> RecentSearchesList(state = state, onIntent = onIntent)
            !state.hasSearched -> EmptyState(
                icon = Icons.Default.TravelExplore,
                title = "All-India post office lookup",
                message = "Search any PIN code or post office name across India. Results are cached for offline reuse, and your searches appear here for quick re-use.",
            )
            state.results.isEmpty() && !state.isLoading -> EmptyState(
                icon = Icons.Default.SearchOff,
                title = "No results",
                message = "Nothing matched \"${state.submittedQuery}\".",
            )
            state.visibleResults.isEmpty() -> EmptyState(
                icon = Icons.Default.SearchOff,
                title = "No results match the filters",
                message = "Clear the State/District filters to see all ${state.results.size} result(s).",
                actionLabel = "Clear filters",
                onAction = { onIntent(OnlineSearchIntent.ClearFilters) },
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    val shown = state.visibleResults.size
                    Text(
                        if (state.hasFilters) "$shown of ${state.results.size} post office(s) for \"${state.submittedQuery}\""
                        else "$shown post office(s) for \"${state.submittedQuery}\"",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.results.firstOrNull()?.source?.takeIf { it.isNotBlank() }?.let { source ->
                        Text(
                            "Source: $source • tap a card for details",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.visibleResults, key = { "${it.name}|${it.pincode}|${it.branchType}|${it.district}" }) { office ->
                    PostOfficeCard(
                        office = office,
                        localCount = state.localCountFor(office),
                        onClick = { onIntent(OnlineSearchIntent.SelectOffice(office)) },
                        onShowLocalBeats = { bridge.showLocalBeatsFor(office.pincode) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentSearchesList(state: OnlineSearchState, onIntent: (OnlineSearchIntent) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Recent searches",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (state.recents.any { !it.pinned }) {
                    TextButton(onClick = { onIntent(OnlineSearchIntent.ClearRecents) }) { Text("Clear") }
                }
            }
        }
        items(state.recents, key = { it.query.lowercase() }) { recent ->
            ListItem(
                modifier = Modifier.clickable { onIntent(OnlineSearchIntent.SearchRecent(recent.query)) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                leadingContent = {
                    Icon(
                        if (recent.pinned) Icons.Default.PushPin else Icons.Default.History,
                        contentDescription = null,
                        tint = if (recent.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                headlineContent = { Text(recent.query) },
                supportingContent = if (recent.useCount > 1) {
                    { Text("Searched ${recent.useCount} times", style = MaterialTheme.typography.bodySmall) }
                } else null,
                trailingContent = {
                    Row {
                        IconButton(onClick = { onIntent(OnlineSearchIntent.TogglePinRecent(recent.query)) }) {
                            Icon(
                                if (recent.pinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                contentDescription = if (recent.pinned) "Unpin" else "Pin",
                                tint = if (recent.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onIntent(OnlineSearchIntent.RemoveRecent(recent.query)) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove")
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun PostOfficeCard(
    office: PostOffice,
    localCount: Int,
    onClick: () -> Unit,
    onShowLocalBeats: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    office.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                AssistChip(onClick = onClick, label = { Text(office.pincode) })
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(office.branchType, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (office.deliveryStatus.isNotBlank()) {
                    Text("•", style = MaterialTheme.typography.bodySmall)
                    Text(office.deliveryStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            LabeledValue("District", office.district)
            LabeledValue("State", office.state)
            LabeledValue("Division", office.division)
            LabeledValue("Region", office.region)
            LabeledValue("Circle", office.circle)
            LabeledValue("Block", office.block)
            if (localCount > 0) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onShowLocalBeats),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "$localCount local beat record(s) for this PIN  ›",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

internal fun PostOffice.shareText(): String = buildString {
    appendLine(name)
    appendLine("PIN: $pincode")
    if (branchType.isNotBlank()) appendLine(branchType + if (deliveryStatus.isNotBlank()) " • $deliveryStatus" else "")
    if (district.isNotBlank()) appendLine("District: $district")
    if (state.isNotBlank()) appendLine("State: $state")
    if (division.isNotBlank()) appendLine("Division: $division")
    if (region.isNotBlank()) appendLine("Region: $region")
    if (circle.isNotBlank()) appendLine("Circle: $circle")
    append("— shared from PIN Beat Finder")
}
