package com.pinbeatfinder.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's modal bottom sheet. Material's sheet lives in its own window, and on some phones
 * that window reports no system-bar insets, so a tall sheet runs under the status bar with its
 * drag handle among the clock and battery icons. The insets are therefore read here, in the
 * activity window where they are always known, and applied by hand: the drag handle moves down
 * by the status-bar height as the sheet reaches the top of the screen, and [content] receives
 * the navigation-bar height to keep its last row above the gesture area.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.(bottomInset: Dp) -> Unit,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val density = LocalDensity.current
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        dragHandle = {
            // Offset of the sheet's top edge from the top of the screen; unknown until first laid out.
            val sheetTop = runCatching { sheetState.requireOffset() }.getOrNull()
            val handleTop = if (sheetTop == null) 0.dp else (statusBarTop - with(density) { sheetTop.toDp() }).coerceIn(0.dp, statusBarTop)
            Box(Modifier.padding(top = handleTop)) { BottomSheetDefaults.DragHandle() }
        },
    ) {
        Column(Modifier.fillMaxWidth()) { content(navigationBarBottom) }
    }
}
