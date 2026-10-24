package com.music.bitchord.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AlbumOrderingTest {
    private fun song(id: String, track: Int?, disc: Int? = null) =
        Song(id, id, "Artist", null, localTrackNumber = track, localDiscNumber = disc)

    @Test fun parsesPaddedAndTotalTagsAndRejectsInvalidTags() {
        listOf("1", "01", "1/12", " 01 / 12 ").forEach { assertEquals(1, parseTrackTag(it)) }
        listOf(null, "", "0", "-1", "abc", "99999999999999999999").forEach { assertNull(parseTrackTag(it)) }
    }
    @Test fun albumUsesDiscThenTrackAndKeepsUntaggedLast() {
        val tracks = listOf(song("missing", null), song("disc2track1", 1, 2), song("track10", 10), song("track2", 2), song("track1", 1))
        assertEquals(listOf("track1", "track2", "track10", "disc2track1", "missing"), tracks.inAlbumOrder().map { it.videoId })
        assertEquals("missing", tracks.first().videoId) // input stays untouched
    }
    @Test fun tiesAndMissingTagsKeepInputOrder() {
        val tracks = listOf(song("b", null), song("c", 1), song("a", null), song("d", 1))
        assertEquals(listOf("c", "d", "b", "a"), tracks.inAlbumOrder().map { it.videoId })
    }
    @Test fun invalidNumericTagsCannotPrecedeNumberedTracks() {
        val tracks = listOf(song("zero", 0), song("negative", -4), song("valid", 1))
        assertEquals(listOf("valid", "zero", "negative"), tracks.inAlbumOrder().map { it.videoId })
    }
}
