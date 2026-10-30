package com.music.bitchord.ui.player

/** Selects a player presentation without changing the app-wide appearance. */
enum class PlayerVisualStyle {
    ORIGINAL,
    GOOGLE_EXPRESSIVE_PLUS,
    LIQUID_GLASS_PLUS;

    companion object {
        /** Unknown values from a future or damaged preference keep the legacy player. */
        fun fromStorage(value: String?): PlayerVisualStyle =
            entries.firstOrNull { it.name == value } ?: ORIGINAL
    }
}
