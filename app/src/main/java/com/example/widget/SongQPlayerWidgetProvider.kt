package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R

class SongQPlayerWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            SongQWidgetBridge.ACTION_PLAY_PAUSE,
            SongQWidgetBridge.ACTION_NEXT,
            SongQWidgetBridge.ACTION_PREV -> {
                val callback = SongQWidgetBridge.onWidgetCommand
                if (callback != null) {
                    callback(intent.action!!)
                } else {
                    val serviceAction = when (intent.action) {
                        SongQWidgetBridge.ACTION_PLAY_PAUSE -> com.example.audio.SongQPlaybackService.ACTION_PLAY_PAUSE
                        SongQWidgetBridge.ACTION_NEXT -> com.example.audio.SongQPlaybackService.ACTION_NEXT
                        else -> com.example.audio.SongQPlaybackService.ACTION_PREV
                    }
                    val svcIntent = Intent(context, com.example.audio.SongQPlaybackService::class.java).apply {
                        action = serviceAction
                    }
                    runCatching {
                        androidx.core.content.ContextCompat.startForegroundService(context, svcIntent)
                    }
                }
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val (title, subtitle, isPlaying) = SongQWidgetBridge.readState(context)
        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_songq_player)
            views.setTextViewText(R.id.widget_track_title, title)
            views.setTextViewText(R.id.widget_track_subtitle, subtitle)
            views.setImageViewResource(
                R.id.widget_btn_play,
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openAppPending = PendingIntent.getActivity(
                context, 10, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_logo, openAppPending)
            views.setOnClickPendingIntent(R.id.widget_track_title, openAppPending)

            views.setOnClickPendingIntent(
                R.id.widget_btn_prev,
                broadcastPending(context, SongQWidgetBridge.ACTION_PREV, 11)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_play,
                broadcastPending(context, SongQWidgetBridge.ACTION_PLAY_PAUSE, 12)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_next,
                broadcastPending(context, SongQWidgetBridge.ACTION_NEXT, 13)
            )

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    private fun broadcastPending(context: Context, action: String, reqCode: Int): PendingIntent {
        val intent = Intent(context, SongQPlayerWidgetProvider::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
