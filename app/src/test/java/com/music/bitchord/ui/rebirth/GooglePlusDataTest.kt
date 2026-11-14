package com.music.bitchord.ui.rebirth

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.stats.NameEntry
import com.music.bitchord.data.stats.StoredBucket
import com.music.bitchord.data.stats.TrackEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class GooglePlusDataTest {
    private fun song(n: Int, path: String? = "/music/a/$n.flac", added: Long = n.toLong()) = Song(
        videoId = "content://media/$n", title = "Song $n", artist = "Artist ${n % 2}", thumbnailUrl = null,
        durationText = "3:0$n", localUri = "content://media/$n", localPath = path, localDateAddedSeconds = added,
    )
    private val library = (1..8).map { song(it) }

    @Test fun dailyMixIsTheLibraryStableForADayAndIndependentOfScanOrder() {
        val today = plusDailyMix(library, 20_000)
        assertEquals(library.map { it.videoId }.toSet(), today.map { it.videoId }.toSet())
        assertEquals(today, plusDailyMix(library.reversed(), 20_000))
        assertEquals(today, plusDailyMix(library + library, 20_000))
        assertTrue((1L..6L).any { plusDailyMix(library, 20_000 + it) != today })
        assertEquals(3, plusDailyMix(library, 20_000, size = 3).size)
        assertTrue(plusDailyMix(emptyList(), 20_000).isEmpty())
    }

    @Test fun recentComesFromHistoryNewestFirstAndSkipsUnknownSongs() {
        val buckets = listOf(
            StoredBucket(month = "2026-10", tracks = listOf(TrackEntry("content://media/2", last = 100), TrackEntry("gone", last = 900))),
            StoredBucket(month = "2026-11", tracks = listOf(TrackEntry("content://media/5", last = 300), TrackEntry("content://media/2", last = 500), TrackEntry("content://media/7", last = 0))),
        )
        assertEquals(listOf("content://media/2", "content://media/5"), plusRecentlyPlayed(buckets, library).map { it.videoId })
        assertTrue(plusRecentlyPlayed(emptyList(), library).isEmpty())
        val home = plusHome(library, buckets, LocalDate.of(2026, 11, 14))
        assertTrue(home.recentFromHistory)
        assertEquals("content://media/2", home.recent.first().videoId)
    }

    @Test fun withoutHistoryRecentFallsBackToRecentlyAddedAndSaysSo() {
        val home = plusHome(library, emptyList(), LocalDate.of(2026, 11, 14))
        assertFalse(home.recentFromHistory)
        assertEquals("content://media/8", home.recent.first().videoId)
        assertNull(home.stats)
    }

    @Test fun statsReadOnlyTheRequestedMonth() {
        val buckets = listOf(
            StoredBucket(month = "2026-10", tracks = listOf(TrackEntry("a", ms = 6_000_000, plays = 50))),
            StoredBucket(
                month = "2026-11",
                tracks = listOf(TrackEntry("a", ms = 120_000, plays = 2), TrackEntry("b", ms = 185_000, plays = 1)),
                artists = listOf(NameEntry("Luna", ms = 100_000), NameEntry("Sol", ms = 205_000)),
            ),
        )
        val stats = plusStats(buckets, YearMonth.of(2026, 11))!!
        assertEquals(5L, stats.minutes)
        assertEquals(3, stats.plays)
        assertEquals("Sol", stats.topArtist)
        assertEquals(2, stats.distinctSongs)
        assertNull(plusStats(buckets, YearMonth.of(2026, 12)))
    }

    @Test fun genresGroupRealTagsAndUntaggedSongsAreInNone() {
        val tags = mapOf("content://media/1" to "Rock", "content://media/2" to "rock; Jazz", "content://media/3" to " Jazz ")
        val genres = plusGenreCollections(library, tags)
        assertEquals(listOf("genre:Jazz", "genre:Rock"), genres.map { it.id }.sorted())
        assertEquals(listOf("content://media/1", "content://media/2"), genres.first { it.title == "Rock" }.songs.map { it.videoId })
        assertEquals(listOf("content://media/2", "content://media/3"), genres.first { it.title == "Jazz" }.songs.map { it.videoId })
        assertTrue(plusGenreCollections(library, emptyMap()).isEmpty())
    }

    @Test fun foldersGroupByDirectoryAndIgnoreSongsWithoutAPath() {
        val songs = listOf(song(1, "/sd/Music/Rock/a.flac"), song(2, "/sd/Music/Rock/b.flac"), song(3, "/sd/Music/Jazz/c.mp3"), song(4, null))
        val folders = plusFolderCollections(songs)
        assertEquals(listOf("Jazz", "Rock"), folders.map { it.title })
        assertEquals("/sd/Music/Rock", folders[1].plusFolderPath())
        assertEquals(2, folders[1].songs.size)
    }

    @Test fun tabOrderRepairsMissingUnknownAndDuplicateNames() {
        assertEquals(PlusLibraryTab.entries.toList(), plusTabOrder(null))
        assertEquals(PlusLibraryTab.entries.toList(), plusTabOrder("garbage,,"))
        val stored = plusTabOrder("FOLDERS,SONGS,FOLDERS,NOPE")
        assertEquals(PlusLibraryTab.FOLDERS, stored[0])
        assertEquals(PlusLibraryTab.SONGS, stored[1])
        assertEquals(PlusLibraryTab.entries.toSet(), stored.toSet())
        assertEquals(PlusLibraryTab.entries.size, stored.size)
        val moved = plusMoveTab(PlusLibraryTab.entries.toList(), PlusLibraryTab.FOLDERS, -1)
        assertEquals(listOf(PlusLibraryTab.SONGS, PlusLibraryTab.ALBUMS, PlusLibraryTab.ARTISTS, PlusLibraryTab.FOLDERS, PlusLibraryTab.PLAYLISTS, PlusLibraryTab.FAVORITES), moved)
        assertEquals(moved, plusMoveTab(moved, PlusLibraryTab.SONGS, -1))
    }

    @Test fun durationsParseOnlyRealClockText() {
        assertEquals(190L, plusDurationSeconds("3:10"))
        assertEquals(3723L, plusDurationSeconds("1:02:03"))
        assertEquals(0L, plusDurationSeconds(null))
        assertEquals(0L, plusDurationSeconds("live"))
    }
}
