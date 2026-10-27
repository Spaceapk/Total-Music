package com.music.bitchord.ui.player

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.PlaybackPosition

internal data class RebirthTransport(val playPause: () -> Unit, val previous: () -> Unit, val next: () -> Unit, val shuffle: () -> Unit, val repeat: () -> Unit, val seek: (Float) -> Unit)

@Composable
internal fun RebirthPlayer(style: PlayerVisualStyle, song: Song, playing: Boolean, loading: Boolean, position: PlaybackPosition,
    duration: Long, queue: List<Song>, queueIndex: Int, previousEnabled: Boolean, nextEnabled: Boolean, locked: Boolean,
    shuffled: Boolean, repeatMode: Int, liked: Boolean, glassEffects: Boolean, dynamicBackground: Boolean, alternativeControls: Boolean,
    transport: RebirthTransport, onLike: () -> Unit, onMenu: () -> Unit, onDismiss: () -> Unit, onJump: (Int) -> Unit,
    onRemove: (Int) -> Unit, onMove: (Int, Int) -> Unit, onClear: () -> Unit, onAlbum: () -> Unit, onArtist: () -> Unit,
    onAdvanced: () -> Unit, lyrics: List<LyricLine>?, lyricsUnavailable: Boolean,
    onSeekLine: (Long) -> Unit, onOutput: () -> Unit, onListenTogether: () -> Unit,
    modifier: Modifier = Modifier) {
    var queueOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var lyricsOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    PlayerBackHandler(enabled = queueOpen || lyricsOpen) { queueOpen = false; lyricsOpen = false }
    val glass = style == PlayerVisualStyle.LIQUID_GLASS_PLUS
    val colors = rebirthColors(style, MaterialTheme.colorScheme)
    MaterialTheme(colorScheme = colors) {
        Box(modifier.fillMaxSize().background(colors.background)) {
            if (glass && dynamicBackground) Box(Modifier.fillMaxSize().background(Brush.radialGradient(
                if (colors.background.luminance() < .5f) listOf(Color(0xFF1C435F), Color.Black) else listOf(Color(0xFFD4EAF7), colors.background), radius = 1200f)))
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.KeyboardArrowDown, "Cerrar reproductor") }
                    Text(if (queueOpen) "Cola de reproducción" else if (lyricsOpen) "Letra" else "Reproduciendo", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = onLike) { Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorito") }
                    IconButton(onClick = onMenu) { Icon(Icons.Rounded.MoreVert, "Opciones de la canción") }
                }
                when {
                    lyricsOpen -> RebirthLyrics(lyrics, lyricsUnavailable, position, !locked, onSeekLine, Modifier.weight(1f))
                    queueOpen -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
                        item { TextButton(onClick = onClear, enabled = !locked) { Text("Limpiar cola añadida") } }
                        itemsIndexed(queue, key = { i, s -> s.queueEntryId ?: "${s.videoId}:$i" }) { i, s ->
                            Surface(shape = RoundedCornerShape(20.dp), color = if (i == queueIndex) colors.primaryContainer else colors.surface, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(s.title, Modifier.fillMaxWidth().clickable(enabled = !locked) { onJump(i) }.padding(vertical = 12.dp), maxLines = 2)
                                    Text(s.artist, style = MaterialTheme.typography.bodySmall)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        IconButton(onClick = { onMove(i, i - 1) }, enabled = !locked && i > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Subir") }
                                        IconButton(onClick = { onMove(i, i + 1) }, enabled = !locked && i < queue.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, "Bajar") }
                                        IconButton(onClick = { onRemove(i) }, enabled = !locked && i != queueIndex) { Icon(Icons.Rounded.Close, "Quitar de la cola") }
                                    }
                                }
                            }
                        }
                    }
                    glass -> Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(20.dp))
                        Sleeve(song, playing, Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(1f), 28)
                        Seek(position, duration, song.videoId, !locked, transport.seek)
                        Text(song.title, Modifier.clickable(onClick = onAlbum).padding(top = 8.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 3)
                        TextButton(onClick = onArtist) { Text(song.artist, maxLines = 2) }
                        Surface(Modifier.fillMaxWidth().padding(vertical = 12.dp), shape = RoundedCornerShape(40.dp),
                            color = colors.surfaceContainerHigh.copy(alpha = if (glassEffects) .72f else 1f),
                            shadowElevation = if (glassEffects) 12.dp else 0.dp,
                            border = if (glassEffects) BorderStroke(1.dp, colors.primary.copy(alpha = .25f)) else null) {
                            Transport(playing, loading, previousEnabled, nextEnabled, locked, shuffled, repeatMode, transport, false)
                        }
                        NextSongs(queue, queueIndex, { queueOpen = true })
                    }
                    else -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Sleeve(song, playing, Modifier.fillMaxWidth().heightIn(max = 420.dp).aspectRatio(1.18f), 46)
                        Column(Modifier.padding(24.dp)) {
                            Text(song.title, Modifier.clickable(onClick = onAlbum), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, maxLines = 3)
                            TextButton(onClick = onArtist, contentPadding = PaddingValues(0.dp)) { Text(song.artist, style = MaterialTheme.typography.titleMedium) }
                            Seek(position, duration, song.videoId, !locked, transport.seek)
                            Transport(playing, loading, previousEnabled, nextEnabled, locked, shuffled, repeatMode, transport, alternativeControls)
                            NextSongs(queue, queueIndex, { queueOpen = true })
                        }
                    }
                }
                RebirthVolume(Modifier.padding(horizontal = 24.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { queueOpen = !queueOpen; lyricsOpen = false }) { Text(if (queueOpen) "Reproductor" else "Cola") }
                    TextButton(onClick = { lyricsOpen = !lyricsOpen; queueOpen = false }) { Text(if (lyricsOpen) "Reproductor" else "Letra") }
                    TextButton(onClick = onOutput) { Text("Salida") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = onListenTogether) { Text("Escuchar juntos") }
                    TextButton(onClick = onAdvanced) { Text("Herramientas de reproducción") }
                }
            }
        }
    }
}
@Composable
private fun NextSongs(queue: List<Song>, index: Int, onOpen: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clickable(onClick = onOpen)) {
        Column(Modifier.padding(18.dp)) { Text("A continuación", style = MaterialTheme.typography.labelLarge); queue.drop(index + 1).take(2).forEach { Text(it.title, Modifier.padding(top = 8.dp), maxLines = 1) }; if (index >= queue.lastIndex) Text("Fin de la cola") }
    }
}
@Composable
private fun Sleeve(song: Song, playing: Boolean, modifier: Modifier, radius: Int) {
    val art = PlayerPlatform.host.rememberRemoteArtworkUrl(song)
    val canvas = rememberCanvasArtwork(song)
    Box(modifier.clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = radius.dp, bottomEnd = radius.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.MusicNote, null, Modifier.size(64.dp))
        AsyncImage(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        canvas?.let { CanvasArtworkPlayer(it, playing, Modifier.fillMaxSize()) }
    }
}
/** Keeps ticking playhead observation outside the player and library composition scopes. */
@Composable
private fun Seek(position: PlaybackPosition, duration: Long, identity: String, enabled: Boolean, onSeek: (Float) -> Unit) {
    var pending by remember(identity) { mutableStateOf<Float?>(null) }
    val fraction = if (duration > 0) (position.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column { Slider(pending ?: fraction, onValueChange = { pending = it }, onValueChangeFinished = { pending?.let(onSeek); pending = null }, enabled = enabled && duration > 0)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(time(position.positionMs), style = MaterialTheme.typography.labelSmall); Text(time(duration), style = MaterialTheme.typography.labelSmall) } }
}
private fun time(ms: Long): String { val seconds = ms.coerceAtLeast(0) / 1000; return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}" }
@Composable
private fun Transport(playing: Boolean, loading: Boolean, previous: Boolean, next: Boolean, locked: Boolean, shuffled: Boolean, repeat: Int, action: RebirthTransport, expressive: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        IconToggleButton(shuffled, onCheckedChange = { action.shuffle() }, enabled = !locked) { Icon(Icons.Rounded.Shuffle, "Aleatorio") }
        IconButton(action.previous, enabled = previous && !locked) { Icon(Icons.Rounded.SkipPrevious, "Anterior") }
        FilledIconButton(action.playPause, Modifier.width(if (expressive) 88.dp else 64.dp).height(64.dp), shape = RoundedCornerShape(if (expressive) 22.dp else 32.dp)) { if (loading) CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary) else Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pausar" else "Reproducir") }
        IconButton(action.next, enabled = next && !locked) { Icon(Icons.Rounded.SkipNext, "Siguiente") }
        IconToggleButton(repeat != 0, onCheckedChange = { action.repeat() }, enabled = !locked) { Icon(if (repeat == 1) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repetición: $repeat") }
    }
}

/** Playhead reads are isolated from the artwork and transport composition. */
@Composable
private fun RebirthLyrics(lines: List<LyricLine>?, unavailable: Boolean, position: PlaybackPosition,
    enabled: Boolean, onSeek: (Long) -> Unit, modifier: Modifier) {
    val active = lines?.indexOfLast { it.timeMs <= position.positionMs } ?: -1
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (lines.isNullOrEmpty()) item { Text(if (unavailable) "No hay letra disponible para esta canción." else "Buscando letra…", style = MaterialTheme.typography.headlineSmall) }
        else itemsIndexed(lines) { index, line ->
            if (line.text.isNotBlank()) Text(line.text,
                modifier = Modifier.fillMaxWidth().clickable(enabled = enabled && line.timeMs >= 0) { onSeek(line.timeMs) }.padding(vertical = 8.dp),
                style = if (index == active) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                fontWeight = if (index == active) FontWeight.Bold else FontWeight.Normal,
                color = if (index == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RebirthVolume(modifier: Modifier) {
    val volume = rememberPlayerVolume()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.VolumeMute, "Volumen")
        Slider(volume.level.value, onValueChange = volume::drag, onValueChangeFinished = volume::release,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
        Icon(Icons.Rounded.VolumeUp, null)
    }
}
