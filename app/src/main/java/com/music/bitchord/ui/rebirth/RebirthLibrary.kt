package com.music.bitchord.ui.rebirth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.music.bitchord.ui.components.LocalAppBackdrop
import com.music.bitchord.ui.components.liquidGlass
import com.music.bitchord.ui.components.backdrop.backdrops.layerBackdrop
import com.music.bitchord.ui.components.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.data.DevicePlaylistStore
import com.music.bitchord.data.PlaylistSort
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.LocalMusicSort
import com.music.bitchord.ui.player.PlayerVisualStyle
import com.music.bitchord.ui.player.rebirthColors
import com.music.bitchord.ui.screens.DevicePlaylistPickerDialog

internal fun rebirthAlbumOrder(songs: List<Song>) = songs.sortedWith(compareBy<Song> { it.localDiscNumber ?: 1 }
    .thenBy { it.localTrackNumber?.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
internal fun rebirthMatches(song: Song, query: String) = query.isBlank() || listOf(song.title, song.artist, song.albumName.orEmpty()).any { it.contains(query.trim(), true) }
private enum class Destination(val label: String) { HOME("Inicio"), LIBRARY("Biblioteca"), SEARCH("Buscar") }
private enum class Category(val label: String) { SONGS("Canciones"), ALBUMS("Álbumes"), ARTISTS("Artistas"), PLAYLISTS("Playlists"), FAVORITES("Favoritos") }
private data class Collection(val id: String, val title: String, val songs: List<Song>, val playlistId: String? = null)

@Composable
fun RebirthLocalExperience(style: PlayerVisualStyle, songs: List<Song>, currentSong: Song?, isPlaying: Boolean,
    loading: Boolean, favoriteIds: Set<String>, initialQuery: String,
    onPlay: (List<Song>, Int) -> Unit, onSongMenu: (Song) -> Unit, onSettings: () -> Unit,
    onPlayPause: () -> Unit, onNext: () -> Unit, onExpandPlayer: () -> Unit, emptyMessage: String? = null) {
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    var category by rememberSaveable { mutableStateOf(Category.SONGS) }
    var query by rememberSaveable { mutableStateOf("") }
    var collectionId by rememberSaveable { mutableStateOf<String?>(null) }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val selectedIds = remember(selected) { selected.toHashSet() }
    var addToPlaylist by remember { mutableStateOf<List<Song>?>(null) }
    var playlistName by remember { mutableStateOf<String?>(null) }
    var renameId by remember { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var editPlaylist by rememberSaveable { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember(context) { DevicePlaylistStore.forDevice(context) }
    val playlists by store.playlists.collectAsStateWithLifecycle()
    val order by AppSettings.localMusicSort.collectAsStateWithLifecycle()
    val transitions by AppSettings.playerAnimatedTransitions.collectAsStateWithLifecycle()
    val reduceMotion by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val albums = remember(songs) { songs.groupBy { it.albumName?.takeIf(String::isNotBlank) ?: "Sin álbum" }.map { (name, tracks) -> Collection("album:$name", name, rebirthAlbumOrder(tracks)) } }
    val artists = remember(songs) { songs.groupBy { it.artist }.map { (name, tracks) -> Collection("artist:$name", name, tracks) } }
    val saved = remember(playlists) { playlists.map { Collection("playlist:${it.id}", it.name, it.sortedSongs(), it.id) } }
    val allCollections = remember(albums, artists, saved) { albums + artists + saved }
    val opened = allCollections.firstOrNull { it.id == collectionId }
    val collections = when (category) { Category.ALBUMS -> albums; Category.ARTISTS -> artists; Category.PLAYLISTS -> saved; else -> emptyList() }
    val collectionRows = remember(collections, query) { collections.filter { it.title.contains(query, true) }.chunked(2) }
    val sortedLibrary = remember(songs, order) { when (order) {
        LocalMusicSort.TRACK_NUMBER -> rebirthAlbumOrder(songs)
        LocalMusicSort.TITLE_ASC -> songs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        LocalMusicSort.TITLE_DESC -> songs.sortedWith(compareByDescending<Song> { it.title.lowercase(java.util.Locale.ROOT) })
        LocalMusicSort.DATE_ADDED -> songs.sortedByDescending { it.localDateAddedSeconds ?: Long.MIN_VALUE }
        LocalMusicSort.DATE_MODIFIED -> songs.sortedByDescending { it.localDateModifiedSeconds ?: Long.MIN_VALUE }
    } }
    val visible = remember(sortedLibrary, opened, query, category, favoriteIds) {
        val source = opened?.songs ?: if (category == Category.FAVORITES) sortedLibrary.filter { it.videoId in favoriteIds } else sortedLibrary
        source.filter { rebirthMatches(it, query) }
    }
    LaunchedEffect(initialQuery) { if (initialQuery.isNotBlank()) { destination = Destination.SEARCH; query = initialQuery } }
    LaunchedEffect(query, collectionId) { selected = emptyList(); editPlaylist = false }
    BackHandler(selected.isNotEmpty() || collectionId != null || destination != Destination.HOME) {
        when { selected.isNotEmpty() -> selected = emptyList(); collectionId != null -> collectionId = null; else -> destination = Destination.HOME }
    }
    val glass = style == PlayerVisualStyle.LIQUID_GLASS_PLUS
    val adaptive by com.music.bitchord.data.settings.TotalMusicSettings.adaptiveGlass.collectAsStateWithLifecycle()
    val effects by AppSettings.playerGlassEffects.collectAsStateWithLifecycle()
    val reduceBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val navigationScroll = com.music.bitchord.ui.components.floatingtabbar.rememberFloatingTabBarScrollConnection()
    val backdrop = rememberLayerBackdrop()
    val recordBackdrop = glass && effects && !reduceBlur && com.music.bitchord.ui.components.isGlassSupported()
    val colors = rebirthColors(style, MaterialTheme.colorScheme)
    MaterialTheme(colorScheme = colors) {
        Surface(color = colors.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().nestedScroll(navigationScroll)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (collectionId != null) IconButton(onClick = { collectionId = null }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver") }
                    Text("Total Music", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Box {
                        IconButton(onClick = { sortOpen = true }) { Icon(Icons.Rounded.Sort, "Ordenar") }
                        DropdownMenu(sortOpen, onDismissRequest = { sortOpen = false }) {
                            LocalMusicSort.entries.forEach { sort -> DropdownMenuItem(text = { Text(sort.label()) }, onClick = { AppSettings.setLocalMusicSort(sort); sortOpen = false }) }
                        }
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Ajustes") }
                }
                if (selected.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${selected.size}", Modifier.weight(1f))
                    TextButton(onClick = { selected = visible.map { it.videoId } }) { Text("Seleccionar todo") }
                    IconButton(onClick = { addToPlaylist = visible.filter { it.videoId in selectedIds } }) { Icon(Icons.Rounded.PlaylistAdd, "Añadir selección a playlist") }
                    IconButton(onClick = { selected = emptyList() }) { Icon(Icons.Rounded.Close, "Cancelar selección") }
                }
                AnimatedContent(targetState = destination to collectionId, modifier = Modifier.weight(1f).fillMaxWidth().then(if (recordBackdrop) Modifier.layerBackdrop(backdrop) else Modifier), label = "rebirthRoute",
                    transitionSpec = { fadeIn(tween(if (transitions && !reduceMotion) 180 else 0)) togetherWith fadeOut(tween(if (transitions && !reduceMotion) 180 else 0)) }) { (route, id) ->
                    val page = allCollections.firstOrNull { it.id == id }
                    when {
                        page != null -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            item { if (glass) Box(Modifier.fillMaxWidth().height(240.dp)) {
                                Cover(page.songs.firstOrNull(), Modifier.fillMaxSize(), 30)
                                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black))).padding(20.dp)) { Text(page.title, color = Color.White, style = MaterialTheme.typography.headlineMedium) }
                            } else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { Cover(page.songs.firstOrNull(), Modifier.size(128.dp)); Text(page.title, Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black) } }
                            item { Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(onClick = { if (page.songs.isNotEmpty()) onPlay(page.songs, 0) }, enabled = page.songs.isNotEmpty()) { Text("Reproducir") }
                                IconButton(onClick = { addToPlaylist = page.songs }) { Icon(Icons.Rounded.PlaylistAdd, "Añadir colección a playlist") }
                            } }
                            page.playlistId?.let { playlistId -> item { Row(Modifier.horizontalScroll(rememberScrollState())) {
                                TextButton(onClick = { renameId = playlistId; playlistName = page.title }) { Text("Nombre") }
                                TextButton(onClick = { editPlaylist = !editPlaylist }) { Text(if (editPlaylist) "Terminar" else "Editar") }
                                var playlistSortOpen by remember { mutableStateOf(false) }
                                Box {
                                    TextButton(onClick = { playlistSortOpen = true }) { Text("Orden") }
                                    DropdownMenu(playlistSortOpen, onDismissRequest = { playlistSortOpen = false }) {
                                        PlaylistSort.entries.forEach { sort -> DropdownMenuItem(text = { Text(when (sort) {
                                            PlaylistSort.MANUAL -> "Manual"; PlaylistSort.TITLE_ASC -> "Título A–Z"; PlaylistSort.TITLE_DESC -> "Título Z–A"; PlaylistSort.ARTIST -> "Artista"; PlaylistSort.RECENTLY_ADDED -> "Añadidas recientemente"
                                        }) }, onClick = { store.setSort(playlistId, sort); playlistSortOpen = false }) }
                                    }
                                }
                                TextButton(onClick = { deleteId = playlistId }) { Text("Eliminar") }
                            } } }
                            val pageSongs = page.songs.filter { rebirthMatches(it, query) }
                            itemsIndexed(pageSongs, key = { i, s -> "${s.videoId}:$i" }) { i, song ->
                                Track(song, i, song.videoId in selectedIds, song.videoId == currentSong?.videoId,
                                    { if (selected.isEmpty()) onPlay(pageSongs, i) else selected = selected.toggle(song.videoId) },
                                    { selected = selected.toggle(song.videoId) }, { onSongMenu(song) })
                                if (editPlaylist && page.playlistId != null && query.isBlank()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    fun move(to: Int) {
                                        val reordered = pageSongs.toMutableList().apply { add(to, removeAt(i)) }
                                        runCatching { store.reorder(page.playlistId, reordered.mapNotNull { it.localUri }) }.onFailure { android.widget.Toast.makeText(context, "La playlist cambió. Vuelve a abrirla.", android.widget.Toast.LENGTH_SHORT).show() }
                                    }
                                    IconButton(onClick = { move(i - 1) }, enabled = i > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Subir canción") }
                                    IconButton(onClick = { move(i + 1) }, enabled = i < pageSongs.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, "Bajar canción") }
                                    IconButton(onClick = { song.localUri?.let { store.removeSong(page.playlistId, it) } }) { Icon(Icons.Rounded.PlaylistRemove, "Quitar de esta playlist") }
                                }
                            }
                        }
                        route == Destination.HOME -> if (glass) GlassHome(songs, currentSong, albums, onPlay, onSongMenu, { collectionId = it.id; query = "" }) else ExpressiveHome(songs, currentSong, albums, onPlay, { collectionId = it.id; query = "" })
                        else -> Column(Modifier.fillMaxSize()) {
                            OutlinedTextField(query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), label = { Text("Buscar en tu música") }, singleLine = true, shape = RoundedCornerShape(if (glass) 18.dp else 30.dp))
                            if (category == Category.PLAYLISTS) TextButton(onClick = { renameId = null; playlistName = "" }) { Text("Crear playlist") }
                            @Composable fun Body(modifier: Modifier) {
                                LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (category == Category.SONGS || category == Category.FAVORITES) {
                                        if (visible.isEmpty()) item { Text(emptyMessage ?: "No hay canciones que coincidan.") }
                                        itemsIndexed(visible, key = { _, s -> s.videoId }) { i, song -> Track(song, i, song.videoId in selectedIds, song.videoId == currentSong?.videoId,
                                            { if (selected.isEmpty()) onPlay(visible, i) else selected = selected.toggle(song.videoId) }, { selected = selected.toggle(song.videoId) }, { onSongMenu(song) }) }
                                    } else {
                                        items(collectionRows) { pair -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            pair.forEach { c -> Column(Modifier.weight(1f).clickable { collectionId = c.id; query = "" }) { Cover(c.songs.firstOrNull(), Modifier.fillMaxWidth().aspectRatio(1f)); Text(c.title, maxLines = 2); Text("${c.songs.size} canciones", style = MaterialTheme.typography.labelSmall) } }
                                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                                        } }
                                    }
                                }
                            }
                            if (glass) Row(Modifier.weight(1f)) {
                                Column(Modifier.width(108.dp)) { Category.entries.forEach { c -> TextButton(onClick = { category = c }) { Text(c.label, fontWeight = if (category == c) FontWeight.Bold else FontWeight.Normal) } } }
                                Body(Modifier.weight(1f).fillMaxHeight())
                            } else {
                                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(Category.entries) { c -> FilterChip(category == c, onClick = { category = c }, label = { Text(c.label) }) } }
                                Body(Modifier.weight(1f).fillMaxWidth())
                            }
                        }
                    }
                }
                CompositionLocalProvider(LocalAppBackdrop provides backdrop) {
                    currentSong?.let { RebirthMiniPlayer(style, it, isPlaying, loading, onPlayPause, onNext, onExpandPlayer) }
                    val navShape = RoundedCornerShape(if (glass) 28.dp else 20.dp)
                    Surface(color = if (glass && effects) Color.Transparent else colors.surfaceContainer,
                        shape = navShape, modifier = Modifier.fillMaxWidth().padding(horizontal = if (glass) 24.dp else 12.dp, vertical = 4.dp)
                            .then(if (glass && effects) Modifier.liquidGlass(navShape) else Modifier)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Destination.entries.forEach { d -> TextButton(onClick = { destination = d; collectionId = null; query = ""; selected = emptyList() }) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(when (d) { Destination.HOME -> Icons.Rounded.Home; Destination.LIBRARY -> Icons.Rounded.LibraryMusic; Destination.SEARCH -> Icons.Rounded.Search }, d.label,
                                        tint = if (d == destination) colors.primary else colors.onSurfaceVariant)
                                    if (!(adaptive && navigationScroll.isInline)) Text(d.label, fontWeight = if (d == destination) FontWeight.Bold else FontWeight.Normal)
                                }
                            } }
                        }
                    }
                }
            }
        }
        addToPlaylist?.let { DevicePlaylistPickerDialog(it) { addToPlaylist = null; selected = emptyList() } }
        playlistName?.let { name -> AlertDialog(onDismissRequest = { playlistName = null }, title = { Text(if (renameId == null) "Crear playlist" else "Renombrar playlist") },
            text = { OutlinedTextField(name, onValueChange = { playlistName = it }, label = { Text("Nombre") }) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { val id = renameId; if (id == null) store.create(name.trim()) else store.rename(id, name.trim()); playlistName = null }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { playlistName = null }) { Text("Cancelar") } }) }
        deleteId?.let { id -> AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("¿Eliminar la playlist?") }, text = { Text("Las canciones del dispositivo se conservarán.") },
            confirmButton = { TextButton(onClick = { store.delete(id); deleteId = null; collectionId = null }) { Text("Eliminar") } }, dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancelar") } }) }
    }
}
private fun LocalMusicSort.label() = when (this) { LocalMusicSort.TRACK_NUMBER -> "Número de pista"; LocalMusicSort.TITLE_ASC -> "Título A–Z"; LocalMusicSort.TITLE_DESC -> "Título Z–A"; LocalMusicSort.DATE_ADDED -> "Añadidas recientemente"; LocalMusicSort.DATE_MODIFIED -> "Modificadas recientemente" }
private fun List<String>.toggle(id: String) = if (id in this) filterNot { it == id } else this + id

@Composable
private fun Cover(song: Song?, modifier: Modifier, radius: Int = 24) {
    Box(modifier.clip(RoundedCornerShape(radius.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.MusicNote, null)
        AsyncImage(song?.thumbnailUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Track(song: Song, index: Int, selected: Boolean, playing: Boolean, onClick: () -> Unit, onSelect: () -> Unit, onMenu: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
        .combinedClickable(onClick = onClick, onLongClick = onSelect).padding(start = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (selected) "✓" else "${index + 1}")
        Cover(song, Modifier.size(48.dp), 12)
        Column(Modifier.weight(1f)) { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface); Text(song.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall) }
        IconButton(onClick = onMenu) { Icon(Icons.Rounded.MoreVert, "Opciones de ${song.title}") }
    }
}
@Composable
private fun ExpressiveHome(songs: List<Song>, current: Song?, albums: List<Collection>, onPlay: (List<Song>, Int) -> Unit, onAlbum: (Collection) -> Unit) {
    val hero = current ?: songs.firstOrNull()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Dale play\na tu día", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black); Text(hero?.title.orEmpty(), maxLines = 2)
                FilledIconButton(onClick = { if (songs.isNotEmpty()) onPlay(songs, songs.indexOfFirst { it.videoId == hero?.videoId }.coerceAtLeast(0)) }, Modifier.size(64.dp)) { Icon(Icons.Rounded.PlayArrow, "Reproducir") } }
            Cover(hero, Modifier.weight(1f).height(220.dp), 60)
        } }
        item { Text("Tu colección", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Metric("${songs.size}", "Canciones", Color(0xFFDCD7FC), Modifier.weight(1f)); Metric("${albums.size}", "Álbumes", Color(0xFFD7FF4F), Modifier.weight(1f)) } }
        items(albums.chunked(2)) { pair -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { pair.forEach { c -> Column(Modifier.weight(1f).clickable { onAlbum(c) }) { Cover(c.songs.firstOrNull(), Modifier.fillMaxWidth().aspectRatio(1f)); Text(c.title, maxLines = 2) } }; if (pair.size == 1) Spacer(Modifier.weight(1f)) } }
        if (songs.isEmpty()) item { Text("Añade música al dispositivo para empezar.") }
    }
}
@Composable
private fun Metric(number: String, label: String, color: Color, modifier: Modifier) { Column(modifier.clip(RoundedCornerShape(26.dp)).background(color).padding(20.dp)) { Text(number, color = Color.Black, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black); Text(label, color = Color.Black) } }
@Composable
private fun GlassHome(songs: List<Song>, current: Song?, albums: List<Collection>, onPlay: (List<Song>, Int) -> Unit, onSongMenu: (Song) -> Unit, onAlbum: (Collection) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item { Column(Modifier.padding(horizontal = 24.dp)) { Text("Tu espacio", style = MaterialTheme.typography.headlineLarge); Text("La música que va contigo", color = MaterialTheme.colorScheme.primary) } }
        item { LazyRow(contentPadding = PaddingValues(horizontal = 32.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(albums, key = { it.id }) { c -> Column(Modifier.width(180.dp).clickable { onAlbum(c) }) { Cover(c.songs.firstOrNull(), Modifier.size(180.dp), 18); Text(c.title, maxLines = 1) } } } }
        item { Surface(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) { Cover(current ?: songs.firstOrNull(), Modifier.size(72.dp), 16); Column(Modifier.weight(1f)) { Text("TU SESIÓN", style = MaterialTheme.typography.labelSmall); Text(current?.title ?: "Empieza a escuchar", maxLines = 2) }; IconButton(onClick = { if (songs.isNotEmpty()) onPlay(songs, songs.indexOfFirst { it.videoId == current?.videoId }.coerceAtLeast(0)) }) { Icon(Icons.Rounded.PlayArrow, "Reproducir") } }
        } }
        item { Text("Explora tu música", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleLarge) }
        itemsIndexed(songs.take(8), key = { _, s -> s.videoId }) { i, song -> Track(song, i, false, current?.videoId == song.videoId, { onPlay(songs, i) }, { onSongMenu(song) }, { onSongMenu(song) }) }
        if (songs.isEmpty()) item { Text("Añade música al dispositivo para empezar.", Modifier.padding(24.dp)) }
    }
}
