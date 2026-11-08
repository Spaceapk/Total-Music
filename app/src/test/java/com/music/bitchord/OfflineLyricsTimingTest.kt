package com.music.bitchord

import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricWord
import com.music.bitchord.data.lyrics.rebuildOfflineTranslation
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineLyricsTimingTest {
    @Test fun translationKeepsTimestampsAndOriginalVocalEnvelope() {
        val original = listOf(
            LyricLine(1000, "Hello", words = listOf(LyricWord(1000, 1400, "Hello"))),
            LyricLine(2500, "World"),
        )
        val translated = rebuildOfflineTranslation(original, listOf("Hola", "Mundo"))
        assertEquals(listOf(1000L, 2500L), translated.map { it.timeMs })
        assertEquals("Hola", translated[0].text)
        assertEquals(original[0], translated[0].timingSource)
        assertEquals(1000L, translated[0].words.single().startMs)
        assertEquals(1400L, translated[0].words.single().endMs)
        assertEquals(emptyList<LyricWord>(), translated[1].words)
    }
}
