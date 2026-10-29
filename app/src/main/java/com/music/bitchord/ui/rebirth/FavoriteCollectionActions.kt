package com.music.bitchord.ui.rebirth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.music.bitchord.R
import com.music.bitchord.data.model.Song

/** Pass the visible favorites as the queue, not the complete unfiltered device library. */
@Composable
internal fun FavoriteCollectionActions(songs: List<Song>, onPlay: (List<Song>, Int) -> Unit, onShuffle: (List<Song>) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        TextButton(enabled = songs.isNotEmpty(), onClick = { onPlay(songs, 0) }) { Text(stringResource(R.string.play)) }
        TextButton(enabled = songs.isNotEmpty(), onClick = { onShuffle(songs) }) { Text(stringResource(R.string.shuffle)) }
    }
}
