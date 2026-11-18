package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.explorer.ExplorerBreadcrumb
import com.example.data.explorer.ExplorerEntry
import com.example.data.explorer.ExplorerFilterMode
import com.example.data.explorer.LocalFileExplorer
import com.example.data.model.SourceType
import com.example.ui.components.CodecBadge
import com.example.ui.components.GPlusFlatCard
import com.example.ui.components.SongQFileLogoIcon
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.GPlusBlue
import com.example.ui.theme.GPlusGreen
import com.example.ui.theme.GpmAmber
import com.example.ui.theme.GpmOrange
import com.example.ui.viewmodel.ExplorerUiState

@Composable
fun FileExplorerScreen(
    explorerState: ExplorerUiState,
    currentPlayingUriOrPath: String?,
    onInitExplorer: () -> Unit,
    onSelectAppRoot: (ExplorerBreadcrumb, Boolean) -> Unit,
    onOpenMfaRoot: () -> Unit,
    onOpenSqRoot: () -> Unit,
    onPickSafDirectory: () -> Unit,
    onOpenSavedSafDirectory: () -> Unit,
    onSelectFilter: (ExplorerFilterMode) -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleRecursive: () -> Unit,
    onNavigateBreadcrumb: (Int) -> Unit,
    onOpenFolder: (ExplorerEntry.Folder) -> Unit,
    onPlayMfa: (ExplorerEntry.MfaFile) -> Unit,
    onPlaySq: (ExplorerEntry.SqPlaylistFile) -> Unit,
    onImportSq: (ExplorerEntry.SqPlaylistFile) -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val appRoots = LocalFileExplorer.getAppRoots(context)

    LaunchedEffect(Unit) {
        onInitExplorer()
    }

    val mfaCount = explorerState.entries.count { it is ExplorerEntry.MfaFile }
    val sqCount = explorerState.entries.count { it is ExplorerEntry.SqPlaylistFile }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Explorer Header & SAF Root Picker
        item {
            GPlusFlatCard(accentStripColor = GpmOrange) {
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
                                text = "EKSPLORATOR PLIKÓW .MFA / .SQ",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Filtruje wyłącznie foldery oraz pliki MediaFileAudio (.mfa) i playlisty Song/Q (.sq)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(44.dp).testTag("explorer_refresh")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Odśwież", tint = GpmOrange)
                        }
                    }

                    // Quick Storage Roots
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        appRoots.firstOrNull()?.let { firstRoot ->
                            RootChip(
                                label = "Wszystkie (.MFA+.SQ)",
                                selected = explorerState.activeRootLabel == firstRoot.label && explorerState.recursiveScan,
                                onClick = { onSelectAppRoot(firstRoot, true) }
                            )
                        }
                        RootChip(
                            label = "Folder .MFA",
                            selected = explorerState.activeRootLabel == "Biblioteka .MFA",
                            onClick = onOpenMfaRoot
                        )
                        RootChip(
                            label = "Folder .SQ",
                            selected = explorerState.activeRootLabel == "Playlisty .SQ",
                            onClick = onOpenSqRoot
                        )
                        if (explorerState.savedSafTreeUri.isNotBlank()) {
                            RootChip(
                                label = "Mój Folder (SAF)",
                                selected = explorerState.activeRootLabel == "Folder urządzenia (SAF)",
                                onClick = onOpenSavedSafDirectory
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onPickSafDirectory,
                            colors = ButtonDefaults.buttonColors(containerColor = GpmOrange),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("explorer_pick_saf_folder")
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Otwórz folder pamięci", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        OutlinedButton(
                            onClick = onToggleRecursive,
                            modifier = Modifier
                                .height(46.dp)
                                .testTag("explorer_toggle_recursive")
                        ) {
                            Text(
                                text = if (explorerState.recursiveScan) "Skanowanie: Głębokie" else "Skanowanie: Folder",
                                maxLines = 1
                            )
                        }
                    }

                    // Filter Chips (.MFA + .SQ / .MFA / .SQ)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExplorerFilterMode.entries.forEach { mode ->
                            val selected = explorerState.filterMode == mode
                            Surface(
                                color = if (selected) GpmOrange else MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSelectFilter(mode) }
                                    .testTag("explorer_filter_${mode.name}")
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = explorerState.query,
                        onValueChange = onQueryChange,
                        placeholder = { Text("Filtruj pliki .mfa / .sq w katalogu...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("explorer_search_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Znaleziono: $mfaCount .mfa • $sqCount .sq",
                            style = MaterialTheme.typography.labelMedium,
                            color = GpmOrange,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Dotknij plik, aby odtworzyć",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Breadcrumbs Bar
        if (explorerState.breadcrumbs.isNotEmpty()) {
            item {
                GPlusFlatCard(accentStripColor = GPlusBlue) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (explorerState.breadcrumbs.size > 1) {
                            IconButton(
                                onClick = { onNavigateBreadcrumb(explorerState.breadcrumbs.lastIndex - 1) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Poziom wyżej")
                            }
                        } else {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = GPlusBlue,
                                modifier = Modifier.padding(horizontal = 6.dp).size(20.dp)
                            )
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            explorerState.breadcrumbs.forEachIndexed { idx, crumb ->
                                val isLast = idx == explorerState.breadcrumbs.lastIndex
                                Text(
                                    text = crumb.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isLast) GpmOrange else GPlusBlue,
                                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier
                                        .clickable { onNavigateBreadcrumb(idx) }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                                if (!isLast) {
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (explorerState.isLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = GpmOrange)
                }
            }
        } else if (explorerState.entries.isEmpty()) {
            item {
                GPlusFlatCard(accentStripColor = GpmAmber) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SongQFileLogoIcon(sourceType = SourceType.MFA, modifier = Modifier.size(56.dp))
                        Text(
                            text = "Brak plików .mfa lub .sq w tym folderze",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Wybierz inny folder pamięci przyciskiem powyżej lub włącz głębokie skanowanie, aby przeszukać podfoldery.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(explorerState.entries, key = { it.id }) { entry ->
                when (entry) {
                    is ExplorerEntry.Folder -> {
                        GPlusFlatCard(
                            accentStripColor = GpmAmber,
                            onClick = { onOpenFolder(entry) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(GpmAmber.copy(alpha = 0.18f), MaterialTheme.shapes.small)
                                        .border(1.dp, GpmAmber, MaterialTheme.shapes.small),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = GpmAmber)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(entry.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = "Otwórz folder")
                            }
                        }
                    }

                    is ExplorerEntry.MfaFile -> {
                        val isActive = currentPlayingUriOrPath == entry.uriOrPath
                        GPlusFlatCard(
                            accentStripColor = if (isActive) GpmOrange else GPlusBlue,
                            onClick = { onPlayMfa(entry) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                                    .testTag("explorer_mfa_${entry.name}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SongQFileLogoIcon(sourceType = SourceType.MFA, modifier = Modifier.size(46.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        text = entry.trackTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${entry.name} • ${formatDurationMs(entry.durationMs)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CodecBadge(label = ".MFA", sourceType = SourceType.MFA)
                                        Text(
                                            text = entry.subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(GpmOrange)
                                        .clickable { onPlayMfa(entry) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Odtwórz .mfa", tint = Color.White)
                                }
                            }
                        }
                    }

                    is ExplorerEntry.SqPlaylistFile -> {
                        GPlusFlatCard(
                            accentStripColor = GPlusGreen,
                            onClick = { onPlaySq(entry) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                                    .testTag("explorer_sq_${entry.name}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SongQFileLogoIcon(
                                    sourceType = SourceType.WEB_LINK,
                                    isPlaylistFile = true,
                                    modifier = Modifier.size(46.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        text = entry.playlistName,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = entry.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CodecBadge(label = ".SQ (${entry.trackCount})", sourceType = SourceType.WEB_LINK)
                                        Text(
                                            text = LocalFileExplorer.formatFileSize(entry.sizeBytes),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onImportSq(entry) },
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(Icons.Default.PlaylistAdd, contentDescription = "Importuj .sq")
                                }
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(GPlusGreen)
                                        .clickable { onPlaySq(entry) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Odtwórz .sq", tint = Color.White)
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
private fun RootChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) GpmOrange else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .border(1.dp, if (selected) GpmOrange else MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        )
    }
}
