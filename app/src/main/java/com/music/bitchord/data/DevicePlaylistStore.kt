package com.music.bitchord.data

import android.content.Context
import android.util.AtomicFile
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class PlaylistSort { MANUAL, TITLE_ASC, TITLE_DESC, ARTIST, RECENTLY_ADDED }

data class DevicePlaylist(
    val id: String,
    val name: String,
    val songs: List<Song>,
    val sort: PlaylistSort = PlaylistSort.MANUAL,
    // Membership insertion order is independent of the user's manual running order.
    val addedUris: List<String> = songs.mapNotNull { it.localUri },
) {
    fun sortedSongs(): List<Song> {
        val collator = java.text.Collator.getInstance().apply { strength = java.text.Collator.PRIMARY }
        val byTitle = Comparator<Song> { a, b -> collator.compare(a.title, b.title) }
        return when (sort) {
            PlaylistSort.MANUAL -> songs
            PlaylistSort.TITLE_ASC -> songs.sortedWith(byTitle)
            PlaylistSort.TITLE_DESC -> songs.sortedWith(byTitle.reversed())
            PlaylistSort.ARTIST -> songs.sortedWith(Comparator<Song> { a, b ->
                collator.compare(a.artist, b.artist).takeIf { it != 0 } ?: byTitle.compare(a, b)
            })
            PlaylistSort.RECENTLY_ADDED -> {
                val positions = addedUris.withIndex().associate { it.value to it.index }
                songs.sortedByDescending { positions[it.localUri] ?: -1 }
            }
        }
    }
}

/** Star Music device-only playlists. Atomic writes preserve the previous file on failure. */
class DevicePlaylistStore(context: Context, filename: String = "star-device-playlists.json") {
    companion object {
        private var deviceStore: DevicePlaylistStore? = null
        /** One writer and one observable state across local-tab navigation. */
        fun forDevice(context: Context): DevicePlaylistStore = synchronized(this) {
            deviceStore ?: DevicePlaylistStore(context.applicationContext).also { deviceStore = it }
        }
    }
    private val file = AtomicFile(File(context.filesDir, filename))
    private val state = MutableStateFlow(read())
    val playlists = state.asStateFlow()

    private fun read(): List<DevicePlaylist> {
        // openRead restores AtomicFile's backup first. Checking only the base
        // file before this would discard playlists after an interrupted write.
        val input = try { file.openRead() } catch (e: java.io.FileNotFoundException) {
            if (!file.baseFile.exists()) return emptyList() else throw e
        }
        val array = JSONArray(input.use { it.readBytes().toString(Charsets.UTF_8) })
        return (0 until array.length()).map { i ->
            val p = array.getJSONObject(i)
            val tracks = p.getJSONArray("songs")
            DevicePlaylist(p.getString("id"), p.getString("name"), (0 until tracks.length()).map { j ->
                val s = tracks.getJSONObject(j)
                fun optional(key: String) = if (s.isNull(key)) null else s.optString(key).takeIf { it.isNotBlank() }
                Song(videoId = s.getString("id"), title = s.getString("title"), artist = s.getString("artist"),
                    thumbnailUrl = optional("art"), durationText = optional("duration"), albumName = optional("album"),
                    localUri = s.getString("uri"), localPath = optional("path"), downloadFormat = optional("format"),
                    localTrackNumber = s.optInt("track").takeIf { it > 0 },
                    localDiscNumber = s.optInt("disc").takeIf { it > 0 })
            }, sort = runCatching { PlaylistSort.valueOf(p.optString("sort")) }.getOrDefault(PlaylistSort.MANUAL),
                addedUris = p.optJSONArray("addedUris")?.let { uris ->
                    (0 until uris.length()).map { uris.getString(it) }
                } ?: (0 until tracks.length()).map { tracks.getJSONObject(it).getString("uri") })
        }
    }

    @Synchronized
    private fun update(transform: (List<DevicePlaylist>) -> List<DevicePlaylist>) {
        val next = transform(state.value)
        val array = JSONArray()
        next.forEach { p ->
            val tracks = JSONArray()
            p.songs.forEach { s ->
                tracks.put(JSONObject().put("id", s.videoId).put("title", s.title).put("artist", s.artist)
                    .put("art", s.thumbnailUrl).put("duration", s.durationText).put("album", s.albumName)
                    .put("uri", s.localUri).put("path", s.localPath).put("format", s.downloadFormat)
                    .put("track", s.localTrackNumber).put("disc", s.localDiscNumber))
            }
            array.put(JSONObject().put("id", p.id).put("name", p.name).put("songs", tracks)
                .put("sort", p.sort.name).put("addedUris", JSONArray(p.addedUris)))
        }
        val output = file.startWrite()
        try {
            output.write(array.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
        state.value = next
    }

    fun create(name: String) {
        require(name.trim().isNotEmpty())
        update { it + DevicePlaylist(UUID.randomUUID().toString(), name.trim(), emptyList()) }
    }
    fun createWithSongs(name: String, songs: List<Song>) {
        require(name.trim().isNotEmpty())
        require(songs.all { it.localUri?.let { uri -> uri.startsWith("content://") || uri.startsWith("file://") } == true })
        update { it + DevicePlaylist(UUID.randomUUID().toString(), name.trim(), songs.distinctBy { song -> song.localUri }) }
    }
    fun rename(id: String, name: String) {
        require(name.trim().isNotEmpty())
        update { list -> list.map { if (it.id == id) it.copy(name = name.trim()) else it } }
    }
    fun delete(id: String) = update { list -> list.filterNot { it.id == id } }
    fun add(id: String, songs: List<Song>) {
        require(songs.all { it.localUri?.let { uri -> uri.startsWith("content://") || uri.startsWith("file://") } == true })
        update { list -> list.map { if (it.id == id) it.copy(songs = (it.songs + songs).distinctBy { song -> song.localUri },
            addedUris = (it.addedUris + songs.mapNotNull { song -> song.localUri }).distinct()) else it } }
    }
    fun remove(id: String, index: Int) = update { list -> list.map { p ->
        if (p.id == id) p.copy(songs = p.songs.filterIndexed { i, _ -> i != index },
            addedUris = p.addedUris.filterNot { it == p.songs.getOrNull(index)?.localUri }) else p
    } }
    fun setSort(id: String, sort: PlaylistSort) = update { list ->
        list.map { if (it.id == id) it.copy(sort = sort) else it }
    }
    /** Resolve identity at write time; sorted/filtered row indexes are not storage indexes. */
    fun removeSong(id: String, uri: String) = update { list -> list.map { p ->
        if (p.id == id) p.copy(songs = p.songs.filterNot { it.localUri == uri },
            addedUris = p.addedUris.filterNot { it == uri }) else p
    } }
    /** Apply a permutation only; reject stale edits rather than losing newer tracks. */
    fun reorder(id: String, uris: List<String>) = update { list -> list.map { p ->
        if (p.id == id) {
            val existing = p.songs.associateBy { it.localUri }
            require(uris.size == p.songs.size && uris.toSet().size == uris.size &&
                uris.toSet() == existing.keys) { "Playlist changed; reopen the reorder view" }
            p.copy(songs = uris.map { existing.getValue(it) }, sort = PlaylistSort.MANUAL)
        } else p
    } }
    fun move(id: String, from: Int, to: Int) = update { list -> list.map { p ->
        if (p.id == id && from in p.songs.indices && to in p.songs.indices) {
            val tracks = p.songs.toMutableList()
            tracks.add(to, tracks.removeAt(from))
            p.copy(songs = tracks)
        } else p
    } }
}
