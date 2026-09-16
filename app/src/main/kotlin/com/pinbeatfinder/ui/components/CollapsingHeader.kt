package com.pinbeatfinder.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import kotlin.math.roundToInt

/**
 * Header that slides out of view as the content scrolls up and slides back in as soon as the
 * content scrolls down ("enter always"), so a tall search/filter block no longer pins the list
 * to the bottom third of small screens. The content is given the space the header vacates.
 *
 * Works with any scrollable content (LazyColumn, verticalScroll) because it hooks the
 * nested-scroll chain; non-scrollable content simply leaves the header in place.
 */
@Composable
fun CollapsingHeader(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    // 0 = fully shown … -headerHeight = fully hidden. Kept in px; header height is read at measure time.
    var offset by remember { mutableFloatStateOf(0f) }
    var headerHeight by remember { mutableFloatStateOf(0f) }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy == 0f || headerHeight <= 0f) return Offset.Zero
                val target = (offset + dy).coerceIn(-headerHeight, 0f)
                val consumed = target - offset
                offset = target
                return Offset(0f, consumed)
            }
        }
    }

    Layout(
        modifier = modifier
            .nestedScroll(connection)
            .clipToBounds(),
        content = {
            Box { header() }
            Box { content() }
        },
    ) { measurables, constraints ->
        val headerPlaceable = measurables[0].measure(constraints.copy(minHeight = 0, maxHeight = Int.MAX_VALUE))
        headerHeight = headerPlaceable.height.toFloat()
        val shown = (headerPlaceable.height + offset.roundToInt()).coerceIn(0, headerPlaceable.height)
        val contentHeight = (constraints.maxHeight - shown).coerceAtLeast(0)
        val contentPlaceable = measurables[1].measure(constraints.copy(minHeight = contentHeight, maxHeight = contentHeight))
        layout(constraints.maxWidth, constraints.maxHeight) {
            headerPlaceable.placeRelative(0, shown - headerPlaceable.height)
            contentPlaceable.placeRelative(0, shown)
        }
    }
}
