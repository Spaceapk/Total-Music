package com.music.bitchord.ui.rebirth

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.components.liquidGlass
import com.music.bitchord.ui.components.LocalPlayerPull
import com.music.bitchord.ui.components.MiniPlayerPull
import com.music.bitchord.ui.components.miniPlayerGestures
import com.music.bitchord.ui.player.LocalPlayerDock
import com.music.bitchord.ui.player.PlayerDock
import com.music.bitchord.R
import com.music.bitchord.data.model.Song

/**
 * The two mini players and the expressive navigation, which the library scenes
 * draw below themselves.
 *
 * They are separate compositions, not one composition with a flag: the
 * expressive one is a card that continues the page's columns, the glass one is
 * a capsule that sits over whatever is behind it and shows the smallest
 * possible version of the session.
 */

@Composable
internal fun RebirthExpressiveMiniPlayer(
    song: Song?,
    playing: Boolean,
    loading: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
    onPrevious: () -> Unit = {},
    locked: Boolean = false,
    onBlocked: () -> Unit = {},
    /** Where the player's artwork lands when it closes into this bar. */
    dock: PlayerDock? = null,
    /** Dragging the bar up into the player. */
    pull: MiniPlayerPull? = null,
) {
    // Read here rather than at the call sites: a shell lays the bar out and
    // has nothing to thread — see [LocalPlayerDock] and [LocalPlayerPull].
    val resolvedDock = dock ?: LocalPlayerDock.current
    val resolvedPull = pull ?: LocalPlayerPull.current
    if (song == null) return
    com.music.bitchord.ui.theme.ArtworkPlayerTheme(song) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .miniPlayerGestures(
                pull = resolvedPull, onNext = onNext, onPrevious = onPrevious,
                locked = locked, onBlocked = onBlocked,
            ),
    ) {
        Row(
            Modifier.clickable(onClick = onExpand).padding(start = 6.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Cover(song = song, modifier = Modifier.size(36.dp), corner = 1000, placeholder = Icons.Rounded.MusicNote, dock = resolvedDock)
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(song.title, style = MaterialTheme.typography.labelLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onPrevious, enabled = !locked, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.widget_previous), Modifier.size(18.dp))
            }
            FilledIconButton(onClick = onPlayPause, shape = CircleShape, modifier = Modifier.size(48.dp)) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    stringResource(if (playing) R.string.pause else R.string.play), Modifier.size(20.dp))
            }
            IconButton(onClick = onNext, enabled = !locked, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.SkipNext, stringResource(R.string.widget_next), Modifier.size(18.dp))
            }
        }
    }
    }
}

@Composable
internal fun RebirthGlassMiniPlayer(
    song: Song?,
    playing: Boolean,
    loading: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
    glassEffects: Boolean = false,
    onPrevious: () -> Unit = {},
    locked: Boolean = false,
    onBlocked: () -> Unit = {},
    /** Where the player's artwork lands when it closes into this capsule. */
    dock: PlayerDock? = null,
    /** Dragging the bar up into the player. */
    pull: MiniPlayerPull? = null,
) {
    // Read here rather than at the call sites: see the expressive bar.
    val resolvedDock = dock ?: LocalPlayerDock.current
    val resolvedPull = pull ?: LocalPlayerPull.current
    if (song == null) return
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = if (glassEffects) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .miniPlayerGestures(
                pull = resolvedPull,
                onNext = onNext,
                onPrevious = onPrevious,
                locked = locked,
                onBlocked = onBlocked,
            )
            .then(if (glassEffects) Modifier.liquidGlass(RoundedCornerShape(26.dp), clear = true) else Modifier),
    ) {
        Row(
            Modifier.clickable(onClick = onExpand).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Cover(song = song, modifier = Modifier.size(40.dp), corner = 12, placeholder = Icons.Rounded.MusicNote, dock = resolvedDock)
            Column(Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(40.dp), shape = CircleShape) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (playing) R.string.pause else R.string.play),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.widget_next), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
internal fun RebirthExpressiveNav(
    current: RebirthDestination,
    onDestination: (RebirthDestination) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExpressiveNavItem(RebirthDestination.HOME, Icons.Rounded.Home, current, onDestination, Modifier.weight(1f))
        ExpressiveNavItem(RebirthDestination.SEARCH, Icons.Rounded.Search, current, onDestination, Modifier.weight(1f))
        ExpressiveNavItem(RebirthDestination.LIBRARY, Icons.Rounded.LibraryMusic, current, onDestination, Modifier.weight(1f))
    }
}

@Composable
private fun ExpressiveNavItem(
    destination: RebirthDestination,
    icon: ImageVector,
    current: RebirthDestination,
    onDestination: (RebirthDestination) -> Unit,
    modifier: Modifier,
) {
    val selected = destination == current
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                },
            )
            .semantics { this.selected = selected; role = Role.Tab }
            .clickable { onDestination(destination) }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = destination.label(),
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = destination.label(),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
