package com.music.bitchord.ui.player

import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Shared tokens, not shared screen compositions. Original is returned unchanged. */
fun rebirthColors(style: PlayerVisualStyle, base: ColorScheme): ColorScheme {
    val dark = base.background.luminance() < .5f
    return when (style) {
        PlayerVisualStyle.ORIGINAL -> base
        PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS -> base.copy(background = if (dark) Color.Black else Color(0xFFFFF9F0), surface = if (dark) Color.Black else Color(0xFFFFF9F0))
        PlayerVisualStyle.LIQUID_GLASS_PLUS -> if (dark) darkColorScheme(background = Color.Black, surface = Color(0xFF101D2C), onSurface = Color(0xFFF0F5FF), primary = Color(0xFF94DAF3), surfaceContainer = Color(0xFF132437), surfaceContainerHigh = Color(0xFF1A3045))
            else lightColorScheme(background = Color(0xFFF2F8FC), surface = Color(0xFFF8FCFF), onSurface = Color(0xFF102638), primary = Color(0xFF146581), surfaceContainer = Color(0xFFE9F3F9), surfaceContainerHigh = Color(0xFFDDECF5))
    }
}
