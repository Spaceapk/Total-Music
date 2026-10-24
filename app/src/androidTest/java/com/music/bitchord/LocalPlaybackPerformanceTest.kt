package com.music.bitchord

import android.content.ComponentName
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.PlaybackService
import com.music.bitchord.playback.playSongs
import com.music.bitchord.playback.toSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private val queuePhasePattern = Regex("""phase=([^ ]+).*phaseUs=(-?\d+).*count=(-?\d+)""")

/** Identical harness injected into both refs by CI; reports, never invents, timings. */
class LocalPlaybackPerformanceTest {
    @Test fun localQueueLatencySamples() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val audio = File(context.cacheDir, "performance-fixture.wav")
        val dataSize = 44100 * 2 * 15
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(44100).putInt(88200).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(dataSize)
        audio.writeBytes(buffer.array())
        var controller: MediaController? = null
        val rows = mutableListOf("queue_size,trial,submit_ms,is_playing_ms")
        val phaseRows = mutableListOf("queue_size,trial,phase,phase_us,count")
        try {
            var future: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
            instrumentation.runOnMainSync {
                future = MediaController.Builder(context,
                    SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
            }
            val player = future!!.get(30, TimeUnit.SECONDS)
            controller = player
            for (size in listOf(1, 731, 2000)) {
                val songs = List(size) { index ->
                    Song("perf-$index", "Local $index", "Test", null,
                        localUri = Uri.fromFile(audio).toString(), localPath = audio.absolutePath)
                }
                // Trial zero warms the service/decoder and remains in the raw output.
                repeat(8) { trial ->
                    // Unique ids keep TrackLog.forTrack scoped to this trial;
                    // the id prefix is not used by the local fixture resolver.
                    val trialSongs = songs.mapIndexed { index, song ->
                        song.copy(videoId = "perf-$size-$trial-$index")
                    }
                    val started = CountDownLatch(1)
                    val error = AtomicReference<PlaybackException?>()
                    var playingNs = 0L
                    val listener = object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            if (isPlaying) { playingNs = SystemClock.elapsedRealtimeNanos(); started.countDown() }
                        }
                        override fun onPlayerError(e: PlaybackException) { error.set(e); started.countDown() }
                    }
                    instrumentation.runOnMainSync {
                        player.stop()
                        player.clearMediaItems()
                        player.addListener(listener)
                    }
                    var tapNs = 0L
                    var submittedNs = 0L
                    try {
                        runBlocking { withContext(Dispatchers.Main) {
                            tapNs = SystemClock.elapsedRealtimeNanos()
                            player.playSongs(trialSongs, size / 2)
                            submittedNs = SystemClock.elapsedRealtimeNanos()
                        } }
                        assertTrue("Local audio timed out", started.await(30, TimeUnit.SECONDS))
                        assertNull("Playback error", error.get())
                        assertTrue(playingNs > tapNs)
                        val row = "$size,$trial,${(submittedNs - tapNs) / 1e6},${(playingNs - tapNs) / 1e6}"
                        rows.add(row)
                        Log.i("TotalMusicPerformance", row)
                        instrumentation.runOnMainSync {
                            // The service retains only a bounded played history.
                            // Verify the selected track and all upcoming tracks,
                            // rather than requiring discarded history to remain.
                            assertEquals(size - size / 2, player.mediaItemCount - player.currentMediaItemIndex)
                            assertEquals("perf-$size-$trial-${size / 2}", player.currentMediaItem!!.mediaId)
                            assertEquals("perf-$size-$trial-${size - 1}", player.getMediaItemAt(player.mediaItemCount - 1).mediaId)
                        }
                        // Separate post-callback probe for the readback used by
                        // the Compose queue snapshot. It is intentionally not
                        // included in either latency column above.
                        instrumentation.runOnMainSync {
                            val snapshotStartedAt = SystemClock.elapsedRealtimeNanos()
                            val snapshot = (0 until player.mediaItemCount)
                                .map { player.getMediaItemAt(it).toSong() }
                            phaseRows.add(
                                "$size,$trial,harness_queue_snapshot," +
                                    "${(SystemClock.elapsedRealtimeNanos() - snapshotStartedAt) / 1_000L}," +
                                    "${snapshot.size}",
                            )
                        }
                        val exported = runBlocking { TrackLog.forTrack(trialSongs[size / 2], null) }
                        exported.lineSequence()
                            .mapNotNull { queuePhasePattern.find(it) }
                            .forEach { match ->
                                phaseRows.add(
                                    "$size,$trial,${match.groupValues[1]}," +
                                        "${match.groupValues[2]},${match.groupValues[3]}",
                                )
                            }
                    } finally {
                        instrumentation.runOnMainSync { player.removeListener(listener) }
                    }
                }
            }
        } finally {
            File(context.getExternalFilesDir(null), "local-playback-performance.csv")
                .writeText(rows.joinToString("\n") + "\n")
            File(context.getExternalFilesDir(null), "local-playback-performance-phases.csv")
                .writeText(phaseRows.joinToString("\n") + "\n")
            instrumentation.runOnMainSync { controller?.stop(); controller?.clearMediaItems(); controller?.release() }
            audio.delete()
        }
    }
}
