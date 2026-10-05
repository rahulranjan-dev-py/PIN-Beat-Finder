package com.pinbeatfinder.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pinbeatfinder.R
import com.pinbeatfinder.appContainer
import com.pinbeatfinder.data.prefs.DefaultTab
import com.pinbeatfinder.data.update.UpdateInstaller
import com.pinbeatfinder.ui.components.AddFab
import com.pinbeatfinder.ui.components.UpdateBanner
import com.pinbeatfinder.ui.local.LocalBeatsIntent
import com.pinbeatfinder.ui.local.LocalBeatsScreen
import com.pinbeatfinder.ui.local.LocalBeatsViewModel
import com.pinbeatfinder.ui.online.OnlineBridge
import com.pinbeatfinder.ui.online.OnlineSearchScreen
import com.pinbeatfinder.ui.online.OnlineSearchViewModel
import com.pinbeatfinder.ui.settings.SettingsScreen
import com.pinbeatfinder.ui.theme.LocalExtraColors

private enum class MainTab(val labelRes: Int) {
    ONLINE(R.string.tab_online),
    LOCAL(R.string.tab_local),
}

/**
 * Host of the two main screens. "Night Mail" has no app bar: each screen draws its own large
 * title, and the tabs live in a bottom navigation bar with a pill behind the active icon.
 */
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
    var showSettings by rememberSaveable { mutableStateOf(false) }

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

    val extra = LocalExtraColors.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = extra.bottomBar, contentColor = MaterialTheme.colorScheme.onSurface) {
                MainTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { selectedTab = t.ordinal },
                        icon = {
                            // A globe, not a cloud: the All-India tab works offline from the built-in directory.
                            Icon(if (t == MainTab.ONLINE) Icons.Default.Public else Icons.Default.Storage, contentDescription = null)
                        },
                        label = { Text(stringResource(t.labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == MainTab.LOCAL) AddFab(onClick = { localViewModel.onIntent(LocalBeatsIntent.OpenEditor()) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            updateState.available?.takeIf { updateState.shouldShowBanner }?.let { release ->
                val downloadState by container.apkDownloader.state.collectAsStateWithLifecycle()
                // A finished download survives the "allow installs" settings detour and a re-open.
                // The transfer itself lives in the downloader, not here, so leaving this
                // composition never drops it.
                LaunchedEffect(release.tag) {
                    container.apkDownloader.prune(keepTag = release.tag)
                    container.apkDownloader.restoreIfDownloaded(release.tag)
                }
                val install = {
                    container.apkDownloader.readyFile()?.let { file -> context.startActivity(UpdateInstaller.installIntent(context, file)) }
                    Unit
                }
                // Returning from the "allow from this source" page: continue straight to the installer.
                val allowInstalls = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    if (UpdateInstaller.canInstall(context)) install()
                }
                UpdateBanner(
                    release = release,
                    download = downloadState,
                    onDownload = { container.apkDownloader.start(release) },
                    onCancelDownload = { container.apkDownloader.cancel() },
                    onInstall = {
                        if (UpdateInstaller.canInstall(context)) install() else allowInstalls.launch(UpdateInstaller.permissionIntent(context))
                    },
                    onLater = { container.updateChecker.later(release.tag) },
                    onDismiss = { container.updateChecker.dismiss(release.tag); container.apkDownloader.reset() },
                    onOpenBrowser = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.apkUrl ?: release.pageUrl))) },
                )
            }
            when (tab) {
                MainTab.ONLINE -> OnlineSearchScreen(
                    viewModel = onlineViewModel,
                    snackbarHostState = snackbarHostState,
                    bridge = onlineBridge,
                    onOpenSettings = { showSettings = true },
                )
                MainTab.LOCAL -> LocalBeatsScreen(
                    viewModel = localViewModel,
                    snackbarHostState = snackbarHostState,
                    launchIntent = { intent -> activity?.startActivity(intent) },
                    onLookupOnline = lookupOnline,
                    onOpenSettings = { showSettings = true },
                )
            }
        }
    }
}
