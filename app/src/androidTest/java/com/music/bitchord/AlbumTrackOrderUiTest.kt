package com.music.bitchord

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.runtime.mutableStateOf
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.LibraryViewType
import com.music.bitchord.data.settings.LocalMusicSort
import com.music.bitchord.ui.screens.LocalMusicScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Opens the actual album UI, chooses the exposed sort item, then inspects its playback order. */
class AlbumTrackOrderUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun opensAlbumAndSortsByDiscAndTrack() {
        val oldSort = AppSettings.localMusicSort.value
        val oldView = AppSettings.localMusicViewType.value
        val songs = listOf(
            Song("2", "Alpha", "Artist", null, albumName = "Test album", localTrackNumber = 2),
            Song("3", "Bravo", "Artist", null, albumName = "Test album", localTrackNumber = 1, localDiscNumber = 2),
            Song("4", "Charlie", "Artist", null, albumName = "Test album"),
            Song("1", "Zulu", "Artist", null, albumName = "Test album", localTrackNumber = 1),
        )
        var played = emptyList<String>()
        try {
            compose.runOnUiThread {
                AppSettings.setLocalMusicSort(LocalMusicSort.TITLE_ASC)
                AppSettings.setLocalMusicViewType(LibraryViewType.LIST)
            }
            compose.setContent { MaterialTheme {
                LocalMusicScreen(songs, onSongClick = { tracks, _ -> played = tracks.map { it.videoId } },
                    onSongLongPress = {}, onSongSwipe = {}, onShuffle = {},
                    contentPadding = PaddingValues(), navigationTab = 2)
            } }
            compose.onNodeWithText("Test album").performClick()
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.sort_music)).performClick()
            compose.onNodeWithText(compose.activity.getString(R.string.sort_track_number)).performClick()
            compose.onNodeWithText("Zulu").performClick()
            compose.runOnIdle {
                assertEquals(listOf("1", "2", "3", "4"), played)
                AppSettings.reload()
                assertEquals(LocalMusicSort.TRACK_NUMBER, AppSettings.localMusicSort.value)
            }
        } finally {
            compose.runOnUiThread {
                AppSettings.setLocalMusicSort(oldSort)
                AppSettings.setLocalMusicViewType(oldView)
            }
        }
    }

    @Test fun selectAllUsesOnlySearchResultsAndDropsHiddenSelection() {
        val oldView = AppSettings.localMusicViewType.value
        val query = mutableStateOf("Target")
        val songs = listOf(
            Song("a", "First", "Target", null),
            Song("b", "Second", "Target", null),
            Song("c", "Third", "Other", null),
        )
        try {
            compose.runOnUiThread { AppSettings.setLocalMusicViewType(LibraryViewType.LIST) }
            compose.setContent { MaterialTheme {
                LocalMusicScreen(songs, onSongClick = { _, _ -> },
                    onSongLongPress = {}, onSongSwipe = {}, onShuffle = {},
                    contentPadding = PaddingValues(), navigationTab = 0, query = query.value)
            } }
            compose.onNodeWithText("First").performTouchInput { longClick() }
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.select_all)).performClick()
            compose.onNodeWithText("2").assertExists()
            compose.runOnIdle { query.value = "Other" }
            compose.onNodeWithText("Third").performTouchInput { longClick() }
            compose.onNodeWithText("1").assertExists()
        } finally {
            compose.runOnUiThread { AppSettings.setLocalMusicViewType(oldView) }
        }
    }
}
