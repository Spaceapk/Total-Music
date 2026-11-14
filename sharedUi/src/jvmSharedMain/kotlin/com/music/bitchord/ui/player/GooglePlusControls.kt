package com.music.bitchord.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.music.bitchord.sharedui.resources.*
import org.jetbrains.compose.resources.stringResource

/** Only the Google Plus scene uses these measurements and timings. */
internal object GooglePlusTokens {
    val transportHeight = 68.dp
    val controlGap = 6.dp
    val toggleCorner = 12.dp
    const val stateDurationMs = 160
    const val pageDurationMs = 300
}

@Composable
internal fun GooglePlusTransport(
    playing: Boolean, loading: Boolean, previousEnabled: Boolean, nextEnabled: Boolean,
    locked: Boolean, shuffled: Boolean, repeatMode: Int, liked: Boolean,
    transport: RebirthTransport, onLike: () -> Unit, modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = rememberRebirthMotionEnabled()
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().testTag("expressive-transport"), horizontalArrangement = Arrangement.spacedBy(GooglePlusTokens.controlGap)) {
            GooglePlusTransportButton(Icons.Rounded.SkipPrevious, stringResource(Res.string.plus_previous),
                previousEnabled && !locked, transport.previous, Modifier.weight(1f))
            FilledIconButton(
                onClick = transport.playPause,
                modifier = Modifier.weight(1f).height(GooglePlusTokens.transportHeight).testTag("google-plus-play"),
                shape = RoundedCornerShape(24.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer),
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else Crossfade(playing, animationSpec = tween(if (motion) GooglePlusTokens.stateDurationMs else 0)) { active ->
                    Icon(if (active) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        stringResource(if (active) Res.string.pause else Res.string.play), Modifier.size(32.dp))
                }
            }
            GooglePlusTransportButton(Icons.Rounded.SkipNext, stringResource(Res.string.plus_next),
                nextEnabled && !locked, transport.next, Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))
        Surface(shape = CircleShape, color = colors.surfaceContainerLow, modifier = Modifier.fillMaxWidth(.90f)) {
            Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                GooglePlusToggle(Icons.Rounded.Shuffle, shuffled, !locked,
                    stringResource(if (shuffled) Res.string.shuffle_on else Res.string.shuffle_off),
                    transport.shuffle, Modifier.weight(1f))
                GooglePlusToggle(if (repeatMode == 1) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    repeatMode != 0, !locked,
                    stringResource(when (repeatMode) { 0 -> Res.string.repeat_off; 1 -> Res.string.repeat_one; else -> Res.string.repeat_all }),
                    transport.repeat, Modifier.weight(1f))
                GooglePlusToggle(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    liked, true, stringResource(if (liked) Res.string.rebirth_remove_favorite else Res.string.rebirth_add_favorite),
                    onLike, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GooglePlusTransportButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    FilledIconButton(onClick, modifier.height(GooglePlusTokens.transportHeight), enabled = enabled, shape = CircleShape) {
        Icon(icon, label, Modifier.size(28.dp))
    }
}

@Composable
private fun GooglePlusToggle(icon: ImageVector, selected: Boolean, enabled: Boolean, label: String, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val motion = rememberRebirthMotionEnabled()
    val background by animateColorAsState(if (selected) colors.primaryContainer else colors.surfaceContainer,
        tween(if (motion) GooglePlusTokens.stateDurationMs else 0))
    FilledIconToggleButton(
        checked = selected, onCheckedChange = { onClick() }, enabled = enabled,
        modifier = modifier.height(48.dp).semantics { stateDescription = label },
        shape = RoundedCornerShape(GooglePlusTokens.toggleCorner),
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = background, checkedContainerColor = background,
            contentColor = colors.onSurface, checkedContentColor = colors.onPrimaryContainer),
    ) { Icon(icon, label, Modifier.size(24.dp)) }
}
