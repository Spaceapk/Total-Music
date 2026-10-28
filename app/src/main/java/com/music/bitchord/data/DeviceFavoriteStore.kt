package com.music.bitchord.data

import android.content.Context
import com.music.bitchord.data.model.LikeStatus

/** Device-owned ratings, independent of sign-in and available before playback starts. */
object DeviceFavoriteStore {
    fun initialize(context: Context, filename: String = "total-music-favorites") {
        val prefs = context.getSharedPreferences(filename, Context.MODE_PRIVATE)
        val restored = prefs.all.mapNotNull { (id, value) ->
            (value as? String)?.let { runCatching { LikeStatus.valueOf(it) }.getOrNull() }
                ?.let { id to it }
        }.toMap()
        LikeState.attachPersistence(restored) { ratings ->
            prefs.edit().apply {
                clear()
                ratings.forEach { (id, status) -> putString(id, status.name) }
            }.apply()
        }
    }
}
