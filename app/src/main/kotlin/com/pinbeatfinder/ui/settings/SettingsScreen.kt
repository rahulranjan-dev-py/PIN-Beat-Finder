package com.pinbeatfinder.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pinbeatfinder.BuildConfig
import com.pinbeatfinder.R
import com.pinbeatfinder.appContainer
import com.pinbeatfinder.data.prefs.DefaultTab
import com.pinbeatfinder.data.prefs.TextScale
import com.pinbeatfinder.data.prefs.ThemeMode
import com.pinbeatfinder.ui.theme.OnPostBoxRed
import com.pinbeatfinder.ui.theme.PostBoxRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppLanguage(val tag: String, val labelRes: Int) {
    SYSTEM("", R.string.lang_system),
    ENGLISH("en", R.string.lang_english),
    HINDI("hi", R.string.lang_hindi),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val repo = container.appSettingsRepository
    val settings by repo.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var apiKeyDraft by remember(settings.dataGovInApiKey) { mutableStateOf(settings.dataGovInApiKey) }
    val currentLanguage = remember {
        val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        AppLanguage.entries.firstOrNull { it.tag.isNotEmpty() && tag.startsWith(it.tag) } ?: AppLanguage.SYSTEM
    }
    var language by remember { mutableStateOf(currentLanguage) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PostBoxRed,
                    titleContentColor = OnPostBoxRed,
                    navigationIconContentColor = OnPostBoxRed,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingGroup(stringResource(R.string.settings_default_tab)) {
                Segmented(
                    options = DefaultTab.entries,
                    selected = settings.defaultTab,
                    label = { stringResource(if (it == DefaultTab.ONLINE) R.string.tab_online else R.string.tab_local) },
                    onSelect = { v -> repo.update { it.copy(defaultTab = v) } },
                )
            }

            SettingGroup(stringResource(R.string.settings_theme)) {
                Segmented(
                    options = ThemeMode.entries,
                    selected = settings.themeMode,
                    label = {
                        stringResource(
                            when (it) {
                                ThemeMode.SYSTEM -> R.string.theme_system
                                ThemeMode.LIGHT -> R.string.theme_light
                                ThemeMode.DARK -> R.string.theme_dark
                            },
                        )
                    },
                    onSelect = { v -> repo.update { it.copy(themeMode = v) } },
                )
            }

            SettingGroup(stringResource(R.string.settings_text_size)) {
                Segmented(
                    options = TextScale.entries,
                    selected = settings.textScale,
                    label = { stringResource(if (it == TextScale.NORMAL) R.string.text_normal else R.string.text_large) },
                    onSelect = { v -> repo.update { it.copy(textScale = v) } },
                )
            }

            SettingGroup(stringResource(R.string.settings_language)) {
                Segmented(
                    options = AppLanguage.entries,
                    selected = language,
                    label = { stringResource(it.labelRes) },
                    onSelect = { v ->
                        language = v
                        // AppCompat persists this and recreates the activity with the new locale.
                        AppCompatDelegate.setApplicationLocales(
                            if (v.tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(v.tag),
                        )
                    },
                )
            }

            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_haptics), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.settings_haptics_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = settings.hapticsEnabled, onCheckedChange = { v -> repo.update { it.copy(hapticsEnabled = v) } })
            }

            HorizontalDivider()

            SettingGroup(stringResource(R.string.settings_api_key), description = stringResource(R.string.settings_api_key_desc)) {
                OutlinedTextField(
                    value = apiKeyDraft,
                    onValueChange = { apiKeyDraft = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        repo.update { it.copy(dataGovInApiKey = apiKeyDraft) }
                        scope.launch { snackbar.showSnackbar(context.getString(R.string.settings_api_key_saved)) }
                    },
                    enabled = apiKeyDraft != settings.dataGovInApiKey,
                ) { Text(stringResource(R.string.action_save)) }
            }

            SettingGroup(stringResource(R.string.settings_clear_cache), description = stringResource(R.string.settings_clear_cache_desc)) {
                OutlinedButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { runCatching { container.clearOnlineCache() } }
                        snackbar.showSnackbar(context.getString(R.string.settings_cache_cleared))
                    }
                }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_clear_cache))
                }
            }

            HorizontalDivider()

            SettingGroup(stringResource(R.string.settings_about)) {
                Text(
                    stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingGroup(title: String, description: String? = null, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (description != null) {
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) { Text(label(option), maxLines = 1) }
        }
    }
}
