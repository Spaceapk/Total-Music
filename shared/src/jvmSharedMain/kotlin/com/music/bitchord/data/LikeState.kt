package com.music.bitchord.data

import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Device ratings and cloud seeds, shared by the UI and playback service.
 *
 * The library remains the source for ratings that were already known at load time;
 * these overrides win over it so a notification tap and a player-screen tap paint
 * the same result immediately.
 */
/** Metadata kept beside the rating so favorites survive without a library rescan. */
data class FavoriteEntry(val song: Song, val addedAtMillis: Long)

object LikeState {
    private val _overrides = MutableStateFlow<Map<String, LikeStatus>>(emptyMap())
    val overrides: StateFlow<Map<String, LikeStatus>> = _overrides.asStateFlow()

    private val _entries = MutableStateFlow<Map<String, FavoriteEntry>>(emptyMap())
    val entries: StateFlow<Map<String, FavoriteEntry>> = _entries.asStateFlow()

    // Explicit device choices survive account changes; cloud seeds never enter this map.
    private var deviceRatings: Map<String, LikeStatus> = emptyMap()
    private var persist: ((Map<String, LikeStatus>) -> Unit)? = null
    private var persistStore: ((Map<String, LikeStatus>, Map<String, FavoriteEntry>) -> Unit)? = null
    private var clock: () -> Long = { System.currentTimeMillis() }

    @Synchronized
    fun attachPersistence(initial: Map<String, LikeStatus>, writer: ((Map<String, LikeStatus>) -> Unit)?) {
        deviceRatings = initial.toMap()
        persist = writer
        persistStore = null
        _entries.value = emptyMap()
        _overrides.value = _overrides.value + deviceRatings
    }

    /** One writer for the rating and its catalog entry: no per-theme favorite databases. */
    @Synchronized
    fun attachStorePersistence(
        initialRatings: Map<String, LikeStatus>,
        initialEntries: Map<String, FavoriteEntry>,
        writer: ((Map<String, LikeStatus>, Map<String, FavoriteEntry>) -> Unit)?,
        timeSource: () -> Long = { System.currentTimeMillis() },
    ) {
        deviceRatings = initialRatings.toMap()
        persist = null
        persistStore = writer
        clock = timeSource
        _entries.value = initialEntries.filterKeys { deviceRatings[it] == LikeStatus.LIKE }
        _overrides.value = _overrides.value + deviceRatings
    }

    @Synchronized
    fun set(videoId: String, status: LikeStatus, song: Song? = null) {
        if (videoId.isBlank()) return
        val next = deviceRatings + (videoId to status)
        val known = song ?: _entries.value[videoId]?.song
        val nextEntries = when {
            status != LikeStatus.LIKE -> _entries.value - videoId
            known != null -> _entries.value + (videoId to
                (_entries.value[videoId]?.copy(song = known) ?: FavoriteEntry(known, clock())))
            else -> _entries.value
        }
        persistStore?.invoke(next, nextEntries) ?: persist?.invoke(next)
        if (persistStore != null || persist != null) {
            deviceRatings = next
            _entries.value = nextEntries
        }
        _overrides.value = _overrides.value + (videoId to status)
    }

    /** Hydrates hearts restored from an older APK or seeded by the account library. */
    @Synchronized
    fun rememberSongs(songs: List<Song>) {
        if (songs.isEmpty()) return
        var next = _entries.value
        songs.forEach { song ->
            if (_overrides.value[song.videoId] == LikeStatus.LIKE && song.videoId !in next) {
                next = next + (song.videoId to FavoriteEntry(song, 0L))
            }
        }
        if (next !== _entries.value) {
            persistStore?.invoke(deviceRatings, next)
            _entries.value = next
        }
    }

    /**
     * Records a rating read off a track's own menu, but only when the menu
     * actually states one. A missing like button or an absent rating (null)
     * must never overwrite what this session already knows — kept null
     * rather than INDIFFERENT so the two stay distinct — and an explicit
     * override already made this session always wins.
     */
    @Synchronized
    fun rememberStated(videoId: String, stated: LikeStatus?) {
        if (stated != null && stated != LikeStatus.INDIFFERENT && videoId !in _overrides.value) {
            _overrides.value = _overrides.value + (videoId to stated)
        }
    }

    /** Seeds only ratings not already changed explicitly during this session. */
    @Synchronized
    fun seedLiked(videoIds: Set<String>) {
        if (videoIds.isEmpty()) return
        val next = _overrides.value.toMutableMap()
        videoIds.forEach { next.putIfAbsent(it, LikeStatus.LIKE) }
        if (next != _overrides.value) _overrides.value = next
    }

    @Synchronized
    fun clear() {
        _overrides.value = deviceRatings
    }
}
