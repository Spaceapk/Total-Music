enum class PlaylistSort {
    MANUAL,
    TITLE_ASC,
    TITLE_DESC,
    ARTIST_ASC,
    ARTIST_DESC,
    ALBUM_ASC,
    ALBUM_DESC,
    TRACK_ASC,
    TRACK_DESC,
    RECENTLY_ADDED,
    ;

    companion object {
        /**
         * Read a persisted order, including the `ARTIST` name written before
         * 1.8.4. `ARTIST` meant artist ascending, so reopening an older
         * playlist keeps the order its owner chose instead of resetting it.
         */
        fun fromPersisted(value: String?): PlaylistSort = when (value) {
            "ARTIST" -> ARTIST_ASC
            else -> PlaylistSort.entries.firstOrNull { it.name == value } ?: MANUAL
        }
    }
}

/** A usable track number; zero and negative values are missing tags, not track 0. */
private fun Song.numberedTrack(): Int? = localTrackNumber?.takeIf { it > 0 }

/** Same rule as `AlbumOrdering`: a missing disc number means disc one. */
private fun Song.numberedDisc(): Int = localDiscNumber?.takeIf { it > 0 } ?: 1

data class DevicePlaylist(
    val id: String,
    val name: String,
    val songs: List<Song>,
    val sort: PlaylistSort = PlaylistSort.MANUAL,
    // Membership insertion order is independent of the user's manual running order.
    val addedUris: List<String> = songs.mapNotNull { it.localUri },
) {
    /**
     * The playlist in the order its own picker asks for.
     *
     * Album order is groups of *(album, artist)*: two records that happen to
     * share a name are two records, so one artist's never interleaves with the
     * other's. Inside a group, discs and track numbers are compared as numbers
     * (so 2 comes before 10), and a track with no number goes last — the same
     * rule as `AlbumOrdering`, so the library and a playlist cannot disagree.
     *
     * Artist order is artist, then album, then disc and track, which is what
     * "by artist" has to mean to stay readable: grouping by the artist but
     * sorting his records alphabetically by song title is not an artist order.
     *
     * Descending reverses the group key only. A record's tracks never run
     * backwards just because its artist is listed Z–A.
     */
    fun sortedSongs(): List<Song> {
        if (sort == PlaylistSort.MANUAL) return songs
        val collator = java.text.Collator.getInstance().apply { strength = java.text.Collator.PRIMARY }
        val byTitle = Comparator<Song> { a, b -> collator.compare(a.title, b.title) }
        val byArtist = Comparator<Song> { a, b -> collator.compare(a.artist, b.artist) }
        val byAlbum = Comparator<Song> { a, b ->
            collator.compare(a.albumName.orEmpty(), b.albumName.orEmpty())
        }
        val untaggedLast = compareBy<Song> { it.numberedTrack() == null }
        val discAndTrack = untaggedLast
            .thenBy { it.numberedDisc() }
            .thenBy { it.numberedTrack() ?: Int.MAX_VALUE }
            .thenComparing(byTitle)
        // Untagged tracks stay last in both directions: reversing a missing
        // number would otherwise promote them to the top of the list.
        val discAndTrackDescending = untaggedLast
            .thenByDescending { it.numberedDisc() }
            .thenByDescending { it.numberedTrack() ?: -1 }
            .thenComparing(byTitle)
        // The album group is the name *and* its artist, so homonymous albums
        // remain separate groups. Descending reverses the name only; the
        // artist is still ascending inside it.
        val albumGroup = byAlbum.thenComparing(byArtist)
        val albumGroupDescending = byAlbum.reversed().thenComparing(byArtist)
        return when (sort) {
            PlaylistSort.MANUAL -> songs
            PlaylistSort.TITLE_ASC -> songs.sortedWith(byTitle)
            PlaylistSort.TITLE_DESC -> songs.sortedWith(byTitle.reversed())
            PlaylistSort.ARTIST_ASC -> songs.sortedWith(
                byArtist.thenComparing(byAlbum).thenComparing(discAndTrack),
            )
            PlaylistSort.ARTIST_DESC -> songs.sortedWith(
                byArtist.reversed().thenComparing(byAlbum).thenComparing(discAndTrack),
            )
            PlaylistSort.ALBUM_ASC -> songs.sortedWith(albumGroup.thenComparing(discAndTrack))
            PlaylistSort.ALBUM_DESC -> songs.sortedWith(albumGroupDescending.thenComparing(discAndTrack))
            PlaylistSort.TRACK_ASC -> songs.sortedWith(discAndTrack)
            PlaylistSort.TRACK_DESC -> songs.sortedWith(discAndTrackDescending)
            PlaylistSort.RECENTLY_ADDED -> {
                val positions = addedUris.withIndex().associate { it.value to it.index }
                songs.sortedWith(
                    compareByDescending<Song> { positions[it.localUri] ?: -1 }.thenComparing(byTitle),
                )
            }
        }
    }
}

/**
 * [uris] with the entry at [from] moved to [to], or null when the request is
 * stale or impossible.
 *
 * Kept as a plain list edit so the drag, the store and its tests all agree on
 * what "one move" means, without a Compose layout or an Android context.
 */
internal fun movedPlaylistOrder(uris: List<String>, from: Int, to: Int): List<String>? {
    if (from !in uris.indices || to !in uris.indices || from == to) return null
    // A playlist cannot hold the same local file twice; if one somehow does,
    // an index-based move would be ambiguous and is refused rather than
    // dropping or duplicating a song.
    if (uris.toSet().size != uris.size) return null
    return uris.toMutableList().apply { add(to, removeAt(from)) }
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
            }, sort = PlaylistSort.fromPersisted(p.optString("sort")),
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
    /**
     * Moves the row at [from] to [to] in the order currently on screen and
     * stores that visible order as the manual one.
     *
     * Dragging is also how a playlist returns to manual order: when another
     * criterion is active the rows are shown sorted, and the drop persists
     * exactly what the reader arranged instead of discarding the gesture. The
     * indexes are row positions in [DevicePlaylist.sortedSongs], not storage
     * positions — the two differ whenever the playlist is not in manual order.
     */
    fun move(id: String, from: Int, to: Int) = update { list -> list.map { p ->
        if (p.id != id) return@map p
        val visible = p.sortedSongs().mapNotNull { it.localUri }
        val moved = if (visible.size == p.songs.size) {
            movedPlaylistOrder(visible, from, to)
        } else {
            null
        }
        val byUri = p.songs.associateBy { it.localUri }
        if (moved == null || byUri.size != p.songs.size || moved.any { it !in byUri }) {
            p
        } else {
            p.copy(songs = moved.map(byUri::getValue), sort = PlaylistSort.MANUAL)
        }
    } }
}
