package com.music.bitchord.data

import android.content.Context
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import org.json.JSONArray
import org.json.JSONObject

/** Device-owned ratings and favorite metadata, independent of sign-in. */
object DeviceFavoriteStore {
    private const val SNAPSHOT_KEY = "favorites_snapshot_v1"

    fun initialize(context: Context, filename: String = "total-music-favorites") {
        val prefs = context.getSharedPreferences(filename, Context.MODE_PRIVATE)
        val legacy = prefs.all.mapNotNull { (id, value) ->
            if (id == SNAPSHOT_KEY) null else (value as? String)
                ?.let { runCatching { LikeStatus.valueOf(it) }.getOrNull() }?.let { id to it }
        }.toMap()
        val snapshot = runCatching {
            prefs.getString(SNAPSHOT_KEY, null)?.let { JSONObject(it) }
        }.getOrNull()
        val ratings = snapshot?.optJSONObject("ratings")?.let { json ->
            json.keys().asSequence().mapNotNull { id ->
                runCatching { LikeStatus.valueOf(json.getString(id)) }.getOrNull()?.let { id to it }
            }.toMap()
        } ?: legacy
        val entries = snapshot?.optJSONArray("entries")?.let { array ->
            (0 until array.length()).mapNotNull { i ->
                runCatching {
                    val e = array.getJSONObject(i)
                    val s = e.getJSONObject("song")
                    val song = Song(
                        videoId = s.getString("id"), title = s.getString("title"),
                        artist = s.getString("artist"), thumbnailUrl = s.optString("art").takeIf { it.isNotBlank() },
                        durationText = s.optString("duration").takeIf { it.isNotBlank() },
                        albumName = s.optString("album").takeIf { it.isNotBlank() },
                        localUri = s.optString("uri").takeIf { it.isNotBlank() },
                        localPath = s.optString("path").takeIf { it.isNotBlank() },
                        downloadFormat = s.optString("format").takeIf { it.isNotBlank() },
                    )
                    song.videoId to FavoriteEntry(song, e.getLong("addedAt"))
                }.getOrNull()
            }.toMap()
        }.orEmpty()
        LikeState.attachStorePersistence(ratings, entries, writer = { nextRatings, nextEntries ->
            val json = JSONObject().put("version", 1)
                .put("ratings", JSONObject().apply { nextRatings.forEach { (id, status) -> put(id, status.name) } })
                .put("entries", JSONArray().apply {
                    nextEntries.forEach { (id, entry) ->
                        val s = entry.song
                        put(JSONObject().put("addedAt", entry.addedAtMillis).put("song", JSONObject()
                            .put("id", id).put("title", s.title).put("artist", s.artist)
                            .put("art", s.thumbnailUrl).put("duration", s.durationText)
                            .put("album", s.albumName).put("uri", s.localUri)
                            .put("path", s.localPath).put("format", s.downloadFormat)))
                    }
                })
            // SharedPreferences writes one editor atomically. Legacy rating keys remain readable by 1.8.2.
            prefs.edit().apply {
                clear()
                nextRatings.forEach { (id, status) -> putString(id, status.name) }
                putString(SNAPSHOT_KEY, json.toString())
            }.apply()
        })
        LikeState.rememberSongs(entries.values.map { it.song })
    }
}
