package com.pinbeatfinder.ui.online

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pinbeatfinder.R
import com.pinbeatfinder.core.util.AppError
import com.pinbeatfinder.data.directory.SeedState
import com.pinbeatfinder.domain.model.BeatDraft
import com.pinbeatfinder.domain.model.PostOffice
import com.pinbeatfinder.domain.model.toBeatDraft
import com.pinbeatfinder.ui.components.EmptyState
import com.pinbeatfinder.ui.components.CollapsingHeader
import com.pinbeatfinder.ui.components.FilterChipsRow
import com.pinbeatfinder.ui.components.LabeledValue
import com.pinbeatfinder.ui.theme.rememberHaptic
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
    val haptic = rememberHaptic()

    OnlineSearchContent(state = state, onIntent = viewModel::onIntent, bridge = bridge)

    state.selectedOffice?.let { office ->
        PostOfficeDetailSheet(
            office = office,
            localCount = state.localCountFor(office),
            onDismiss = { viewModel.onIntent(OnlineSearchIntent.DismissDetail) },
            onCopyPin = {
                clipboard.setText(AnnotatedString(office.pincode))
                haptic(HapticFeedbackType.Confirm)
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.snackbar_pin_copied, office.pincode)) }
            },
            onShare = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_subject, office.name, office.pincode))
                    putExtra(Intent.EXTRA_TEXT, office.shareText(context))
                }
                context.startActivity(Intent.createChooser(send, context.getString(R.string.share_office_title)))
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
    CollapsingHeader(
        modifier = Modifier.fillMaxSize(),
        revealKey = listOf(state.submittedQuery, state.stateFilter, state.districtFilter, state.results.size, state.error != null),
        header = { OnlineHeader(state, onIntent) },
    ) {
        Column(Modifier.fillMaxSize()) {
            if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                state.error != null -> EmptyState(
                    icon = if (state.error is AppError.Offline) Icons.Default.CloudOff else Icons.Default.SearchOff,
                    title = stringResource(if (state.error is AppError.NotFound) R.string.online_no_results_title else R.string.online_lookup_failed),
                    message = state.error.userMessage().asString(),
                    actionLabel = if (state.error is AppError.NotFound) null else stringResource(R.string.action_retry),
                    onAction = { onIntent(OnlineSearchIntent.Retry) },
                )
                !state.hasSearched && state.recents.isNotEmpty() -> RecentSearchesList(state = state, onIntent = onIntent)
                !state.hasSearched -> EmptyState(
                    icon = Icons.Default.TravelExplore,
                    title = stringResource(R.string.online_intro_title),
                    message = stringResource(R.string.online_intro_message),
                )
                state.results.isEmpty() && !state.isLoading -> EmptyState(
                    icon = Icons.Default.SearchOff,
                    title = stringResource(R.string.online_no_results_title),
                    message = stringResource(R.string.online_no_results_message, state.submittedQuery),
                )
                state.visibleResults.isEmpty() -> EmptyState(
                    icon = Icons.Default.SearchOff,
                    title = stringResource(R.string.online_filtered_out_title),
                    message = stringResource(R.string.online_filtered_out_message, state.results.size),
                    actionLabel = stringResource(R.string.action_clear_filters),
                    onAction = { onIntent(OnlineSearchIntent.ClearFilters) },
                )
                else -> ResultsList(state, onIntent, bridge)
            }
        }
    }
}

/** Directory status, offline banner, search box and result filters; slides away as results scroll. */
@Composable
private fun OnlineHeader(state: OnlineSearchState, onIntent: (OnlineSearchIntent) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val isNumeric = state.query.all(Char::isDigit) && state.query.isNotEmpty()

    Column(Modifier.fillMaxWidth()) {
        when (val seed = state.seedState) {
            is SeedState.Seeding -> Column(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.directory_preparing, (seed.progress * 100).toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                LinearProgressIndicator(progress = { seed.progress }, modifier = Modifier.fillMaxWidth())
            }
            is SeedState.Failed -> Text(
                stringResource(R.string.directory_failed, seed.message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            else -> Unit
        }
        if (state.isOffline) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.online_offline_banner),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = { onIntent(OnlineSearchIntent.QueryChanged(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
            placeholder = { Text(stringResource(R.string.search_online_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onIntent(OnlineSearchIntent.Clear) }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_clear))
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
        )
        Text(
            stringResource(R.string.online_supporting),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )

        // Post-office names repeat across India ("Govindapur" exists in five states); let the
        // user narrow the fetched list without another network call.
        if (state.results.isNotEmpty()) {
            FilterChipsRow(
                label = stringResource(R.string.filter_state),
                options = state.states,
                selected = state.stateFilter,
                onSelect = { onIntent(OnlineSearchIntent.StateFilterSelected(it)) },
                hideWhenSingle = true,
            )
            FilterChipsRow(
                label = stringResource(R.string.filter_district),
                options = state.districts,
                selected = state.districtFilter,
                onSelect = { onIntent(OnlineSearchIntent.DistrictFilterSelected(it)) },
                modifier = Modifier.padding(top = 4.dp),
                hideWhenSingle = true,
            )
        }
        Spacer(Modifier.height(2.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultsList(state: OnlineSearchState, onIntent: (OnlineSearchIntent) -> Unit, bridge: OnlineBridge) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            val shown = state.visibleResults.size
            Text(
                if (state.hasFilters) stringResource(R.string.online_count_filtered, shown, state.results.size, state.submittedQuery)
                else stringResource(R.string.online_count, shown, state.submittedQuery),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.results.firstOrNull()?.source?.takeIf { it.isNotBlank() }?.let { source ->
                Text(
                    stringResource(R.string.online_source, source),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.isGrouped && state.collapsedStates.isNotEmpty()) {
                TextButton(onClick = { onIntent(OnlineSearchIntent.ExpandAllGroups) }, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.online_expand_all))
                }
            }
        }
        if (state.isGrouped) {
            // A common name spans several states: sticky state headers turn the list into a
            // table of contents, and groups stay collapsed until the user opens one.
            state.groupedResults.forEach { (stateName, offices) ->
                val collapsed = stateName in state.collapsedStates
                stickyHeader(key = "hdr|$stateName") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { onIntent(OnlineSearchIntent.ToggleStateGroup(stateName)) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.online_group_header, stateName, offices.size),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                            contentDescription = stringResource(if (collapsed) R.string.action_expand else R.string.action_collapse),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (!collapsed) {
                    itemsIndexed(offices, key = { i, it -> officeKey(it, i) }) { _, office ->
                        PostOfficeCard(
                            office = office,
                            localCount = state.localCountFor(office),
                            onClick = { onIntent(OnlineSearchIntent.SelectOffice(office)) },
                            onShowLocalBeats = { bridge.showLocalBeatsFor(office.pincode) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        } else {
            itemsIndexed(state.visibleResults, key = { i, it -> officeKey(it, i) }) { _, office ->
                PostOfficeCard(
                    office = office,
                    localCount = state.localCountFor(office),
                    onClick = { onIntent(OnlineSearchIntent.SelectOffice(office)) },
                    onShowLocalBeats = { bridge.showLocalBeatsFor(office.pincode) },
                    modifier = Modifier.animateItem(),
                )
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
                    stringResource(R.string.recents_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (state.recents.any { !it.pinned }) {
                    TextButton(onClick = { onIntent(OnlineSearchIntent.ClearRecents) }) { Text(stringResource(R.string.action_clear)) }
                }
            }
        }
        items(state.recents, key = { it.query.lowercase() }) { recent ->
            ListItem(
                modifier = Modifier
                    .animateItem()
                    .clickable { onIntent(OnlineSearchIntent.SearchRecent(recent.query)) },
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
                    { Text(stringResource(R.string.recents_searched_times, recent.useCount), style = MaterialTheme.typography.bodySmall) }
                } else null,
                trailingContent = {
                    Row {
                        IconButton(onClick = { onIntent(OnlineSearchIntent.TogglePinRecent(recent.query)) }) {
                            Icon(
                                if (recent.pinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                contentDescription = stringResource(if (recent.pinned) R.string.action_unpin else R.string.action_pin),
                                tint = if (recent.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onIntent(OnlineSearchIntent.RemoveRecent(recent.query)) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_remove))
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
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(office.branchType, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (office.deliveryStatus.isNotBlank()) {
                    Text("•", style = MaterialTheme.typography.bodySmall)
                    Text(office.deliveryStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (office.fromCache) {
                    SuggestionChip(
                        onClick = onClick,
                        label = { Text(stringResource(R.string.online_cached), style = MaterialTheme.typography.labelSmall) },
                        icon = { Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.width(14.dp)) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LabeledValue(stringResource(R.string.label_district), office.district)
            LabeledValue(stringResource(R.string.label_state), office.state)
            LabeledValue(stringResource(R.string.label_division), office.division)
            LabeledValue(stringResource(R.string.label_region), office.region)
            LabeledValue(stringResource(R.string.label_circle), office.circle)
            LabeledValue(stringResource(R.string.label_block), office.block)
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
                        stringResource(R.string.online_local_count, localCount),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

internal fun PostOffice.shareText(context: Context): String = buildString {
    appendLine(name)
    appendLine(context.getString(R.string.detail_pin, pincode))
    if (branchType.isNotBlank()) appendLine(branchType + if (deliveryStatus.isNotBlank()) " • $deliveryStatus" else "")
    if (district.isNotBlank()) appendLine("${context.getString(R.string.label_district)}: $district")
    if (state.isNotBlank()) appendLine("${context.getString(R.string.label_state)}: $state")
    if (division.isNotBlank()) appendLine("${context.getString(R.string.label_division)}: $division")
    if (region.isNotBlank()) appendLine("${context.getString(R.string.label_region)}: $region")
    if (circle.isNotBlank()) appendLine("${context.getString(R.string.label_circle)}: $circle")
    append(context.getString(R.string.share_footer))
}

/**
 * Lazy-list key for a result row. The repository already drops exact repeats, but a key must
 * never collide (Compose throws), so the row index is folded in as the final tie-break.
 */
private fun officeKey(office: PostOffice, index: Int): String =
    "${office.name}|${office.pincode}|${office.branchType}|${office.district}|$index"
