package com.music.bitchord.ui.player

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.music.bitchord.data.lyrics.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GooglePlusLyricsTest {
    private val lines = listOf(LyricLine(1000, "One"), LyricLine(2500, "Two"), LyricLine(-1, "Static"))
    @Test fun boundariesAndBackwardSeeks() {
        assertEquals(-1, googlePlusActiveLyricIndex(lines, 999))
        assertEquals(0, googlePlusActiveLyricIndex(lines, 1000))
        assertEquals(0, googlePlusActiveLyricIndex(lines, 2499))
        assertEquals(1, googlePlusActiveLyricIndex(lines, 2500))
        assertEquals(1, googlePlusActiveLyricIndex(lines, 9000))
        assertEquals(0, googlePlusActiveLyricIndex(lines, 1200))
    }
    @Test fun staticEmptyAndDuplicateTimestamps() {
        assertEquals(-1, googlePlusActiveLyricIndex(emptyList(), 5000))
        assertEquals(-1, googlePlusActiveLyricIndex(listOf(LyricLine(-1, "Static")), 5000))
        assertEquals(1, googlePlusActiveLyricIndex(listOf(LyricLine(0, "A"), LyricLine(0, "B")), 0))
    }
    @Test fun artworkPaletteSurvivesPlusPresentation() {
        val artwork = lightColorScheme(background = Color(0xFFFFDDB5), surface = Color(0xFFFFDDB5))
        assertSame(artwork, rebirthColors(PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS, artwork))
    }
}
