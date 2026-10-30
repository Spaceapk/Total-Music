package com.music.bitchord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.data.DeviceFavoriteStore
import com.music.bitchord.data.DevicePlaylistStore
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.PlaylistSort
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.FavoriteSort
import com.music.bitchord.ui.player.PlayerVisualStyle
import com.music.bitchord.ui.rebirth.RebirthDestination
import com.music.bitchord.ui.rebirth.RebirthLocalExperience
import com.music.bitchord.ui.rebirth.favoriteSongsInOrder
import com.music.bitchord.ui.screens.DevicePlaylistsTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/**
 * Dragging a playlist's rows, through the real store and both compositions.
 *
 * The gesture is real touch input on the handle, not a call to the move
 * function: what this checks is that the handle is reachable, that the drop is
 * translated into one move, and that a playlist which was showing another
 * order keeps exactly those rows as its manual order afterwards.
 */
class PlaylistReorderUiTest {
    @get:Rule val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun songs() = listOf(
        Song("content://media/185/alpha", "Alpha", "Artist", null, localUri = "content://media/185/alpha"),
        Song("content://media/185/bravo", "Bravo", "Artist", null, localUri = "content://media/185/bravo"),
        Song("content://media/185/charlie", "Charlie", "Artist", null, localUri = "content://media/185/charlie"),
        Song("content://media/185/delta", "Delta", "Artist", null, localUri = "content://media/185/delta"),
    )

    @Test fun expressivePlaylistDragStoresTheVisibleOrderAsManual() =
        dragInPlusTheme(PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS)

    @Test fun glassPlaylistDragStoresTheVisibleOrderAsManual() =
        dragInPlusTheme(PlayerVisualStyle.LIQUID_GLASS_PLUS)

    private fun dragInPlusTheme(style: PlayerVisualStyle) {
        val store = DevicePlaylistStore.forDevice(context)
        val title = "185 drag ${style.name} ${UUID.randomUUID()}"
        val tracks = songs()
        // Manual order differs from title order on purpose: the visible list is
        // Charlie, Alpha, Delta, Bravo.
        store.createWithSongs(title, listOf(tracks[2], tracks[0], tracks[3], tracks[1]))
        val id = store.playlists.value.first { it.name == title }.id
        try {
            compose.setContent {
                MaterialTheme {
                    val ratings by LikeState.overrides.collectAsState()
                    RebirthLocalExperience(
                        style = style,
                        songs = tracks,
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
            store.setSort(id, PlaylistSort.TITLE_ASC)
            compose.onNodeWithText(context.getString(R.string.playlists)).performScrollTo().performClick()
            compose.onNodeWithText(title).performScrollTo().performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("playlist-drag-${tracks[0].videoId}").fetchSemanticsNodes().isNotEmpty()
            }

            val first = compose.onNodeWithTag("playlist-drag-${tracks[0].videoId}")
                .fetchSemanticsNode().boundsInRoot
            val second = compose.onNodeWithTag("playlist-drag-${tracks[1].videoId}")
                .fetchSemanticsNode().boundsInRoot
            val rowStep = (second.top - first.top).coerceAtLeast(1f)

            // Carry Alpha (first visible row) past the three rows below it.
            compose.onNodeWithTag("playlist-drag-${tracks[0].videoId}").performTouchInput {
                down(center)
                moveBy(Offset(0f, rowStep))
                moveBy(Offset(0f, rowStep))
                moveBy(Offset(0f, rowStep))
                moveBy(Offset(0f, rowStep * 0.6f))
                up()
            }
            compose.waitForIdle()

            val moved = store.playlists.value.first { it.id == id }
            assertEquals(PlaylistSort.MANUAL, moved.sort)
            // The visible list was Alpha, Bravo, Charlie, Delta. Alpha was
            // carried to the last visible row, and the drop kept the rows as
            // arranged instead of reverting to the title order.
            assertEquals(
                listOf("bravo", "charlie", "delta", "alpha"),
                moved.songs.map { it.title.lowercase() },
            )
            assertEquals(
                moved.songs.map { it.videoId },
                moved.sortedSongs().map { it.videoId },
            )
            assertEquals(4, moved.songs.map { it.videoId }.toSet().size)
        } finally {
            store.delete(id)
        }
    }

    @Test fun originalPlaylistTabReordersByDraggingToo() {
        val store = DevicePlaylistStore.forDevice(context)
        val title = "185 original ${UUID.randomUUID()}"
        val tracks = songs()
        store.createWithSongs(title, tracks)
        val id = store.playlists.value.first { it.name == title }.id
        try {
            compose.setContent {
                MaterialTheme {
                    DevicePlaylistsTab(
                        songs = tracks,
                        onPlay = { _, _ -> },
                        padding = PaddingValues(),
                        favorites = emptyList(),
                    )
                }
            }
            compose.onNodeWithText(title).performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("playlist-drag-${tracks[0].videoId}").fetchSemanticsNodes().isNotEmpty()
            }
            val first = compose.onNodeWithTag("playlist-drag-${tracks[0].videoId}")
                .fetchSemanticsNode().boundsInRoot
            val second = compose.onNodeWithTag("playlist-drag-${tracks[1].videoId}")
                .fetchSemanticsNode().boundsInRoot
            val rowStep = (second.top - first.top).coerceAtLeast(1f)

            compose.onNodeWithTag("playlist-drag-${tracks[0].videoId}").performTouchInput {
                down(center)
                moveBy(Offset(0f, rowStep))
                moveBy(Offset(0f, rowStep * 1.5f))
                up()
            }
            compose.waitForIdle()

            val moved = store.playlists.value.first { it.id == id }
            assertNotEquals(tracks[0].videoId, moved.songs.first().videoId)
            assertEquals(PlaylistSort.MANUAL, moved.sort)
            assertEquals(4, moved.songs.map { it.videoId }.toSet().size)
            assertEquals(
                moved.songs.map { it.videoId },
                moved.sortedSongs().map { it.videoId },
            )
        } finally {
            store.delete(id)
        }
    }

    @Test fun theOriginalPlaylistsTabOffersTheSharedFavoritesAndRemovesItsHeart() {
        val oldSort = com.music.bitchord.data.settings.AppSettings.favoriteSort.value
        val filename = "test-185-original-favorites-${UUID.randomUUID()}"
        val apple = Song("content://media/185/apple", "Apple", "Artist", null, localUri = "content://media/185/apple")
        val zebra = Song("content://media/185/zebra", "Zebra", "Artist", null, localUri = "content://media/185/zebra")
        val tracks = listOf(apple, zebra)
        try {
            com.music.bitchord.data.settings.AppSettings.setFavoriteSort(FavoriteSort.TITLE)
            DeviceFavoriteStore.initialize(context, filename)
            tracks.forEach { LikeState.set(it.videoId, LikeStatus.LIKE, it) }
            compose.setContent {
                MaterialTheme {
                    val ratings by LikeState.overrides.collectAsState()
                    val favorites = favoriteSongsInOrder(
                        favoriteSongs = tracks,
                        deviceSongs = tracks,
                        favoriteIds = ratings.filterValues { it == LikeStatus.LIKE }.keys,
                        sort = FavoriteSort.TITLE,
                    )
                    DevicePlaylistsTab(
                        songs = tracks,
                        onPlay = { _, _ -> },
                        padding = PaddingValues(),
                        favorites = favorites,
                    )
                }
            }
            compose.onNodeWithTag("original-favorites-entry").performClick()
            compose.onNodeWithText("Apple").assertExists()
            compose.onNodeWithText("Zebra").assertExists()
            // Title order puts Zebra second; its heart is the second one.
            assertEquals(
                1,
                compose.onAllNodesWithTag("original-favorite-${zebra.videoId}")
                    .fetchSemanticsNodes().size,
            )
            compose.onNodeWithTag("original-favorite-${zebra.videoId}").performClick()
            compose.waitForIdle()
            // Clicking the row plays; it must not quietly change the heart.
            assertEquals(LikeStatus.LIKE, LikeState.overrides.value[zebra.videoId])
            compose.onAllNodesWithTag("favorites-remove-heart")[1].performClick()
            compose.runOnIdle {
                assertEquals(LikeStatus.INDIFFERENT, LikeState.overrides.value[zebra.videoId])
                assertEquals(LikeStatus.LIKE, LikeState.overrides.value[apple.videoId])
            }
            compose.onNodeWithText("Zebra").assertDoesNotExist()
            compose.onNodeWithText("Apple").assertExists()
        } finally {
            com.music.bitchord.data.settings.AppSettings.setFavoriteSort(oldSort)
            LikeState.attachPersistence(emptyMap(), null)
            LikeState.clear()
            DeviceFavoriteStore.initialize(context)
            context.deleteSharedPreferences(filename)
        }
    }
}
