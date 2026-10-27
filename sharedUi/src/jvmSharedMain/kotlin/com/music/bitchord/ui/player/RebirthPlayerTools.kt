package com.music.bitchord.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.rebirth_playback_tools
import dev.chrisbanes.haze.HazeState
import org.jetbrains.compose.resources.stringResource

/**
 * One row of the playback tools.
 *
 * The caller owns the labels, the icons and the callbacks, because every one of
 * them is a surface the app already had: the output drawer, the provider sheet,
 * the lyric offset slider, the equalizer page. This sheet is a new *route* to
 * them — themed, in the player's own visual language — not a new set of
 * features, which is why nothing here touches playback state directly.
 */
internal class RebirthTool(
    val label: String,
    val icon: ImageVector,
    val caption: String? = null,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * "Playback tools", in either rebirth style.
 *
 * Before 1.8.0 this button opened the *original* player, which meant the one
 * control a redrawn screen offered dropped the reader back into the screen it
 * had just replaced. The tools are now a sheet of their own; the expressive
 * style lists them as full-width rows, the glass one as a two-column grid of
 * tiles, and both call the same callbacks.
 */
@Composable
internal fun RebirthToolsSheet(
    style: PlayerVisualStyle,
    hazeState: HazeState,
    tools: List<RebirthTool>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = rebirthSpec(style)
    PlayerDrawer(
        hazeState = hazeState,
        title = stringResource(Res.string.rebirth_playback_tools),
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        if (spec.glass) {
            tools.chunked(2).forEach { pair ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    pair.forEach { tool ->
                        GlassToolTile(tool, Modifier.weight(1f))
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
        } else {
            tools.forEach { tool ->
                ExpressiveToolRow(tool)
                Spacer(Modifier.height(6.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ExpressiveToolRow(tool: RebirthTool) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = tool.enabled, onClick = tool.onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = tool.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (tool.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                tool.caption?.let { caption ->
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassToolTile(tool: RebirthTool, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
        modifier = modifier,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = tool.enabled, onClick = tool.onClick)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = tool.label,
                style = MaterialTheme.typography.titleSmall,
                color = if (tool.enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            tool.caption?.let { caption ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
