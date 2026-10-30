package com.music.bitchord

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithTag
import com.music.bitchord.data.DeviceFavoriteStore
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.FavoriteSort
import com.music.bitchord.ui.player.PlayerVisualStyle
import com.music.bitchord.ui.rebirth.RebirthDestination
import com.music.bitchord.ui.rebirth.RebirthLocalExperience
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FavoriteCatalogUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sortAndRemoveHeartUseTheSharedStore() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val filename = "test-183-catalog"
        val oldSort = AppSettings.favoriteSort.value
        val songs = listOf(
            Song("content://media/183/a", "Zebra", "Alpha", null, durationText = "2:00", localUri = "content://media/183/a"),
            Song("content://media/183/b", "Apple", "Beta", null, durationText = "3:00", localUri = "content://media/183/b"),
        )
        try {
            DeviceFavoriteStore.initialize(context, filename)
            songs.forEach { LikeState.set(it.videoId, LikeStatus.LIKE, it) }
            compose.setContent {
                val ratings by LikeState.overrides.collectAsState()
                MaterialTheme {
                    RebirthLocalExperience(
                        style = PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS, songs = songs,
                        favoriteSongs = songs, favoriteIds = ratings.filterValues { it == LikeStatus.LIKE }.keys,
                        currentSong = null, isPlaying = false, loading = false, initialQuery = "",
                        destination = RebirthDestination.LIBRARY,
                        onPlay = { _, _ -> }, onSongMenu = {}, onSettings = {}, onPlayPause = {},
                        onNext = {}, onExpandPlayer = {},
                    )
                }
            }
            compose.onNodeWithText(context.getString(R.string.rebirth_favorites)).performScrollTo().performClick()
            compose.onNodeWithText("Apple").assertExists()
            compose.onNodeWithText("Zebra").assertExists()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("favorites-sort").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("favorites-sort").performClick()
            compose.onNodeWithText(context.getString(R.string.sort_title_ascending)).performClick()
            assertEquals(FavoriteSort.TITLE, AppSettings.favoriteSort.value)
            compose.onNodeWithText("Apple").assertExists()
            // Title ascending puts Apple first and Zebra second; remove Zebra's heart.
            compose.onAllNodesWithContentDescription(context.getString(R.string.remove_from_liked), useUnmergedTree = true)[1].performClick()
            compose.runOnIdle {
                assertEquals(LikeStatus.INDIFFERENT, LikeState.overrides.value[songs[0].videoId])
                assertEquals(LikeStatus.LIKE, LikeState.overrides.value[songs[1].videoId])
            }
            compose.onNodeWithText("Zebra").assertDoesNotExist()
        } finally {
            AppSettings.setFavoriteSort(oldSort)
            LikeState.attachPersistence(emptyMap(), null)
            LikeState.clear()
            DeviceFavoriteStore.initialize(context)
            context.deleteSharedPreferences(filename)
        }
    }
}
