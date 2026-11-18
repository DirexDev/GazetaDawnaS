package com.example.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.example.MainActivity
import com.example.R

/**
 * Zaawansowana usługa odtwarzania w tle Song/Q (mediaPlayback foreground service):
 * - MediaSession (ekran blokady, Bluetooth, Android Auto, przyciski multimedialne)
 * - Notification.MediaStyle z akcjami: Poprzedni, Pauza/Play, Następny, Zatrzymaj
 * - AudioFocusRequest + wyciszanie (ducking) przy powiadomieniach i automatyczna pauza przy rozmowach
 * - Ochrona ACTION_AUDIO_BECOMING_NOISY (pauza po odłączeniu słuchawek/BT)
 * - PARTIAL_WAKE_LOCK + WifiLock na czas aktywnego odtwarzania w tle
 */
class SongQPlaybackService : Service() {

    inner class LocalBinder : Binder() {
        val engine: SongQAudioEngine
            get() = audioEngine
    }

    private val binder = LocalBinder()
    lateinit var audioEngine: SongQAudioEngine
        private set

    private lateinit var mediaSession: MediaSession
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var pausedByTransientAudioFocusLoss = false
    private var noisyReceiverRegistered = false

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                audioEngine.pausePlayback()
            }
        }
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                audioEngine.setDuckVolume(false)
                if (pausedByTransientAudioFocusLoss) {
                    pausedByTransientAudioFocusLoss = false
                    audioEngine.resumePlayback()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                pausedByTransientAudioFocusLoss = false
                audioEngine.pausePlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (audioEngine.playback.value.isPlaying) {
                    pausedByTransientAudioFocusLoss = true
                    audioEngine.pausePlayback()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                audioEngine.setDuckVolume(true)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        audioManager = getSystemService(AudioManager::class.java)

        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SongQ::BackgroundAudioLock")?.apply {
            setReferenceCounted(false)
        }
        val wifiMgr = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION")
        wifiLock = wifiMgr?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "SongQ::StreamWifiLock")?.apply {
            setReferenceCounted(false)
        }

        mediaSession = MediaSession(this, "SongQMediaSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    if (requestAudioFocus()) {
                        audioEngine.resumePlayback()
                    }
                }

                override fun onPause() {
                    audioEngine.pausePlayback()
                }

                override fun onSkipToNext() {
                    audioEngine.skipNext()
                }

                override fun onSkipToPrevious() {
                    audioEngine.skipPrevious()
                }

                override fun onSeekTo(pos: Long) {
                    audioEngine.seekTo(pos)
                }

                override fun onStop() {
                    stopBackgroundSession()
                }
            })
            isActive = true
        }

        audioEngine = SongQAudioEngine(applicationContext).apply {
            onPlaybackStateChanged = { state ->
                handlePlaybackUpdate(state)
            }
        }

        if (!noisyReceiverRegistered) {
            registerReceiver(
                becomingNoisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            )
            noisyReceiverRegistered = true
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                if (!audioEngine.playback.value.isPlaying) {
                    requestAudioFocus()
                }
                audioEngine.togglePlayPause()
            }
            ACTION_NEXT -> {
                requestAudioFocus()
                audioEngine.skipNext()
            }
            ACTION_PREV -> {
                requestAudioFocus()
                audioEngine.skipPrevious()
            }
            ACTION_STOP -> {
                stopBackgroundSession()
                return START_NOT_STICKY
            }
        }
        handlePlaybackUpdate(audioEngine.playback.value)
        return START_STICKY
    }

    private fun stopBackgroundSession() {
        audioEngine.stopPlayback()
        abandonAudioFocus()
        releaseLocks()
        mediaSession.isActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handlePlaybackUpdate(state: PlaybackUiState) {
        if (state.isPlaying) {
            requestAudioFocus()
            acquireLocks()
            mediaSession.isActive = true
        } else {
            releaseLocks()
        }
        updateMediaSessionState(state)
        updateNotification(state)
    }

    private fun updateMediaSessionState(state: PlaybackUiState) {
        val track = state.currentTrack
        val pbState = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackState.ACTION_SEEK_TO or
                    PlaybackState.ACTION_STOP
            )
            .setState(
                when {
                    state.isPlaying -> PlaybackState.STATE_PLAYING
                    track != null -> PlaybackState.STATE_PAUSED
                    else -> PlaybackState.STATE_STOPPED
                },
                state.positionMs,
                if (state.isPlaying) 1.0f else 0f
            )
            .build()
        mediaSession.setPlaybackState(pbState)

        if (track != null) {
            val defaultArt = runCatching {
                BitmapFactory.decodeResource(resources, R.drawable.img_album_placeholder_1794959043577)
            }.getOrNull()
            val meta = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album.ifBlank { "Song/Q DirexSoft" })
                .putLong(MediaMetadata.METADATA_KEY_DURATION, state.durationMs.coerceAtLeast(track.durationMs))
                .apply {
                    if (defaultArt != null) putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, defaultArt)
                }
                .build()
            mediaSession.setMetadata(meta)
        }
    }

    private fun updateNotification(state: PlaybackUiState) {
        val track = state.currentTrack
        val timerSuffix = if (state.sleepTimerRemainingSec > 0) {
            " • Timer ${state.sleepTimerRemainingSec / 60}m"
        } else ""
        val notification = buildMediaStyleNotification(
            title = track?.title ?: "Song/Q Studio Deck",
            subtitle = if (track != null) {
                "${track.artist} • EQ: ${audioEngine.eq.value.activePresetName}$timerSuffix"
            } else {
                "DirexSoft Audio Engine • Gotowy"
            },
            isPlaying = state.isPlaying
        )

        if (state.isPlaying) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        } else if (track != null) {
            runCatching {
                getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun buildMediaStyleNotification(
        title: String,
        subtitle: String,
        isPlaying: Boolean
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = runCatching {
            BitmapFactory.decodeResource(resources, R.drawable.img_album_placeholder_1794959043577)
        }.getOrNull()

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val mediaStyle = Notification.MediaStyle()
            .setMediaSession(mediaSession.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        return builder
            .setSmallIcon(R.drawable.ic_songq_widget_logo)
            .apply { if (largeIcon != null) setLargeIcon(largeIcon) }
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSubText("Song/Q Play Music Engine")
            .setContentIntent(contentIntent)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setStyle(mediaStyle)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_previous,
                    "Poprzedni",
                    servicePending(ACTION_PREV, 201)
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (isPlaying) "Pauza" else "Odtwarzaj",
                    servicePending(ACTION_PLAY_PAUSE, 202)
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_next,
                    "Następny",
                    servicePending(ACTION_NEXT, 203)
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Zatrzymaj",
                    servicePending(ACTION_STOP, 204)
                ).build()
            )
            .build()
    }

    private fun servicePending(action: String, reqCode: Int): PendingIntent {
        val intent = Intent(this, SongQPlaybackService::class.java).apply {
            this.action = action
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && action != ACTION_STOP) {
            PendingIntent.getForegroundService(this, reqCode, intent, flags)
        } else {
            PendingIntent.getService(this, reqCode, intent, flags)
        }
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
            audioFocusRequest = req
            audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
    }

    private fun acquireLocks() {
        runCatching {
            if (wakeLock?.isHeld == false) wakeLock?.acquire(60 * 60 * 1000L)
            if (wifiLock?.isHeld == false) wifiLock?.acquire()
        }
    }

    private fun releaseLocks() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm?.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Odtwarzacz Song/Q",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Sterowanie odtwarzaniem audio i korektorem EQ Song/Q w tle"
                }
                nm?.createNotificationChannel(channel)
            }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // Utrzymuj zaawansowaną usługę odtwarzania w tle nawet po zminimalizowaniu/odłączeniu aktywności
        if (!audioEngine.playback.value.isPlaying && audioEngine.playback.value.currentTrack == null) {
            stopSelf()
        }
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        if (noisyReceiverRegistered) {
            runCatching { unregisterReceiver(becomingNoisyReceiver) }
            noisyReceiverRegistered = false
        }
        abandonAudioFocus()
        releaseLocks()
        runCatching { mediaSession.release() }
        audioEngine.release()
    }

    companion object {
        const val CHANNEL_ID = "songq_playback_channel"
        const val NOTIFICATION_ID = 1150
        const val ACTION_PLAY_PAUSE = "com.example.songq.SERVICE_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.songq.SERVICE_NEXT"
        const val ACTION_PREV = "com.example.songq.SERVICE_PREV"
        const val ACTION_STOP = "com.example.songq.SERVICE_STOP"
    }
}

