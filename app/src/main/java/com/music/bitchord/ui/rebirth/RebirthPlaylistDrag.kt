package com.music.bitchord.ui.rebirth

import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Drag-to-reorder for a playlist's rows.
 *
 * Deliberately simpler than the player's queue drag, because a playlist is a
 * different thing to hold: it is persisted by the store the moment a move
 * lands, so a drag that swapped rows live would write a dozen orders the user
 * never asked for — and if the finger left the screen mid-list, the playlist
 * would keep whatever half-way order the last frame produced.
 *
 * So nothing is written during the drag. The row follows the finger, and on
 * release the number of rows it was carried past is rounded to a whole number
 * and sent as **one** move. Anything the user does not see drop, cannot land
 * in their playlist.
 *
 * Where the player's engine draws a lifted copy and reads the live layout for
 * each swap, this one only needs the row height — the rows in a playlist are
 * uniform — so it stays readable and has no frame-to-frame state to get wrong.
 */
internal class RebirthPlaylistDrag(private val listState: LazyListState) {

    /** The row under the finger, or null at rest. */
    var heldKey by mutableStateOf<String?>(null)
        private set

    /** How far the held row has been carried, in pixels. */
    var offset by mutableFloatStateOf(0f)
        private set

    private var rowHeight = 1f
    private var fromIndex = -1
    private var lastIndex = 0

    /** Called once, on release, with the move the user made. */
    var onDrop: (Int, Int) -> Unit = { _, _ -> }

    fun start(key: String, index: Int, total: Int) {
        if (total <= 1 || index !in 0 until total) return
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        rowHeight = item.size.toFloat().takeIf { it > 0f } ?: 1f
        fromIndex = index
        lastIndex = total - 1
        offset = 0f
        heldKey = key
    }

    fun drag(deltaY: Float) {
        if (heldKey == null) return
        // Clamped to the playlist's own ends: carrying the last row into space
        // above the first would otherwise look like it was going somewhere.
        offset = (offset + deltaY).coerceIn(-rowHeight * (fromIndex + 0.5f), rowHeight * (lastIndex - fromIndex + 0.5f))
    }

    /** The row was released wherever it is. */
    fun end() {
        val key = heldKey ?: return
        val moved = (offset / rowHeight).roundToInt()
        val to = (fromIndex + moved).coerceIn(0, lastIndex)
        heldKey = null
        offset = 0f
        val from = fromIndex
        fromIndex = -1
        if (key.isNotEmpty() && to != from && from in 0..lastIndex) onDrop(from, to)
    }

    /** The list changed under the finger, or the gesture was cancelled. */
    fun cancel() {
        heldKey = null
        offset = 0f
        fromIndex = -1
    }
}

/** Whether [key] is the row the finger is carrying. */
internal fun RebirthPlaylistDrag.isHeld(key: String): Boolean = heldKey == key

/**
 * The row's own displacement while it is carried, and the order it must draw
 * in: above its neighbours, or the rows it passes would cover it.
 *
 * The offset is read inside the layer block rather than at the call site, so a
 * finger moving does not recompose the row — only its layer is re-applied.
 */
internal fun Modifier.rebirthPlaylistLift(drag: RebirthPlaylistDrag, key: String): Modifier =
    this
        .graphicsLayer {
            translationY = if (drag.heldKey == key) drag.offset else 0f
        }
        .zIndex(if (drag.heldKey == key) 1f else 0f)

/**
 * The handle a row is picked up by.
 *
 * A handle rather than the whole row: the row is where taps play, long-presses
 * select and the menu lives, and a drag that started anywhere on it would make
 * all three unreliable.
 */
@Composable
internal fun Modifier.rebirthPlaylistHandle(
    drag: RebirthPlaylistDrag,
    key: String,
    index: Int,
    total: Int,
): Modifier {
    val haptics = LocalHapticFeedback.current
    return this.pointerInput(drag, key, index, total) {
        detectDragGestures(
            onDragStart = {
                drag.start(key, index, total)
                if (drag.heldKey != null) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
 * A playlist row's actions, in the room the two arrows and the close button
 * used to take.
 *
 * Same three things — up, down, remove — and one 40 dp target instead of
 * three, so a narrow phone gives the title its space back. The arrows were
 * also only two of the three actions, and both were unreachable once the
 * reorder guard closed; a menu can say why it is disabled.
 */
@Composable
internal fun PlaylistRowActions(
    index: Int,
    lastIndex: Int,
    canMove: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
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
                text = { Text(stringResource(R.string.star_move_up)) },
                enabled = canMove && index > 0,
                onClick = {
                    menuOpen = false
                    onMoveUp()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.star_move_down)) },
                enabled = canMove && index < lastIndex,
                onClick = {
                    menuOpen = false
                    onMoveDown()
                },
            )
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
