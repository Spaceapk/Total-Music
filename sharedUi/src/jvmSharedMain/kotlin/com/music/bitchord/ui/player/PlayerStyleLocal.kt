package com.music.bitchord.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Which player presentation the surface being composed belongs to.
 *
 * [PlayerVisualStyle] already travels as a parameter through the player and
 * through the app's rebirth screens. It does not reach the *shared* sheets —
 * the output drawer, the cast and pipeline popups, the lyric provider sheet —
 * because those were written when there was one player and take no style
 * argument. Threading a parameter through five call sites in two modules, plus
 * every row inside them, would have meant changing the original player to
 * serve the new ones.
 *
 * A composition local instead: the player installs the style once, at the top
 * of whichever scene it drew, and a sheet that wants to look like its host asks
 * for it. Anything composed outside a player — the app's own settings, an
 * activity preview — gets [PlayerVisualStyle.ORIGINAL], which is exactly the
 * look those surfaces have always had.
 */
val LocalPlayerStyle: ProvidableCompositionLocal<PlayerVisualStyle> =
    staticCompositionLocalOf { PlayerVisualStyle.ORIGINAL }

/** Whether the surface being composed belongs to a rebirth player. */
val currentPlayerStyle: PlayerVisualStyle
    @Composable get() = LocalPlayerStyle.current

/**
 * The surface colour a player sheet puts behind itself.
 *
 * The glass style is a dark, frosted product whatever the app's theme is; the
 * expressive one is an editorial light surface that has to follow the scheme
 * it was handed, or a drawer opened from it looks like a piece of another app.
 */
@Composable
internal fun playerSheetBackground(style: PlayerVisualStyle): Color = when (style) {
    PlayerVisualStyle.ORIGINAL -> Color(0xFF121212)
    PlayerVisualStyle.LIQUID_GLASS_PLUS -> Color(0xFF0B1622)
    PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS -> androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
}

/** The text and icon colour on that surface. */
@Composable
internal fun playerSheetForeground(style: PlayerVisualStyle): Color = when (style) {
    PlayerVisualStyle.ORIGINAL -> Color.White
    PlayerVisualStyle.LIQUID_GLASS_PLUS -> Color(0xFFEAF3FA)
    PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS -> androidx.compose.material3.MaterialTheme.colorScheme.onSurface
}

/**
 * The ink a player sheet's rows are drawn in.
 *
 * The four sheets that open over a player — output, lyric providers, lyric
 * offset, the party's member list — each used to answer this question for
 * themselves from the app-wide expressive switch. They now answer it the same
 * way the drawer behind them does, so a row can never be dark on a dark sheet.
 */
@Composable
internal fun playerSheetInk(): Color {
    val style = LocalPlayerStyle.current
    return if (style != PlayerVisualStyle.ORIGINAL) {
        playerSheetForeground(style)
    } else if (com.music.bitchord.ui.theme.LocalGoogleExpressive.current) {
        androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    } else {
        Color.White
    }
}

/** The corner a sheet is drawn with, per style. */
internal fun playerSheetCorner(style: PlayerVisualStyle) = when (style) {
    PlayerVisualStyle.ORIGINAL -> androidx.compose.foundation.shape.RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    // Glass is tighter and rounder-cornered: a slab of glass, not a card.
    PlayerVisualStyle.LIQUID_GLASS_PLUS -> androidx.compose.foundation.shape.RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    // Expressive is a printed page pulled up from the bottom: square shoulders.
    PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS -> androidx.compose.foundation.shape.RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)
}
