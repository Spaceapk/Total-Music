package com.music.bitchord

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.TotalMusicSettings
import com.music.bitchord.ui.player.PlayerVisualStyle
import com.music.bitchord.ui.rebirth.AppearanceThemePicker
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppearanceThemeUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun fourCardsApplyExistingPreferencesImmediately() {
        val oldStyle = AppSettings.playerVisualStyle.value
        val oldExpressive = TotalMusicSettings.expressive.value
        try {
            compose.setContent { MaterialTheme { AppearanceThemePicker() } }
            listOf(
                Triple("Liquid Glass+", PlayerVisualStyle.LIQUID_GLASS_PLUS, false),
                Triple("Google Expressive+", PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS, true),
                Triple("Google Expressive", PlayerVisualStyle.ORIGINAL, true),
                Triple("Liquid Glass", PlayerVisualStyle.ORIGINAL, false),
            ).forEach { (label, style, expressive) ->
                compose.onNodeWithText(label).performClick()
                compose.runOnIdle {
                    assertEquals(style, AppSettings.playerVisualStyle.value)
                    assertEquals(expressive, TotalMusicSettings.expressive.value)
                    val prefs = compose.activity.getSharedPreferences("bitchord_settings", 0)
                    assertEquals(style.name, prefs.getString("player_visual_style", null))
                    assertEquals(expressive, prefs.getBoolean("google_expressive", !expressive))
                }
            }
        } finally {
            compose.runOnIdle {
                TotalMusicSettings.setExpressive(oldExpressive)
                AppSettings.setPlayerVisualStyle(oldStyle)
            }
        }
    }
}
