package com.music.bitchord.ui.rebirth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.DevicePlaylistStore
import com.music.bitchord.data.PlaylistSort
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.settings.FavoriteSort
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.TotalMusicSettings
import com.music.bitchord.ui.components.LocalAppBackdrop
import com.music.bitchord.ui.components.backdrop.backdrops.rememberLayerBackdrop
import com.music.bitchord.ui.components.isGlassSupported
import com.music.bitchord.ui.player.PlayerVisualStyle
import com.music.bitchord.ui.player.rebirthColors
import com.music.bitchord.ui.screens.DevicePlaylistPickerDialog

/**
 * The rebirth library experience: one state, two compositions.
 *
 * Everything below the state — what is selected, what is open, what a query
 * filters — happens once, here. [RebirthExpressiveLibrary] and
 * [RebirthGlassLibrary] receive that state and the same callbacks and are free
 * to arrange it however their style says, which is what makes them two designs
 * rather than one design with two colour schemes.
 */
@Composable
fun RebirthLocalExperience(
    style: PlayerVisualStyle,
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    loading: Boolean,
    favoriteIds: Set<String>,
    favoriteSongs: List<Song> = emptyList(),
    initialQuery: String,
    onPlay: (List<Song>, Int) -> Unit,
    onSongMenu: (Song) -> Unit,
    onSettings: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onExpandPlayer: () -> Unit,
    emptyMessage: String? = null,
    onShuffle: ((List<Song>) -> Unit)? = null,
    onOpenGlobalSearch: (() -> Unit)? = null,
    destination: RebirthDestination? = null,
    onDestinationChange: (RebirthDestination) -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember(context) { DevicePlaylistStore.forDevice(context) }
    val playlists by store.playlists.collectAsStateWithLifecycle()
    val order by AppSettings.localMusicSort.collectAsStateWithLifecycle()
    val favoriteSort by AppSettings.favoriteSort.collectAsStateWithLifecycle()
    val favoriteEntries by LikeState.entries.collectAsStateWithLifecycle()
    val transitions by AppSettings.playerAnimatedTransitions.collectAsStateWithLifecycle()
    val reduceMotion by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val glassEffects by AppSettings.playerGlassEffects.collectAsStateWithLifecycle()
    val adaptiveGlass by TotalMusicSettings.adaptiveGlass.collectAsStateWithLifecycle()
    val reduceBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()

    var savedDestination by rememberSaveable { mutableStateOf(RebirthDestination.HOME) }
    val currentDestination = destination ?: savedDestination
    fun navigate(target: RebirthDestination) {
        savedDestination = target
        onDestinationChange(target)
    }
    var category by rememberSaveable { mutableStateOf(RebirthCategory.SONGS) }
    var query by rememberSaveable { mutableStateOf("") }
    var collectionId by rememberSaveable { mutableStateOf<String?>(null) }
    var selection by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val selectionIds = remember(selection) { selection.toHashSet() }
    var addToPlaylist by remember { mutableStateOf<List<Song>?>(null) }
    var playlistName by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<RebirthCollection?>(null) }
    var deleting by remember { mutableStateOf<RebirthCollection?>(null) }

    // A search coming in from the app's own search box lands in the shell's
    // search page rather than replacing the page the reader was on.
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            navigate(RebirthDestination.SEARCH)
            query = initialQuery
        }
    }
    // A query or a closed collection invalidates a selection made under it; so
    // does leaving the page the selection was made on.
    LaunchedEffect(query, collectionId, currentDestination, category) { selection = emptyList() }

    val (albums, artists, saved) = remember(songs, playlists) { rebirthCollections(songs, playlists) }
    val opened = remember(collectionId, albums, artists, saved) {
        (albums + artists + saved).firstOrNull { it.id == collectionId }
    }
    val favoriteVisible = remember(favoriteSongs, favoriteSort, favoriteEntries, query) {
        val added = favoriteEntries.mapValues { it.value.addedAtMillis }
        sortFavorites(favoriteSongs.filter { it.videoId in favoriteIds }, favoriteSort, added).filter { rebirthMatches(it, query) }
    }
    val visible = remember(songs, order, opened, category, favoriteIds, query, favoriteVisible) {
        if (category == RebirthCategory.FAVORITES && opened == null) favoriteVisible else rebirthVisibleSongs(
            sorted = rebirthSortedLibrary(songs, order),
            opened = opened,
            category = category,
            favoriteIds = favoriteIds,
            query = query,
        )
    }
    val collections = when (category) {
        RebirthCategory.ALBUMS -> albums
        RebirthCategory.ARTISTS -> artists
        RebirthCategory.PLAYLISTS -> saved
        else -> emptyList()
    }
    val collectionRows = remember(collections, query) {
        collections.filter { it.title.contains(query, ignoreCase = true) }.chunked(2)
    }
    val openedSort = remember(opened, playlists) {
        val id = opened?.playlistId
        playlists.firstOrNull { it.id == id }?.sort ?: PlaylistSort.MANUAL
    }
    val canReorder = rebirthCanReorder(query, openedSort)

    BackHandler(selection.isNotEmpty() || opened != null || currentDestination != RebirthDestination.HOME) {
        when {
            selection.isNotEmpty() -> selection = emptyList()
            opened != null -> collectionId = null
            else -> navigate(RebirthDestination.HOME)
        }
    }

    val glass = style == PlayerVisualStyle.LIQUID_GLASS_PLUS
    val colors = rebirthColors(style, MaterialTheme.colorScheme)
    // The style's own background goes into the recording, for the reason
    // MainActivity paints the window's into the app-level layer: a page that
    // paints nothing records transparent pixels, and glass sampling those has
    // nothing to blur. It is this palette's background, not the theme's — the
    // rebirth styles bring their own.
    val layerBackground = colors.background
    val paintBackdrop: ContentDrawScope.() -> Unit = remember(layerBackground) {
        {
            drawRect(layerBackground)
            drawContent()
        }
    }
    val backdrop = rememberLayerBackdrop(onDraw = paintBackdrop)
    val recordBackdrop = glass && glassEffects && !reduceBlur && isGlassSupported()

    val data = RebirthLibraryData(
        style = style,
        glass = glass,
        destination = currentDestination,
        category = category,
        query = query,
        songs = songs,
        visible = visible,
        albums = albums,
        artists = artists,
        playlists = saved,
        collectionRows = collectionRows,
        opened = opened,
        openedSort = openedSort,
        canReorder = canReorder,
        currentSong = currentSong,
        isPlaying = isPlaying,
        loading = loading,
        favoriteIds = favoriteIds,
        favoriteSongs = favoriteSongs,
        favoriteSort = favoriteSort,
        selection = selectionIds,
        sort = order,
        emptyMessage = emptyMessage,
        transitions = transitions,
        reduceMotion = reduceMotion,
        glassEffects = glassEffects,
        adaptiveGlass = adaptiveGlass,
        backdropRecording = recordBackdrop,
    )

    val actions = RebirthLibraryActions(
        onPlay = onPlay,
        onShuffle = { list -> onShuffle?.invoke(list) ?: onPlay(list, (list.indices).random()) },
        onSongMenu = onSongMenu,
        onSettings = onSettings,
        onPlayPause = onPlayPause,
        onNext = onNext,
        onExpandPlayer = onExpandPlayer,
        onDestination = { navigate(it); collectionId = null },
        onCategory = { category = it; collectionId = null },
        onQuery = { query = it },
        onOpenCollection = { collection -> collectionId = collection?.id },
        onSort = AppSettings::setLocalMusicSort,
        onFavoriteSort = AppSettings::setFavoriteSort,
        onRemoveFavorite = { song -> LikeState.set(song.videoId, LikeStatus.INDIFFERENT, song) },
        onPlaylistSort = { sort -> opened?.playlistId?.let { id -> store.setSort(id, sort) } },
        onToggleSelection = { id ->
            selection = if (id in selectionIds) selection.filterNot { it == id } else selection + id
        },
        onSelectAll = { rows -> selection = rows.map { it.videoId } },
        onClearSelection = { selection = emptyList() },
        onAddSelectionToPlaylist = { rows -> if (rows.isNotEmpty()) addToPlaylist = rows },
        onCreatePlaylist = { playlistName = "" },
        onRenamePlaylist = { collection -> renaming = collection; playlistName = collection.title },
        onDeletePlaylist = { collection -> deleting = collection },
        onAddToPlaylist = { song -> addToPlaylist = listOf(song) },
        onMoveInPlaylist = { from, to ->
            val id = opened?.playlistId
            if (id != null && canReorder && to in visible.indices && from in visible.indices) {
                store.move(id, from, to)
            }
        },
        onRemoveFromPlaylist = { index ->
            val id = opened?.playlistId
            val song = visible.getOrNull(index)
            if (id != null && song != null && canReorder) store.remove(id, index)
        },
        onOpenGlobalSearch = { onOpenGlobalSearch?.invoke() },
    )

    MaterialTheme(colorScheme = colors) {
        Surface(color = colors.background) {
            Box(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalAppBackdrop provides backdrop) {
                    // No recording node here. The layer is recorded inside
                    // [RebirthGlassLibrary], around the page only: the chrome
                    // samples that layer, and a surface inside the layer it
                    // samples is the 1.8.0 crash. See the comment there.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding(),
                    ) {
                        if (glass) {
                            RebirthGlassLibrary(actions = actions, data = data)
                        } else {
                            RebirthExpressiveLibrary(actions = actions, data = data)
                        }
                    }
                }
            }
        }
        addToPlaylist?.let { songs ->
            DevicePlaylistPickerDialog(songs) {
                addToPlaylist = null
                selection = emptyList()
            }
        }
        playlistName?.let { name ->
            val isRename = renaming != null
            var draft by remember(name) { mutableStateOf(name) }
            RebirthDialog(
                style = style,
                title = stringResource(if (isRename) R.string.rename_playlist else R.string.create_playlist),
                onDismiss = { playlistName = null; renaming = null },
                confirmLabel = stringResource(R.string.save),
                confirmEnabled = draft.isNotBlank(),
                onConfirm = {
                    val target = renaming
                    if (target == null) {
                        store.create(draft.trim())
                    } else {
                        store.rename(target.playlistId.orEmpty(), draft.trim())
                    }
                    playlistName = null
                    renaming = null
                },
            ) {
                RebirthTextField(
                    value = draft,
                    onValueChange = { draft = it.take(64) },
                    label = stringResource(R.string.name),
                )
            }
        }
        deleting?.let { collection ->
            RebirthDialog(
                style = style,
                title = stringResource(R.string.delete_playlist),
                onDismiss = { deleting = null },
                confirmLabel = stringResource(R.string.delete),
                onConfirm = {
                    collection.playlistId?.let(store::delete)
                    deleting = null
                    if (collectionId == collection.id) collectionId = null
                },
            ) {
                androidx.compose.material3.Text(
                    text = stringResource(R.string.delete_playlist_confirmation, collection.title),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
