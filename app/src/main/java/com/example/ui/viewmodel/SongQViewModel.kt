package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioMetadataHelper
import com.example.audio.EqPresets
import com.example.audio.EqUiState
import com.example.audio.MfaCodec
import com.example.audio.PlaybackUiState
import com.example.audio.SongQAudioEngine
import com.example.audio.SqPlaylistCodec
import com.example.data.explorer.ExplorerBreadcrumb
import com.example.data.explorer.ExplorerEntry
import com.example.data.explorer.ExplorerFilterMode
import com.example.data.explorer.LocalFileExplorer
import com.example.data.firebase.SongQRepository
import com.example.data.model.ActivityLogDoc
import com.example.data.model.ArtistInfoResult
import com.example.data.model.EqPreset
import com.example.data.model.PlayableTrack
import com.example.data.model.SearchEngineType
import com.example.data.model.SourceType
import com.example.data.model.SqPlaylistDoc
import com.example.data.model.SqTrackDoc
import com.example.data.model.WebTrackResult
import com.example.data.prefs.EqPreferencesStore
import com.example.data.prefs.StoredAudioPrefs
import com.example.data.remote.MusicDownloadProgress
import com.example.data.remote.MusicDownloader
import com.example.data.remote.WebSearchRepository
import com.example.data.update.AppUpdater
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class SearchUiState(
    val query: String = "",
    val selectedEngine: SearchEngineType = SearchEngineType.ITUNES,
    val isLoading: Boolean = false,
    val trackResults: List<WebTrackResult> = emptyList(),
    val artistResults: List<ArtistInfoResult> = emptyList(),
    val errorMessage: String? = null
)

data class ExplorerUiState(
    val breadcrumbs: List<ExplorerBreadcrumb> = emptyList(),
    val entries: List<ExplorerEntry> = emptyList(),
    val filterMode: ExplorerFilterMode = ExplorerFilterMode.ALL_SUPPORTED,
    val query: String = "",
    val recursiveScan: Boolean = true,
    val isLoading: Boolean = false,
    val activeRootLabel: String = "Pamięć Song/Q",
    val savedSafTreeUri: String = ""
)

class SongQViewModel(
    private val repository: SongQRepository?,
    private val webSearchRepository: WebSearchRepository,
    private val musicDownloader: MusicDownloader,
    val audioEngine: SongQAudioEngine,
    private val prefsStore: EqPreferencesStore,
    private val mfaLibraryDir: File,
    private val sqPlaylistsDir: File,
    private val audioDownloadsDir: File,
    private val currentUserId: String
) : ViewModel() {

    val playbackState: StateFlow<PlaybackUiState> = audioEngine.playback
    val eqState: StateFlow<EqUiState> = audioEngine.eq

    val audioPrefs: StateFlow<StoredAudioPrefs> = prefsStore.prefsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), StoredAudioPrefs())

    private val _localTracks = MutableStateFlow<List<PlayableTrack>>(emptyList())
    val localTracks: StateFlow<List<PlayableTrack>> = _localTracks.asStateFlow()

    private val _selectedPlaylistId = MutableStateFlow<String?>(null)
    val selectedPlaylistId: StateFlow<String?> = _selectedPlaylistId.asStateFlow()

    private val _searchState = MutableStateFlow(SearchUiState())
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private val _explorerState = MutableStateFlow(ExplorerUiState())
    val explorerState: StateFlow<ExplorerUiState> = _explorerState.asStateFlow()

    private val _downloadProgress = MutableStateFlow<MusicDownloadProgress?>(null)
    val downloadProgress: StateFlow<MusicDownloadProgress?> = _downloadProgress.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private val _updateStatus = MutableStateFlow<String?>(null)
    val updateStatus: StateFlow<String?> = _updateStatus.asStateFlow()

    // Local .sq backing flows when signed in via custom DirexSoft ID (no Firebase Auth session)
    private val _localPlaylists = MutableStateFlow<List<SqPlaylistDoc>>(emptyList())
    private val _localActivityLogs = MutableStateFlow<List<ActivityLogDoc>>(emptyList())
    private val _localTracksRevision = MutableStateFlow(0)

    val playlistsState: StateFlow<UiState<List<SqPlaylistDoc>>> = if (repository != null) {
        repository.observePlaylists(currentUserId)
            .map<List<SqPlaylistDoc>, UiState<List<SqPlaylistDoc>>> { UiState.Success(it) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                Log.w(TAG, "observePlaylists error", root)
                emit(UiState.Error(root.localizedMessage ?: "Błąd odczytu playlist .sq"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)
    } else {
        _localPlaylists
            .map { UiState.Success(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Success(emptyList()))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedPlaylistTracksState: StateFlow<UiState<List<SqTrackDoc>>> = if (repository != null) {
        _selectedPlaylistId
            .flatMapLatest { plId ->
                if (plId.isNullOrBlank()) {
                    flowOf(UiState.Success(emptyList()))
                } else {
                    repository.observePlaylistTracks(currentUserId, plId)
                        .map<List<SqTrackDoc>, UiState<List<SqTrackDoc>>> { UiState.Success(it) }
                        .catch { error ->
                            val fsErr = generateSequence(error) { it.cause }
                                .filterIsInstance<FirebaseFirestoreException>()
                                .firstOrNull()
                            if (fsErr == null && error is CancellationException) throw error
                            val root = fsErr ?: error
                            emit(UiState.Error(root.localizedMessage ?: "Błąd odczytu utworów playlisty"))
                        }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)
    } else {
        _selectedPlaylistId
            .flatMapLatest { plId ->
                _localTracksRevision.map {
                    if (plId.isNullOrBlank()) {
                        UiState.Success(emptyList())
                    } else {
                        UiState.Success(readLocalPlaylistTracks(plId))
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Success(emptyList()))
    }

    val activityState: StateFlow<UiState<List<ActivityLogDoc>>> = if (repository != null) {
        repository.observeActivity(currentUserId)
            .map<List<ActivityLogDoc>, UiState<List<ActivityLogDoc>>> { UiState.Success(it) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                emit(UiState.Error(root.localizedMessage ?: "Błąd historii aktywności"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)
    } else {
        _localActivityLogs
            .map { UiState.Success(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Success(emptyList()))
    }

    init {
        initializeUserAndLibrary()
    }

    private fun initializeUserAndLibrary() {
        viewModelScope.launch {
            val user = Firebase.auth.currentUser
            val prefs = prefsStore.prefsFlow.firstOrNull()
            if (prefs != null) {
                audioEngine.applyEqState(
                    enabled = prefs.eqEnabled,
                    presetName = prefs.activePresetName,
                    bandGainsDb = prefs.bandGainsDb,
                    bassDb = prefs.bassDb,
                    midsDb = prefs.midsDb,
                    trebleDb = prefs.trebleDb,
                    bassBoostStrength = prefs.bassBoostStrength,
                    virtualizerStrength = prefs.virtualizerStrength,
                    preampDb = prefs.preampDb
                )
            }
            if (user != null && repository != null) {
                repository.ensureUserProfile(
                    displayName = user.displayName ?: "Karol Kopeć",
                    email = user.email,
                    photoUrl = user.photoUrl?.toString(),
                    preferredPreset = audioEngine.eq.value.activePresetName
                )
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (repository == null) {
                refreshLocalSqPlaylists()
            }
            val files = MfaCodec.ensureStarterMfaLibrary(mfaLibraryDir)
            val mfaTracks = files.mapNotNull { file ->
                try {
                    val meta = file.inputStream().use { MfaCodec.readMetadata(it) }
                    PlayableTrack(
                        id = "mfa_${file.name}",
                        title = meta.title,
                        artist = meta.artist,
                        album = meta.album,
                        sourceType = SourceType.MFA,
                        codecLabel = "MFA/PCM-Z (-${meta.compressionRatioPercent}%)",
                        uriOrUrl = file.absolutePath,
                        durationMs = meta.durationMs,
                        sampleRate = meta.sampleRate,
                        compressionRatioPercent = meta.compressionRatioPercent
                    )
                } catch (_: Exception) {
                    null
                }
            }
            audioDownloadsDir.mkdirs()
            val downloadedAndImported = audioDownloadsDir.listFiles { f ->
                f.isFile && f.name.substringAfterLast('.', "").lowercase() in setOf(
                    "mp3", "m4a", "aac", "flac", "ogg", "opus", "wav"
                )
            }?.sortedByDescending { it.lastModified() }?.map { file ->
                val meta = AudioMetadataHelper.extractFromFile(file)
                PlayableTrack(
                    id = "audio_${file.name}",
                    title = meta.title,
                    artist = meta.artist,
                    album = meta.album,
                    sourceType = SourceType.LOCAL,
                    codecLabel = meta.codecLabel,
                    uriOrUrl = file.absolutePath,
                    durationMs = meta.durationMs
                )
            }.orEmpty()

            _localTracks.value = downloadedAndImported + mfaTracks
        }
    }

    private fun localSqFile(playlistId: String): File = File(sqPlaylistsDir, "$playlistId.sq")

    private fun refreshLocalSqPlaylists() {
        sqPlaylistsDir.mkdirs()
        val files = sqPlaylistsDir.listFiles { _, name -> name.endsWith(".sq") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
        _localPlaylists.value = files.mapNotNull { file ->
            try {
                val doc = file.inputStream().use { SqPlaylistCodec.deserialize(it, file.nameWithoutExtension) }
                SqPlaylistDoc(
                    id = file.nameWithoutExtension,
                    userId = currentUserId,
                    name = doc.name,
                    description = doc.description,
                    formatVersion = "SQ1",
                    trackCount = doc.tracks.size.toLong(),
                    totalDurationMs = doc.tracks.sumOf { it.durationMs }
                )
            } catch (_: Exception) {
                null
            }
        }
        _localTracksRevision.update { it + 1 }
    }

    private fun readLocalPlaylistTracks(playlistId: String): List<SqTrackDoc> {
        val file = localSqFile(playlistId)
        if (!file.exists()) return emptyList()
        return try {
            val doc = file.inputStream().use { SqPlaylistCodec.deserialize(it, playlistId) }
            doc.tracks.mapIndexed { idx, track ->
                SqTrackDoc(
                    id = track.id.ifBlank { "${playlistId}_$idx" },
                    userId = currentUserId,
                    playlistId = playlistId,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    sourceType = track.sourceType.name,
                    codecLabel = track.codecLabel,
                    uriOrUrl = track.uriOrUrl,
                    artworkUrl = track.artworkUrl,
                    durationMs = track.durationMs,
                    orderIndex = idx.toLong()
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeLocalSqDocument(playlistId: String, doc: SqPlaylistCodec.SqDocument) {
        sqPlaylistsDir.mkdirs()
        localSqFile(playlistId).outputStream().use { SqPlaylistCodec.serialize(doc, it) }
        refreshLocalSqPlaylists()
    }

    private suspend fun recordActivity(
        queryOrTitle: String,
        artistName: String,
        engine: String,
        targetUrl: String
    ) {
        if (repository != null) {
            repository.logActivity(
                userId = currentUserId,
                queryOrTitle = queryOrTitle,
                artistName = artistName,
                engine = engine,
                targetUrl = targetUrl
            )
        } else {
            val entry = ActivityLogDoc(
                id = "act_${UUID.randomUUID()}",
                userId = currentUserId,
                queryOrTitle = queryOrTitle,
                artistName = artistName,
                engine = engine,
                targetUrl = targetUrl
            )
            _localActivityLogs.update { (listOf(entry) + it).take(40) }
        }
    }

    fun clearBanner() {
        _bannerMessage.value = null
    }

    fun selectPlaylist(playlistId: String?) {
        _selectedPlaylistId.value = playlistId
    }

    fun playFromLibrary(track: PlayableTrack) {
        val list = _localTracks.value
        val idx = list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        audioEngine.setQueueAndPlay(list.ifEmpty { listOf(track) }, idx)
        viewModelScope.launch {
            recordActivity(
                queryOrTitle = track.title,
                artistName = track.artist,
                engine = if (track.sourceType == SourceType.MFA) "MFA_LOCAL" else "SQ_PLAYLIST",
                targetUrl = track.uriOrUrl.take(500)
            )
        }
    }

    fun enqueueTrack(track: PlayableTrack) {
        audioEngine.enqueueTrack(track)
        _bannerMessage.value = "Dodano do kolejki: ${track.title}"
    }

    fun playPlaylistTracks(tracks: List<SqTrackDoc>, startIndex: Int = 0) {
        val playable = tracks.map { it.toPlayableTrack() }
        if (playable.isNotEmpty()) {
            audioEngine.setQueueAndPlay(playable, startIndex)
            val chosen = playable[startIndex.coerceIn(0, playable.lastIndex)]
            viewModelScope.launch {
                recordActivity(
                    queryOrTitle = chosen.title,
                    artistName = chosen.artist,
                    engine = "SQ_PLAYLIST",
                    targetUrl = chosen.uriOrUrl.take(500)
                )
            }
        }
    }

    fun createSqPlaylist(name: String, description: String) {
        viewModelScope.launch {
            if (repository != null) {
                val res = repository.createPlaylist(currentUserId, name, description)
                res.onSuccess { newId ->
                    _selectedPlaylistId.value = newId
                    _bannerMessage.value = "Utworzono playlistę .sq: $name"
                }.onFailure { err ->
                    _bannerMessage.value = "Błąd tworzenia playlisty: ${err.localizedMessage}"
                }
            } else {
                withContext(Dispatchers.IO) {
                    val newId = "sq_${UUID.randomUUID().toString().replace("-", "").take(12)}"
                    writeLocalSqDocument(
                        newId,
                        SqPlaylistCodec.SqDocument(
                            name = name.trim().ifBlank { "Playlista Song/Q" },
                            description = description.trim(),
                            tracks = emptyList()
                        )
                    )
                    _selectedPlaylistId.value = newId
                }
                _bannerMessage.value = "Utworzono lokalną playlistę .sq: $name"
            }
        }
    }

    fun deleteSqPlaylist(playlistId: String) {
        viewModelScope.launch {
            if (repository != null) {
                repository.deletePlaylist(currentUserId, playlistId)
                    .onSuccess {
                        if (_selectedPlaylistId.value == playlistId) {
                            _selectedPlaylistId.value = null
                        }
                        _bannerMessage.value = "Usunięto playlistę .sq"
                    }
                    .onFailure { _bannerMessage.value = it.localizedMessage }
            } else {
                withContext(Dispatchers.IO) {
                    localSqFile(playlistId).delete()
                    refreshLocalSqPlaylists()
                }
                if (_selectedPlaylistId.value == playlistId) {
                    _selectedPlaylistId.value = null
                }
                _bannerMessage.value = "Usunięto lokalną playlistę .sq"
            }
        }
    }

    fun addTrackToPlaylist(playlistId: String, track: PlayableTrack) {
        viewModelScope.launch {
            if (repository != null) {
                val currentCount = (selectedPlaylistTracksState.value as? UiState.Success)?.data?.size?.toLong() ?: 0L
                repository.addTrackToPlaylist(currentUserId, playlistId, track, currentCount)
                    .onSuccess {
                        _bannerMessage.value = "Zapisano w .sq (0 KB duplikatów): ${track.title}"
                    }
                    .onFailure { _bannerMessage.value = "Błąd zapisu do .sq: ${it.localizedMessage}" }
            } else {
                withContext(Dispatchers.IO) {
                    val file = localSqFile(playlistId)
                    if (file.exists()) {
                        val doc = file.inputStream().use { SqPlaylistCodec.deserialize(it, playlistId) }
                        writeLocalSqDocument(playlistId, doc.copy(tracks = doc.tracks + track))
                    }
                }
                _bannerMessage.value = "Zapisano w .sq (0 KB duplikatów): ${track.title}"
            }
        }
    }

    fun moveTrackInPlaylist(playlistId: String, tracks: List<SqTrackDoc>, fromIndex: Int, toIndex: Int) {
        if (fromIndex !in tracks.indices || toIndex !in tracks.indices || fromIndex == toIndex) return
        viewModelScope.launch {
            if (repository != null) {
                val first = tracks[fromIndex]
                val second = tracks[toIndex]
                repository.updateTrackOrder(currentUserId, playlistId, first.id, second.orderIndex)
                repository.updateTrackOrder(currentUserId, playlistId, second.id, first.orderIndex)
            } else {
                withContext(Dispatchers.IO) {
                    val file = localSqFile(playlistId)
                    if (file.exists()) {
                        val doc = file.inputStream().use { SqPlaylistCodec.deserialize(it, playlistId) }
                        val mutable = doc.tracks.toMutableList()
                        if (fromIndex in mutable.indices && toIndex in mutable.indices) {
                            val item = mutable.removeAt(fromIndex)
                            mutable.add(toIndex, item)
                            writeLocalSqDocument(playlistId, doc.copy(tracks = mutable))
                        }
                    }
                }
            }
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, track: SqTrackDoc) {
        viewModelScope.launch {
            if (repository != null) {
                repository.deleteTrackFromPlaylist(currentUserId, playlistId, track.id, track.durationMs)
                    .onSuccess { _bannerMessage.value = "Usunięto z playlisty .sq: ${track.title}" }
                    .onFailure { _bannerMessage.value = it.localizedMessage }
            } else {
                withContext(Dispatchers.IO) {
                    val file = localSqFile(playlistId)
                    if (file.exists()) {
                        val doc = file.inputStream().use { SqPlaylistCodec.deserialize(it, playlistId) }
                        val updated = doc.tracks.filterIndexed { idx, t ->
                            (t.id.ifBlank { "${playlistId}_$idx" }) != track.id
                        }
                        writeLocalSqDocument(playlistId, doc.copy(tracks = updated))
                    }
                }
                _bannerMessage.value = "Usunięto z playlisty .sq: ${track.title}"
            }
        }
    }

    fun exportPlaylistAsSq(context: Context, uri: Uri, playlist: SqPlaylistDoc, tracks: List<SqTrackDoc>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val doc = SqPlaylistCodec.SqDocument(
                    name = playlist.name,
                    description = playlist.description,
                    tracks = tracks.map { it.toPlayableTrack() }
                )
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    SqPlaylistCodec.serialize(doc, out)
                }
                _bannerMessage.value = "Wyeksportowano plik ${playlist.name}.sq"
            } catch (e: Exception) {
                _bannerMessage.value = "Błąd eksportu .sq: ${e.localizedMessage}"
            }
        }
    }

    fun handleIncomingFileUri(context: Context, uri: Uri) {
        val displayName = resolveFileName(context, uri)
        val lower = displayName.lowercase()
        when {
            lower.endsWith(".sq") -> importSqFromUri(context, uri, displayName)
            lower.endsWith(".mfa") -> importMfaFromUri(context, uri, displayName)
            lower.endsWith(".wav") -> convertWavToMfaFromUri(context, uri, displayName)
            else -> importGenericAudioUri(context, uri, displayName)
        }
    }

    private fun importSqFromUri(context: Context, uri: Uri, displayName: String) {
        viewModelScope.launch {
            try {
                val doc = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        SqPlaylistCodec.deserialize(input, displayName.removeSuffix(".sq"))
                    } ?: throw IllegalStateException("Nie można odczytać pliku .sq")
                }
                if (repository != null) {
                    val res = repository.createPlaylist(currentUserId, doc.name, doc.description)
                    res.onSuccess { newPlaylistId ->
                        _selectedPlaylistId.value = newPlaylistId
                        doc.tracks.forEachIndexed { idx, track ->
                            repository.addTrackToPlaylist(currentUserId, newPlaylistId, track, idx.toLong())
                        }
                        _bannerMessage.value = "Zaimportowano playlistę .sq (${doc.tracks.size} utworów)"
                    }
                } else {
                    val newId = "sq_${UUID.randomUUID().toString().replace("-", "").take(12)}"
                    withContext(Dispatchers.IO) {
                        writeLocalSqDocument(newId, doc)
                    }
                    _selectedPlaylistId.value = newId
                    _bannerMessage.value = "Zaimportowano playlistę .sq (${doc.tracks.size} utworów)"
                }
            } catch (e: Exception) {
                _bannerMessage.value = "Błąd importu .sq: ${e.localizedMessage}"
            }
        }
    }

    private fun importMfaFromUri(context: Context, uri: Uri, displayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val destFile = File(mfaLibraryDir, "imported_${System.currentTimeMillis()}_${displayName}")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { out -> input.copyTo(out) }
                }
                val meta = destFile.inputStream().use { MfaCodec.readMetadata(it) }
                val track = PlayableTrack(
                    id = "mfa_${destFile.name}",
                    title = meta.title,
                    artist = meta.artist,
                    album = meta.album,
                    sourceType = SourceType.MFA,
                    codecLabel = "MFA/PCM-Z (-${meta.compressionRatioPercent}%)",
                    uriOrUrl = destFile.absolutePath,
                    durationMs = meta.durationMs,
                    sampleRate = meta.sampleRate,
                    compressionRatioPercent = meta.compressionRatioPercent
                )
                _localTracks.update { listOf(track) + it }
                withContext(Dispatchers.Main) {
                    playFromLibrary(track)
                    _bannerMessage.value = "Zaimportowano i odtworzono .mfa: ${meta.title}"
                }
            } catch (e: Exception) {
                _bannerMessage.value = "Błąd pliku .mfa: ${e.localizedMessage}"
            }
        }
    }

    fun convertWavToMfaFromUri(context: Context, uri: Uri, displayName: String = resolveFileName(context, uri)) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val baseName = displayName.substringBeforeLast('.').ifBlank { "Konwersja_${System.currentTimeMillis()}" }
                val destFile = File(mfaLibraryDir, "${baseName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")}.mfa")
                val meta = context.contentResolver.openInputStream(uri)?.use { wavIn ->
                    destFile.outputStream().use { mfaOut ->
                        MfaCodec.convertWavToMfa(wavIn, mfaOut, baseName, "Import WAV -> Song/Q")
                    }
                } ?: throw IllegalStateException("Nie można otworzyć pliku WAV")

                val track = PlayableTrack(
                    id = "mfa_${destFile.name}",
                    title = meta.title,
                    artist = meta.artist,
                    album = meta.album,
                    sourceType = SourceType.MFA,
                    codecLabel = "MFA/PCM-Z (-${meta.compressionRatioPercent}%)",
                    uriOrUrl = destFile.absolutePath,
                    durationMs = meta.durationMs,
                    sampleRate = meta.sampleRate,
                    compressionRatioPercent = meta.compressionRatioPercent
                )
                _localTracks.update { listOf(track) + it }
                _bannerMessage.value = "Skompresowano do .mfa (-${meta.compressionRatioPercent}%): ${meta.title}"
            } catch (e: Exception) {
                _bannerMessage.value = "Błąd konwersji do .mfa: ${e.localizedMessage}"
            }
        }
    }

    fun exportMfaToUri(context: Context, destUri: Uri, track: PlayableTrack) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val src = File(track.uriOrUrl)
                if (!src.exists()) throw IllegalStateException("Plik .mfa nie istnieje lokalnie")
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    src.inputStream().use { it.copyTo(out) }
                }
                _bannerMessage.value = "Wyeksportowano ${track.title}.mfa"
            } catch (e: Exception) {
                _bannerMessage.value = "Błąd eksportu .mfa: ${e.localizedMessage}"
            }
        }
    }

    private fun importGenericAudioUri(context: Context, uri: Uri, displayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                audioDownloadsDir.mkdirs()
                val ext = displayName.substringAfterLast('.', "").lowercase()
                    .takeIf { it in setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav") }
                    ?: AudioMetadataHelper.detectCodecBadge(displayName, context.contentResolver.getType(uri)).second
                val cleanBase = displayName.substringBeforeLast('.')
                    .replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
                    .trim()
                    .replace(Regex("\\s+"), "_")
                    .take(48)
                    .ifBlank { "songq_audio" }
                val destFile = File(audioDownloadsDir, "${cleanBase}_${System.currentTimeMillis() % 100000}.$ext")
                val copied = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { out -> input.copyTo(out) }
                    }
                    destFile.exists() && destFile.length() > 0L
                }.getOrDefault(false)

                val meta = if (copied) {
                    AudioMetadataHelper.extractFromFile(destFile, displayName.substringBeforeLast('.'))
                } else {
                    AudioMetadataHelper.extractFromUri(context, uri, displayName)
                }

                val track = PlayableTrack(
                    id = "local_${UUID.randomUUID()}",
                    title = meta.title,
                    artist = meta.artist,
                    album = meta.album,
                    sourceType = SourceType.LOCAL,
                    codecLabel = meta.codecLabel,
                    uriOrUrl = if (copied) destFile.absolutePath else uri.toString(),
                    durationMs = meta.durationMs
                )
                _localTracks.update { listOf(track) + it }
                withContext(Dispatchers.Main) {
                    playFromLibrary(track)
                    _bannerMessage.value = "Dodano i odtworzono (${meta.codecLabel}): ${meta.title}"
                }
            } catch (e: Exception) {
                _bannerMessage.value = "Błąd importu audio: ${e.localizedMessage}"
            }
        }
    }

    fun clearDownloadProgress() {
        _downloadProgress.value = null
    }

    fun downloadWebResult(result: WebTrackResult) {
        downloadFromUrlOrQuery(
            urlOrQuery = result.externalUrl,
            titleHint = result.title,
            artistHint = result.artist,
            albumHint = result.album,
            previewUrl = result.previewUrl,
            artworkUrl = result.artworkUrl
        )
    }

    fun downloadFromUrlOrQuery(
        urlOrQuery: String,
        titleHint: String = "",
        artistHint: String = "",
        albumHint: String = "Pobrane w Song/Q",
        previewUrl: String = "",
        artworkUrl: String = ""
    ) {
        val cleaned = urlOrQuery.trim()
        if (cleaned.isBlank() && titleHint.isBlank()) {
            _bannerMessage.value = "Wklej link YouTube / strony muzycznej lub nazwę utworu."
            return
        }
        viewModelScope.launch {
            val resolvedTitle = titleHint.ifBlank {
                if (cleaned.startsWith("http")) "Pobieranie z sieci" else cleaned
            }
            val resolvedUrl = if (cleaned.startsWith("http", ignoreCase = true)) cleaned else ""
            val outcome = musicDownloader.downloadTrack(
                titleHint = resolvedTitle,
                artistHint = artistHint,
                albumHint = albumHint,
                targetUrl = resolvedUrl,
                fallbackPreviewUrl = previewUrl,
                artworkUrl = artworkUrl,
                destDir = audioDownloadsDir,
                onProgress = { progress ->
                    _downloadProgress.value = progress
                }
            )
            outcome.onSuccess { res ->
                _localTracks.update { listOf(res.track) + it }
                _downloadProgress.value = MusicDownloadProgress(
                    title = res.track.title,
                    sourceLabel = res.sourceNote,
                    progressPercent = 100,
                    isDownloading = false,
                    statusText = "Gotowe offline: ${res.track.codecLabel}"
                )
                _bannerMessage.value = "Pobrano (${res.sourceNote}): ${res.track.title}"
                recordActivity(
                    queryOrTitle = res.track.title,
                    artistName = res.track.artist,
                    engine = "YOUTUBE",
                    targetUrl = res.track.uriOrUrl.take(500)
                )
            }.onFailure { err ->
                _downloadProgress.value = null
                _bannerMessage.value = err.localizedMessage ?: "Błąd pobierania muzyki"
            }
        }
    }

    // --- Web / API Search ---

    fun setSearchQuery(q: String) {
        _searchState.update { it.copy(query = q) }
    }

    fun selectSearchEngine(engine: SearchEngineType) {
        _searchState.update { it.copy(selectedEngine = engine) }
        if (_searchState.value.query.isNotBlank()) {
            performWebSearch()
        }
    }

    fun performWebSearch() {
        val query = _searchState.value.query.trim()
        val engine = _searchState.value.selectedEngine
        if (query.isBlank()) return
        viewModelScope.launch {
            _searchState.update { it.copy(isLoading = true, errorMessage = null) }
            val tracksRes = webSearchRepository.searchTracks(query, engine)
            val artistsRes = webSearchRepository.searchArtists(query)
            _searchState.update { state ->
                state.copy(
                    isLoading = false,
                    trackResults = tracksRes.getOrDefault(emptyList()),
                    artistResults = artistsRes.getOrDefault(emptyList()),
                    errorMessage = tracksRes.exceptionOrNull()?.localizedMessage
                )
            }
            recordActivity(
                queryOrTitle = query,
                artistName = artistsRes.getOrNull()?.firstOrNull()?.name ?: "",
                engine = engine.name,
                targetUrl = webSearchRepository.buildSearchUrl(query, engine)
            )
        }
    }

    fun playWebPreview(result: WebTrackResult) {
        if (result.previewUrl.isNotBlank()) {
            val playable = PlayableTrack(
                id = "web_${result.id}",
                title = result.title,
                artist = result.artist,
                album = result.album,
                sourceType = SourceType.ITUNES_PREVIEW,
                codecLabel = "WEB-API STREAM",
                uriOrUrl = result.previewUrl,
                artworkUrl = result.artworkUrl,
                durationMs = 30000L
            )
            audioEngine.setQueueAndPlay(listOf(playable), 0)
            viewModelScope.launch {
                recordActivity(
                    queryOrTitle = result.title,
                    artistName = result.artist,
                    engine = result.engine.name,
                    targetUrl = result.externalUrl
                )
            }
        } else {
            _bannerMessage.value = "Ten wynik używa linku zewnętrznego — otwórz w ${result.engine.displayName} lub zapisz w .sq"
        }
    }

    fun addWebResultToPlaylist(result: WebTrackResult, playlistId: String) {
        val track = PlayableTrack(
            id = "sqweb_${result.id}",
            title = result.title,
            artist = result.artist,
            album = result.album,
            sourceType = if (result.previewUrl.isNotBlank()) SourceType.ITUNES_PREVIEW else SourceType.WEB_LINK,
            codecLabel = "${result.engine.badge} • SQ-LINK",
            uriOrUrl = result.previewUrl.ifBlank { result.externalUrl },
            artworkUrl = result.artworkUrl,
            durationMs = result.durationMs
        )
        addTrackToPlaylist(playlistId, track)
    }

    // --- EQ & Preferences ---

    fun selectEqPreset(preset: EqPreset) {
        audioEngine.selectPreset(preset)
        persistEq()
        if (repository != null) {
            viewModelScope.launch {
                repository.updatePreferredPreset(currentUserId, preset.name)
            }
        }
    }

    fun updateToneSliders(bassDb: Float, midsDb: Float, trebleDb: Float) {
        audioEngine.updateToneSliders(bassDb = bassDb, midsDb = midsDb, trebleDb = trebleDb)
        persistEq()
    }

    fun resetEqToFlat() {
        selectEqPreset(EqPresets.all.first())
    }

    fun updateEqBand(index: Int, gainDb: Float) {
        audioEngine.updateBandGain(index, gainDb)
        persistEq()
    }

    fun updateBassBoost(strength: Int) {
        audioEngine.applyEqState(bassBoostStrength = strength)
        persistEq()
    }

    fun updateVirtualizer(strength: Int) {
        audioEngine.applyEqState(virtualizerStrength = strength)
        persistEq()
    }

    fun updatePreamp(preampDb: Float) {
        audioEngine.applyEqState(preampDb = preampDb)
        persistEq()
    }

    fun toggleEqEnabled(enabled: Boolean) {
        audioEngine.applyEqState(enabled = enabled)
        persistEq()
    }

    fun toggleDarkTheme(dark: Boolean) {
        viewModelScope.launch {
            prefsStore.saveDarkTheme(dark)
        }
    }

    fun togglePlayMusicMode(enabled: Boolean) {
        viewModelScope.launch {
            prefsStore.savePlayMusicMode(enabled)
        }
    }

    fun savePreferredMusicApp(packageName: String, label: String) {
        viewModelScope.launch {
            prefsStore.savePreferredMusicApp(packageName, label)
            _bannerMessage.value = "Powiązano z aplikacją muzyczną: $label"
        }
    }

    // --- Google Play Music Mix & Background Modes ---

    fun playImFeelingLuckyMix() {
        val pool = _localTracks.value.shuffled()
        if (pool.isEmpty()) {
            _bannerMessage.value = "Dodaj lub odtwórz pliki .mfa, aby uruchomić składankę Szczęśliwy Traf"
            return
        }
        if (!audioEngine.playback.value.shuffleEnabled) {
            audioEngine.toggleShuffle()
        }
        audioEngine.setQueueAndPlay(pool, 0)
        _bannerMessage.value = "Składanka Szczęśliwy Traf (Google Play Music): ${pool.size} utworów"
    }

    // --- Local File System Explorer (.mfa & .sq) ---

    fun initExplorerIfNeeded(context: Context) {
        if (_explorerState.value.breadcrumbs.isNotEmpty()) return
        viewModelScope.launch {
            val savedTree = prefsStore.prefsFlow.firstOrNull()?.explorerTreeUri.orEmpty()
            val rootCrumb = ExplorerBreadcrumb(
                label = "Pamięć Song/Q",
                filePath = context.filesDir.absolutePath
            )
            _explorerState.update {
                it.copy(
                    breadcrumbs = listOf(rootCrumb),
                    activeRootLabel = rootCrumb.label,
                    savedSafTreeUri = savedTree
                )
            }
            refreshExplorer(context)
        }
    }

    fun openExplorerAppRoot(context: Context, root: ExplorerBreadcrumb, recursive: Boolean = false) {
        _explorerState.update {
            it.copy(
                breadcrumbs = listOf(root),
                activeRootLabel = root.label,
                recursiveScan = recursive
            )
        }
        refreshExplorer(context)
    }

    fun openExplorerMfaRoot(context: Context) {
        openExplorerAppRoot(
            context,
            ExplorerBreadcrumb("Biblioteka .MFA", filePath = mfaLibraryDir.absolutePath),
            recursive = false
        )
    }

    fun openExplorerSqRoot(context: Context) {
        openExplorerAppRoot(
            context,
            ExplorerBreadcrumb("Playlisty .SQ", filePath = sqPlaylistsDir.absolutePath),
            recursive = false
        )
    }

    fun onSafTreePicked(context: Context, treeUri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val uriStr = treeUri.toString()
        viewModelScope.launch {
            prefsStore.saveExplorerTreeUri(uriStr)
        }
        val rootCrumb = ExplorerBreadcrumb(
            label = "Folder urządzenia (SAF)",
            treeUri = uriStr,
            documentId = null
        )
        _explorerState.update {
            it.copy(
                breadcrumbs = listOf(rootCrumb),
                activeRootLabel = rootCrumb.label,
                savedSafTreeUri = uriStr,
                recursiveScan = false
            )
        }
        refreshExplorer(context)
    }

    fun openSavedSafTree(context: Context) {
        val saved = _explorerState.value.savedSafTreeUri
        if (saved.isBlank()) return
        val rootCrumb = ExplorerBreadcrumb(
            label = "Folder urządzenia (SAF)",
            treeUri = saved,
            documentId = null
        )
        _explorerState.update {
            it.copy(
                breadcrumbs = listOf(rootCrumb),
                activeRootLabel = rootCrumb.label,
                recursiveScan = false
            )
        }
        refreshExplorer(context)
    }

    fun openExplorerFolder(context: Context, folder: ExplorerEntry.Folder) {
        val current = _explorerState.value
        val curTop = current.breadcrumbs.lastOrNull()
        val nextCrumb = when {
            folder.filePath != null -> ExplorerBreadcrumb(label = folder.name, filePath = folder.filePath)
            folder.documentUri != null && curTop?.treeUri != null -> ExplorerBreadcrumb(
                label = folder.name,
                treeUri = curTop.treeUri,
                documentId = folder.documentUri
            )
            else -> return
        }
        _explorerState.update {
            it.copy(
                breadcrumbs = it.breadcrumbs + nextCrumb,
                recursiveScan = false
            )
        }
        refreshExplorer(context)
    }

    fun navigateExplorerBreadcrumb(context: Context, index: Int) {
        val crumbs = _explorerState.value.breadcrumbs
        if (index !in crumbs.indices) return
        _explorerState.update {
            it.copy(breadcrumbs = crumbs.take(index + 1))
        }
        refreshExplorer(context)
    }

    fun setExplorerFilter(context: Context, mode: ExplorerFilterMode) {
        _explorerState.update { it.copy(filterMode = mode) }
        refreshExplorer(context)
    }

    fun setExplorerQuery(context: Context, query: String) {
        _explorerState.update { it.copy(query = query) }
        refreshExplorer(context)
    }

    fun toggleExplorerRecursive(context: Context) {
        _explorerState.update { it.copy(recursiveScan = !it.recursiveScan) }
        refreshExplorer(context)
    }

    fun refreshExplorer(context: Context) {
        val state = _explorerState.value
        val target = state.breadcrumbs.lastOrNull() ?: return
        viewModelScope.launch {
            _explorerState.update { it.copy(isLoading = true) }
            val items = withContext(Dispatchers.IO) {
                when {
                    target.filePath != null -> LocalFileExplorer.listLocalDirectory(
                        directory = File(target.filePath),
                        filterMode = state.filterMode,
                        query = state.query,
                        recursive = state.recursiveScan
                    )
                    target.treeUri != null -> LocalFileExplorer.listSafTreeDirectory(
                        context = context,
                        treeUri = Uri.parse(target.treeUri),
                        documentId = target.documentId,
                        filterMode = state.filterMode,
                        query = state.query,
                        recursive = state.recursiveScan
                    )
                    else -> emptyList()
                }
            }
            _explorerState.update { it.copy(isLoading = false, entries = items) }
        }
    }

    fun playExplorerMfa(entry: ExplorerEntry.MfaFile) {
        val allMfaInView = _explorerState.value.entries
            .filterIsInstance<ExplorerEntry.MfaFile>()
            .map { it.toPlayableTrack() }
        val chosen = entry.toPlayableTrack()
        val queue = allMfaInView.ifEmpty { listOf(chosen) }
        val startIdx = queue.indexOfFirst { it.uriOrUrl == chosen.uriOrUrl }.coerceAtLeast(0)
        audioEngine.setQueueAndPlay(queue, startIdx)
        _bannerMessage.value = "Odtwarzanie .MFA z Eksploratora: ${entry.trackTitle}"
        viewModelScope.launch {
            recordActivity(
                queryOrTitle = entry.trackTitle,
                artistName = entry.artist,
                engine = "MFA_LOCAL",
                targetUrl = entry.uriOrPath.take(500)
            )
        }
    }

    fun playExplorerSq(context: Context, entry: ExplorerEntry.SqPlaylistFile) {
        viewModelScope.launch {
            val tracks = withContext(Dispatchers.IO) {
                LocalFileExplorer.readSqTracksFromEntry(context, entry)
            }
            if (tracks.isEmpty()) {
                _bannerMessage.value = "Playlista ${entry.playlistName}.sq jest pusta lub zawiera tylko linki"
            } else {
                audioEngine.setQueueAndPlay(tracks, 0)
                _bannerMessage.value = "Odtwarzanie .SQ (${tracks.size} utworów): ${entry.playlistName}"
                recordActivity(
                    queryOrTitle = entry.playlistName,
                    artistName = "${tracks.size} utworów .SQ",
                    engine = "SQ_PLAYLIST",
                    targetUrl = entry.uriOrPath.take(500)
                )
            }
        }
    }

    fun importExplorerSqToPlaylists(context: Context, entry: ExplorerEntry.SqPlaylistFile) {
        viewModelScope.launch {
            val tracks = withContext(Dispatchers.IO) {
                LocalFileExplorer.readSqTracksFromEntry(context, entry)
            }
            if (repository != null) {
                repository.createPlaylist(currentUserId, entry.playlistName, entry.description)
                    .onSuccess { newId ->
                        _selectedPlaylistId.value = newId
                        tracks.forEachIndexed { idx, t ->
                            repository.addTrackToPlaylist(currentUserId, newId, t, idx.toLong())
                        }
                        _bannerMessage.value = "Zaimportowano ${entry.playlistName}.sq (${tracks.size} utworów)"
                    }
            } else {
                val newId = "sq_${UUID.randomUUID().toString().replace("-", "").take(12)}"
                withContext(Dispatchers.IO) {
                    writeLocalSqDocument(
                        newId,
                        SqPlaylistCodec.SqDocument(
                            name = entry.playlistName,
                            description = entry.description,
                            tracks = tracks
                        )
                    )
                }
                _selectedPlaylistId.value = newId
                _bannerMessage.value = "Dodano do playlist .SQ: ${entry.playlistName}"
            }
        }
    }

    private fun persistEq() {
        val s = audioEngine.eq.value
        viewModelScope.launch {
            prefsStore.saveEqSettings(
                presetName = s.activePresetName,
                bandGainsDb = s.bands.map { it.levelDb },
                bassDb = s.bassDb,
                midsDb = s.midsDb,
                trebleDb = s.trebleDb,
                bassBoostStrength = s.bassBoostStrength,
                virtualizerStrength = s.virtualizerStrength,
                preampDb = s.preampDb,
                eqEnabled = s.enabled
            )
        }
    }

    // --- APK Updater ---

    fun handleUpdateApkUri(context: Context, uri: Uri) {
        when (val result = AppUpdater.stageAndInstallUpdate(context, uri)) {
            is AppUpdater.UpdateLaunchResult.InstallerLaunched -> {
                _updateStatus.value = "Uruchomiono instalator aktualizacji: ${result.info.fileName} (wersja ${result.info.targetVersion})"
            }
            is AppUpdater.UpdateLaunchResult.NeedsUnknownSourcesPermission -> {
                _updateStatus.value = "Zezwól na instalację z tego źródła dla Song/Q, a następnie wybierz ${result.info.fileName} ponownie."
            }
            is AppUpdater.UpdateLaunchResult.InvalidFile -> {
                _updateStatus.value = result.reason
            }
        }
    }

    private fun resolveFileName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val name = c.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "audio_songq"
    }

    private companion object {
        const val TAG = "SongQViewModel"
    }
}
