package com.music.bitchord.widget

import android.content.Context

/** Per-instance appearance choices for home-screen widgets. */
internal enum class WidgetThemeMode { SYSTEM, LIGHT, DARK }

internal object MediaWidgetPreferences {
    fun theme(context: Context, widgetId: Int): WidgetThemeMode {
        val stored = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(key(widgetId), WidgetThemeMode.SYSTEM.name)
        return runCatching { WidgetThemeMode.valueOf(stored ?: WidgetThemeMode.SYSTEM.name) }
            .getOrDefault(WidgetThemeMode.SYSTEM)
    }

    fun setTheme(context: Context, widgetId: Int, value: WidgetThemeMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(key(widgetId), value.name).apply()
    }

    fun delete(context: Context, widgetIds: IntArray) {
        val editor = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
        widgetIds.forEach { editor.remove(key(it)) }
        editor.apply()
    }

    private fun key(widgetId: Int) = "theme_$widgetId"
    private const val FILE = "total_music_widget_preferences"
}
