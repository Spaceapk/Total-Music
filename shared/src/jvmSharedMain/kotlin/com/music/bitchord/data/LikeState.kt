package com.music.bitchord.data

import com.music.bitchord.data.model.LikeStatus
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
object LikeState {
    private val _overrides = MutableStateFlow<Map<String, LikeStatus>>(emptyMap())
    val overrides: StateFlow<Map<String, LikeStatus>> = _overrides.asStateFlow()

    // Explicit device choices survive account changes; cloud seeds never enter this map.
    private var deviceRatings: Map<String, LikeStatus> = emptyMap()
    private var persist: ((Map<String, LikeStatus>) -> Unit)? = null

    @Synchronized
    fun attachPersistence(initial: Map<String, LikeStatus>, writer: ((Map<String, LikeStatus>) -> Unit)?) {
        deviceRatings = initial.toMap()
        persist = writer
        _overrides.value = _overrides.value + deviceRatings
    }

    @Synchronized
    fun set(videoId: String, status: LikeStatus) {
        if (videoId.isBlank()) return
        val next = deviceRatings + (videoId to status)
        persist?.invoke(next)
        if (persist != null) deviceRatings = next
        _overrides.value = _overrides.value + (videoId to status)
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
