package com.music.bitchord.data.lyrics

import android.content.Context
import android.util.AtomicFile
import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Local English/Spanish lyric translation. Original timestamps remain authoritative;
 * any failure returns Unavailable so the caller can fall back without replacing the
 * original lyric with a partial translation.
 */
class OfflineLyricsEngine(private val context: Context) {
    val downloading = MutableStateFlow(false)
    private val identifier by lazy { LanguageIdentification.getClient() }
    private val models = RemoteModelManager.getInstance()
    private val translators = mutableMapOf<String, Translator>()

    suspend fun identify(text: String): String? = runCatching {
        val tag = identifier.identifyLanguage(text.take(10_000)).awaitTask()
        tag.takeIf { it == TranslateLanguage.ENGLISH || it == TranslateLanguage.SPANISH }
    }.getOrNull()

    suspend fun translate(
        trackId: String,
        lines: List<LyricLine>,
        targetLanguageTag: String,
    ): LyricsTranslation.Result = withContext(Dispatchers.IO) {
        val target = when (targetLanguageTag.lowercase().substringBefore('-')) {
            "en" -> TranslateLanguage.ENGLISH
            "es" -> TranslateLanguage.SPANISH
            else -> return@withContext LyricsTranslation.Result.Unavailable
        }
        val sample = lines.firstOrNull { it.text.isNotBlank() }?.text ?: return@withContext LyricsTranslation.Result.Unavailable
        val source = identify(sample) ?: return@withContext LyricsTranslation.Result.Unavailable
        if (source == target) return@withContext LyricsTranslation.Result.SameLanguage(source)
        val key = cacheKey(trackId, source, target, lines)
        readCache(key, lines)?.let { return@withContext LyricsTranslation.Result.Translated(it, source, true) }
        val sourceModel = TranslateRemoteModel.Builder(source).build()
        val targetModel = TranslateRemoteModel.Builder(target).build()
        val ready = runCatching {
            models.isModelDownloaded(sourceModel).awaitTask() && models.isModelDownloaded(targetModel).awaitTask()
        }.getOrDefault(false)
        if (!ready) {
            // null means no active network; true means metered. Neither may start a model download.
            if (AppSettings.meteredConnection.value != false) return@withContext LyricsTranslation.Result.Unavailable
            downloading.value = true
            try {
                val conditions = DownloadConditions.Builder().requireWifi().build()
                models.download(sourceModel, conditions).awaitTask()
                models.download(targetModel, conditions).awaitTask()
            } catch (_: Exception) {
                return@withContext LyricsTranslation.Result.Unavailable
            } finally {
                downloading.value = false
            }
        }
        runCatching {
            val translator = translators.getOrPut("$source-$target") {
                Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())
            }
            val texts = lines.map { if (it.text.isBlank()) it.text else translator.translate(it.text).awaitTask() }
            if (texts.zip(lines).any { (translated, original) -> original.text.isNotBlank() && translated.isBlank() }) {
                return@runCatching LyricsTranslation.Result.Unavailable
            }
            val rebuilt = rebuildOfflineTranslation(lines, texts)
            writeCache(key, rebuilt)
            LyricsTranslation.Result.Translated(rebuilt, source, false)
        }.getOrDefault(LyricsTranslation.Result.Unavailable)
    }

    private fun cacheFile(key: String) = AtomicFile(File(File(context.filesDir, "lyrics-translation-offline-v1").apply { mkdirs() }, "$key.json"))
    private fun readCache(key: String, original: List<LyricLine>): List<LyricLine>? = runCatching {
        val json = JSONObject(cacheFile(key).openRead().bufferedReader().use { it.readText() })
        val texts = json.getJSONArray("texts")
        val originals = json.getJSONArray("times")
        if (texts.length() != originals.length()) return null
        // Cache is only used through translate(), which has the matching original lines.
        // Store texts alongside timestamps so a changed lyric file cannot reuse stale words.
        if (original.size != texts.length()) return null
        original.mapIndexed { i, line ->
            if (line.timeMs != originals.getLong(i)) return null
            rebuildOfflineTranslation(listOf(line), listOf(texts.getString(i))).first()
        }
    }.getOrNull()
    private fun writeCache(key: String, lines: List<LyricLine>) = runCatching {
        val json = JSONObject().put("texts", JSONArray(lines.map { it.text })).put("times", JSONArray(lines.map { it.timeMs }))
        val file = cacheFile(key)
        val stream = file.startWrite()
        try {
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    private fun cacheKey(trackId: String, source: String, target: String, lines: List<LyricLine>): String {
        val input = buildString {
            append(trackId).append('\u0000').append(source).append('\u0000').append(target)
            lines.forEach { append('\u0000').append(it.timeMs).append('\u0000').append(it.text) }
        }
        return MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

/** Preserve line timing and the original vocal envelope; never invent translated syllable timings. */
internal fun rebuildOfflineTranslation(original: List<LyricLine>, translated: List<String>): List<LyricLine> =
    original.mapIndexed { index, line ->
        val text = translated.getOrNull(index) ?: line.text
        line.copy(
            text = text,
            words = if (line.words.isEmpty() || text.isEmpty()) emptyList() else listOf(
                LyricWord(line.words.first().startMs, line.words.last().endMs, text)
            ),
            timingSource = line.takeIf { it.isWordSynced },
        )
    }

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
