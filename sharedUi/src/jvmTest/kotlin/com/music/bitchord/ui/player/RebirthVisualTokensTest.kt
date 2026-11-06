package com.music.bitchord.ui.player

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression test for the visual contract the two rebirth player scenes draw from. */
class RebirthVisualTokensTest {

    private val light = lightColorScheme()
    private val dark = darkColorScheme()

    @Test
    fun expressivePlusKeepsTheBasePaletteOnDark() {
        val colors = rebirthColors(PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS, dark)
        assertEquals(Color.Black, colors.background)
        assertEquals(Color.Black, colors.surface)
    }

    @Test
    fun glassPlusDarkIsFrostedDark() {
        val colors = rebirthColors(PlayerVisualStyle.LIQUID_GLASS_PLUS, dark)
        assertEquals(Color.Black, colors.background)
        assertEquals(Color(0xFF101D2C.toInt()), colors.surface)
        assertEquals(Color(0xFFF0F5FF.toInt()), colors.onSurface)
        assertTrue(colors.isDarkPlayerScheme())
    }

    @Test
    fun glassPlusLightIsPale() {
        val colors = rebirthColors(PlayerVisualStyle.LIQUID_GLASS_PLUS, light)
        assertEquals(Color(0xFFF2F8FC.toInt()), colors.background)
        assertEquals(Color(0xFFF8FCFF.toInt()), colors.surface)
        assertFalse(colors.isDarkPlayerScheme())
    }

    @Test
    fun originalPassesTheSchemeThrough() {
        assertTrue(rebirthColors(PlayerVisualStyle.ORIGINAL, light) === light)
        assertTrue(rebirthColors(PlayerVisualStyle.ORIGINAL, dark) === dark)
    }
}
