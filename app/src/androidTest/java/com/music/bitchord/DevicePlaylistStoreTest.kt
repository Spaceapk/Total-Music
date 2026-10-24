package com.music.bitchord

import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.data.PlaylistSort
import com.music.bitchord.data.DevicePlaylistStore
import com.music.bitchord.data.model.Song
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DevicePlaylistStoreTest {
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
            store.setSort(ids[1], PlaylistSort.ARTIST)
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
}
