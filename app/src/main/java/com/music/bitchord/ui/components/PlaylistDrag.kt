package com.music.bitchord.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.music.bitchord.R
import kotlin.math.roundToInt

/**
 * Drag-to-reorder for the rows of a playlist.
 *
 * Nothing is written while the finger moves. The held row follows it, and on
 * release the row it was carried over is rounded to a whole row and sent as
 * **one** move. A playlist is persisted on every change; writing the dozen
 * orders a drag passes through would be a dozen disk writes, and a gesture
 * cancelled half way would leave the playlist in an order nobody chose.
 *
 * The target is read from the live layout rather than from a fixed row height:
 * a playlist whose titles wrap onto two lines does not have equal rows, and
 * dividing the finger's travel by one row's height would then drop the song on
 * the wrong neighbour.
 *
 * Near either end the list scrolls under the held row at [edgeSpeedPx], so a
 * song can be carried beyond the screen. The scrolled distance is added to the
 * row's own offset, which keeps it under the finger while its slot moves.
 */
internal class PlaylistDrag(
    private val listState: LazyListState,
    private val edgeZonePx: Float,
    private val edgeSpeedPx: Float,
) {
    /** The row under the finger, or null at rest. */
    var heldKey by mutableStateOf<String?>(null)
        private set

    /** How far the held row has been carried, in pixels. */
    var offset by mutableFloatStateOf(0f)
        private set

    /** Pixels per second the list should scroll now, signed; zero when still. */
    var autoScrollVelocity by mutableFloatStateOf(0f)
        private set

    private var fromIndex = -1
    private var lastIndex = 0
    /** Layout rows before the first song row (headers), so a layout index maps to a row index. */
    private var headerRows = 0
    private var rowHeight = 1f

    /** Called once, on release, with the move the user made. */
    var onDrop: (Int, Int) -> Unit = { _, _ -> }

    val isDragging: Boolean get() = heldKey != null

    fun start(key: String, index: Int, total: Int) {
        if (total <= 1 || index !in 0 until total) return
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        rowHeight = item.size.toFloat().takeIf { it > 0f } ?: 1f
        fromIndex = index
        lastIndex = total - 1
        headerRows = (item.index - index).coerceAtLeast(0)
        offset = 0f
        heldKey = key
        updateAutoScroll(item.offset.toFloat(), item.size)
    }

    fun drag(deltaY: Float) {
        if (heldKey == null) return
        offset += deltaY
        heldItem()?.let { updateAutoScroll(it.offset.toFloat(), it.size) }
    }

    /** The list moved under the finger; keep the row under it and re-check the edges. */
    fun onScrolled(scrolled: Float) {
        if (heldKey == null || scrolled == 0f) return
        offset += scrolled
        heldItem()?.let { updateAutoScroll(it.offset.toFloat(), it.size) }
    }

    /** The row was released wherever it is. */
    fun end() {
        val key = heldKey ?: return
        val to = targetIndex()
        val from = fromIndex
        val valid = from in 0..lastIndex
        reset()
        if (key.isNotEmpty() && valid && to != from) onDrop(from, to)
    }

    /** The gesture was cancelled, or the list changed under the finger. */
    fun cancel() = reset()

    private fun reset() {
        heldKey = null
        offset = 0f
        autoScrollVelocity = 0f
        fromIndex = -1
        lastIndex = 0
        headerRows = 0
        rowHeight = 1f
    }

    private fun heldItem() =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == heldKey }

    /**
     * The row whose slot now contains the held row's centre, as a playlist row
     * index. Outside the viewport the nearest visible row wins; the autoscroll
     * keeps moving the list until the wanted row comes into view.
     */
    private fun targetIndex(): Int {
        val items = listState.layoutInfo.visibleItemsInfo
        val dragged = items.firstOrNull { it.key == heldKey } ?: return fallbackTarget()
        val centre = dragged.offset + offset + dragged.size / 2f
        val hit = items.firstOrNull { item ->
            item.key != heldKey && centre >= item.offset && centre <= item.offset + item.size
        }
        val absolute = hit?.index ?: if (centre < (items.firstOrNull()?.offset ?: 0)) {
            items.firstOrNull()?.index ?: 0
        } else {
            items.lastOrNull()?.index ?: lastIndex
        }
        return (absolute - headerRows).coerceIn(0, lastIndex)
    }

    private fun fallbackTarget(): Int =
        (fromIndex + (offset / rowHeight).roundToInt()).coerceIn(0, lastIndex)

    private fun updateAutoScroll(itemOffset: Float, itemSize: Int) {
        val layout = listState.layoutInfo
        val top = itemOffset + offset
        autoScrollVelocity = playlistAutoScrollVelocity(
            top = top,
            bottom = top + itemSize,
            viewportStart = layout.viewportStartOffset,
            viewportEnd = layout.viewportEndOffset,
            canScrollBackward = listState.canScrollBackward,
            canScrollForward = listState.canScrollForward,
            edgeZone = edgeZonePx,
            edgeSpeed = edgeSpeedPx,
        )
    }
}

/**
 * How fast the list should scroll while a row is held at [top]…[bottom].
 *
 * Signed: negative towards the start of the list. Zero in the middle, and zero
 * at an edge the list cannot move past, so a playlist's first row does not
 * drive the list into nothing.
 */
internal fun playlistAutoScrollVelocity(
    top: Float,
    bottom: Float,
    viewportStart: Int,
    viewportEnd: Int,
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
    edgeZone: Float,
    edgeSpeed: Float,
): Float = when {
    top < viewportStart + edgeZone && canScrollBackward -> -edgeSpeed
    bottom > viewportEnd - edgeZone && canScrollForward -> edgeSpeed
    else -> 0f
}

/**
 * Drives [PlaylistDrag]'s autoscroll while a row is held.
 *
 * One frame loop per drag rather than per recomposition; it ends when the row
 * is released because [PlaylistDrag.heldKey] is the effect's key.
 */
@Composable
internal fun PlaylistDragAutoScroll(drag: PlaylistDrag, listState: LazyListState) {
    val held = drag.heldKey
    LaunchedEffect(held, listState) {
        if (held == null) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val seconds = ((now - previous) / 1_000_000_000f).coerceIn(0f, 0.05f)
            previous = now
            val velocity = drag.autoScrollVelocity
            if (velocity != 0f) {
                val scrolled = listState.scrollBy(velocity * seconds)
                if (scrolled != 0f) drag.onScrolled(scrolled)
            }
        }
    }
}

/** The row's own displacement while it is carried, and the order it must draw in. */
internal fun Modifier.playlistDragLift(drag: PlaylistDrag, key: String): Modifier =
    this
        .graphicsLayer {
            translationY = if (drag.heldKey == key) drag.offset else 0f
            alpha = if (drag.heldKey == key) 0.96f else 1f
        }
        .zIndex(if (drag.heldKey == key) 1f else 0f)

/**
 * The handle a row is picked up by.
 *
 * A handle rather than the whole row: the row is where taps play and the menu
 * lives, and a drag that started anywhere on it would make both unreliable.
 * A long press on the handle also starts the drag, which is what lists on the
 * phone teach people to try first.
 */
@Composable
internal fun Modifier.playlistDragHandle(
    drag: PlaylistDrag,
    key: String,
    index: Int,
    total: Int,
): Modifier {
    val haptics = LocalHapticFeedback.current
    return this.pointerInput(drag, key, index, total) {
        detectDragGestures(
            onDragStart = {
                drag.start(key, index, total)
                if (drag.heldKey != null) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            },
            onDragEnd = { drag.end() },
            onDragCancel = { drag.cancel() },
            onDrag = { change, amount ->
                change.consume()
                drag.drag(amount.y)
            },
        )
    }
}

/**
 * A playlist row's menu.
 *
 * It used to carry Up and Down beside Remove. Those two are gone: the handle
 * beside the row reorders it directly, and two menu entries that each move one
 * position are a slower way to do the same thing. Remove is the only action
 * that is not already on the row itself.
 */
@Composable
internal fun PlaylistRowActions(onRemove: () -> Unit) {
    // `open` is a Kotlin modifier, and a local of that name in a lambda reads
    // as one to at least one parser. `menuOpen` costs a syllable and says more.
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.more),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.remove_from_playlist)) },
                onClick = {
                    menuOpen = false
                    onRemove()
                },
            )
        }
    }
}
