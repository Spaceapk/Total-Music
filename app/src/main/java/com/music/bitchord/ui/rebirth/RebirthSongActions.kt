package com.music.bitchord.ui.rebirth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.player.PlayerVisualStyle
import com.music.bitchord.ui.player.rebirthColors

/**
 * The song menu's frame, in both rebirth styles.
 *
 * The rows are the caller's — [content] is the same list the original sheet
 * builds, which is what keeps every action (download, lyrics, playlist, sleep
 * timer, radio, share) reachable here. What changes is the frame around them:
 * an editorial header with the two queue actions laid out as buttons in the
 * expressive style, a square cover card with a capsule of actions in the glass
 * one.
 */
@Composable
fun RebirthSongActions(
    style: PlayerVisualStyle,
    song: Song,
    onNext: () -> Unit,
    onQueue: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = style == PlayerVisualStyle.LIQUID_GLASS_PLUS
    val colors = rebirthColors(style, MaterialTheme.colorScheme)
    MaterialTheme(colorScheme = colors) {
        Surface(
            modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = if (glass) 32.dp else 40.dp, topEnd = if (glass) 32.dp else 40.dp),
            color = if (glass) colors.surfaceContainer else colors.background,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.88f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            ) {
                if (glass) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AsyncImage(
                            model = song.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier.size(104.dp).clip(RoundedCornerShape(26.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Text(
                            text = song.title,
                            modifier = Modifier.padding(top = 12.dp),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        Surface(
                            shape = CircleShape,
                            color = colors.surfaceContainerHigh.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 16.dp),
                        ) {
                            Row(
                                Modifier.padding(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilledTonalButton(
                                    onClick = onNext,
                                    modifier = Modifier.weight(1f),
                                    shape = CircleShape,
                                ) {
                                    Text(
                                        text = stringResource(R.string.play_next),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                FilledTonalButton(
                                    onClick = onQueue,
                                    modifier = Modifier.weight(1f),
                                    shape = CircleShape,
                                ) {
                                    Text(
                                        text = stringResource(R.string.add_to_queue),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        AsyncImage(
                            model = song.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier.size(88.dp).clip(RoundedCornerShape(26.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FilledTonalButton(
                            onClick = onNext,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(22.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.play_next),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Button(
                            onClick = onQueue,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(22.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.add_to_queue),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
                content()
            }
        }
    }
}
