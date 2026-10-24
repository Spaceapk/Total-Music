package com.music.bitchord.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads available file metadata without changing tags or guessing missing credits. */
object LocalSongMetadata {
    suspend fun read(context: Context, song: Song): List<Pair<Int, String>> = withContext(Dispatchers.IO) {
        val values = linkedMapOf(R.string.meta_title to song.title, R.string.meta_artist to song.artist)
        song.albumName?.let { values[R.string.meta_album] = it }
        val uri = song.localUri?.let(Uri::parse) ?: return@withContext values.toList()
        values[R.string.meta_location] = uri.toString()
        if (uri.scheme == "file") uri.path?.let { path ->
            val file = java.io.File(path)
            if (file.isFile) {
                values[R.string.meta_filename] = file.name
                values[R.string.meta_size] = "${file.length()} bytes"
            }
        }
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val name = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val size = c.getColumnIndex(OpenableColumns.SIZE)
                    if (name >= 0 && !c.isNull(name)) values[R.string.meta_filename] = c.getString(name)
                    if (size >= 0 && !c.isNull(size)) values[R.string.meta_size] = "${c.getLong(size)} bytes"
                }
            }
            context.contentResolver.getType(uri)?.let { values[R.string.meta_format] = it }
        }
        runCatching {
            val reader = MediaMetadataRetriever()
            try {
                reader.setDataSource(context, uri)
                val keys = listOf(
                    R.string.meta_title to MediaMetadataRetriever.METADATA_KEY_TITLE,
                    R.string.meta_artist to MediaMetadataRetriever.METADATA_KEY_ARTIST,
                    R.string.meta_album to MediaMetadataRetriever.METADATA_KEY_ALBUM,
                    R.string.meta_album_artist to MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST,
                    R.string.meta_composer to MediaMetadataRetriever.METADATA_KEY_COMPOSER,
                    R.string.meta_writer to MediaMetadataRetriever.METADATA_KEY_WRITER,
                    R.string.meta_author to MediaMetadataRetriever.METADATA_KEY_AUTHOR,
                    R.string.meta_year to MediaMetadataRetriever.METADATA_KEY_YEAR,
                    R.string.meta_genre to MediaMetadataRetriever.METADATA_KEY_GENRE,
                    R.string.meta_track to MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER,
                    R.string.meta_disc to MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER,
                    R.string.meta_bitrate to MediaMetadataRetriever.METADATA_KEY_BITRATE,
                    R.string.meta_duration to MediaMetadataRetriever.METADATA_KEY_DURATION,
                    R.string.meta_format to MediaMetadataRetriever.METADATA_KEY_MIMETYPE,
                )
                keys.forEach { (label, key) ->
                    reader.extractMetadata(key)?.takeIf { it.isNotBlank() }?.let { values[label] = it }
                }
            } finally { reader.release() }
        }
        values.toList()
    }
}
