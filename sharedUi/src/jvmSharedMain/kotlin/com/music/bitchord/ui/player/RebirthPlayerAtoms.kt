package com.music.bitchord.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.lyrics_loading_lines
import com.music.bitchord.sharedui.resources.lyrics_not_available
import com.music.bitchord.sharedui.resources.no_lyrics_for_track
import com.music.bitchord.sharedui.resources.rebirth_clear_added
import com.music.bitchord.sharedui.resources.rebirth_next_up_empty
import com.music.bitchord.sharedui.resources.rebirth_queue_tracks
import com.music.bitchord.sharedui.resources.rebirth_romanize
import com.music.bitchord.sharedui.resources.rebirth_translate
import com.music.bitchord.sharedui.resources.remove_from_queue
import com.music.bitchord.sharedui.resources.up_next
import com.music.bitchord.ui.haptics.rememberHaptics
import org.jetbrains.compose.resources.stringResource

/**
 * What both rebirth scenes drive the session with.
 *
 * A player scene owns no playback state of its own: every button here is the
 * caller's callback, unchanged from the original player's. The point of the
 * two scenes is what a screen looks like, never what a control does.
 */
internal data class RebirthTransport(
    val playPause: () -> Unit,
    val previous: () -> Unit,
    val next: () -> Unit,
    val shuffle: () -> Unit,
    val repeat: () -> Unit,
    /** A fraction of the duration, as the scrubber reports it. */
    val seek: (Float) -> Unit,
)

/** `m:ss`, or `h:mm:ss` past an hour — the same shape the rest of the app uses. */
internal fun rebirthTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L)) / 1000L
    val seconds = total % 60L
    val minutes = (total / 60L) % 60L
    val hours = total / 3600L
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

/**
 * The scrubber.
 *
 * The handle follows the finger rather than the playhead while it is down: the
 * caller's seek is answered by the session some time later, and a bar that
 * springs back to the old position in between is the one thing everybody
 * notices about a bad player. [pending] is dropped as soon as the track or the
 * reported duration changes, so it can never outlive the track it was dragged
 * on — see the same reasoning in [PlayerScrubState].
 */
@Composable
internal fun RebirthSeekBar(
    position: PlaybackPosition,
    durationMs: Long,
    enabled: Boolean,
    spec: RebirthSpec,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    trackId: String = "",
) {
    var pending by remember(trackId, durationMs) { mutableStateOf<Float?>(null) }
    val fraction = if (durationMs > 0L) {
        (position.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(modifier.fillMaxWidth()) {
        Slider(
            value = pending ?: fraction,
            onValueChange = { pending = it },
            onValueChangeFinished = {
                pending?.let(onSeek)
                pending = null
            },
            enabled = enabled && durationMs > 0L,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(rebirthTime(position.positionMs), style = spec.captionStyle)
            Text(rebirthTime(durationMs), style = spec.captionStyle)
        }
    }
}

/**
 * Play, skip and the two toggles.
 *
 * [expanded] is the expressive scene's wider main button: a Material "play
 * pill" rather than the round glass one. The callbacks are identical either
 * way, which is the only reason the two scenes can share this at all.
 */
@Composable
internal fun RebirthTransportRow(
    playing: Boolean,
    loading: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    locked: Boolean,
    shuffled: Boolean,
    repeatMode: Int,
    spec: RebirthSpec,
    transport: RebirthTransport,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconToggleButton(
            checked = shuffled,
            onCheckedChange = { transport.shuffle() },
            enabled = !locked,
        ) {
            Icon(
                Icons.Rounded.Shuffle,
                contentDescription = null,
                tint = if (shuffled) primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = transport.previous, enabled = previousEnabled && !locked) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = null)
        }
        FilledIconButton(
            onClick = transport.playPause,
            shape = RoundedCornerShape(spec.controlCorner),
            modifier = if (expanded) {
                Modifier.width(88.dp).height(64.dp)
            } else {
                Modifier.size(64.dp)
            },
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = primary, contentColor = onPrimary),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                )
            }
        }
        IconButton(onClick = transport.next, enabled = nextEnabled && !locked) {
            Icon(Icons.Rounded.SkipNext, contentDescription = null)
        }
        IconToggleButton(
            checked = repeatMode != 0,
            onCheckedChange = { transport.repeat() },
            enabled = !locked,
        ) {
            Icon(
                imageVector = if (repeatMode == 1) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                contentDescription = null,
                tint = if (repeatMode != 0) primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The system output level, driven through the host exactly as the original player drives it. */
@Composable
internal fun RebirthVolumeRow(spec: RebirthSpec, modifier: Modifier = Modifier) {
    val volume = rememberPlayerVolume()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.AutoMirrored.Rounded.VolumeOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Slider(
            value = volume.level.value,
            onValueChange = volume::drag,
            onValueChangeFinished = volume::release,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Icon(
            Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** The sleeve both scenes put their song behind, in each one's own corner radius. */
@Composable
internal fun RebirthSleeve(
    song: Song,
    playing: Boolean,
    spec: RebirthSpec,
    modifier: Modifier = Modifier,
    corner: Dp = spec.artworkCorner,
    showCanvas: Boolean = true,
) {
    val art = PlayerPlatform.host.rememberRemoteArtworkUrl(song)
    val canvas = if (showCanvas) rememberCanvasArtwork(song) else null
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(56.dp))
        AsyncImage(
            model = art,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        canvas?.let { CanvasArtworkPlayer(it, playing, Modifier.fillMaxSize()) }
    }
}

/**
 * The queue, as a rebirth scene draws it.
 *
 * Same calls the original's inline queue makes — jump, move, remove, clear the
 * added tracks — with its own rows. Reordering is done with the two arrows
 * rather than a drag: the drag needs the shared queue list's gesture plumbing,
 * which is welded to the original player's foreground colours, and porting it
 * is tracked in the handoff rather than half-done here.
 */
@Composable
internal fun RebirthQueuePage(
    queue: List<Song>,
    queueIndex: Int,
    locked: Boolean,
    spec: RebirthSpec,
    onJump: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spec.gutter, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.rebirth_queue_tracks, queue.size),
                    modifier = Modifier.weight(1f),
                    style = spec.captionStyle,
                )
                TextButton(onClick = onClear, enabled = !locked) {
                    Text(stringResource(Res.string.rebirth_clear_added))
                }
            }
        }
        itemsIndexed(
            queue,
            key = { index, song -> song.queueEntryId ?: "${song.videoId}:$index" },
        ) { index, song ->
            val current = index == queueIndex
            Surface(
                shape = RoundedCornerShape(spec.panelCorner),
                color = if (current) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable(enabled = !locked) { onJump(index) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(
                            text = song.title,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = spec.labelStyle,
                            color = if (current) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Text(
                            text = song.artist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { onMove(index, index - 1) },
                        enabled = !locked && index > 0,
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null)
                    }
                    IconButton(
                        onClick = { onMove(index, index + 1) },
                        enabled = !locked && index < queue.lastIndex,
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
                    }
                    IconButton(
                        onClick = { onRemove(index) },
                        enabled = !locked && !current,
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(Res.string.remove_from_queue))
                    }
                }
            }
        }
    }
}

/**
 * The lyric page.
 *
 * Translation and romanization come from [rememberLyricsTranslation], the same
 * state machine the original panel uses, so the words, the target language and
 * the "already in this language" answers are the ones the app always gives.
 * What is new is the page: both rebirth styles show the lines at their own
 * scale with a seek-on-tap, and neither hides the transport behind them.
 */
@Composable
internal fun RebirthLyricsPage(
    style: PlayerVisualStyle,
    song: Song,
    lyrics: List<LyricLine>?,
    lyricsSource: com.music.bitchord.data.lyrics.LyricsSource?,
    lyricsUnavailable: Boolean,
    position: PlaybackPosition,
    enabled: Boolean,
    spec: RebirthSpec,
    onSeekLine: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val looking = stringResource(Res.string.lyrics_loading_lines)
    val translation = rememberLyricsTranslation(
        trackId = song.videoId,
        lyrics = lyrics,
        lyricsSource = lyricsSource,
        lyricsUnavailable = lyricsUnavailable,
        loadingText = looking,
        haptics = haptics,
    )
    val lines = translation.displayedLyrics
    val subs = translation.subLines
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = spec.gutter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = translation.status,
                modifier = Modifier.weight(1f),
                style = spec.captionStyle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (lines.isNotEmpty()) {
                LyricModeChip(
                    label = stringResource(Res.string.rebirth_translate),
                    selected = translation.showingTranslation,
                    onClick = translation.toggleTranslation,
                )
                LyricModeChip(
                    label = stringResource(Res.string.rebirth_romanize),
                    selected = translation.showingRomanization,
                    onClick = translation.toggleRomanization,
                )
            }
        }
        when {
            lines.isEmpty() && lyricsUnavailable -> EmptyLyrics(stringResource(Res.string.lyrics_not_available))
            lines.isEmpty() -> EmptyLyrics(stringResource(Res.string.no_lyrics_for_track))
            else -> LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = spec.gutter, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (spec.glass) 14.dp else 18.dp),
            ) {
                itemsIndexed(lines) { index, line ->
                    val isActive = position.positionMs >= line.timeMs && line.timeMs >= 0
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(spec.panelCorner))
                            .clickable(enabled = enabled && line.timeMs >= 0) { onSeekLine(line.timeMs) }
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            text = line.text,
                            style = if (isActive) {
                                MaterialTheme.typography.titleLarge
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            color = if (isActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        subs?.getOrNull(index)?.text?.takeIf { it.isNotBlank() }?.let { sub ->
                            Text(
                                text = sub,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun EmptyLyrics(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The "next up" strip both scenes show under the transport.
 *
 * Reading the queue from the current index rather than from the front is what
 * makes this the *next* two tracks rather than the last two played.
 */
@Composable
internal fun RebirthUpNext(
    queue: List<Song>,
    queueIndex: Int,
    spec: RebirthSpec,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val upcoming = remember(queue, queueIndex) {
        if (queueIndex < 0) queue.take(2) else queue.drop(queueIndex + 1).take(2)
    }
    Surface(
        shape = RoundedCornerShape(spec.panelCorner),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (spec.glass) 0.55f else 0.85f),
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpenQueue),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(
                text = stringResource(Res.string.up_next),
                style = spec.captionStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (upcoming.isEmpty()) {
                Text(
                    text = stringResource(Res.string.rebirth_next_up_empty),
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                upcoming.forEach { song ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AsyncImage(
                            model = song.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = song.artist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Whether a scene has room for its two-column arrangement. */
internal fun rebirthWide(width: Dp): Boolean = width >= 600.dp

/** A square sleeve that never eats the whole screen on a short window. */
internal fun Modifier.rebirthSleeveHeight(maxHeight: Dp): Modifier = this.heightIn(max = maxHeight).aspectRatio(1f)

/** The tint a glass tile uses over artwork; flat scenes never ask for it. */
internal fun rebirthTileColor(spec: RebirthSpec): Color =
    if (spec.glass) Color.White.copy(alpha = 0.08f) else Color.Transparent
