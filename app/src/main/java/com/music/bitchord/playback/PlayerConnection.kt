package com.music.bitchord.playback

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.os.Trace
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.NOTIFICATION_ART_PX
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.sources.SourceRegistry
import com.music.bitchord.data.sources.TrackMatcher
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.rememberIsForeground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Snapshot of playback state, driven by the MediaController. */
data class PlayerState(
    val song: Song? = null,
    val isPlaying: Boolean = false,
    /**
     * The playhead. A field rather than a value: its identity never changes, so
     * carrying it here costs no invalidation — see [PlaybackPosition].
     */
    val position: PlaybackPosition = PlaybackPosition(),
    /**
     * Left here rather than moved alongside the position: it settles once per
     * track, and [mutableStateOf] compares structurally, so the poll writing it
     * back unchanged every tick invalidates nothing.
     */
    val durationMs: Long = 0L,
    val error: String? = null,
    /** True while ExoPlayer is buffering — including our own stream-URL resolution. */
    val isLoading: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Song> = emptyList(),
    val queueIndex: Int = 0,
    /**
     * Whether the queue has somewhere to go either side of the current track.
     * Taken from the player rather than [queueIndex], so the wrap-around of
     * repeat-all is already accounted for.
     */
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    /** True while the current item is the replacement installed by a quality upgrade. */
    val isQualityUpgraded: Boolean = false,
)

/** Binds to [PlaybackService] for the lifetime of the composition. */
@Composable
fun rememberMediaController(): MediaController? {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }

    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            { controller = runCatching { future.get() }.getOrNull() },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            MediaController.releaseFuture(future)
            controller = null
        }
    }
    return controller
}

/** Routes the player-screen AutoPlay button through the playback service. */
fun MediaController.toggleAutoplay() {
    sendCustomCommand(
        SessionCommand(ACTION_TOGGLE_AUTOPLAY, Bundle.EMPTY),
        Bundle.EMPTY,
    )
}

/**
 * Routes Shuffle through the playback service so changing its state and
 * reordering the service-owned queue happen as one operation.
 */
fun MediaController.toggleShuffle() {
    sendCustomCommand(
        SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY),
        Bundle.EMPTY,
    )
}

/** Clears the previous queue's service and restart state before starting radio. */
suspend fun MediaController.beginRadioQueue() {
    sendCustomCommand(
        SessionCommand(ACTION_BEGIN_RADIO_QUEUE, Bundle.EMPTY),
        Bundle.EMPTY,
    ).await()
}

/**
 * Asks the service to go looking for a better copy of the playing track, now,
 * because the listener asked for it.
 *
 * A session command rather than a `replaceMediaItem` from here: the item has to
 * be rebuilt, the track's cached rendition has to be dropped and the automatic
 * path's verdicts about it have to be cleared, and all three live on the
 * service's side of the session — see [PlaybackService]'s `upgradeQualityNow`.
 */
fun MediaController.upgradeQuality() {
    sendCustomCommand(
        SessionCommand(ACTION_UPGRADE_QUALITY, Bundle.EMPTY),
        Bundle.EMPTY,
    )
}

/**
 * Asks the service to revert the playing track to YouTube's original stream.
 */
fun MediaController.revertToOriginal() {
    sendCustomCommand(
        SessionCommand(ACTION_REVERT_TO_ORIGINAL, Bundle.EMPTY),
        Bundle.EMPTY,
    )
}

/**
 * Asks the service to smoothly transition the playing track to another version/rendition.
 */
fun MediaController.swapToVersion(targetSong: Song) {
    sendCustomCommand(
        SessionCommand(ACTION_SWAP_VERSION, Bundle.EMPTY),
        bundleOf(EXTRA_SWAP_MEDIA_ITEM to targetSong.toSongBundle()),
    )
}

/**
 * The credited artists, read back out of the two parallel lists they travel in.
 *
 * A `Song` is a data class and a `MediaItem`'s extras are a `Bundle`, which
 * holds scalars and collections of them — so the credits cross as their names
 * and their ids side by side rather than as the list itself. A blank id is a
 * name YouTube stated without a channel behind it, which is kept so the credit
 * still reads; it simply has no page to open.
 *
 * Shorter of the two lists wins, and they are written together, so a bundle
 * that has been through something which dropped one of them yields what is left
 * rather than a list of names with no ids or ids with no names.
 */
private fun Bundle.artistsFromBundle(): List<ArtistRef> {
    val names = getStringArrayList(ARTISTS_NAMES).orEmpty()
    val ids = getStringArrayList(ARTISTS_IDS).orEmpty()
    if (names.isEmpty() || ids.isEmpty()) return emptyList()
    return names.indices.map { index ->
        ArtistRef(name = names[index], browseId = ids.getOrNull(index)?.ifBlank { null })
    }
}

private const val ARTISTS_NAMES = "artistNames"
private const val ARTISTS_IDS = "artistIds"

fun Song.toSongBundle(): Bundle = bundleOf(
    "videoId" to videoId,
    "title" to title,
    "artist" to artist,
    "thumbnailUrl" to thumbnailUrl,
    "durationText" to durationText,
    "artistId" to artistId,
    "albumId" to albumId,
    "albumName" to albumName,
    "isVideo" to isVideo,
    "isVideoOrigin" to isVideoOrigin,
    "setVideoId" to setVideoId,
    "fromAutoplay" to fromAutoplay,
    "radioName" to radioName,
    "localUri" to localUri,
    "downloadFormat" to downloadFormat,
    "localPath" to localPath,
    "sourceQuality" to sourceQuality,
    "playbackSource" to playbackSource,
    "playbackSourceType" to playbackSourceType?.name,
    "playbackSourceId" to playbackSourceId,
    "isExplicit" to (isExplicit ?: false),
    // `bundleOf` has no overload for a list of strings, so the two halves of
    // the credits go in afterwards.
).apply {
    putStringArrayList(ARTISTS_NAMES, ArrayList(artists.map { it.name }))
    putStringArrayList(ARTISTS_IDS, ArrayList(artists.map { it.browseId.orEmpty() }))
}

fun songFromBundle(b: Bundle): Song = Song(
    videoId = b.getString("videoId").orEmpty(),
    title = b.getString("title").orEmpty(),
    artist = b.getString("artist").orEmpty(),
    thumbnailUrl = b.getString("thumbnailUrl"),
    durationText = b.getString("durationText"),
    artistId = b.getString("artistId"),
    artists = b.artistsFromBundle(),
    albumId = b.getString("albumId"),
    albumName = b.getString("albumName"),
    isVideo = b.getBoolean("isVideo"),
    isVideoOrigin = b.getBoolean("isVideoOrigin"),
    setVideoId = b.getString("setVideoId"),
    fromAutoplay = b.getBoolean("fromAutoplay"),
    radioName = b.getString("radioName"),
    localUri = b.getString("localUri"),
    downloadFormat = b.getString("downloadFormat"),
    localPath = b.getString("localPath"),
    sourceQuality = b.getString("sourceQuality"),
    playbackSource = b.getString("playbackSource"),
    playbackSourceType = b.getString("playbackSourceType")?.let { runCatching { com.music.bitchord.data.model.PlaybackSourceType.valueOf(it) }.getOrNull() },
    playbackSourceId = b.getString("playbackSourceId"),
    isExplicit = if (b.containsKey("isExplicit")) b.getBoolean("isExplicit") else null,
)

/** Flushes the current radio queue to disk before reporting that it started. */
suspend fun MediaController.commitRadioQueue() {
    sendCustomCommand(
        SessionCommand(ACTION_COMMIT_RADIO_QUEUE, Bundle.EMPTY),
        Bundle.EMPTY,
    ).await()
}


/**
 * Marks the span of a queue-row drag — see [PartySync.beginQueueDrag]. Each
 * neighbour the row crosses is still its own `moveMediaItem`, sent the moment
 * it happens so the local queue and the on-screen list stay in step; this only
 * tells a jam's party sync to hold its publish until the row is dropped,
 * instead of sending one for every neighbour crossed along the way.
 */
fun MediaController.setQueueDragActive(active: Boolean) {
    sendCustomCommand(
        SessionCommand(ACTION_QUEUE_DRAG, Bundle.EMPTY),
        bundleOf(EXTRA_QUEUE_DRAG_ACTIVE to active),
    )
}

/** Mirrors the controller into Compose state, polling position while playing. */
@Composable
fun rememberPlayerState(controller: MediaController?): PlayerState {
    val position = remember { PlaybackPosition() }
    var state by remember { mutableStateOf(PlayerState(position = position)) }

    DisposableEffect(controller) {
        val player = controller ?: return@DisposableEffect onDispose {}

        // A MediaController reads each item across the session boundary. Keep
        // the converted queue until its timeline actually changes; playback,
        // buffering, repeat and metadata events do not require another O(n)
        // walk over a large playlist.
        var queueSnapshot = emptyList<Song>()

        // Set when the timeline change was the *contents* changing, which is the
        // only kind this has to convert again.
        //
        // `EVENT_TIMELINE_CHANGED` on its own is not that question. It also
        // fires for `TIMELINE_CHANGE_REASON_SOURCE_UPDATE`, which every track
        // raises as its source is prepared and reports its real duration — the
        // running order is identical, only the window it describes has filled
        // in. Rebuilding on those meant a thousand-track queue converted a
        // thousand songs out of the session for every track it loaded, on the
        // main thread, for as long as the queue kept playing.
        var queueChanged = false

        fun sync(
            error: String? = null,
            rebuildQueue: Boolean = false,
            refreshCurrentQueueItem: Boolean = false,
        ) {
            val item = player.currentMediaItem
            if (rebuildQueue) {
                val startedAt = SystemClock.elapsedRealtimeNanos()
                queueSnapshot = (0 until player.mediaItemCount)
                    .map { player.getMediaItemAt(it).toSong() }
                TrackLog.onQueuePhase(
                    item?.mediaId,
                    "controller_queue_snapshot",
                    (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000L,
                    queueSnapshot.size,
                )
            } else if (refreshCurrentQueueItem && item != null) {
                val index = player.currentMediaItemIndex
                if (index in queueSnapshot.indices) {
                    queueSnapshot = queueSnapshot.toMutableList().also { it[index] = item.toSong() }
                }
            }
            // Synced here too, so seeking while paused or buffering still moves
            // the scrubber (the poll loop only runs on play).
            position.report(player.currentPosition.coerceAtLeast(0L))
            state = state.copy(
                song = item?.toSong(),
                isPlaying = player.isPlaying,
                durationMs = player.duration.coerceAtLeast(0L),
                error = error,
                isLoading = player.playbackState == Player.STATE_BUFFERING,
                repeatMode = player.repeatMode,
                queue = queueSnapshot,
                queueIndex = player.currentMediaItemIndex,
                hasPrevious = player.hasPreviousMediaItem(),
                hasNext = player.hasNextMediaItem(),
                isQualityUpgraded = item?.mediaMetadata?.extras
                    ?.getBoolean(EXTRA_QUALITY_UPGRADED) == true,
            )
        }

        val listener = object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                // The count guard is belt and braces: a playlist change is the
                // only reason the queue can be a different length, so if it is,
                // this must convert it again whatever the reason claims.
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED ||
                    timeline.windowCount != queueSnapshot.size
                ) {
                    queueChanged = true
                }
            }
            // Every jump the player makes — a seek, a skip, a repeat starting
            // over, a stretch of silence skipped — is announced here, ahead of
            // the `onEvents` that reports where it landed. It is the only thing
            // the lyrics accept as a reason to go backwards; see PlaybackPosition.
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                position.seeks++
            }
            override fun onEvents(p: Player, events: Player.Events) = sync(
                error = state.error,
                rebuildQueue = queueChanged.also { queueChanged = false },
                refreshCurrentQueueItem = events.contains(Player.EVENT_MEDIA_METADATA_CHANGED),
            )
            override fun onPlayerErrorChanged(error: androidx.media3.common.PlaybackException?) {
                sync(error?.let { "Playback failed: ${it.errorCodeName}" })
            }
        }
        player.addListener(listener)
        sync(rebuildQueue = true)
        onDispose { player.removeListener(listener) }
    }

    // Only while the app is on screen. The poll exists to move a scrubber, and
    // a scrubber behind a locked screen is not being read — but the loop is a
    // plain `delay`, so without this it went on making two binder round-trips a
    // second to the media session for the whole time the phone was in a pocket.
    // Nothing is lost by stopping: `sync` above runs on the controller's own
    // events, and the first thing that happens on the way back is a fresh read.
    val foreground = rememberIsForeground()
    LaunchedEffect(controller, state.isPlaying, foreground) {
        while (controller != null && state.isPlaying && foreground) {
            position.report(controller.currentPosition.coerceAtLeast(0L))
            val duration = controller.duration.coerceAtLeast(0L)
            if (duration != state.durationMs) state = state.copy(durationMs = duration)
            delay(500)
        }
    }
    return state
}

/**
 * The inverse of [toMediaItem], as far as a MediaItem can carry a [Song].
 *
 * It has to round-trip the fields used by the live queue UI and playback
 * service. A field dropped here disappears as soon as a Song passes through
 * Media3, because controllers and service helpers read it back through this.
 */
fun MediaItem.toSong() = Song(
    videoId = mediaId,
    title = mediaMetadata.title?.toString().orEmpty(),
    artist = mediaMetadata.artist?.toString().orEmpty(),
    thumbnailUrl = mediaMetadata.artworkUri?.toString(),
    durationText = mediaMetadata.extras?.getString(EXTRA_DURATION),
    artistId = mediaMetadata.extras?.getString(EXTRA_ARTIST_ID),
    artists = mediaMetadata.extras?.artistsFromBundle() ?: emptyList(),
    albumId = mediaMetadata.extras?.getString(EXTRA_ALBUM_ID),
    albumName = mediaMetadata.albumTitle?.toString(),
    isExplicit = mediaMetadata.extras?.takeIf { it.containsKey(EXTRA_EXPLICIT) }
        ?.getBoolean(EXTRA_EXPLICIT),
    isVideo = mediaMetadata.extras?.getBoolean(EXTRA_IS_VIDEO) == true,
    isVideoOrigin = mediaMetadata.extras?.getBoolean(EXTRA_VIDEO_ORIGIN) == true ||
        mediaMetadata.extras?.getBoolean(EXTRA_IS_VIDEO) == true,
    setVideoId = mediaMetadata.extras?.getString(EXTRA_SET_VIDEO_ID),
    queueTier = this.queueTier,
    queueEntryId = this.queueEntryId,
    radioName = mediaMetadata.extras?.getString(EXTRA_RADIO_NAME),
    playbackSource = mediaMetadata.extras?.getString(EXTRA_PLAYBACK_SOURCE),
    playbackSourceType = mediaMetadata.extras?.getString(EXTRA_PLAYBACK_SOURCE_TYPE)
        ?.let { runCatching { PlaybackSourceType.valueOf(it) }.getOrNull() },
    playbackSourceId = mediaMetadata.extras?.getString(EXTRA_PLAYBACK_SOURCE_ID),
    localUri = mediaMetadata.extras?.getString(EXTRA_LOCAL_URI),
    localPath = mediaMetadata.extras?.getString(EXTRA_LOCAL_PATH),
)

/** @see Song.fromAutoplay */
val MediaItem.fromAutoplay: Boolean
    get() = queueTier == QueueTier.AUTOPLAY

/** @see Song.queueTier */
val MediaItem.queueTier: QueueTier
    get() {
        return when (mediaMetadata.extras?.getString(EXTRA_QUEUE_TIER)) {
            "USER_QUEUE" -> QueueTier.USER_QUEUE
            "CONTEXT" -> QueueTier.CONTEXT
            "AUTOPLAY" -> QueueTier.AUTOPLAY
            else -> if (mediaMetadata.extras?.getBoolean(EXTRA_FROM_AUTOPLAY) == true) {
                QueueTier.AUTOPLAY
            } else {
                QueueTier.CONTEXT
            }
        }
    }

/** @see Song.queueEntryId */
val MediaItem.queueEntryId: String?
    get() = mediaMetadata.extras?.getString(EXTRA_QUEUE_ENTRY_ID)

/** Public metadata keys for queue categorization and immutable queue entry identity. */
const val EXTRA_QUEUE_TIER = "bitchord.queueTier"
const val EXTRA_QUEUE_ENTRY_ID = "bitchord.queueEntryId"

/**
 * Marks a queue entry as AutoPlay's rather than the user's. Carried on the
 * MediaItem so it survives the trip through the session — the queue belongs to
 * the player, and the UI only ever sees it back through a MediaController.
 */
private const val EXTRA_FROM_AUTOPLAY = "bitchord.fromAutoplay"

/** @see Song.radioName */
private const val EXTRA_RADIO_NAME = "bitchord.radioName"

/** @see Song.playbackSource */
private const val EXTRA_PLAYBACK_SOURCE = "bitchord.playbackSource"
private const val EXTRA_PLAYBACK_SOURCE_TYPE = "bitchord.playbackSourceType"
private const val EXTRA_PLAYBACK_SOURCE_ID = "bitchord.playbackSourceId"

/**
 * The artist and album pages this track hangs under, when they are known.
 *
 * Carried so they survive the round trip through the session: the player's own
 * menu backfills them with a lookup when they are missing (see MainActivity's
 * `links`), but a queue restored after a restart, or a track read back by the
 * service, has only what the item carries.
 */
private const val EXTRA_ARTIST_ID = "bitchord.artistId"
private const val EXTRA_ALBUM_ID = "bitchord.albumId"

/** @see Song.setVideoId */
private const val EXTRA_SET_VIDEO_ID = "bitchord.setVideoId"

/** @see Song.localUri */
internal const val EXTRA_LOCAL_URI = "bitchord.localUri"

/** @see Song.localPath */
internal const val EXTRA_LOCAL_PATH = "bitchord.localPath"

/**
 * How long the track runs, as the row that queued it said.
 *
 * On the item rather than left to [MediaMetadata.durationMs] because that field
 * is the *player's* to state, and the player takes its own figure from the
 * decoder. This one is the claim a cross-source match is made on — see
 * [TrackMatcher] — and the two disagree often enough that overwriting either
 * with the other loses information. Carried so that [toSong] can give it back
 * to the live queue and matching code.
 */
private const val EXTRA_DURATION = "bitchord.durationText"
private const val EXTRA_EXPLICIT = "bitchord.explicit"
private const val EXTRA_IS_VIDEO = "bitchord.isVideo"
private const val EXTRA_VIDEO_ORIGIN = "bitchord.isVideoOrigin"

/** Set only on the replacement item produced by [QualityUpgrade]. */
const val EXTRA_QUALITY_UPGRADED = "bitchord.qualityUpgraded"

/** Whether this item was selected from a music-video row, even after conversion. */
val MediaItem.isVideoOrigin: Boolean
    get() = mediaMetadata.extras?.getBoolean(EXTRA_VIDEO_ORIGIN) == true ||
        mediaMetadata.extras?.getBoolean(EXTRA_IS_VIDEO) == true

fun MediaController.autoplaySectionStart(): Int = autoplaySectionStart(
    fromAutoplay = (0 until mediaItemCount).map { getMediaItemAt(it).fromAutoplay },
    currentIndex = currentMediaItemIndex,
)

/**
 * Custom scheme; PlaybackService resolves the real stream URL at play time.
 *
 * A video-tagged [Song] plays using the video's own audio by default. The
 * player can explicitly replace just the current item with a catalogue match.
 */
/**
 * MP4-family containers (m4a/aac/amr/wma/...) store their header or trailing
 * metadata in a way that needs backward seeking to parse, which the
 * content:// route (ContentDataSource) doesn't reliably support — the same
 * bytes read fine as a plain file. Formats like flac/mp3/ogg/webm already
 * seek correctly through content:// and are left alone.
 */
private val DIRECT_FILE_URI_EXTENSIONS = setOf(
    "m4a", "m4b", "m4p", "mp4", "aac", "3ga", "3gp", "3gpp",
    "alac", "amr", "awb", "wma", "aif", "aiff", "ac3", "dts",
    // DSF keeps its tags at the end, so the extractor reads the tail first and
    // seeks back to the audio; DFF walks every chunk to the end before it
    // starts, and seeks back the same way.
    "dsf", "dff",
)

/**
 * Aggregate counters for one queue conversion. This is deliberately optional:
 * all the other queue entry points keep the same conversion behavior without
 * paying for a probe they did not ask for.
 */
class MediaItemBuildStats {
    var sourceKeyParses: Int = 0
    var sourceKeyParseNanos: Long = 0L
    var downloadChecks: Int = 0
    var downloadCheckNanos: Long = 0L
    var localFileChecks: Int = 0
    var localFileCheckNanos: Long = 0L
}

private fun resolvePlaybackUri(
    uriString: String,
    localPath: String?,
    stats: MediaItemBuildStats? = null,
): String {
    if (localPath.isNullOrBlank() || !uriString.startsWith("content://")) return uriString
    val ext = localPath.substringAfterLast('.', "").lowercase(Locale.ROOT)
    if (ext !in DIRECT_FILE_URI_EXTENSIONS) return uriString
    val startedAt = stats?.let { SystemClock.elapsedRealtimeNanos() } ?: 0L
    val file = File(localPath)
    val resolved = if (file.exists() && file.canRead()) Uri.fromFile(file).toString() else uriString
    if (stats != null) {
        stats.localFileChecks++
        stats.localFileCheckNanos += SystemClock.elapsedRealtimeNanos() - startedAt
    }
    return resolved
}

/**
 * The `&n=&a=&d=` tail every playback URI carries: what this track is, in the
 * terms [com.music.bitchord.data.sources.TrackMatcher] compares recordings on.
 *
 * The runtime is the one of the three that can rule a candidate *out* on its
 * own, and it is only ever a hint here — a row that never carried a duration
 * simply omits it and the match is made on title and artist alone, as it was
 * before.
 */
private fun Song.matchQuery(): String = buildString {
    append("&n=").append(Uri.encode(title))
    append("&a=").append(Uri.encode(artist))
    TrackMatcher.secondsOf(durationText)?.let { append("&d=").append(it) }
    albumName?.takeIf { it.isNotBlank() }?.let { append("&l=").append(Uri.encode(it)) }
    isExplicit?.let { append("&e=").append(if (it) "1" else "0") }
    // `m` controls source routing, not player policy. A converted catalogue
    // track keeps [isVideoOrigin] for AutoMix, but is intentionally audio here
    // so the normal source-quality upgrade path can improve it.
    if (isVideo) append("&m=1")
}

fun Song.toMediaItem(stats: MediaItemBuildStats? = null): MediaItem {
    val sourceStartedAt = stats?.let { SystemClock.elapsedRealtimeNanos() } ?: 0L
    val sourceTrack = SourceRegistry.parseTrackKey(videoId)
    if (stats != null) {
        stats.sourceKeyParses++
        stats.sourceKeyParseNanos += SystemClock.elapsedRealtimeNanos() - sourceStartedAt
    }
    // A row from search or a playlist carries no file of its own, but the track
    // may still be on disk from a download — see [Downloads.saved].
    //
    // Answered *here*, where the item is built, rather than in the player's
    // stream resolver, because everything downstream decides what to do by the
    // scheme the item arrives with: [AudioCache.playbackFactory] sends file and
    // content URIs past the disk cache instead of writing a second copy of
    // them, and DefaultDataSource picks ContentDataSource off the same scheme.
    // A local URI substituted further down lands inside the half of the chain
    // that only speaks HTTP, where OkHttp rejects it as a malformed URL — which
    // is what a downloaded track played from search used to do, four times over,
    // before giving up.
    //
    // Keep explicit local references even when a file has been deleted.
    // Space Music cannot replace them with online streams: ExoPlayer must
    // report the local file error so the service can show a useful message.
    val offlineUri = if (localUri != null) {
        localUri
    } else {
        val startedAt = stats?.let { SystemClock.elapsedRealtimeNanos() } ?: 0L
        val recorded = Downloads.verifiedSavedUri(videoId)
        if (stats != null) {
            stats.downloadChecks++
            stats.downloadCheckNanos += SystemClock.elapsedRealtimeNanos() - startedAt
        }
        recorded
    }
    val uriString = offlineUri ?: when {
        videoId.startsWith("content://") || videoId.startsWith("file://") -> videoId
        // Title, artist and runtime ride along in the URI because they are what
        // a cross-source match is made on, and the resolver runs on ExoPlayer's
        // loader thread with nothing but a DataSpec in hand — see
        // [SourceResolver.resolve]. Read-ahead resolves tracks that aren't the
        // current item, so reaching back for the session's metadata isn't an
        // option either.
        sourceTrack != null -> SourceRegistry.trackUri(sourceTrack.first, sourceTrack.second)
            .let { "$it${matchQuery()}" }
        // A track the listener reverted by hand stays reverted — see
        // [OriginalVersion]. Applied here rather than at the one menu that
        // reverts, because "stays" has to mean the next queue entry built for
        // this song too: another play from a list, a restored queue, Android
        // Auto. Everything downstream reads the item's URI and nothing reads
        // the preference, so this is the only place it has to be said.
        OriginalVersion.isPinned(videoId) -> directYouTubeUri()
        // The same three fields, for the same reason, on the YouTube path: a
        // source ranked above YouTube gets offered this track before YouTube
        // resolves it — see [SourceResolver.substituteForYouTube] — and that
        // match is made on them, which the loader thread has no other way to
        // reach.
        else -> "bitchord://watch?v=$videoId${matchQuery()}"
    }
    return MediaItem.Builder()
        .setMediaId(videoId)
        .setUri(resolvePlaybackUri(uriString, localPath, stats))
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            // The release this track came off, when whoever queued it knew.
            //
            // A native field rather than an extra because Media3 bundles this
            // one across the session on its own, and because the lock screen and
            // Android Auto both draw it — a track queued from an album page had
            // the name in hand all along and was arriving at those surfaces
            // without it. It is also what the Replay's album chart is counted
            // on: read back off the player, a track with no album here is a
            // track that cannot be filed under one.
            .setAlbumTitle(albumName)
            // Sized here rather than left as stored: this is what the lock
            // screen, the notification and Android Auto draw, all of them
            // large, and none of them go back for a better copy later.
            .setArtworkUri(artworkAt(NOTIFICATION_ART_PX)?.toUri())
            // System media surfaces (One UI's Now Bar, Android Auto, Assistant)
            // classify a session by its media type; untyped sessions get treated
            // as generic audio and lose the music-specific card.
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            // What a queue entry has to carry about itself: which section of
            // the queue it belongs to, whether it is playing off the device,
            // and how long the row that queued it said it runs. The uri two
            // lines up answers the second question but does not survive the
            // trip back out — Media3 leaves a MediaItem's localConfiguration
            // out of the bundle it sends to a MediaController — so without this
            // a track playing from a file reaches the UI looking like any other
            // YouTube track, and the player's menu offers to rate, download and
            // share it.
            //
            // Set for every track rather than only the local and AutoPlay ones,
            // because the runtime applies to all of them: gated on those two, a
            // plain YouTube track carries no extras at all, so [toSong] reads
            // back a null duration and later matching loses the `&d=` it
            // depends on.
            .apply {
                if (queueTier != QueueTier.CONTEXT || queueEntryId != null || fromAutoplay ||
                    offlineUri != null || durationText != null ||
                    artistId != null || albumId != null || setVideoId != null ||
                    isExplicit != null || isVideo || isVideoOrigin || radioName != null ||
                    playbackSource != null || playbackSourceType != null || playbackSourceId != null
                ) {
                    setExtras(
                        bundleOf(
                            EXTRA_QUEUE_TIER to queueTier.name,
                            EXTRA_QUEUE_ENTRY_ID to queueEntryId,
                            EXTRA_FROM_AUTOPLAY to fromAutoplay,
                            EXTRA_RADIO_NAME to radioName,
                            EXTRA_PLAYBACK_SOURCE to playbackSource,
                            EXTRA_PLAYBACK_SOURCE_TYPE to playbackSourceType?.name,
                            EXTRA_PLAYBACK_SOURCE_ID to playbackSourceId,
                            EXTRA_LOCAL_URI to offlineUri,
                            EXTRA_LOCAL_PATH to localPath,
                            EXTRA_DURATION to durationText,
                            EXTRA_ARTIST_ID to artistId,
                            EXTRA_ALBUM_ID to albumId,
                            EXTRA_SET_VIDEO_ID to setVideoId,
                            EXTRA_EXPLICIT to isExplicit,
                            EXTRA_IS_VIDEO to isVideo,
                            EXTRA_VIDEO_ORIGIN to isVideoOrigin,
                        ),
                    )
                }
            }
            .build(),
    )
    .build()
}

/**
 * The current song reopened through YouTube alone, bypassing every substitute
 * and quality-upgrade path.  The separate rendition tag is essential: the
 * base cache key may currently contain JioSaavn or module bytes, and resuming
 * that entry as though it were a YouTube WebM corrupts the stream.
 */
fun Song.toDirectYouTubeMediaItem(): MediaItem =
    toMediaItem().buildUpon()
        .setUri(directYouTubeUri())
        .build()

private fun Song.directYouTubeUri(): String =
    "bitchord://watch?v=$videoId${matchQuery()}&$DIRECT_YOUTUBE_PARAMETER=1&q=original"

/**
 * Whether there is a YouTube upload behind this song to go back *to* — the
 * question "Revert to original" only means something for.
 *
 * Offered for any track that has one rather than only for a track a quality
 * upgrade has visibly swapped, because the swap is not the only way to end up
 * on a copy that is wrong. A source ranked above YouTube gets first refusal on
 * every track — see [SourceResolver.substituteForYouTube] — so a song can be
 * playing JioSaavn's or a module's idea of it from its very first second, with
 * nothing on screen having changed and nothing to undo. Those are precisely the
 * ones worth doubting: the match is made on title, artist and runtime, and a
 * live version, a remaster or a different mix agreeing on all three is a
 * catalogue's ordinary business. The listener is the only one who can hear that
 * it is the wrong recording, and until this was always available they had no
 * way to say so.
 *
 * The three exclusions are all "there is no such upload", not "reverting would
 * be unwise":
 *
 *  - a track queued from a module's own catalogue has no YouTube id at all,
 *    only a source-and-track key that would build a nonsense URI;
 *  - a file on the device is identified by its own `content://` or `file://`
 *    URI, for the same reason;
 *  - a music video *is* the YouTube upload, so there is nowhere for it to go.
 *    Its catalogue match is a separate control with its own way back — see
 *    `VideoAudioVersionButton`.
 */
fun Song.hasYouTubeOriginal(): Boolean =
    videoId.isNotBlank() &&
        !videoId.startsWith("content://") &&
        !videoId.startsWith("file://") &&
        !isVideo &&
        SourceRegistry.parseTrackKey(videoId) == null

/** A playback URI carrying this is explicitly requested YouTube, never a substitute. */
const val DIRECT_YOUTUBE_PARAMETER = "direct_youtube"

/**
 * Which track a playback URI is for, as a media id — the inverse of the URI
 * [toMediaItem] builds, as far as the identity goes.
 *
 * Needed because most of what this app does to a track happens somewhere that
 * has only the URI: the resolver runs on ExoPlayer's loader thread with a
 * DataSpec in hand, and read-ahead means the track being fetched is usually not
 * the one playing. That is what makes it the answer to "whose log line is this"
 * — see [com.music.bitchord.data.TrackLog.about].
 *
 * Deliberately not the cache key, which looks similar and is not the same
 * thing: that one splits a track's renditions apart on purpose and spells a
 * source-backed track differently again, so filing lines under it would scatter
 * one song's story across several names.
 */
fun mediaIdIn(uri: Uri): String? = if (uri.authority == "source") {
    val configId = uri.getQueryParameter("s")
    val trackId = uri.getQueryParameter("t")
    if (configId != null && trackId != null) SourceRegistry.trackKey(configId, trackId) else null
} else {
    uri.getQueryParameter("v")
}

/**
 * The installed index to seek to when the timeline is the same context as [songs], or null when
 * the original queue operation is required.
 *
 * This is intentionally a conservative equality check rather than an id-only check. A library
 * row can change its artwork, source, local URI, or queue tier while its video id stays the same;
 * reusing a source in any of those cases would play or display stale information. Queue-entry ids
 * are the exception: QueueCoordinator assigns fresh ids to each rebuilt context, so they are not
 * identity of the song being selected. Fields which [toMediaItem] does not serialize are compared
 * in their already-installed form for the same reason — a full rebuild would drop them too.
 *
 * The first version is deliberately limited to explicit local rows. That keeps stream routing,
 * Original Version pins and newly completed online downloads out of the equality question; those
 * queues still take the original conversion path. USER_QUEUE and AUTOPLAY are excluded as well,
 * so seekToDefaultPosition cannot change the meaning of a manually queued entry.
 */
private fun MediaController.reusableQueueIndex(songs: List<Song>, requestedIndex: Int): Int? {
    if (requestedIndex !in songs.indices || songs.isEmpty()) return null
    // Repeat-all rotates expired history to the tail instead of removing it. That needs the
    // original queue operation, not an assumptions-about-suffixes fast path.
    if (repeatMode == Player.REPEAT_MODE_ALL) return null
    if (songs.any { it.queueTier != QueueTier.CONTEXT || it.localUri == null }) return null

    val installedCount = mediaItemCount
    val installedCurrentIndex = currentMediaItemIndex
    if (installedCount <= 0 || installedCount > songs.size) return null
    if (installedCurrentIndex !in 0 until installedCount) return null

    // PlaybackService retains at most MAX_QUEUE_HISTORY entries before the current item. Thus a
    // previously installed context can be a suffix of the requested library list. No tail is
    // removed by that policy, so the size difference identifies the suffix's offset.
    val offset = songs.size - installedCount
    val targetIndex = requestedIndex - offset
    if (targetIndex !in 0 until installedCount || targetIndex == installedCurrentIndex) return null

    // A full rebuild would restore the complete list and then trim its history. A seek can only
    // trim the suffix already present. Require both operations to finish with the same head offset;
    // otherwise a backward tap could silently lose history which the original path would restore.
    val rebuiltHead = (requestedIndex - MAX_QUEUE_HISTORY).coerceAtLeast(0)
    val reusedHead = offset + (targetIndex - MAX_QUEUE_HISTORY).coerceAtLeast(0)
    if (rebuiltHead != reusedHead) return null

    val matches = (0 until installedCount).all { index ->
        getMediaItemAt(index).matchesQueueSong(songs[offset + index])
    }
    return targetIndex.takeIf { matches }
}

/** Compare the metadata and playback identity which survive a MediaItem round-trip. */
private fun MediaItem.matchesQueueSong(song: Song): Boolean {
    val current = toSong()
    // bundleOf stores a null explicit flag as a null Bundle value on some Android versions,
    // while toSong quite correctly treats a present key as a tri-state value. Read the raw value
    // so a null flag is not mistaken for an explicit "clean" flag.
    val actualExplicit = mediaMetadata.extras?.let { extras ->
        if (!extras.containsKey(EXTRA_EXPLICIT) || extras.get(EXTRA_EXPLICIT) == null) {
            null
        } else {
            extras.getBoolean(EXTRA_EXPLICIT)
        }
    }
    val comparableCurrent = current.copy(isExplicit = actualExplicit)
    val expected = song.copy(
        thumbnailUrl = song.artworkAt(NOTIFICATION_ART_PX),
        queueEntryId = current.queueEntryId,
        artists = current.artists,
        downloadFormat = current.downloadFormat,
        localDateAddedSeconds = current.localDateAddedSeconds,
        localDateModifiedSeconds = current.localDateModifiedSeconds,
        localTrackNumber = current.localTrackNumber,
        localDiscNumber = current.localDiscNumber,
        sourceQuality = current.sourceQuality,
    )
    return comparableCurrent == expected
}

/**
 * Replaces the queue with [songs] and starts it.
 *
 * Suspending because of the mapping: [toMediaItem] is not the cheap struct copy
 * it looks like — it builds a URI and a metadata bundle per track, and asks
 * [Downloads] whether the file it has on record for the track is still on disk,
 * which is a `stat` per downloaded song. One track's worth of that is nothing.
 * A playlist's worth is hundreds of milliseconds of it, and run inline it lands
 * on the frame that handles the tap, so the page freezes before the music
 * starts. Built off the main thread and handed to the player back on it, which
 * is where Media3 requires the call to be made.
 */
suspend fun MediaController.playSongs(
    songs: List<Song>,
    startIndex: Int,
    requestStartedAtMs: Long? = null,
) {
    if (songs.isEmpty()) return
    val selectedIndex = startIndex.coerceIn(songs.indices)
    val selectedVideoId = songs[selectedIndex].videoId
    // Capture the old item before leaving the main thread. Together with the
    // service's callback line this gives a user-exportable request-to-callback
    // interval for both a first start and a change to another song. Library
    // taps supply their earlier playFrom timestamp so queue preparation is
    // included as well.
    TrackLog.onQueueRequested(
        selectedVideoId,
        currentMediaItem?.mediaId,
        songs.size,
        requestStartedAtMs,
    )
    // A queue started while shuffle is on goes in shuffled rather than being
    // played out of order — see [QueueShuffle]. The track the user picked still
    // leads, so it ends up at the top instead of at [startIndex].
    val shuffled = QueueShuffle.enabled.value

    // setMediaItems synchronously creates one MediaSource per entry (Media3
    // ExoPlayerImpl.setMediaItems). When the exact same context is already installed and the
    // listener picked a different position, keep those sources and only change the selection.
    // Same-index taps deliberately fall through so the original operation still resets that
    // track to position zero and reapplies prepare/play.
    val reuseCheckStartedAtNs = SystemClock.elapsedRealtimeNanos()
    val reuseIndex = if (shuffled) null else reusableQueueIndex(songs, selectedIndex)
    TrackLog.onQueuePhase(
        videoId = selectedVideoId,
        phase = "existing_queue_check",
        phaseUs = (SystemClock.elapsedRealtimeNanos() - reuseCheckStartedAtNs) / 1_000L,
        queueSize = songs.size,
        detail = "reuse=${reuseIndex != null} installedIndex=${reuseIndex ?: -1}",
    )
    if (reuseIndex != null) {
        val reuseStartedAtNs = SystemClock.elapsedRealtimeNanos()
        // ForwardingPlayer delegates this overload straight to ExoPlayer. Unlike
        // SessionPlayer.seekTo(index, position), it cannot prune or reorder skipped entries.
        seekToDefaultPosition(reuseIndex)
        if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) prepare()
        play()
        TrackLog.onQueuePhase(
            videoId = selectedVideoId,
            phase = "existing_queue_reuse",
            phaseUs = (SystemClock.elapsedRealtimeNanos() - reuseStartedAtNs) / 1_000L,
            queueSize = songs.size,
            detail = "operation=seekToDefaultPosition",
        )
        return
    }

    val stats = MediaItemBuildStats()
    var shuffleNanos = 0L
    var conversionNanos = 0L
    // File verification belongs on IO: slow storage must not occupy Default's
    // CPU workers shared by artwork palette extraction and library calculations.
    // The TrackLog context also lets a MediaStore operation report which tap
    // caused it, without passing a logger through every converter.
    val items = withContext(Dispatchers.IO + TrackLog.about(selectedVideoId)) {
        Trace.beginSection("TotalMusic.buildQueue")
        try {
            val shuffleStartedAt = SystemClock.elapsedRealtimeNanos()
            val queue = if (shuffled) {
                QueueShuffle.startingOrder(songs, selectedIndex)
            } else {
                songs
            }
            shuffleNanos = SystemClock.elapsedRealtimeNanos() - shuffleStartedAt
            val conversionStartedAt = SystemClock.elapsedRealtimeNanos()
            val converted = queue.map {
                coroutineContext.ensureActive()
                it.toMediaItem(stats)
            }
            conversionNanos = SystemClock.elapsedRealtimeNanos() - conversionStartedAt
            converted
        } finally {
            Trace.endSection()
        }
    }
    TrackLog.onQueueBuilt(selectedVideoId, items.size)
    TrackLog.onQueuePhase(
        selectedVideoId,
        "media_item_conversion",
        conversionNanos / 1_000L,
        items.size,
        "shuffleUs=${shuffleNanos / 1_000L}" +
            " sourceKeyParses=${stats.sourceKeyParses}" +
            " sourceKeyParseUs=${stats.sourceKeyParseNanos / 1_000L}" +
            " downloadChecks=${stats.downloadChecks}" +
            " downloadCheckUs=${stats.downloadCheckNanos / 1_000L}" +
            " localFileChecks=${stats.localFileChecks}" +
            " localFileCheckUs=${stats.localFileCheckNanos / 1_000L}",
    )
    var setMediaItemsNanos = 0L
    var prepareNanos = 0L
    var playNanos = 0L
    Trace.beginSection("TotalMusic.submitQueue")
    try {
        var startedAt = SystemClock.elapsedRealtimeNanos()
        setMediaItems(items, queueStartIndex(startIndex, items.size, shuffled), 0L)
        setMediaItemsNanos = SystemClock.elapsedRealtimeNanos() - startedAt
        startedAt = SystemClock.elapsedRealtimeNanos()
        prepare()
        prepareNanos = SystemClock.elapsedRealtimeNanos() - startedAt
        startedAt = SystemClock.elapsedRealtimeNanos()
        play()
        playNanos = SystemClock.elapsedRealtimeNanos() - startedAt
        TrackLog.onQueueSubmitted(selectedVideoId)
        TrackLog.onQueuePhase(
            selectedVideoId,
            "controller_submit",
            (setMediaItemsNanos + prepareNanos + playNanos) / 1_000L,
            items.size,
            "setMediaItemsUs=${setMediaItemsNanos / 1_000L}" +
                " prepareUs=${prepareNanos / 1_000L} playUs=${playNanos / 1_000L}",
        )
    } finally {
        Trace.endSection()
    }
}

/**
 * The selected track is moved to the head when a new queue is shuffled, so
 * playback must begin there rather than at its index in the unshuffled list.
 */
internal fun queueStartIndex(requestedIndex: Int, itemCount: Int, shuffled: Boolean): Int =
    if (shuffled) 0 else requestedIndex.coerceIn(0, itemCount - 1)
