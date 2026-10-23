package com.music.bitchord.ui.components

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.music.bitchord.R
import com.music.bitchord.data.LocalSongMetadata
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** File deletion is always confirmed here and additionally by Android when required. */
@Composable
internal fun LocalSongFileActions(song: Song, accent: Color, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember(song.videoId) { mutableStateOf(false) }
    var confirmDelete by remember(song.videoId) { mutableStateOf(false) }
    var busy by remember(song.videoId) { mutableStateOf(false) }
    var retryAfterConsent by remember { mutableStateOf(false) }
    val uri = song.localUri?.let(Uri::parse)
    fun reportDeleted() {
        busy = false
        Toast.makeText(context, R.string.local_file_deleted, Toast.LENGTH_SHORT).show()
        onDeleted()
    }
    fun reportFailure() {
        busy = false
        Toast.makeText(context, R.string.local_file_delete_failed, Toast.LENGTH_LONG).show()
    }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) busy = false
        else if (!retryAfterConsent) reportDeleted()
        else scope.launch {
            val removed = withContext(Dispatchers.IO) { runCatching { context.contentResolver.delete(uri!!, null, null) > 0 }.getOrDefault(false) }
            if (removed) reportDeleted() else reportFailure()
        }
    }
    fun remove() {
        if (uri == null || busy) return
        busy = true
        if (Build.VERSION.SDK_INT >= 30 && uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY) {
            runCatching {
                retryAfterConsent = false
                consent.launch(IntentSenderRequest.Builder(MediaStore.createDeleteRequest(context.contentResolver, listOf(uri)).intentSender).build())
            }.onFailure { reportFailure() }
        } else scope.launch {
            try {
                val removed = withContext(Dispatchers.IO) {
                    if (uri.scheme == "file") uri.path?.let { File(it).delete() } == true
                    else context.contentResolver.delete(uri, null, null) > 0
                }
                if (removed) reportDeleted() else reportFailure()
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT == 29 && e is RecoverableSecurityException) {
                    retryAfterConsent = true
                    consent.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
                } else reportFailure()
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) remove() else reportFailure()
    }
    ActionRow(Icons.Rounded.Info, stringResource(R.string.local_song_information), accent = accent) { info = true }
    if (uri != null) ActionRow(Icons.Rounded.Delete, stringResource(R.string.local_delete_file), accent = accent) {
        if (!busy) confirmDelete = true
    }
    if (info) {
        val metadata by produceState<List<Pair<Int, String>>?>(null, song) {
            value = LocalSongMetadata.read(context, song)
        }
        AlertDialog(onDismissRequest = { info = false }, title = { Text(stringResource(R.string.local_song_information)) },
            text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                val rows = metadata
                if (rows == null) CircularProgressIndicator()
                else rows.forEach { (label, value) ->
                    Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
            } }, confirmButton = { TextButton(onClick = { info = false }) { Text(stringResource(R.string.back)) } })
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.local_delete_file)) },
        text = { Text(stringResource(R.string.local_delete_file_confirm, song.title)) },
        confirmButton = { TextButton(onClick = {
            confirmDelete = false
            if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                permission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            else remove()
        }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } })
}
