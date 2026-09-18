package com.pinbeatfinder.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pinbeatfinder.R
import com.pinbeatfinder.data.update.DownloadState
import com.pinbeatfinder.data.update.ReleaseInfo

/**
 * "Update available" strip under the app bar. Walks through: offer → downloading (progress,
 * cancel) → verifying → ready (Install) or failed (Retry / open in browser). "Later" hides it
 * until the app next comes to the foreground; × hides it for that version.
 */
@Composable
fun UpdateBanner(
    release: ReleaseInfo,
    download: DownloadState,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onLater: () -> Unit,
    onDismiss: () -> Unit,
    onOpenBrowser: () -> Unit,
) {
    val fg = MaterialTheme.colorScheme.onTertiaryContainer
    val active = download.tagOrNull() == release.tag
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = fg)
            Spacer(Modifier.width(10.dp))
            Text(
                text = when {
                    active && download is DownloadState.Downloading -> stringResource(R.string.update_downloading, release.tag)
                    active && download is DownloadState.Verifying -> stringResource(R.string.update_verifying)
                    active && download is DownloadState.Ready -> stringResource(R.string.update_ready, release.tag)
                    active && download is DownloadState.Failed -> stringResource(R.string.update_failed, download.reason)
                    else -> stringResource(R.string.update_available, release.tag)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = fg,
                modifier = Modifier.weight(1f),
            )
            if (!(active && download is DownloadState.Downloading)) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.update_dismiss), tint = fg) }
            }
        }
        when {
            active && download is DownloadState.Downloading -> {
                val f = download.fraction
                if (f == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(end = 12.dp)) else LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth().padding(end = 12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        if (download.total > 0) stringResource(R.string.update_progress_of, download.bytes / 1_048_576f, download.total / 1_048_576f)
                        else stringResource(R.string.update_progress, download.bytes / 1_048_576f),
                        style = MaterialTheme.typography.labelSmall,
                        color = fg,
                        modifier = Modifier.weight(1f).padding(top = 6.dp),
                    )
                    TextButton(onClick = onCancelDownload) { Text(stringResource(R.string.action_cancel)) }
                }
            }
            active && download is DownloadState.Verifying -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(end = 12.dp, bottom = 8.dp))
            active && download is DownloadState.Ready -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!download.verified) {
                    Text(stringResource(R.string.update_unverified), style = MaterialTheme.typography.labelSmall, color = fg, modifier = Modifier.weight(1f).padding(top = 8.dp))
                }
                Button(onClick = onInstall) { Text(stringResource(R.string.update_install)) }
                Spacer(Modifier.width(8.dp))
            }
            active && download is DownloadState.Failed -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onOpenBrowser) { Text(stringResource(R.string.update_open_browser)) }
                TextButton(onClick = onDownload) { Text(stringResource(R.string.action_retry)) }
            }
            else -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onLater) { Text(stringResource(R.string.update_later)) }
                Button(onClick = onDownload) { Text(stringResource(R.string.update_download_install)) }
                Spacer(Modifier.width(8.dp))
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

private fun DownloadState.tagOrNull(): String? = when (this) {
    is DownloadState.Downloading -> tag
    is DownloadState.Verifying -> tag
    is DownloadState.Ready -> tag
    is DownloadState.Failed -> tag
    DownloadState.Idle -> null
}
