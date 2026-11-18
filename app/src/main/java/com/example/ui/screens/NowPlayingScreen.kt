package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.audio.EqUiState
import com.example.audio.PlaybackUiState
import com.example.audio.RepeatMode
import com.example.data.model.PlayableTrack
import com.example.data.search.MusicAppLinker
import com.example.ui.components.CodecBadge
import com.example.ui.components.GPlusFlatCard
import com.example.ui.components.SongQFileLogoIcon
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.GPlusAmber
import com.example.ui.theme.GPlusBlue
import com.example.ui.theme.GpmCoral
import com.example.ui.theme.GpmOrange
import kotlin.math.roundToInt

/**
 * Pełnoekranowy odtwarzacz Song/Q w estetyce Google Play Music z obsługą gestów i trybów tła:
 * - Przesunięcie poziome na okładce: Następny / Poprzedni utwór
 * - Podwójne stuknięcie na okładce: Odtwarzaj / Pauza
 * - Przesunięcie w dół na górnym panelu: Minimalizacja
 * - Dotyk po widmie fali: Płynne przewijanie (scrubbing)
 * - Przesunięcie pionowe po okładce: Regulacja Bass Boost
 * - Shuffle, Repeat (Wył./Wszystkie/Jeden), Wyłącznik czasowy i wybór aplikacji muzycznej
 */
@Composable
fun NowPlayingScreen(
    playbackState: PlaybackUiState,
    eqState: EqUiState,
    playMusicMode: Boolean = true,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSetSleepTimerMinutes: (Int) -> Unit,
    onAdjustBass: (Int) -> Unit,
    onOpenEqualizer: () -> Unit,
    onPlayQueueItem: (PlayableTrack) -> Unit,
    onRemoveQueueItem: (String) -> Unit
) {
    BackHandler(onBack = onDismiss)

    val context = LocalContext.current
    val track = playbackState.currentTrack
    var artDragX by remember { mutableFloatStateOf(0f) }
    var gestureHint by remember { mutableStateOf("Gesty: Przesuń bokiem = Zmień utwór • 2x Tap = Pauza/Play • W dół = Zamknij") }
    var showQueue by remember { mutableStateOf(false) }
    var showSleepOptions by remember { mutableStateOf(false) }

    val accent = MaterialTheme.colorScheme.primary
    val topBarBrush = if (playMusicMode) {
        Brush.horizontalGradient(listOf(GpmOrange, GpmCoral))
    } else {
        Brush.horizontalGradient(listOf(accent, accent))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        // Top Swipe-Down Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(topBarBrush)
                .pointerInput(Unit) {
                    var dragY = 0f
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragY > 80f) onDismiss()
                            dragY = 0f
                        },
                        onVerticalDrag = { _, delta -> dragY += delta }
                    )
                }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(48.dp).testTag("now_playing_minimize")
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimalizuj", tint = Color.White)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(
                    text = "ODTWARZACZ SONG/Q",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                val timerText = if (playbackState.sleepTimerRemainingSec > 0) {
                    val m = playbackState.sleepTimerRemainingSec / 60
                    val s = playbackState.sleepTimerRemainingSec % 60
                    " • Sen %d:%02d".format(m, s)
                } else ""
                Text(
                    text = "EQ: ${eqState.activePresetName} • BASS ${eqState.bassBoostStrength / 10}%$timerText",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.88f)
                )
            }
            IconButton(
                onClick = { showQueue = !showQueue },
                modifier = Modifier.size(48.dp).testTag("now_playing_queue_toggle")
            ) {
                Icon(Icons.Default.QueueMusic, contentDescription = "Kolejka odtwarzania", tint = Color.White)
            }
        }

        if (track == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Brak aktywnego utworu", style = MaterialTheme.typography.titleLarge)
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                // Gesture-Controlled Artwork & Format Card
                GPlusFlatCard(accentStripColor = accent) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .background(Color(0xFF1E1E1E))
                            .offset { IntOffset(artDragX.roundToInt(), 0) }
                            .pointerInput(track.id) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        onTogglePlayPause()
                                        gestureHint = if (playbackState.isPlaying) "Gest 2x Tap: Pauza" else "Gest 2x Tap: Odtwarzanie"
                                    }
                                )
                            }
                            .pointerInput(track.id) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        when {
                                            artDragX < -100f -> {
                                                gestureHint = "Gest w lewo: Następny utwór"
                                                onNext()
                                            }
                                            artDragX > 100f -> {
                                                gestureHint = "Gest w prawo: Poprzedni utwór"
                                                onPrevious()
                                            }
                                        }
                                        artDragX = 0f
                                    },
                                    onDragCancel = { artDragX = 0f },
                                    onHorizontalDrag = { _, dragAmount ->
                                        artDragX = (artDragX + dragAmount).coerceIn(-180f, 180f)
                                    }
                                )
                            }
                            .pointerInput(eqState.bassBoostStrength) {
                                detectVerticalDragGestures { _, dragAmount ->
                                    val delta = (-dragAmount * 2f).toInt()
                                    if (delta != 0) {
                                        val nextBass = (eqState.bassBoostStrength + delta).coerceIn(0, 1000)
                                        onAdjustBass(nextBass)
                                        gestureHint = "Gest pionowy: Bass Boost ${nextBass / 10}%"
                                    }
                                }
                            }
                    ) {
                        if (track.artworkUrl.isNotBlank()) {
                            AsyncImage(
                                model = track.artworkUrl,
                                contentDescription = track.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.img_album_placeholder_1794959043577),
                                contentDescription = track.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopStart)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CodecBadge(label = track.codecLabel, sourceType = track.sourceType)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        MusicAppLinker.openSystemMusicChooser(
                                            context = context,
                                            titleOrQuery = "${track.artist} ${track.title}".trim(),
                                            targetUrl = if (track.uriOrUrl.startsWith("http")) track.uriOrUrl else ""
                                        )
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.6f))
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = "Otwórz w aplikacji muzycznej",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                SongQFileLogoIcon(sourceType = track.sourceType, modifier = Modifier.size(36.dp))
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomStart)
                                .background(Color.Black.copy(alpha = 0.72f))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Column {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${track.artist} • ${track.album.ifBlank { "Song/Q" }}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = gestureHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                // Interactive Waveform Canvas Scrubber + Controls + Background Modes
                GPlusFlatCard(accentStripColor = GPlusBlue) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val bars = playbackState.waveformBars
                        val progress = if (playbackState.durationMs > 0L) {
                            (playbackState.positionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .background(Color(0xFF1A1A1A))
                                .border(1.dp, MaterialTheme.colorScheme.outline)
                                .pointerInput(playbackState.durationMs) {
                                    detectTapGestures { offset ->
                                        if (playbackState.durationMs > 0L && size.width > 0) {
                                            val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                            onSeek((frac * playbackState.durationMs).toLong())
                                        }
                                    }
                                }
                        ) {
                            val count = bars.size.coerceAtLeast(1)
                            val slotW = size.width / count.toFloat()
                            val barW = (slotW * 0.65f).coerceAtLeast(2f)
                            bars.forEachIndexed { idx, amp ->
                                val barH = (amp * (size.height - 10f)).coerceAtLeast(4f)
                                val x = idx * slotW + (slotW - barW) / 2f
                                val y = (size.height - barH) / 2f
                                val barFrac = idx.toFloat() / count.toFloat()
                                val color = if (barFrac <= progress) accent else GPlusAmber.copy(alpha = 0.45f)
                                drawRect(
                                    color = color,
                                    topLeft = Offset(x, y),
                                    size = Size(barW, barH)
                                )
                            }
                        }

                        if (playbackState.durationMs > 0L) {
                            Slider(
                                value = progress,
                                onValueChange = { frac ->
                                    onSeek((frac * playbackState.durationMs).toLong())
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = accent,
                                    activeTrackColor = accent
                                )
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatDurationMs(playbackState.positionMs), style = MaterialTheme.typography.labelMedium)
                            Text(formatDurationMs(playbackState.durationMs), style = MaterialTheme.typography.labelMedium)
                        }

                        // Primary Transport Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onToggleShuffle,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (playbackState.shuffleEnabled) accent.copy(alpha = 0.16f) else Color.Transparent)
                            ) {
                                Icon(
                                    Icons.Default.Shuffle,
                                    contentDescription = "Odtwarzanie losowe",
                                    tint = if (playbackState.shuffleEnabled) accent else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = onPrevious,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            ) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = "Poprzedni")
                            }

                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(accent)
                                    .clickable(onClick = onTogglePlayPause)
                                    .testTag("now_playing_play_pause"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Odtwarzaj lub pauzuj",
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp)
                                )
                            }

                            IconButton(
                                onClick = onNext,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            ) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Następny")
                            }

                            IconButton(
                                onClick = onCycleRepeat,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (playbackState.repeatMode != RepeatMode.OFF) accent.copy(alpha = 0.16f) else Color.Transparent)
                            ) {
                                Icon(
                                    imageVector = if (playbackState.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                    contentDescription = "Tryb powtarzania: ${playbackState.repeatMode.label}",
                                    tint = if (playbackState.repeatMode != RepeatMode.OFF) accent else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Secondary Actions: Equalizer + Sleep Timer
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(GPlusBlue)
                                    .clickable {
                                        onDismiss()
                                        onOpenEqualizer()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Korektor EQ", color = Color.White, style = MaterialTheme.typography.labelLarge)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(if (playbackState.sleepTimerRemainingSec > 0) accent else MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                                    .clickable { showSleepOptions = !showSleepOptions },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Bedtime,
                                        contentDescription = null,
                                        tint = if (playbackState.sleepTimerRemainingSec > 0) Color.White else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (playbackState.sleepTimerRemainingSec > 0) {
                                            "Sen (${playbackState.sleepTimerRemainingSec / 60 + 1}m)"
                                        } else "Wyłącznik",
                                        color = if (playbackState.sleepTimerRemainingSec > 0) Color.White else MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }

                        if (showSleepOptions) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(0 to "Wył.", 10 to "10 min", 20 to "20 min", 30 to "30 min", 60 to "60 min").forEach { (mins, label) ->
                                    Box(
                                        modifier = Modifier
                                            .clip(MaterialTheme.shapes.extraSmall)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
                                            .clickable {
                                                onSetSleepTimerMinutes(mins)
                                                showSleepOptions = false
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(label, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }

                        if (!playbackState.statusMessage.isNullOrBlank()) {
                            Text(
                                text = playbackState.statusMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = accent
                            )
                        }
                    }
                }
            }

            if (showQueue) {
                item {
                    Text(
                        text = "KOLEJKA ODTWARZANIA (${playbackState.queue.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(playbackState.queue, key = { it.id }) { qTrack ->
                    GPlusFlatCard(
                        accentStripColor = if (qTrack.id == track.id) accent else null,
                        onClick = { onPlayQueueItem(qTrack) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SongQFileLogoIcon(sourceType = qTrack.sourceType, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(qTrack.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(qTrack.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            IconButton(onClick = { onRemoveQueueItem(qTrack.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Usuń z kolejki")
                            }
                        }
                    }
                }
            }
        }
    }
}
