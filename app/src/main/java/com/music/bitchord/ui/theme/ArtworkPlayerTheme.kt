package com.music.bitchord.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.TotalMusicSettings
import com.music.bitchord.ui.components.rememberRemoteArtworkUrl

/** Scoped to the full player: the library and the user's chosen accent stay independent. */
@Composable
fun ArtworkPlayerTheme(song: Song, content: @Composable () -> Unit) {
    val enabled by TotalMusicSettings.artworkColors.collectAsStateWithLifecycle()
    val style by com.music.bitchord.data.settings.AppSettings.playerVisualStyle.collectAsStateWithLifecycle()
    if (!enabled || (!LocalGoogleExpressive.current && style != com.music.bitchord.ui.player.PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS)) {
        content()
        return
    }
    val artwork = rememberRemoteArtworkUrl(song)
    if (artwork == null) {
        content()
        return
    }
    val base = MaterialTheme.colorScheme
    val dark = base.background.luminance() < 0.5f
    // Shares the bounded 128px, off-main quantizer/cache with album pages.
    val palette = rememberArtworkPalette(artwork, dark = dark, animateCached = true)
    val primary = palette.accent
    val onPrimary = if (primary.luminance() > 0.179f) Color.Black else Color.White
    val background = if (dark) Color.Black else palette.background
    val container = palette.elevated
    val colors = base.copy(
        background = background, onBackground = palette.onBackground,
        surface = background, onSurface = palette.onBackground,
        surfaceVariant = container, onSurfaceVariant = palette.onBackgroundVariant,
        primary = primary, onPrimary = onPrimary,
        secondary = primary, onSecondary = onPrimary,
        tertiary = primary, onTertiary = onPrimary,
        primaryContainer = container, onPrimaryContainer = palette.onBackground,
        secondaryContainer = container, onSecondaryContainer = palette.onBackground,
        tertiaryContainer = container, onTertiaryContainer = palette.onBackground,
        surfaceContainerLowest = background,
        surfaceContainerLow = lerp(background, container, 0.35f),
        surfaceContainer = lerp(background, container, 0.55f),
        surfaceContainerHigh = lerp(background, container, 0.8f),
        surfaceContainerHighest = container,
        surfaceTint = Color.Transparent,
        outline = palette.divider,
    )
    val resolved = if (palette.background == base.background && palette.accent == base.primary) base else colors
    MaterialTheme(colorScheme = resolved, content = content)
}
