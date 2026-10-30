package com.music.bitchord.ui.rebirth

import com.music.bitchord.ui.player.PlayerVisualStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceThemeTest {
    @Test fun allFourChoicesRoundTripExistingPreferences() {
        AppearanceTheme.entries.forEach { theme ->
            assertEquals(theme, AppearanceTheme.current(theme.style, theme.expressive))
        }
    }
    @Test fun plusStylesWinOverLegacyExpressivePreference() {
        assertEquals(AppearanceTheme.GLASS_PLUS, AppearanceTheme.current(PlayerVisualStyle.LIQUID_GLASS_PLUS, true))
        assertEquals(AppearanceTheme.GOOGLE_PLUS, AppearanceTheme.current(PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS, false))
    }
}
