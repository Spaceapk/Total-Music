package com.music.bitchord.ui.rebirth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.FavoriteSort

/** Pass the visible favorites as the queue, not the complete unfiltered device library. */
@Composable
internal fun FavoriteCollectionActions(
    songs: List<Song>,
    onPlay: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    sort: FavoriteSort = FavoriteSort.DATE_ADDED,
    onSort: (FavoriteSort) -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        TextButton(enabled = songs.isNotEmpty(), onClick = { onPlay(songs, 0) }) { Text(stringResource(R.string.play)) }
        TextButton(enabled = songs.isNotEmpty(), onClick = { onShuffle(songs) }) { Text(stringResource(R.string.shuffle)) }
        Box {
            IconButton(onClick = { open = true }) { Icon(Icons.Rounded.Sort, contentDescription = stringResource(R.string.favorites_sort)) }
            DropdownMenu(open, onDismissRequest = { open = false }) {
                FavoriteSort.entries.forEach { entry ->
                    DropdownMenuItem(text = { Text(stringResource(entry.labelRes())) }, onClick = { onSort(entry); open = false })
                }
            }
        }
    }
}

internal fun FavoriteSort.labelRes(): Int = when (this) {
    FavoriteSort.DATE_ADDED -> R.string.favorites_sort_date
    FavoriteSort.TITLE -> R.string.sort_title_ascending
    FavoriteSort.ARTIST -> R.string.artists
}
