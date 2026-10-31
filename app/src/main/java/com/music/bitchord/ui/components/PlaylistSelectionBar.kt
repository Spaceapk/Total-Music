package com.music.bitchord.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song

/** One action menu for every local playlist appearance. Rows always follow visible order. */
@Composable
internal fun PlaylistSelectionBar(
    visible: List<Song>,
    selectedIds: Set<String>,
    onSelection: (Set<String>) -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onAddToPlaylist: (List<Song>) -> Unit,
    onQueue: ((List<Song>, Boolean) -> Unit)?,
    onRemove: ((List<Song>) -> Unit)? = null,
    onMove: ((List<Song>, Boolean) -> Unit)? = null,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val rows = visible.filter { it.videoId in selectedIds }
    var menu by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<List<Song>?>(null) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("playlist-selection"), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.rebirth_selected_count, rows.size), Modifier.weight(1f))
        TextButton(enabled = enabled, onClick = { onSelection(visible.mapTo(HashSet()) { it.videoId }) }) {
            Text(stringResource(R.string.select_all))
        }
        Box {
            IconButton(enabled = enabled && rows.isNotEmpty(), onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, stringResource(R.string.selection_actions))
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                fun act(action: () -> Unit) { menu = false; action() }
                DropdownMenuItem(text = { Text(stringResource(R.string.play)) }, onClick = { act { onPlay(rows, 0); onSelection(emptySet()) } })
                if (onQueue != null) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.play_next)) }, onClick = { act { onQueue(rows, true); onSelection(emptySet()) } })
                    DropdownMenuItem(text = { Text(stringResource(R.string.add_to_queue)) }, onClick = { act { onQueue(rows, false); onSelection(emptySet()) } })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.add_to_playlist)) }, onClick = { act { onAddToPlaylist(rows) } })
                DropdownMenuItem(text = { Text(stringResource(R.string.selection_favorite)) }, onClick = { act {
                    rows.forEach { LikeState.set(it.videoId, LikeStatus.LIKE, it) }; onSelection(emptySet())
                } })
                DropdownMenuItem(text = { Text(stringResource(R.string.selection_unfavorite)) }, onClick = { act {
                    rows.forEach { LikeState.set(it.videoId, LikeStatus.INDIFFERENT, it) }; onSelection(emptySet())
                } })
                if (onMove != null) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.selection_to_start)) }, onClick = { act { onMove(rows, true); onSelection(emptySet()) } })
                    DropdownMenuItem(text = { Text(stringResource(R.string.selection_to_end)) }, onClick = { act { onMove(rows, false); onSelection(emptySet()) } })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.selection_share)) }, onClick = { act {
                    val text = rows.joinToString("\n") { "${it.artist} — ${it.title}" }
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(android.content.Intent.createChooser(send, null))
                } })
                DropdownMenuItem(text = { Text(stringResource(R.string.selection_invert)) }, onClick = { act {
                    onSelection(visible.filterNot { it.videoId in selectedIds }.mapTo(HashSet()) { it.videoId })
                } })
                if (onRemove != null) DropdownMenuItem(
                    text = { Text(stringResource(R.string.selection_remove), color = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.testTag("playlist-remove-selected"),
                    onClick = { act { confirmRemove = rows.toList() } },
                )
            }
        }
        IconButton(onClick = { onSelection(emptySet()) }) { Icon(Icons.Rounded.Close, stringResource(R.string.cancel)) }
    }
    confirmRemove?.let { captured ->
        AlertDialog(onDismissRequest = { confirmRemove = null },
            title = { Text(stringResource(R.string.selection_remove)) },
            text = { Text(stringResource(R.string.selection_remove_confirm, captured.size)) },
            confirmButton = { TextButton(enabled = enabled, onClick = {
                onRemove?.invoke(captured); confirmRemove = null; onSelection(emptySet())
            }) { Text(stringResource(R.string.remove)) } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
