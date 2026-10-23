package com.music.bitchord.data.model

/** Read-only ordering; missing/invalid tags follow numbered tracks. */
fun parseTrackTag(value: String?): Int? = value?.substringBefore('/')?.trim()?.toIntOrNull()?.takeIf { it > 0 }

fun List<Song>.inAlbumOrder(): List<Song> = sortedWith(
    compareBy<Song> { it.localTrackNumber?.takeIf { n -> n > 0 } == null }
        .thenBy { it.localDiscNumber?.takeIf { n -> n > 0 } ?: 1 }
        .thenBy { it.localTrackNumber?.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }
)
