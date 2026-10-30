package com.music.bitchord

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.data.DeviceFavoriteStore
import com.music.bitchord.data.DevicePlaylistStore
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.PlaylistSort
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.FavoriteSort
import com.music.bitchord.ui.player.PlayerVisualStyle
import com.music.bitchord.ui.rebirth.RebirthDestination
import com.music.bitchord.ui.rebirth.RebirthLocalExperience
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/**
 * The virtual favorites collection and the playlist order menu, through the
 * real composition and the real stores. Both are addressable by tag because a
 * visible text is also used by the category chip next to them.
 */
class FavoriteCollectionUiTest {
    @get:Rule val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun theVirtualFavoritesCollectionFollowsTheSharedHeartStore() {
        val filename = "test-184-favorites-${UUID.randomUUID()}"
        val keep = Song("content://media/184/keep", "Keep me", "Artist", null, localUri = "content://media/184/keep")
        val remove = Song("content://media/184/remove", "Remove me", "Artist", null, localUri = "content://media/184/remove")
        val songs = listOf(keep, remove)
        val oldSort = AppSettings.favoriteSort.value
        try {
            // Title order makes the two rows deterministic regardless of how
            // close together the two addedAt timestamps happen to be.
            AppSettings.setFavoriteSort(FavoriteSort.TITLE)
            DeviceFavoriteStore.initialize(context, filename)
            songs.forEach { LikeState.set(it.videoId, LikeStatus.LIKE, it) }
            compose.setContent {
                val ratings by LikeState.overrides.collectAsState()
                MaterialTheme {
                    RebirthLocalExperience(
                        style = PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS,
                        songs = songs,
                        favoriteSongs = songs,
                        favoriteIds = ratings.filterValues { it == LikeStatus.LIKE }.keys,
                        currentSong = null,
                        isPlaying = false,
                        loading = false,
                        initialQuery = "",
                        destination = RebirthDestination.LIBRARY,
                        onPlay = { _, _ -> },
                        onSongMenu = {},
                        onSettings = {},
                        onPlayPause = {},
                        onNext = {},
                        onExpandPlayer = {},
                    )
                }
            }
            compose.onNodeWithText(context.getString(R.string.playlists)).performScrollTo().performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("favorites-collection").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("favorites-collection").performScrollTo().performClick()
            compose.onNodeWithText("Keep me").assertExists()
            compose.onNodeWithText("Remove me").assertExists()
            compose.onAllNodesWithContentDescription(
                context.getString(R.string.remove_from_liked),
                useUnmergedTree = true,
            )[1].performClick()
            compose.runOnIdle {
                assertEquals(
                    LikeStatus.INDIFFERENT,
                    LikeState.overrides.value[remove.videoId],
                )
                assertEquals(LikeStatus.LIKE, LikeState.overrides.value[keep.videoId])
            }
            compose.onNodeWithText("Remove me").assertDoesNotExist()
            compose.onNodeWithText("Keep me").assertExists()
        } finally {
            AppSettings.setFavoriteSort(oldSort)
            LikeState.attachPersistence(emptyMap(), null)
            LikeState.clear()
            DeviceFavoriteStore.initialize(context)
            context.deleteSharedPreferences(filename)
        }
    }

    @Test fun aPlaylistCanBeOrderedByAlbumInBothDirectionsAndTheChoiceIsPersisted() {
        val store = DevicePlaylistStore.forDevice(context)
        val name = "184 order ${UUID.randomUUID()}"
        val one = Song("content://media/184/a", "One", "Artist", null, albumName = "Primero", localUri = "content://media/184/a")
        val two = Song("content://media/184/b", "Two", "Artist", null, albumName = "Segundo", localUri = "content://media/184/b")
        store.createWithSongs(name, listOf(one, two))
        val id = store.playlists.value.first { it.name == name }.id
        try {
            compose.setContent {
                val ratings by LikeState.overrides.collectAsState()
                MaterialTheme {
                    RebirthLocalExperience(
                        style = PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS,
                        songs = listOf(one, two),
                        favoriteSongs = emptyList(),
                        favoriteIds = ratings.filterValues { it == LikeStatus.LIKE }.keys,
                        currentSong = null,
                        isPlaying = false,
                        loading = false,
                        initialQuery = "",
                        destination = RebirthDestination.LIBRARY,
                        onPlay = { _, _ -> },
                        onSongMenu = {},
                        onSettings = {},
                        onPlayPause = {},
                        onNext = {},
                        onExpandPlayer = {},
                    )
                }
            }
            compose.onNodeWithText(context.getString(R.string.playlists)).performScrollTo().performClick()
            compose.onNodeWithText(name).performScrollTo().performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("playlist-sort").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("playlist-sort").performClick()
            compose.onNodeWithTag("playlist-sort-ALBUM_DESC").performClick()
            compose.runOnIdle {
                assertEquals(
                    PlaylistSort.ALBUM_DESC,
                    store.playlists.value.first { it.id == id }.sort,
                )
                assertEquals(
                    listOf(two.videoId, one.videoId),
                    store.playlists.value.first { it.id == id }.sortedSongs().map { it.videoId },
                )
            }
            // The same control in the other direction, so both menu entries are
            // exercised rather than only the one that reverses the manual order.
            compose.onNodeWithTag("playlist-sort").performClick()
            compose.onNodeWithTag("playlist-sort-ALBUM_ASC").performClick()
            compose.runOnIdle {
                assertEquals(
                    PlaylistSort.ALBUM_ASC,
                    store.playlists.value.first { it.id == id }.sort,
                )
                assertEquals(
                    listOf(one.videoId, two.videoId),
                    store.playlists.value.first { it.id == id }.sortedSongs().map { it.videoId },
                )
                // The persisted order is independent from the manual one.
                assertEquals(
                    listOf(one.videoId, two.videoId),
                    store.playlists.value.first { it.id == id }.songs.map { it.videoId },
                )
            }
        } finally {
            store.delete(id)
        }
    }
}
