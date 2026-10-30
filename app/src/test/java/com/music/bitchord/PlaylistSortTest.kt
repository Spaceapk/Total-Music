package com.music.bitchord

import com.music.bitchord.data.DevicePlaylist
import com.music.bitchord.data.PlaylistSort
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
}
