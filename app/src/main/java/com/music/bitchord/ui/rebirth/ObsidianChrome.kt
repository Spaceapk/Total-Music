package com.music.bitchord.ui.rebirth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.components.LocalPlayerPull
import com.music.bitchord.ui.components.miniPlayerGestures
import com.music.bitchord.ui.components.playerDockArt
import com.music.bitchord.ui.player.LocalPlayerDock
import com.music.bitchord.ui.player.LocalMotionReduced

/** One integrated, size-animated surface: mini player plus navigation/search. */
@Composable
internal fun ObsidianDock(data: RebirthLibraryData, actions: RebirthLibraryActions) {
    ObsidianNavigationDock(
        song = data.currentSong,
        playing = data.isPlaying,
        loading = data.loading,
        reduceMotion = data.reduceMotion || !data.transitions,
        destination = data.destination,
        query = data.query,
        onQuery = actions.onQuery,
        onDestination = actions.onDestination,
        onExpandPlayer = actions.onExpandPlayer,
        onPlayPause = actions.onPlayPause,
        onNext = actions.onNext,
        onPrevious = actions.onPrevious,
    )
}

@Composable
internal fun ObsidianNavigationDock(
    song: Song?,
    playing: Boolean,
    loading: Boolean,
    reduceMotion: Boolean,
    destination: RebirthDestination,
    query: String,
    onQuery: (String) -> Unit,
    onDestination: (RebirthDestination) -> Unit,
    onExpandPlayer: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit = {},
) {
    var searchExpanded by rememberSaveable { mutableStateOf(destination == RebirthDestination.SEARCH) }
    LaunchedEffect(destination) { if (destination == RebirthDestination.SEARCH) searchExpanded = true }
    val reduced = reduceMotion || LocalMotionReduced.current
    val colors = MaterialTheme.colorScheme
    val corner by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (song != null) 32.dp else 28.dp,
        animationSpec = if (reduced) androidx.compose.animation.core.snap() else tween(260),
        label = "obsidian-dock-corner",
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .animateContentSize(if (reduced) androidx.compose.animation.core.snap() else tween(280))
            .testTag("obsidian-dock"),
        shape = RoundedCornerShape(corner),
        color = colors.surfaceContainerHigh.copy(alpha = 0.96f),
        contentColor = colors.onSurface,
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (song != null) {
                ObsidianMiniPlayer(
                    song = song, playing = playing, loading = loading, reduced = reduced,
                    onExpand = onExpandPlayer, onPlayPause = onPlayPause,
                    onNext = onNext, onPrevious = onPrevious,
                )
            }
            AnimatedContent(
                targetState = searchExpanded,
                label = "obsidian-navigation-search",
                transitionSpec = {
                    if (reduced) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                    else androidx.compose.animation.fadeIn(tween(180)) togetherWith androidx.compose.animation.fadeOut(tween(120))
                },
            ) { searching ->
                if (searching) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { searchExpanded = false; onQuery(""); onDestination(RebirthDestination.HOME) }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                        TextField(
                            value = query,
                            onValueChange = onQuery,
                            modifier = Modifier.weight(1f).testTag("obsidian-search"),
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.obsidian_search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        )
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQuery("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.obsidian_close))
                            }
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ObsidianNavItem(Icons.Rounded.Home, stringResource(R.string.home), destination == RebirthDestination.HOME, Modifier.weight(1f)) {
                            onQuery(""); onDestination(RebirthDestination.HOME)
                        }
                        ObsidianNavItem(Icons.Rounded.LibraryMusic, stringResource(R.string.library), destination == RebirthDestination.LIBRARY, Modifier.weight(1f)) {
                            onQuery(""); onDestination(RebirthDestination.LIBRARY)
                        }
                        ObsidianNavItem(Icons.Rounded.Search, stringResource(R.string.search), false, Modifier.weight(1f)) {
                            searchExpanded = true; onDestination(RebirthDestination.SEARCH)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ObsidianMiniPlayer(
    song: Song,
    playing: Boolean,
    loading: Boolean,
    reduced: Boolean,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val dock = LocalPlayerDock.current
    val pull = LocalPlayerPull.current
    val rotation = remember(song.videoId) { Animatable(0f) }
    LaunchedEffect(playing, reduced, song.videoId) {
        if (playing && !reduced) {
            while (true) {
                rotation.animateTo(
                    targetValue = rotation.value + 360f,
                    animationSpec = tween(durationMillis = 18_000, easing = LinearEasing),
                )
            }
        }
    }
    Row(
        Modifier.fillMaxWidth()
            .miniPlayerGestures(pull = pull, onNext = onNext, onPrevious = onPrevious)
            .clickable(onClick = onExpand)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Cover(
            song = song,
            modifier = Modifier.size(48.dp).testTag("obsidian-disc")
                .playerDockArt(dock, 24.dp)
                .graphicsLayer { rotationZ = if (reduced) 0f else rotation.value % 360f },
            corner = 24,
            placeholder = Icons.Rounded.MusicNote,
            dock = dock,
        )
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onPlayPause, modifier = Modifier.size(48.dp)) {
            if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = stringResource(if (playing) R.string.pause else R.string.play))
        }
        IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.widget_next))
        }
    }
}

@Composable
private fun ObsidianNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(CircleShape)
            .background(if (selected) colors.primary.copy(alpha = 0.14f) else Color.Transparent)
            .semantics { this.selected = selected; role = Role.Tab }
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) colors.primary else colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) colors.primary else colors.onSurfaceVariant, maxLines = 1)
    }
}
