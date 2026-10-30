package com.music.bitchord.data.settings

import android.content.SharedPreferences
import com.music.bitchord.playback.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ln

/** Stored in the existing preference file so export/import carries these settings too. */
object TotalMusicSettings {
    private lateinit var prefs: SharedPreferences
    val expressive = MutableStateFlow(false)
    val artworkColors = MutableStateFlow(false)
    val adaptiveGlass = MutableStateFlow(false)
    val accent = MutableStateFlow(0xFF0B57D0.toInt())
    val config = MutableStateFlow(TotalEqConfig())
    val profiles = MutableStateFlow<Map<String, TotalEqConfig>>(emptyMap())
    val sampleRate = MutableStateFlow(48000)
    /** A/B monitor only. False = saved processing; true = untouched EQ input. Not persisted. */
    val compareOriginal = MutableStateFlow(false)
    fun setCompareOriginal(value: Boolean) { compareOriginal.value = value }

    fun init(preferences: SharedPreferences) {
        prefs = preferences
        artworkColors.value = prefs.getBoolean("google_artwork_colors", false)
        adaptiveGlass.value = prefs.getBoolean("adaptive_glass", false)
        expressive.value = prefs.getBoolean("google_expressive", false)
        accent.value = prefs.getInt("google_accent", 0xFF0B57D0.toInt())
        config.value = if (!prefs.contains("total_eq")) TotalEqConfig(graphicMode = TotalGraphicMode.TEN)
            else runCatching { decode(JSONObject(prefs.getString("total_eq", "{}")!!)) }.getOrDefault(TotalEqConfig())
        profiles.value = runCatching {
            val json = JSONObject(prefs.getString("total_eq_profiles", "{}")!!)
            json.keys().asSequence().take(64).associateWith { decode(json.getJSONObject(it)) }
        }.getOrDefault(emptyMap())
    }
    fun setArtworkColors(value: Boolean) {
        artworkColors.value = value
        prefs.edit().putBoolean("google_artwork_colors", value).apply()
    }
    fun setAdaptiveGlass(value: Boolean) {
        adaptiveGlass.value = value
        prefs.edit().putBoolean("adaptive_glass", value).apply()
    }
    fun setAccent(value: Int) {
        accent.value = value or 0xFF000000.toInt()
        prefs.edit().putInt("google_accent", accent.value).apply()
    }
    fun setExpressive(value: Boolean) {
        expressive.value = value
        prefs.edit().putBoolean("google_expressive", value).apply()
    }
    fun update(value: TotalEqConfig) {
        config.value = value.sanitized()
        prefs.edit().putString("total_eq", encode(config.value).toString()).apply()
    }
    fun saveProfile(name: String) {
        val key = name.trim().take(48)
        if (key.isEmpty() || (key !in profiles.value && profiles.value.size >= 64)) return
        profiles.value = profiles.value + (key to config.value)
        persistProfiles()
    }
    fun renameProfile(oldName: String, newName: String): Boolean {
        val key = newName.trim().take(48)
        if (key.isEmpty() || oldName !in profiles.value) return false
        if (key != oldName && (key in profiles.value || key in factoryProfiles)) return false
        profiles.value = profiles.value.entries.associate { (name, value) ->
            (if (name == oldName) key else name) to value
        }
        persistProfiles()
        return true
    }
    fun deleteProfile(name: String) {
        profiles.value = profiles.value - name
        persistProfiles()
    }
    private fun persistProfiles() {
        val json = JSONObject()
        profiles.value.forEach { (name, value) -> json.put(name, encode(value)) }
        prefs.edit().putString("total_eq_profiles", json.toString()).apply()
    }
    private fun encode(c: TotalEqConfig): JSONObject = JSONObject().apply {
        put("graphicMode", c.graphicMode.name); put("tenBands", JSONArray(c.tenBands))
        put("bands", JSONArray(c.bands)); put("preamp", c.preamp); put("bass", c.bass); put("treble", c.treble)
        put("balance", c.balance); put("width", c.width); put("crossfeed", c.crossfeed)
        put("autoHeadroom", c.autoHeadroom); put("limiter", c.limiter)
        put("filters", JSONArray().apply { c.filters.forEach { f -> put(JSONObject().apply {
            put("type", f.type.name); put("frequency", f.frequency); put("gain", f.gain); put("q", f.q); put("enabled", f.enabled)
        }) } })
    }
    private fun decode(j: JSONObject): TotalEqConfig {
        val bands = j.optJSONArray("bands")
        val filters = j.optJSONArray("filters")
        val tenBands = j.optJSONArray("tenBands")
        return TotalEqConfig(
            graphicMode = runCatching { TotalGraphicMode.valueOf(j.optString("graphicMode")) }.getOrDefault(TotalGraphicMode.THIRTY_ONE),
            tenBands = List(10) { tenBands?.optDouble(it, 0.0)?.toFloat() ?: 0f },
            bands = List(31) { bands?.optDouble(it, 0.0)?.toFloat() ?: 0f },
            filters = List(minOf(filters?.length() ?: 0, 8)) { i ->
                val f = filters!!.getJSONObject(i)
                TotalFilter(runCatching { TotalFilterType.valueOf(f.optString("type")) }.getOrDefault(TotalFilterType.PEAK),
                    f.optDouble("frequency", 1000.0).toFloat(), f.optDouble("gain", 0.0).toFloat(),
                    f.optDouble("q", 0.707).toFloat(), f.optBoolean("enabled", true))
            },
            preamp = j.optDouble("preamp", 0.0).toFloat(), bass = j.optDouble("bass", 0.0).toFloat(),
            treble = j.optDouble("treble", 0.0).toFloat(), balance = j.optDouble("balance", 0.0).toFloat(),
            width = j.optDouble("width", 1.0).toFloat(), crossfeed = j.optDouble("crossfeed", 0.0).toFloat(),
            autoHeadroom = j.optBoolean("autoHeadroom", true), limiter = j.optBoolean("limiter", true),
        ).sanitized()
    }

    val factoryProfiles: Map<String, TotalEqConfig> = linkedMapOf(
        "Flat" to listOf(0f,0f,0f,0f,0f,0f,0f),
        "Bass Boost" to listOf(6f,4f,1f,0f,0f,0f,0f),
        "Deep Bass" to listOf(9f,5f,0f,-1f,0f,1f,0f),
        "Rock" to listOf(5f,3f,-2f,-1f,2f,4f,3f),
        "Pop" to listOf(1f,2f,0f,3f,3f,1f,0f),
        "Jazz" to listOf(3f,2f,0f,1f,2f,2f,3f),
        "Clásica" to listOf(2f,1f,0f,-1f,0f,2f,3f),
        "Electrónica" to listOf(6f,4f,-1f,0f,2f,4f,4f),
        "Vocal" to listOf(-3f,-2f,1f,4f,3f,0f,-1f),
        "Acústica" to listOf(1f,1f,2f,2f,2f,3f,2f),
        "Hip-Hop" to listOf(7f,5f,1f,0f,2f,1f,2f),
    ).mapValues { (_, gains) ->
        val anchors = listOf(20f,80f,315f,1000f,3150f,8000f,20000f)
        fun gainsAt(frequencies: List<Float>) = frequencies.map { hz ->
            val i = (0..5).firstOrNull { hz <= anchors[it + 1] } ?: 5
            val t = (ln(hz / anchors[i]) / ln(anchors[i + 1] / anchors[i])).coerceIn(0f, 1f)
            gains[i] + (gains[i + 1] - gains[i]) * t
        }
        TotalEqConfig(bands = gainsAt(TotalEqDesign.frequencies), tenBands = gainsAt(TotalEqDesign.tenFrequencies))
    }
}
