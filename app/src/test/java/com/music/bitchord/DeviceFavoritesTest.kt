package com.music.bitchord

import com.music.bitchord.data.LikeState
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.FavoriteEntry
import com.music.bitchord.data.settings.FavoriteSort
import com.music.bitchord.ui.rebirth.sortFavorites
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

    @Test fun metadataAndTimestampsShareOnePersistenceSnapshot() {
        var savedRatings = emptyMap<String, LikeStatus>()
        var savedEntries = emptyMap<String, FavoriteEntry>()
        var now = 1000L
        LikeState.attachStorePersistence(emptyMap(), emptyMap(), { r, e -> savedRatings = r; savedEntries = e }, { now })
        val song = Song("remote-1", "Title", "Artist", "art", durationText = "3:21")
        LikeState.set(song.videoId, LikeStatus.LIKE, song)
        assertEquals(1000L, savedEntries.getValue(song.videoId).addedAtMillis)
        now = 2000L
        LikeState.set(song.videoId, LikeStatus.LIKE, song.copy(title = "Updated"))
        assertEquals(1000L, savedEntries.getValue(song.videoId).addedAtMillis)
        LikeState.attachStorePersistence(savedRatings, savedEntries, { r, e -> savedRatings = r; savedEntries = e }, { now })
        assertEquals("Updated", LikeState.entries.value.getValue(song.videoId).song.title)
        LikeState.set(song.videoId, LikeStatus.INDIFFERENT)
        assertFalse(savedEntries.containsKey(song.videoId))
        assertEquals(LikeStatus.INDIFFERENT, savedRatings[song.videoId])
    }

    @Test fun favoriteSortsByDateTitleAndArtist() {
        val a = Song("a", "Zebra", "Alpha", null)
        val b = Song("b", "Apple", "Beta", null)
        val added = mapOf("a" to 2L, "b" to 1L)
        assertEquals(listOf("a", "b"), sortFavorites(listOf(b, a), FavoriteSort.DATE_ADDED, added).map { it.videoId })
        assertEquals(listOf("b", "a"), sortFavorites(listOf(a, b), FavoriteSort.TITLE).map { it.videoId })
        assertEquals(listOf("a", "b"), sortFavorites(listOf(b, a), FavoriteSort.ARTIST).map { it.videoId })
    }

    @Test fun concurrentEditsDoNotLoseFavorites() {
        val writers = (1..24).map { n -> Thread { LikeState.set("track-$n", LikeStatus.LIKE) } }
        writers.forEach { it.start() }
        writers.forEach { it.join() }
        assertEquals(24, disk.size)
        assertEquals(disk, LikeState.overrides.value)
    }
}
