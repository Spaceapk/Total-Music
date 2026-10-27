package com.music.bitchord.ui.rebirth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.player.PlayerVisualStyle
import com.music.bitchord.ui.player.rebirthColors

@Composable
fun RebirthSongActions(style: PlayerVisualStyle, song: Song, onNext: () -> Unit, onQueue: () -> Unit,
    modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val glass = style == PlayerVisualStyle.LIQUID_GLASS_PLUS
    val colors = rebirthColors(style, MaterialTheme.colorScheme)
    MaterialTheme(colorScheme = colors) {
        Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(topStart = if (glass) 32.dp else 40.dp, topEnd = if (glass) 32.dp else 40.dp), color = if (glass) colors.surfaceContainer else colors.background) {
            Column(Modifier.fillMaxWidth().heightIn(max = LocalConfiguration.current.screenHeightDp.dp * .88f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
                if (glass) Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(song.thumbnailUrl, null, Modifier.size(100.dp).clip(RoundedCornerShape(24.dp)), contentScale = ContentScale.Crop)
                    Text(song.title, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleLarge, maxLines = 3)
                    Text(song.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    Surface(shape = RoundedCornerShape(30.dp), color = colors.surfaceContainerHigh, modifier = Modifier.padding(top = 16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onNext, modifier = Modifier.weight(1f)) { Text("Reproducir después") }
                            TextButton(onClick = onQueue, modifier = Modifier.weight(1f)) { Text("A la cola") }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        AsyncImage(song.thumbnailUrl, null, Modifier.size(88.dp).clip(RoundedCornerShape(26.dp)), contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f)) { Text(song.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, maxLines = 3); Text(song.artist, maxLines = 2) }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(onClick = onNext, modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp)) { Text("Reproducir después") }
                        Button(onClick = onQueue, modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp)) { Text("Añadir a cola") }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                content()
            }
        }
    }
}
