package com.music.bitchord

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.TotalMusicSettings
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.ui.player.NowPlayingScreen
import com.music.bitchord.ui.player.PlayerVisualStyle
import com.music.bitchord.ui.rebirth.AppearanceThemePicker
import com.music.bitchord.ui.rebirth.RebirthLocalExperience
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ObsidianFidelityUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val songs = listOf(
        Song("obsidian-1", "Glass One", "A Band", null, albumName = "Glass album", localUri = "content://obsidian/1"),
        Song("obsidian-2", "Glass Two", "B Band", null, albumName = "Glass album", localUri = "content://obsidian/2"),
    )

    @Test fun fifthThemePersistsWithoutChangingLegacyThemes() {
        val old = AppSettings.playerVisualStyle.value
        try {
            compose.setContent { MaterialTheme { AppearanceThemePicker() } }
            compose.onNodeWithText("Obsidian").performClick()
            compose.runOnIdle {
                assertEquals(PlayerVisualStyle.OBSIDIAN, AppSettings.playerVisualStyle.value)
                assertEquals("OBSIDIAN", compose.activity.getSharedPreferences("bitchord_settings", 0).getString("player_visual_style", null))
            }
        } finally { compose.runOnIdle { AppSettings.setPlayerVisualStyle(old) } }
    }

    @Test fun integratedDockSearchesAndTransforms() {
        compose.setContent {
            MaterialTheme {
                RebirthLocalExperience(
                    style = PlayerVisualStyle.OBSIDIAN, songs = songs, currentSong = songs[0],
                    isPlaying = true, loading = false, favoriteIds = emptySet(), initialQuery = "",
                    onPlay = { _, _ -> }, onSongMenu = {}, onSettings = {}, onPlayPause = {},
                    onNext = {}, onPrevious = {}, onExpandPlayer = {},
                )
            }
        }
        compose.onNodeWithTag("obsidian-library").assertExists()
        compose.onNodeWithTag("obsidian-dock").assertExists()
        compose.onNodeWithTag("obsidian-disc").assertExists()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.search)).performClick()
        compose.onNodeWithTag("obsidian-search").assertExists().performTextInput("Glass One")
        compose.onNodeWithText("Glass One").assertExists()
        compose.onNodeWithText("Glass Two").assertDoesNotExist()
    }

    @Test fun immersivePlayerDiscQueueAndSynchronizedLyrics() {
        val old = AppSettings.playerVisualStyle.value
        val position = PlaybackPosition().apply { report(1500) }
        val lyrics = listOf(LyricLine(0, "Intro line"), LyricLine(1000, "Active line"), LyricLine(3000, "Future line"))
        try {
            AppSettings.setPlayerVisualStyle(PlayerVisualStyle.OBSIDIAN)
            compose.setContent {
                MaterialTheme {
                    NowPlayingScreen(
                        song = songs[0], isPlaying = true, isLoading = false, position = position,
                        durationMs = 60000, audioVersionSwitching = false, qualityUpgraded = false,
                        queue = songs, queueIndex = 0, hasPrevious = false, hasNext = true,
                        repeatMode = 0, shuffleEnabled = false, autoplayEnabled = false,
                        signedIn = false, accountName = null, likeStatus = com.music.bitchord.data.model.LikeStatus.INDIFFERENT,
                        onToggleLike = {}, onPlayPause = {}, onNext = {}, onPrevious = {}, onBlockedControl = {},
                        onSeek = {}, onSeekFraction = {}, onToggleShuffle = {}, onCycleRepeat = {},
                        onToggleAutoplay = {}, onJumpTo = {}, onRemoveFromQueue = {}, onMoveInQueue = { _, _ -> },
                        onClearQueue = {}, onOpenMenu = {}, onOpenAlbum = {}, onOpenArtist = { _, _ -> },
                        onOpenPlaybackSource = {}, onListenTogether = {}, lyrics = lyrics, lyricsSource = null,
                        lyricsProviderStates = emptyMap(), onSelectLyricsProvider = {}, lyricsUnavailable = false,
                        lyricsOffsetOpen = false, onDismissLyricsOffset = {}, windowWidth = 360.dp, windowHeight = 800.dp,
                    )
                }
            }
            compose.onNodeWithTag("obsidian-player").assertExists()
            compose.onNodeWithTag("rebirth-artwork").assertExists()
            compose.onNodeWithTag("obsidian-open-queue").performClick()
            compose.onNodeWithTag("obsidian-queue").assertExists()
            compose.onNodeWithTag("obsidian-page-back").performClick()
            compose.onNodeWithTag("obsidian-lyrics").performClick()
            compose.onNodeWithTag("obsidian-synced-lyrics").assertExists()
            compose.onNodeWithTag("obsidian-lyric-line-1").assertExists()
        } finally { compose.runOnIdle { AppSettings.setPlayerVisualStyle(old) } }
    }
}
