package com.music.bitchord.ui.rebirth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.music.bitchord.R
import com.music.bitchord.data.PlaylistSort
import com.music.bitchord.data.settings.LocalMusicSort
import com.music.bitchord.ui.player.PlayerVisualStyle
import com.music.bitchord.ui.player.rebirthColors

/**
 * The pieces both app-side rebirth screens share, and nothing else.
 *
 * Same rule as the player's tokens: a shared label or a shared dialog frame is
 * fine, a shared screen is not. Everything here is either a string the app
 * already had or a frame with no opinion about what goes in it.
 */

/** The colour scheme the whole app is drawn in while a rebirth screen is up. */
@Composable
internal fun rebirthAppColors(style: PlayerVisualStyle) = rebirthColors(style, MaterialTheme.colorScheme)

@Composable
internal fun RebirthCategory.label(): String = stringResource(
    when (this) {
        RebirthCategory.SONGS -> R.string.songs
        RebirthCategory.ALBUMS -> R.string.albums
        RebirthCategory.ARTISTS -> R.string.artists
        RebirthCategory.PLAYLISTS -> R.string.playlists
        RebirthCategory.FAVORITES -> R.string.rebirth_favorites
    },
)

@Composable
internal fun RebirthDestination.label(): String = stringResource(
    when (this) {
        RebirthDestination.HOME -> R.string.home
        RebirthDestination.LIBRARY -> R.string.library
        RebirthDestination.SEARCH -> R.string.search
    },
)

@Composable
internal fun LocalMusicSort.rebirthLabel(): String = stringResource(
    when (this) {
        LocalMusicSort.TRACK_NUMBER -> R.string.sort_default
        LocalMusicSort.TITLE_ASC -> R.string.sort_title_ascending
        LocalMusicSort.TITLE_DESC -> R.string.sort_title_descending
        LocalMusicSort.DATE_ADDED -> R.string.sort_date_added
        LocalMusicSort.DATE_MODIFIED -> R.string.sort_date_modified
    },
)

@Composable
internal fun PlaylistSort.rebirthLabel(): String = stringResource(
    when (this) {
        PlaylistSort.MANUAL -> R.string.space_sort_manual
        PlaylistSort.TITLE_ASC -> R.string.sort_title_ascending
        PlaylistSort.TITLE_DESC -> R.string.sort_title_descending
        PlaylistSort.ARTIST -> R.string.space_sort_artist
        PlaylistSort.RECENTLY_ADDED -> R.string.space_sort_recent
    },
)

/** The caption under a collection: how many tracks it holds. */
@Composable
internal fun collectionCaption(size: Int): String = stringResource(R.string.card_songs_format, size.toString())

/**
 * A dialog in the screen's own language.
 *
 * The app's dialogs are Material's, which is right for Original and wrong for
 * both rebirth styles: they are drawn over a themed surface that has its own
 * corners, and a stock `AlertDialog` on top of it reads as a different app.
 */
@Composable
internal fun RebirthDialog(
    style: PlayerVisualStyle,
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: (() -> Unit)?,
    dismissLabel: String = stringResource(R.string.cancel),
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val glass = style == PlayerVisualStyle.LIQUID_GLASS_PLUS
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(if (glass) 28.dp else 34.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    text = title,
                    style = if (glass) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.headlineSmall
                    },
                    fontWeight = FontWeight.SemiBold,
                )
                Column(Modifier.padding(top = 14.dp), content = content)
                Row(
                    Modifier.fillMaxWidth().padding(top = 18.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(dismissLabel) }
                    if (onConfirm != null) {
                        TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) }
                    }
                }
            }
        }
    }
}

/** A text field, shaped like the sheet it sits in. */
@Composable
internal fun RebirthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth(),
    )
}

/** The search box, with the clear affordance the app's own field has. */
@Composable
internal fun RebirthSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    glass: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.clear_search))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(if (glass) 22.dp else 30.dp),
        modifier = modifier.fillMaxWidth(),
    )
}

/** What a screen says when it has nothing to show, and why. */
@Composable
internal fun RebirthEmptyState(
    icon: ImageVector,
    title: String,
    message: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
        }
        Text(
            text = title,
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        message?.let {
            Text(
                text = it,
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A caption over a block, with an optional action on the right. */
@Composable
internal fun RebirthSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        action?.invoke()
    }
}

/** A pill the reader can pick, used by both styles with their own radii. */
@Composable
internal fun RebirthFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    glass: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(if (glass) 14.dp else 24.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = Modifier.clip(RoundedCornerShape(if (glass) 14.dp else 24.dp)).clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
