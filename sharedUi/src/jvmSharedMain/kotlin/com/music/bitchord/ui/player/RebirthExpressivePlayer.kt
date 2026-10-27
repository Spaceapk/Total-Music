package com.music.bitchord.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.now_playing
import com.music.bitchord.sharedui.resources.queue
import com.music.bitchord.sharedui.resources.rebirth_lyrics_page
import com.music.bitchord.sharedui.resources.rebirth_playback_tools
import com.music.bitchord.sharedui.resources.rebirth_output
import com.music.bitchord.sharedui.resources.rebirth_queue_tracks
import com.music.bitchord.sharedui.resources.rebirth_tools_short
import org.jetbrains.compose.resources.stringResource

/**
 * Google Expressive+ Reimagined: an editorial player.
 *
 * The song is the page. Artwork runs edge to edge under the bar, the title is
 * set at display size beneath it, and everything the reader can do is a row of
 * labelled pills at the foot — queue, lyrics, output, tools. Nothing is hidden
 * behind a gesture except the queue and the lyrics, which get pages of their
 * own rather than a drawer over the sleeve.
 *
 * The glass scene next door shares [RebirthTransport], [RebirthQueuePage] and
 * the lyric page with this one and nothing else: no card, no radius and no type
 * size is common between them on purpose.
 */
@Composable
internal fun RebirthExpressiveScene(
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
    dynamicBackground: Boolean,
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
    onOutput: () -> Unit,
    onListenTogether: () -> Unit,
    onTools: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // The optional dynamic backdrop: the same switch the glass scene reads,
        // painted as two soft pools of colour rather than a frost.
        if (dynamicBackground) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        colors = listOf(
                            colors.primary.copy(alpha = 0.22f),
                            Color.Transparent,
                        ),
                        center = Offset(220f, 180f),
                        radius = 900f,
                    ),
                ),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        colors = listOf(
                            colors.tertiary.copy(alpha = 0.16f),
                            Color.Transparent,
                        ),
                        center = Offset(860f, 980f),
                        radius = 1100f,
                    ),
                ),
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            ExpressiveTopBar(
                spec = spec,
                song = song,
                liked = liked,
                page = page,
                onLike = onLike,
                onMenu = onMenu,
                onDismiss = onDismiss,
                onPage = onPage,
            )
            when (page) {
                RebirthPage.QUEUE -> RebirthQueuePage(
                    queue = queue,
                    queueIndex = queueIndex,
                    locked = locked,
                    spec = spec,
                    onJump = onJump,
                    onMove = onMove,
                    onRemove = onRemove,
                    onClear = onClear,
                    onDragActiveChange = onDragActiveChange,
                    modifier = Modifier.weight(1f),
                )
                RebirthPage.LYRICS -> RebirthLyricsPage(
                    style = spec.style,
                    song = song,
                    lyrics = lyrics,
                    lyricsSource = lyricsSource,
                    lyricsUnavailable = lyricsUnavailable,
                    position = position,
                    enabled = !locked,
                    spec = spec,
                    onSeekLine = onSeekLine,
                    onOpenOffset = onOpenLyricsOffset,
                    onOpenProviders = onOpenLyricsProviders,
                    modifier = Modifier.weight(1f),
                )
                RebirthPage.PLAYER -> Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        // A short window (a phone in landscape, or a large font
                        // setting eating the vertical space) keeps the sleeve
                        // at a third of what it would otherwise take; the
                        // transport below it never scrolls out of reach because
                        // the whole column scrolls.
                        val artMax = if (maxHeight > 520.dp) 340.dp else 200.dp
                        RebirthSleeve(
                            song = song,
                            playing = playing,
                            spec = spec,
                            modifier = Modifier
                                .padding(horizontal = spec.gutter)
                                .fillMaxWidth()
                                .heightIn(max = artMax)
                                .aspectRatio(1.12f),
                            corner = spec.artworkCorner,
                        )
                    }
                    Column(Modifier.padding(horizontal = spec.gutter, vertical = 16.dp)) {
                        Text(
                            text = song.title,
                            style = spec.titleStyle,
                            color = colors.onSurface,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable(onClick = onAlbum),
                        )
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onArtist)
                                .padding(vertical = 2.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        RebirthSeekBar(
                            position = position,
                            durationMs = durationMs,
                            enabled = !locked,
                            spec = spec,
                            onSeek = transport.seek,
                            trackId = song.videoId,
                        )
                        RebirthTransportRow(
                            playing = playing,
                            loading = loading,
                            previousEnabled = previousEnabled,
                            nextEnabled = nextEnabled,
                            locked = locked,
                            shuffled = shuffled,
                            repeatMode = repeatMode,
                            spec = spec,
                            transport = transport,
                            expanded = true,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Spacer(Modifier.height(spec.gap))
                        RebirthUpNext(
                            queue = queue,
                            queueIndex = queueIndex,
                            spec = spec,
                            onOpenQueue = { onPage(RebirthPage.QUEUE) },
                        )
                        Spacer(Modifier.height(spec.gap))
                    }
                }
            }
            if (page == RebirthPage.PLAYER) {
                RebirthVolumeRow(spec, Modifier.padding(horizontal = spec.gutter))
                ExpressiveActionBar(
                    queueSize = queue.size,
                    onQueue = { onPage(RebirthPage.QUEUE) },
                    onLyrics = { onPage(RebirthPage.LYRICS) },
                    onOutput = onOutput,
                    onTools = onTools,
                    onListenTogether = onListenTogether,
                )
            }
        }
    }
}

@Composable
private fun ExpressiveTopBar(
    spec: RebirthSpec,
    song: Song,
    liked: Boolean,
    page: RebirthPage,
    onLike: () -> Unit,
    onMenu: () -> Unit,
    onDismiss: () -> Unit,
    onPage: (RebirthPage) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { if (page == RebirthPage.PLAYER) onDismiss() else onPage(RebirthPage.PLAYER) },
        ) {
            Icon(
                imageVector = if (page == RebirthPage.PLAYER) {
                    Icons.Rounded.KeyboardArrowDown
                } else {
                    Icons.AutoMirrored.Rounded.ArrowBack
                },
                contentDescription = null,
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(
                text = when (page) {
                    RebirthPage.PLAYER -> stringResource(Res.string.now_playing)
                    RebirthPage.QUEUE -> stringResource(Res.string.queue)
                    RebirthPage.LYRICS -> stringResource(Res.string.rebirth_lyrics_page)
                },
                style = spec.captionStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = song.albumName ?: song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onLike) {
            Icon(
                imageVector = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = null,
                tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onMenu) {
            Icon(Icons.Rounded.MoreVert, contentDescription = null)
        }
    }
}

@Composable
private fun ExpressiveActionBar(
    queueSize: Int,
    onQueue: () -> Unit,
    onLyrics: () -> Unit,
    onOutput: () -> Unit,
    onTools: () -> Unit,
    onListenTogether: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ExpressivePill(
            icon = Icons.Rounded.QueueMusic,
            label = stringResource(Res.string.rebirth_queue_tracks, queueSize),
            onClick = onQueue,
            modifier = Modifier.weight(1f),
        )
        ExpressivePill(
            icon = Icons.Rounded.Notes,
            label = stringResource(Res.string.rebirth_lyrics_page),
            onClick = onLyrics,
            modifier = Modifier.weight(1f),
        )
        ExpressivePill(
            icon = Icons.Rounded.Cast,
            label = stringResource(Res.string.rebirth_output),
            onClick = onOutput,
            modifier = Modifier.weight(1f),
        )
        ExpressivePill(
            icon = Icons.Rounded.Tune,
            label = stringResource(Res.string.rebirth_tools_short),
            onClick = onTools,
            modifier = Modifier.weight(1f),
        )
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.clip(CircleShape).clickable(onClick = onListenTogether),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(Res.string.listen_together),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ExpressivePill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
