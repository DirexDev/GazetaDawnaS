package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.SourceType
import com.example.data.model.SqPlaylistDoc
import com.example.data.model.SqTrackDoc
import com.example.ui.components.CodecBadge
import com.example.ui.components.GPlusFlatCard
import com.example.ui.components.SongQFileLogoIcon
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.GPlusBlue
import com.example.ui.theme.GPlusGreen
import com.example.ui.theme.GPlusRed
import com.example.ui.viewmodel.UiState

@Composable
fun PlaylistsScreen(
    playlistsState: UiState<List<SqPlaylistDoc>>,
    selectedPlaylistId: String?,
    selectedTracksState: UiState<List<SqTrackDoc>>,
    onSelectPlaylist: (String?) -> Unit,
    onCreatePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onImportSqFile: () -> Unit,
    onExportSqFile: (SqPlaylistDoc, List<SqTrackDoc>) -> Unit,
    onPlayPlaylist: (List<SqTrackDoc>, Int) -> Unit,
    onMoveTrack: (String, List<SqTrackDoc>, Int, Int) -> Unit,
    onRemoveTrack: (String, SqTrackDoc) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        CreateSqPlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, desc ->
                onCreatePlaylist(name, desc)
                showCreateDialog = false
            }
        )
    }

    val playlists = (playlistsState as? UiState.Success)?.data ?: emptyList()
    val selectedPlaylist = playlists.firstOrNull { it.id == selectedPlaylistId }

    if (selectedPlaylist != null) {
        val tracks = (selectedTracksState as? UiState.Success)?.data ?: emptyList()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                GPlusFlatCard(accentStripColor = GPlusBlue) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onSelectPlaylist(null) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć do playlist")
                            }
                            SongQFileLogoIcon(sourceType = SourceType.LOCAL, isPlaylistFile = true, modifier = Modifier.size(42.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${selectedPlaylist.name}.sq", style = MaterialTheme.typography.headlineSmall)
                                Text(
                                    text = selectedPlaylist.description.ifBlank { "Kontener .SQ1 • ${tracks.size} pozycji" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onPlayPlaylist(tracks, 0) },
                                enabled = tracks.isNotEmpty(),
                                shape = RectangleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = GPlusRed),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Odtwórz .sq")
                            }
                            OutlinedButton(
                                onClick = { onExportSqFile(selectedPlaylist, tracks) },
                                shape = RectangleShape,
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Eksportuj .sq")
                            }
                        }
                    }
                }
            }

            when (selectedTracksState) {
                is UiState.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GPlusBlue)
                    }
                }
                is UiState.Error -> item {
                    GPlusFlatCard(accentStripColor = GPlusRed) {
                        Text(selectedTracksState.message, color = GPlusRed, modifier = Modifier.padding(14.dp))
                    }
                }
                is UiState.Success -> {
                    if (tracks.isEmpty()) {
                        item {
                            GPlusFlatCard {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Playlista .sq jest pusta", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Dodaj utwory z Biblioteki lub bezpośrednio z Wyszukiwarki Web (bez zużycia pamięci!).",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        itemsIndexed(tracks, key = { _, t -> t.id }) { idx, track ->
                            val sourceType = SourceType.entries.firstOrNull { it.name == track.sourceType } ?: SourceType.WEB_LINK
                            GPlusFlatCard(
                                accentStripColor = GPlusGreen,
                                onClick = { onPlayPlaylist(tracks, idx) }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SongQFileLogoIcon(sourceType = sourceType, modifier = Modifier.size(40.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${track.artist} • ${formatDurationMs(track.durationMs)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                        CodecBadge(label = track.codecLabel, sourceType = sourceType)
                                    }
                                    IconButton(
                                        onClick = { onMoveTrack(selectedPlaylist.id, tracks, idx, idx - 1) },
                                        enabled = idx > 0
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Przesuń w górę")
                                    }
                                    IconButton(
                                        onClick = { onMoveTrack(selectedPlaylist.id, tracks, idx, idx + 1) },
                                        enabled = idx < tracks.lastIndex
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Przesuń w dół")
                                    }
                                    IconButton(
                                        onClick = { onRemoveTrack(selectedPlaylist.id, track) }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Usuń utwór")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            GPlusFlatCard(accentStripColor = GPlusBlue) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("PLAYLISTY .SQ (SONG/Q CONTAINER)", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Lekkie listy odtwarzania .sq przechowujące ścieżki .mfa oraz linki z wyszukiwarek internetowych w chmurze Google i plikach lokalnych.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showCreateDialog = true },
                            shape = RectangleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = GPlusBlue),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("create_sq_playlist_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Nowa .sq")
                        }
                        OutlinedButton(
                            onClick = onImportSqFile,
                            shape = RectangleShape,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("import_sq_file_button")
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Importuj .sq")
                        }
                    }
                }
            }
        }

        when (playlistsState) {
            is UiState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GPlusBlue)
                }
            }
            is UiState.Error -> item {
                GPlusFlatCard(accentStripColor = GPlusRed) {
                    Text(playlistsState.message, color = GPlusRed, modifier = Modifier.padding(14.dp))
                }
            }
            is UiState.Success -> {
                if (playlists.isEmpty()) {
                    item {
                        GPlusFlatCard {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SongQFileLogoIcon(sourceType = SourceType.LOCAL, isPlaylistFile = true, modifier = Modifier.size(56.dp))
                                Text("Brak playlist .sq", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Kliknij \"Nowa .sq\", aby utworzyć pierwszą playlistę lub zaimportuj plik .sq z urządzenia.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(playlists, key = { it.id }) { pl ->
                        GPlusFlatCard(
                            accentStripColor = GPlusBlue,
                            onClick = { onSelectPlaylist(pl.id) },
                            modifier = Modifier.testTag("playlist_card_${pl.id}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SongQFileLogoIcon(sourceType = SourceType.LOCAL, isPlaylistFile = true, modifier = Modifier.size(46.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${pl.name}.sq", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        text = "${pl.trackCount} utworów • ${pl.formatVersion} • ${formatDurationMs(pl.totalDurationMs)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (pl.description.isNotBlank()) {
                                        Text(pl.description, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                IconButton(onClick = { onDeletePlaylist(pl.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Usuń playlistę")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateSqPlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        title = { Text("Nowa playlista .sq", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nazwa playlisty (bez .sq)") },
                    singleLine = true,
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxWidth().testTag("new_playlist_name_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Opis (opcjonalnie)") },
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name, description) },
                enabled = name.isNotBlank(),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(containerColor = GPlusBlue)
            ) {
                Text("Utwórz .sq")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}
