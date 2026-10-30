package com.music.bitchord.ui.rebirth

import com.music.bitchord.data.DevicePlaylist
import com.music.bitchord.data.PlaylistSort
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.FavoriteSort
import com.music.bitchord.data.settings.LocalMusicSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The library's decisions, without a screen.
 *
 * Everything asserted here is a pure function in RebirthLibraryModel.kt, which
 * is the point of that file: the parts of the rebirth library that can be
 * wrong without looking wrong — an order, a filter, a selection that outlives
 * the query it was made under — are testable on the JVM, and the parts that
 * can only be judged by eye are not pretended to be tested here.
 */
class RebirthLibraryTest {

    private fun song(
        id: String,
        track: Int? = null,
        disc: Int? = null,
        artist: String = "Horizonte",
        album: String? = "Luz de octubre",
        added: Long? = null,
    ) = Song(
        videoId = id,
        title = id,
        artist = artist,
        thumbnailUrl = null,
        albumName = album,
        localTrackNumber = track,
        localDiscNumber = disc,
        localDateAddedSeconds = added,
    )

    /**
     * The album rule is the app's own, not this screen's: `AlbumOrdering`
     * already says what a missing disc number means (disc one) and where a
     * track without a number goes (last). The rebirth library delegates to it,
     * so an album cannot be listed one way here and another way there — and
     * this test pins the shared answer rather than a second opinion about it.
     */
    @Test
    fun albumOrderUsesTheSharedRuleAcrossDiscs() {
        val result = rebirthAlbumOrder(
            listOf(song("sin etiqueta"), song("dos", 2), song("disco dos", 1, 2), song("uno", 1)),
        )
        assertEquals(listOf("uno", "dos", "disco dos", "sin etiqueta"), result.map { it.videoId })
    }

    @Test
    fun albumOrderTreatsAMissingDiscAsDiscOne() {
        val result = rebirthAlbumOrder(listOf(song("segundo", 1, 2), song("sin disco", 3)))
        assertEquals(listOf("sin disco", "segundo"), result.map { it.videoId })
    }

    @Test
    fun searchMatchesMetadataWithoutCaseOrOuterWhitespace() {
        val subject = song("Primera luz", 1)
        assertTrue(rebirthMatches(subject, " HORIZONTE "))
        assertTrue(rebirthMatches(subject, "octubre"))
        assertTrue(rebirthMatches(subject, ""))
        assertFalse(rebirthMatches(subject, "otra canción"))
    }

    @Test
    fun trackNumberOrderIsTheAlbumOrderAndNotTheScanOrder() {
        val scanned = listOf(song("c", 3), song("a", 1), song("b", 2))
        assertEquals(
            listOf("a", "b", "c"),
            rebirthSortedLibrary(scanned, LocalMusicSort.TRACK_NUMBER).map { it.videoId },
        )
    }

    @Test
    fun dateAddedSortPutsTheNewestFirstAndUndatedLast() {
        val library = listOf(
            song("old", added = 10),
            song("undated"),
            song("new", added = 30),
        )
        assertEquals(
            listOf("new", "old", "undated"),
            rebirthSortedLibrary(library, LocalMusicSort.DATE_ADDED).map { it.videoId },
        )
    }

    @Test
    fun aSelectionOnlySurvivesWhileItsRowsAreVisible() {
        val selection = linkedSetOf("a", "b", "c")
        val visible = listOf(song("b"), song("c"))
        assertEquals(setOf("b", "c"), rebirthFilteredSelection(selection, visible))
        // Clearing the query does not resurrect what the query hid: the caller
        // re-derives from the *visible* rows, so the dropped id never returns.
        assertEquals(
            setOf("b", "c"),
            rebirthFilteredSelection(rebirthFilteredSelection(selection, visible), visible),
        )
    }

    @Test
    fun aFilteredPlaylistCannotBeReorderedButAnySortCan() {
        assertTrue(rebirthCanReorder(""))
        assertFalse(rebirthCanReorder("luz"))
        // A sorted playlist is still reorderable: the drop stores the rows as
        // arranged and switches the playlist to manual. Only a filter is
        // refused, because it is not the playlist's own running order.
        assertTrue(rebirthCanReorder(""))
    }

    @Test
    fun theSortCycleReturnsToManual() {
        var sort = PlaylistSort.MANUAL
        repeat(PlaylistSort.entries.size) { sort = nextPlaylistSort(sort) }
        assertEquals(PlaylistSort.MANUAL, sort)
        // Every order is reachable, so no entry is dead.
        val seen = mutableSetOf<PlaylistSort>()
        var current = PlaylistSort.MANUAL
        repeat(PlaylistSort.entries.size - 1) {
            current = nextPlaylistSort(current)
            seen += current
        }
        assertEquals(PlaylistSort.entries.toSet() - PlaylistSort.MANUAL, seen)
    }

    @Test
    fun favoritesAreTheOnlyCategoryThatFiltersWithoutAQuery() {
        val library = listOf(song("liked"), song("not liked"))
        val visible = rebirthVisibleSongs(
            sorted = library,
            opened = null,
            category = RebirthCategory.FAVORITES,
            favoriteIds = setOf("liked"),
            query = "",
        )
        assertEquals(listOf("liked"), visible.map { it.videoId })
    }

    @Test
    fun anOpenCollectionIgnoresTheCategoryAndKeepsTheQuery() {
        val opened = RebirthCollection("playlist:1", "Cocina", listOf(song("sopa"), song("café")), playlistId = "1")
        assertEquals(
            listOf("sopa"),
            rebirthVisibleSongs(
                sorted = emptyList(),
                opened = opened,
                category = RebirthCategory.SONGS,
                favoriteIds = emptySet(),
                query = "sop",
            ).map { it.videoId },
        )
    }

    @Test
    fun theVirtualFavoritesLeadTheSavedCollectionsAndAreNotAPlaylist() {
        val favorites = rebirthFavoriteCollection(listOf(song("liked")), "Mis favoritas")
        val playlist = DevicePlaylist(id = "p1", name = "Camino", songs = listOf(song("saved")))
        val (_, _, saved) = rebirthCollections(
            songs = emptyList(),
            playlists = listOf(playlist),
            favorites = favorites,
        )
        assertEquals(listOf(FAVORITES_COLLECTION_ID, "playlist:p1"), saved.map { it.id })
        assertTrue(saved.first().isFavorites)
        assertFalse(saved.first().isPlaylist)
        assertFalse(saved.first().canBeRenamed)
        // The virtual collection is present even when empty, so its page stays
        // open while the last heart is removed on it.
        val (_, _, empty) = rebirthCollections(
            songs = emptyList(),
            playlists = emptyList(),
            favorites = rebirthFavoriteCollection(emptyList(), "Mis favoritas"),
        )
        assertEquals(listOf(FAVORITES_COLLECTION_ID), empty.map { it.id })
    }

    @Test
    fun savedPlaylistsBecomeCollectionsSortedByTheirOwnOrder() {
        val playlist = DevicePlaylist(
            id = "p1",
            name = "Camino",
            songs = listOf(song("z", added = 1), song("a", added = 2)),
            sort = PlaylistSort.TITLE_ASC,
        )
        val (albums, artists, saved) = rebirthCollections(
            songs = listOf(song("x", artist = "Otra")),
            playlists = listOf(playlist),
            unknownAlbum = "Sin álbum",
            unknownArtist = "Artista desconocido",
        )
        assertEquals(listOf("Luz de octubre"), albums.map { it.title })
        assertEquals(listOf("Otra"), artists.map { it.title })
        assertEquals(listOf("a", "z"), saved.single().songs.map { it.videoId })
        assertEquals("p1", saved.single().playlistId)
    }

    @Test
    fun theGalleryDropsNonMatchingRowsAndKeepsPairs() {
        val collections = listOf(
            RebirthCollection("album:a", "Aurora", emptyList()),
            RebirthCollection("album:b", "Bruma", emptyList()),
            RebirthCollection("album:c", "Cielo", emptyList()),
        )
        assertEquals(2, rebirthRows(collections, "").size)
        assertEquals(1, rebirthRows(collections, "").last().size)
        assertEquals(listOf("Bruma"), rebirthRows(collections, "bru").single().map { it.title })
    }

    @Test
    fun theFavoritesCollectionMergesEveryHeartOnceAndUsesTheSharedOrder() {
        val library = listOf(
            song("library-only", artist = "Beta"),
            song("both", artist = "Alpha"),
            song("not liked", artist = "Alpha"),
        )
        val savedEntry = song("entry-only", artist = "Gamma")
        val favorites = favoriteSongsInOrder(
            favoriteSongs = listOf(savedEntry, library[1]),
            deviceSongs = library,
            favoriteIds = setOf("library-only", "both", "entry-only"),
            sort = FavoriteSort.TITLE,
            addedAt = emptyMap(),
        )
        // The device track the entries did not carry is included, the track
        // without a heart is not, and no song appears twice.
        assertEquals(
            listOf("both", "entry-only", "library-only"),
            favorites.map { it.videoId },
        )
        assertEquals(3, favorites.map { it.videoId }.toSet().size)
        // Removing the heart anywhere removes it from this same list.
        assertEquals(
            listOf("both", "library-only"),
            favoriteSongsInOrder(
                favoriteSongs = listOf(savedEntry, library[1]),
                deviceSongs = library,
                favoriteIds = setOf("library-only", "both"),
                sort = FavoriteSort.TITLE,
            ).map { it.videoId },
        )
        assertEquals(
            emptyList<String>(),
            favoriteSongsInOrder(emptyList(), library, emptySet(), FavoriteSort.DATE_ADDED)
                .map { it.videoId },
        )
    }

    @Test
    fun favoritesByArtistUseTheSameArtistThenAlbumRuleOnEveryTheme() {
        val songs = listOf(
            song("z", artist = "Zulu") ,
            song("a", artist = "Alpha"),
            song("b", artist = "Alpha"),
        )
        val ordered = favoriteSongsInOrder(
            favoriteSongs = emptyList(),
            deviceSongs = songs,
            favoriteIds = setOf("a", "b", "z"),
            sort = FavoriteSort.ARTIST,
        )
        assertEquals(listOf("a", "b", "z"), ordered.map { it.videoId })
    }
}
