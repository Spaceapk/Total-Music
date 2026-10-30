package com.music.bitchord.ui.player

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs

/**
 * Drag-to-reorder for a list that is a queue.
 *
 * This is the rebirth player's own engine, and it is a second implementation
 * of the same idea as the original player's private `QueueDrag` on purpose:
 * that one is welded to its tiered rows, its section limits and its
 * `Color.White` foreground, and lifting it out would have changed the original
 * player to serve the new one. What is shared is the part worth sharing — the
 * edge-scroll ramp in [edgeScrollSpeed] — so the two lists move at the same
 * pace near the edges.
 *
 * The model is the original's, because it is the one that holds up under a
 * live queue:
 *
 *  * the finger holds the row's **centre**, and only the finger moves it;
 *  * swaps are read off the live layout, and each is sent to the player the
 *    moment the held row has travelled more than half of a neighbour;
 *  * the row the user sees is drawn by the caller over the list, from
 *    [heldTop] — never from its own slot, which would jump a whole row the
 *    frame a swap lands.
 *
 * One deliberate difference: [start] is given the queue's own identity for the
 * row ([RebirthQueueDrag.key]) rather than a section, because the rebirth
 * queue is one flat list. Header rows are simply not keys the engine knows,
 * so they can never be traded with.
 */
internal class RebirthQueueDrag(private val listState: LazyListState) {

    /**
     * The queue as the caller sees it: stable key to timeline index.
     *
     * Rebuilt on every queue change, which is what a move is: the engine reads
     * the newest mapping when it decides the next swap, so a swap it has
     * already sent is never re-sent off a stale one.
     */
    var rows: List<RebirthQueueRow> = emptyList()

    var onMove: (Int, Int) -> Unit = { _, _ -> }

    /** [REBIRTH_QUEUE_EDGE_ZONE] and [REBIRTH_QUEUE_EDGE_SPEED], in pixels. */
    private var edgeZone = 0f
    private var edgeSpeed = 0f

    /** Density-dependent, so set from the caller's composition rather than here. */
    fun configure(edgeZonePx: Float, edgeSpeedPx: Float) {
        edgeZone = edgeZonePx
        edgeSpeed = edgeSpeedPx
    }

    /** The key of the row under the finger, or null at rest. */
    var heldKey by mutableStateOf<String?>(null)
        private set

    /** Where the held row is drawn: its top in the viewport, clamped on screen. */
    var heldTop by mutableFloatStateOf(0f)
        private set

    /**
     * The index the last sent move should leave the row at, until the list
     * shows it there. Deciding another swap off the pre-move layout would send
     * the same move twice.
     */
    private var awaitingIndex: Int? = null

    private var heldCenter = 0f
    private var heldSize = 0

    /** Signed, in pixels a second; read by the caller's scroll loop. */
    var autoScroll by mutableFloatStateOf(0f)
        private set

    fun start(key: String) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        heldKey = key
        heldSize = item.size
        heldCenter = item.offset + item.size / 2f
        heldTop = item.offset.toFloat()
        awaitingIndex = null
        autoScroll = 0f
    }

    fun drag(deltaY: Float) = settle(deltaY)

    /** Called by the caller's scroll loop so the finger can hold still. */
    fun scrolled() = settle(0f)

    fun end(key: String) {
        if (heldKey != key) return
        end()
    }

    /**
     * Drops whatever is held, without asking which row it was.
     *
     * The caller uses this when the queue changes underneath a drag — a track
     * finished, the host reordered — because the row being carried may no
     * longer exist, and carrying a row that is not in the list has nothing to
     * settle against.
     */
    fun end() {
        heldKey = null
        awaitingIndex = null
        autoScroll = 0f
    }

    /** Whether [keys] still contains the held row. */
    fun holdsAnyOf(keys: Collection<String>): Boolean = heldKey?.let { it in keys } ?: true

    private fun settle(deltaY: Float) {
        val key = heldKey ?: return
        val info = listState.layoutInfo
        val items = info.visibleItemsInfo
        val half = heldSize / 2f
        val order = rows

        // Kept inside the queue's own ends where those are on screen, rather
        // than running on past them into rows that cannot be traded with.
        var center = heldCenter + deltaY
        order.firstOrNull()?.let { first ->
            items.firstOrNull { it.key == first.key }?.let { center = center.coerceAtLeast(it.offset + half) }
        }
        order.lastOrNull()?.let { last ->
            items.firstOrNull { it.key == last.key }?.let { center = center.coerceAtMost(it.offset + it.size - half) }
        }
        heldCenter = center
        val top = center - half
        val viewportStart = info.viewportStartOffset.toFloat()
        heldTop = top.coerceIn(viewportStart, (info.viewportEndOffset - heldSize).toFloat().coerceAtLeast(viewportStart))

        // Its slot scrolled out of view: nothing to trade against until the
        // list brings it back.
        val dragged = items.firstOrNull { it.key == key } ?: run { autoScroll = 0f; return }
        aimAutoScroll(top, key, order)

        awaitingIndex?.let { awaited ->
            if (dragged.index != awaited) return
            awaitingIndex = null
        }
        val target = items
            .filter { item -> item.key != key && order.any { it.key == item.key } }
            .minByOrNull { abs(it.offset + it.size / 2f - center) }
            ?: return
        // Half a row of overlap per swap, so a row cannot be traded back and
        // forth over a single pixel of travel.
        if (abs(center - (target.offset + target.size / 2f)) <= target.size / 2f) {
            val from = order.firstOrNull { it.key == key }?.index ?: return
            val to = order.firstOrNull { it.key == target.key }?.index ?: return
            if (from == to) return
            // LazyColumn keeps its place by its first visible key. Moving that
            // very row while there is list above it drags the whole list with
            // it; waiting for the next row keeps the held row still on screen.
            if (target.index == listState.firstVisibleItemIndex && listState.canScrollBackward) return
            onMove(from, to)
            awaitingIndex = target.index
        }
    }

    private fun aimAutoScroll(top: Float, key: String, order: List<RebirthQueueRow>) {
        val info = listState.layoutInfo
        val speed = edgeScrollSpeed(
            top = top,
            bottom = top + heldSize,
            viewportStart = info.viewportStartOffset,
            viewportEnd = info.viewportEndOffset,
            zone = edgeZone,
            speed = edgeSpeed,
        )
        val blocked = when {
            speed < 0f -> order.firstOrNull()?.key == key || !listState.canScrollBackward
            speed > 0f -> order.lastOrNull()?.key == key || !listState.canScrollForward
            else -> true
        }
        autoScroll = if (blocked) 0f else speed
    }
}

/** One row the drag may move: its stable key and where it sits in the queue. */
internal data class RebirthQueueRow(val key: String, val index: Int)

/** The edge-scroll bounds, in dp, matching the original queue's pace. */
private val REBIRTH_QUEUE_EDGE_ZONE = 40.dp
private val REBIRTH_QUEUE_EDGE_SPEED = 340.dp

/**
 * Wires a [RebirthQueueDrag] to a list: the finger held near an edge scrolls
 * the list under itself, and each frame's scroll is settled as if the list had
 * moved and the row had not.
 */
@Composable
internal fun rememberRebirthQueueDrag(
    listState: LazyListState,
    rows: List<RebirthQueueRow>,
    onMove: (Int, Int) -> Unit,
): RebirthQueueDrag {
    val drag = remember(listState) { RebirthQueueDrag(listState) }
    drag.rows = rows
    drag.onMove = onMove
    with(LocalDensity.current) {
        drag.configure(
            edgeZonePx = REBIRTH_QUEUE_EDGE_ZONE.toPx(),
            edgeSpeedPx = REBIRTH_QUEUE_EDGE_SPEED.toPx(),
        )
    }
    val speed = drag.autoScroll
    LaunchedEffect(drag, speed) {
        if (speed == 0f) return@LaunchedEffect
        listState.scroll {
            var previous = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                // A dropped frame paid back in full lands as a lurch.
                val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(1f / 30f)
                previous = now
                if (scrollBy(speed * seconds) == 0f) break
                drag.scrolled()
            }
        }
    }
    return drag
}

/**
 * The gesture a row's handle carries: long press to lift, drag to move, and a
 * tick of haptics at each end so the row is felt leaving and landing.
 */
@Composable
internal fun Modifier.rebirthReorderHandle(
    drag: RebirthQueueDrag,
    key: String,
): Modifier {
    val haptics = LocalHapticFeedback.current
    return this.pointerInput(drag, key) {
        detectDragGesturesAfterLongPress(
            onDragStart = {
                drag.start(key)
                if (drag.heldKey != null) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onDragEnd = { drag.end(key) },
            onDragCancel = { drag.end(key) },
            onDrag = { change, amount ->
                change.consume()
                drag.drag(amount.y)
            },
        )
    }
}

/** The index a row currently occupies, or null when it is not in the queue. */
internal fun List<RebirthQueueRow>.indexOfKey(key: String): Int? =
    firstOrNull { it.key == key }?.index

/**
 * A stable key per queue entry.
 *
 * [com.music.bitchord.data.model.Song.queueEntryId] is assigned once, when a
 * track enters the queue, and survives every move — which is exactly the
 * property a drag needs. The fallback covers the transient lists that are
 * built before the timeline has stamped them, where a duplicate is
 * indistinguishable from its twin and an occurrence counter is the only
 * stable answer available.
 */
internal fun rebirthQueueKeys(queue: List<com.music.bitchord.data.model.Song>): List<String> {
    val seen = HashMap<String, Int>()
    return queue.map { song ->
        song.queueEntryId ?: run {
            val base = "v:${song.videoId}"
            val occurrence = seen.getOrElse(base) { 0 }
            seen[base] = occurrence + 1
            "$base#$occurrence"
        }
    }
}

/** Rows for [rebirthQueueKeys], in queue order. */
internal fun rebirthQueueRows(queue: List<com.music.bitchord.data.model.Song>): List<RebirthQueueRow> =
    rebirthQueueKeys(queue).mapIndexed { index, key -> RebirthQueueRow(key, index) }


