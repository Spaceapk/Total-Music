package com.music.bitchord.ui.rebirth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.LocalMusicSort
import com.music.bitchord.ui.components.PlaylistSelectionBar
import com.music.bitchord.ui.components.floatingBarInsets

/** Independent dark library composition; all state mutations still use the shared stores. */
@Composable
internal fun ObsidianLibrary(data: RebirthLibraryData, actions: RebirthLibraryActions) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().imePadding().background(colors.background).testTag("obsidian-library"),
    ) {
        if (data.opened != null) {
            Box(Modifier.weight(1f).fillMaxWidth()) { ExpressiveCollection(data, actions) }
        } else {
            ObsidianHeader(data, actions)
            if (data.selection.isNotEmpty()) {
                PlaylistSelectionBar(
                    visible = data.visible, selectedIds = data.selection,
                    onSelection = actions.onSelection, onPlay = actions.onPlaySelection,
                    onAddToPlaylist = actions.onAddSelectionToPlaylist,
                    onQueue = actions.onQueueSelection,
                    onRemove = actions.onRemoveSelection, onMove = actions.onMoveSelection,
                    enabled = !actions.selectionBusy,
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    data.songs.isEmpty() -> RebirthEmptyState(
                        icon = Icons.Rounded.MusicNote,
                        title = stringResource(R.string.library_empty),
                        message = data.emptyMessage ?: stringResource(R.string.rebirth_home_empty),
                    )
                    data.destination == RebirthDestination.SEARCH || data.query.isNotBlank() -> ObsidianRows(data, actions)
                    data.destination == RebirthDestination.HOME -> ObsidianHome(data, actions)
                    else -> Column(Modifier.fillMaxSize()) {
                        ObsidianCategories(data, actions)
                        when (data.category) {
                            RebirthCategory.ARTISTS -> ObsidianArtists(data, actions)
                            RebirthCategory.ALBUMS -> ObsidianAlbums(data, actions)
                            RebirthCategory.PLAYLISTS -> ObsidianPlaylists(data, actions)
                            else -> ObsidianRows(data, actions)
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().windowInsetsPadding(floatingBarInsets)) {
            ObsidianDock(data, actions)
        }
    }
}

@Composable
private fun ObsidianHeader(data: RebirthLibraryData, actions: RebirthLibraryActions) {
    var sortOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.obsidian_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = when (data.destination) {
                    RebirthDestination.HOME -> stringResource(R.string.rebirth_home_collection)
                    RebirthDestination.LIBRARY -> data.category.label()
                    RebirthDestination.SEARCH -> stringResource(R.string.rebirth_search_local)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { sortOpen = true }) {
                Icon(Icons.Rounded.Sort, contentDescription = stringResource(R.string.sort_library))
            }
            DropdownMenu(sortOpen, onDismissRequest = { sortOpen = false }) {
                LocalMusicSort.entries.forEach { sort ->
                    DropdownMenuItem(text = { Text(sort.rebirthLabel()) }, onClick = { actions.onSort(sort); sortOpen = false })
                }
            }
        }
        IconButton(onClick = actions.onSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings))
        }
    }
}

@Composable
private fun ObsidianCategories(data: RebirthLibraryData, actions: RebirthLibraryActions) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RebirthCategory.entries.forEach { category ->
            RebirthFilterPill(category.label(), data.category == category, { actions.onCategory(category) }, glass = false)
        }
    }
}

@Composable
private fun ObsidianHome(data: RebirthLibraryData, actions: RebirthLibraryActions) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Cover(
                    song = data.currentSong ?: data.songs.firstOrNull(),
                    modifier = Modifier.size(112.dp), corner = 56,
                    placeholder = Icons.Rounded.MusicNote,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        data.currentSong?.title ?: stringResource(R.string.rebirth_home_hero),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        data.currentSong?.artist ?: stringResource(R.string.rebirth_home_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalIconButton(
                            onClick = {
                                val index = data.songs.indexOfFirst { it.videoId == data.currentSong?.videoId }.coerceAtLeast(0)
                                actions.onPlay(data.songs, index)
                            },
                            modifier = Modifier.size(52.dp), shape = CircleShape,
                        ) { Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.play)) }
                        FilledTonalIconButton(
                            onClick = { actions.onShuffle(data.songs) },
                            modifier = Modifier.size(52.dp), shape = CircleShape,
                        ) { Icon(Icons.Rounded.Shuffle, contentDescription = stringResource(R.string.shuffle)) }
                    }
                }
            }
        }
        if (data.artists.isNotEmpty()) {
            item {
                RebirthSectionHeader(stringResource(R.string.obsidian_artists), Modifier.padding(horizontal = 20.dp)) {
                    TextButton(onClick = { actions.onDestination(RebirthDestination.LIBRARY); actions.onCategory(RebirthCategory.ARTISTS) }) {
                        Text(stringResource(R.string.obsidian_see_all))
                    }
                }
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(data.artists.take(14), key = { it.id }) { artist ->
                        Column(
                            Modifier.width(92.dp).clickable { actions.onOpenCollection(artist) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Cover(artist.songs.firstOrNull(), Modifier.size(88.dp), 44, Icons.Rounded.MusicNote)
                            Text(artist.title, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
        item { RebirthSectionHeader(stringResource(R.string.obsidian_quick_picks), Modifier.padding(horizontal = 20.dp)) }
        itemsIndexed(data.songs.take(8), key = { _, song -> "quick-${song.videoId}" }) { index, song ->
            ExpressiveTrackRow(
                song = song, position = index, selected = song.videoId in data.selection,
                playing = data.currentSong?.videoId == song.videoId && data.isPlaying,
                onPlay = { actions.onPlay(data.songs, data.songs.indexOf(song).coerceAtLeast(0)) },
                onSelect = { actions.onToggleSelection(song.videoId) }, onMenu = { actions.onSongMenu(song) },
            )
        }
        if (data.albums.isNotEmpty()) {
            item { RebirthSectionHeader(stringResource(R.string.albums), Modifier.padding(horizontal = 20.dp)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(data.albums.take(12), key = { it.id }) { album ->
                        Column(Modifier.width(132.dp).clickable { actions.onOpenCollection(album) }) {
                            Cover(album.songs.firstOrNull(), Modifier.fillMaxWidth().aspectRatio(1f), 22, Icons.Rounded.Album)
                            Text(album.title, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ObsidianRows(data: RebirthLibraryData, actions: RebirthLibraryActions) {
    if (data.visible.isEmpty()) {
        RebirthEmptyState(Icons.Rounded.MusicNote, stringResource(R.string.no_results), null)
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        itemsIndexed(data.visible, key = { _, song -> song.videoId }) { index, song ->
            ExpressiveTrackRow(
                song = song, position = index, selected = song.videoId in data.selection,
                playing = data.currentSong?.videoId == song.videoId && data.isPlaying,
                onPlay = { actions.onPlay(data.visible, index) },
                onSelect = { actions.onToggleSelection(song.videoId) }, onMenu = { actions.onSongMenu(song) },
                favoriteAction = data.category == RebirthCategory.FAVORITES,
                onRemoveFavorite = { actions.onRemoveFavorite(song) },
            )
        }
    }
}

@Composable
private fun ObsidianArtists(data: RebirthLibraryData, actions: RebirthLibraryActions) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        items(data.artists.chunked(3), key = { row -> row.joinToString("|") { it.id } }) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { artist ->
                    Column(Modifier.weight(1f).clickable { actions.onOpenCollection(artist) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Cover(artist.songs.firstOrNull(), Modifier.fillMaxWidth().aspectRatio(1f), 1000, Icons.Rounded.MusicNote)
                        Text(artist.title, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ObsidianAlbums(data: RebirthLibraryData, actions: RebirthLibraryActions) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(data.collectionRows, key = { row -> row.joinToString("|") { it.id } }) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { album ->
                    Column(Modifier.weight(1f).clickable { actions.onOpenCollection(album) }) {
                        Cover(album.songs.firstOrNull(), Modifier.fillMaxWidth().aspectRatio(1f), 22, Icons.Rounded.Album)
                        Text(album.title, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(collectionCaption(album.songs.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ObsidianPlaylists(data: RebirthLibraryData, actions: RebirthLibraryActions) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
        items(data.playlists, key = { it.id }) { collection ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .clickable { actions.onOpenCollection(collection) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Cover(
                    collection.songs.firstOrNull(), Modifier.size(52.dp), if (collection.isFavorites) 26 else 16,
                    if (collection.isFavorites) Icons.Rounded.Favorite else Icons.AutoMirrored.Rounded.QueueMusic,
                )
                Column(Modifier.weight(1f)) {
                    Text(collection.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(collectionCaption(collection.songs.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            TextButton(onClick = actions.onCreatePlaylist) {
                Icon(Icons.Rounded.PlaylistAdd, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.new_playlist))
            }
        }
    }
}
