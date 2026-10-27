package com.music.bitchord.ui.rebirth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.Color
import com.music.bitchord.ui.components.liquidGlass
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.player.PlayerVisualStyle

/** Owns its cover. Deliberately does not use the legacy shared artwork overlay. */
@Composable
fun RebirthMiniPlayer(style: PlayerVisualStyle, song: Song, playing: Boolean, loading: Boolean,
    onPlayPause: () -> Unit, onNext: () -> Unit, onExpand: () -> Unit) {
    val effects by com.music.bitchord.data.settings.AppSettings.playerGlassEffects.collectAsStateWithLifecycle()
    val glass = style == PlayerVisualStyle.LIQUID_GLASS_PLUS
    if (!glass) {
        Surface(shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomStart = 10.dp, bottomEnd = 10.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            Column(Modifier.clickable(onClick = onExpand).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage(song.thumbnailUrl, null, Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f)) {
                        Text("AHORA SUENA", style = MaterialTheme.typography.labelSmall)
                        Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                        Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                    FilledIconButton(onPlayPause, Modifier.size(56.dp), shape = RoundedCornerShape(18.dp)) {
                        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary)
                        else Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pausar" else "Reproducir")
                    }
                    IconButton(onClick = onNext) { Icon(Icons.Rounded.SkipNext, "Siguiente") }
                }
            }
        }
        return
    }
    val shape = RoundedCornerShape(if (glass) 28.dp else 18.dp)
    Surface(shape = shape, color = if (glass && effects) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).then(if (glass && effects) Modifier.liquidGlass(shape) else Modifier)) {
        Row(Modifier.clickable(onClick = onExpand).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(song.thumbnailUrl, null, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall); Text(song.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall) }
            IconButton(onClick = onPlayPause) { if (loading) CircularProgressIndicator(Modifier.size(24.dp)) else Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pausar" else "Reproducir") }
            IconButton(onClick = onNext) { Icon(Icons.Rounded.SkipNext, "Siguiente") }
        }
    }
}
