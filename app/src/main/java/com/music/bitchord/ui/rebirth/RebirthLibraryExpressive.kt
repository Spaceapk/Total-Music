package com.music.bitchord.ui.rebirth

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.LocalMusicSort

/**
 * Google Expressive+ Reimagined, app side.
 *
 * An editorial library: one hero, numbered rows, big type and thumb-sized
 * metrics. The glass gallery next door indexes the same data on a rail and
 * shows covers as the primary object; nothing about the arrangement is shared,
 * and everything about the data is — see [RebirthLibraryData].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RebirthExpressiveLibrary(data: RebirthLibraryData, actions: RebirthLibraryActions) {
    var sortOpen by remember { mutableStateOf(false) }
    val selectionActive = data.selection.isNotEmpty()
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.rebirth_home_hero),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when (data.destination) {
                        RebirthDestination.HOME -> stringResource(R.string.rebirth_home_collection)
                        RebirthDestination.LIBRARY -> data.category.label()
                        RebirthDestination.SEARCH -> stringResource(R.string.rebirth_search_local)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { sortOpen = true }) {
                    Icon(Icons.Rounded.Sort, contentDescription = stringResource(R.string.sort_library))
                }
                DropdownMenu(sortOpen, onDismissRequest = { sortOpen = false }) {
                    LocalMusicSort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sort.rebirthLabel()) },
                            onClick = {
                                actions.onSort(sort)
                                sortOpen = false
                            },
                        )
                    }
                }
            }
            IconButton(onClick = actions.onSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings))
            }
        }
        if (data.destination == RebirthDestination.SEARCH || data.query.isNotEmpty()) {
            RebirthSearchField(
                value = data.query,
                onValueChange = actions.onQuery,
                placeholder = stringResource(R.string.search_hint),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        if (selectionActive) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.rebirth_selected_count, data.selection.size),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = { actions.onSelectAll(data.visible) }) {
                    Text(stringResource(R.string.select_all))
                }
                IconButton(onClick = { actions.onAddSelectionToPlaylist(data.visible.filter { it.videoId in data.selection }) }) {
                    Icon(Icons.Rounded.PlaylistAdd, contentDescription = stringResource(R.string.add_to_playlist))
                }
                IconButton(onClick = actions.onClearSelection) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cancel))
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                data.opened != null -> ExpressiveCollection(data, actions)
                data.destination == RebirthDestination.HOME -> ExpressiveHome(data, actions)
                data.destination == RebirthDestination.SEARCH -> ExpressiveRows(data, actions)
                else -> Column(Modifier.fillMaxSize()) {
                    CategoryPills(data, actions, glass = false)
                    if (data.category == RebirthCategory.ALBUMS ||
                        data.category == RebirthCategory.ARTISTS ||
                        data.category == RebirthCategory.PLAYLISTS
                    ) {
                        ExpressiveGallery(data, actions)
                    } else {
                        ExpressiveRows(data, actions)
                    }
                }
            }
        }
        RebirthExpressiveMiniPlayer(
            song = data.currentSong,
            playing = data.isPlaying,
            loading = data.loading,
            onPlayPause = actions.onPlayPause,
            onNext = actions.onNext,
            onExpand = actions.onExpandPlayer,
        )
        RebirthExpressiveNav(
            current = data.destination,
            onDestination = actions.onDestination,
        )
    }
}

@Composable
private fun CategoryPills(data: RebirthLibraryData, actions: RebirthLibraryActions, glass: Boolean) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RebirthCategory.entries.forEach { category ->
            RebirthFilterPill(
                label = category.label(),
                selected = data.category == category,
                onClick = { actions.onCategory(category) },
                glass = glass,
            )
        }
    }
}

@Composable
private fun ExpressiveHome(data: RebirthLibraryData, actions: RebirthLibraryActions) {
    if (data.songs.isEmpty()) {
        RebirthEmptyState(
            icon = Icons.Rounded.MusicNote,
            title = stringResource(R.string.library_empty),
            message = data.emptyMessage ?: stringResource(R.string.rebirth_home_empty),
        )
        return
    }
    val hero = data.currentSong ?: data.songs.firstOrNull()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = hero?.title ?: stringResource(R.string.library_empty),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = hero?.artist.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalIconButton(
                            onClick = {
                                val index = data.songs.indexOfFirst { it.videoId == hero?.videoId }.coerceAtLeast(0)
                                actions.onPlay(data.songs, index)
                            },
                            modifier = Modifier.size(56.dp),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.play))
                        }
                        FilledTonalIconButton(
                            onClick = { actions.onShuffle(data.songs) },
                            modifier = Modifier.size(56.dp),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = stringResource(R.string.shuffle))
                        }
                    }
                }
                Cover(
                    song = hero,
                    modifier = Modifier.width(140.dp).aspectRatio(1f),
                    corner = 32,
                    placeholder = Icons.Rounded.MusicNote,
                )
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricTile(
                    value = data.songs.size.toString(),
                    label = stringResource(R.string.songs),
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    value = data.albums.size.toString(),
                    label = stringResource(R.string.albums),
                    container = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            RebirthSectionHeader(
                title = stringResource(R.string.rebirth_home_explore),
                modifier = Modifier.padding(horizontal = 20.dp),
                action = {
                    TextButton(onClick = { actions.onCategory(RebirthCategory.ALBUMS); actions.onDestination(RebirthDestination.LIBRARY) }) {
                        Text(stringResource(R.string.show_all))
                    }
                },
            )
        }
        item {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(data.albums, key = { it.id }) { album ->
                    Column(
                        Modifier.width(150.dp).clickable { actions.onOpenCollection(album) },
                    ) {
                        Cover(
                            song = album.songs.firstOrNull(),
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            corner = 24,
                            placeholder = Icons.Rounded.Album,
                        )
                        Text(
                            text = album.title,
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = collectionCaption(album.songs.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        item {
            RebirthSectionHeader(
                title = stringResource(R.string.songs),
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        itemsIndexed(data.songs.take(8), key = { _, song -> song.videoId }) { index, song ->
            ExpressiveTrackRow(
                song = song,
                position = index,
                selected = song.videoId in data.selection,
                playing = data.currentSong?.videoId == song.videoId && data.isPlaying,
                onPlay = { actions.onPlay(data.songs, index) },
                onSelect = { actions.onToggleSelection(song.videoId) },
                onMenu = { actions.onSongMenu(song) },
            )
        }
        item {
            TextButton(
                onClick = { actions.onDestination(RebirthDestination.LIBRARY) },
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Icon(Icons.Rounded.LibraryMusic, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.library))
            }
        }
    }
}

@Composable
private fun MetricTile(value: String, label: String, container: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = container,
        modifier = modifier,
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
            )
            Text(text = label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ExpressiveRows(data: RebirthLibraryData, actions: RebirthLibraryActions) {
    if (data.visible.isEmpty()) {
        RebirthEmptyState(
            icon = Icons.Rounded.Search,
            title = stringResource(R.string.no_results),
            message = if (data.query.isBlank()) {
                data.emptyMessage ?: stringResource(R.string.rebirth_home_empty)
            } else {
                stringResource(R.string.search_empty)
            },
        )
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
    ) {
        itemsIndexed(data.visible, key = { _, song -> song.videoId }) { index, song ->
            ExpressiveTrackRow(
                song = song,
                position = index,
                selected = song.videoId in data.selection,
                playing = data.currentSong?.videoId == song.videoId && data.isPlaying,
                onPlay = { actions.onPlay(data.visible, index) },
                onSelect = { actions.onToggleSelection(song.videoId) },
                onMenu = { actions.onSongMenu(song) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExpressiveTrackRow(
    song: Song,
    position: Int,
    selected: Boolean,
    playing: Boolean,
    onPlay: () -> Unit,
    onSelect: () -> Unit,
    onMenu: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                },
            )
            .combinedClickable(onClick = onPlay, onLongClick = onSelect)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (selected) "" else "${position + 1}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(18.dp),
        )
        if (selected) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Cover(song = song, modifier = Modifier.size(48.dp), corner = 14, placeholder = Icons.Rounded.MusicNote)
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal,
                color = if (playing) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onMenu) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more))
        }
    }
}

@Composable
private fun ExpressiveGallery(data: RebirthLibraryData, actions: RebirthLibraryActions) {
    val collections = when (data.category) {
        RebirthCategory.ALBUMS -> data.albums
        RebirthCategory.ARTISTS -> data.artists
        RebirthCategory.PLAYLISTS -> data.playlists
        else -> emptyList()
    }
    if (collections.isEmpty()) {
        RebirthEmptyState(
            icon = if (data.category == RebirthCategory.PLAYLISTS) Icons.AutoMirrored.Rounded.QueueMusic else Icons.Rounded.Album,
            title = if (data.category == RebirthCategory.PLAYLISTS) {
                stringResource(R.string.no_playlists_yet)
            } else {
                stringResource(R.string.no_local_albums)
            },
            message = data.emptyMessage,
        )
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(data.collectionRows, key = { row -> row.joinToString("|") { it.id } }) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { collection ->
                    Column(
                        Modifier.weight(1f).clickable { actions.onOpenCollection(collection) },
                    ) {
                        Cover(
                            song = collection.songs.firstOrNull(),
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            corner = 26,
                            placeholder = Icons.Rounded.Album,
                        )
                        Text(
                            text = collection.title,
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = collectionCaption(collection.songs.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (data.category == RebirthCategory.PLAYLISTS) {
            item {
                TextButton(onClick = actions.onCreatePlaylist, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Icon(Icons.Rounded.PlaylistAdd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.new_playlist))
                }
            }
        }
    }
}

@Composable
private fun ExpressiveCollection(data: RebirthLibraryData, actions: RebirthLibraryActions) {
    val collection = data.opened ?: return
    val listState = rememberLazyListState()
    // One move per drop, decided on release — see [RebirthPlaylistDrag].
    val drag = remember(listState) { RebirthPlaylistDrag(listState) }
    drag.onDrop = { from, to -> actions.onMoveInPlaylist(from, to) }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { actions.onOpenCollection(null) }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = collection.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = collectionCaption(collection.songs.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (collection.isPlaylist) {
                    IconButton(onClick = { actions.onRenamePlaylist(collection) }) {
                        Icon(Icons.Rounded.Sort, contentDescription = stringResource(R.string.rename_playlist))
                    }
                }
                FilledTonalIconButton(
                    onClick = { if (collection.songs.isNotEmpty()) actions.onPlay(collection.songs, 0) },
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.play))
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (collection.isPlaylist) {
                    Text(
                        text = data.openedSort.rebirthLabel(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { actions.onPlaylistSort(nextPlaylistSort(data.openedSort)) }) {
                        Text(stringResource(R.string.space_playlist_sort))
                    }
                }
            }
        }
        if (collection.isPlaylist && !data.canReorder) {
            item {
                Text(
                    text = stringResource(R.string.rebirth_reorder_search_hint),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        itemsIndexed(data.visible, key = { _, song -> song.videoId }) { index, song ->
            val reorderable = collection.isPlaylist && data.canReorder
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .then(
                        if (reorderable) {
                            Modifier.rebirthPlaylistLift(drag, song.videoId)
                        } else {
                            Modifier
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (reorderable) {
                    Icon(
                        Icons.Rounded.DragHandle,
                        contentDescription = stringResource(R.string.rebirth_playlist_drag),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(36.dp)
                            .padding(8.dp)
                            .rebirthPlaylistHandle(drag, song.videoId, index, data.visible.size),
                    )
                }
                Box(Modifier.weight(1f)) {
                    ExpressiveTrackRow(
                        song = song,
                        position = index,
                        selected = song.videoId in data.selection,
                        playing = data.currentSong?.videoId == song.videoId && data.isPlaying,
                        onPlay = { actions.onPlay(data.visible, index) },
                        onSelect = { actions.onToggleSelection(song.videoId) },
                        onMenu = { actions.onSongMenu(song) },
                    )
                }
                if (collection.isPlaylist) {
                    // The same three actions the arrows offered, in the room
                    // the arrows took: at 200 % font they were three 40 dp
                    // targets squeezing the title on a narrow phone, and only
                    // two of them could reorder.
                    PlaylistRowActions(
                        index = index,
                        lastIndex = data.visible.lastIndex,
                        canMove = data.canReorder,
                        onMoveUp = { actions.onMoveInPlaylist(index, index - 1) },
                        onMoveDown = { actions.onMoveInPlaylist(index, index + 1) },
                        onRemove = { actions.onRemoveFromPlaylist(index) },
                    )
                }
            }
        }
        if (collection.isPlaylist) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    TextButton(onClick = { actions.onRenamePlaylist(collection) }) {
                        Text(stringResource(R.string.rename_playlist))
                    }
                    TextButton(onClick = { actions.onDeletePlaylist(collection) }) {
                        Text(stringResource(R.string.delete_playlist))
                    }
                }
            }
        }
    }
}

/** One sleeve, with the placeholder every list in this screen falls back to. */
@Composable
internal fun Cover(
    song: Song?,
    modifier: Modifier,
    corner: Int,
    placeholder: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Box(
        modifier.clip(RoundedCornerShape(corner.dp)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = placeholder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
        )
        AsyncImage(
            model = song?.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}
