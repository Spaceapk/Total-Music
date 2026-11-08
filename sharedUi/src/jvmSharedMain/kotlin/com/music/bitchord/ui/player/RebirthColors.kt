package com.music.bitchord.ui.player

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared tokens, not shared screen compositions. Original is returned unchanged.
 *
 * The two rebirth styles are different products: one is an editorial Material
 * reading of the same catalogue, the other is a dark, frosted control surface.
 * What they agree on is the plumbing — the transport, the queue, the lyric
 * clock — which lives in sibling files. What they disagree about is everything
 * a screenshot would show, and that is what this file names.
 */
fun rebirthColors(style: PlayerVisualStyle, base: ColorScheme): ColorScheme {
    val dark = base.background.luminance() < .5f
    return when (style) {
        PlayerVisualStyle.ORIGINAL -> base
        PlayerVisualStyle.OBSIDIAN -> darkColorScheme(
            primary = Color(0xFFD4B6FF), onPrimary = Color(0xFF26004F),
            primaryContainer = Color(0xFF39264F), onPrimaryContainer = Color(0xFFF0E2FF),
            secondary = Color(0xFFBFC6DC), tertiary = Color(0xFF9ED8D0),
            background = Color(0xFF08090C), surface = Color(0xFF0E1015),
            surfaceVariant = Color(0xFF242630), onBackground = Color(0xFFF4F1FA),
            onSurface = Color(0xFFF4F1FA), onSurfaceVariant = Color(0xFFB8B5C4),
            surfaceContainer = Color(0xFF15171E), surfaceContainerHigh = Color(0xFF1D1F28),
            surfaceContainerHighest = Color(0xFF282A35), outline = Color(0xFF555361),
        )
        PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS -> base.copy(
            background = if (dark) Color.Black else Color(0xFFFFF9F0),
            surface = if (dark) Color.Black else Color(0xFFFFF9F0),
        )
        PlayerVisualStyle.LIQUID_GLASS_PLUS -> if (dark) {
            darkColorScheme(
                background = Color.Black,
                surface = Color(0xFF101D2C),
                onSurface = Color(0xFFF0F5FF),
                primary = Color(0xFF94DAF3),
                surfaceContainer = Color(0xFF132437),
                surfaceContainerHigh = Color(0xFF1A3045),
            )
        } else {
            lightColorScheme(
                background = Color(0xFFF2F8FC),
                surface = Color(0xFFF8FCFF),
                onSurface = Color(0xFF102638),
                primary = Color(0xFF146581),
                surfaceContainer = Color(0xFFE9F3F9),
                surfaceContainerHigh = Color(0xFFDDECF5),
            )
        }
    }
}

/** Whether the scheme a player scene was handed is a dark one. */
internal fun ColorScheme.isDarkPlayerScheme(): Boolean = background.luminance() < .5f

/**
 * Measurements and type for one rebirth scene.
 *
 * Kept as one value rather than scattered literals so a scene can be read as a
 * composition: anything the two styles disagree about is here, and anything
 * that differs between two screens of the *same* style is not.
 */
internal class RebirthSpec(
    val style: PlayerVisualStyle,
    /** Corner of the sleeve. */
    val artworkCorner: Dp,
    /** Corner of the cards and panels the scene lays out. */
    val panelCorner: Dp,
    /** Corner of the transport surface the buttons sit on. */
    val controlCorner: Dp,
    /** Air at the screen edge. */
    val gutter: Dp,
    /** Air between two blocks. */
    val gap: Dp,
    /** The song title over the sleeve. */
    val titleStyle: TextStyle,
    /** A row's own label: action names, list entries. */
    val labelStyle: TextStyle,
    /** Micro caps: section captions, "now playing" markers. */
    val captionStyle: TextStyle,
) {
    val glass: Boolean get() = style == PlayerVisualStyle.LIQUID_GLASS_PLUS
}

/** Not remembered: it reads the ambient typography and holds nothing else. */
@Composable
internal fun rebirthSpec(style: PlayerVisualStyle): RebirthSpec {
    val type = MaterialTheme.typography
    return when (style) {
        PlayerVisualStyle.OBSIDIAN -> RebirthSpec(
            style = style, artworkCorner = 1000.dp, panelCorner = 24.dp,
            controlCorner = 28.dp, gutter = 20.dp, gap = 16.dp,
            titleStyle = type.headlineMedium.copy(fontWeight = FontWeight.Bold),
            labelStyle = type.titleSmall, captionStyle = type.labelSmall,
        )
        PlayerVisualStyle.ORIGINAL,
        PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS,
        -> RebirthSpec(
            style = style,
            artworkCorner = 30.dp,
            panelCorner = 28.dp,
            controlCorner = 40.dp,
            gutter = 20.dp,
            gap = 18.dp,
            titleStyle = type.displaySmall.copy(fontWeight = FontWeight.Black),
            labelStyle = type.labelLarge,
            captionStyle = type.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium),
        )
        PlayerVisualStyle.LIQUID_GLASS_PLUS -> RebirthSpec(
            style = style,
            artworkCorner = 34.dp,
            panelCorner = 26.dp,
            controlCorner = 24.dp,
            gutter = 18.dp,
            gap = 14.dp,
            titleStyle = type.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            labelStyle = type.titleSmall,
            captionStyle = type.labelSmall.copy(letterSpacing = 1.6.sp, fontWeight = FontWeight.Medium),
        )
    }
}
