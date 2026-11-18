package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.model.SearchEngineType

class SongQSearchWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_songq_search)
            views.setOnClickPendingIntent(
                R.id.widget_search_bar,
                launchSearchPending(context, SearchEngineType.ITUNES, 100)
            )
            views.setOnClickPendingIntent(
                R.id.widget_chip_yt,
                launchSearchPending(context, SearchEngineType.YOUTUBE, 101)
            )
            views.setOnClickPendingIntent(
                R.id.widget_chip_ytm,
                launchSearchPending(context, SearchEngineType.YOUTUBE_MUSIC, 102)
            )
            views.setOnClickPendingIntent(
                R.id.widget_chip_web,
                launchSearchPending(context, SearchEngineType.ITUNES, 103)
            )
            views.setOnClickPendingIntent(
                R.id.widget_chip_spotify,
                launchSearchPending(context, SearchEngineType.SPOTIFY, 104)
            )
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    private fun launchSearchPending(
        context: Context,
        engine: SearchEngineType,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(SongQWidgetBridge.EXTRA_START_TAB, "search")
            putExtra(SongQWidgetBridge.EXTRA_SEARCH_ENGINE, engine.name)
            putExtra(SongQWidgetBridge.EXTRA_FOCUS_SEARCH, true)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
