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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
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

/** Which words a Google Plus lyric line shows. The engine and cache are the shared ones. */
internal enum class GooglePlusLyricView { ORIGINAL, TRANSLATED, BOTH }

/**
 * The translated text to draw under an original line in "both" mode.
 * Null when there is nothing distinct to add, so a line never repeats itself.
 */
internal fun googlePlusSubLine(original: LyricLine, translated: List<LyricLine>?, index: Int): String? {
    val text = translated?.getOrNull(index)?.text?.trim().orEmpty()
    return text.takeIf { it.isNotEmpty() && !it.equals(original.text.trim(), ignoreCase = true) }
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
    val translated = (translation.translationState as? LyricsTranslationUiState.Ready)?.lines
    val view = when {
        !translation.showingTranslation -> GooglePlusLyricView.ORIGINAL
        both -> GooglePlusLyricView.BOTH
        else -> GooglePlusLyricView.TRANSLATED
    }
    fun select(target: GooglePlusLyricView) {
        when (target) {
            GooglePlusLyricView.ORIGINAL -> {
                if (translation.showingTranslation) translation.toggleTranslation()
                if (translation.showingRomanization) translation.toggleRomanization()
                both = false
            }
            GooglePlusLyricView.TRANSLATED -> {
                if (!translation.showingTranslation) translation.toggleTranslation()
                both = false
            }
            GooglePlusLyricView.BOTH -> {
                if (!translation.showingTranslation) translation.toggleTranslation()
                both = true
            }
        }
    }
    // "Both" keeps the original words on top and adds the shared engine's
    // translation beneath; it never draws the same translation twice.
    val lines = if (view == GooglePlusLyricView.BOTH) original else translation.displayedLyrics
    val timed = synced && !staticMode && lines.any { it.timeMs >= 0 }
    // Derived, so the playhead ticking only recomposes when the line changes.
    val activeState = remember(lines, timed, offsetMs, position) {
        derivedStateOf {
            if (timed) googlePlusActiveLyricIndex(lines, adjustedLyricsPosition(position.positionMs, offsetMs)) else -1
        }
    }
    val active = activeState.value
    val list = rememberLazyListState()
    val dragging by list.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragging) { if (dragging) follow = false }
    LaunchedEffect(song.videoId, active, timed, follow, lines.size) {
        if (timed && follow && !dragging && active >= 0) {
            if (motion) list.animateScrollToItem(active) else list.scrollToItem(active)
        }
    }
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().testTag("google-plus-lyrics")) {
        Surface(shape = CircleShape, color = colors.surfaceContainerLow,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(.82f)
                .clip(CircleShape).clickable(onClick = onBack).testTag("google-plus-lyrics-capsule")) {
            Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(PlayerPlatform.host.rememberRemoteArtworkUrl(song), null,
                    Modifier.size(44.dp).clip(CircleShape).background(colors.primaryContainer), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant)
                }
                Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.padding(end = 8.dp).size(20.dp), tint = colors.onSurfaceVariant)
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val floatingSpace = 96.dp
            if (lines.isEmpty()) {
                Text(translation.status, Modifier.align(Alignment.Center).padding(24.dp), textAlign = TextAlign.Center,
                    color = colors.onSurfaceVariant)
            } else {
                LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("google-plus-lyric-list"),
                    contentPadding = PaddingValues(
                        start = spec.gutter, end = spec.gutter,
                        top = if (timed) maxHeight * .34f else 16.dp,
                        bottom = if (timed) maxHeight * .5f else floatingSpace),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    itemsIndexed(lines) { index, line ->
                        val current = index == active
                        val foreground by animateColorAsState(
                            if (current) colors.primary else if (!timed) colors.onSurface else colors.onSurfaceVariant.copy(alpha = .62f),
                            tween(if (motion) GooglePlusTokens.stateDurationMs else 0))
                        if (line.isGap) {
                            Spacer(Modifier.fillMaxWidth().height(8.dp).testTag("google-plus-lyric-$index"))
                        } else Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .testTag("google-plus-lyric-$index").semantics { selected = current }
                            .clickable(enabled = enabled && timed && line.timeMs >= 0) {
                                follow = true
                                // Input timestamps and seek callbacks use the same raw lyric clock.
                                onSeekLine(line.timeMs)
                            }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(line.text, Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                                style = if (current) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                                fontWeight = if (current) FontWeight.Bold else FontWeight.Medium, color = foreground)
                            if (view == GooglePlusLyricView.BOTH) googlePlusSubLine(line, translated, index)?.let {
                                Text(it, Modifier.fillMaxWidth().padding(top = 4.dp).testTag("google-plus-lyric-sub-$index"),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyLarge, color = foreground.copy(alpha = foreground.alpha * .86f))
                            }
                        }
                    }
                }
            }
            // Floating playback: stays over the words instead of taking a row from them.
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (!follow && timed && active >= 0) {
                    FilledTonalButton(onClick = { follow = true }, modifier = Modifier.padding(bottom = 8.dp).testTag("google-plus-lyrics-return")) {
                        Text(stringResource(Res.string.plus_return))
                    }
                }
                Row(Modifier.fillMaxWidth().testTag("google-plus-lyrics-floating"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledIconButton(transport.playPause, Modifier.size(64.dp).testTag("google-plus-lyrics-play"), shape = RoundedCornerShape(22.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.primary, contentColor = colors.onPrimary)) {
                        if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = colors.onPrimary)
                        else Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            stringResource(if (playing) Res.string.pause else Res.string.play))
                    }
                    Surface(shape = CircleShape, color = colors.surfaceContainerHigh, modifier = Modifier.weight(1f)) {
                        RebirthSeekBar(position, durationMs, enabled, spec, transport.seek,
                            Modifier.padding(horizontal = 16.dp, vertical = 2.dp), song.videoId)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack, Modifier.testTag("google-plus-lyrics-back")) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.now_playing)) }
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !staticMode, onClick = { staticMode = false; follow = true },
                    label = { Text(stringResource(Res.string.plus_synced)) }, modifier = Modifier.testTag("google-plus-lyrics-synced"))
                FilterChip(selected = staticMode, onClick = { staticMode = true },
                    label = { Text(stringResource(Res.string.plus_static)) }, modifier = Modifier.testTag("google-plus-lyrics-static"))
                if (original.isNotEmpty()) {
                    FilterChip(selected = view == GooglePlusLyricView.ORIGINAL, onClick = { select(GooglePlusLyricView.ORIGINAL) },
                        label = { Text(stringResource(Res.string.plus_original)) }, modifier = Modifier.testTag("google-plus-lyrics-original"))
                    FilterChip(selected = view == GooglePlusLyricView.TRANSLATED, onClick = { select(GooglePlusLyricView.TRANSLATED) },
                        label = { Text(stringResource(Res.string.plus_translated)) }, modifier = Modifier.testTag("google-plus-lyrics-translated"))
                    FilterChip(selected = view == GooglePlusLyricView.BOTH, onClick = { select(GooglePlusLyricView.BOTH) },
                        label = { Text(stringResource(Res.string.plus_both)) }, modifier = Modifier.testTag("google-plus-lyrics-both"))
                }
            }
            Box {
                IconButton({ menu = true }, Modifier.testTag("google-plus-lyrics-menu")) { Icon(Icons.Rounded.MoreVert, stringResource(Res.string.more)) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(translation.status) }, enabled = false, onClick = {})
                    if (!synced) DropdownMenuItem(text = { Text(stringResource(Res.string.rebirth_lyrics_sync_off)) }, enabled = false, onClick = {})
                    DropdownMenuItem(text = { Text(stringResource(Res.string.rebirth_translate)) },
                        onClick = { select(if (translation.showingTranslation) GooglePlusLyricView.ORIGINAL else GooglePlusLyricView.TRANSLATED); menu = false })
                    DropdownMenuItem(text = { Text(stringResource(Res.string.rebirth_romanize)) }, onClick = { translation.toggleRomanization(); both = false; menu = false })
                    DropdownMenuItem(text = { Text(stringResource(Res.string.rebirth_lyrics_offset)) }, enabled = enabled, onClick = { menu = false; onOpenOffset() })
                    DropdownMenuItem(text = { Text(stringResource(Res.string.choose_lyrics_provider)) }, enabled = enabled, onClick = { menu = false; onOpenProviders() })
                }
            }
        }
    }
}
