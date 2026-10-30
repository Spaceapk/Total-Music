package com.music.bitchord

import com.music.bitchord.data.DevicePlaylist
import com.music.bitchord.data.PlaylistSort
import com.music.bitchord.data.movedPlaylistOrder
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class PlaylistSortTest {
    private fun song(
        id: String,
        title: String,
        artist: String,
        album: String?,
        track: Int? = null,
        disc: Int? = null,
    ) = Song(
        id,
        title,
        artist,
        null,
        albumName = album,
        localUri = "file:///$id",
        localTrackNumber = track,
        localDiscNumber = disc,
    )

    private val z = song("z", "Zulu", "Alpha", "Uno", 2)
    private val a = song("a", "Álbum", "Zulu", "Dos", 1)
    private val b = song("b", "beta", "Alpha", "Uno", 1)
    private val m = song("m", "Marea", "Alpha", "Tres", 1)

    private fun ids(sort: PlaylistSort) = DevicePlaylist(
        "id", "Test", listOf(z, a, b, m), sort = sort,
    ).sortedSongs().map { it.videoId }

    @Test fun titleArtistRecentAndManualOrdersAreIndependent() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("es"))
            assertEquals(listOf("a", "b", "m", "z"), ids(PlaylistSort.TITLE_ASC))
            assertEquals(listOf("z", "m", "b", "a"), ids(PlaylistSort.TITLE_DESC))
            assertEquals(listOf("m", "b", "z", "a"), ids(PlaylistSort.ARTIST_ASC))
            assertEquals(listOf("z", "a", "b", "m"), ids(PlaylistSort.MANUAL))
        } finally { Locale.setDefault(original) }
    }

    @Test fun artistOrderHasBothDirectionsAndKeepsAlbumsInsideAnArtistAscending() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("es"))
            assertEquals(listOf("m", "b", "z", "a"), ids(PlaylistSort.ARTIST_ASC))
            // Alpha's records stay in album order even though the artist groups
            // themselves run backwards; Zulu (a) comes first.
            assertEquals(listOf("a", "m", "b", "z"), ids(PlaylistSort.ARTIST_DESC))
        } finally { Locale.setDefault(original) }
    }

    @Test fun albumOrderHasBothDirectionsAndKeepsTracksAscending() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("es"))
            assertEquals(listOf("a", "m", "b", "z"), ids(PlaylistSort.ALBUM_ASC))
            // "Uno" first, but still its track 1 (b) before track 2 (z).
            assertEquals(listOf("b", "z", "m", "a"), ids(PlaylistSort.ALBUM_DESC))
        } finally { Locale.setDefault(original) }
    }

    @Test fun albumOrderUsesDiscsThenTrackNumbersAndPutsUntaggedTracksLast() {
        val discTwo = song("d2", "Segunda", "Alpha", "Set", 1, 2)
        val discOne = song("d1", "Primera", "Alpha", "Set", 2, 1)
        val untagged = song("x", "Sin pista", "Alpha", "Set")
        val ordered = DevicePlaylist(
            "id", "Set", listOf(discTwo, untagged, discOne), sort = PlaylistSort.ALBUM_ASC,
        ).sortedSongs().map { it.videoId }
        assertEquals(listOf("d1", "d2", "x"), ordered)
    }

    @Test fun recentlyAddedStillUsesMembershipOrder() {
        val playlist = DevicePlaylist(
            "id", "Recent", listOf(z, a, b), addedUris = listOf("file:///a", "file:///z", "file:///b"),
            sort = PlaylistSort.RECENTLY_ADDED,
        )
        assertEquals(listOf("b", "z", "a"), playlist.sortedSongs().map { it.videoId })
    }

    @Test fun legacyArtistNameMigratesToAscendingInsteadOfResetting() {
        assertEquals(PlaylistSort.ARTIST_ASC, PlaylistSort.fromPersisted("ARTIST"))
        assertEquals(PlaylistSort.ARTIST_DESC, PlaylistSort.fromPersisted("ARTIST_DESC"))
        assertEquals(PlaylistSort.ALBUM_ASC, PlaylistSort.fromPersisted("ALBUM_ASC"))
        assertEquals(PlaylistSort.MANUAL, PlaylistSort.fromPersisted("something removed"))
        assertEquals(PlaylistSort.MANUAL, PlaylistSort.fromPersisted(null))
    }

    @Test fun trackNumberOrderIsNumericAcrossDiscsAndPutsUntaggedTracksLast() {
        val ten = song("t10", "Diez", "Artist", "Set", 10)
        val two = song("t2", "Dos", "Artist", "Set", 2)
        val one = song("t1", "Uno", "Artist", "Set", 1)
        val discTwo = song("d2", "Disco dos", "Artist", "Set", 1, 2)
        val untagged = song("x", "Sin pista", "Artist", "Set")
        val playlist = DevicePlaylist(
            "id", "Set", listOf(ten, untagged, discTwo, two, one), sort = PlaylistSort.TRACK_ASC,
        )
        // Numeric, not textual: 2 precedes 10. Disc two comes after disc one.
        assertEquals(
            listOf("t1", "t2", "t10", "d2", "x"),
            playlist.sortedSongs().map { it.videoId },
        )
        // Descending still leaves the untagged track at the end.
        val descending = playlist.copy(sort = PlaylistSort.TRACK_DESC).sortedSongs().map { it.videoId }
        assertEquals("x", descending.last())
        assertEquals(listOf("d2", "t10", "t2", "t1", "x"), descending)
    }

    @Test fun albumOrderKeepsHomonymousAlbumsOfDifferentArtistsApart() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("es"))
            // Two different records with the same name. A comparator that only
            // looked at the album name would interleave these tracks.
            val alphaTwo = song("a2", "Alpha dos", "Alpha", "Greatest Hits", 2)
            val alphaOne = song("a1", "Alpha uno", "Alpha", "Greatest Hits", 1)
            val zuluTwo = song("z2", "Zulu dos", "Zulu", "Greatest Hits", 2)
            val zuluOne = song("z1", "Zulu uno", "Zulu", "Greatest Hits", 1)
            val playlist = DevicePlaylist(
                "id", "Hits",
                listOf(zuluTwo, alphaTwo, zuluOne, alphaOne),
                sort = PlaylistSort.ALBUM_ASC,
            )
            assertEquals(
                listOf("a1", "a2", "z1", "z2"),
                playlist.sortedSongs().map { it.videoId },
            )
        } finally { Locale.setDefault(original) }
    }

    @Test fun artistOrderGoesArtistThenAlbumThenDiscAndTrack() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("es"))
            val secondAlbum = song("b", "Primera del segundo", "Alpha", "Zeta", 1)
            val firstDisc = song("c", "Segunda del primero", "Alpha", "Alfa", 2, 1)
            val secondDisc = song("d", "Primera del disco dos", "Alpha", "Alfa", 1, 2)
            val otherArtist = song("a", "Otra", "Beta", "Alfa", 1)
            val playlist = DevicePlaylist(
                "id", "Artist",
                listOf(otherArtist, secondAlbum, secondDisc, firstDisc),
                sort = PlaylistSort.ARTIST_ASC,
            )
            assertEquals(
                listOf("c", "d", "b", "a"),
                playlist.sortedSongs().map { it.videoId },
            )
        } finally { Locale.setDefault(original) }
    }

    @Test fun movingARowRejectsStaleOrAmbiguousRequests() {
        assertEquals(
            listOf("b", "c", "a"),
            movedPlaylistOrder(listOf("a", "b", "c"), 0, 2),
        )
        assertEquals(
            listOf("c", "a", "b"),
            movedPlaylistOrder(listOf("a", "b", "c"), 2, 0),
        )
        assertEquals(null, movedPlaylistOrder(listOf("a", "b"), 0, 0))
        assertEquals(null, movedPlaylistOrder(listOf("a", "b"), -1, 0))
        assertEquals(null, movedPlaylistOrder(listOf("a", "b"), 0, 5))
        assertEquals(null, movedPlaylistOrder(listOf("a", "a"), 0, 1))
    }

    @Test fun largePlaylistSortsEveryTrackAndKeepsTheManualOrderIntact() {
        val songs = (0 until 500).map { index ->
            val track = (index * 7) % 250 + 1
            song("s$index", "Song ${(index * 37) % 500}", "Artist ${(index * 11) % 20}", "Album ${(index * 13) % 40}", track, (index % 3) + 1)
        }
        val playlist = DevicePlaylist("big", "Big", songs, sort = PlaylistSort.TRACK_ASC)
        val sorted = playlist.sortedSongs()
        assertEquals(500, sorted.size)
        assertEquals(500, sorted.map { it.videoId }.toSet().size)
        // Every disc and track pair is non-decreasing.
        val keys = sorted.map { (it.localDiscNumber ?: 1) to (it.localTrackNumber ?: Int.MAX_VALUE) }
        assertEquals(keys.sortedWith(compareBy({ it.first }, { it.second })), keys)
        // Sorting never rewrites the manual order.
        assertEquals(songs.map { it.videoId }, playlist.songs.map { it.videoId })
    }

    @Test fun newSortNamesPersistAndUnknownNamesStillFallBackToManual() {
        assertEquals(PlaylistSort.TRACK_ASC, PlaylistSort.fromPersisted("TRACK_ASC"))
        assertEquals(PlaylistSort.TRACK_DESC, PlaylistSort.fromPersisted("TRACK_DESC"))
        assertEquals(PlaylistSort.RECENTLY_ADDED, PlaylistSort.fromPersisted("RECENTLY_ADDED"))
    }
}
