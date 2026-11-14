package com.music.bitchord.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.automirrored.rounded.Notes
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
import androidx.compose.ui.platform.testTag
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
import com.music.bitchord.sharedui.resources.listen_together
import com.music.bitchord.sharedui.resources.rebirth_add_favorite
import com.music.bitchord.sharedui.resources.rebirth_remove_favorite
import com.music.bitchord.sharedui.resources.now_playing
import com.music.bitchord.sharedui.resources.queue
import com.music.bitchord.sharedui.resources.rebirth_lyrics_page
import com.music.bitchord.sharedui.resources.rebirth_playback_tools
import com.music.bitchord.sharedui.resources.rebirth_output
import com.music.bitchord.sharedui.resources.rebirth_queue_tracks
import com.music.bitchord.sharedui.resources.rebirth_tools_short
import org.jetbrains.compose.resources.stringResource

/**
 * Google Plus 2: reference-led player presentation.
 *
 * Keeps the existing media session, artwork dock, queue callbacks, providers
 * and playback tools. Layout and lyric presentation are scoped to this scene.
 * Motion timings remain provisional until device/video comparison is complete.
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
    val hideVolume by PlayerPlatform.host.settings.hideVolumeBar.collectAsStateWithLifecycle()
    val motion = rememberRebirthMotionEnabled()
    val dock = LocalPlayerDock.current
    val dockFrame = rememberRebirthDockFrame()
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .rebirthDockHost(dock, dockFrame),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            ExpressiveTopBar(
                page = page,
                onDismiss = onDismiss,
                onPage = onPage,
                onOutput = onOutput,
                onTools = onTools,
            )
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    if (!motion) EnterTransition.None togetherWith ExitTransition.None
                    else if (targetState == RebirthPage.QUEUE) {
                        (fadeIn(tween(180)) + slideInVertically(tween(GooglePlusTokens.pageDurationMs, easing = RebirthEmphasizedDecelerate)) { it }) togetherWith fadeOut(tween(120))
                    } else if (initialState == RebirthPage.QUEUE) {
                        fadeIn(tween(180)) togetherWith (fadeOut(tween(180)) + slideOutVertically(tween(GooglePlusTokens.pageDurationMs)) { it })
                    } else rebirthPageTransition(motion)
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { target ->
                when (target) {
                    RebirthPage.QUEUE -> GooglePlusQueuePanel(
                        song = song, queue = queue, queueIndex = queueIndex, locked = locked,
                        shuffled = shuffled, repeatMode = repeatMode, spec = spec, transport = transport,
                        onJump = onJump, onMove = onMove, onRemove = onRemove, onClear = onClear,
                        onDragActiveChange = onDragActiveChange,
                        onClose = { onPage(RebirthPage.PLAYER) },
                        modifier = Modifier.fillMaxSize(),
                    )
                    RebirthPage.LYRICS -> GooglePlusLyricsPage(
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
                        playing = playing, loading = loading, durationMs = durationMs,
                        transport = transport, onBack = { onPage(RebirthPage.PLAYER) },
                        modifier = Modifier.fillMaxSize(),
                    )
                    RebirthPage.PLAYER -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    val availableHeight = maxHeight
                    Column(
                        Modifier.fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .heightIn(min = availableHeight),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            // Balance against the usable viewport; short windows
                            // and large text can scroll the complete group.
                            val side = minOf((maxWidth - spec.gutter * 2).coerceAtLeast(0.dp), (availableHeight * 0.57f).coerceIn(140.dp, 480.dp))
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                RebirthEnter(delayMillis = 0, motion = motion) {
                                    GooglePlusSleeve(
                                        song = song, queueIndex = queueIndex, playing = playing, spec = spec,
                                        onNext = { if (nextEnabled && !locked) transport.next() },
                                        onPrevious = { if (previousEnabled && !locked) transport.previous() },
                                        dockFrame = dockFrame,
                                        modifier = Modifier.size(side).testTag("rebirth-artwork"),
                                    )
                                }
                            }
                        }
                        Column(Modifier.padding(horizontal = spec.gutter, vertical = 16.dp)) {
                            RebirthEnter(delayMillis = 0, motion = false) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
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
                                }
                                IconButton(onClick = { onPage(RebirthPage.LYRICS) }, modifier = Modifier.testTag("google-plus-open-lyrics")) {
                                    Icon(Icons.AutoMirrored.Rounded.Notes, stringResource(Res.string.rebirth_lyrics_page))
                                }
                                IconButton(onClick = onMenu) {
                                    Icon(Icons.Rounded.MoreVert, stringResource(Res.string.rebirth_tools_short))
                                }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            RebirthEnter(delayMillis = 150, motion = motion) {
                                Column {
                                    RebirthSeekBar(
                                        position = position,
                                        durationMs = durationMs,
                                        enabled = !locked,
                                        spec = spec,
                                        onSeek = transport.seek,
                                        trackId = song.videoId,
                                    )
                                    Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        PlaybackQualityLabel(song, loading)
                                        GooglePlusQuality()
                                    }
                                    GooglePlusTransport(
                                        playing = playing, loading = loading,
                                        previousEnabled = previousEnabled, nextEnabled = nextEnabled,
                                        locked = locked, shuffled = shuffled, repeatMode = repeatMode,
                                        liked = liked, transport = transport, onLike = onLike,
                                        modifier = Modifier.padding(top = 20.dp),
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            if (!hideVolume) {
                                RebirthEnter(delayMillis = 210, motion = motion) {
                                    RebirthVolumeRow(spec)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun ExpressiveTopBar(
    page: RebirthPage,
    onDismiss: () -> Unit,
    onPage: (RebirthPage) -> Unit,
    onOutput: () -> Unit,
    onTools: () -> Unit,
) {
    if (page == RebirthPage.LYRICS) return // The lyric page owns its song capsule and back control.
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { if (page == RebirthPage.PLAYER) onDismiss() else onPage(RebirthPage.PLAYER) }) {
            Icon(if (page == RebirthPage.PLAYER) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(Res.string.now_playing))
        }
        Text(stringResource(if (page == RebirthPage.QUEUE) Res.string.queue else Res.string.now_playing),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.titleSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        IconButton(onClick = onOutput) { Icon(Icons.Rounded.Cast, stringResource(Res.string.rebirth_output)) }
        IconButton(onClick = { onPage(if (page == RebirthPage.QUEUE) RebirthPage.PLAYER else RebirthPage.QUEUE) }, modifier = Modifier.testTag("google-plus-open-queue")) {
            Icon(Icons.Rounded.QueueMusic, stringResource(Res.string.queue))
        }
        IconButton(onClick = onTools) { Icon(Icons.Rounded.Tune, stringResource(Res.string.rebirth_playback_tools)) }
    }
}

