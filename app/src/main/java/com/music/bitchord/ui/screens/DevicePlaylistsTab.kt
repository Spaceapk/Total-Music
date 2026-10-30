package com.music.bitchord.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.ui.components.ReorderableSongList
import com.music.bitchord.data.PlaylistSort
import com.music.bitchord.data.DevicePlaylistStore
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Local-only management; playback uses the same queue and engine as the device library. */
@Composable
fun DevicePlaylistsTab(songs: List<Song>, onPlay: (List<Song>, Int) -> Unit, padding: PaddingValues, onOpenSettings: () -> Unit = {}) {
    val context = LocalContext.current.applicationContext
    val loaded = remember(context) { runCatching { DevicePlaylistStore.forDevice(context) } }
    val store = loaded.getOrNull()
    if (store == null) {
        Text(stringResource(R.string.star_save_error), Modifier.padding(20.dp))
        return
    }
    val playlists by store.playlists.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = playlists.firstOrNull { it.id == selectedId }
    var searchOpen by rememberSaveable(selectedId) { mutableStateOf(false) }
    var query by rememberSaveable(selectedId) { mutableStateOf("") }
    var sortOpen by remember(selectedId) { mutableStateOf(false) }
    val orderedSongs = remember(selected) { selected?.sortedSongs().orEmpty() }
    val visibleSongs = remember(orderedSongs, query) {
        orderedSongs.filter { query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) }
    }
    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var reordering by remember { mutableStateOf(false) }
    var actionsExpanded by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun change(action: () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { withContext(Dispatchers.IO) { action() } }
            catch (e: Exception) { error = e.localizedMessage ?: "Could not save playlist" }
            finally { busy = false }
        }
    }
    BackHandler(selected != null || searchOpen) {
        if (searchOpen) { searchOpen = false; query = "" } else selectedId = null
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected != null) IconButton(onClick = { selectedId = null }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
            }
            if (searchOpen) OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text(stringResource(R.string.space_playlist_search)) }, modifier = Modifier.weight(1f))
            else Text(selected?.name ?: stringResource(R.string.playlists), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { searchOpen = !searchOpen; query = "" }) {
                Icon(if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search, stringResource(R.string.space_playlist_search))
            }
            if (selected == null) IconButton(onClick = { name = ""; editing = true }, enabled = !busy) {
                Icon(Icons.Rounded.Add, stringResource(R.string.create_playlist))
            }
            if (selected == null) IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, stringResource(R.string.settings))
            }
        }
        if (selected != null) Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp),
                onClick = { onPlay(visibleSongs.map { it.copy(videoId = it.localUri!!) }, 0) }, enabled = visibleSongs.isNotEmpty()) {
                Icon(Icons.Rounded.PlayArrow, null)
                Text(stringResource(R.string.play), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { checked = emptySet(); adding = true }, enabled = !busy) {
                Icon(Icons.Rounded.Add, stringResource(R.string.star_add_songs))
            }
            Box {
                IconButton(onClick = { sortOpen = true }, enabled = !busy) {
                    Icon(Icons.Rounded.Sort, stringResource(R.string.space_playlist_sort))
                }
                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    Text(stringResource(R.string.space_playlist_sort_hint), Modifier.padding(16.dp), style = MaterialTheme.typography.labelMedium)
                    PlaylistSort.entries.forEach { option ->
                        DropdownMenuItem(text = { Text(stringResource(when (option) {
                            PlaylistSort.MANUAL -> R.string.space_sort_manual
                            PlaylistSort.TITLE_ASC -> R.string.sort_title_ascending
                            PlaylistSort.TITLE_DESC -> R.string.sort_title_descending
                            PlaylistSort.ARTIST_ASC -> R.string.space_sort_artist_ascending
                            PlaylistSort.ARTIST_DESC -> R.string.space_sort_artist_descending
                            PlaylistSort.ALBUM_ASC -> R.string.space_sort_album_ascending
                            PlaylistSort.ALBUM_DESC -> R.string.space_sort_album_descending
                            PlaylistSort.RECENTLY_ADDED -> R.string.space_sort_recent
                        })) }, trailingIcon = {
                            if (selected.sort == option) Icon(Icons.Rounded.Check, null)
                        }, onClick = { sortOpen = false; change { store.setSort(selected.id, option) } })
                    }
                }
            }
            Box {
                IconButton(onClick = { actionsExpanded = true }, enabled = !busy) {
                    Icon(Icons.Rounded.MoreVert, stringResource(R.string.space_playlist_actions))
                }
                DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.reorder_songs)) },
                        leadingIcon = { Icon(Icons.Rounded.DragHandle, null) }, enabled = selected.songs.size > 1,
                        onClick = { actionsExpanded = false; reordering = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.rename)) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { actionsExpanded = false; name = selected.name; editing = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.settings)) },
                        leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                        onClick = { actionsExpanded = false; onOpenSettings() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = { actionsExpanded = false; deleting = true })
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (selected == null) {
            if (playlists.isEmpty()) Text(stringResource(R.string.star_playlist_hint), Modifier.padding(20.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
            ) {
                items(playlists.filter { query.isBlank() || it.name.contains(query, true) }, key = { it.id }) { p ->
                    ListItem(headlineContent = { Text(p.name) }, supportingContent = { Text("${p.songs.size}") },
                        leadingContent = { Icon(Icons.Rounded.LibraryMusic, null) },
                        modifier = Modifier.clickable { selectedId = p.id })
                }
            }
        } else {
            if (selected.songs.isEmpty()) Text(stringResource(R.string.star_playlist_hint), Modifier.padding(20.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
            ) {
                itemsIndexed(visibleSongs, key = { _, s -> s.localUri ?: s.videoId }) { index, song ->
                    ListItem(headlineContent = { Text(song.title, maxLines = 1) }, supportingContent = { Text(song.artist, maxLines = 1) },
                        modifier = Modifier.clickable { onPlay(visibleSongs.map { it.copy(videoId = it.localUri!!) }, index) },
                        trailingContent = {
                            var songMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { songMenu = true }, enabled = !busy) {
                                    Icon(Icons.Rounded.MoreVert, stringResource(R.string.space_song_actions))
                                }
                                DropdownMenu(expanded = songMenu, onDismissRequest = { songMenu = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.remove)) },
                                        leadingIcon = { Icon(Icons.Rounded.PlaylistRemove, null) },
                                        onClick = { songMenu = false; change { store.removeSong(selected.id, requireNotNull(song.localUri)) } })
                                }
                            }
                        })
                }
            }
        }
    }
    if (reordering && selected != null) {
        val playlistId = selected.id
        val order = remember(playlistId) { mutableStateListOf<Song>().apply { addAll(orderedSongs) } }
        androidx.compose.ui.window.Dialog(onDismissRequest = { reordering = false }) {
            Surface(shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(16.dp)) {
                    Text(stringResource(R.string.reorder_songs), style = MaterialTheme.typography.titleLarge)
                    Box(Modifier.weight(1f, fill = false)) { ReorderableSongList(order, enabled = !busy) }
                    Row(Modifier.align(Alignment.End)) {
                        TextButton(onClick = { reordering = false }) { Text(stringResource(R.string.cancel)) }
                        TextButton(enabled = !busy, onClick = {
                            val uris = order.map { requireNotNull(it.localUri) }
                            change { store.reorder(playlistId, uris) }
                            reordering = false
                        }) { Text(stringResource(R.string.save_order)) }
                    }
                }
            }
        }
    }
    if (editing) AlertDialog(onDismissRequest = { editing = false },
        title = { Text(stringResource(if (selected == null) R.string.create_playlist else R.string.rename)) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(R.string.playlist_name)) }) },
        confirmButton = { TextButton(enabled = name.isNotBlank() && !busy, onClick = {
            val id = selected?.id
            val value = name
            change { if (id == null) store.create(value) else store.rename(id, value) }
            editing = false
        }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.cancel)) } })
    if (deleting && selected != null) AlertDialog(onDismissRequest = { deleting = false },
        title = { Text(stringResource(R.string.delete)) }, text = { Text(stringResource(R.string.star_delete_playlist)) },
        confirmButton = { TextButton(onClick = { val id = selected.id; change { store.delete(id) }; selectedId = null; deleting = false }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } })
    if (adding && selected != null) AlertDialog(onDismissRequest = { adding = false },
        title = { Text(stringResource(R.string.star_add_songs)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(songs.filter { it.localUri != null }.distinctBy { it.localUri }, key = { it.localUri!! }) { song ->
                    val uri = song.localUri!!
                    Row(Modifier.fillMaxWidth().clickable { checked = if (uri in checked) checked - uri else checked + uri }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = uri in checked, onCheckedChange = { checked = if (it) checked + uri else checked - uri })
                        Column { Text(song.title, maxLines = 1); Text(song.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = checked.isNotEmpty() && !busy, onClick = {
            val id = selected.id; val tracks = songs.filter { it.localUri in checked }
            change { store.add(id, tracks) }; adding = false
        }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.cancel)) } })
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text(stringResource(R.string.star_save_error)) }, text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text(stringResource(R.string.back)) } }) }
}

/** Shared by the song menu and library batch selection; never uses account playlists. */
@Composable
fun DevicePlaylistPickerDialog(songs: List<Song>, onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { runCatching { DevicePlaylistStore.forDevice(context) } }.getOrNull()
    if (store == null) {
        AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.star_save_error)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
        return
    }
    val playlists by store.playlists.collectAsStateWithLifecycle()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.add_to_playlist)) },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        Row(Modifier.fillMaxWidth().clickable(enabled = !busy) {
                            selectedId = playlist.id; name = ""
                        }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedId == playlist.id, enabled = !busy,
                                onClick = { selectedId = playlist.id; name = "" })
                            Text(playlist.name, maxLines = 2)
                        }
                    }
                }
                OutlinedTextField(value = name, enabled = !busy, singleLine = true,
                    onValueChange = { name = it; if (it.isNotBlank()) selectedId = null },
                    label = { Text(stringResource(R.string.new_playlist)) })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && (name.isNotBlank() || playlists.any { it.id == selectedId }), onClick = {
                val id = selectedId
                val title = name.trim()
                busy = true
                scope.launch {
                    try {
                        val added = withContext(Dispatchers.IO) {
                            val before = store.playlists.value.firstOrNull { it.id == id }?.songs?.size ?: 0
                            if (id == null) store.createWithSongs(title, songs) else store.add(id, songs)
                            if (id == null) songs.distinctBy { it.localUri }.size
                            else (store.playlists.value.first { it.id == id }.songs.size - before)
                        }
                        android.widget.Toast.makeText(context,
                            context.getString(R.string.playlist_added_count, added), android.widget.Toast.LENGTH_SHORT).show()
                        onDismiss()
                    } catch (e: Exception) { error = e.localizedMessage }
                    finally { busy = false }
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
