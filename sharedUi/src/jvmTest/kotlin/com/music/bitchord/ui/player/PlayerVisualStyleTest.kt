package com.music.bitchord.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerVisualStyleTest {
    @Test
    fun storedStylesRoundTripAndUnknownValuesKeepTheLegacyPlayer() {
        PlayerVisualStyle.entries.forEach { style ->
            assertEquals(style, PlayerVisualStyle.fromStorage(style.name))
        }
        assertEquals(PlayerVisualStyle.ORIGINAL, PlayerVisualStyle.fromStorage(null))
        assertEquals(PlayerVisualStyle.ORIGINAL, PlayerVisualStyle.fromStorage("unknown"))
    }
}
