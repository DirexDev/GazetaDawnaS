package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.SearchEngineType
import com.example.data.model.SourceType
import com.example.data.model.SqPlaylistDoc
import com.example.data.model.WebTrackResult
import com.example.data.remote.MusicDownloadProgress
import com.example.data.search.MusicAppLinker
import com.example.data.search.MusicAppTarget
import com.example.ui.components.CodecBadge
import com.example.ui.components.DownloadProgressBanner
import com.example.ui.components.GPlusFlatCard
import com.example.ui.components.SongQFileLogoIcon
import com.example.ui.theme.GPlusAmber
import com.example.ui.theme.GPlusBlue
import com.example.ui.theme.GPlusGreen
import com.example.ui.theme.GPlusRed
import com.example.ui.theme.GpmOrange
import com.example.ui.viewmodel.SearchUiState

@Composable
fun SearchScreen(
    searchState: SearchUiState,
    playlists: List<SqPlaylistDoc>,
    downloadProgress: MusicDownloadProgress?,
    preferredMusicAppPackage: String,
    preferredMusicAppLabel: String,
    onQueryChange: (String) -> Unit,
    onSelectEngine: (SearchEngineType) -> Unit,
    onSearch: () -> Unit,
    onPlayPreview: (WebTrackResult) -> Unit,
    onDownloadTrack: (WebTrackResult) -> Unit,
    onDownloadFromUrlOrQuery: (String) -> Unit,
    onDismissDownloadProgress: () -> Unit,
    onSavePreferredMusicApp: (String, String) -> Unit,
    onAddResultToPlaylist: (WebTrackResult, String) -> Unit
) {
    val context = LocalContext.current
    var directDownloadInput by remember { mutableStateOf("") }
    var appPickerTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    val availableApps = remember(appPickerTarget) {
        if (appPickerTarget != null) MusicAppLinker.discoverMusicApps(context) else emptyList()
    }

    if (appPickerTarget != null) {
        val (targetTitle, targetUrl) = appPickerTarget!!
        MusicAppChooserDialog(
            trackTitle = targetTitle,
            apps = availableApps,
            preferredPackage = preferredMusicAppPackage,
            onSelectApp = { app, setAsDefault ->
                if (setAsDefault && app.packageName != null) {
                    onSavePreferredMusicApp(app.packageName, app.label)
                }
                MusicAppLinker.openInTargetApp(
                    context = context,
                    target = app,
                    queryOrTitle = targetTitle,
                    fallbackUrl = targetUrl
                )
                appPickerTarget = null
            },
            onOpenSystemChooser = {
                MusicAppLinker.openSystemMusicChooser(
                    context = context,
                    titleOrQuery = targetTitle,
                    targetUrl = targetUrl
                )
                appPickerTarget = null
            },
            onDismiss = { appPickerTarget = null }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (downloadProgress != null) {
            item {
                DownloadProgressBanner(
                    progress = downloadProgress,
                    onDismiss = onDismissDownloadProgress
                )
            }
        }

        // Direct YouTube / Music Site Downloader Card
        item {
            GPlusFlatCard(accentStripColor = GpmOrange) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = GpmOrange)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "POBIERZ MUZYKĘ Z YOUTUBE I STRON MUZYCZNYCH",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Wklej link YouTube / YouTube Music / MP3 / M4A / SoundCloud albo wpisz frazę, aby zapisać offline do biblioteki Song/Q",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = directDownloadInput,
                            onValueChange = { directDownloadInput = it },
                            placeholder = { Text("https://youtu.be/... lub artysta i tytuł") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (directDownloadInput.isNotBlank()) {
                                        onDownloadFromUrlOrQuery(directDownloadInput)
                                    }
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("yt_download_url_input")
                        )
                        Button(
                            onClick = { onDownloadFromUrlOrQuery(directDownloadInput) },
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.buttonColors(containerColor = GpmOrange),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("yt_download_submit_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Pobierz utwór")
                            Spacer(Modifier.width(4.dp))
                            Text("Pobierz")
                        }
                    }
                }
            }
        }

        // Relevance-Ranked Web & API Search Card
        item {
            GPlusFlatCard(accentStripColor = GPlusBlue) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "WYSZUKIWARKA MUZYKI Z RANKINGIEM TRAFNOŚCI",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Wyniki są sortowane według największej trafności z wpisaną frazą. Możesz słuchać, pobierać lub otworzyć dowolny utwór w wybranej aplikacji muzycznej" +
                            if (preferredMusicAppLabel.isNotBlank()) " (domyślna: $preferredMusicAppLabel)." else ".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SearchEngineType.entries.forEach { engine ->
                            val selected = engine == searchState.selectedEngine
                            Box(
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .border(
                                        1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        MaterialTheme.shapes.extraSmall
                                    )
                                    .clickable { onSelectEngine(engine) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "${engine.badge}: ${engine.displayName}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchState.query,
                            onValueChange = onQueryChange,
                            placeholder = { Text("Wpisz dokładny tytuł lub artystę...") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("web_search_input")
                        )
                        Button(
                            onClick = onSearch,
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("web_search_submit")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Szukaj")
                        }
                    }
                }
            }
        }

        if (searchState.isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (!searchState.errorMessage.isNullOrBlank()) {
            item {
                GPlusFlatCard(accentStripColor = GPlusRed) {
                    Text(
                        text = "Błąd sieci API: ${searchState.errorMessage}",
                        color = GPlusRed,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        // Artist Info Cards from MusicBrainz API
        if (searchState.artistResults.isNotEmpty()) {
            item {
                Text(
                    text = "INFORMACJE O ARTYSTACH (MUSICBRAINZ API)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(searchState.artistResults.take(3), key = { "art_${it.id}" }) { artist ->
                GPlusFlatCard(accentStripColor = GPlusAmber) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(GPlusAmber),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF212121))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(artist.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${artist.type} • ${artist.country}" +
                                    if (artist.disambiguation.isNotBlank()) " • ${artist.disambiguation}" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                            if (artist.tags.isNotEmpty()) {
                                Text(
                                    text = artist.tags.joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GPlusBlue
                                )
                            }
                        }
                        IconButton(onClick = {
                            appPickerTarget = artist.name to artist.externalUrl
                        }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Wybierz aplikację muzyczną")
                        }
                    }
                }
            }
        }

        if (searchState.trackResults.isNotEmpty()) {
            item {
                Text(
                    text = "WYNIKI POSORTOWANE WEDŁUG TRAFNOŚCI (${searchState.trackResults.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
            itemsIndexed(searchState.trackResults, key = { _, item -> item.id }) { index, result ->
                WebResultCard(
                    result = result,
                    isTopMatch = index == 0,
                    playlists = playlists,
                    onPlayPreview = { onPlayPreview(result) },
                    onDownload = { onDownloadTrack(result) },
                    onOpenMusicAppPicker = {
                        appPickerTarget = "${result.artist} ${result.title}".trim() to result.externalUrl
                    },
                    onAddToPlaylist = { playlistId -> onAddResultToPlaylist(result, playlistId) }
                )
            }
        }
    }
}

@Composable
private fun WebResultCard(
    result: WebTrackResult,
    isTopMatch: Boolean,
    playlists: List<SqPlaylistDoc>,
    onPlayPreview: () -> Unit,
    onDownload: () -> Unit,
    onOpenMusicAppPicker: () -> Unit,
    onAddToPlaylist: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val sourceType = if (result.previewUrl.isNotBlank()) SourceType.ITUNES_PREVIEW else SourceType.WEB_LINK

    GPlusFlatCard(
        accentStripColor = if (isTopMatch) GpmOrange else if (result.previewUrl.isNotBlank()) GPlusBlue else GPlusGreen,
        onClick = { if (result.previewUrl.isNotBlank()) onPlayPreview() else onOpenMusicAppPicker() },
        modifier = Modifier.testTag("search_result_${result.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isTopMatch) {
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(GpmOrange)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "#1 NAJTRAFNIEJSZY WYNIK DLA FRAZY",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (result.artworkUrl.isNotBlank()) {
                    AsyncImage(
                        model = result.artworkUrl,
                        contentDescription = result.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(MaterialTheme.shapes.small)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    )
                } else {
                    SongQFileLogoIcon(sourceType = sourceType, modifier = Modifier.size(48.dp))
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(result.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = "${result.artist} • ${result.album.ifBlank { result.genre }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    CodecBadge(
                        label = if (result.previewUrl.isNotBlank()) "ODSŁUCH + ${result.engine.badge}" else "LINK ${result.engine.badge} (0 KB)",
                        sourceType = sourceType
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (result.previewUrl.isNotBlank()) {
                    OutlinedButton(
                        onClick = onPlayPreview,
                        shape = MaterialTheme.shapes.small,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = GpmOrange)
                        Spacer(Modifier.width(4.dp))
                        Text("Graj", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Button(
                    onClick = onDownload,
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(containerColor = GpmOrange),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Pobierz", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onOpenMusicAppPicker,
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f).height(38.dp).testTag("choose_music_app_${result.id}")
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Aplikacja", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.PlaylistAdd, contentDescription = "Zapisz w .sq")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (playlists.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Utwórz najpierw playlistę .sq") },
                                onClick = { showMenu = false }
                            )
                        } else {
                            playlists.forEach { pl ->
                                DropdownMenuItem(
                                    text = { Text("Zapisz do ${pl.name}.sq (0 KB)") },
                                    onClick = {
                                        showMenu = false
                                        onAddToPlaylist(pl.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicAppChooserDialog(
    trackTitle: String,
    apps: List<MusicAppTarget>,
    preferredPackage: String,
    onSelectApp: (MusicAppTarget, Boolean) -> Unit,
    onOpenSystemChooser: () -> Unit,
    onDismiss: () -> Unit
) {
    var rememberDefault by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Wybierz aplikację muzyczną", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = trackTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { rememberDefault = !rememberDefault }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = rememberDefault,
                        onCheckedChange = { rememberDefault = it }
                    )
                    Text("Zapamiętaj wybraną aplikację w ustawieniach", style = MaterialTheme.typography.bodySmall)
                }

                LazyColumn(
                    modifier = Modifier.height(290.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(apps, key = { it.id }) { app ->
                        val isPreferred = app.packageName != null && app.packageName == preferredPackage
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectApp(app, rememberDefault) },
                            shape = MaterialTheme.shapes.small,
                            color = if (isPreferred) GpmOrange.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, if (isPreferred) GpmOrange else MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (app.isInstalled) GPlusGreen else GPlusBlue),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(app.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        if (app.isInstalled) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = "Zainstalowana", tint = GPlusGreen, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                    Text(
                                        app.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenSystemChooser) {
                Text("Wybór systemowy Android")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj")
            }
        }
    )
}
