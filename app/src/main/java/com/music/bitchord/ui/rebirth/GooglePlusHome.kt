package com.music.bitchord.ui.rebirth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.model.Song

/**
 * Google Plus 2, app side: the "Your Mix" home, the search landing and the
 * library tabs. Every card is fed by [RebirthLibraryData] — the device
 * library, its tags and the recorded listening history — and every tap goes
 * through the same [RebirthLibraryActions] the rest of the shell uses.
 */

@Composable
internal fun GooglePlusHome(data: RebirthLibraryData, actions: RebirthLibraryActions) {
    if (data.songs.isEmpty()) {
        RebirthEmptyState(
            icon = Icons.Rounded.MusicNote,
            title = stringResource(R.string.library_empty),
            message = data.emptyMessage ?: stringResource(R.string.rebirth_home_empty),
        )
        return
    }
    val home = data.plusHome
    val mix = home?.dailyMix?.takeIf { it.isNotEmpty() } ?: data.songs.take(PLUS_DAILY_MIX_SIZE)
    val recent = home?.recent.orEmpty()
    LazyColumn(
        Modifier.fillMaxSize().testTag("google-plus-home"),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "plus-mix") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.google_plus_mix), Modifier.weight(1f),
                        style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
                    FilledTonalIconButton(onClick = { actions.onShuffle(data.songs) },
                        modifier = Modifier.size(64.dp).testTag("google-plus-shuffle"), shape = RoundedCornerShape(24.dp)) {
                        Icon(Icons.Rounded.Shuffle, stringResource(R.string.shuffle))
                    }
                }
                GooglePlusCollage(mix) { song -> actions.onPlay(mix, mix.indexOf(song).coerceAtLeast(0)) }
            }
        }
        item(key = "plus-daily-mix") { PlusDailyMixCard(mix) { actions.onPlay(mix, 0) } }
        if (recent.isNotEmpty()) {
            item(key = "plus-recent-header") {
                RebirthSectionHeader(
                    title = stringResource(if (home?.recentFromHistory == true) R.string.google_plus_recent_played else R.string.google_plus_recent_added),
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            item(key = "plus-recent") {
                LazyRow(Modifier.testTag("google-plus-recent"), contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(recent, key = { _, song -> song.videoId }) { index, song ->
                        Column(Modifier.width(124.dp).clip(RoundedCornerShape(20.dp)).clickable { actions.onPlay(recent, index) }) {
                            Cover(song, Modifier.fillMaxWidth().aspectRatio(1f), corner = 20, placeholder = Icons.Rounded.MusicNote)
                            Text(song.title, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        item(key = "plus-stats") { PlusStatsSection(home?.stats, data) }
        if (data.albums.isNotEmpty()) {
            item(key = "plus-albums-header") {
                RebirthSectionHeader(
                    title = stringResource(R.string.rebirth_home_explore),
                    modifier = Modifier.padding(horizontal = 20.dp),
                    action = {
                        TextButton(onClick = { actions.onDestination(RebirthDestination.LIBRARY); actions.onCategory(RebirthCategory.ALBUMS) }) {
                            Text(stringResource(R.string.show_all))
                        }
                    },
                )
            }
            item(key = "plus-albums") {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(data.albums, key = { it.id }) { album ->
                        Column(Modifier.width(150.dp).clickable { actions.onOpenCollection(album) }) {
                            Cover(album.songs.firstOrNull(), Modifier.fillMaxWidth().aspectRatio(1f), corner = 24, placeholder = Icons.Rounded.Album)
                            Text(album.title, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(collectionCaption(album.songs.size), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }
        item(key = "plus-songs-header") {
            RebirthSectionHeader(title = stringResource(R.string.songs), modifier = Modifier.padding(horizontal = 20.dp))
        }
        itemsIndexed(data.songs.take(8), key = { _, song -> "song:" + song.videoId }) { index, song ->
            ExpressiveTrackRow(
                song = song, position = index,
                selected = song.videoId in data.selection,
                playing = data.currentSong?.videoId == song.videoId && data.isPlaying,
                onPlay = { actions.onPlay(data.songs, index) },
                onSelect = { actions.onToggleSelection(song.videoId) },
                onMenu = { actions.onSongMenu(song) },
            )
        }
        item(key = "plus-library") {
            TextButton(onClick = { actions.onDestination(RebirthDestination.LIBRARY) }, modifier = Modifier.padding(horizontal = 12.dp)) {
                Icon(Icons.Rounded.LibraryMusic, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.library))
            }
        }
    }
}

/** Five real sleeves of today's mix in five shapes; tapping one plays the mix from it. */
@Composable
private fun GooglePlusCollage(songs: List<Song>, onPlay: (Song) -> Unit) {
    val covers = remember(songs) { songs.take(5) }
    BoxWithConstraints(Modifier.fillMaxWidth().height(300.dp).testTag("google-plus-mix-collage")) {
        val unit = maxWidth
        covers.forEachIndexed { index, song ->
            val alignment = when (index) {
                0 -> Alignment.Center
                1 -> Alignment.TopStart
                2 -> Alignment.TopEnd
                3 -> Alignment.BottomStart
                else -> Alignment.BottomEnd
            }
            val side = unit * when (index) { 0 -> .50f; 1 -> .24f; 2 -> .22f; 3 -> .26f; else -> .22f }
            val corner = when (index) { 0 -> 40; 1 -> 1000; 2 -> 22; 3 -> 28; else -> 1000 }
            val turn = when (index) { 0 -> -6f; 2 -> 12f; 3 -> -14f; else -> 0f }
            Cover(song, Modifier.align(alignment)
                .padding(if (index == 0) 0.dp else 6.dp)
                .rotate(turn)
                .size(side)
                .testTag("google-plus-collage-$index")
                .clickable { onPlay(song) },
                corner = corner, placeholder = Icons.Rounded.MusicNote)
        }
    }
}

@Composable
private fun PlusDailyMixCard(mix: List<Song>, onPlay: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val seconds = remember(mix) { mix.sumOf { plusDurationSeconds(it.durationText) } }
    Surface(
        shape = RoundedCornerShape(32.dp), color = colors.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(32.dp))
            .clickable(onClick = onPlay).testTag("google-plus-daily-mix"),
    ) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.google_plus_daily_mix), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, color = colors.onPrimaryContainer)
                Text(stringResource(R.string.google_plus_daily_mix_caption, mix.size, plusClock(seconds)),
                    style = MaterialTheme.typography.bodyMedium, color = colors.onPrimaryContainer)
                Text(stringResource(R.string.google_plus_daily_mix_hint), style = MaterialTheme.typography.bodySmall,
                    color = colors.onPrimaryContainer.copy(alpha = .78f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                    mix.take(4).forEach { song ->
                        Cover(song, Modifier.size(34.dp), corner = 1000, placeholder = Icons.Rounded.MusicNote)
                    }
                }
            }
            FilledIconButton(onClick = onPlay, modifier = Modifier.size(64.dp), shape = RoundedCornerShape(24.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.primary, contentColor = colors.onPrimary)) {
                Icon(Icons.Rounded.PlayArrow, stringResource(R.string.google_plus_play_daily_mix), Modifier.size(32.dp))
            }
        }
    }
}

private fun plusClock(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "$hours h $minutes min" else "$minutes min"
}

@Composable
private fun PlusStatsSection(stats: PlusStats?, data: RebirthLibraryData) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("google-plus-stats"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RebirthSectionHeader(title = stringResource(R.string.google_plus_stats))
        if (stats != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlusStatTile(stats.minutes.toString(), stringResource(R.string.google_plus_minutes),
                    stringResource(R.string.google_plus_stats_month), colors.tertiaryContainer, colors.onTertiaryContainer, Modifier.weight(1f))
                PlusStatTile(stats.plays.toString(), stringResource(R.string.google_plus_plays),
                    stringResource(R.string.google_plus_stats_month), colors.secondaryContainer, colors.onSecondaryContainer, Modifier.weight(1f))
            }
            stats.topArtist?.let { artist ->
                PlusStatTile(artist, stringResource(R.string.google_plus_top_artist),
                    stringResource(R.string.google_plus_stats_month), colors.surfaceContainerHigh, colors.onSurface, Modifier.fillMaxWidth(), compact = true)
            }
        } else {
            Text(stringResource(R.string.google_plus_no_history), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PlusStatTile(data.songs.size.toString(), stringResource(R.string.songs), null, colors.surfaceContainerHigh, colors.onSurface, Modifier.weight(1f), compact = true)
            PlusStatTile(data.albums.size.toString(), stringResource(R.string.albums), null, colors.surfaceContainerHigh, colors.onSurface, Modifier.weight(1f), compact = true)
            PlusStatTile(data.artists.size.toString(), stringResource(R.string.artists), null, colors.surfaceContainerHigh, colors.onSurface, Modifier.weight(1f), compact = true)
        }
    }
}

@Composable
private fun PlusStatTile(value: String, label: String, caption: String?, container: Color, content: Color, modifier: Modifier, compact: Boolean = false) {
    Surface(shape = RoundedCornerShape(if (compact) 22.dp else 28.dp), color = container, modifier = modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = if (compact) 12.dp else 16.dp)) {
            if (caption != null) Text(caption, style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = .75f), maxLines = 1)
            Text(value, style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = .85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ── Search landing ───────────────────────────────────────────────────────

private class PlusTile(val label: String, val caption: String?, val icon: ImageVector, val tag: String, val onClick: () -> Unit)

/** Pairs of container colours, light then dark; the text colour is chosen by contrast. */
private val PlusTilePalette = listOf(
    Color(0xFFF7C9D4) to Color(0xFF6B2A3C), Color(0xFFCFE3FF) to Color(0xFF1F4170),
    Color(0xFFD5EFC8) to Color(0xFF2B5223), Color(0xFFFFE0B5) to Color(0xFF6A4410),
    Color(0xFFE5D4FF) to Color(0xFF47307A), Color(0xFFC6F0EC) to Color(0xFF12524D),
    Color(0xFFFFD6C7) to Color(0xFF6E3220), Color(0xFFE9E7B8) to Color(0xFF4E4B12),
)

private fun plusGenreIcon(name: String): ImageVector {
    val key = name.lowercase()
    return when {
        listOf("jazz", "classic", "clásic", "piano", "blues").any { it in key } -> Icons.Rounded.Piano
        listOf("electr", "dance", "house", "techno", "edm").any { it in key } -> Icons.Rounded.GraphicEq
        listOf("pop", "rap", "hip", "vocal", "r&b", "soul").any { it in key } -> Icons.Rounded.Mic
        listOf("rock", "metal", "punk", "indie", "altern").any { it in key } -> Icons.Rounded.Headphones
        listOf("world", "latin", "folk", "regional", "reggae").any { it in key } -> Icons.Rounded.Public
        else -> Icons.Rounded.MusicNote
    }
}

/**
 * What the search page shows before anything is typed: the library's own
 * categories and the genres found in the files' tags. A tile opens exactly
 * the list it names; there are no placeholder genres.
 */
@Composable
internal fun GooglePlusSearchLanding(data: RebirthLibraryData, actions: RebirthLibraryActions, onTab: (PlusLibraryTab) -> Unit) {
    val browse = listOf(
        PlusTile(stringResource(R.string.songs), collectionCaption(data.songs.size), Icons.Rounded.MusicNote, "plus-browse-songs") { onTab(PlusLibraryTab.SONGS) },
        PlusTile(stringResource(R.string.albums), data.albums.size.toString(), Icons.Rounded.Album, "plus-browse-albums") { onTab(PlusLibraryTab.ALBUMS) },
        PlusTile(stringResource(R.string.artists), data.artists.size.toString(), Icons.Rounded.Person, "plus-browse-artists") { onTab(PlusLibraryTab.ARTISTS) },
        PlusTile(stringResource(R.string.playlists), data.playlists.count { it.isPlaylist }.toString(), Icons.AutoMirrored.Rounded.QueueMusic, "plus-browse-playlists") { onTab(PlusLibraryTab.PLAYLISTS) },
        PlusTile(stringResource(R.string.google_plus_folders), data.folders.size.toString(), Icons.Rounded.Folder, "plus-browse-folders") { onTab(PlusLibraryTab.FOLDERS) },
        PlusTile(stringResource(R.string.rebirth_favorites), collectionCaption(data.favoriteIds.size), Icons.Rounded.Favorite, "plus-browse-favorites") { onTab(PlusLibraryTab.FAVORITES) },
    )
    val genres = data.genres.map { genre ->
        PlusTile(genre.title, collectionCaption(genre.songs.size), plusGenreIcon(genre.title), "plus-genre-${genre.title}") { actions.onOpenCollection(genre) }
    }
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    LazyColumn(
        Modifier.fillMaxSize().testTag("google-plus-search-landing"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "genres-header") { RebirthSectionHeader(title = stringResource(R.string.google_plus_genres)) }
        if (genres.isEmpty()) {
            item(key = "genres-empty") {
                Text(stringResource(R.string.google_plus_no_genres), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        itemsIndexed(genres.chunked(2), key = { index, _ -> "genres-$index" }) { rowIndex, row ->
            PlusTileRow(row, offset = rowIndex * 2, dark = dark, tall = true)
        }
        item(key = "browse-header") { RebirthSectionHeader(title = stringResource(R.string.google_plus_browse)) }
        itemsIndexed(browse.chunked(2), key = { index, _ -> "browse-$index" }) { rowIndex, row ->
            PlusTileRow(row, offset = genres.size + rowIndex * 2, dark = dark, tall = false)
        }
        item(key = "online") {
            TextButton(onClick = actions.onOpenGlobalSearch, modifier = Modifier.testTag("plus-online-search")) {
                Icon(Icons.Rounded.Public, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.google_plus_online_search))
            }
        }
    }
}

@Composable
private fun PlusTileRow(row: List<PlusTile>, offset: Int, dark: Boolean, tall: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        row.forEachIndexed { index, tile ->
            val pair = PlusTilePalette[(offset + index) % PlusTilePalette.size]
            val container = if (dark) pair.second else pair.first
            val content = if (container.luminance() > .45f) Color(0xFF1B1B1F) else Color(0xFFF4F1F6)
            Surface(
                shape = RoundedCornerShape(24.dp), color = container,
                modifier = Modifier.weight(1f).heightIn(min = if (tall) 104.dp else 76.dp).clip(RoundedCornerShape(24.dp))
                    .clickable(onClick = tile.onClick).testTag(tile.tag),
            ) {
                Box(Modifier.padding(14.dp)) {
                    Column(Modifier.align(Alignment.TopStart).padding(end = 36.dp)) {
                        Text(tile.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            color = content, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        tile.caption?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = .8f), maxLines = 1)
                        }
                    }
                    Icon(tile.icon, contentDescription = null, tint = content.copy(alpha = .85f),
                        modifier = Modifier.align(Alignment.BottomEnd).size(if (tall) 34.dp else 26.dp).rotate(if (tall) -12f else 0f))
                }
            }
        }
        if (row.size == 1) Spacer(Modifier.weight(1f))
    }
}

// ── Library tabs and folders ─────────────────────────────────────────────

@Composable
internal fun PlusLibraryTab.label(): String = stringResource(
    when (this) {
        PlusLibraryTab.SONGS -> R.string.songs
        PlusLibraryTab.ALBUMS -> R.string.albums
        PlusLibraryTab.ARTISTS -> R.string.artists
        PlusLibraryTab.PLAYLISTS -> R.string.playlists
        PlusLibraryTab.FOLDERS -> R.string.google_plus_folders
        PlusLibraryTab.FAVORITES -> R.string.rebirth_favorites
    },
)

internal fun PlusLibraryTab.category(): RebirthCategory? = when (this) {
    PlusLibraryTab.SONGS -> RebirthCategory.SONGS
    PlusLibraryTab.ALBUMS -> RebirthCategory.ALBUMS
    PlusLibraryTab.ARTISTS -> RebirthCategory.ARTISTS
    PlusLibraryTab.PLAYLISTS -> RebirthCategory.PLAYLISTS
    PlusLibraryTab.FAVORITES -> RebirthCategory.FAVORITES
    PlusLibraryTab.FOLDERS -> null
}

internal fun plusSelectedTab(category: RebirthCategory, folders: Boolean): PlusLibraryTab = if (folders) PlusLibraryTab.FOLDERS else when (category) {
    RebirthCategory.SONGS -> PlusLibraryTab.SONGS
    RebirthCategory.ALBUMS -> PlusLibraryTab.ALBUMS
    RebirthCategory.ARTISTS -> PlusLibraryTab.ARTISTS
    RebirthCategory.PLAYLISTS -> PlusLibraryTab.PLAYLISTS
    RebirthCategory.FAVORITES -> PlusLibraryTab.FAVORITES
}

@Composable
internal fun GooglePlusLibraryTabs(order: List<PlusLibraryTab>, selected: PlusLibraryTab, onTab: (PlusLibraryTab) -> Unit, onReorder: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()).testTag("google-plus-library-tabs"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            order.forEach { tab ->
                Box(Modifier.testTag("plus-tab-${tab.name}").semantics { this.selected = tab == selected; role = Role.Tab }) {
                    RebirthFilterPill(label = tab.label(), selected = tab == selected, onClick = { onTab(tab) }, glass = false)
                }
            }
        }
        IconButton(onClick = onReorder, modifier = Modifier.testTag("plus-reorder-tabs")) {
            Icon(Icons.Rounded.SwapVert, contentDescription = stringResource(R.string.google_plus_reorder_tabs))
        }
    }
}

@Composable
internal fun GooglePlusTabOrderDialog(data: RebirthLibraryData, initial: List<PlusLibraryTab>, onSave: (List<PlusLibraryTab>) -> Unit, onDismiss: () -> Unit) {
    var draft by remember(initial) { mutableStateOf(initial) }
    RebirthDialog(
        style = data.style, title = stringResource(R.string.google_plus_reorder_tabs), onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.save), onConfirm = { onSave(draft) },
    ) {
        draft.forEachIndexed { index, tab ->
            val name = tab.label()
            Row(Modifier.fillMaxWidth().testTag("plus-order-${tab.name}"), verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { draft = plusMoveTab(draft, tab, -1) }, enabled = index > 0, modifier = Modifier.testTag("plus-order-up-${tab.name}")) {
                    Icon(Icons.Rounded.ArrowUpward, stringResource(R.string.google_plus_move_earlier, name))
                }
                IconButton(onClick = { draft = plusMoveTab(draft, tab, 1) }, enabled = index < draft.lastIndex, modifier = Modifier.testTag("plus-order-down-${tab.name}")) {
                    Icon(Icons.Rounded.ArrowDownward, stringResource(R.string.google_plus_move_later, name))
                }
            }
        }
    }
}

@Composable
internal fun GooglePlusFolders(data: RebirthLibraryData, actions: RebirthLibraryActions) {
    val folders = remember(data.folders, data.query) {
        data.folders.filter { data.query.isBlank() || it.title.contains(data.query.trim(), ignoreCase = true) }
    }
    if (folders.isEmpty()) {
        RebirthEmptyState(icon = Icons.Rounded.Folder, title = stringResource(R.string.google_plus_no_folders), message = data.emptyMessage)
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("google-plus-folders"), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(folders, key = { it.id }) { folder ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { actions.onOpenCollection(folder) }
                    .padding(horizontal = 8.dp, vertical = 8.dp).testTag(folder.id),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text(folder.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(collectionCaption(folder.songs.size), folder.plusFolderPath()).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
