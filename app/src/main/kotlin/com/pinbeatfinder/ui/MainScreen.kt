package com.pinbeatfinder.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pinbeatfinder.R
import com.pinbeatfinder.appContainer
import com.pinbeatfinder.data.prefs.DefaultTab
import com.pinbeatfinder.ui.local.LocalBeatsIntent
import com.pinbeatfinder.ui.local.LocalBeatsMenuAction
import com.pinbeatfinder.ui.local.LocalBeatsScreen
import com.pinbeatfinder.ui.local.LocalBeatsViewModel
import com.pinbeatfinder.ui.online.OnlineBridge
import com.pinbeatfinder.ui.online.OnlineSearchScreen
import com.pinbeatfinder.ui.online.OnlineSearchViewModel
import com.pinbeatfinder.ui.settings.SettingsScreen
import com.pinbeatfinder.ui.theme.OnPostBoxRed
import com.pinbeatfinder.ui.theme.PostBoxRed

private enum class MainTab(val labelRes: Int) {
    ONLINE(R.string.tab_online),
    LOCAL(R.string.tab_local),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val container = LocalContext.current.appContainer
    val onlineViewModel: OnlineSearchViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                OnlineSearchViewModel(
                    container.postalLookupRepository,
                    container.recentSearchesRepository,
                    container.beatDirectoryRepository,
                    container.connectivity,
                    container.directorySeeder.state,
                )
            }
        },
    )
    val localViewModel: LocalBeatsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LocalBeatsViewModel(container.beatDirectoryRepository, container.excelSyncManager, container.indiaPostDirectory) }
        },
    )

    val initialTab = remember {
        if (container.appSettingsRepository.settings.value.defaultTab == DefaultTab.LOCAL) MainTab.LOCAL else MainTab.ONLINE
    }
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab.ordinal) }
    val tab = MainTab.entries[selectedTab]
    val snackbarHostState = remember { SnackbarHostState() }
    var menuExpanded by remember { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // The menu lives in the top bar but its actions belong to the local tab; bridge via callback.
    var menuActionHandler by remember { mutableStateOf<(LocalBeatsMenuAction) -> Unit>({}) }

    // Cross-tab bridge: each side asks the host to switch tabs and hand over a query/draft.
    val onlineBridge = remember(onlineViewModel, localViewModel) {
        OnlineBridge(
            showLocalBeatsFor = { pin ->
                localViewModel.onIntent(LocalBeatsIntent.ShowPincode(pin))
                selectedTab = MainTab.LOCAL.ordinal
            },
            addToLocal = { draft ->
                localViewModel.onIntent(LocalBeatsIntent.OpenEditorWithDraft(draft))
                selectedTab = MainTab.LOCAL.ordinal
            },
        )
    }
    val lookupOnline: (String) -> Unit = { query ->
        onlineViewModel.searchFor(query)
        selectedTab = MainTab.ONLINE.ordinal
    }

    val updateState by container.updateChecker.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (container.crashReporter.hasUnacknowledgedCrash()) {
            container.crashReporter.acknowledge()
            snackbarHostState.showSnackbar(context.getString(R.string.crash_snackbar), duration = SnackbarDuration.Long)
        }
    }

    if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScreen(onBack = { showSettings = false })
        return
    }

    // Back on the main screen asks before closing; a stray back press mid-entry should not lose the app.
    val activity = LocalActivity.current
    var confirmExit by remember { mutableStateOf(false) }
    BackHandler(enabled = !confirmExit) { confirmExit = true }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
            title = { Text(stringResource(R.string.exit_title)) },
            text = { Text(stringResource(R.string.exit_message)) },
            confirmButton = { TextButton(onClick = { confirmExit = false; activity?.finish() }) { Text(stringResource(R.string.action_exit)) } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PostBoxRed,
                    titleContentColor = OnPostBoxRed,
                    actionIconContentColor = OnPostBoxRed,
                ),
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                    if (tab == MainTab.LOCAL) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more_options))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            LocalBeatsMenuAction.entries.forEachIndexed { index, action ->
                                if (index == 2 || index == 4) HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(action.labelRes)) },
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
            updateState.available?.takeIf { updateState.shouldShowBanner }?.let { release ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.update_available, release.tag),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.apkUrl ?: release.pageUrl)))
                    }) { Text(stringResource(R.string.update_download)) }
                    IconButton(onClick = { container.updateChecker.dismiss(release.tag) }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.update_dismiss))
                    }
                }
            }
            TabRow(selectedTabIndex = selectedTab) {
                MainTab.entries.forEach { t ->
                    // Icon inline with the label: a 48dp tab instead of the 72dp icon-over-text one.
                    Tab(
                        selected = t == tab,
                        onClick = { selectedTab = t.ordinal },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (t == MainTab.ONLINE) Icons.Default.CloudQueue else Icons.Default.Storage,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(t.labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        },
                    )
                }
            }
            when (tab) {
                MainTab.ONLINE -> OnlineSearchScreen(
                    viewModel = onlineViewModel,
                    snackbarHostState = snackbarHostState,
                    bridge = onlineBridge,
                )
                MainTab.LOCAL -> {
                    val activity = LocalActivity.current
                    LocalBeatsScreen(
                        viewModel = localViewModel,
                        snackbarHostState = snackbarHostState,
                        registerMenuHandler = { handler -> menuActionHandler = handler },
                        launchIntent = { intent -> activity?.startActivity(intent) },
                        onLookupOnline = lookupOnline,
                    )
                }
            }
        }
    }
}
