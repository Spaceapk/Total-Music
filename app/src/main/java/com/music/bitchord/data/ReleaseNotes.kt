package com.music.bitchord.data

import android.content.Context
import com.music.bitchord.BuildConfig
import com.music.bitchord.R
import org.json.JSONArray

/** Bundled, versioned source; never uses notes for a downloaded but uninstalled APK. */
object ReleaseNotes {
    private fun entries(context: Context): JSONArray = JSONArray(
        context.assets.open("release-notes.json").bufferedReader().use { it.readText() }
    )
    fun installed(context: Context): String {
        val notes = entries(context)
        return forVersion(notes, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)
            ?: context.getString(R.string.release_notes_unavailable)
    }
    internal fun forVersion(notes: JSONArray, code: Int, name: String): String? =
        (0 until notes.length()).map { notes.getJSONObject(it) }
            .firstOrNull { it.getInt("versionCode") == code &&
                it.getString("versionName") == name.substringBefore('-') }?.getString("notes")
    fun history(context: Context): String {
        val notes = entries(context)
        return (0 until notes.length()).joinToString("\n\n") {
            val entry = notes.getJSONObject(it)
            "${entry.getString("versionName")} (${entry.getInt("versionCode")})\n${entry.getString("notes") }"
        }
    }
}
