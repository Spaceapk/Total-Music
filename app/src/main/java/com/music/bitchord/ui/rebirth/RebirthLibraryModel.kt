package com.music.bitchord.ui.rebirth

import com.music.bitchord.data.DevicePlaylist
import com.music.bitchord.data.PlaylistSort
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.inAlbumOrder
import com.music.bitchord.data.settings.LocalMusicSort
import com.music.bitchord.data.settings.FavoriteSort
import com.music.bitchord.data.LikeState
import com.music.bitchord.ui.player.PlayerVisualStyle

/**
 * The three pages the rebirth library has.
 *
 * They are pages of one shell rather than destinations of the app's tab bar:
 * the tab bar belongs to the app, and the shell is only ever what the Library
 * tab draws. Keeping that distinction is what lets "search" here mean "search
 * what is on this device" without stealing the meaning of the app's own search
 * tab, which stays one tap away in the shell's bar.
 */
enum class RebirthDestination { HOME, LIBRARY, SEARCH }

/** What the library page is listing. */
internal enum class RebirthCategory { SONGS, ALBUMS, ARTISTS, PLAYLISTS, FAVORITES }

/**
 * An album, an artist or a saved playlist, as one row of the library grid.
 *
 * [playlistId] is set only for saved playlists — it is the id the store knows
 * them by, and the reason renaming one has to go through the store rather than
 * through the title this object carries.
 */
internal data class RebirthCollection(
    val id: String,
    val title: String,
    val songs: List<Song>,
    val playlistId: String? = null,
    /**
     * "Mis favoritas" is a collection with no row in the playlist file: its
     * membership is the shared heart store, so every heart tapped in the
     * player, a notification or another page is visible here without a second
     * copy that could fall out of date.
     */
    val isFavorites: Boolean = false,
) {
    val isPlaylist: Boolean get() = playlistId != null
    /** Only a saved playlist has a name in the file to rename. */
    val canBeRenamed: Boolean get() = isPlaylist
}

/** The virtual collection's stable id; it is not a DevicePlaylist id. */
internal const val FAVORITES_COLLECTION_ID = "collection:favorites"

/** The virtual collection, in the order the favorites picker last chose. */
internal fun rebirthFavoriteCollection(songs: List<Song>, title: String): RebirthCollection =
    RebirthCollection(
        id = FAVORITES_COLLECTION_ID,
        title = title,
        songs = songs,
        isFavorites = true,
    )

/**
 * One page of the shell, in everything both compositions need to draw it.
 *
 * Nothing in here is Compose: a scene receives it, a unit test can build it.
 */
internal data class RebirthLibraryData(
    val style: PlayerVisualStyle,
    val glass: Boolean,
    val destination: RebirthDestination,
    val category: RebirthCategory,
    val query: String,
    val songs: List<Song>,
    val visible: List<Song>,
    val albums: List<RebirthCollection>,
    val artists: List<RebirthCollection>,
    val playlists: List<RebirthCollection>,
    val collectionRows: List<List<RebirthCollection>>,
    val opened: RebirthCollection?,
    val openedSort: PlaylistSort,
    val canReorder: Boolean,
    val currentSong: Song?,
    val isPlaying: Boolean,
    val loading: Boolean,
    val favoriteIds: Set<String>,
    val favoriteSongs: List<Song>,
    val favoriteSort: FavoriteSort,
    val selection: Set<String>,
    val sort: LocalMusicSort,
    val emptyMessage: String?,
    val transitions: Boolean,
    val reduceMotion: Boolean,
    val glassEffects: Boolean,
    val adaptiveGlass: Boolean,
    val backdropRecording: Boolean,
)

/** Everything both compositions can do. A missing action is a compile error, not a dead row. */
internal class RebirthLibraryActions(
    val onPlay: (List<Song>, Int) -> Unit,
    val onShuffle: (List<Song>) -> Unit,
    val onSongMenu: (Song) -> Unit,
    val onSettings: () -> Unit,
    val onPlayPause: () -> Unit,
    val onNext: () -> Unit,
    val onExpandPlayer: () -> Unit,
    val onDestination: (RebirthDestination) -> Unit,
    val onCategory: (RebirthCategory) -> Unit,
    val onQuery: (String) -> Unit,
    val onOpenCollection: (RebirthCollection?) -> Unit,
    val onSort: (LocalMusicSort) -> Unit,
    val onFavoriteSort: (FavoriteSort) -> Unit,
    val onRemoveFavorite: (Song) -> Unit,
    val onPlaylistSort: (PlaylistSort) -> Unit,
    val onToggleSelection: (String) -> Unit,
    val onSelectAll: (List<Song>) -> Unit,
    val onClearSelection: () -> Unit,
    val onAddSelectionToPlaylist: (List<Song>) -> Unit,
    val onCreatePlaylist: () -> Unit,
    val onRenamePlaylist: (RebirthCollection) -> Unit,
    val onDeletePlaylist: (RebirthCollection) -> Unit,
    val onAddToPlaylist: (Song) -> Unit,
    val onMoveInPlaylist: (Int, Int) -> Unit,
    val onRemoveFromPlaylist: (Int) -> Unit,
    val onOpenGlobalSearch: () -> Unit,
)

/**
 * Albums in track order, not in the order the scanner happened to return.
 *
 * Discs first, then track numbers, then whatever says neither last: a
 * compilation whose tags were never filled in would otherwise sort to the top
 * by nothing at all. The rule itself lives in `AlbumOrdering`, shared with the
 * album screen, so the library and an album cannot disagree about disc 2 of a
 * set whose first disc has no tag: a missing disc number is disc one.
 */
internal fun rebirthAlbumOrder(songs: List<Song>): List<Song> = songs.inAlbumOrder()

/** Whether a song answers a query, ignoring case and outer whitespace. */
internal fun rebirthMatches(song: Song, query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return song.title.contains(needle, ignoreCase = true) ||
        song.artist.contains(needle, ignoreCase = true) ||
        song.albumName.orEmpty().contains(needle, ignoreCase = true)
}

/**
 * The library in the requested order.
 *
 * [LocalMusicSort.TRACK_NUMBER] is the disc-and-track order the legacy list
 * calls "track number", not "the order the device gave us": the scanner's own
 * order is not stable across a rescan, so nothing here falls back to it.
 */
internal fun rebirthSortedLibrary(songs: List<Song>, sort: LocalMusicSort): List<Song> = when (sort) {
    LocalMusicSort.TRACK_NUMBER -> rebirthAlbumOrder(songs)
    LocalMusicSort.TITLE_ASC -> songs.sortedBy { it.title.lowercase() }
    LocalMusicSort.TITLE_DESC -> songs.sortedByDescending { it.title.lowercase() }
    LocalMusicSort.DATE_ADDED -> songs.sortedByDescending { it.localDateAddedSeconds ?: 0L }
    LocalMusicSort.DATE_MODIFIED -> songs.sortedByDescending { it.localDateModifiedSeconds ?: 0L }
}

/**
 * A selection, kept only while the rows it was made on are still on screen.
 *
 * The filter runs both ways: a query that hides a selected song drops it, and
 * clearing the query does not bring it back. That is what keeps "3 selected"
 * from describing rows nobody can see, and it is the bug the 1.7.0 prototype
 * had when a search was typed over a selection.
 */
internal fun rebirthFilteredSelection(selection: Set<String>, visible: List<Song>): Set<String> =
    selection intersect visible.mapTo(HashSet()) { it.videoId }

/**
 * Whether a playlist can be reordered right now.
 *
 * The only condition is no query. A filtered list is not the playlist's running
 * order, so a move inside it would write an order nobody asked for; the UI
 * explains this rather than hiding the handle, so the reason is visible.
 *
 * Any sort criterion is reorderable. Dragging while one is active stores the
 * rows exactly as they were arranged and switches the playlist to manual —
 * see `DevicePlaylistStore.move`.
 */
internal fun rebirthCanReorder(query: String): Boolean = query.isBlank()

/**
 * The next order in the playlist sort cycle.
 *
 * Manual first because it is the only one a reader can edit, and returning to
 * it is how they get their own running order back.
 */
internal fun nextPlaylistSort(current: PlaylistSort): PlaylistSort = when (current) {
    PlaylistSort.MANUAL -> PlaylistSort.TITLE_ASC
    PlaylistSort.TITLE_ASC -> PlaylistSort.TITLE_DESC
    PlaylistSort.TITLE_DESC -> PlaylistSort.ARTIST_ASC
    PlaylistSort.ARTIST_ASC -> PlaylistSort.ARTIST_DESC
    PlaylistSort.ARTIST_DESC -> PlaylistSort.ALBUM_ASC
    PlaylistSort.ALBUM_ASC -> PlaylistSort.ALBUM_DESC
    PlaylistSort.ALBUM_DESC -> PlaylistSort.TRACK_ASC
    PlaylistSort.TRACK_ASC -> PlaylistSort.TRACK_DESC
    PlaylistSort.TRACK_DESC -> PlaylistSort.RECENTLY_ADDED
    PlaylistSort.RECENTLY_ADDED -> PlaylistSort.MANUAL
}

/** The rows the library page lists: a collection if one is open, otherwise the category. */
internal fun rebirthVisibleSongs(
    sorted: List<Song>,
    opened: RebirthCollection?,
    category: RebirthCategory,
    favoriteIds: Set<String>,
    query: String,
): List<Song> {
    val base = when {
        opened != null -> opened.songs
        category == RebirthCategory.FAVORITES -> sorted.filter { it.videoId in favoriteIds }
        else -> sorted
    }
    return if (query.isBlank()) base else base.filter { rebirthMatches(it, query) }
}

internal fun sortFavorites(songs: List<Song>, sort: FavoriteSort, addedAt: Map<String, Long> = emptyMap()): List<Song> {
    val collator = java.text.Collator.getInstance().apply { strength = java.text.Collator.PRIMARY }
    val byTitle = Comparator<Song> { a, b -> collator.compare(a.title, b.title) }
    return when (sort) {
        FavoriteSort.DATE_ADDED -> songs.sortedWith(
            compareByDescending<Song> { addedAt[it.videoId] ?: 0L }.thenComparing(byTitle),
        )
        FavoriteSort.TITLE -> songs.sortedWith(byTitle)
        FavoriteSort.ARTIST -> songs.sortedWith(
            Comparator<Song> { a, b -> collator.compare(a.artist, b.artist) }.thenComparing(byTitle),
        )
    }
}

/**
 * The favorites collection, assembled once for every theme.
 *
 * Its membership is the one shared heart store: a song is here because its
 * rating says LIKE, whether it is a device file, a saved catalog entry or a
 * track the account had already liked. Passing the device library as well as
 * the persisted entries is what stops the original library and the two Plus
 * libraries from showing three slightly different favorites lists — and the
 * sort is the same [FavoriteSort] everywhere, so the order matches too.
 *
 * Entries win over library rows for metadata because they keep the heart's
 * own copy of a song that may no longer be on the device.
 */
internal fun favoriteSongsInOrder(
    favoriteSongs: List<Song>,
    deviceSongs: List<Song>,
    favoriteIds: Set<String>,
    sort: FavoriteSort,
    addedAt: Map<String, Long> = emptyMap(),
): List<Song> {
    if (favoriteIds.isEmpty()) return emptyList()
    val known = LinkedHashMap<String, Song>()
    favoriteSongs.forEach { known[it.videoId] = it }
    deviceSongs.forEach { song ->
        if (song.videoId in favoriteIds) known.putIfAbsent(song.videoId, song)
    }
    return sortFavorites(
        known.values.filter { it.videoId in favoriteIds }.distinctBy { it.videoId },
        sort,
        addedAt,
    )
}

/** Albums, artists and saved playlists, grouped once per library change. */
internal fun rebirthCollections(
    songs: List<Song>,
    playlists: List<DevicePlaylist>,
    unknownAlbum: String = "Unknown album",
    unknownArtist: String = "Unknown artist",
    favorites: RebirthCollection? = null,
): Triple<List<RebirthCollection>, List<RebirthCollection>, List<RebirthCollection>> {
    val albums = songs
        .groupBy { it.albumName?.takeIf(String::isNotBlank) ?: unknownAlbum }
        .map { (name, tracks) -> RebirthCollection("album:$name", name, rebirthAlbumOrder(tracks)) }
        .sortedBy { it.title.lowercase(java.util.Locale.ROOT) }
    val artists = songs
        .groupBy { it.artist.ifBlank { unknownArtist } }
        .map { (name, tracks) -> RebirthCollection("artist:$name", name, tracks) }
        .sortedBy { it.title.lowercase(java.util.Locale.ROOT) }
    val saved = listOfNotNull(favorites) + playlists.map { playlist ->
        RebirthCollection(
            id = "playlist:${playlist.id}",
            title = playlist.name,
            songs = playlist.sortedSongs(),
            playlistId = playlist.id,
        )
    }
    return Triple(albums, artists, saved)
}

/** The two-column grid rows a collection page fills, with the odd one kept alone. */
internal fun rebirthRows(collections: List<RebirthCollection>, query: String): List<List<RebirthCollection>> =
    collections
        .filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
        .chunked(2)
