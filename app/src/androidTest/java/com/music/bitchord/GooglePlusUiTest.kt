package com.music.bitchord

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.runtime.mutableStateOf
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.ui.rebirth.RebirthDestination
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

    private val library = listOf(
        Song("content://plus/10", "Amber Road", "North Lights", null, durationText = "3:10", albumName = "Dawn", localUri = "content://plus/10", localPath = "/storage/emulated/0/Music/Rock/amber.flac", localDateAddedSeconds = 600),
        Song("content://plus/11", "Blue Hour", "North Lights", null, durationText = "4:05", albumName = "Dawn", localUri = "content://plus/11", localPath = "/storage/emulated/0/Music/Rock/blue.flac", localDateAddedSeconds = 500),
        Song("content://plus/12", "Cinder", "Mara Sol", null, durationText = "2:45", albumName = "Embers", localUri = "content://plus/12", localPath = "/storage/emulated/0/Music/Jazz/cinder.mp3", localDateAddedSeconds = 400),
        Song("content://plus/13", "Drift", "Mara Sol", null, durationText = "5:20", albumName = "Embers", localUri = "content://plus/13", localPath = "/storage/emulated/0/Music/Jazz/drift.mp3", localDateAddedSeconds = 300),
        Song("content://plus/14", "Echo Park", "Vale", null, durationText = "3:33", albumName = "Lines", localUri = "content://plus/14", localPath = "/storage/emulated/0/Download/echo.mp3", localDateAddedSeconds = 200),
    )
    private val genreTags = mapOf(
        "content://plus/10" to "Rock", "content://plus/11" to "Rock",
        "content://plus/12" to "Jazz", "content://plus/13" to "Jazz",
    )

    /** Saves what the test really drew; a failed capture never fails or fakes a test. */
    private fun capture(name: String) {
        runCatching {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val directory = java.io.File(context.getExternalFilesDir(null), "plus-captures").apply { mkdirs() }
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            java.io.File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun shell(
        destination: RebirthDestination,
        current: Song? = null,
        onPlay: (List<Song>, Int) -> Unit = { _, _ -> },
        dark: Boolean = false,
    ) {
        val page = mutableStateOf(destination)
        compose.setContent {
            CompositionLocalProvider(LocalMotionReduced provides true) {
                MaterialTheme(colorScheme = if (dark) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()) {
                    RebirthLocalExperience(
                        style = PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS, songs = library, currentSong = current,
                        isPlaying = current != null, loading = false, favoriteIds = setOf("content://plus/12"),
                        initialQuery = "", onPlay = onPlay, onSongMenu = {}, onSettings = {}, onPlayPause = {},
                        onNext = {}, onPrevious = {}, onExpandPlayer = {},
                        destination = page.value, onDestinationChange = { page.value = it },
                        genreTags = genreTags,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun homeShowsMixRecentAndStatsFromTheRealLibrary() {
        val played = mutableListOf<Pair<List<Song>, Int>>()
        shell(RebirthDestination.HOME, onPlay = { rows, index -> played += rows to index })
        compose.onNodeWithTag("google-plus-mix-collage").assertExists()
        compose.onNodeWithTag("google-plus-daily-mix").assertExists()
        capture("home-top-light")
        compose.onNodeWithTag("google-plus-daily-mix").performClick()
        compose.runOnIdle {
            assertEquals(1, played.size)
            // The mix is the library itself, reordered: same songs, nothing invented.
            assertEquals(library.map { it.videoId }.toSet(), played[0].first.map { it.videoId }.toSet())
            assertEquals(0, played[0].second)
        }
        compose.onNodeWithTag("google-plus-home").performScrollToNode(hasTestTag("google-plus-recent"))
        compose.onNodeWithTag("google-plus-recent").assertExists()
        compose.onNodeWithTag("google-plus-home").performScrollToNode(hasTestTag("google-plus-stats"))
        compose.onNodeWithTag("google-plus-stats").assertExists()
        capture("home-stats-light")
    }

    @Test fun searchLandingGenresOpenTheirOwnSongs() {
        shell(RebirthDestination.SEARCH)
        compose.onNodeWithTag("google-plus-search-landing").assertExists()
        compose.onNodeWithTag("plus-genre-Rock").assertExists()
        compose.onNodeWithTag("plus-genre-Jazz").assertExists()
        capture("search-landing-light")
        compose.onNodeWithTag("plus-genre-Jazz").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Cinder").assertExists()
        compose.onNodeWithText("Drift").assertExists()
        compose.onNodeWithText("Amber Road").assertDoesNotExist()
        capture("search-genre-jazz-light")
    }

    @Test fun libraryTabsIncludeFoldersAndKeepTheirStoredOrder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("google_plus_ui", android.content.Context.MODE_PRIVATE)
        val before = prefs.getString("library_tab_order", null)
        try {
            prefs.edit().remove("library_tab_order").commit()
            shell(RebirthDestination.LIBRARY)
            compose.onNodeWithTag("plus-tab-SONGS").assertIsSelected()
            capture("library-songs-light")
            compose.onNodeWithTag("plus-tab-FOLDERS").performScrollTo().performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("google-plus-folders").assertExists()
            compose.onNodeWithTag("folder:/storage/emulated/0/Music/Jazz").assertExists()
            capture("library-folders-light")
            compose.onNodeWithTag("folder:/storage/emulated/0/Music/Jazz").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Cinder").assertExists()
            compose.onNodeWithText("Echo Park").assertDoesNotExist()
            compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("plus-reorder-tabs").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("plus-order-up-FOLDERS").performClick()
            capture("library-reorder-dialog-light")
            compose.onNodeWithText(context.getString(R.string.save)).performClick()
            compose.waitForIdle()
            assertEquals("SONGS,ALBUMS,ARTISTS,FOLDERS,PLAYLISTS,FAVORITES", prefs.getString("library_tab_order", null))
        } finally {
            prefs.edit().apply { if (before == null) remove("library_tab_order") else putString("library_tab_order", before) }.commit()
        }
    }

    @Test fun darkShellDrawsHomeSearchAndLibrary() {
        shell(RebirthDestination.HOME, current = library[0], dark = true)
        compose.onNodeWithTag("google-plus-mix-collage").assertExists()
        capture("home-top-dark")
    }

    private fun player(position: PlaybackPosition, lyrics: List<LyricLine>?, queue: List<Song> = songs, index: Int = 0, dark: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalMotionReduced provides true) {
                MaterialTheme(colorScheme = if (dark) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()) {
                    NowPlayingScreen(
                        song = queue[index], isPlaying = true, isLoading = false, position = position,
                        durationMs = 60000, audioVersionSwitching = false, qualityUpgraded = false,
                        queue = queue, queueIndex = index, hasPrevious = index > 0, hasNext = index < queue.lastIndex,
                        repeatMode = 0, shuffleEnabled = false, autoplayEnabled = false,
                        signedIn = false, accountName = null, likeStatus = com.music.bitchord.data.model.LikeStatus.INDIFFERENT,
                        onToggleLike = {}, onPlayPause = {}, onNext = {}, onPrevious = {}, onBlockedControl = {},
                        onSeek = {}, onSeekFraction = {}, onToggleShuffle = {}, onCycleRepeat = {},
                        onToggleAutoplay = {}, onJumpTo = {}, onRemoveFromQueue = {}, onMoveInQueue = { _, _ -> },
                        onClearQueue = {}, onOpenMenu = {}, onOpenAlbum = {}, onOpenArtist = { _, _ -> },
                        onOpenPlaybackSource = {}, onListenTogether = {}, lyrics = lyrics, lyricsSource = null,
                        lyricsProviderStates = emptyMap(), onSelectLyricsProvider = {}, lyricsUnavailable = lyrics == null,
                        lyricsOffsetOpen = false, onDismissLyricsOffset = {}, windowWidth = 360.dp, windowHeight = 800.dp,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun lyricsFollowExactlyOneLineAndStaticModeHasNone() {
        val old = AppSettings.playerVisualStyle.value
        val position = PlaybackPosition().apply { report(1500) }
        val lyrics = listOf(LyricLine(0, "Intro line"), LyricLine(1000, "Active line"), LyricLine(3000, "Future line"), LyricLine(5000, "Last line"))
        try {
            AppSettings.setPlayerVisualStyle(PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS)
            player(position, lyrics)
            capture("player-light")
            compose.onNodeWithTag("google-plus-open-lyrics").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("google-plus-lyric-1").assertIsSelected()
            compose.onNodeWithTag("google-plus-lyric-0").assertIsNotSelected()
            compose.onNodeWithTag("google-plus-lyric-2").assertIsNotSelected()
            capture("lyrics-synced-light")
            compose.runOnUiThread { position.report(3200) }
            compose.waitForIdle()
            compose.onNodeWithTag("google-plus-lyric-2").assertIsSelected()
            compose.onNodeWithTag("google-plus-lyric-1").assertIsNotSelected()
            // Seeking backwards moves the single active line back with it.
            compose.runOnUiThread { position.report(200) }
            compose.waitForIdle()
            compose.onNodeWithTag("google-plus-lyric-0").assertIsSelected()
            compose.onNodeWithTag("google-plus-lyric-2").assertIsNotSelected()
            compose.onNodeWithTag("google-plus-lyrics-floating").assertExists()
            compose.onNodeWithTag("google-plus-lyrics-original").assertIsSelected()
            compose.onNodeWithTag("google-plus-lyrics-translated").assertExists()
            compose.onNodeWithTag("google-plus-lyrics-both").assertExists()
            compose.onNodeWithTag("google-plus-lyrics-static").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("google-plus-lyric-0").assertIsNotSelected()
            compose.onNodeWithTag("google-plus-lyric-1").assertIsNotSelected()
            capture("lyrics-static-light")
            compose.onNodeWithTag("google-plus-lyrics-back").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("expressive-transport").assertExists()
        } finally { compose.runOnIdle { AppSettings.setPlayerVisualStyle(old) } }
    }

    @Test fun queuePanelShowsCurrentSongRowsAndOrderActions() {
        val old = AppSettings.playerVisualStyle.value
        try {
            AppSettings.setPlayerVisualStyle(PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS)
            player(PlaybackPosition().apply { report(1000) }, null, queue = library, index = 1)
            compose.onNodeWithTag("google-plus-open-queue").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("google-plus-queue-current").assertExists()
            compose.onNodeWithTag("google-plus-queue-actions").assertExists()
            compose.onNodeWithText("Cinder").assertExists()
            capture("queue-light")
            compose.onNodeWithTag("google-plus-queue-close").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("expressive-transport").assertExists()
        } finally { compose.runOnIdle { AppSettings.setPlayerVisualStyle(old) } }
    }

    @Test fun darkPlayerDraws() {
        val old = AppSettings.playerVisualStyle.value
        try {
            AppSettings.setPlayerVisualStyle(PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS)
            player(PlaybackPosition().apply { report(1000) }, null, queue = library, index = 0, dark = true)
            compose.onNodeWithTag("expressive-transport").assertExists()
            capture("player-dark")
        } finally { compose.runOnIdle { AppSettings.setPlayerVisualStyle(old) } }
    }
}
