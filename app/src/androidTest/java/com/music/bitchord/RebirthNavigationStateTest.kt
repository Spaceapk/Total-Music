package com.music.bitchord

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.player.PlayerVisualStyle
import com.music.bitchord.ui.rebirth.RebirthDestination
import com.music.bitchord.ui.rebirth.RebirthLocalExperience
import org.junit.Rule
import org.junit.Test

class RebirthNavigationStateTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun expressiveCompactRestoresLibraryDestination() = restore(PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS, 320.dp)
    @Test fun glassCompactRestoresLibraryDestination() = restore(PlayerVisualStyle.LIQUID_GLASS_PLUS, 320.dp)
    @Test fun glassWideRestoresLibraryDestination() = restore(PlayerVisualStyle.LIQUID_GLASS_PLUS, 600.dp)

    private fun restore(style: PlayerVisualStyle, width: Dp) {
        val settingsOpen = mutableStateOf(false)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val holder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
            var destination by rememberSaveable { mutableStateOf(RebirthDestination.HOME) }
            MaterialTheme {
                Box(Modifier.requiredWidth(width).fillMaxHeight()) {
                    if (!settingsOpen.value) holder.SaveableStateProvider("rebirth-library") {
                    RebirthLocalExperience(
                        style = style, songs = emptyList(), currentSong = null,
                        isPlaying = false, loading = false, favoriteIds = emptySet(), initialQuery = "",
                        onPlay = { _, _ -> }, onSongMenu = {}, onSettings = { settingsOpen.value = true },
                        onPlayPause = {}, onNext = {}, onExpandPlayer = {},
                        destination = destination, onDestinationChange = { destination = it },
                    )
                    }
                }
            }
        }
        val library = compose.activity.getString(R.string.library)
        compose.onNodeWithContentDescription(library).performClick()
        compose.onNodeWithContentDescription(library).assertIsSelected()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.settings)).performClick()
        compose.runOnUiThread { settingsOpen.value = false }
        compose.onNodeWithContentDescription(library).assertIsSelected()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription(library).assertIsSelected()
        val search = compose.activity.getString(R.string.search)
        compose.onNodeWithContentDescription(search).performClick()
        compose.onNodeWithContentDescription(search).assertIsSelected()
    }
}
