package com.music.bitchord

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.like
import com.music.bitchord.sharedui.resources.remove_from_liked
import com.music.bitchord.sharedui.resources.rebirth_add_favorite
import com.music.bitchord.sharedui.resources.rebirth_remove_favorite
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.player.NowPlayingScreen
import com.music.bitchord.ui.player.PlayerVisualStyle
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exercise the real player heart and ViewModel with a local song and no account. */
class FavoritePlayerUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun originalHeartWorksForLocalSongsWithoutSignIn() = toggle(PlayerVisualStyle.ORIGINAL)
    @Test fun expressiveHeartWorksForLocalSongsWithoutSignIn() = toggle(PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS)
    @Test fun glassHeartWorksForLocalSongsWithoutSignIn() = toggle(PlayerVisualStyle.LIQUID_GLASS_PLUS)

    @Test fun expressiveArtworkAndHeartFitLandscape() = toggle(PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS, landscape = true)

    @Test fun expressiveTransportUsesTheLowerHalfOfTallScreens() = toggle(PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS, tall = true)

    private fun toggle(style: PlayerVisualStyle, landscape: Boolean = false, tall: Boolean = false) {
        val previousStyle = AppSettings.playerVisualStyle.value
        val art = java.io.File(compose.activity.filesDir, "visual-test-cover.png")
        val bitmap = android.graphics.Bitmap.createBitmap(600, 600, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.shader = android.graphics.LinearGradient(0f, 0f, 600f, 600f,
            intArrayOf(0xFF163D78.toInt(), 0xFFC44372.toInt(), 0xFFF5AD65.toInt()), null,
            android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 600f, 600f, paint)
        paint.shader = null
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 54f
        canvas.drawText("TOTAL MUSIC", 52f, 290f, paint)
        paint.textSize = 28f
        canvas.drawText("Visual Experience", 55f, 340f, paint)
        art.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val song = Song("content://media/test-favorite", "Prueba de favoritos", "Total Music", android.net.Uri.fromFile(art).toString(),
            localUri = "content://media/test-favorite")
        val viewModel = ViewModelProvider(compose.activity,
            ViewModelProvider.AndroidViewModelFactory(compose.activity.application))[MainViewModel::class.java]
        LikeState.set(song.videoId, LikeStatus.INDIFFERENT)
        AppSettings.setPlayerVisualStyle(style)
        var add = ""
        var remove = ""
        try {
            compose.setContent {
                val ratings by viewModel.likeStatuses.collectAsStateWithLifecycle()
                add = stringResource(if (style == PlayerVisualStyle.ORIGINAL) Res.string.like else Res.string.rebirth_add_favorite)
                remove = stringResource(if (style == PlayerVisualStyle.ORIGINAL) Res.string.remove_from_liked else Res.string.rebirth_remove_favorite)
                MaterialTheme {
                    Box(if (landscape) Modifier.requiredSize(640.dp, 360.dp) else if (tall) Modifier.requiredSize(360.dp, 900.dp) else Modifier) {
                    NowPlayingScreen(
                        song = song, isPlaying = false, isLoading = false, position = PlaybackPosition(),
                        durationMs = 60000, audioVersionSwitching = false, qualityUpgraded = false,
                        queue = listOf(song), queueIndex = 0, hasPrevious = false, hasNext = false,
                        repeatMode = 0, shuffleEnabled = false, autoplayEnabled = false,
                        signedIn = false, accountName = null,
                        likeStatus = ratings[song.videoId] ?: LikeStatus.INDIFFERENT,
                        onToggleLike = { viewModel.toggleLike(song.videoId) },
                        onPlayPause = {}, onNext = {}, onPrevious = {}, onBlockedControl = {},
                        onSeek = {}, onSeekFraction = {}, onToggleShuffle = {}, onCycleRepeat = {},
                        onToggleAutoplay = {}, onJumpTo = {}, onRemoveFromQueue = {}, onMoveInQueue = { _, _ -> },
                        onClearQueue = {}, onOpenMenu = {}, onOpenAlbum = {}, onOpenArtist = { _, _ -> },
                        onOpenPlaybackSource = {}, onListenTogether = {}, lyrics = null, lyricsSource = null,
                        lyricsProviderStates = emptyMap(), onSelectLyricsProvider = {}, lyricsUnavailable = true,
                        lyricsOffsetOpen = false, onDismissLyricsOffset = {}, windowWidth = if (landscape) 640.dp else 320.dp, windowHeight = if (landscape) 360.dp else 640.dp,
                    )
                    }
                }
            }
            compose.waitForIdle()
            if (landscape) {
                val sleeve = compose.onNodeWithTag("rebirth-artwork").fetchSemanticsNode().boundsInRoot
                assertEquals(sleeve.width, sleeve.height, 1f)
                assertTrue("Landscape sleeve must leave room for controls", sleeve.height > 0f && sleeve.height <= with(compose.density) { 180.dp.toPx() })
            }
            if (tall) {
                val transport = compose.onNodeWithTag("expressive-transport").fetchSemanticsNode().boundsInRoot
                assertTrue("Transport must occupy the lower half", transport.center.y > with(compose.density) { 450.dp.toPx() })
            }
            compose.onNodeWithContentDescription(add).performClick()
            compose.waitUntil(10000) { LikeState.overrides.value[song.videoId] == LikeStatus.LIKE }
            compose.onNodeWithContentDescription(remove).assertExists().performClick()
            compose.waitUntil(10000) { LikeState.overrides.value[song.videoId] == LikeStatus.INDIFFERENT }
            assertEquals(LikeStatus.INDIFFERENT, viewModel.likeStatusOf(song.videoId))
            compose.waitForIdle()
            val previews = java.io.File(compose.activity.filesDir, "player-previews").apply { mkdirs() }
            val name = "${style.name}-${if (landscape) "landscape" else if (tall) "tall" else "portrait"}.png"
            val screenshot = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            checkNotNull(screenshot) { "The emulator did not return a screenshot" }
            java.io.File(previews, name).outputStream().use {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            screenshot.recycle()
        } finally {
            AppSettings.setPlayerVisualStyle(previousStyle)
            LikeState.set(song.videoId, LikeStatus.INDIFFERENT)
        }
    }
}
