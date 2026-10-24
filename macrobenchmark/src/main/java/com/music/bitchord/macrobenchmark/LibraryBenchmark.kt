package com.music.bitchord.macrobenchmark

import android.os.Build
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Physical-device measurements for the real local library. These tests never
 * seed, clear, rename or delete media. Run them on the Infinix with comparable
 * app data and artwork state; an emulator result is not a phone result.
 */
@RunWith(AndroidJUnit4::class)
class LibraryBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Before
    fun grantLocalAudioAccess() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            "android.permission.READ_MEDIA_AUDIO"
        } else {
            "android.permission.READ_EXTERNAL_STORAGE"
        }
        // Shell grant is limited to the existing audio-read permission. It
        // does not clear app data or touch the user's media files.
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm grant $TARGET_PACKAGE $permission").close()
    }

    @Test
    fun scrollLocalLibrary() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 10,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        dismissReleaseNotes(device)
        val songs = device.wait(Until.findObject(By.res(SONGS_LIST)), 20_000)
            ?: error("Local Songs list not found; open the local library and grant audio access")
        songs.setGestureMargin(device.displayWidth / 5)
        // Exercise both directions so recycled rows and artwork restoration
        // are included, rather than measuring only the first populated screen.
        repeat(4) {
            songs.fling(Direction.DOWN)
            device.waitForIdle()
        }
        repeat(4) {
            songs.fling(Direction.UP)
            device.waitForIdle()
        }
    }

    @Test
    fun libraryTabNavigation() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 10,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        dismissReleaseNotes(device)
        device.wait(Until.hasObject(By.res(SONGS_LIST)), 20_000)
        clickTab(device, "albums")
        device.waitForIdle()
        clickTab(device, "artists")
        device.waitForIdle()
        clickTab(device, "songs")
        device.wait(Until.hasObject(By.res(SONGS_LIST)), 10_000)
    }

    @Test
    fun coldStart() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = 8,
        setupBlock = { pressHome() },
    ) {
        // Dismiss the once-per-version release-notes dialog before running the
        // cold-start comparison, so every iteration measures the same launch.
        startActivityAndWait()
    }

    private fun clickTab(device: UiDevice, resourceName: String) {
        val label = targetString(resourceName)
        val tab = device.wait(Until.findObject(By.desc(label)), 5_000)
            ?: device.wait(Until.findObject(By.text(label)), 5_000)
            ?: error("Navigation tab not found: $resourceName ($label)")
        tab.click()
    }

    private fun dismissReleaseNotes(device: UiDevice) {
        val title = targetString("space_whats_new").substringBefore('%')
        if (title.isNotBlank() && device.hasObject(By.textStartsWith(title))) {
            device.findObject(By.text(targetString("back")))?.click()
            device.waitForIdle()
        }
    }

    private fun targetString(resourceName: String): String {
        val context = InstrumentationRegistry.getInstrumentation().context
        val resources = context.packageManager.getResourcesForApplication(TARGET_PACKAGE)
        val id = resources.getIdentifier(resourceName, "string", TARGET_PACKAGE)
        check(id != 0) { "Missing target string resource: $resourceName" }
        return resources.getString(id)
    }

    private companion object {
        const val TARGET_PACKAGE = "org.nexlibre.totalmusic"
        const val SONGS_LIST = "local_songs_list"
    }
}
