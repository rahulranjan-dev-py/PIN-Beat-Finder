package com.pinbeatfinder.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pinbeatfinder.R
import com.pinbeatfinder.appContainer
import com.pinbeatfinder.ui.local.LocalBeatsIntent
import com.pinbeatfinder.ui.local.LocalBeatsMenuAction
import com.pinbeatfinder.ui.local.LocalBeatsScreen
import com.pinbeatfinder.ui.local.LocalBeatsViewModel
import com.pinbeatfinder.ui.online.OnlineSearchScreen
import com.pinbeatfinder.ui.online.OnlineSearchViewModel

private enum class MainTab(val labelRes: Int) {
    ONLINE(R.string.tab_online),
    LOCAL(R.string.tab_local),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val container = LocalContext.current.appContainer
    val onlineViewModel: OnlineSearchViewModel = viewModel(
        factory = viewModelFactory { initializer { OnlineSearchViewModel(container.postalLookupRepository) } },
    )
    val localViewModel: LocalBeatsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LocalBeatsViewModel(container.beatDirectoryRepository, container.excelSyncManager) }
        },
    )

    var selectedTab by rememberSaveable { mutableIntStateOf(MainTab.LOCAL.ordinal) }
    val tab = MainTab.entries[selectedTab]
    val snackbarHostState = remember { SnackbarHostState() }
    var menuExpanded by remember { mutableStateOf(false) }
    // The menu lives in the top bar but its actions belong to the local tab; bridge via callback.
    var menuActionHandler by remember { mutableStateOf<(LocalBeatsMenuAction) -> Unit>({}) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                actions = {
                    if (tab == MainTab.LOCAL) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            LocalBeatsMenuAction.entries.forEachIndexed { index, action ->
                                if (index == 2 || index == 4) HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(action.label) },
                                    leadingIcon = { Icon(action.icon, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        menuActionHandler(action)
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (tab == MainTab.LOCAL) {
                ExtendedFloatingActionButton(
                    onClick = { localViewModel.onIntent(LocalBeatsIntent.OpenEditor()) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_beat)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                MainTab.entries.forEach { t ->
                    Tab(
                        selected = t == tab,
                        onClick = { selectedTab = t.ordinal },
                        text = { Text(stringResource(t.labelRes)) },
                        icon = {
                            Icon(
                                if (t == MainTab.ONLINE) Icons.Default.CloudQueue else Icons.Default.Storage,
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
            when (tab) {
                MainTab.ONLINE -> OnlineSearchScreen(viewModel = onlineViewModel)
                MainTab.LOCAL -> {
                    val activity = LocalActivity.current
                    LocalBeatsScreen(
                        viewModel = localViewModel,
                        snackbarHostState = snackbarHostState,
                        registerMenuHandler = { handler -> menuActionHandler = handler },
                        launchIntent = { intent -> activity?.startActivity(intent) },
                    )
                }
            }
        }
    }
}
