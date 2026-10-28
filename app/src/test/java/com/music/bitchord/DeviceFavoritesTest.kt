package com.music.bitchord

import com.music.bitchord.data.LikeState
import com.music.bitchord.data.model.LikeStatus
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DeviceFavoritesTest {
    private var disk = emptyMap<String, LikeStatus>()

    @Before fun initialize() {
        LikeState.attachPersistence(emptyMap()) { disk = it.toMap() }
        LikeState.clear()
    }

    @After fun detach() {
        LikeState.attachPersistence(emptyMap(), null)
        LikeState.clear()
    }

    @Test fun addAndRemoveSurviveProcessRestoration() {
        val id = "content://media/external/audio/media/17"
        LikeState.set(id, LikeStatus.LIKE)
        assertEquals(LikeStatus.LIKE, disk[id])
        LikeState.attachPersistence(disk) { disk = it.toMap() }
        assertEquals(LikeStatus.LIKE, LikeState.overrides.value[id])
        LikeState.set(id, LikeStatus.INDIFFERENT)
        LikeState.attachPersistence(disk) { disk = it.toMap() }
        LikeState.seedLiked(setOf(id))
        assertEquals(LikeStatus.INDIFFERENT, LikeState.overrides.value[id])
    }

    @Test fun cloudRefreshAndAccountChangeCannotUndoDeviceChoice() {
        LikeState.set("removed", LikeStatus.INDIFFERENT)
        LikeState.set("kept", LikeStatus.LIKE)
        LikeState.seedLiked(setOf("removed", "cloud-only"))
        LikeState.clear()
        assertEquals(LikeStatus.INDIFFERENT, LikeState.overrides.value["removed"])
        assertEquals(LikeStatus.LIKE, LikeState.overrides.value["kept"])
        assertFalse(LikeState.overrides.value.containsKey("cloud-only"))
        assertFalse(disk.containsKey("cloud-only"))
    }

    @Test fun playerAndNotificationObserveTheSameChanges() {
        val player = LikeState.overrides
        val notification = LikeState.overrides
        LikeState.set("track", LikeStatus.LIKE)
        assertEquals(LikeStatus.LIKE, player.value["track"])
        assertEquals(player.value, notification.value)
        LikeState.set("track", LikeStatus.INDIFFERENT)
        assertEquals(LikeStatus.INDIFFERENT, notification.value["track"])
        assertEquals(disk, player.value)
    }

    @Test fun concurrentEditsDoNotLoseFavorites() {
        val writers = (1..24).map { n -> Thread { LikeState.set("track-$n", LikeStatus.LIKE) } }
        writers.forEach { it.start() }
        writers.forEach { it.join() }
        assertEquals(24, disk.size)
        assertEquals(disk, LikeState.overrides.value)
    }
}
