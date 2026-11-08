package com.music.bitchord

import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.data.DeviceFavoriteStore
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.TotalMusicSettings
import com.music.bitchord.ui.player.PlayerVisualStyle
import org.junit.Assert.*
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** CI invokes these methods in separate instrumentations with am force-stop between them. */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FavoriteProcessRestartTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val filename = "test-182-process-favorites"

    @Test fun aWriteBeforeProcessDeath() {
        val backup = context.getSharedPreferences("test-182-process-backup", 0)
        assertTrue(backup.edit().clear()
            .putString("style", AppSettings.playerVisualStyle.value.name)
            .putBoolean("expressive", TotalMusicSettings.expressive.value).putBoolean("seeded", true).commit())
        DeviceFavoriteStore.initialize(context, filename)
        LikeState.set("restart-liked", LikeStatus.LIKE)
        LikeState.set("restart-removed", LikeStatus.INDIFFERENT)
        assertTrue(context.getSharedPreferences(filename, 0).edit().commit())
        AppSettings.setPlayerVisualStyle(PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS)
        TotalMusicSettings.setExpressive(true)
        assertTrue(context.getSharedPreferences("bitchord_settings", 0).edit().commit())
    }

    @Test fun bReadInNewProcessAndCleanUp() {
        val backup = context.getSharedPreferences("test-182-process-backup", 0)
        assertTrue("Missing seed: this phase must follow the force-stop", backup.getBoolean("seeded", false))
        try {
            // These two values were loaded by Application in the new process, not set by this method.
            assertEquals(PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS, AppSettings.playerVisualStyle.value)
            assertTrue(TotalMusicSettings.expressive.value)
            DeviceFavoriteStore.initialize(context, filename)
            assertEquals(LikeStatus.LIKE, LikeState.overrides.value["restart-liked"])
            assertEquals(LikeStatus.INDIFFERENT, LikeState.overrides.value["restart-removed"])
            LikeState.seedLiked(setOf("restart-removed"))
            assertEquals(LikeStatus.INDIFFERENT, LikeState.overrides.value["restart-removed"])
        } finally {
            AppSettings.setPlayerVisualStyle(PlayerVisualStyle.valueOf(backup.getString("style", "ORIGINAL")!!))
            TotalMusicSettings.setExpressive(backup.getBoolean("expressive", false))
            LikeState.attachPersistence(emptyMap(), null)
            LikeState.clear()
            DeviceFavoriteStore.initialize(context)
            context.deleteSharedPreferences(filename)
            context.deleteSharedPreferences("test-182-process-backup")
        }
    }
}
