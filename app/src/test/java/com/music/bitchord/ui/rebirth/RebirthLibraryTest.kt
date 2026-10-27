package com.music.bitchord.ui.rebirth

import com.music.bitchord.data.model.Song
import org.junit.Assert.*
import org.junit.Test

class RebirthLibraryTest {
    private fun song(id: String, track: Int?, disc: Int? = null) = Song(videoId = id, title = id, artist = "Horizonte", thumbnailUrl = null,
        albumName = "Luz de octubre", localTrackNumber = track, localDiscNumber = disc)
    @Test fun albumOrderKeepsDiscsAndPlacesMissingTagsLast() {
        val result = rebirthAlbumOrder(listOf(song("sin etiqueta", null), song("dos", 2), song("disco dos", 1, 2), song("uno", 1)))
        assertEquals(listOf("uno", "dos", "sin etiqueta", "disco dos"), result.map { it.videoId })
    }
    @Test fun searchMatchesMetadataWithoutCaseOrOuterWhitespace() {
        val song = song("Primera luz", 1)
        assertTrue(rebirthMatches(song, " HORIZONTE ")); assertTrue(rebirthMatches(song, "octubre"))
        assertTrue(rebirthMatches(song, "")); assertFalse(rebirthMatches(song, "otra canción"))
    }
}
