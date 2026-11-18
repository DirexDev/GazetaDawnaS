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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.ActivityLogDoc
import com.example.ui.components.GPlusFlatCard
import com.example.ui.theme.GPlusAmber
import com.example.ui.theme.GPlusBlue
import com.example.ui.theme.GPlusGreen
import com.example.ui.theme.GPlusRed
import com.example.ui.theme.GpmOrange
import com.example.ui.viewmodel.UiState

@Composable
fun SettingsSystemScreen(
    userName: String,
    userEmail: String,
    isGoogleCloudSynced: Boolean,
    darkTheme: Boolean,
    playMusicMode: Boolean,
    preferredMusicAppLabel: String,
    updateStatus: String?,
    activityState: UiState<List<ActivityLogDoc>>,
    onToggleDarkTheme: (Boolean) -> Unit,
    onTogglePlayMusicMode: (Boolean) -> Unit,
    onClearPreferredMusicApp: () -> Unit,
    onPickUpdateApk: () -> Unit,
    onLinkGoogleSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Account, Theme & Style Switch Card
        item {
            GPlusFlatCard(accentStripColor = if (playMusicMode) GpmOrange else GPlusBlue) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(if (isGoogleCloudSynced) GPlusBlue else MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color.White)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(userName.ifBlank { "Konto Song/Q" }, style = MaterialTheme.typography.titleMedium)
                            val modeSubtitle = if (isGoogleCloudSynced) {
                                "${userEmail.ifBlank { "Konto Google" }} • Synchronizacja w chmurze"
                            } else {
                                "${userEmail.ifBlank { "DirexSoft ID" }} • Konto własne Song/Q (lokalne .sq)"
                            }
                            Text(modeSubtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (!isGoogleCloudSynced) {
                        Button(
                            onClick = onLinkGoogleSignIn,
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.buttonColors(containerColor = GPlusBlue),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Zaloguj przez Google (włącz synchronizację)")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Styl Google Play Music (Nowoczesny)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Wyłącz, aby wrócić do klasycznego trybu Google+ Retro Flat",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = playMusicMode,
                            onCheckedChange = onTogglePlayMusicMode,
                            colors = SwitchDefaults.colors(checkedTrackColor = GpmOrange),
                            modifier = Modifier.testTag("play_music_mode_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tryb Ciemny Studio", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Przełącz płaski styl dzienny / nocny",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = darkTheme,
                            onCheckedChange = onToggleDarkTheme,
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("theme_switch")
                        )
                    }

                    if (preferredMusicAppLabel.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = GpmOrange)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Domyślna aplikacja zewnętrzna:", style = MaterialTheme.typography.labelSmall)
                                    Text(preferredMusicAppLabel, style = MaterialTheme.typography.titleSmall)
                                }
                            }
                            OutlinedButton(onClick = onClearPreferredMusicApp, shape = MaterialTheme.shapes.small) {
                                Text("Resetuj")
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onSignOut,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isGoogleCloudSynced) "Wyloguj się z konta Google" else "Wyloguj się z konta Song/Q")
                    }
                }
            }
        }

        // APK Self-Update Card (SongQ.<wersja>.update.apk)
        item {
            GPlusFlatCard(accentStripColor = GPlusGreen) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(40.dp).background(GPlusGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = Color.White)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("SYSTEM AKTUALIZACJI SONG/Q", style = MaterialTheme.typography.titleLarge)
                            Text("Aktualna wersja zainstalowana: 1.15", style = MaterialTheme.typography.labelMedium, color = GPlusGreen)
                        }
                    }

                    Text(
                        "Wybierz pobrany plik instalacyjny o nazwie w formacie SongQ.<danawersja>.update.apk (np. SongQ.1.16.update.apk). Po wybraniu aplikacja natychmiast rozpocznie aktualizację.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = onPickUpdateApk,
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(containerColor = GPlusGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("pick_update_apk_button")
                    ) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Wybierz plik SongQ.*.update.apk")
                    }

                    if (!updateStatus.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline)
                                .padding(10.dp)
                        ) {
                            Text(updateStatus, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // DirexSoft Corporations & Karol Kopeć Credits
        item {
            GPlusFlatCard(accentStripColor = GPlusAmber) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Business, contentDescription = null, tint = GPlusRed)
                        Spacer(Modifier.width(8.dp))
                        Text("DIREXSOFT CORPORATIONS", style = MaterialTheme.typography.titleLarge)
                    }
                    Text("Aplikacja: Song/Q • Wersja 1.15 (Android 8.0+)", style = MaterialTheme.typography.bodyLarge)
                    Text("Założyciel, właściciel i jedyny pracownik: Karol Kopeć", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Silnik audio: .MFA (MediaFileAudio PCM-Z), .SQ (Playlisty) + MP3/M4A/FLAC/OGG/OPUS/WAV + Korektor 5-Band EQ.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Activity Stream
        item {
            Text(
                text = "OSTATNIA AKTYWNOŚĆ W SONG/Q",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        when (activityState) {
            is UiState.Loading -> item {
                Text("Ładowanie aktywności...", style = MaterialTheme.typography.bodyMedium)
            }
            is UiState.Error -> item {
                Text(activityState.message, color = GPlusRed, style = MaterialTheme.typography.bodyMedium)
            }
            is UiState.Success -> {
                if (activityState.data.isEmpty()) {
                    item {
                        GPlusFlatCard {
                            Text(
                                "Brak historii aktywności — wyszukaj utwór, pobierz z sieci lub odtwórz plik .mfa.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                } else {
                    items(activityState.data, key = { it.id }) { act ->
                        GPlusFlatCard(accentStripColor = MaterialTheme.colorScheme.primary) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(act.queryOrTitle, style = MaterialTheme.typography.titleMedium)
                                val sub = buildString {
                                    if (act.artistName.isNotBlank()) append("${act.artistName} • ")
                                    append(act.engine)
                                }
                                Text(sub, style = MaterialTheme.typography.labelSmall, color = GPlusBlue)
                            }
                        }
                    }
                }
            }
        }
    }
}
