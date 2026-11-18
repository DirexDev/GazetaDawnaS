package com.example

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.audio.SongQAudioEngine
import com.example.audio.SongQPlaybackService
import com.example.data.auth.GoogleAuthManager
import com.example.data.auth.LocalAccountStore
import com.example.data.auth.LocalSongQAccount
import com.example.data.firebase.SongQRepository
import com.example.data.model.PlayableTrack
import com.example.data.model.SearchEngineType
import com.example.data.model.SqPlaylistDoc
import com.example.data.model.SqTrackDoc
import com.example.data.prefs.EqPreferencesStore
import com.example.data.prefs.StoredAudioPrefs
import com.example.data.remote.MusicDownloader
import com.example.data.remote.WebSearchRepository
import com.example.ui.components.GPlusHeaderBar
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.SongQGoogleSearchBar
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.EqualizerScreen
import com.example.ui.screens.FileExplorerScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsSystemScreen
import com.example.ui.theme.GpmOrange
import com.example.ui.theme.SongQTheme
import com.example.ui.theme.SongQUiStyle
import com.example.ui.viewmodel.SongQViewModel
import com.example.ui.viewmodel.UiState
import com.example.widget.SongQWidgetBridge
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

private const val ROUTE_LIBRARY = "library"
private const val ROUTE_EXPLORER = "explorer"
private const val ROUTE_PLAYLISTS = "playlists"
private const val ROUTE_SEARCH = "search"
private const val ROUTE_EQUALIZER = "equalizer"
private const val ROUTE_SYSTEM = "system"

private data class SongQTab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val testTag: String
)

private val primaryBottomTabs = listOf(
    SongQTab(ROUTE_LIBRARY, R.string.nav_library, Icons.Default.LibraryMusic, "tab_library"),
    SongQTab(ROUTE_EXPLORER, R.string.nav_explorer, Icons.Default.FolderOpen, "tab_explorer"),
    SongQTab(ROUTE_PLAYLISTS, R.string.nav_playlists, Icons.Default.QueueMusic, "tab_playlists"),
    SongQTab(ROUTE_SEARCH, R.string.nav_search, Icons.Default.Search, "tab_search"),
    SongQTab(ROUTE_EQUALIZER, R.string.nav_equalizer, Icons.Default.GraphicEq, "tab_equalizer")
)

private val wideRailTabs = primaryBottomTabs + SongQTab(
    ROUTE_SYSTEM,
    R.string.nav_settings,
    Icons.Default.Settings,
    "tab_system"
)

class MainActivity : ComponentActivity() {
    private val pendingFileUri = MutableStateFlow<Uri?>(null)
    private val pendingSearchEngine = MutableStateFlow<SearchEngineType?>(null)
    private val pendingStartTab = MutableStateFlow<String?>(null)
    private val pendingFocusSearch = MutableStateFlow(false)

    private val serviceEngineFlow = MutableStateFlow<SongQAudioEngine?>(null)
    private var serviceBound = false
    private lateinit var prefsStore: EqPreferencesStore
    private lateinit var localAccountStore: LocalAccountStore

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? SongQPlaybackService.LocalBinder
            if (binder != null) {
                serviceEngineFlow.value = binder.engine
                serviceBound = true
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceEngineFlow.value = null
            serviceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        prefsStore = EqPreferencesStore(applicationContext)
        localAccountStore = LocalAccountStore(applicationContext)
        processIntent(intent)

        val svcIntent = Intent(this, SongQPlaybackService::class.java)
        runCatching { startService(svcIntent) }
        bindService(svcIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        setContent {
            val prefs by prefsStore.prefsFlow.collectAsStateWithLifecycle(
                initialValue = StoredAudioPrefs()
            )
            val boundEngine by serviceEngineFlow.collectAsStateWithLifecycle()
            val uiStyle = if (prefs.playMusicMode) {
                SongQUiStyle.PLAY_MUSIC_MODERN
            } else {
                SongQUiStyle.GOOGLE_PLUS_RETRO
            }

            SongQTheme(darkTheme = prefs.darkTheme, uiStyle = uiStyle) {
                val activeEngine = boundEngine
                if (activeEngine == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(color = GpmOrange)
                    }
                } else {
                    SongQRoot(
                        audioEngine = activeEngine,
                        serviceConnected = true,
                        prefsStore = prefsStore,
                        localAccountStore = localAccountStore,
                        pendingFileUriFlow = pendingFileUri,
                        pendingStartTabFlow = pendingStartTab,
                        pendingSearchEngineFlow = pendingSearchEngine,
                        pendingFocusSearchFlow = pendingFocusSearch,
                        onConsumePendingFile = { pendingFileUri.value = null },
                        onConsumePendingTab = {
                            pendingStartTab.value = null
                            pendingSearchEngine.value = null
                            pendingFocusSearch.value = false
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processIntent(intent)
    }

    private fun processIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            pendingFileUri.value = intent.data
        }
        val tab = intent.getStringExtra(SongQWidgetBridge.EXTRA_START_TAB)
        if (!tab.isNullOrBlank()) {
            pendingStartTab.value = tab
        }
        val engineName = intent.getStringExtra(SongQWidgetBridge.EXTRA_SEARCH_ENGINE)
        if (!engineName.isNullOrBlank()) {
            pendingSearchEngine.value = SearchEngineType.entries.firstOrNull { it.name == engineName }
        }
        if (intent.getBooleanExtra(SongQWidgetBridge.EXTRA_FOCUS_SEARCH, false)) {
            pendingFocusSearch.value = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            runCatching { unbindService(serviceConnection) }
            serviceBound = false
        }
    }
}

@Composable
private fun SongQRoot(
    audioEngine: SongQAudioEngine,
    serviceConnected: Boolean,
    prefsStore: EqPreferencesStore,
    localAccountStore: LocalAccountStore,
    pendingFileUriFlow: MutableStateFlow<Uri?>,
    pendingStartTabFlow: MutableStateFlow<String?>,
    pendingSearchEngineFlow: MutableStateFlow<SearchEngineType?>,
    pendingFocusSearchFlow: MutableStateFlow<Boolean>,
    onConsumePendingFile: () -> Unit,
    onConsumePendingTab: () -> Unit
) {
    val auth = remember { Firebase.auth }
    val firebaseUser by GoogleAuthManager.authStateFlow(auth)
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val localAccount by localAccountStore.activeAccountFlow
        .collectAsStateWithLifecycle(initialValue = null)

    val sessionKey = firebaseUser?.uid ?: localAccount?.id
    if (sessionKey == null) {
        AuthScreen(
            localAccountStore = localAccountStore,
            onAuthSuccess = {}
        )
    } else {
        key(sessionKey) {
            AuthenticatedSongQShell(
                sessionKey = sessionKey,
                firebaseUser = firebaseUser,
                localAccount = localAccount,
                localAccountStore = localAccountStore,
                audioEngine = audioEngine,
                serviceConnected = serviceConnected,
                prefsStore = prefsStore,
                pendingFileUriFlow = pendingFileUriFlow,
                pendingStartTabFlow = pendingStartTabFlow,
                pendingSearchEngineFlow = pendingSearchEngineFlow,
                pendingFocusSearchFlow = pendingFocusSearchFlow,
                onConsumePendingFile = onConsumePendingFile,
                onConsumePendingTab = onConsumePendingTab
            )
        }
    }
}

@Composable
private fun AuthenticatedSongQShell(
    sessionKey: String,
    firebaseUser: FirebaseUser?,
    localAccount: LocalSongQAccount?,
    localAccountStore: LocalAccountStore,
    audioEngine: SongQAudioEngine,
    serviceConnected: Boolean,
    prefsStore: EqPreferencesStore,
    pendingFileUriFlow: MutableStateFlow<Uri?>,
    pendingStartTabFlow: MutableStateFlow<String?>,
    pendingSearchEngineFlow: MutableStateFlow<SearchEngineType?>,
    pendingFocusSearchFlow: MutableStateFlow<Boolean>,
    onConsumePendingFile: () -> Unit,
    onConsumePendingTab: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val searchBarFocusRequester = remember { FocusRequester() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val viewModel: SongQViewModel = viewModel(
        key = sessionKey,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val repo = if (firebaseUser != null) {
                    val dbId = app.getString(R.string.firestore_database_id)
                    SongQRepository(FirebaseFirestore.getInstance(dbId))
                } else {
                    null
                }
                val mfaDir = File(app.filesDir, "mfa_library").apply { mkdirs() }
                val sqDir = File(app.filesDir, "sq_playlists_$sessionKey").apply { mkdirs() }
                val downloadsDir = File(app.filesDir, "audio_library").apply { mkdirs() }
                SongQViewModel(
                    repository = repo,
                    webSearchRepository = WebSearchRepository(),
                    musicDownloader = MusicDownloader(),
                    audioEngine = audioEngine,
                    prefsStore = prefsStore,
                    mfaLibraryDir = mfaDir,
                    sqPlaylistsDir = sqDir,
                    audioDownloadsDir = downloadsDir,
                    currentUserId = sessionKey
                )
            }
        }
    )

    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route ?: ROUTE_LIBRARY

    val localTracks by viewModel.localTracks.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val eqState by viewModel.eqState.collectAsStateWithLifecycle()
    val audioPrefs by viewModel.audioPrefs.collectAsStateWithLifecycle()
    val playlistsState by viewModel.playlistsState.collectAsStateWithLifecycle()
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsStateWithLifecycle()
    val selectedTracksState by viewModel.selectedPlaylistTracksState.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val explorerState by viewModel.explorerState.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val activityState by viewModel.activityState.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()
    val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()

    val pendingFile by pendingFileUriFlow.collectAsStateWithLifecycle()
    val pendingTab by pendingStartTabFlow.collectAsStateWithLifecycle()
    val pendingEngine by pendingSearchEngineFlow.collectAsStateWithLifecycle()
    val pendingFocus by pendingFocusSearchFlow.collectAsStateWithLifecycle()

    var showNowPlaying by remember { mutableStateOf(false) }
    var pendingExportSq by remember { mutableStateOf<Pair<SqPlaylistDoc, List<SqTrackDoc>>?>(null) }
    var pendingExportMfa by remember { mutableStateOf<PlayableTrack?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(bannerMessage) {
        val msg = bannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    LaunchedEffect(pendingFile) {
        val uri = pendingFile
        if (uri != null) {
            viewModel.handleIncomingFileUri(context, uri)
            onConsumePendingFile()
        }
    }

    LaunchedEffect(pendingTab, pendingEngine, pendingFocus) {
        if (pendingTab == "search" || pendingFocus) {
            pendingEngine?.let { viewModel.selectSearchEngine(it) }
            navController.navigate(ROUTE_SEARCH) {
                launchSingleTop = true
                restoreState = true
            }
            if (pendingFocus) {
                runCatching { searchBarFocusRequester.requestFocus() }
            }
            onConsumePendingTab()
        }
    }

    // SAF File Pickers & OpenDocumentTree (Zero broad storage permissions)
    val openAudioOrMfaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.handleIncomingFileUri(context, uri)
    }

    val openSafTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.onSafTreePicked(context, uri)
    }

    val convertWavLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.convertWavToMfaFromUri(context, uri)
    }

    val importSqLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.handleIncomingFileUri(context, uri)
    }

    val exportSqLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val data = pendingExportSq
        if (uri != null && data != null) {
            viewModel.exportPlaylistAsSq(context, uri, data.first, data.second)
        }
        pendingExportSq = null
    }

    val exportMfaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val track = pendingExportMfa
        if (uri != null && track != null) {
            viewModel.exportMfaToUri(context, uri, track)
        }
        pendingExportMfa = null
    }

    val pickUpdateApkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.handleUpdateApkUri(context, uri)
    }

    val playlists = (playlistsState as? UiState.Success)?.data ?: emptyList()

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(ROUTE_LIBRARY) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 720.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .statusBarsPadding()
                ) {
                    GPlusHeaderBar(
                        title = "Song/Q",
                        subtitle = if (audioPrefs.playMusicMode) {
                            "Google Play Music Mode • DirexSoft"
                        } else {
                            "DirexSoft Corporations • Karol Kopeć"
                        },
                        badgeText = "v1.15",
                        playMusicMode = audioPrefs.playMusicMode,
                        onOpenSettings = { navigateToTab(ROUTE_SYSTEM) }
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        SongQGoogleSearchBar(
                            query = searchState.query,
                            onQueryChange = { viewModel.setSearchQuery(it) },
                            onSearch = {
                                navigateToTab(ROUTE_SEARCH)
                                viewModel.performWebSearch()
                            },
                            focusRequester = searchBarFocusRequester
                        )
                    }
                }
            },
            bottomBar = {
                if (!isWideScreen) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .navigationBarsPadding()
                    ) {
                        MiniPlayerBar(
                            playbackState = playbackState,
                            playMusicMode = audioPrefs.playMusicMode,
                            onTogglePlayPause = { audioEngine.togglePlayPause() },
                            onNext = { audioEngine.skipNext() },
                            onPrevious = { audioEngine.skipPrevious() },
                            onExpand = { showNowPlaying = true }
                        )
                        GPlusBottomTabBar(
                            currentRoute = currentRoute,
                            onSelectTab = { route -> navigateToTab(route) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .border(1.dp, MaterialTheme.colorScheme.outline)
                            .navigationBarsPadding()
                    ) {
                        Spacer(Modifier.height(12.dp))
                        wideRailTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                                label = { Text(stringResource(tab.labelRes), maxLines = 1) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Box(modifier = Modifier.weight(1f)) {
                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_LIBRARY
                        ) {
                            composable(ROUTE_LIBRARY) {
                                LibraryScreen(
                                    tracks = localTracks,
                                    currentTrackId = playbackState.currentTrack?.id,
                                    playlists = playlists,
                                    playMusicMode = audioPrefs.playMusicMode,
                                    onPlayTrack = { viewModel.playFromLibrary(it) },
                                    onPlayImFeelingLuckyMix = { viewModel.playImFeelingLuckyMix() },
                                    onEnqueueTrack = { viewModel.enqueueTrack(it) },
                                    onAddToPlaylist = { plId, track -> viewModel.addTrackToPlaylist(plId, track) },
                                    onOpenAudioOrMfaPicker = {
                                        openAudioOrMfaLauncher.launch(arrayOf("audio/*", "application/octet-stream", "*/*"))
                                    },
                                    onConvertWavToMfaPicker = {
                                        convertWavLauncher.launch(arrayOf("audio/wav", "audio/x-wav", "*/*"))
                                    },
                                    onOpenExplorer = { navigateToTab(ROUTE_EXPLORER) },
                                    onExportMfaTrack = { track ->
                                        pendingExportMfa = track
                                        exportMfaLauncher.launch("${track.title.replace(" ", "_")}.mfa")
                                    }
                                )
                            }

                            composable(ROUTE_EXPLORER) {
                                FileExplorerScreen(
                                    explorerState = explorerState,
                                    currentPlayingUriOrPath = playbackState.currentTrack?.uriOrUrl,
                                    onInitExplorer = { viewModel.initExplorerIfNeeded(context) },
                                    onSelectAppRoot = { root, recursive ->
                                        viewModel.openExplorerAppRoot(context, root, recursive)
                                    },
                                    onOpenMfaRoot = { viewModel.openExplorerMfaRoot(context) },
                                    onOpenSqRoot = { viewModel.openExplorerSqRoot(context) },
                                    onPickSafDirectory = { openSafTreeLauncher.launch(null) },
                                    onOpenSavedSafDirectory = { viewModel.openSavedSafTree(context) },
                                    onSelectFilter = { mode -> viewModel.setExplorerFilter(context, mode) },
                                    onQueryChange = { q -> viewModel.setExplorerQuery(context, q) },
                                    onToggleRecursive = { viewModel.toggleExplorerRecursive(context) },
                                    onNavigateBreadcrumb = { idx -> viewModel.navigateExplorerBreadcrumb(context, idx) },
                                    onOpenFolder = { folder -> viewModel.openExplorerFolder(context, folder) },
                                    onPlayMfa = { entry -> viewModel.playExplorerMfa(entry) },
                                    onPlaySq = { entry -> viewModel.playExplorerSq(context, entry) },
                                    onImportSq = { entry -> viewModel.importExplorerSqToPlaylists(context, entry) },
                                    onRefresh = { viewModel.refreshExplorer(context) }
                                )
                            }

                            composable(ROUTE_PLAYLISTS) {
                                PlaylistsScreen(
                                    playlistsState = playlistsState,
                                    selectedPlaylistId = selectedPlaylistId,
                                    selectedTracksState = selectedTracksState,
                                    onSelectPlaylist = { viewModel.selectPlaylist(it) },
                                    onCreatePlaylist = { name, desc -> viewModel.createSqPlaylist(name, desc) },
                                    onDeletePlaylist = { viewModel.deleteSqPlaylist(it) },
                                    onImportSqFile = {
                                        importSqLauncher.launch(arrayOf("application/octet-stream", "application/json", "*/*"))
                                    },
                                    onExportSqFile = { pl, trList ->
                                        pendingExportSq = pl to trList
                                        exportSqLauncher.launch("${pl.name.replace(" ", "_")}.sq")
                                    },
                                    onPlayPlaylist = { trList, idx -> viewModel.playPlaylistTracks(trList, idx) },
                                    onMoveTrack = { plId, list, from, to ->
                                        viewModel.moveTrackInPlaylist(plId, list, from, to)
                                    },
                                    onRemoveTrack = { plId, tr -> viewModel.removeTrackFromPlaylist(plId, tr) }
                                )
                            }

                            composable(ROUTE_SEARCH) {
                                SearchScreen(
                                    searchState = searchState,
                                    playlists = playlists,
                                    downloadProgress = downloadProgress,
                                    preferredMusicAppPackage = audioPrefs.preferredMusicAppPackage,
                                    preferredMusicAppLabel = audioPrefs.preferredMusicAppLabel,
                                    onQueryChange = { viewModel.setSearchQuery(it) },
                                    onSelectEngine = { viewModel.selectSearchEngine(it) },
                                    onSearch = { viewModel.performWebSearch() },
                                    onPlayPreview = { viewModel.playWebPreview(it) },
                                    onDownloadTrack = { viewModel.downloadWebResult(it) },
                                    onDownloadFromUrlOrQuery = { input -> viewModel.downloadFromUrlOrQuery(input) },
                                    onDismissDownloadProgress = { viewModel.clearDownloadProgress() },
                                    onSavePreferredMusicApp = { pkg, label ->
                                        viewModel.savePreferredMusicApp(pkg, label)
                                    },
                                    onAddResultToPlaylist = { res, plId ->
                                        viewModel.addWebResultToPlaylist(res, plId)
                                    }
                                )
                            }

                            composable(ROUTE_EQUALIZER) {
                                EqualizerScreen(
                                    eqState = eqState,
                                    serviceConnected = serviceConnected,
                                    onToggleEnabled = { viewModel.toggleEqEnabled(it) },
                                    onSelectPreset = { viewModel.selectEqPreset(it) },
                                    onResetFlat = { viewModel.resetEqToFlat() },
                                    onUpdateToneSliders = { b, m, t ->
                                        viewModel.updateToneSliders(b, m, t)
                                    },
                                    onUpdateBand = { idx, gain -> viewModel.updateEqBand(idx, gain) },
                                    onUpdateBassBoost = { viewModel.updateBassBoost(it) },
                                    onUpdateVirtualizer = { viewModel.updateVirtualizer(it) },
                                    onUpdatePreamp = { viewModel.updatePreamp(it) }
                                )
                            }

                            composable(ROUTE_SYSTEM) {
                                BackHandler {
                                    navigateToTab(ROUTE_LIBRARY)
                                }
                                SettingsSystemScreen(
                                    userName = firebaseUser?.displayName
                                        ?: localAccount?.displayName
                                        ?: "Karol Kopeć",
                                    userEmail = firebaseUser?.email
                                        ?: localAccount?.login
                                        ?: "",
                                    isGoogleCloudSynced = firebaseUser != null,
                                    darkTheme = audioPrefs.darkTheme,
                                    playMusicMode = audioPrefs.playMusicMode,
                                    preferredMusicAppLabel = audioPrefs.preferredMusicAppLabel,
                                    updateStatus = updateStatus,
                                    activityState = activityState,
                                    onToggleDarkTheme = { viewModel.toggleDarkTheme(it) },
                                    onTogglePlayMusicMode = { viewModel.togglePlayMusicMode(it) },
                                    onClearPreferredMusicApp = { viewModel.savePreferredMusicApp("", "") },
                                    onPickUpdateApk = {
                                        pickUpdateApkLauncher.launch(
                                            arrayOf("application/vnd.android.package-archive", "*/*")
                                        )
                                    },
                                    onLinkGoogleSignIn = {
                                        GoogleAuthManager.onGoogleSignInClicked(
                                            context = context,
                                            credentialManager = credentialManager,
                                            onAuthSuccess = {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Połączono z kontem Google — synchronizacja .sq aktywna!")
                                                }
                                            },
                                            onAuthError = { msg ->
                                                scope.launch { snackbarHostState.showSnackbar(msg) }
                                            },
                                            onAuthCancelled = {},
                                            scope = scope
                                        )
                                    },
                                    onSignOut = {
                                        if (firebaseUser != null) {
                                            GoogleAuthManager.signOut(credentialManager, scope)
                                        }
                                        scope.launch {
                                            localAccountStore.signOutLocal()
                                        }
                                    }
                                )
                            }
                        }
                    }

                    if (isWideScreen) {
                        MiniPlayerBar(
                            playbackState = playbackState,
                            playMusicMode = audioPrefs.playMusicMode,
                            onTogglePlayPause = { audioEngine.togglePlayPause() },
                            onNext = { audioEngine.skipNext() },
                            onPrevious = { audioEngine.skipPrevious() },
                            onExpand = { showNowPlaying = true },
                            modifier = Modifier.navigationBarsPadding()
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showNowPlaying,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            NowPlayingScreen(
                playbackState = playbackState,
                eqState = eqState,
                playMusicMode = audioPrefs.playMusicMode,
                onDismiss = { showNowPlaying = false },
                onTogglePlayPause = { audioEngine.togglePlayPause() },
                onNext = { audioEngine.skipNext() },
                onPrevious = { audioEngine.skipPrevious() },
                onSeek = { audioEngine.seekTo(it) },
                onToggleShuffle = { audioEngine.toggleShuffle() },
                onCycleRepeat = { audioEngine.cycleRepeatMode() },
                onSetSleepTimerMinutes = { mins -> audioEngine.setSleepTimerMinutes(mins) },
                onAdjustBass = { viewModel.updateBassBoost(it) },
                onOpenEqualizer = { navigateToTab(ROUTE_EQUALIZER) },
                onPlayQueueItem = { audioEngine.playTrack(it) },
                onRemoveQueueItem = { audioEngine.removeFromQueue(it) }
            )
        }
    }
}

@Composable
private fun GPlusBottomTabBar(
    currentRoute: String,
    onSelectTab: (String) -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RectangleShape),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        primaryBottomTabs.forEach { tab ->
            val selected = currentRoute == tab.route
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp)
                    .background(if (selected) accent.copy(alpha = 0.09f) else Color.Transparent)
                    .clickable { onSelectTab(tab.route) }
                    .testTag(tab.testTag),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(if (selected) accent else Color.Transparent)
                )
                Icon(
                    imageVector = tab.icon,
                    contentDescription = stringResource(tab.labelRes),
                    tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = stringResource(tab.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
    }
}
