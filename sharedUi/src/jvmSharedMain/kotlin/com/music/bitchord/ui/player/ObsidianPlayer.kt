package com.music.bitchord.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.lyrics_not_available
import com.music.bitchord.sharedui.resources.no_lyrics_for_track
import com.music.bitchord.sharedui.resources.rebirth_lyrics_offset
import com.music.bitchord.sharedui.resources.rebirth_lyrics_offset_value
import com.music.bitchord.sharedui.resources.rebirth_lyrics_sync_off
import com.music.bitchord.sharedui.resources.queue
import com.music.bitchord.sharedui.resources.rebirth_romanize
import com.music.bitchord.sharedui.resources.rebirth_translate
import com.music.bitchord.sharedui.resources.choose_lyrics_provider
import com.music.bitchord.sharedui.resources.close_lyrics
import com.music.bitchord.sharedui.resources.open_lyrics
import com.music.bitchord.sharedui.resources.widget_previous
import com.music.bitchord.sharedui.resources.widget_next
import com.music.bitchord.sharedui.resources.shuffle_on
import com.music.bitchord.sharedui.resources.repeat_all
import com.music.bitchord.sharedui.resources.lyrics_loading_lines
import org.jetbrains.compose.resources.stringArrayResource
import com.music.bitchord.ui.haptics.rememberHaptics
import org.jetbrains.compose.resources.stringResource

private val ObsidianBackground = Color(0xFF08090B)

/** Immersive fifth player. It owns no session state: every action is the existing transport. */
@Composable
internal fun ObsidianScene(
    spec: RebirthSpec,
    song: Song,
    playing: Boolean,
    loading: Boolean,
    position: PlaybackPosition,
    durationMs: Long,
    queue: List<Song>,
    queueIndex: Int,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    locked: Boolean,
    shuffled: Boolean,
    repeatMode: Int,
    liked: Boolean,
    alternativeControls: Boolean,
    transport: RebirthTransport,
    page: RebirthPage,
    onPage: (RebirthPage) -> Unit,
    onLike: () -> Unit,
    onMenu: () -> Unit,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
    onDragActiveChange: (Boolean) -> Unit,
    onOpenLyricsOffset: () -> Unit,
    onOpenLyricsProviders: () -> Unit,
    onAlbum: () -> Unit,
    onArtist: () -> Unit,
    lyrics: List<LyricLine>?,
    lyricsSource: LyricsSource?,
    lyricsUnavailable: Boolean,
    onSeekLine: (Long) -> Unit,
    onTools: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = rememberRebirthMotionEnabled()
    val dynamic by PlayerPlatform.host.settings.obsidianDynamicBackground.collectAsStateWithLifecycle()
    val dock = LocalPlayerDock.current
    val dockFrame = rememberRebirthDockFrame()
    val artwork = PlayerPlatform.host.rememberRemoteArtworkUrl(song)
    Box(
        modifier.fillMaxSize().background(ObsidianBackground).testTag("obsidian-player")
            .rebirthDockHost(dock, dockFrame),
    ) {
        if (dynamic && artwork != null) {
            AsyncImage(
                model = artwork, contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(64.dp), contentScale = ContentScale.Crop,
            )
            Box(Modifier.fillMaxSize().background(ObsidianBackground.copy(alpha = 0.76f)))
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(Res.string.close_lyrics))
                }
                Text(
                    text = song.albumName ?: song.playbackSource ?: "Obsidian",
                    modifier = Modifier.weight(1f).clickable(onClick = onAlbum),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onMenu) { Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(Res.string.more)) }
            }
            AnimatedContent(
                targetState = page,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = { rebirthPageTransition(motion) },
                label = "obsidian-page",
            ) { target ->
                when (target) {
                    RebirthPage.QUEUE -> Column(Modifier.fillMaxSize()) {
                        ObsidianPageBack(stringResource(Res.string.queue)) { onPage(RebirthPage.PLAYER) }
                        RebirthQueuePage(
                            queue = queue, queueIndex = queueIndex, locked = locked, spec = spec,
                            onJump = onJump, onMove = onMove, onRemove = onRemove, onClear = onClear,
                            onDragActiveChange = onDragActiveChange,
                            modifier = Modifier.fillMaxSize().testTag("obsidian-queue"),
                        )
                    }
                    RebirthPage.LYRICS -> Column(Modifier.fillMaxSize()) {
                        ObsidianPageBack(stringResource(Res.string.open_lyrics)) { onPage(RebirthPage.PLAYER) }
                        ObsidianLyricsPage(
                            song = song, lyrics = lyrics, lyricsSource = lyricsSource,
                            lyricsUnavailable = lyricsUnavailable, position = position,
                            enabled = !locked, spec = spec, onSeekLine = onSeekLine,
                            onOpenOffset = onOpenLyricsOffset, onOpenProviders = onOpenLyricsProviders,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    RebirthPage.PLAYER -> BoxWithConstraints(Modifier.fillMaxSize()) {
                        val side = minOf((maxWidth - 40.dp).coerceAtLeast(120.dp), (maxHeight * 0.48f).coerceIn(150.dp, 400.dp))
                        Column(
                            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
                                .padding(horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            RebirthEnter(0, motion) {
                                ObsidianDisc(song, playing, motion, spec, side, dockFrame) {
                                    if (nextEnabled && !locked) transport.next()
                                }
                            }
                            Spacer(Modifier.height(22.dp))
                            RebirthEnter(60, motion, Modifier.fillMaxWidth()) {
                                Column {
                                    Text(song.title, style = spec.titleStyle, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable(onClick = onAlbum))
                                    Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable(onClick = onArtist))
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            RebirthSeekBar(position, durationMs, !locked, spec, transport.seek, Modifier.fillMaxWidth(), song.videoId)
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = transport.previous, enabled = previousEnabled && !locked) { Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(Res.string.widget_previous)) }
                                FilledIconButton(
                                    onClick = transport.playPause, enabled = !locked,
                                    modifier = Modifier.size(64.dp), shape = CircleShape,
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black),
                                ) {
                                    if (loading) CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp, color = Color.Black)
                                    else Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = stringResource(if (playing) Res.string.pause else Res.string.play), modifier = Modifier.size(34.dp))
                                }
                                IconButton(onClick = transport.next, enabled = nextEnabled && !locked) { Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(Res.string.widget_next)) }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = transport.shuffle, enabled = !locked) { Icon(Icons.Rounded.Shuffle, contentDescription = stringResource(Res.string.shuffle_on), tint = if (shuffled) colors.primary else colors.onSurfaceVariant) }
                                IconButton(onClick = onLike, enabled = !locked) { Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = stringResource(Res.string.like), tint = if (liked) colors.primary else colors.onSurfaceVariant) }
                                IconButton(onClick = { onPage(RebirthPage.LYRICS) }, modifier = Modifier.testTag("obsidian-lyrics")) { Icon(Icons.Rounded.Lyrics, contentDescription = stringResource(Res.string.open_lyrics)) }
                                IconButton(onClick = { onPage(RebirthPage.QUEUE) }, modifier = Modifier.testTag("obsidian-open-queue")) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = stringResource(Res.string.queue)) }
                                IconButton(onClick = onTools) { Icon(Icons.Rounded.Tune, contentDescription = stringResource(Res.string.rebirth_playback_tools)) }
                                IconButton(onClick = transport.repeat, enabled = !locked) { Icon(if (repeatMode == 2) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, contentDescription = stringResource(Res.string.repeat_all), tint = if (repeatMode != 0) colors.primary else colors.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ObsidianDisc(
    song: Song,
    playing: Boolean,
    motion: Boolean,
    spec: RebirthSpec,
    side: androidx.compose.ui.unit.Dp,
    dockFrame: RebirthDockFrame,
    onNext: () -> Unit,
) {
    val rotation = remember(song.videoId) { Animatable(0f) }
    LaunchedEffect(playing, motion, song.videoId) {
        if (playing && motion) {
            while (true) rotation.animateTo(rotation.value + 360f, tween(24_000, easing = LinearEasing))
        }
    }
    val ring = MaterialTheme.colorScheme.primary
    Box(Modifier.size(side), contentAlignment = Alignment.Center) {
        RebirthSleeve(
            song = song, playing = playing, spec = spec,
            modifier = Modifier.fillMaxSize().testTag("rebirth-artwork"),
            corner = side / 2, showCanvas = false, onNext = onNext,
            dockFrame = dockFrame,
        )
        Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = if (motion) rotation.value % 360f else 0f }) {
            val stroke = 2.dp.toPx()
            drawCircle(ring.copy(alpha = 0.38f), radius = size.minDimension / 2f - stroke, style = Stroke(stroke))
            drawCircle(Color.White.copy(alpha = 0.16f), radius = size.minDimension * 0.18f, style = Stroke(stroke))
            drawCircle(Color.White.copy(alpha = 0.7f), radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width / 2f, stroke + 3.dp.toPx()))
        }
    }
}

@Composable
private fun ObsidianPageBack(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("obsidian-page-back")) { Icon(Icons.Rounded.Close, contentDescription = stringResource(Res.string.close_lyrics)) }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** The last timestamp reached, not every future line after the playhead. */
internal fun obsidianActiveLyricIndex(lines: List<LyricLine>, positionMs: Long): Int =
    lines.indexOfLast { it.timeMs in 0..positionMs }

@Composable
private fun ObsidianLyricsPage(
    song: Song,
    lyrics: List<LyricLine>?,
    lyricsSource: LyricsSource?,
    lyricsUnavailable: Boolean,
    position: PlaybackPosition,
    enabled: Boolean,
    spec: RebirthSpec,
    onSeekLine: (Long) -> Unit,
    onOpenOffset: () -> Unit,
    onOpenProviders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val loadingLines = stringArrayResource(Res.array.lyrics_loading_lines)
    val loading = remember(song.videoId) { loadingLines.random() }
    val translation = rememberLyricsTranslation(song.videoId, lyrics, lyricsSource, lyricsUnavailable, loading, haptics)
    val lines = translation.displayedLyrics
    val synced by PlayerSettings.syncedLyrics.collectAsStateWithLifecycle()
    val offset by PlayerSettings.lyricsOffsetMs.collectAsStateWithLifecycle()
    val clock = adjustedLyricsPosition(position.positionMs, offset)
    val active = remember(lines, clock) { obsidianActiveLyricIndex(lines, clock) }
    val listState = rememberLazyListState()
    LaunchedEffect(song.videoId, active, synced) {
        if (synced && active >= 0) listState.animateScrollToItem((active - 1).coerceAtLeast(0))
    }
    Column(modifier.fillMaxSize()) {
        Text(translation.status, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (lines.isNotEmpty()) {
                ObsidianChip(stringResource(Res.string.rebirth_translate), translation.showingTranslation, translation.toggleTranslation)
                ObsidianChip(stringResource(Res.string.rebirth_romanize), translation.showingRomanization, translation.toggleRomanization)
            }
            if (!synced) Text(stringResource(Res.string.rebirth_lyrics_sync_off), style = MaterialTheme.typography.labelSmall)
            ObsidianChip(if (offset == 0) stringResource(Res.string.rebirth_lyrics_offset) else stringResource(Res.string.rebirth_lyrics_offset_value, String.format(java.util.Locale.getDefault(), "%+.1f s", offset / 1000.0)), offset != 0) { if (enabled) onOpenOffset() }
            ObsidianChip(stringResource(Res.string.choose_lyrics_provider), false) { if (enabled) onOpenProviders() }
        }
        when {
            lines.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(if (lyricsUnavailable) Res.string.lyrics_not_available else Res.string.no_lyrics_for_track), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                state = listState, modifier = Modifier.weight(1f).fillMaxWidth().testTag("obsidian-synced-lyrics"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(lines) { index, line ->
                    val current = index == active
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .testTag("obsidian-lyric-line-$index")
                            .clickable(enabled = enabled && line.timeMs >= 0) { onSeekLine(line.timeMs) }
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            line.text,
                            style = if (current) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        translation.subLines?.getOrNull(index)?.text?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ObsidianChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(shape = CircleShape, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.clip(CircleShape).clickable(onClick = onClick)) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
    }
}
