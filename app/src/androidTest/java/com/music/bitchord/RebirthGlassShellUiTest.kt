package com.music.bitchord

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.player.PlayerVisualStyle
import com.music.bitchord.ui.rebirth.RebirthLocalExperience
import org.junit.Rule
import org.junit.Test

/**
 * The rebirth shell, composed and drawn on a real device, with Settings opened
 * over it and closed again.
 *
 * This is the 1.8.0 crash as a test rather than as a transcript. The Liquid
 * Glass+ page used to be recorded into a backdrop layer while the floating
 * capsule inside it sampled that same layer, so the layer contained a draw of
 * itself. RenderThread followed that until the stack was gone and the process
 * died with SIGSEGV in `RenderNode::prepareTreeImpl` — a native death, which
 * means the way this test fails is that the instrumentation process goes down
 * with the app. A JVM test cannot see it (there is no RenderThread and no
 * RenderNode), and neither can a tap-driven probe, which can only report that
 * the app was already gone.
 *
 * Drawing is the operation under test, so the clock is stopped and advanced by
 * hand: `waitForIdle` alone would let the first frame arrive whenever it liked,
 * and a crash then would be reported against whichever assertion ran next.
 */
class RebirthGlassShellUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val songs = listOf(
        Song("glass-1", "Glass One", "A Band", null, albumName = "Glass album"),
        Song("glass-2", "Glass Two", "A Band", null, albumName = "Glass album"),
    )

    @Test
    fun liquidGlassShellDrawsAndSurvivesTheSettingsRoundTrip() {
        drawShellAndReturnFromSettings(PlayerVisualStyle.LIQUID_GLASS_PLUS)
    }

    /** The control: this style never recorded a layer, so it always passed. */
    @Test
    fun expressiveShellDrawsAndSurvivesTheSettingsRoundTrip() {
        drawShellAndReturnFromSettings(PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS)
    }

    private fun drawShellAndReturnFromSettings(style: PlayerVisualStyle) {
        // The recording is what crashed, so make sure it is on: glass effects
        // default to on, and "reduce dynamic blur" is the switch that turns the
        // whole path off again.
        AppSettings.setPlayerGlassEffects(true)
        AppSettings.setReduceDynamicBlur(false)

        val settingsOpen = mutableStateOf(false)
        compose.mainClock.autoAdvance = false

        compose.setContent {
            MaterialTheme {
                if (settingsOpen.value) {
                    // Stands in for the Settings screen: the shell is out of the
                    // composition, exactly as it is while a full-screen route
                    // covers it.
                    Box(Modifier.fillMaxSize())
                } else {
                    RebirthLocalExperience(
                        style = style,
                        songs = songs,
                        currentSong = null,
                        isPlaying = false,
                        loading = false,
                        favoriteIds = emptySet(),
                        initialQuery = "",
                        onPlay = { _, _ -> },
                        onSongMenu = { },
                        onSettings = { settingsOpen.value = true },
                        onPlayPause = { },
                        onNext = { },
                        onExpandPlayer = { },
                    )
                }
            }
        }

        settle()

        // Drawn once and still in the composition: pre-1.8.1 this is where the
        // process died.
        compose.onNodeWithContentDescription(label(R.string.settings)).assertExists()

        compose.onNodeWithContentDescription(label(R.string.settings)).performClick()
        settle()
        compose.runOnUiThread { settingsOpen.value = false }
        settle()

        // Composed again after being out of the tree, which is the redraw the
        // report describes as "it closes when I come back from Settings".
        compose.onNodeWithContentDescription(label(R.string.settings)).assertExists()
    }

    /**
     * A few frames with the clock under our control, then let the rule idle.
     *
     * The frames are what draw; the idle check then fails loudly if anything
     * was left pending, rather than the test passing over a screen that never
     * reached the screen.
     */
    private fun settle() {
        repeat(4) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    private fun label(id: Int): String = compose.activity.getString(id)
}
