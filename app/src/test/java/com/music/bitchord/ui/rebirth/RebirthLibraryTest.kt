package com.music.bitchord.ui.rebirth

import com.music.bitchord.data.DevicePlaylist
import com.music.bitchord.data.PlaylistSort
import com.music.bitchord.data.model.Song
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

    @Test
    fun albumOrderKeepsDiscsAndPlacesMissingTagsLast() {
        val result = rebirthAlbumOrder(
            listOf(song("sin etiqueta"), song("dos", 2), song("disco dos", 1, 2), song("uno", 1)),
        )
        assertEquals(listOf("uno", "dos", "sin etiqueta", "disco dos"), result.map { it.videoId })
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
    fun aFilteredPlaylistCannotBeReordered() {
        assertTrue(rebirthCanReorder("", PlaylistSort.MANUAL))
        assertFalse(rebirthCanReorder("luz", PlaylistSort.MANUAL))
        assertFalse(rebirthCanReorder("", PlaylistSort.TITLE_ASC))
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
}
