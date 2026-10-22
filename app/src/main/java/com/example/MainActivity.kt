package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.components.NowPlayingFullScreenOverlay
import com.example.components.SynapseBottomChrome
import com.example.data.MusicDestination
import com.example.features.home.MusicHomeScreen
import com.example.features.library.MusicLibraryScreen
import com.example.features.search.MusicSearchScreen
import com.example.features.settings.MusicSettingsScreen
import com.example.ui.theme.HighlightFavoritePink
import com.example.ui.theme.LabelPrimary
import com.example.ui.theme.MainSurface
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.OledBlack

class MainActivity : ComponentActivity() {

    private val viewModel: SynapseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingAudioIntent(intent)

        setContent {
            MyApplicationTheme {
                SynapseMusicApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingAudioIntent(intent)
    }

    private fun handleIncomingAudioIntent(intent: Intent?) {
        val dataUri = intent?.data ?: return
        if (intent.action == Intent.ACTION_VIEW) {
            viewModel.importLocalAudioUris(listOf(dataUri), playImmediately = true)
        }
    }
}

@Composable
fun SynapseMusicApp(viewModel: SynapseViewModel) {
    val context = LocalContext.current

    val currentDestination by viewModel.currentDestination.collectAsStateWithLifecycle()
    val selectedLibraryCategory by viewModel.selectedLibraryCategory.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val isNowPlayingExpanded by viewModel.isNowPlayingExpanded.collectAsStateWithLifecycle()

    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val allTracks by viewModel.allTracks.collectAsStateWithLifecycle()
    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val recentlyAdded by viewModel.recentlyAdded.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    val audioQuality by viewModel.audioQuality.collectAsStateWithLifecycle()
    val gaplessPlaybackEnabled by viewModel.gaplessPlaybackEnabled.collectAsStateWithLifecycle()
    val protectionReport by viewModel.protectionReport.collectAsStateWithLifecycle()
    val settingsStatusMessage by viewModel.settingsStatusMessage.collectAsStateWithLifecycle()

    var notificationPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationPermissionGranted = granted
    }

    // System Document Picker supporting MP3, M4A/AAC, FLAC, WAV, OGG, and Opus
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importLocalAudioUris(uris, playImmediately = false)
        }
    }

    val launchAudioPicker = {
        audioPickerLauncher.launch(
            arrayOf(
                "audio/*",
                "audio/mpeg",
                "audio/mp4",
                "audio/aac",
                "audio/flac",
                "audio/x-flac",
                "audio/wav",
                "audio/x-wav",
                "audio/ogg",
                "audio/opus",
                "application/ogg"
            )
        )
    }

    if (currentDestination != MusicDestination.HOME && !isNowPlayingExpanded) {
        BackHandler {
            viewModel.selectDestination(MusicDestination.HOME)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
    ) {
        Scaffold(
            containerColor = OledBlack,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                SynapseBottomChrome(
                    currentDestination = currentDestination,
                    playbackState = playbackState,
                    onSelectDestination = { viewModel.selectDestination(it) },
                    onOpenNowPlaying = { viewModel.openNowPlaying() },
                    onPlayPauseToggle = { viewModel.togglePlayPause() },
                    onSkipNext = { viewModel.skipNext() },
                    onToggleFavorite = { viewModel.toggleCurrentTrackFavorite() }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentDestination) {
                    MusicDestination.HOME -> {
                        MusicHomeScreen(
                            allTracks = allTracks,
                            recentlyPlayed = recentlyPlayed,
                            recentlyAdded = recentlyAdded,
                            favoriteTracks = favoriteTracks,
                            albums = albums,
                            playlists = playlists,
                            playbackState = playbackState,
                            onPlayTrackFromList = { track, list ->
                                viewModel.playTrackFromList(track, list)
                            },
                            onShufflePlayAll = { viewModel.shufflePlayAll() },
                            onPlayPlaylist = { viewModel.playPlaylist(it) },
                            onPlayAlbum = { viewModel.playAlbum(it) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onPlayNext = { viewModel.playNextInQueue(it) },
                            onAddToQueue = { viewModel.addToQueueEnd(it) },
                            onAddToPlaylist = { playlistId, track ->
                                viewModel.addTrackToPlaylist(playlistId, track)
                            },
                            onImportAudioFiles = launchAudioPicker,
                            onNavigateToSearch = {
                                viewModel.selectDestination(MusicDestination.SEARCH)
                            },
                            onNavigateToSettings = {
                                viewModel.selectDestination(MusicDestination.SETTINGS)
                            },
                            onOpenNowPlaying = { viewModel.openNowPlaying() }
                        )
                    }

                    MusicDestination.SEARCH -> {
                        MusicSearchScreen(
                            allTracks = allTracks,
                            albums = albums,
                            playlists = playlists,
                            playbackState = playbackState,
                            sortOption = sortOption,
                            onChangeSortOption = { viewModel.setSortOption(it) },
                            onPlayTrackFromList = { track, list ->
                                viewModel.playTrackFromList(track, list)
                            },
                            onPlayAlbum = { viewModel.playAlbum(it) },
                            onPlayPlaylist = { viewModel.playPlaylist(it) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onPlayNext = { viewModel.playNextInQueue(it) },
                            onAddToQueue = { viewModel.addToQueueEnd(it) },
                            onAddToPlaylist = { playlistId, track ->
                                viewModel.addTrackToPlaylist(playlistId, track)
                            }
                        )
                    }

                    MusicDestination.LIBRARY -> {
                        MusicLibraryScreen(
                            allTracks = allTracks,
                            favoriteTracks = favoriteTracks,
                            recentlyPlayed = recentlyPlayed,
                            albums = albums,
                            artists = artists,
                            playlists = playlists,
                            playbackState = playbackState,
                            selectedCategory = selectedLibraryCategory,
                            onSelectCategory = { viewModel.selectLibraryCategory(it) },
                            sortOption = sortOption,
                            onChangeSortOption = { viewModel.setSortOption(it) },
                            onImportAudioFiles = launchAudioPicker,
                            onPlayTrackFromList = { track, list ->
                                viewModel.playTrackFromList(track, list)
                            },
                            onPlayAlbum = { viewModel.playAlbum(it) },
                            onPlayArtist = { viewModel.playArtist(it) },
                            onPlayPlaylist = { viewModel.playPlaylist(it) },
                            onCreatePlaylist = { name, desc ->
                                viewModel.createPlaylist(name, desc)
                            },
                            onDeletePlaylist = { viewModel.deletePlaylist(it) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onPlayNext = { viewModel.playNextInQueue(it) },
                            onAddToQueue = { viewModel.addToQueueEnd(it) },
                            onAddToPlaylist = { playlistId, track ->
                                viewModel.addTrackToPlaylist(playlistId, track)
                            }
                        )
                    }

                    MusicDestination.SETTINGS -> {
                        MusicSettingsScreen(
                            audioQuality = audioQuality,
                            gaplessPlaybackEnabled = gaplessPlaybackEnabled,
                            playbackState = playbackState,
                            protectionReport = protectionReport,
                            notificationPermissionGranted = notificationPermissionGranted,
                            statusMessage = settingsStatusMessage,
                            onSelectAudioQuality = { viewModel.setAudioQuality(it) },
                            onToggleGaplessPlayback = { viewModel.setGaplessPlayback(it) },
                            onSetSleepTimerMinutes = { viewModel.setSleepTimerMinutes(it) },
                            onRequestNotificationPermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onRescanLibrary = { viewModel.rescanLibraryAndVerifyAvailability() },
                            onClearCachedArtwork = { viewModel.clearCachedArtwork() },
                            onToggleContentProtection = { viewModel.setContentProtectionEnabled(it) },
                            onAddCustomBlockedDomain = { viewModel.addCustomBlockedDomain(it) },
                            onRemoveCustomBlockedDomain = { viewModel.removeCustomBlockedDomain(it) }
                        )
                    }
                }

                // Error toast banner if a local file was moved or permission revoked
                val errorMessage = playbackState.errorMessage
                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        color = MainSurface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth()
                            .border(1.dp, HighlightFavoritePink, RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = HighlightFavoritePink
                            )
                            Text(
                                text = errorMessage,
                                color = LabelPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.playbackManager.dismissError() }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss Error",
                                    tint = LabelPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Full-Screen Adaptive Now Playing & Queue Sheet Overlay
        AnimatedVisibility(
            visible = isNowPlayingExpanded && playbackState.currentTrack != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            NowPlayingFullScreenOverlay(
                playbackState = playbackState,
                onDismiss = { viewModel.closeNowPlaying() },
                onPlayPauseToggle = { viewModel.togglePlayPause() },
                onSkipNext = { viewModel.skipNext() },
                onSkipPrevious = { viewModel.skipPrevious() },
                onSeekTo = { viewModel.seekTo(it) },
                onToggleFavorite = { viewModel.toggleCurrentTrackFavorite() },
                onCycleRepeatMode = { viewModel.cycleRepeatMode() },
                onToggleShuffle = { viewModel.toggleShuffle() },
                onVolumeChange = { viewModel.setVolume(it) },
                onPlaybackSpeedChange = { viewModel.setPlaybackSpeed(it) },
                onSetSleepTimerMinutes = { viewModel.setSleepTimerMinutes(it) },
                onSelectQueueTrack = { track ->
                    viewModel.playTrackFromList(track, playbackState.queue)
                },
                onMoveQueueItem = { from, to ->
                    viewModel.moveQueueItem(from, to)
                },
                onRemoveQueueItem = { index ->
                    viewModel.removeQueueItem(index)
                },
                onClearUpcomingQueue = {
                    viewModel.clearUpcomingQueue()
                }
            )
        }
    }
}
