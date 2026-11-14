package com.music.bitchord.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.sharedui.resources.*
import com.music.bitchord.ui.haptics.rememberHaptics
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/** Latest reached timestamp, independent of the list's display/translation mode.
 * Static lines do not become active, and seeking backwards recomputes the result.
 */
internal fun googlePlusActiveLyricIndex(lines: List<LyricLine>, clockMs: Long): Int {
    var active = -1
    var latest = Long.MIN_VALUE
    lines.forEachIndexed { index, line ->
        if (line.timeMs >= 0 && line.timeMs <= clockMs && line.timeMs >= latest) {
            active = index
            latest = line.timeMs
        }
    }
    return active
}

@Composable
internal fun GooglePlusLyricsPage(
    song: Song, lyrics: List<LyricLine>?, lyricsSource: LyricsSource?,
    lyricsUnavailable: Boolean, position: PlaybackPosition, enabled: Boolean,
    spec: RebirthSpec, onSeekLine: (Long) -> Unit, onOpenOffset: () -> Unit,
    onOpenProviders: () -> Unit, playing: Boolean, loading: Boolean,
    durationMs: Long, transport: RebirthTransport, onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loadingLines = stringArrayResource(Res.array.lyrics_loading_lines)
    val looking = remember(song.videoId) { loadingLines.firstOrNull().orEmpty() }
    val translation = rememberLyricsTranslation(song.videoId, lyrics, lyricsSource,
        lyricsUnavailable, looking, rememberHaptics())
    val synced by PlayerSettings.syncedLyrics.collectAsStateWithLifecycle()
    val offsetMs by PlayerSettings.lyricsOffsetMs.collectAsStateWithLifecycle()
    val motion = rememberRebirthMotionEnabled()
    var staticMode by rememberSaveable(song.videoId) { mutableStateOf(false) }
    var follow by rememberSaveable(song.videoId) { mutableStateOf(true) }
    var both by rememberSaveable(song.videoId) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val original = lyrics.orEmpty()
    // The translation engine and cache remain shared. "Both" renders original
    // words with the translated subline, never the same translation twice.
    val lines = if (both) original else translation.displayedLyrics
    val timed = synced && !staticMode && lines.any { it.timeMs >= 0 }
    val active = if (timed) googlePlusActiveLyricIndex(lines, adjustedLyricsPosition(position.positionMs, offsetMs)) else -1
    val list = rememberLazyListState()
    val dragging by list.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragging) { if (dragging) follow = false }
    LaunchedEffect(song.videoId, active, timed, follow, lines) {
        if (timed && follow && !dragging && active >= 0) {
            if (motion) list.animateScrollToItem(active) else list.scrollToItem(active)
        }
    }
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().testTag("google-plus-lyrics")) {
        Surface(shape = CircleShape, color = colors.surfaceContainerLow,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(.8f)) {
            Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(PlayerPlatform.host.rememberRemoteArtworkUrl(song), null,
                    Modifier.size(44.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            if (lines.isEmpty()) {
                Text(translation.status, Modifier.align(Alignment.Center).padding(24.dp), textAlign = TextAlign.Center)
            } else {
                LazyColumn(state = list, modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = spec.gutter, vertical = if (timed) maxHeight * .38f else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    itemsIndexed(lines) { index, line ->
                        val current = index == active
                        val foreground by animateColorAsState(
                            if (current) colors.primary else if (!timed) colors.onSurface else colors.onSurfaceVariant.copy(alpha = .68f),
                            tween(if (motion) GooglePlusTokens.stateDurationMs else 0))
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .testTag("google-plus-lyric-$index").semantics { selected = current }
                            .clickable(enabled = enabled && timed && line.timeMs >= 0) {
                                follow = true
                                // Input timestamps and seek callbacks use the same raw lyric clock.
                                onSeekLine(line.timeMs)
                            }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(line.text, Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal, color = foreground)
                            if (both) translation.subLines?.getOrNull(index)?.text?.takeIf { it.isNotBlank() }?.let {
                                Text(it, Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium, color = foreground)
                            }
                        }
                    }
                }
            }
            if (!follow && timed && active >= 0) {
                FilledTonalButton(onClick = { follow = true }, modifier = Modifier.align(Alignment.BottomCenter)) {
                    Text(stringResource(Res.string.plus_return))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledIconButton(transport.playPause, Modifier.size(64.dp), shape = RoundedCornerShape(20.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer)) {
                if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    stringResource(if (playing) Res.string.pause else Res.string.play))
            }
            Surface(shape = CircleShape, color = colors.surfaceContainerLow, modifier = Modifier.weight(1f)) {
                RebirthSeekBar(position, durationMs, enabled, spec, transport.seek,
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp), song.videoId)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.now_playing)) }
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !staticMode, onClick = { staticMode = false; follow = true }, label = { Text(stringResource(Res.string.plus_synced)) })
                FilterChip(selected = staticMode, onClick = { staticMode = true }, label = { Text(stringResource(Res.string.plus_static)) })
            }
            Box {
                IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(Res.string.more)) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(translation.status) }, enabled = false, onClick = {})
                    if (!synced) DropdownMenuItem(text = { Text(stringResource(Res.string.rebirth_lyrics_sync_off)) }, enabled = false, onClick = {})
                    DropdownMenuItem(text = { Text(stringResource(Res.string.rebirth_translate)) }, onClick = { translation.toggleTranslation(); both = false })
                    DropdownMenuItem(text = { Text(stringResource(Res.string.plus_both)) }, enabled = translation.showingTranslation,
                        onClick = { both = !both; menu = false })
                    DropdownMenuItem(text = { Text(stringResource(Res.string.plus_original)) }, onClick = {
                        if (translation.showingTranslation) translation.toggleTranslation()
                        if (translation.showingRomanization) translation.toggleRomanization()
                        both = false; menu = false
                    })
                    DropdownMenuItem(text = { Text(stringResource(Res.string.rebirth_romanize)) }, onClick = { translation.toggleRomanization(); both = false })
                    DropdownMenuItem(text = { Text(stringResource(Res.string.rebirth_lyrics_offset)) }, enabled = enabled, onClick = { menu = false; onOpenOffset() })
                    DropdownMenuItem(text = { Text(stringResource(Res.string.choose_lyrics_provider)) }, enabled = enabled, onClick = { menu = false; onOpenProviders() })
                }
            }
        }
    }
}
