package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

object SongQWidgetBridge {
    const val ACTION_PLAY_PAUSE = "com.example.songq.WIDGET_PLAY_PAUSE"
    const val ACTION_NEXT = "com.example.songq.WIDGET_NEXT"
    const val ACTION_PREV = "com.example.songq.WIDGET_PREV"

    const val EXTRA_START_TAB = "extra_start_tab"
    const val EXTRA_SEARCH_ENGINE = "extra_search_engine"
    const val EXTRA_FOCUS_SEARCH = "extra_focus_search"

    private const val PREFS_NAME = "songq_widget_state"
    private const val KEY_TITLE = "title"
    private const val KEY_SUBTITLE = "subtitle"
    private const val KEY_IS_PLAYING = "is_playing"

    var onWidgetCommand: ((String) -> Unit)? = null

    fun saveAndRefreshPlayerWidgets(
        context: Context,
        title: String,
        subtitle: String,
        isPlaying: Boolean
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_TITLE, title)
            .putString(KEY_SUBTITLE, subtitle)
            .putBoolean(KEY_IS_PLAYING, isPlaying)
            .apply()

        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, SongQPlayerWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isNotEmpty()) {
            val intent = Intent(context, SongQPlayerWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }

    fun readState(context: Context): Triple<String, String, Boolean> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val title = prefs.getString(KEY_TITLE, "Song/Q Studio Deck") ?: "Song/Q Studio Deck"
        val subtitle = prefs.getString(KEY_SUBTITLE, "MFA/PCM-Z • DirexSoft v1.15") ?: "MFA/PCM-Z • DirexSoft v1.15"
        val playing = prefs.getBoolean(KEY_IS_PLAYING, false)
        return Triple(title, subtitle, playing)
    }
}
