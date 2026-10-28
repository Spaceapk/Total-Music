package com.music.bitchord.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.listen_together
import com.music.bitchord.sharedui.resources.rebirth_add_favorite
import com.music.bitchord.sharedui.resources.rebirth_remove_favorite
import com.music.bitchord.sharedui.resources.now_playing
import com.music.bitchord.sharedui.resources.queue
import com.music.bitchord.sharedui.resources.rebirth_lyrics_page
import com.music.bitchord.sharedui.resources.rebirth_output
import com.music.bitchord.sharedui.resources.rebirth_queue_tracks
import com.music.bitchord.sharedui.resources.rebirth_tools_short
import org.jetbrains.compose.resources.stringResource

/**
 * Liquid Glass+ Reimagined: a control surface over the cover.
 *
 * Where the expressive scene sets the song as a page, this one floats: one
 * square sleeve centred in the window, one capsule of controls under it, and
 * everything else as small frosted tiles that stay out of the artwork's way.
 * The three pages are a segmented control at the top rather than a bar at the
 * foot, and the type is small and tracked rather than display-sized.
 *
 * Both scenes are drawn from the same state — see [RebirthPlayer] — so the
 * difference a reader sees is composition, never behaviour.
 */
@Composable
internal fun RebirthGlassScene(
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
    glassEffects: Boolean,
    dynamicBackground: Boolean,
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
    onOutput: () -> Unit,
    onListenTogether: () -> Unit,
    onTools: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val glassChrome = glassEffects
    val reducedBlur by PlayerPlatform.host.settings.reduceDynamicBlur.collectAsStateWithLifecycle()
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        val artworkLimit = (maxHeight * 0.42f).coerceIn(160.dp, 360.dp)
        PlayerPlatform.host.GlassBackdrop(enabled = glassChrome, background = {
            Box(Modifier.fillMaxSize().background(colors.background))
            if (dynamicBackground) {
                if (!reducedBlur) {
                    val artwork = PlayerPlatform.host.rememberRemoteArtworkUrl(song)
                    coil3.compose.AsyncImage(
                        model = artwork,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().blur(48.dp),
                    )
                    Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.58f)))
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.radialGradient(
                            colors = listOf(colors.primary.copy(alpha = 0.22f), Color.Transparent),
                            center = Offset(320f, 260f),
                            radius = 1400f,
                        ),
                    ),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF4D7BE6).copy(alpha = 0.22f), Color.Transparent),
                            center = Offset(980f, 1500f),
                            radius = 1200f,
                        ),
                    ),
                )
            }
        }, content = {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp),
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
                    Text(
                        text = song.albumName ?: song.artist,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = onLike) {
                        Icon(
                            imageVector = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = stringResource(if (liked) Res.string.rebirth_remove_favorite else Res.string.rebirth_add_favorite),
                            tint = if (liked) colors.primary else colors.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onMenu) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = null)
                    }
                }
                GlassSegmentedPages(page = page, onPage = onPage, spec = spec, glassChrome = glassChrome)
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
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = spec.gutter),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(10.dp))
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            // The sleeve takes the shorter of the window's width
                            // and 360 dp, so a tablet does not stretch a square to
                            // the size of a pillow and a short window still leaves
                            // room for the capsule and the tiles beneath it.
                            val side = minOf(maxWidth, artworkLimit)
                            RebirthSleeve(
                                song = song,
                                playing = playing,
                                spec = spec,
                                modifier = Modifier.size(side).align(Alignment.Center),
                                corner = spec.artworkCorner,
                            )
                        }
                        Column(
                            Modifier.fillMaxWidth().padding(top = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = song.title,
                                style = spec.titleStyle,
                                color = colors.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable(onClick = onAlbum),
                            )
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clip(CircleShape)
                                    .clickable(onClick = onArtist)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        RebirthGlassPanel(
                            shape = RoundedCornerShape(spec.panelCorner),
                            enabled = glassChrome,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
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
                                    expanded = alternativeControls,
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        GlassTileRow(
                            queueSize = queue.size,
                            onQueue = { onPage(RebirthPage.QUEUE) },
                            onOutput = onOutput,
                            onTools = onTools,
                            glassChrome = glassChrome,
                        )
                        Spacer(Modifier.height(10.dp))
                        RebirthGlassPanel(
                            shape = RoundedCornerShape(20.dp),
                            enabled = glassChrome,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().clickable(onClick = onListenTogether)
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    text = stringResource(Res.string.listen_together),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        RebirthUpNext(
                            queue = queue,
                            queueIndex = queueIndex,
                            spec = spec,
                            onOpenQueue = { onPage(RebirthPage.QUEUE) },
                        )
                        Spacer(Modifier.height(14.dp))
                        RebirthGlassPanel(
                            shape = RoundedCornerShape(20.dp),
                            enabled = glassChrome,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            RebirthVolumeRow(
                                spec,
                                Modifier.padding(horizontal = spec.gutter, vertical = 6.dp),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        })
    }
}

@Composable
private fun GlassSegmentedPages(page: RebirthPage, onPage: (RebirthPage) -> Unit, spec: RebirthSpec, glassChrome: Boolean) {
    RebirthGlassPanel(
        shape = RoundedCornerShape(40.dp),
        enabled = glassChrome,
        modifier = Modifier.padding(horizontal = spec.gutter, vertical = 6.dp).fillMaxWidth(),
    ) {
        Row(Modifier.padding(4.dp)) {
            GlassSegment(
                label = stringResource(Res.string.now_playing),
                selected = page == RebirthPage.PLAYER,
                onClick = { onPage(RebirthPage.PLAYER) },
                modifier = Modifier.weight(1f),
            )
            GlassSegment(
                label = stringResource(Res.string.queue),
                selected = page == RebirthPage.QUEUE,
                onClick = { onPage(RebirthPage.QUEUE) },
                modifier = Modifier.weight(1f),
            )
            GlassSegment(
                label = stringResource(Res.string.rebirth_lyrics_page),
                selected = page == RebirthPage.LYRICS,
                onClick = { onPage(RebirthPage.LYRICS) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun GlassSegment(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        } else {
            Color.Transparent
        },
        modifier = modifier.clip(RoundedCornerShape(32.dp)).clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun GlassTileRow(
    queueSize: Int,
    onQueue: () -> Unit,
    onOutput: () -> Unit,
    onTools: () -> Unit,
    glassChrome: Boolean,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlassTile(
            icon = Icons.Rounded.QueueMusic,
            label = stringResource(Res.string.rebirth_queue_tracks, queueSize),
            caption = null,
            onClick = onQueue,
            glassChrome = glassChrome,
            modifier = Modifier.weight(1f),
        )
        GlassTile(
            icon = Icons.Rounded.Cast,
            label = stringResource(Res.string.rebirth_output),
            caption = null,
            onClick = onOutput,
            glassChrome = glassChrome,
            modifier = Modifier.weight(1f),
        )
        GlassTile(
            icon = Icons.Rounded.Tune,
            label = stringResource(Res.string.rebirth_tools_short),
            caption = null,
            onClick = onTools,
            glassChrome = glassChrome,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun GlassTile(
    icon: ImageVector,
    label: String,
    caption: String?,
    onClick: () -> Unit,
    glassChrome: Boolean,
    modifier: Modifier = Modifier,
) {
    RebirthGlassPanel(
        shape = RoundedCornerShape(20.dp),
        enabled = glassChrome,
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            caption?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
