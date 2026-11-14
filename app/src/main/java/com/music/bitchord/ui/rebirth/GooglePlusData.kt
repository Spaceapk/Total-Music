package com.music.bitchord.ui.rebirth

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.stats.StoredBucket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

/**
 * Google Plus 2 data. Everything here is derived from what the app already
 * owns — the device library, MediaStore tags and the listening history that
 * Replay records. Nothing is a recommendation engine and nothing is sample data:
 * an empty source produces an empty section, which the screen then leaves out.
 */

/** This month's listening, read from the same buckets Replay uses. */
internal data class PlusStats(
    val minutes: Long,
    val plays: Int,
    val topArtist: String?,
    val distinctSongs: Int,
)

internal data class PlusHome(
    /** Today's selection from the library; the same all day, different tomorrow. */
    val dailyMix: List<Song>,
    /** Last heard first when there is history, otherwise last added first. */
    val recent: List<Song>,
    /** False when [recent] had to fall back to "recently added". */
    val recentFromHistory: Boolean,
    val stats: PlusStats?,
)

/** The tabs of the Google Plus library, in the order the reader can rearrange. */
internal enum class PlusLibraryTab { SONGS, ALBUMS, ARTISTS, PLAYLISTS, FOLDERS, FAVORITES }

internal const val PLUS_DAILY_MIX_SIZE = 25
internal const val PLUS_RECENT_SIZE = 12

/**
 * A daily mix: a seeded shuffle of the real library. Sorting by id first makes
 * the result independent of the scanner's order, so it only changes with the
 * date or with the library itself.
 */
internal fun plusDailyMix(songs: List<Song>, epochDay: Long, size: Int = PLUS_DAILY_MIX_SIZE): List<Song> =
    songs.distinctBy { it.videoId }
        .sortedBy { it.videoId }
        .shuffled(java.util.Random(epochDay * 1_000_003L + 17L))
        .take(size)

/** Library songs by when they were last heard, newest first. Unknown ids are skipped. */
internal fun plusRecentlyPlayed(buckets: List<StoredBucket>, library: List<Song>, limit: Int = PLUS_RECENT_SIZE): List<Song> {
    if (buckets.isEmpty() || library.isEmpty()) return emptyList()
    val lastHeard = HashMap<String, Long>()
    buckets.forEach { bucket ->
        bucket.tracks.forEach { track ->
            if (track.last > 0L) lastHeard[track.id] = maxOf(lastHeard[track.id] ?: 0L, track.last)
        }
    }
    if (lastHeard.isEmpty()) return emptyList()
    val byId = HashMap<String, Song>()
    library.forEach { song ->
        byId.putIfAbsent(song.videoId, song)
        song.localUri?.let { byId.putIfAbsent(it, song) }
    }
    return lastHeard.entries
        .sortedByDescending { it.value }
        .mapNotNull { byId[it.key] }
        .distinctBy { it.videoId }
        .take(limit)
}

internal fun plusRecentlyAdded(library: List<Song>, limit: Int = PLUS_RECENT_SIZE): List<Song> =
    library.filter { (it.localDateAddedSeconds ?: 0L) > 0L }
        .sortedByDescending { it.localDateAddedSeconds ?: 0L }
        .take(limit)

/** Null when nothing was heard in [month]; the screen then shows library counts only. */
internal fun plusStats(buckets: List<StoredBucket>, month: YearMonth): PlusStats? {
    val bucket = buckets.filter { it.month == month.toString() }
    val tracks = bucket.flatMap { it.tracks }
    val totalMs = tracks.sumOf { it.ms }
    val plays = tracks.sumOf { it.plays }
    if (totalMs <= 0L && plays <= 0) return null
    val topArtist = bucket.flatMap { it.artists }
        .groupBy { it.name }
        .mapValues { entry -> entry.value.sumOf { it.ms } }
        .filterKeys { it.isNotBlank() }
        .maxByOrNull { it.value }?.key
    return PlusStats(
        minutes = totalMs / 60_000L,
        plays = plays,
        topArtist = topArtist,
        distinctSongs = tracks.map { it.id }.distinct().size,
    )
}

internal fun plusHome(songs: List<Song>, buckets: List<StoredBucket>, today: LocalDate): PlusHome {
    val played = plusRecentlyPlayed(buckets, songs)
    return PlusHome(
        dailyMix = plusDailyMix(songs, today.toEpochDay()),
        recent = played.ifEmpty { plusRecentlyAdded(songs) },
        recentFromHistory = played.isNotEmpty(),
        stats = plusStats(buckets, YearMonth.from(today)),
    )
}

/** `m:ss` or `h:mm:ss` as the library writes it; 0 for anything else. */
internal fun plusDurationSeconds(text: String?): Long {
    val parts = text?.trim()?.split(':')?.map { it.toLongOrNull() ?: return 0L } ?: return 0L
    return when (parts.size) {
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> 0L
    }
}

/**
 * One collection per genre tag. A tag listing several genres with `;` files
 * the song under each; a song with no tag is in none. Ids are `genre:<name>`.
 */
internal fun plusGenreCollections(songs: List<Song>, genreByUri: Map<String, String>): List<RebirthCollection> {
    if (genreByUri.isEmpty()) return emptyList()
    val groups = LinkedHashMap<String, Pair<String, MutableList<Song>>>()
    songs.forEach { song ->
        val raw = genreByUri[song.localUri ?: song.videoId] ?: return@forEach
        raw.split(';').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.forEach { name ->
            groups.getOrPut(name.lowercase()) { name to mutableListOf() }.second.add(song)
        }
    }
    return groups.values
        .map { (name, tracks) -> RebirthCollection("genre:$name", name, tracks) }
        .sortedWith(compareByDescending<RebirthCollection> { it.songs.size }.thenBy { it.title.lowercase() })
}

/** One collection per directory that holds music. Ids are `folder:<path>`. */
internal fun plusFolderCollections(songs: List<Song>): List<RebirthCollection> =
    songs.mapNotNull { song ->
        val path = song.localPath?.replace('\\', '/')?.substringBeforeLast('/', "")?.takeIf { it.isNotBlank() }
        path?.let { it to song }
    }
        .groupBy({ it.first }, { it.second })
        .map { (path, tracks) -> RebirthCollection("folder:$path", path.substringAfterLast('/').ifBlank { path }, tracks) }
        .sortedBy { it.title.lowercase() }

/** The directory a folder collection stands for, for its caption. */
internal fun RebirthCollection.plusFolderPath(): String? = id.removePrefix("folder:").takeIf { id.startsWith("folder:") }

/**
 * The stored tab order, repaired: unknown names are dropped, duplicates
 * collapse and any tab missing from the stored value is appended in its
 * default place, so an older or damaged preference can never hide a tab.
 */
internal fun plusTabOrder(stored: String?): List<PlusLibraryTab> {
    val parsed = stored.orEmpty().split(',')
        .mapNotNull { name -> PlusLibraryTab.entries.firstOrNull { it.name == name.trim() } }
        .distinct()
    return parsed + PlusLibraryTab.entries.filterNot { it in parsed }
}

internal fun plusMoveTab(order: List<PlusLibraryTab>, tab: PlusLibraryTab, delta: Int): List<PlusLibraryTab> {
    val from = order.indexOf(tab)
    val to = from + delta
    if (from < 0 || to !in order.indices) return order
    return order.toMutableList().apply { add(to, removeAt(from)) }
}

/** Presentation-only preferences of Google Plus. Its own file: no existing preference is touched. */
internal object GooglePlusPrefs {
    private const val FILE = "google_plus_ui"
    private const val KEY_TAB_ORDER = "library_tab_order"

    fun tabOrder(context: Context): List<PlusLibraryTab> =
        plusTabOrder(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_TAB_ORDER, null))

    fun setTabOrder(context: Context, order: List<PlusLibraryTab>) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_TAB_ORDER, order.joinToString(",") { it.name }).apply()
    }
}

/** Genre tags as MediaStore indexed them, keyed by the same content URI a local [Song] carries. */
internal object LocalGenreIndex {
    suspend fun load(context: Context): Map<String, String> = withContext(Dispatchers.IO) {
        val result = HashMap<String, String>()
        runCatching {
            val resolver = context.contentResolver
            val audio = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                resolver.query(audio, arrayOf(MediaStore.Audio.Media._ID, "genre"), null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val genre = cursor.getString(1)?.trim()
                        if (!genre.isNullOrEmpty()) {
                            result[ContentUris.withAppendedId(audio, cursor.getLong(0)).toString()] = genre
                        }
                    }
                }
            } else {
                val genres = ArrayList<Pair<Long, String>>()
                resolver.query(
                    MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME), null, null, null,
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(1)?.trim()
                        if (!name.isNullOrEmpty()) genres += cursor.getLong(0) to name
                    }
                }
                genres.forEach { (id, name) ->
                    resolver.query(
                        MediaStore.Audio.Genres.Members.getContentUri("external", id),
                        arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID), null, null, null,
                    )?.use { cursor ->
                        while (cursor.moveToNext()) {
                            val key = ContentUris.withAppendedId(audio, cursor.getLong(0)).toString()
                            result[key] = result[key]?.let { "$it;$name" } ?: name
                        }
                    }
                }
            }
        }
        result
    }
}
