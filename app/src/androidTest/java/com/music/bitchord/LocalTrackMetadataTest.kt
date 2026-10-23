package com.music.bitchord

import android.content.ContentValues
import android.media.MediaScannerConnection
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import com.music.bitchord.data.LocalMediaRepository
import com.music.bitchord.data.LocalSongMetadata
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercises Android's real container tag readers and MediaStore, not mocked metadata. */
class LocalTrackMetadataTest {
    @SdkSuppress(minSdkVersion = 29)
    @Test fun scansTrackAndDiscFromMp3FlacOggAndM4a() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val resolver = context.contentResolver
        val permission = if (android.os.Build.VERSION.SDK_INT >= 33)
            "android.permission.READ_MEDIA_AUDIO" else "android.permission.READ_EXTERNAL_STORAGE"
        instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} $permission").use {
            ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> input.readBytes() }
        }
        val oldFolder = AppSettings.localMusicFolderUri.value
        val created = mutableListOf<android.net.Uri>()
        try {
            AppSettings.setLocalMusicFolderUri("")
            for ((extension, mime) in listOf("mp3" to "audio/mpeg", "flac" to "audio/flac",
                "ogg" to "audio/ogg", "m4a" to "audio/mp4")) {
                val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, "total-music-${UUID.randomUUID()}.$extension")
                    put(MediaStore.Audio.Media.MIME_TYPE, mime)
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/TotalMusic150Tests/")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                })!!
                created.add(uri)
                val encoded = instrumentation.context.assets.open("tagged-track.$extension.b64").use { it.readBytes() }
                resolver.openOutputStream(uri)!!.use { it.write(Base64.decode(encoded, Base64.DEFAULT)) }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
                val path = resolver.query(uri, arrayOf(MediaStore.Audio.Media.DATA), null, null, null)!!.use {
                    assertTrue(it.moveToFirst()); it.getString(0)
                }
                val scanned = CountDownLatch(1)
                MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf(mime)) { _, _ -> scanned.countDown() }
                assertTrue("Media scanner timed out: $extension", scanned.await(30, TimeUnit.SECONDS))
                val song = LocalMediaRepository.getLocalMusic(context).singleOrNull { it.localUri == uri.toString() }
                assertNotNull("Tagged $extension missing from device library", song)
                assertEquals("Track parsed from $extension", 1, song!!.localTrackNumber)
                assertEquals("Disc parsed from $extension", 2, song.localDiscNumber)
                assertEquals("Exact Artist", song.artist)
                val metadata = LocalSongMetadata.read(context, song).toMap()
                assertEquals("Tagged track", metadata[R.string.meta_title])
                assertTrue("Native track metadata missing: $extension", metadata.containsKey(R.string.meta_track))
            }
        } finally {
            created.forEach { resolver.delete(it, null, null) }
            AppSettings.setLocalMusicFolderUri(oldFolder)
        }
    }
}
