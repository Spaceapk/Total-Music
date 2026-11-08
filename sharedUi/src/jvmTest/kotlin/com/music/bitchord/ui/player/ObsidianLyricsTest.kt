package com.music.bitchord.ui.player

import com.music.bitchord.data.lyrics.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Test

class ObsidianLyricsTest {
    private val lines = listOf(
        LyricLine(0, "Intro"), LyricLine(1000, "First"), LyricLine(2500, "Second"), LyricLine(-1, "Untimed"),
    )
    @Test fun activeLineIsTheLastTimestampReached() {
        assertEquals(-1, obsidianActiveLyricIndex(lines, -1))
        assertEquals(0, obsidianActiveLyricIndex(lines, 0))
        assertEquals(0, obsidianActiveLyricIndex(lines, 999))
        assertEquals(1, obsidianActiveLyricIndex(lines, 1000))
        assertEquals(2, obsidianActiveLyricIndex(lines, 9000))
    }
    @Test fun obsidianStyleRoundTrips() {
        assertEquals(PlayerVisualStyle.OBSIDIAN, PlayerVisualStyle.fromStorage("OBSIDIAN"))
    }
}
