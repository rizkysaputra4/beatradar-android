package com.boimin.beatradar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * A bottom sheet state that still closes with a drag down, but not by accident. Material's defaults close the sheet
 * after a 56dp drag or any flick faster than 125dp/s. Here the drag has to cover a third of the sheet's height or, on
 * the handle and other non-scrolling parts, end in a real flick ([FLICK_TO_CLOSE]); a shorter or slower one springs back.
 */
@OptIn(ExperimentalMaterial3Api::class)
class CalmSheetState internal constructor(val sheetState: SheetState, internal val heights: SheetHeights)

/** The sheet's measured parts, filled in by [CalmBottomSheet]; their sum is the sheet's height. */
internal class SheetHeights {
    val handle = mutableIntStateOf(0)
    val content = mutableIntStateOf(0)
    val total: Int get() = handle.intValue + content.intValue
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberCalmSheetState(skipPartiallyExpanded: Boolean = false): CalmSheetState {
    val density = LocalDensity.current
    val heights = remember { SheetHeights() }
    // Read at release: a third of the sheet as it is now. Before it has been measured, the old fixed distance.
    val positional = { heights.total.takeIf { it > 0 }?.let { it * DRAG_TO_CLOSE_FRACTION } ?: with(density) { DRAG_TO_CLOSE_FALLBACK.toPx() } }
    val velocity = { with(density) { FLICK_TO_CLOSE.toPx() } }
    val confirm: (SheetValue) -> Boolean = { true }
    val state = rememberSaveable(
        skipPartiallyExpanded,
        saver = SheetState.Saver(skipPartiallyExpanded, positional, velocity, confirm, skipHiddenState = false),
    ) {
        SheetState(skipPartiallyExpanded, positional, velocity, SheetValue.Hidden, confirm)
    }
    return remember(state) { CalmSheetState(state, heights) }
}

/**
 * [ModalBottomSheet] that doesn't close from scrolling its own content. Material hands the sheet whatever a list's
 * scroll or fling has left over, so a quick scroll back to the top of a list shut the sheet. Here:
 * - a swipe that scrolled the content doesn't also pull the sheet down once the content reaches the top;
 * - a fling left over from the content is dropped: the sheet closes only when dragged a third of its height, and an
 *   upward fling at the bottom of a list no longer makes it jump.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalmBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: CalmSheetState = rememberCalmSheetState(),
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit,
) {
    val calm = remember { CalmContentScroll() }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState.sheetState,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        dragHandle = dragHandle?.let { handle ->
            { Box(Modifier.onSizeChanged { sheetState.heights.handle.intValue = it.height }) { handle() } }
        },
    ) {
        Column(
            Modifier
                .nestedScroll(calm)
                .onSizeChanged { sheetState.heights.content.intValue = it.height },
            content = content,
        )
    }
}

/** Sits between the sheet's content and the sheet, filtering what the content's scrolling passes on to the sheet. */
private class CalmContentScroll : NestedScrollConnection {
    private var contentScrolled = false

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        if (consumed.y != 0f) contentScrolled = true
        // The content scrolled during this swipe: keep the rest of the swipe from moving the sheet down.
        return if (contentScrolled && available.y > 0f) Offset(0f, available.y) else Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        contentScrolled = false
        // Downward: the sheet settles by how far it was dragged, not how fast. Upward too: a fling left over at the
        // bottom of a list made the expanded sheet spring up from its own position and back, a visible jump.
        // (Pulling a half-open sheet up still works: the sheet handles that in onPreFling, before the content.)
        return Velocity(0f, available.y)
    }
}

private const val DRAG_TO_CLOSE_FRACTION = 1f / 3f
private val DRAG_TO_CLOSE_FALLBACK = 200.dp
private val FLICK_TO_CLOSE = 2500.dp // per second
