package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.example.data.model.PlayableTrack
import com.example.data.model.SourceType
import com.example.data.model.SqPlaylistDoc
import com.example.data.search.MusicAppLinker
import com.example.ui.components.CodecBadge
import com.example.ui.components.GPlusFlatCard
import com.example.ui.components.SongQFileLogoIcon
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.GPlusAmber
import com.example.ui.theme.GPlusBlue
import com.example.ui.theme.GPlusGreen
import com.example.ui.theme.GpmAmber
import com.example.ui.theme.GpmCoral
import com.example.ui.theme.GpmOrange
import com.example.ui.theme.GpmOrangeDeep
import kotlin.math.roundToInt

@Composable
fun LibraryScreen(
    tracks: List<PlayableTrack>,
    currentTrackId: String?,
    playlists: List<SqPlaylistDoc>,
    playMusicMode: Boolean = true,
    onPlayTrack: (PlayableTrack) -> Unit,
    onPlayImFeelingLuckyMix: () -> Unit,
    onEnqueueTrack: (PlayableTrack) -> Unit,
    onAddToPlaylist: (String, PlayableTrack) -> Unit,
    onOpenAudioOrMfaPicker: () -> Unit,
    onConvertWavToMfaPicker: () -> Unit,
    onOpenExplorer: () -> Unit,
    onExportMfaTrack: (PlayableTrack) -> Unit
) {
    val context = LocalContext.current
    var filterQuery by remember { mutableStateOf("") }
    val filteredTracks = remember(tracks, filterQuery) {
        if (filterQuery.isBlank()) tracks
        else tracks.filter {
            it.title.contains(filterQuery, ignoreCase = true) ||
                it.artist.contains(filterQuery, ignoreCase = true) ||
                it.codecLabel.contains(filterQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Google Play Music "Słuchaj teraz" Hero Mix Card
        if (playMusicMode) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    shadowElevation = 2.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(GpmOrangeDeep, GpmOrange, GpmAmber)
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "SŁUCHAJ TERAZ • GOOGLE PLAY MUSIC",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.88f),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Składanka Szczęśliwy Traf",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${tracks.size} utworów offline (.MFA, MP3, M4A, FLAC, WAV) gotowych w tle",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.92f)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .clickable(onClick = onPlayImFeelingLuckyMix)
                                        .testTag("lucky_mix_fab"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = "Uruchom Składankę Szczęśliwy Traf",
                                        tint = GpmOrange,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onPlayImFeelingLuckyMix,
                                    shape = MaterialTheme.shapes.small,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = GpmOrangeDeep
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Odtwarzaj Mix", fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                                Button(
                                    onClick = onOpenExplorer,
                                    shape = MaterialTheme.shapes.small,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Black.copy(alpha = 0.25f),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("open_explorer_quick_button")
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Eksplorator .mfa/.sq", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Horizontal Carousel of Square Play Music Album Cards
        if (tracks.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (playMusicMode) "OSTATNIO ODTWARZANE I POBRANE" else "SZYBKI START",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(tracks.take(10), key = { "carousel_${it.id}" }) { track ->
                            PlayMusicCarouselCard(
                                track = track,
                                isActive = track.id == currentTrackId,
                                onPlay = { onPlayTrack(track) }
                            )
                        }
                    }
                }
            }
        }

        // Import & Filter Controls
        item {
            GPlusFlatCard(accentStripColor = MaterialTheme.colorScheme.primary) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "BIBLIOTEKA SONG/Q (.MFA & AUDIO)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Obsługa MP3, M4A, AAC, FLAC, OGG, OPUS, WAV oraz .MFA • Przesuń kartę w prawo, aby dodać do kolejki",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(GPlusAmber)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${tracks.size} PLIKÓW",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF212121),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onOpenAudioOrMfaPicker,
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("import_audio_mfa_button")
                        ) {
                            Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Otwórz Audio / .MFA", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        OutlinedButton(
                            onClick = onConvertWavToMfaPicker,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("convert_wav_to_mfa_button")
                        ) {
                            Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("WAV → .MFA", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    OutlinedTextField(
                        value = filterQuery,
                        onValueChange = { filterQuery = it },
                        placeholder = { Text("Filtruj bibliotekę (tytuł, artysta, MP3, FLAC, .MFA)...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("library_filter_input")
                    )
                }
            }
        }

        if (filteredTracks.isEmpty()) {
            item {
                GPlusFlatCard(accentStripColor = GPlusBlue) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SongQFileLogoIcon(sourceType = SourceType.MFA, modifier = Modifier.size(56.dp))
                        Text("Brak pasujących plików audio", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Otwórz lokalny plik MP3/FLAC/WAV/.MFA lub pobierz utwór w zakładce Szukaj.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredTracks, key = { it.id }) { track ->
                LibraryTrackCard(
                    track = track,
                    isActive = track.id == currentTrackId,
                    playlists = playlists,
                    onPlay = { onPlayTrack(track) },
                    onEnqueue = { onEnqueueTrack(track) },
                    onAddToPlaylist = { playlistId -> onAddToPlaylist(playlistId, track) },
                    onOpenInExternalMusicApp = {
                        MusicAppLinker.openSystemMusicChooser(
                            context = context,
                            titleOrQuery = "${track.artist} ${track.title}".trim(),
                            targetUrl = ""
                        )
                    },
                    onExportMfa = { onExportMfaTrack(track) }
                )
            }
        }
    }
}

@Composable
private fun PlayMusicCarouselCard(
    track: PlayableTrack,
    isActive: Boolean,
    onPlay: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(146.dp)
            .clickable(onClick = onPlay),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isActive) 2.dp else 1.dp,
            color = if (isActive) GpmOrange else MaterialTheme.colorScheme.outline
        ),
        shadowElevation = 1.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp)
                    .background(Color(0xFF212121))
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
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = track.codecLabel.substringBefore(" "),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (isActive) GpmCoral else GpmOrange)
                        .clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Odtwórz ${track.title}",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LibraryTrackCard(
    track: PlayableTrack,
    isActive: Boolean,
    playlists: List<SqPlaylistDoc>,
    onPlay: () -> Unit,
    onEnqueue: () -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onOpenInExternalMusicApp: () -> Unit,
    onExportMfa: () -> Unit
) {
    var dragX by remember { mutableFloatStateOf(0f) }
    var showPlaylistMenu by remember { mutableStateOf(false) }

    GPlusFlatCard(
        accentStripColor = if (isActive) MaterialTheme.colorScheme.primary else if (track.sourceType == SourceType.MFA) GPlusBlue else GPlusGreen,
        onClick = onPlay,
        modifier = Modifier
            .offset { IntOffset(dragX.roundToInt(), 0) }
            .pointerInput(track.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragX > 95f) onEnqueue()
                        dragX = 0f
                    },
                    onDragCancel = { dragX = 0f },
                    onHorizontalDrag = { _, delta ->
                        dragX = (dragX + delta).coerceIn(0f, 160f)
                    }
                )
            }
            .testTag("library_track_${track.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SongQFileLogoIcon(
                sourceType = track.sourceType,
                modifier = Modifier.size(46.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} • ${formatDurationMs(track.durationMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CodecBadge(label = track.codecLabel, sourceType = track.sourceType)
                    if (track.sourceType == SourceType.MFA) {
                        Text(
                            text = "${track.sampleRate / 1000f}kHz PCM-Z",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onEnqueue,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.QueueMusic, contentDescription = "Dodaj do kolejki")
                }

                Box {
                    IconButton(
                        onClick = { showPlaylistMenu = true },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Default.PlaylistAdd, contentDescription = "Opcje utworu i playlisty .sq")
                    }
                    DropdownMenu(
                        expanded = showPlaylistMenu,
                        onDismissRequest = { showPlaylistMenu = false }
                    ) {
                        if (playlists.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Utwórz najpierw playlistę .sq") },
                                onClick = { showPlaylistMenu = false }
                            )
                        } else {
                            playlists.forEach { pl ->
                                DropdownMenuItem(
                                    text = { Text("Dodaj do: ${pl.name}.sq") },
                                    onClick = {
                                        showPlaylistMenu = false
                                        onAddToPlaylist(pl.id)
                                    }
                                )
                            }
                        }
                        DropdownMenuItem(
                            text = { Text("Otwórz w aplikacji muzycznej") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                            onClick = {
                                showPlaylistMenu = false
                                onOpenInExternalMusicApp()
                            }
                        )
                        if (track.sourceType == SourceType.MFA) {
                            DropdownMenuItem(
                                text = { Text("Eksportuj plik .mfa") },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                onClick = {
                                    showPlaylistMenu = false
                                    onExportMfa()
                                }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Odtwórz",
                        tint = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
