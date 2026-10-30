package com.music.bitchord

import android.content.ComponentName
import android.net.Uri
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.data.DeviceFavoriteStore
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.PlaybackService
import com.music.bitchord.playback.QueueShuffle
import com.music.bitchord.playback.playSongs
import com.music.bitchord.ui.player.PlayerVisualStyle
import com.music.bitchord.ui.rebirth.RebirthDestination
import com.music.bitchord.ui.rebirth.RebirthLocalExperience
import com.music.bitchord.ui.screens.SettingsScreen
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.sin

/** Real collection UI -> playSongs -> MediaSessionService -> decoder and advancing playback clock.
 * Test-owned files and preferences: no account, network music or MediaStore selection required.
 */
class FavoritePlaybackJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun expressiveFavoritesPlayAndSurviveSettings() = journey(PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS)
    @Test fun glassFavoritesPlayAndSurviveSettings() = journey(PlayerVisualStyle.LIQUID_GLASS_PLUS)

    private fun journey(style: PlayerVisualStyle) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val filename = "journey-${UUID.randomUUID()}"
        val tone = File(context.filesDir, "$filename.wav")
        // Non-silent 60 s PCM keeps the intended item alive throughout assertions.
        val samples = 22050 * 60
        val bytes = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(22050).putInt(44100).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(samples * 2)
        repeat(samples) { bytes.putShort((4000 * sin(2 * Math.PI * 440 * it / 22050)).toInt().toShort()) }
        tone.writeBytes(bytes.array())
        val songs = listOf("A", "B", "C").map { name ->
            Song("$filename-$name", "182 Favorite $name", "Journey", null,
                localUri = Uri.fromFile(tone).toString(), localPath = tone.absolutePath)
        }
        val oldStyle = AppSettings.playerVisualStyle.value
        val oldShuffle = QueueShuffle.enabled.value
        val oldPersistedShuffle = AppSettings.shuffleEnabled.value
        var controller: MediaController? = null
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        try {
            for (permission in listOf("android.permission.READ_MEDIA_AUDIO", "android.permission.POST_NOTIFICATIONS")) {
                instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} $permission").use { fd ->
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
                }
            }
            DeviceFavoriteStore.initialize(context, filename)
            LikeState.set(songs[0].videoId, LikeStatus.LIKE)
            LikeState.set(songs[1].videoId, LikeStatus.INDIFFERENT)
            LikeState.set(songs[2].videoId, LikeStatus.LIKE)
            assertTrue(context.getSharedPreferences(filename, 0).edit().commit())
            LikeState.attachPersistence(emptyMap(), null)
            LikeState.clear()
            DeviceFavoriteStore.initialize(context, filename)
            assertEquals(setOf(songs[0].videoId, songs[2].videoId), LikeState.overrides.value.filterValues { it == LikeStatus.LIKE }.keys)
            var future: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
            instrumentation.runOnMainSync {
                AppSettings.setPlayerVisualStyle(style)
                QueueShuffle.setEnabled(false)
                future = MediaController.Builder(context,
                    SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
            }
            val player = future!!.get(20, TimeUnit.SECONDS)
            controller = player
            val settingsOpen = mutableStateOf(false)
            compose.setContent {
                val ratings by LikeState.overrides.collectAsState()
                val holder = rememberSaveableStateHolder()
                MaterialTheme {
                    if (settingsOpen.value) {
                        BackHandler { settingsOpen.value = false }
                        SettingsScreen(windowWidth = 360.dp, signedIn = false, account = null,
                            onSignIn = {}, onSignOut = {}, onAccountScrobbling = {}, onEqualizer = {},
                            onOpenReplay = {}, onLyricsSources = {}, onTranslationLanguage = {}, onSources = {},
                            onListenTogether = {}, onSpotifyCanvasAuth = {}, onAppLanguage = {}, contentPadding = PaddingValues())
                    } else holder.SaveableStateProvider("library") {
                        RebirthLocalExperience(style = style, songs = songs, currentSong = null, isPlaying = false,
                            loading = false, favoriteIds = ratings.filterValues { it == LikeStatus.LIKE }.keys,
                            favoriteSongs = songs,
                            initialQuery = "", destination = RebirthDestination.LIBRARY,
                            onPlay = { list, index -> scope.launch { player.playSongs(list, index) } },
                            onShuffle = { list -> QueueShuffle.enableForNextQueue(); scope.launch { player.playSongs(list, 0) } },
                            onSongMenu = {}, onSettings = { settingsOpen.value = true },
                            onPlayPause = {}, onNext = {}, onExpandPlayer = {})
                    }
                }
            }
            val favorites = context.getString(R.string.rebirth_favorites)
            compose.onNodeWithText(favorites).performScrollTo().performClick()
            compose.onNodeWithText("182 Favorite B").assertDoesNotExist()
            compose.onNodeWithText("182 Favorite C").performClick()
            awaitPlayback(player, songs[2].videoId)
            compose.onNodeWithText(context.getString(R.string.play), useUnmergedTree = true).performClick()
            awaitPlayback(player, songs[0].videoId)
            instrumentation.runOnMainSync {
                assertEquals(setOf(songs[0].videoId, songs[2].videoId), (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet())
                player.seekToNextMediaItem()
            }
            awaitPlayback(player, songs[2].videoId)
            // Navigate through the actual Settings composition and back to the saved favorites page.
            compose.onNodeWithContentDescription(context.getString(R.string.settings)).performClick()
            compose.onNodeWithText(context.getString(R.string.appearance_and_themes)).performScrollTo().assertExists()
            compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithText("182 Favorite C").assertExists()
            compose.onNodeWithText(context.getString(R.string.shuffle), useUnmergedTree = true).performClick()
            awaitPlayback(player, songs[0].videoId)
            instrumentation.runOnMainSync {
                assertTrue(QueueShuffle.enabled.value)
                assertEquals(setOf(songs[0].videoId, songs[2].videoId), (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet())
                assertFalse("Queue order, not Media3 hidden shuffle, controls next", player.shuffleModeEnabled)
                player.seekToNextMediaItem()
            }
            awaitPlayback(player, songs[2].videoId)
            LikeState.set(songs[2].videoId, LikeStatus.INDIFFERENT)
            compose.onNodeWithText("182 Favorite C").assertDoesNotExist()
            assertTrue("Removing a favorite must never delete its audio", tone.exists())
            assertTrue(context.getSharedPreferences(filename, 0).edit().commit())
            LikeState.attachPersistence(emptyMap(), null)
            LikeState.clear()
            DeviceFavoriteStore.initialize(context, filename)
            assertEquals(LikeStatus.INDIFFERENT, LikeState.overrides.value[songs[2].videoId])
        } finally {
            scope.cancel()
            instrumentation.runOnMainSync {
                controller?.stop(); controller?.clearMediaItems(); controller?.release()
                AppSettings.setPlayerVisualStyle(oldStyle)
                QueueShuffle.setEnabled(oldShuffle)
                AppSettings.setShuffleEnabled(oldPersistedShuffle)
            }
            LikeState.attachPersistence(emptyMap(), null)
            LikeState.clear()
            DeviceFavoriteStore.initialize(context)
            context.deleteSharedPreferences(filename)
            tone.delete()
        }
    }

    private fun awaitPlayback(player: MediaController, expected: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.elapsedRealtime() + 20000
        var ready = false
        while (!ready && SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync {
                assertNull("Decoder/service error on $expected", player.playerError)
                ready = player.currentMediaItem?.mediaId == expected && player.isPlaying &&
                    player.playbackState == Player.STATE_READY && player.currentPosition >= 250
            }
            if (!ready) Thread.sleep(50)
        }
        assertTrue("No decoded playback with advancing clock for $expected", ready)
    }
}
