package com.pinbeatfinder.ui.online

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pinbeatfinder.R
import com.pinbeatfinder.core.util.AppError
import com.pinbeatfinder.domain.model.PostOffice
import com.pinbeatfinder.ui.components.EmptyState
import com.pinbeatfinder.ui.components.LabeledValue

@Composable
fun OnlineSearchScreen(viewModel: OnlineSearchViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OnlineSearchContent(state = state, onIntent = viewModel::onIntent)
}

@Composable
fun OnlineSearchContent(state: OnlineSearchState, onIntent: (OnlineSearchIntent) -> Unit) {
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

        when {
            state.error != null -> EmptyState(
                icon = if (state.error is AppError.Offline) Icons.Default.CloudOff else Icons.Default.SearchOff,
                title = if (state.error is AppError.NotFound) "No results" else "Lookup failed",
                message = state.error.userMessage(),
                actionLabel = if (state.error is AppError.NotFound) null else "Retry",
                onAction = { onIntent(OnlineSearchIntent.Retry) },
            )
            !state.hasSearched -> EmptyState(
                icon = Icons.Default.TravelExplore,
                title = "All-India post office lookup",
                message = "Search any PIN code or post office name across India. Results are cached for offline reuse.",
            )
            state.results.isEmpty() && !state.isLoading -> EmptyState(
                icon = Icons.Default.SearchOff,
                title = "No results",
                message = "Nothing matched \"${state.submittedQuery}\".",
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        "${state.results.size} post office(s) for \"${state.submittedQuery}\"",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(state.results, key = { "${it.name}|${it.pincode}|${it.branchType}" }) { office ->
                    PostOfficeCard(office)
                }
            }
        }
    }
}

@Composable
private fun PostOfficeCard(office: PostOffice) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                Box {
                    AssistChip(onClick = {}, label = { Text(office.pincode) })
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(office.branchType, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Text("•", style = MaterialTheme.typography.bodySmall)
                Text(office.deliveryStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            LabeledValue("District", office.district)
            LabeledValue("State", office.state)
            LabeledValue("Division", office.division)
            LabeledValue("Region", office.region)
            LabeledValue("Circle", office.circle)
            LabeledValue("Block", office.block)
        }
    }
}
