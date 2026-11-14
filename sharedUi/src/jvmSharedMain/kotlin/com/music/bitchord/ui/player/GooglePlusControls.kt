package com.music.bitchord.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.model.Song
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.music.bitchord.sharedui.resources.*
import org.jetbrains.compose.resources.stringResource

/** Only the Google Plus scene uses these measurements and timings. */
internal object GooglePlusTokens {
    val transportHeight = 68.dp
    val controlGap = 6.dp
    val toggleCorner = 12.dp
    const val stateDurationMs = 160
    const val pageDurationMs = 300
    /** Cover travel when the track changes. Provisional: not measured from the reference video. */
    const val coverSlideMs = 320
}

/**
 * What the decoder is really playing, as text: codec, sample rate, bit depth
 * or bitrate. Built only from measured values; an unknown part is left out and
 * an unknown stream yields null, so nothing here can be an invented label.
 */
internal fun googlePlusQualityText(mimeType: String?, bitrateKbps: Int?, sampleRateHz: Int?, bitDepth: Int?): String? {
    val mime = mimeType?.lowercase()?.takeIf { it.isNotBlank() }
    val codec = when {
        mime == null -> null
        mime.endsWith("flac") -> "FLAC"
        mime.endsWith("alac") -> "ALAC"
        mime.startsWith("audio/dsd") -> "DSD"
        mime == "audio/mpeg" || mime.endsWith("mp3") || mime.endsWith("mpeg-l3") -> "MP3"
        mime.endsWith("mp4a-latm") || mime.endsWith("aac") -> "AAC"
        mime.endsWith("opus") -> "Opus"
        mime.endsWith("vorbis") -> "Vorbis"
        mime.endsWith("eac3-joc") || mime.endsWith("eac3") -> "E-AC-3"
        mime.endsWith("ac3") -> "AC-3"
        mime == "audio/raw" || mime.endsWith("wav") -> "PCM"
        else -> mime.substringAfter('/').uppercase()
    }
    val lossless = NerdStats.isLosslessMime(mime) || codec == "PCM"
    val rate = sampleRateHz?.takeIf { it > 0 }?.let { hz ->
        val khz = hz / 1000.0
        (if (hz % 1000 == 0) (hz / 1000).toString() else ((khz * 10).toInt() / 10.0).toString()) + " kHz"
    }
    val depth = bitDepth?.takeIf { lossless && it > 0 }?.let { "$it bit" }
    val bitrate = bitrateKbps?.takeIf { !lossless && it > 0 }?.let { "$it kbps" }
    return listOfNotNull(codec, rate, depth, bitrate).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** The measured stream of the current track; draws nothing until the player reports one. */
@Composable
internal fun GooglePlusQuality(modifier: Modifier = Modifier) {
    val stats by NerdStats.current.collectAsStateWithLifecycle()
    val text = stats?.let { googlePlusQualityText(it.mimeType, it.bitrateKbps, it.sampleRateHz, it.bitDepth) } ?: return
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.testTag("google-plus-quality")) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private data class GooglePlusSleeveState(val song: Song, val index: Int)

/**
 * The Google Plus sleeve. The frame is the same docked artwork every rebirth
 * scene flies to the mini player; only the picture inside it travels when the
 * track changes — towards the start for the next track, back for the previous.
 */
@Composable
internal fun GooglePlusSleeve(
    song: Song, queueIndex: Int, playing: Boolean, spec: RebirthSpec,
    onNext: () -> Unit, onPrevious: () -> Unit, dockFrame: RebirthDockFrame?,
    modifier: Modifier = Modifier,
) {
    val motion = rememberRebirthMotionEnabled()
    val artScale = rebirthArtworkScale(playing = playing, motion = motion, elastic = false)
    val dock = LocalPlayerDock.current
    Box(
        modifier
            .then(if (dockFrame != null) Modifier.rebirthDockSleeve(dock, dockFrame) else Modifier)
            .scale(artScale)
            .artworkTrackSwipe(onNext, onPrevious)
            .clip(RoundedCornerShape(spec.artworkCorner))
            .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        AnimatedContent(
            targetState = GooglePlusSleeveState(song, queueIndex),
            contentKey = { it.song.videoId + "#" + (it.song.queueEntryId ?: "") },
            transitionSpec = {
                if (!motion) EnterTransition.None togetherWith ExitTransition.None
                else {
                    val direction = if (targetState.index >= initialState.index) 1 else -1
                    (slideInHorizontally(tween(GooglePlusTokens.coverSlideMs, easing = RebirthEmphasizedDecelerate)) { it * direction } +
                        fadeIn(tween(GooglePlusTokens.stateDurationMs))) togetherWith
                        (slideOutHorizontally(tween(GooglePlusTokens.coverSlideMs, easing = RebirthEmphasizedDecelerate)) { -it * direction } +
                            fadeOut(tween(GooglePlusTokens.coverSlideMs)))
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { state ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .5f))
                AsyncImage(
                    model = PlayerPlatform.host.rememberRemoteArtworkUrl(state.song),
                    contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                )
                if (state.song.videoId == song.videoId) {
                    rememberCanvasArtwork(state.song)?.let { CanvasArtworkPlayer(it, playing, Modifier.fillMaxSize()) }
                }
            }
        }
    }
}

/**
 * The queue as a panel over the player: the track that is playing on top, the
 * real queue (same rows, drag engine and callbacks as every rebirth scene) in
 * the middle, and the order toggles at the foot.
 */
@Composable
internal fun GooglePlusQueuePanel(
    song: Song, queue: List<Song>, queueIndex: Int, locked: Boolean, shuffled: Boolean, repeatMode: Int,
    spec: RebirthSpec, transport: RebirthTransport,
    onJump: (Int) -> Unit, onMove: (Int, Int) -> Unit, onRemove: (Int) -> Unit, onClear: () -> Unit,
    onDragActiveChange: (Boolean) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = colors.surfaceContainerLow,
        modifier = modifier.fillMaxSize().testTag("google-plus-queue"),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp, bottom = 6.dp)
                .size(width = 36.dp, height = 4.dp).clip(CircleShape).background(colors.onSurfaceVariant.copy(alpha = .4f)))
            Surface(shape = RoundedCornerShape(24.dp), color = colors.primaryContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = spec.gutter, vertical = 6.dp).testTag("google-plus-queue-current")) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(PlayerPlatform.host.rememberRemoteArtworkUrl(song), null,
                        Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainerHigh), contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(stringResource(Res.string.now_playing), style = spec.captionStyle, color = colors.onPrimaryContainer.copy(alpha = .8f), maxLines = 1)
                        Text(song.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            color = colors.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, style = MaterialTheme.typography.bodySmall, color = colors.onPrimaryContainer.copy(alpha = .8f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClose, Modifier.testTag("google-plus-queue-close")) {
                        Icon(Icons.Rounded.KeyboardArrowDown, stringResource(Res.string.plus_close_queue), tint = colors.onPrimaryContainer)
                    }
                }
            }
            RebirthQueuePage(
                queue = queue, queueIndex = queueIndex, locked = locked, spec = spec,
                onJump = onJump, onMove = onMove, onRemove = onRemove, onClear = onClear,
                onDragActiveChange = onDragActiveChange,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            Surface(shape = CircleShape, color = colors.surfaceContainerHigh,
                modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(.7f).padding(vertical = 10.dp).testTag("google-plus-queue-actions")) {
                Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    GooglePlusToggle(Icons.Rounded.Shuffle, shuffled, !locked,
                        stringResource(if (shuffled) Res.string.shuffle_on else Res.string.shuffle_off),
                        transport.shuffle, Modifier.weight(1f))
                    GooglePlusToggle(if (repeatMode == 1) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        repeatMode != 0, !locked,
                        stringResource(when (repeatMode) { 0 -> Res.string.repeat_off; 1 -> Res.string.repeat_one; else -> Res.string.repeat_all }),
                        transport.repeat, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun GooglePlusTransport(
    playing: Boolean, loading: Boolean, previousEnabled: Boolean, nextEnabled: Boolean,
    locked: Boolean, shuffled: Boolean, repeatMode: Int, liked: Boolean,
    transport: RebirthTransport, onLike: () -> Unit, modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = rememberRebirthMotionEnabled()
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().testTag("expressive-transport"), horizontalArrangement = Arrangement.spacedBy(GooglePlusTokens.controlGap)) {
            GooglePlusTransportButton(Icons.Rounded.SkipPrevious, stringResource(Res.string.plus_previous),
                previousEnabled && !locked, transport.previous, Modifier.weight(1f))
            FilledIconButton(
                onClick = transport.playPause,
                modifier = Modifier.weight(1f).height(GooglePlusTokens.transportHeight).testTag("google-plus-play"),
                shape = RoundedCornerShape(24.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer),
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else Crossfade(playing, animationSpec = tween(if (motion) GooglePlusTokens.stateDurationMs else 0)) { active ->
                    Icon(if (active) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        stringResource(if (active) Res.string.pause else Res.string.play), Modifier.size(32.dp))
                }
            }
            GooglePlusTransportButton(Icons.Rounded.SkipNext, stringResource(Res.string.plus_next),
                nextEnabled && !locked, transport.next, Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))
        Surface(shape = CircleShape, color = colors.surfaceContainerLow, modifier = Modifier.fillMaxWidth(.90f)) {
            Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                GooglePlusToggle(Icons.Rounded.Shuffle, shuffled, !locked,
                    stringResource(if (shuffled) Res.string.shuffle_on else Res.string.shuffle_off),
                    transport.shuffle, Modifier.weight(1f))
                GooglePlusToggle(if (repeatMode == 1) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    repeatMode != 0, !locked,
                    stringResource(when (repeatMode) { 0 -> Res.string.repeat_off; 1 -> Res.string.repeat_one; else -> Res.string.repeat_all }),
                    transport.repeat, Modifier.weight(1f))
                GooglePlusToggle(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    liked, true, stringResource(if (liked) Res.string.rebirth_remove_favorite else Res.string.rebirth_add_favorite),
                    onLike, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GooglePlusTransportButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    FilledIconButton(onClick, modifier.height(GooglePlusTokens.transportHeight), enabled = enabled, shape = CircleShape) {
        Icon(icon, label, Modifier.size(28.dp))
    }
}

@Composable
private fun GooglePlusToggle(icon: ImageVector, selected: Boolean, enabled: Boolean, label: String, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val motion = rememberRebirthMotionEnabled()
    val background by animateColorAsState(if (selected) colors.primaryContainer else colors.surfaceContainer,
        tween(if (motion) GooglePlusTokens.stateDurationMs else 0))
    FilledIconToggleButton(
        checked = selected, onCheckedChange = { onClick() }, enabled = enabled,
        modifier = modifier.height(48.dp).semantics { stateDescription = label },
        shape = RoundedCornerShape(GooglePlusTokens.toggleCorner),
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = background, checkedContainerColor = background,
            contentColor = colors.onSurface, checkedContentColor = colors.onPrimaryContainer),
    ) { Icon(icon, label, Modifier.size(24.dp)) }
}
