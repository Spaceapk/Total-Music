package com.music.bitchord

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
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
import com.music.bitchord.ui.player.LocalMotionReduced
import com.music.bitchord.ui.player.PlayerVisualStyle
import com.music.bitchord.ui.rebirth.AppearanceThemePicker
import com.music.bitchord.ui.rebirth.RebirthLocalExperience
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GooglePlusUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val songs = listOf(
        Song("plus-1", "Track One", "Artist One", null, localUri = "content://plus/1"),
        Song("plus-2", "Track Two", "Artist Two", null, localUri = "content://plus/2"),
    )

    @Test fun googlePlusTransportQueueAndLyricsRemainReachable() {
        val old = AppSettings.playerVisualStyle.value
        val position = PlaybackPosition().apply { report(1500) }
        val lyrics = listOf(LyricLine(0, "Intro line"), LyricLine(1000, "Active line"), LyricLine(3000, "Future line"))
        try {
            AppSettings.setPlayerVisualStyle(PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS)
            compose.setContent {
                CompositionLocalProvider(LocalMotionReduced provides true) {
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
            }
            compose.onNodeWithTag("expressive-transport").assertExists()
            compose.onNodeWithTag("google-plus-open-queue").performClick()
            compose.onNodeWithTag("google-plus-queue").assertExists()
            compose.onNodeWithTag("google-plus-open-queue").performClick()
            compose.onNodeWithTag("google-plus-open-lyrics").performClick()
            compose.onNodeWithTag("google-plus-lyrics").assertExists()
            compose.onNodeWithTag("google-plus-lyric-1").assertExists()
        } finally { compose.runOnIdle { AppSettings.setPlayerVisualStyle(old) } }
    }
}
