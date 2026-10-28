package com.music.bitchord

import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.data.DeviceFavoriteStore
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.model.LikeStatus
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DeviceFavoriteStoreTest {
    @Test fun preferencesRestoreFavoritesAndExplicitRemovals() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filename = "test-favorites-${UUID.randomUUID()}"
        try {
            DeviceFavoriteStore.initialize(context, filename)
            LikeState.set("content://media/17", LikeStatus.LIKE)
            LikeState.set("remote-id", LikeStatus.INDIFFERENT)
            // Flush the queued apply writes before discarding the in-memory state.
            assertTrue(context.getSharedPreferences(filename, 0).edit().commit())
            LikeState.attachPersistence(emptyMap(), null)
            LikeState.clear()
            DeviceFavoriteStore.initialize(context, filename)
            assertEquals(LikeStatus.LIKE, LikeState.overrides.value["content://media/17"])
            assertEquals(LikeStatus.INDIFFERENT, LikeState.overrides.value["remote-id"])
            LikeState.seedLiked(setOf("remote-id"))
            assertEquals(LikeStatus.INDIFFERENT, LikeState.overrides.value["remote-id"])
        } finally {
            context.deleteSharedPreferences(filename)
            LikeState.attachPersistence(emptyMap(), null)
            LikeState.clear()
            DeviceFavoriteStore.initialize(context)
        }
    }
}
