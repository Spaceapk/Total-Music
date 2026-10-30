package com.music.bitchord.ui.rebirth

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.ui.components.PlaylistDrag
import com.music.bitchord.ui.components.PlaylistDragAutoScroll
import com.music.bitchord.ui.components.PlaylistRowActions
import com.music.bitchord.ui.components.playlistDragHandle
import com.music.bitchord.ui.components.playlistDragLift
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.LocalMusicSort
import com.music.bitchord.ui.components.LocalAppBackdrop
import com.music.bitchord.ui.components.backdrop.backdrops.LayerBackdrop
import com.music.bitchord.ui.components.backdrop.backdrops.layerBackdrop
import com.music.bitchord.ui.components.liquidGlass

/**
 * Space the floating chrome covers at the bottom of the page.
 *
 * The page runs the full height of the window and the chrome floats over it —
 * that is what gives the capsule something to refract — so a list has to carry
 * this much extra bottom padding or its last row would sit under the capsule.
 * Measured rather than guessed: the mini player only joins the chrome while
 * something is playing, and the capsule is as tall as its own content.
 */
private val LocalChromeInset = compositionLocalOf { 0.dp }

/**
 * Liquid Glass+ Reimagined, app side.
 *
 * A gallery rather than a list: covers are the object and text is a caption on
 * them. The groupings are a rail down the left of a wide window and a row of
 * chips on a narrow one — the same data, arranged for the width it has — and
 * the navigation is a floating capsule that samples the page behind it when
 * glass effects are on.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RebirthGlassLibrary(data: RebirthLibraryData, actions: RebirthLibraryActions) {
    var sortOpen by remember { mutableStateOf(false) }
    // Measured, not guessed: the mini player only joins the chrome while
    // something is playing, and the capsule is as tall as its content.
    var chromeHeight by remember { mutableIntStateOf(0) }
    // A cast, not an assumption: only a layer can be recorded, and the shell
    // provides one. If it ever provides a different backdrop the page simply
    // stops being recorded and the chrome falls back to its flat surface.
    val backdrop = LocalAppBackdrop.current as? LayerBackdrop
    Box(Modifier.fillMaxSize()) {
        // The page that is recorded and the glass that samples it are siblings.
        //
        // In 1.8.0 they were not. The recording node wrapped this whole shell,
        // the floating capsule included, so the capsule's backdrop sampled the
        // very layer it was being drawn into: a layer containing a draw of
        // itself. RenderThread walks that tree until the stack is gone —
        // "Fatal signal 11 (SIGSEGV) ... in RenderNode::prepareTreeImpl", with
        // "stack pointer is not in a rw map; likely due to stack overflow" — and
        // the process died about two seconds into a Liquid Glass+ launch. That
        // is also why coming back from Settings or the player closed the app:
        // both redraw this page.
        //
        // So the page is recorded here on its own and the chrome is drawn over
        // it afterwards, outside the recording. That is the arrangement the
        // classic screens already use for their glass bar, and the only one
        // where the capsule has a page behind it to refract.
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (data.backdropRecording && backdrop != null) {
                        Modifier.layerBackdrop(backdrop)
                    } else {
                        Modifier
                    },
                ),
        ) {
            CompositionLocalProvider(
                LocalChromeInset provides with(LocalDensity.current) { chromeHeight.toDp() },
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = data.opened?.title ?: data.destination.label(),
                                style = MaterialTheme.typography.headlineLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = when {
                                    data.opened != null -> collectionCaption(data.opened.songs.size)
                                    data.destination == RebirthDestination.HOME -> stringResource(R.string.rebirth_home_subtitle)
                                    else -> data.category.label()
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        Box {
                            IconButton(onClick = { sortOpen = true }) {
                                Icon(Icons.Rounded.MusicNote, contentDescription = stringResource(R.string.sort_library))
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
                    if (data.selection.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.rebirth_selected_count, data.selection.size),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelLarge,
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
                            data.opened != null -> GlassCollection(data, actions)
                            data.destination == RebirthDestination.HOME -> GlassHome(data, actions)
                            data.destination == RebirthDestination.SEARCH -> GlassSearch(data, actions)
                            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                                if (maxWidth >= 520.dp) {
                                    Row(Modifier.fillMaxSize()) {
                                        GlassRail(data, actions)
                                        GlassBody(data, actions, Modifier.weight(1f).fillMaxHeight())
                                    }
                                } else {
                                    Column(Modifier.fillMaxSize()) {
                                        GroupingChips(data, actions)
                                        GlassBody(data, actions, Modifier.weight(1f).fillMaxWidth())
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .onSizeChanged { chromeHeight = it.height },
        ) {
            RebirthGlassMiniPlayer(
                song = data.currentSong,
                playing = data.isPlaying,
                loading = data.loading,
                onPlayPause = actions.onPlayPause,
                onNext = actions.onNext,
                onExpand = actions.onExpandPlayer,
                glassEffects = data.glassEffects && data.backdropRecording,
            )
            RebirthGlassNav(
                current = data.destination,
                onDestination = actions.onDestination,
                glassEffects = data.glassEffects && data.backdropRecording,
            )
        }
    }
}

@Composable
private fun GroupingChips(data: RebirthLibraryData, actions: RebirthLibraryActions) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RebirthCategory.entries.forEach { category ->
            RebirthFilterPill(
                label = category.label(),
                selected = data.category == category,
                onClick = { actions.onCategory(category) },
                glass = true,
            )
        }
    }
}

@Composable
private fun GlassRail(data: RebirthLibraryData, actions: RebirthLibraryActions) {
    Column(
        Modifier.width(112.dp).fillMaxHeight().padding(start = 12.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RebirthCategory.entries.forEach { category ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (data.category == category) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    Color.Transparent
                },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .clickable { actions.onCategory(category) },
            ) {
                Text(
                    text = category.label(),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (data.category == category) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun GlassBody(data: RebirthLibraryData, actions: RebirthLibraryActions, modifier: Modifier) {
    when (data.category) {
        RebirthCategory.ALBUMS, RebirthCategory.ARTISTS, RebirthCategory.PLAYLISTS ->
            GlassGallery(data, actions, modifier)
        else -> GlassRows(data, actions, modifier)
    }
}

@Composable
private fun GlassHome(data: RebirthLibraryData, actions: RebirthLibraryActions) {
    if (data.songs.isEmpty()) {
        RebirthEmptyState(
            icon = Icons.Rounded.MusicNote,
            title = stringResource(R.string.library_empty),
            message = data.emptyMessage ?: stringResource(R.string.rebirth_home_empty),
        )
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp + LocalChromeInset.current),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Cover(
                        song = data.currentSong ?: data.songs.firstOrNull(),
                        modifier = Modifier.size(72.dp),
                        corner = 20,
                        placeholder = Icons.Rounded.MusicNote,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.rebirth_home_session),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = data.currentSong?.title ?: stringResource(R.string.rebirth_home_hero),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = {
                        val index = data.songs.indexOfFirst { it.videoId == data.currentSong?.videoId }.coerceAtLeast(0)
                        actions.onPlay(data.songs, index)
                    }) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.play))
                    }
                }
            }
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(data.albums, key = { it.id }) { album ->
                    Column(Modifier.width(170.dp).clickable { actions.onOpenCollection(album) }) {
                        Cover(
                            song = album.songs.firstOrNull(),
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            corner = 20,
                            placeholder = Icons.Rounded.Album,
                        )
                        Text(
                            text = album.title,
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.rebirth_home_explore),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = { actions.onShuffle(data.songs) }) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = stringResource(R.string.shuffle))
                }
            }
        }
        itemsIndexed(data.songs.take(10), key = { _, song -> song.videoId }) { index, song ->
            GlassTrackRow(
                song = song,
                selected = song.videoId in data.selection,
                favorite = song.videoId in data.favoriteIds,
                playing = data.currentSong?.videoId == song.videoId && data.isPlaying,
                onPlay = { actions.onPlay(data.songs, index) },
                onSelect = { actions.onToggleSelection(song.videoId) },
                onMenu = { actions.onSongMenu(song) },
                favoriteAction = data.category == RebirthCategory.FAVORITES,
                onRemoveFavorite = { actions.onRemoveFavorite(song) },
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(onClick = { actions.onDestination(RebirthDestination.LIBRARY) }) {
                    Icon(Icons.Rounded.LibraryMusic, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.library))
                }
            }
        }
    }
}

@Composable
private fun GlassSearch(data: RebirthLibraryData, actions: RebirthLibraryActions) {
    Column(Modifier.fillMaxSize()) {
        RebirthSearchField(
            value = data.query,
            onValueChange = actions.onQuery,
            placeholder = stringResource(R.string.search_hint),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            glass = true,
        )
        Column(Modifier.weight(1f)) {
            if (data.query.isBlank()) {
                RebirthEmptyState(
                    icon = Icons.Rounded.Search,
                    title = stringResource(R.string.rebirth_search_local),
                    message = stringResource(R.string.search_hint),
                )
            } else {
                GlassRows(data, actions, Modifier.fillMaxSize())
            }
        }
        // Above the floating chrome, not under it.
        TextButton(
            onClick = actions.onOpenGlobalSearch,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .padding(bottom = LocalChromeInset.current),
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.rebirth_search_global))
        }
    }
}

@Composable
private fun GlassRows(data: RebirthLibraryData, actions: RebirthLibraryActions, modifier: Modifier = Modifier) {
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
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 4.dp,
            bottom = 4.dp + LocalChromeInset.current,
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (data.category == RebirthCategory.FAVORITES) {
            item(key = "favorites-playback") {
                FavoriteCollectionActions(data.visible, actions.onPlay, actions.onShuffle, data.favoriteSort, actions.onFavoriteSort)
            }
        }
        itemsIndexed(data.visible, key = { _, song -> song.videoId }) { index, song ->
            GlassTrackRow(
                song = song,
                selected = song.videoId in data.selection,
                favorite = song.videoId in data.favoriteIds,
                playing = data.currentSong?.videoId == song.videoId && data.isPlaying,
                onPlay = { actions.onPlay(data.visible, index) },
                onSelect = { actions.onToggleSelection(song.videoId) },
                onMenu = { actions.onSongMenu(song) },
                favoriteAction = data.category == RebirthCategory.FAVORITES,
                onRemoveFavorite = { actions.onRemoveFavorite(song) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlassTrackRow(
    song: Song,
    selected: Boolean,
    favorite: Boolean,
    playing: Boolean,
    onPlay: () -> Unit,
    onSelect: () -> Unit,
    onMenu: () -> Unit,
    favoriteAction: Boolean = false,
    onRemoveFavorite: () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
            .combinedClickable(onClick = onPlay, onLongClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Cover(song = song, modifier = Modifier.size(44.dp), corner = 12, placeholder = Icons.Rounded.MusicNote)
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal,
                color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(song.artist, song.durationText).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (favoriteAction) {
            IconButton(onClick = onRemoveFavorite) {
                Icon(Icons.Rounded.Favorite, contentDescription = stringResource(R.string.remove_from_liked), tint = MaterialTheme.colorScheme.primary)
            }
        } else if (favorite) {
            Icon(Icons.Rounded.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onMenu) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more))
        }
    }
}

@Composable
private fun GlassGallery(data: RebirthLibraryData, actions: RebirthLibraryActions, modifier: Modifier = Modifier) {
    val collections = when (data.category) {
        RebirthCategory.ALBUMS -> data.albums
        RebirthCategory.ARTISTS -> data.artists
        else -> data.playlists
    }
    if (collections.isEmpty()) {
        RebirthEmptyState(
            icon = if (data.category == RebirthCategory.PLAYLISTS) {
                Icons.AutoMirrored.Rounded.QueueMusic
            } else {
                Icons.Rounded.Album
            },
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
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = 6.dp,
            bottom = 6.dp + LocalChromeInset.current,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(data.collectionRows, key = { row -> row.joinToString("|") { it.id } }) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { collection ->
                    Column(
                        Modifier
                            .weight(1f)
                            .testTag(
                                if (collection.isFavorites) "favorites-collection" else collection.id,
                            )
                            .clickable { actions.onOpenCollection(collection) },
                    ) {
                        Box {
                            Cover(
                                song = collection.songs.firstOrNull(),
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                corner = 18,
                                placeholder = if (collection.isFavorites) {
                                    Icons.Rounded.Favorite
                                } else {
                                    Icons.Rounded.Album
                                },
                            )
                            if (collection.isFavorites) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.Favorite,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(6.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            text = collection.title,
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (data.category == RebirthCategory.PLAYLISTS) {
            item {
                TextButton(onClick = actions.onCreatePlaylist) {
                    Icon(Icons.Rounded.PlaylistAdd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.new_playlist))
                }
            }
        }
    }
}

@Composable
private fun GlassCollection(data: RebirthLibraryData, actions: RebirthLibraryActions) {
    val collection = data.opened ?: return
    val listState = rememberLazyListState()
    // One move per drop, decided on release — see [PlaylistDrag]. Near either
    // edge the list scrolls itself, so a long playlist can be reordered beyond
    // the rows that happen to fit on screen.
    val density = LocalDensity.current
    val edgeZone = with(density) { 56.dp.toPx() }
    val edgeSpeed = with(density) { 1100.dp.toPx() }
    val drag = remember(listState, edgeZone, edgeSpeed) {
        PlaylistDrag(listState, edgeZone, edgeSpeed)
    }
    drag.onDrop = { from, to -> actions.onMoveInPlaylist(from, to) }
    PlaylistDragAutoScroll(drag, listState)
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { actions.onOpenCollection(null) }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(
                text = collectionCaption(collection.songs.size),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = { if (data.visible.isNotEmpty()) actions.onPlay(data.visible, 0) }) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.play))
            }
            if (collection.isPlaylist) {
                PlaylistSortControl(sort = data.openedSort, onSort = actions.onPlaylistSort)
            }
        }
        if (collection.isFavorites) {
            FavoriteCollectionActions(
                data.visible,
                actions.onPlay,
                actions.onShuffle,
                data.favoriteSort,
                actions.onFavoriteSort,
            )
            if (data.visible.isEmpty() && data.query.isBlank()) {
                Text(
                    text = stringResource(R.string.rebirth_favorites_empty),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (collection.isPlaylist && !data.canReorder) {
            Text(
                text = stringResource(R.string.rebirth_reorder_search_hint),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (collection.isPlaylist && data.openedSort != PlaylistSort.MANUAL) {
            Text(
                text = stringResource(R.string.rebirth_reorder_manual_hint),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(
                start = 8.dp,
                end = 8.dp,
                top = 4.dp,
                bottom = 4.dp + LocalChromeInset.current,
            ),
        ) {
            itemsIndexed(data.visible, key = { _, song -> song.videoId }) { index, song ->
                val reorderable = collection.isPlaylist && data.canReorder
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (reorderable) {
                                Modifier.playlistDragLift(drag, song.videoId)
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
                                .size(44.dp)
                                .testTag("playlist-drag-${song.videoId}")
                                .playlistDragHandle(drag, song.videoId, index, data.visible.size)
                                .padding(10.dp),
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        GlassTrackRow(
                            song = song,
                            selected = song.videoId in data.selection,
                            favorite = song.videoId in data.favoriteIds,
                            favoriteAction = collection.isFavorites,
                            onRemoveFavorite = { actions.onRemoveFavorite(song) },
                            playing = data.currentSong?.videoId == song.videoId && data.isPlaying,
                            onPlay = { actions.onPlay(data.visible, index) },
                            onSelect = { actions.onToggleSelection(song.videoId) },
                            onMenu = { actions.onSongMenu(song) },
                        )
                    }
                    if (collection.isPlaylist) {
                        PlaylistRowActions(
                            onRemove = { actions.onRemoveFromPlaylist(index) },
                        )
                    }
                }
            }
            if (collection.isPlaylist) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
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
}

/**
 * The floating capsule.
 *
 * `liquidGlass` is only safe under a provided backdrop, which the caller
 * supplies — see [RebirthLocalExperience]. With glass effects off, or on a
 * device that cannot sample, the same shape is drawn as a flat surface.
 */
@Composable
internal fun RebirthGlassNav(
    current: RebirthDestination,
    onDestination: (RebirthDestination) -> Unit,
    glassEffects: Boolean,
) {
    val shape = RoundedCornerShape(28.dp)
    Surface(
        shape = shape,
        color = if (glassEffects) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .navigationBarsPadding()
            .then(if (glassEffects) Modifier.liquidGlass(shape, clear = true) else Modifier),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DestinationButton(RebirthDestination.HOME, current, onDestination, Icons.Rounded.Home, Modifier.weight(1f))
            DestinationButton(RebirthDestination.LIBRARY, current, onDestination, Icons.Rounded.LibraryMusic, Modifier.weight(1f))
            DestinationButton(RebirthDestination.SEARCH, current, onDestination, Icons.Rounded.Search, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DestinationButton(
    destination: RebirthDestination,
    current: RebirthDestination,
    onDestination: (RebirthDestination) -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val selected = destination == current
    val shape = CircleShape
    Surface(
        shape = shape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            Color.Transparent
        },
        modifier = modifier.padding(horizontal = 4.dp).clip(shape).semantics { this.selected = selected; role = Role.Tab }.clickable { onDestination(destination) },
    ) {
        Column(
            Modifier.heightIn(min = 52.dp).padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = destination.label(),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            run {
                Spacer(Modifier.size(4.dp))
                Text(
                    text = destination.label(),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}


