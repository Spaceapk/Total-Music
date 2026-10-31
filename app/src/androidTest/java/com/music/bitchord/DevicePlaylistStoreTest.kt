package com.music.bitchord

import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.data.PlaylistSort
import com.music.bitchord.data.DevicePlaylistStore
import com.music.bitchord.data.model.Song
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DevicePlaylistStoreTest {
    @Test fun bulkMovesAndRemovalSurviveReloadWithoutDeletingAudioOrOtherLists() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filename = "bulk-${UUID.randomUUID()}.json"
        val audio = java.io.File(context.filesDir, "keep-${UUID.randomUUID()}.mp3")
        try {
            audio.writeText("original audio")
            val a = Song("a", "A", "Artist", null, localUri = android.net.Uri.fromFile(audio).toString(), albumName = "Z", localTrackNumber = 1, localYear = 2000)
            val b = Song("b", "B", "Artist", null, localUri = "file:///b.mp3", albumName = "A", localTrackNumber = 2, localYear = 2002)
            val c = Song("c", "C", "Artist", null, localUri = "file:///c.mp3", albumName = "A", localTrackNumber = 1, localYear = 2002)
            val store = DevicePlaylistStore(context, filename)
            store.createWithSongs("First", listOf(b, a, c))
            store.createWithSongs("Other", listOf(a, b, c))
            val id = store.playlists.value.first().id
            store.setSort(id, PlaylistSort.ALBUM_ASC)
            store.moveSongs(id, setOf(b.localUri!!, c.localUri!!), true)
            var reloaded = DevicePlaylistStore(context, filename).playlists.value
            assertEquals(listOf("c", "b", "a"), reloaded.first().songs.map { it.videoId })
            assertEquals(PlaylistSort.MANUAL, reloaded.first().sort)
            assertEquals(2000, reloaded.first().songs.last().localYear)
            store.removeSongs(id, setOf(a.localUri!!, c.localUri!!))
            reloaded = DevicePlaylistStore(context, filename).playlists.value
            assertEquals(listOf("b"), reloaded.first().songs.map { it.videoId })
            assertEquals(listOf(b.localUri), reloaded.first().addedUris)
            assertEquals(3, reloaded.last().songs.size)
            store.removeSongs(id, setOf(b.localUri!!))
            assertTrue(DevicePlaylistStore(context, filename).playlists.value.first().songs.isEmpty())
            store.delete(id)
            assertEquals("Other", DevicePlaylistStore(context, filename).playlists.value.single().name)
            assertEquals("original audio", audio.readText())
        } finally { context.deleteFile(filename); audio.delete() }
    }

    @Test fun editsSurviveReloadAndKeepOfflineLocations() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filename = "test-${UUID.randomUUID()}.json"
        try {
            val store = DevicePlaylistStore(context, filename)
            val a = Song("local:1", "First", "Artist", null, localUri = "content://media/external/audio/media/1", localPath = "/storage/emulated/0/Music/a.flac", localTrackNumber = 3, localDiscNumber = 2)
            val b = Song("local:2", "Second", "Artist", null, localUri = "file:///storage/emulated/0/Music/b.flac")
            store.createWithSongs("  My files  ", listOf(a, b, a))
            val id = store.playlists.value.single().id
            store.add(id, listOf(a, b, a))
            assertEquals(2, store.playlists.value.single().songs.size)
            store.reorder(id, listOf(b.localUri!!, a.localUri!!))
            assertTrue(runCatching { store.reorder(id, listOf(a.localUri!!, a.localUri!!)) }.isFailure)
            assertTrue(runCatching { store.reorder(id, listOf(a.localUri!!)) }.isFailure)
            store.rename(id, "Offline")
            val reloaded = DevicePlaylistStore(context, filename)
            assertEquals("Offline", reloaded.playlists.value.single().name)
            assertEquals(listOf(b.localUri, a.localUri), reloaded.playlists.value.single().songs.map { it.localUri })
            assertEquals(a.localPath, reloaded.playlists.value.single().songs[1].localPath)
            assertEquals(3, reloaded.playlists.value.single().songs[1].localTrackNumber)
            assertEquals(2, reloaded.playlists.value.single().songs[1].localDiscNumber)
            reloaded.remove(id, 0)
            assertEquals(a.localUri, DevicePlaylistStore(context, filename).playlists.value.single().songs.single().localUri)
            reloaded.move(id, -1, 20)
            assertEquals(1, reloaded.playlists.value.single().songs.size)
            reloaded.delete(id)
            assertTrue(DevicePlaylistStore(context, filename).playlists.value.isEmpty())
        } finally { context.deleteFile(filename) }
    }
    @Test fun independentSortSurvivesReloadWithoutChangingManualOrderOrOtherLists() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filename = "sort-${UUID.randomUUID()}.json"
        try {
            val z = Song("z", "Zulu", "Alpha", null, localUri = "file:///z.mp3")
            val a = Song("a", "Alpha", "Zulu", null, localUri = "file:///a.mp3")
            val b = Song("b", "Beta", "Beta", null, localUri = "file:///b.mp3")
            val store = DevicePlaylistStore(context, filename)
            repeat(3) { store.createWithSongs("List $it", listOf(z, a)) }
            val ids = store.playlists.value.map { it.id }
            store.setSort(ids[0], PlaylistSort.TITLE_ASC)
            store.setSort(ids[1], PlaylistSort.ARTIST_ASC)
            store.setSort(ids[2], PlaylistSort.RECENTLY_ADDED)
            store.add(ids[2], listOf(b, z)) // Existing membership must not become recent again.
            store.reorder(ids[2], listOf(b.localUri!!, z.localUri!!, a.localUri!!))
            store.setSort(ids[2], PlaylistSort.RECENTLY_ADDED)
            val lists = DevicePlaylistStore(context, filename).playlists.value
            assertEquals(listOf("a", "z"), lists[0].sortedSongs().map { it.videoId })
            assertEquals(listOf("z", "a"), lists[1].sortedSongs().map { it.videoId })
            assertEquals(listOf("b", "a", "z"), lists[2].sortedSongs().map { it.videoId })
            assertEquals(listOf(z, a), lists[0].songs) // Sort never overwrites manual order.
            store.removeSong(ids[0], a.localUri!!)
            assertEquals(listOf(z), store.playlists.value[0].songs)
            assertEquals(listOf(z, a), store.playlists.value[1].songs)
            store.removeSong(ids[2], a.localUri!!)
            store.add(ids[2], listOf(a))
            assertEquals("a", DevicePlaylistStore(context, filename).playlists.value[2].sortedSongs().first().videoId)
        } finally { context.deleteFile(filename) }
    }

    @Test fun legacyPlaylistsMigrateWithoutLosingTracks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filename = "legacy-${UUID.randomUUID()}.json"
        try {
            java.io.File(context.filesDir, filename).writeText("""[{"id":"old","name":"Legacy","songs":[{"id":"b","title":"Beta","artist":"A","uri":"file:///b.mp3"},{"id":"a","title":"Alpha","artist":"Z","uri":"file:///a.mp3"}]}]""")
            val store = DevicePlaylistStore(context, filename)
            assertEquals(PlaylistSort.MANUAL, store.playlists.value.single().sort)
            assertEquals(listOf("b", "a"), store.playlists.value.single().sortedSongs().map { it.videoId })
            store.setSort("old", PlaylistSort.TITLE_DESC)
            val reloaded = DevicePlaylistStore(context, filename).playlists.value.single()
            assertEquals(PlaylistSort.TITLE_DESC, reloaded.sort)
            assertEquals(2, reloaded.songs.size)
            assertEquals(listOf("file:///b.mp3", "file:///a.mp3"), reloaded.addedUris)
        } finally { context.deleteFile(filename) }
    }

    @Test fun legacyArtistSortBecomesArtistAscendingOnReload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filename = "legacy-artist-${UUID.randomUUID()}.json"
        try {
            java.io.File(context.filesDir, filename).writeText(
                """[{"id":"old","name":"Legacy artist","sort":"ARTIST","songs":[""" +
                    """{"id":"z","title":"Zulu","artist":"Alpha","uri":"file:///z.mp3"},""" +
                    """{"id":"a","title":"Alpha","artist":"Zulu","uri":"file:///a.mp3"}]}]""",
            )
            // The JSON above is intentionally split only for line length; the
            // persisted name is the pre-1.8.4 "ARTIST".
            val playlist = DevicePlaylistStore(context, filename).playlists.value.single()
            assertEquals(PlaylistSort.ARTIST_ASC, playlist.sort)
            assertEquals(listOf("z", "a"), playlist.sortedSongs().map { it.videoId })
        } finally { context.deleteFile(filename) }
    }

    @Test fun interruptedWriteRecoversBackupWithoutDeletingOriginalAudio() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filename = "test-${UUID.randomUUID()}.json"
        val audio = java.io.File(context.filesDir, "original-${UUID.randomUUID()}.flac")
        val base = java.io.File(context.filesDir, filename)
        val backup = java.io.File(context.filesDir, "$filename.bak")
        try {
            audio.writeText("original bytes must survive playlist edits")
            val song = Song("local-backup", "Original", "Test", null,
                localUri = android.net.Uri.fromFile(audio).toString(), localPath = audio.absolutePath)
            val store = DevicePlaylistStore(context, filename)
            store.createWithSongs("Recoverable", listOf(song))
            val id = store.playlists.value.single().id
            assertTrue(base.renameTo(backup))
            val recovered = DevicePlaylistStore(context, filename)
            assertEquals(song.localUri, recovered.playlists.value.single().songs.single().localUri)
            recovered.remove(id, 0)
            recovered.delete(id)
            assertEquals("original bytes must survive playlist edits", audio.readText())
        } finally {
            context.deleteFile(filename); backup.delete(); audio.delete()
        }
    }
    @Test fun onlineTracksAndBlankNamesAreRejected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filename = "test-${UUID.randomUUID()}.json"
        try {
            val store = DevicePlaylistStore(context, filename)
            assertTrue(runCatching { store.create(" ") }.isFailure)
            assertTrue(runCatching { store.createWithSongs("Remote", listOf(Song("online", "Online", "Artist", null))) }.isFailure)
            assertTrue(store.playlists.value.isEmpty())
            store.create("Local")
            val id = store.playlists.value.single().id
            assertTrue(runCatching { store.add(id, listOf(Song("online", "Online", "Artist", null))) }.isFailure)
            assertTrue(DevicePlaylistStore(context, filename).playlists.value.single().songs.isEmpty())
        } finally { context.deleteFile(filename) }
    }

    @Test fun draggingUsesTheVisibleOrderAndStoresItAsManual() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filename = "visible-${UUID.randomUUID()}.json"
        try {
            val zulu = Song("z", "Zulu", "Alpha", null, localUri = "file:///z.mp3")
            val alpha = Song("a", "Alpha", "Alpha", null, localUri = "file:///a.mp3")
            val beta = Song("b", "Beta", "Alpha", null, localUri = "file:///b.mp3")
            val store = DevicePlaylistStore(context, filename)
            // Manual order is Zulu, Alpha, Beta; the visible title order is
            // Alpha, Beta, Zulu.
            store.createWithSongs("Title order", listOf(zulu, alpha, beta))
            val id = store.playlists.value.single().id
            store.setSort(id, PlaylistSort.TITLE_ASC)
            assertEquals(
                listOf("a", "b", "z"),
                store.playlists.value.single().sortedSongs().map { it.videoId },
            )
            // Carry the first visible row (Alpha) past Beta: row 0 -> row 1.
            store.move(id, 0, 1)
            val moved = store.playlists.value.single()
            assertEquals(PlaylistSort.MANUAL, moved.sort)
            assertEquals(listOf("b", "a", "z"), moved.songs.map { it.videoId })
            assertEquals(listOf("b", "a", "z"), moved.sortedSongs().map { it.videoId })
            // The persisted file agrees after a reload, and membership dates
            // are untouched by a reorder.
            val reloaded = DevicePlaylistStore(context, filename).playlists.value.single()
            assertEquals(PlaylistSort.MANUAL, reloaded.sort)
            assertEquals(listOf("b", "a", "z"), reloaded.songs.map { it.videoId })
            assertEquals(setOf("file:///z.mp3", "file:///a.mp3", "file:///b.mp3"), reloaded.addedUris.toSet())
            // A stale or impossible move must not write anything.
            store.move(id, 0, 9)
            store.move(id, -1, 1)
            assertEquals(listOf("b", "a", "z"), store.playlists.value.single().songs.map { it.videoId })
            assertEquals(PlaylistSort.MANUAL, store.playlists.value.single().sort)
        } finally { context.deleteFile(filename) }
    }

    @Test fun albumOrderSeparatesHomonymousAlbumsAndKeepsDiscsTogether() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filename = "albums-${UUID.randomUUID()}.json"
        try {
            val store = DevicePlaylistStore(context, filename)
            val alphaTwo = Song("a2", "Alpha two", "Alpha", null, albumName = "Greatest Hits", localUri = "file:///a2.mp3", localTrackNumber = 2)
            val alphaOne = Song("a1", "Alpha one", "Alpha", null, albumName = "Greatest Hits", localUri = "file:///a1.mp3", localTrackNumber = 1)
            val zuluTwo = Song("z2", "Zulu two", "Zulu", null, albumName = "Greatest Hits", localUri = "file:///z2.mp3", localTrackNumber = 2)
            val zuluOne = Song("z1", "Zulu one", "Zulu", null, albumName = "Greatest Hits", localUri = "file:///z1.mp3", localTrackNumber = 1)
            val discTwo = Song("d2", "Disc two", "Alpha", null, albumName = "Set", localUri = "file:///d2.mp3", localTrackNumber = 1, localDiscNumber = 2)
            val discOneTen = Song("d1t10", "Ten", "Alpha", null, albumName = "Set", localUri = "file:///d1t10.mp3", localTrackNumber = 10)
            val discOneTwo = Song("d1t2", "Two", "Alpha", null, albumName = "Set", localUri = "file:///d1t2.mp3", localTrackNumber = 2)
            val untagged = Song("x", "No track", "Alpha", null, albumName = "Set", localUri = "file:///x.mp3")
            store.createWithSongs("Albums", listOf(zuluTwo, discTwo, alphaTwo, untagged, zuluOne, discOneTen, alphaOne, discOneTwo))
            val id = store.playlists.value.single().id
            store.setSort(id, PlaylistSort.ALBUM_ASC)
            val ordered = store.playlists.value.single().sortedSongs().map { it.videoId }
            // With no year tags, groups keep first-seen order: Zulu, Set,
            // Alpha. Each group's discs and tracks remain numeric.
            assertEquals(listOf("z1", "z2", "d1t2", "d1t10", "d2", "x", "a1", "a2"), ordered)
            val reloaded = DevicePlaylistStore(context, filename).playlists.value.single()
            assertEquals(PlaylistSort.ALBUM_ASC, reloaded.sort)
            assertEquals(ordered, reloaded.sortedSongs().map { it.videoId })
            store.setSort(id, PlaylistSort.TRACK_DESC)
            val descending = DevicePlaylistStore(context, filename).playlists.value.single()
            assertEquals(PlaylistSort.TRACK_DESC, descending.sort)
            assertEquals("x", descending.sortedSongs().last().videoId)
        } finally { context.deleteFile(filename) }
    }

    @Test fun aLargePlaylistCanBeReorderedWithoutLosingOrDuplicatingTracks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filename = "large-${UUID.randomUUID()}.json"
        try {
            val songs = (0 until 300).map { index ->
                Song(
                    "s$index", "Song ${(index * 41) % 300}", "Artist ${index % 17}", null,
                    albumName = "Album ${index % 23}", localUri = "file:///s$index.mp3",
                    localTrackNumber = index % 30 + 1, localDiscNumber = index % 2 + 1,
                )
            }
            val store = DevicePlaylistStore(context, filename)
            store.createWithSongs("Large", songs)
            val id = store.playlists.value.single().id
            store.setSort(id, PlaylistSort.ALBUM_ASC)
            val visible = store.playlists.value.single().sortedSongs().map { it.videoId }
            // Carry the first album's first row all the way to the end: more
            // than one screen of rows, and more than the old one-row buttons
            // could ever move.
            store.move(id, 0, visible.lastIndex)
            val moved = store.playlists.value.single()
            assertEquals(300, moved.songs.size)
            assertEquals(300, moved.songs.map { it.videoId }.toSet().size)
            assertEquals(PlaylistSort.MANUAL, moved.sort)
            val expected = visible.drop(1) + visible.first()
            assertEquals(expected, moved.songs.map { it.videoId })
            val reloaded = DevicePlaylistStore(context, filename).playlists.value.single()
            assertEquals(expected, reloaded.songs.map { it.videoId })
            assertEquals(300, reloaded.addedUris.toSet().size)
        } finally { context.deleteFile(filename) }
    }

    @Test fun trackNumberOrderIsPersistedPerPlaylist() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filename = "tracks-${UUID.randomUUID()}.json"
        try {
            val store = DevicePlaylistStore(context, filename)
            store.createWithSongs("One", listOf(Song("1", "One", "A", null, localUri = "file:///1.mp3")))
            store.createWithSongs("Two", listOf(Song("2", "Two", "A", null, localUri = "file:///2.mp3")))
            val first = store.playlists.value[0].id
            val second = store.playlists.value[1].id
            store.setSort(first, PlaylistSort.TRACK_DESC)
            val reloaded = DevicePlaylistStore(context, filename).playlists.value
            assertEquals(PlaylistSort.TRACK_DESC, reloaded.first { it.id == first }.sort)
            assertEquals(PlaylistSort.MANUAL, reloaded.first { it.id == second }.sort)
        } finally { context.deleteFile(filename) }
    }
}
