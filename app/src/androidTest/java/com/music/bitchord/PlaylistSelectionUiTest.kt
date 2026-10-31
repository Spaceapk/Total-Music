package com.music.bitchord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.data.DevicePlaylistStore
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.player.PlayerVisualStyle
import com.music.bitchord.ui.rebirth.RebirthDestination
import com.music.bitchord.ui.rebirth.RebirthLocalExperience
import com.music.bitchord.ui.screens.DevicePlaylistsTab
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class PlaylistSelectionUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun originalPlaylistCanRemoveAllSelected() = removeAll(null)
    @Test fun expressivePlaylistCanRemoveAllSelected() = removeAll(PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS)
    @Test fun glassPlaylistCanRemoveAllSelected() = removeAll(PlayerVisualStyle.LIQUID_GLASS_PLUS)

    private fun removeAll(style: PlayerVisualStyle?) {
        val store = DevicePlaylistStore.forDevice(context)
        val title = "Selection ${UUID.randomUUID()}"
        val songs = (1..3).map { Song("content://media/186/$it", "Track $it", "Artist", null, localUri = "content://media/186/$it") }
        store.createWithSongs(title, songs)
        val id = store.playlists.value.first { it.name == title }.id
        var played = 0
        try {
            compose.setContent {
                MaterialTheme {
                    if (style == null) DevicePlaylistsTab(songs, onPlay = { _, _ -> played++ }, padding = PaddingValues())
                    else RebirthLocalExperience(
                        style = style, songs = songs, currentSong = null, isPlaying = false, loading = false,
                        favoriteIds = emptySet(), initialQuery = "", destination = RebirthDestination.LIBRARY,
                        onPlay = { _, _ -> played++ }, onSongMenu = {}, onSettings = {},
                        onPlayPause = {}, onNext = {}, onExpandPlayer = {},
                    )
                }
            }
            if (style != null) compose.onNodeWithText(context.getString(R.string.playlists)).performScrollTo().performClick()
            compose.onNodeWithText(title).performScrollTo().performClick()
            compose.onNodeWithText("Track 1").performTouchInput { longClick() }
            // A normal tap now selects rather than starting playback.
            compose.onNodeWithText("Track 2").performClick()
            compose.runOnIdle { assertEquals(0, played) }
            compose.onNodeWithText(context.getString(R.string.select_all)).performClick()
            compose.onNodeWithContentDescription(context.getString(R.string.selection_actions)).performClick()
            compose.onNodeWithTag("playlist-remove-selected").performClick()
            compose.runOnIdle { assertEquals(3, store.playlists.value.first { it.id == id }.songs.size) }
            compose.onNodeWithText(context.getString(R.string.remove)).performClick()
            compose.waitUntil(5_000) { store.playlists.value.first { it.id == id }.songs.isEmpty() }
            compose.runOnIdle { assertEquals(0, played) }
        } finally { store.delete(id) }
    }
}
