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
    @Test fun gapsCanBeActiveSoNoStaleLineStaysLit() {
        val withGap = listOf(LyricLine(0, "A"), LyricLine(2000, ""), LyricLine(4000, "B"))
        assertEquals(1, googlePlusActiveLyricIndex(withGap, 2500))
        assertEquals(true, withGap[1].isGap)
    }
    @Test fun bothModeNeverRepeatsTheSameWords() {
        val original = LyricLine(0, "Hello world")
        assertEquals("Hola mundo", googlePlusSubLine(original, listOf(LyricLine(0, " Hola mundo ")), 0))
        assertEquals(null, googlePlusSubLine(original, listOf(LyricLine(0, "hello World")), 0))
        assertEquals(null, googlePlusSubLine(original, listOf(LyricLine(0, "")), 0))
        assertEquals(null, googlePlusSubLine(original, null, 0))
        assertEquals(null, googlePlusSubLine(original, emptyList(), 3))
    }
    @Test fun qualityTextUsesOnlyMeasuredValues() {
        assertEquals("FLAC · 44.1 kHz · 16 bit", googlePlusQualityText("audio/flac", 900, 44100, 16))
        assertEquals("FLAC · 96 kHz · 24 bit", googlePlusQualityText("audio/flac", null, 96000, 24))
        assertEquals("MP3 · 44.1 kHz · 192 kbps", googlePlusQualityText("audio/mpeg", 192, 44100, null))
        assertEquals("AAC · 48 kHz", googlePlusQualityText("audio/mp4a-latm", null, 48000, null))
        assertEquals("Opus", googlePlusQualityText("audio/opus", 0, 0, 0))
        assertEquals(null, googlePlusQualityText(null, null, null, null))
        assertEquals("320 kbps", googlePlusQualityText(null, 320, null, null))
    }
}
