package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.util.Log
import com.example.data.model.EqBandState
import com.example.data.model.EqPreset
import com.example.data.model.PlayableTrack
import com.example.data.model.SourceType
import com.example.widget.SongQWidgetBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.pow

enum class RepeatMode(val label: String) {
    OFF("Wył."),
    ALL("Wszystkie"),
    ONE("Jeden")
}

data class PlaybackUiState(
    val currentTrack: PlayableTrack? = null,
    val queue: List<PlayableTrack> = emptyList(),
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val waveformBars: List<Float> = List(28) { 0.15f },
    val statusMessage: String? = null,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val sleepTimerRemainingSec: Int = 0,
    val isDucked: Boolean = false
)

data class EqUiState(
    val enabled: Boolean = true,
    val activePresetName: String = "Studio Flat",
    val bands: List<EqBandState> = defaultEqBands(),
    val bassDb: Float = 0f,
    val midsDb: Float = 0f,
    val trebleDb: Float = 0f,
    val bassBoostStrength: Int = 250, // 0..1000
    val virtualizerStrength: Int = 200, // 0..1000
    val preampDb: Float = 0f, // -6..+6 dB
    val hardwareAttached: Boolean = false,
    val audioSessionId: Int = 0
)

fun defaultEqBands(): List<EqBandState> = listOf(
    EqBandState(0, 60, "60Hz", 0f),
    EqBandState(1, 230, "230Hz", 0f),
    EqBandState(2, 910, "910Hz", 0f),
    EqBandState(3, 3600, "3.6kHz", 0f),
    EqBandState(4, 14000, "14kHz", 0f)
)

object EqPresets {
    val all = listOf(
        EqPreset("Studio Flat", listOf(0f, 0f, 0f, 0f, 0f), 0f, 0f, 0f, 150, 100, 0f),
        EqPreset("Google+ Retro Warm", listOf(4.5f, 3.0f, 0.5f, 1.5f, 3.0f), 4.0f, 0.5f, 2.5f, 450, 320, 1.5f),
        EqPreset("Bass Boost Pro", listOf(9.0f, 5.5f, -1.0f, 1.5f, 3.5f), 8.5f, -1.0f, 2.5f, 850, 350, 2.0f),
        EqPreset("Vocal Clarity", listOf(-3.0f, 1.0f, 6.0f, 5.0f, 2.0f), -2.0f, 6.0f, 3.5f, 100, 250, 1.0f),
        EqPreset("Electronic / Synth", listOf(7.0f, 2.5f, -1.5f, 4.5f, 7.5f), 6.0f, -1.5f, 6.0f, 650, 500, 2.5f),
        EqPreset("Rock Arena", listOf(6.0f, 2.0f, -2.0f, 4.0f, 6.5f), 5.0f, -2.0f, 5.5f, 550, 420, 1.5f),
        EqPreset("Acoustic Hall", listOf(2.5f, 1.5f, 3.0f, 4.5f, 3.5f), 2.0f, 3.0f, 4.0f, 250, 680, 1.0f),
        EqPreset("DirexSoft Master", listOf(5.5f, 2.0f, 1.5f, 5.0f, 6.5f), 4.5f, 1.5f, 5.8f, 600, 450, 3.0f)
    )
}

class SongQAudioEngine(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _playback = MutableStateFlow(PlaybackUiState())
    val playback: StateFlow<PlaybackUiState> = _playback.asStateFlow()

    private val _eq = MutableStateFlow(EqUiState())
    val eq: StateFlow<EqUiState> = _eq.asStateFlow()

    private var audioTrack: AudioTrack? = null
    private var mediaPlayer: MediaPlayer? = null
    private var mfaPlayJob: Job? = null
    private var mediaTickerJob: Job? = null
    private var sleepTimerJob: Job? = null

    private var decodedPcm: ShortArray? = null
    private var decodedSampleRate: Int = 22050
    private var decodedChannels: Int = 1
    @Volatile private var mfaFrameCursor: Int = 0
    @Volatile private var isMfaPaused: Boolean = false
    @Volatile private var duckMultiplier: Float = 1.0f

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    var onPlaybackStateChanged: ((PlaybackUiState) -> Unit)? = null

    init {
        SongQWidgetBridge.onWidgetCommand = { action ->
            when (action) {
                SongQWidgetBridge.ACTION_PLAY_PAUSE -> togglePlayPause()
                SongQWidgetBridge.ACTION_NEXT -> skipNext()
                SongQWidgetBridge.ACTION_PREV -> skipPrevious()
            }
        }
    }

    private fun notifyPlaybackChanged() {
        onPlaybackStateChanged?.invoke(_playback.value)
    }

    fun setQueueAndPlay(tracks: List<PlayableTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val idx = startIndex.coerceIn(0, tracks.lastIndex)
        _playback.update { it.copy(queue = tracks) }
        playTrack(tracks[idx])
    }

    fun enqueueTrack(track: PlayableTrack) {
        _playback.update { state ->
            if (state.queue.any { it.id == track.id }) state
            else state.copy(queue = state.queue + track, statusMessage = "Dodano do kolejki: ${track.title}")
        }
        notifyPlaybackChanged()
    }

    fun removeFromQueue(trackId: String) {
        _playback.update { state ->
            state.copy(queue = state.queue.filterNot { it.id == trackId })
        }
        notifyPlaybackChanged()
    }

    fun playTrack(track: PlayableTrack) {
        stopInternal(clearTrack = false)
        _playback.update { state ->
            val updatedQueue = if (state.queue.any { it.id == track.id }) state.queue else state.queue + track
            state.copy(
                currentTrack = track,
                queue = updatedQueue,
                isPlaying = false,
                positionMs = 0L,
                durationMs = track.durationMs,
                statusMessage = null
            )
        }
        notifyPlaybackChanged()

        when (track.sourceType) {
            SourceType.MFA -> startMfaPlayback(track)
            SourceType.LOCAL, SourceType.ITUNES_PREVIEW -> startMediaPlayerPlayback(track)
            SourceType.WEB_LINK -> {
                _playback.update {
                    it.copy(
                        isPlaying = false,
                        statusMessage = "Link sieciowy (${track.codecLabel}) — otwórz zewnętrznie lub odtwórz podgląd"
                    )
                }
                syncWidgets()
                notifyPlaybackChanged()
            }
        }
    }

    private fun startMfaPlayback(track: PlayableTrack) {
        mfaPlayJob = scope.launch(Dispatchers.IO) {
            try {
                val decoded = if (track.uriOrUrl.startsWith("content://")) {
                    context.contentResolver.openInputStream(Uri.parse(track.uriOrUrl))?.use {
                        MfaCodec.decode(it)
                    } ?: throw IllegalStateException("Nie można otworzyć pliku .mfa")
                } else {
                    val file = File(track.uriOrUrl)
                    file.inputStream().use { MfaCodec.decode(it) }
                }

                decodedPcm = decoded.pcmSamples
                decodedSampleRate = decoded.metadata.sampleRate
                decodedChannels = decoded.metadata.channels.coerceIn(1, 2)
                mfaFrameCursor = 0
                isMfaPaused = false

                val channelConfig = if (decodedChannels == 2) {
                    AudioFormat.CHANNEL_OUT_STEREO
                } else {
                    AudioFormat.CHANNEL_OUT_MONO
                }
                val minBuf = AudioTrack.getMinBufferSize(
                    decodedSampleRate,
                    channelConfig,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(4096)

                val trackPlayer = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(decodedSampleRate)
                            .setChannelMask(channelConfig)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack = trackPlayer
                attachAudioEffects(trackPlayer.audioSessionId)
                trackPlayer.play()

                val durationMs = decoded.metadata.durationMs
                _playback.update {
                    it.copy(
                        isPlaying = true,
                        durationMs = durationMs,
                        positionMs = 0L
                    )
                }
                syncWidgets()
                notifyPlaybackChanged()

                val chunkSamples = 1024 * decodedChannels
                val outBuf = ShortArray(chunkSamples)
                val samples = decoded.pcmSamples
                val totalFrames = samples.size / decodedChannels

                while (isActive && mfaFrameCursor < totalFrames) {
                    if (isMfaPaused) {
                        delay(40)
                        continue
                    }
                    val startSample = mfaFrameCursor * decodedChannels
                    val remaining = samples.size - startSample
                    val count = minOf(chunkSamples, remaining)
                    if (count <= 0) break

                    val eqState = _eq.value
                    val preampMult = (if (eqState.enabled) 10f.pow(eqState.preampDb / 20f) else 1f) * duckMultiplier
                    for (i in 0 until count) {
                        val s = (samples[startSample + i] * preampMult).toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        outBuf[i] = s.toShort()
                    }
                    val written = trackPlayer.write(outBuf, 0, count)
                    if (written > 0) {
                        mfaFrameCursor += written / decodedChannels
                        val posMs = (mfaFrameCursor.toLong() * 1000L) / decodedSampleRate.toLong()
                        val bars = computeWaveformBars(outBuf, written)
                        _playback.update {
                            it.copy(positionMs = posMs, waveformBars = bars)
                        }
                    } else {
                        delay(20)
                    }
                }

                if (isActive && mfaFrameCursor >= totalFrames) {
                    scope.launch(Dispatchers.Main) { skipNext() }
                }
            } catch (e: Exception) {
                Log.e("SongQAudioEngine", "MFA playback failed", e)
                _playback.update {
                    it.copy(isPlaying = false, statusMessage = "Błąd dekodera .mfa: ${e.localizedMessage}")
                }
                syncWidgets()
                notifyPlaybackChanged()
            }
        }
    }

    private fun startMediaPlayerPlayback(track: PlayableTrack) {
        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                val target = track.uriOrUrl.trim()
                when {
                    target.startsWith("http://", ignoreCase = true) ||
                        target.startsWith("https://", ignoreCase = true) -> {
                        setDataSource(
                            context,
                            Uri.parse(target),
                            mapOf("User-Agent" to "Mozilla/5.0 (Android) SongQ/1.15")
                        )
                    }
                    target.startsWith("content://", ignoreCase = true) -> {
                        val uri = Uri.parse(target)
                        val pfd = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
                        if (pfd != null) {
                            pfd.use { setDataSource(it.fileDescriptor) }
                        } else {
                            setDataSource(context, uri)
                        }
                    }
                    target.startsWith("file://", ignoreCase = true) -> {
                        val filePath = Uri.parse(target).path ?: target.removePrefix("file://")
                        val file = File(filePath)
                        if (file.exists()) {
                            java.io.FileInputStream(file).use { fis -> setDataSource(fis.fd) }
                        } else {
                            setDataSource(context, Uri.parse(target))
                        }
                    }
                    else -> {
                        val file = File(target)
                        if (file.exists()) {
                            java.io.FileInputStream(file).use { fis -> setDataSource(fis.fd) }
                        } else {
                            setDataSource(target)
                        }
                    }
                }

                setOnPreparedListener { player ->
                    attachAudioEffects(player.audioSessionId)
                    player.setVolume(duckMultiplier, duckMultiplier)
                    player.start()
                    _playback.update {
                        it.copy(
                            isPlaying = true,
                            durationMs = player.duration.toLong().coerceAtLeast(track.durationMs),
                            positionMs = 0L
                        )
                    }
                    syncWidgets()
                    notifyPlaybackChanged()
                    startMediaTicker(player)
                }
                setOnCompletionListener {
                    skipNext()
                }
                setOnErrorListener { _, what, extra ->
                    _playback.update {
                        it.copy(isPlaying = false, statusMessage = "Błąd formatu audio ($what/$extra) — spróbuj innego źródła")
                    }
                    syncWidgets()
                    notifyPlaybackChanged()
                    true
                }
                prepareAsync()
            }
            mediaPlayer = mp
        } catch (e: Exception) {
            _playback.update {
                it.copy(isPlaying = false, statusMessage = "Nie można odtworzyć formatu: ${e.localizedMessage}")
            }
            syncWidgets()
            notifyPlaybackChanged()
        }
    }

    private fun startMediaTicker(player: MediaPlayer) {
        mediaTickerJob?.cancel()
        mediaTickerJob = scope.launch {
            var phase = 0
            while (isActive) {
                try {
                    if (player.isPlaying) {
                        phase++
                        val eqBoost = _eq.value.bands.map { (it.levelDb + 15f) / 30f }
                        val bars = List(28) { idx ->
                            val bandWeight = eqBoost[(idx * eqBoost.size) / 28]
                            val wave = 0.25f + 0.55f * abs(kotlin.math.sin((phase * 0.35 + idx * 0.6))).toFloat()
                            (wave * (0.6f + bandWeight * 0.6f)).coerceIn(0.08f, 1f)
                        }
                        _playback.update {
                            it.copy(
                                isPlaying = true,
                                positionMs = player.currentPosition.toLong(),
                                durationMs = player.duration.toLong().coerceAtLeast(it.durationMs),
                                waveformBars = bars
                            )
                        }
                    }
                } catch (_: IllegalStateException) {
                    break
                }
                delay(80)
            }
        }
    }

    fun togglePlayPause() {
        val current = _playback.value.currentTrack ?: run {
            val first = _playback.value.queue.firstOrNull() ?: return
            playTrack(first)
            return
        }
        if (_playback.value.isPlaying) {
            pausePlayback()
        } else {
            resumePlayback(current)
        }
    }

    fun pausePlayback() {
        val current = _playback.value.currentTrack ?: return
        if (current.sourceType == SourceType.MFA) {
            isMfaPaused = true
            runCatching { audioTrack?.pause() }
        } else {
            runCatching {
                if (mediaPlayer?.isPlaying == true) mediaPlayer?.pause()
            }
        }
        _playback.update { it.copy(isPlaying = false) }
        syncWidgets()
        notifyPlaybackChanged()
    }

    fun resumePlayback(fallbackTrack: PlayableTrack? = _playback.value.currentTrack) {
        val current = fallbackTrack ?: return
        if (current.sourceType == SourceType.MFA) {
            val track = audioTrack ?: run {
                playTrack(current)
                return
            }
            isMfaPaused = false
            track.play()
            _playback.update { it.copy(isPlaying = true) }
        } else {
            val mp = mediaPlayer ?: run {
                playTrack(current)
                return
            }
            mp.setVolume(duckMultiplier, duckMultiplier)
            mp.start()
            _playback.update { it.copy(isPlaying = true) }
        }
        syncWidgets()
        notifyPlaybackChanged()
    }

    fun stopPlayback() {
        cancelSleepTimer()
        stopInternal(clearTrack = false)
        _playback.update { it.copy(isPlaying = false, positionMs = 0L) }
        syncWidgets()
        notifyPlaybackChanged()
    }

    fun setDuckVolume(ducked: Boolean) {
        duckMultiplier = if (ducked) 0.25f else 1.0f
        runCatching { mediaPlayer?.setVolume(duckMultiplier, duckMultiplier) }
        _playback.update { it.copy(isDucked = ducked) }
    }

    fun toggleShuffle() {
        _playback.update { it.copy(shuffleEnabled = !it.shuffleEnabled) }
        notifyPlaybackChanged()
    }

    fun cycleRepeatMode() {
        val next = when (_playback.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _playback.update { it.copy(repeatMode = next) }
        notifyPlaybackChanged()
    }

    fun setSleepTimerMinutes(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _playback.update { it.copy(sleepTimerRemainingSec = 0) }
            notifyPlaybackChanged()
            return
        }
        var remaining = minutes * 60
        _playback.update { it.copy(sleepTimerRemainingSec = remaining) }
        notifyPlaybackChanged()
        sleepTimerJob = scope.launch {
            while (isActive && remaining > 0) {
                delay(1000L)
                remaining--
                _playback.update { it.copy(sleepTimerRemainingSec = remaining) }
                if (remaining == 0) {
                    pausePlayback()
                    _playback.update { it.copy(statusMessage = "Wyłącznik czasowy Song/Q zatrzymał odtwarzanie.") }
                }
            }
        }
    }

    fun cancelSleepTimer() {
        setSleepTimerMinutes(0)
    }

    fun seekTo(positionMs: Long) {
        val current = _playback.value.currentTrack ?: return
        if (current.sourceType == SourceType.MFA) {
            val pcm = decodedPcm ?: return
            val totalFrames = pcm.size / decodedChannels
            val targetFrame = ((positionMs.coerceAtLeast(0L) * decodedSampleRate) / 1000L).toInt()
                .coerceIn(0, (totalFrames - 1).coerceAtLeast(0))
            mfaFrameCursor = targetFrame
            _playback.update { it.copy(positionMs = positionMs) }
        } else {
            mediaPlayer?.let { mp ->
                try {
                    mp.seekTo(positionMs.toInt().coerceAtLeast(0))
                    _playback.update { it.copy(positionMs = positionMs) }
                } catch (_: Exception) {
                }
            }
        }
        notifyPlaybackChanged()
    }

    fun skipNext() {
        val state = _playback.value
        if (state.queue.isEmpty()) return
        if (state.repeatMode == RepeatMode.ONE && state.currentTrack != null) {
            playTrack(state.currentTrack)
            return
        }
        val curIdx = state.queue.indexOfFirst { it.id == state.currentTrack?.id }
        if (state.shuffleEnabled && state.queue.size > 1) {
            val candidates = state.queue.indices.filter { it != curIdx }
            playTrack(state.queue[candidates.random()])
            return
        }
        val nextIdx = curIdx + 1
        if (nextIdx >= state.queue.size) {
            if (state.repeatMode == RepeatMode.ALL) {
                playTrack(state.queue[0])
            } else {
                pausePlayback()
                seekTo(0L)
            }
        } else {
            playTrack(state.queue[nextIdx])
        }
    }

    fun skipPrevious() {
        val state = _playback.value
        if (state.queue.isEmpty()) return
        if (state.positionMs > 3000L) {
            seekTo(0L)
            return
        }
        val curIdx = state.queue.indexOfFirst { it.id == state.currentTrack?.id }
        val prevIdx = if (curIdx <= 0) state.queue.lastIndex else curIdx - 1
        playTrack(state.queue[prevIdx])
    }

    // --- Equalizer & Tone Sliders (Bass / Mids / Treble + 5-Band DSP) ---

    private fun attachAudioEffects(sessionId: Int) {
        if (sessionId == 0 || sessionId == AudioManager.ERROR) return
        releaseEffects()
        var attached = false
        try {
            val eqObj = Equalizer(0, sessionId)
            equalizer = eqObj
            attached = true
        } catch (e: Exception) {
            Log.w("SongQAudioEngine", "Equalizer unavailable on this session: ${e.message}")
        }
        try {
            bassBoost = BassBoost(0, sessionId)
        } catch (_: Exception) {
        }
        try {
            virtualizer = Virtualizer(0, sessionId)
        } catch (_: Exception) {
        }
        _eq.update { it.copy(hardwareAttached = attached, audioSessionId = sessionId) }
        applyCurrentEqToHardware()
    }

    fun applyEqState(
        enabled: Boolean = _eq.value.enabled,
        presetName: String = _eq.value.activePresetName,
        bandGainsDb: List<Float> = _eq.value.bands.map { it.levelDb },
        bassDb: Float = _eq.value.bassDb,
        midsDb: Float = _eq.value.midsDb,
        trebleDb: Float = _eq.value.trebleDb,
        bassBoostStrength: Int = _eq.value.bassBoostStrength,
        virtualizerStrength: Int = _eq.value.virtualizerStrength,
        preampDb: Float = _eq.value.preampDb
    ) {
        val updatedBands = _eq.value.bands.mapIndexed { idx, band ->
            band.copy(levelDb = bandGainsDb.getOrElse(idx) { band.levelDb }.coerceIn(-15f, 15f))
        }
        _eq.value = _eq.value.copy(
            enabled = enabled,
            activePresetName = presetName,
            bands = updatedBands,
            bassDb = bassDb.coerceIn(-15f, 15f),
            midsDb = midsDb.coerceIn(-15f, 15f),
            trebleDb = trebleDb.coerceIn(-15f, 15f),
            bassBoostStrength = bassBoostStrength.coerceIn(0, 1000),
            virtualizerStrength = virtualizerStrength.coerceIn(0, 1000),
            preampDb = preampDb.coerceIn(-6f, 6f)
        )
        applyCurrentEqToHardware()
    }

    fun selectPreset(preset: EqPreset) {
        applyEqState(
            enabled = true,
            presetName = preset.name,
            bandGainsDb = preset.bandGainsDb,
            bassDb = preset.bassDb,
            midsDb = preset.midsDb,
            trebleDb = preset.trebleDb,
            bassBoostStrength = preset.bassBoostStrength,
            virtualizerStrength = preset.virtualizerStrength,
            preampDb = preset.preampDb
        )
    }

    fun updateToneSliders(
        bassDb: Float = _eq.value.bassDb,
        midsDb: Float = _eq.value.midsDb,
        trebleDb: Float = _eq.value.trebleDb
    ) {
        val b = bassDb.coerceIn(-15f, 15f)
        val m = midsDb.coerceIn(-15f, 15f)
        val t = trebleDb.coerceIn(-15f, 15f)
        val mappedBands = listOf(
            b,
            (b * 0.72f + m * 0.28f).coerceIn(-15f, 15f),
            m,
            (m * 0.28f + t * 0.72f).coerceIn(-15f, 15f),
            t
        )
        val derivedBassBoost = (((b + 15f) / 30f) * 900f).toInt().coerceIn(0, 1000)
        applyEqState(
            enabled = true,
            presetName = "Własny (Tone)",
            bandGainsDb = mappedBands,
            bassDb = b,
            midsDb = m,
            trebleDb = t,
            bassBoostStrength = derivedBassBoost
        )
    }

    fun updateBandGain(bandIndex: Int, gainDb: Float) {
        val current = _eq.value
        val newGains = current.bands.mapIndexed { idx, band ->
            if (idx == bandIndex) gainDb.coerceIn(-15f, 15f) else band.levelDb
        }
        applyEqState(
            presetName = "Własny (5-Band)",
            bandGainsDb = newGains,
            bassDb = ((newGains[0] + newGains[1]) / 2f).coerceIn(-15f, 15f),
            midsDb = newGains[2].coerceIn(-15f, 15f),
            trebleDb = ((newGains[3] + newGains[4]) / 2f).coerceIn(-15f, 15f)
        )
    }

    private fun applyCurrentEqToHardware() {
        val state = _eq.value
        try {
            equalizer?.let { eqObj ->
                eqObj.enabled = state.enabled
                val bandCount = eqObj.numberOfBands.toInt()
                val range = eqObj.bandLevelRange
                val minMb = range[0].toInt()
                val maxMb = range[1].toInt()
                for (i in 0 until bandCount) {
                    val gainDb = state.bands.getOrElse(i) { state.bands.last() }.levelDb
                    val targetMb = (gainDb * 100f).toInt().coerceIn(minMb, maxMb).toShort()
                    eqObj.setBandLevel(i.toShort(), targetMb)
                }
            }
        } catch (e: Exception) {
            Log.w("SongQAudioEngine", "applyEq error: ${e.message}")
        }
        try {
            bassBoost?.let { bb ->
                bb.enabled = state.enabled && state.bassBoostStrength > 0
                if (bb.strengthSupported) {
                    bb.setStrength(state.bassBoostStrength.toShort())
                }
            }
        } catch (_: Exception) {
        }
        try {
            virtualizer?.let { virt ->
                virt.enabled = state.enabled && state.virtualizerStrength > 0
                if (virt.strengthSupported) {
                    virt.setStrength(state.virtualizerStrength.toShort())
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun computeWaveformBars(samples: ShortArray, count: Int): List<Float> {
        val barCount = 28
        val step = (count / barCount).coerceAtLeast(1)
        return List(barCount) { idx ->
            var peak = 0
            val start = idx * step
            val end = minOf(count, start + step)
            for (j in start until end) {
                val amp = abs(samples[j].toInt())
                if (amp > peak) peak = amp
            }
            (peak / 24000f).coerceIn(0.08f, 1f)
        }
    }

    private fun syncWidgets() {
        val state = _playback.value
        val track = state.currentTrack
        SongQWidgetBridge.saveAndRefreshPlayerWidgets(
            context = context,
            title = track?.title ?: "Song/Q Studio Deck",
            subtitle = if (track != null) "${track.artist} • ${track.codecLabel}" else "MFA/PCM-Z • DirexSoft v1.15",
            isPlaying = state.isPlaying
        )
    }

    private fun releaseEffects() {
        try { equalizer?.release() } catch (_: Exception) {}
        try { bassBoost?.release() } catch (_: Exception) {}
        try { virtualizer?.release() } catch (_: Exception) {}
        equalizer = null
        bassBoost = null
        virtualizer = null
    }

    private fun stopInternal(clearTrack: Boolean) {
        mfaPlayJob?.cancel()
        mfaPlayJob = null
        mediaTickerJob?.cancel()
        mediaTickerJob = null
        releaseEffects()
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        _eq.update { it.copy(hardwareAttached = false, audioSessionId = 0) }
        if (clearTrack) {
            _playback.update { it.copy(currentTrack = null, isPlaying = false, positionMs = 0L) }
        }
    }

    fun release() {
        stopInternal(clearTrack = true)
    }
}
