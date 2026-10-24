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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.NotificationsActive
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.components.BeatifyAuthOverlay
import com.example.components.NowPlayingFullScreenOverlay
import com.example.components.SynapseBottomChrome
import com.example.data.MusicDestination
import com.example.features.home.MusicHomeScreen
import com.example.features.library.MusicLibraryScreen
import com.example.features.radio.MusicRadioScreen
import com.example.features.search.MusicSearchScreen
import com.example.features.settings.MusicSettingsScreen
import com.example.services.LocalMediaStoreScanner
import com.example.ui.theme.BeatifyAppleCrimson
import com.example.ui.theme.HighlightFavoritePink
import com.example.ui.theme.LabelPrimary
import com.example.ui.theme.MainSurface
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.OledBlack
import com.example.ui.theme.SecondaryTextZinc

class MainActivity : ComponentActivity() {

    private val viewModel: SynapseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            MyApplicationTheme {
                SynapseMusicApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val dataUri = intent?.data ?: return
        val scheme = dataUri.scheme?.lowercase()
        if (scheme == "beatify") {
            viewModel.handleDeepLinkUri(dataUri.toString())
        } else if (intent.action == Intent.ACTION_VIEW) {
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
    val isAuthSheetVisible by viewModel.isAuthSheetVisible.collectAsStateWithLifecycle()
    val isAuthenticating by viewModel.isAuthenticating.collectAsStateWithLifecycle()
    val authErrorMessage by viewModel.authErrorMessage.collectAsStateWithLifecycle()

    val currentUserUid by viewModel.currentUserUid.collectAsStateWithLifecycle()
    val currentUserEmail by viewModel.currentUserEmail.collectAsStateWithLifecycle()
    val currentUserDisplayName by viewModel.currentUserDisplayName.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val radioStations by viewModel.radioStations.collectAsStateWithLifecycle()
    val podcastEpisodes by viewModel.podcastEpisodes.collectAsStateWithLifecycle()
    val otaManifest by viewModel.otaManifest.collectAsStateWithLifecycle()
    val notificationCategories by viewModel.notificationCategories.collectAsStateWithLifecycle()
    val marketingOptIn by viewModel.marketingOptIn.collectAsStateWithLifecycle()
    val pushNotifications by viewModel.pushNotifications.collectAsStateWithLifecycle()
    val activeForegroundBanner by viewModel.activeForegroundBanner.collectAsStateWithLifecycle()

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

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchUiState by viewModel.searchUiState.collectAsStateWithLifecycle()
    val remoteTrackResults by viewModel.remoteTrackResults.collectAsStateWithLifecycle()
    val remoteArtistResults by viewModel.remoteArtistResults.collectAsStateWithLifecycle()
    val remoteAlbumResults by viewModel.remoteAlbumResults.collectAsStateWithLifecycle()
    val searchErrorMessage by viewModel.searchErrorMessage.collectAsStateWithLifecycle()

    val catalogChannels by viewModel.catalogChannels.collectAsStateWithLifecycle()
    val latestRssVideos by viewModel.latestRssVideos.collectAsStateWithLifecycle()
    val selectedChannel by viewModel.selectedChannel.collectAsStateWithLifecycle()
    val selectedChannelVideos by viewModel.selectedChannelVideos.collectAsStateWithLifecycle()
    val totalCatalogChannels by viewModel.totalCatalogChannels.collectAsStateWithLifecycle()
    val totalSyncedVideos by viewModel.totalSyncedVideos.collectAsStateWithLifecycle()
    val isSyncingRssFeeds by viewModel.isSyncingRssFeeds.collectAsStateWithLifecycle()
    val musicRadioFeed by viewModel.musicRadioFeed.collectAsStateWithLifecycle()
    val isLoadingMusicRadioFeed by viewModel.isLoadingMusicRadioFeed.collectAsStateWithLifecycle()
    val cachedMusicEntries by viewModel.cachedMusicEntries.collectAsStateWithLifecycle()
    val favoriteMusicEntries by viewModel.favoriteMusicEntries.collectAsStateWithLifecycle()
    val youTubeAccountConnected by viewModel.youTubeAccountConnected.collectAsStateWithLifecycle()
    val youTubeConnectedChannelLabel by viewModel.youTubeConnectedChannelLabel.collectAsStateWithLifecycle()
    val currentLyrics by viewModel.currentLyrics.collectAsStateWithLifecycle()
    val isLoadingLyrics by viewModel.isLoadingLyrics.collectAsStateWithLifecycle()

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

    var audioPermissionGranted by remember {
        mutableStateOf(LocalMediaStoreScanner.hasAudioPermission(context))
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        audioPermissionGranted = granted
        if (granted) {
            viewModel.scanDeviceMediaStoreAudio()
        }
    }

    val requestOrRunMediaStoreScan = {
        if (LocalMediaStoreScanner.hasAudioPermission(context)) {
            audioPermissionGranted = true
            viewModel.scanDeviceMediaStoreAudio()
        } else {
            audioPermissionLauncher.launch(LocalMediaStoreScanner.requiredAudioPermission())
        }
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

    if (isAuthSheetVisible) {
        BackHandler {
            viewModel.closeAuthSheet()
        }
    } else if (currentDestination != MusicDestination.HOME && !isNowPlayingExpanded) {
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
                    onSkipPrevious = { viewModel.skipPrevious() },
                    onSkipNext = { viewModel.skipNext() },
                    onToggleFavorite = { viewModel.toggleCurrentTrackFavorite() },
                    onSeekTo = { positionMs -> viewModel.seekTo(positionMs) },
                    isNowPlayingExpanded = isNowPlayingExpanded,
                    lyricsPayload = currentLyrics
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
                            currentUserUid = currentUserUid,
                            musicRadioFeed = musicRadioFeed,
                            onPlayMusicRadioEntry = { entry ->
                                viewModel.playMusicRadioEntry(entry)
                            },
                            onToggleMusicRadioEntryFavorite = { entry ->
                                viewModel.toggleMusicRadioEntryFavorite(entry)
                            },
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
                            onNavigateToSearch = { initialQuery ->
                                viewModel.selectDestination(MusicDestination.SEARCH, initialQuery)
                            },
                            onNavigateToRadio = {
                                viewModel.selectDestination(MusicDestination.RADIO)
                            },
                            onNavigateToSettings = {
                                viewModel.selectDestination(MusicDestination.SETTINGS)
                            },
                            onOpenAuthSheet = { viewModel.openAuthSheet() },
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
                            searchQuery = searchQuery,
                            searchUiState = searchUiState,
                            remoteTrackResults = remoteTrackResults,
                            remoteArtistResults = remoteArtistResults,
                            remoteAlbumResults = remoteAlbumResults,
                            searchErrorMessage = searchErrorMessage,
                            catalogChannels = catalogChannels,
                            latestRssVideos = latestRssVideos,
                            selectedChannel = selectedChannel,
                            selectedChannelVideos = selectedChannelVideos,
                            totalCatalogChannels = totalCatalogChannels,
                            totalSyncedVideos = totalSyncedVideos,
                            isSyncingRssFeeds = isSyncingRssFeeds,
                            musicEntries = cachedMusicEntries,
                            onSearchQueryChange = { viewModel.onSearchQueryChanged(it) },
                            onSearchFocusChange = { viewModel.onSearchFocusChanged(it) },
                            onRetryRemoteSearch = { viewModel.retryRemoteSearch() },
                            onTriggerRssFeedSync = { viewModel.triggerRssFeedSync() },
                            onSelectChannel = { viewModel.selectDiscoveryChannel(it) },
                            onPlayRssVideo = { viewModel.playRssVideo(it) },
                            onPlayMusicEntry = { viewModel.playMusicEntry(it) },
                            onToggleMusicEntryFavorite = { viewModel.toggleMusicEntryFavorite(it.id) },
                            onChangeSortOption = { viewModel.setSortOption(it) },
                            onPlayTrackFromList = { track, list ->
                                viewModel.playTrackFromList(track, list)
                            },
                            onPlayRemoteSearchResult = { result, list ->
                                viewModel.playRemoteSearchResult(result, list)
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

                    MusicDestination.RADIO -> {
                        MusicRadioScreen(
                            radioStations = radioStations,
                            podcastEpisodes = podcastEpisodes,
                            subscriptions = subscriptions,
                            playbackState = playbackState,
                            onPlayRadioStation = { viewModel.playRadioStation(it) },
                            onPlayPodcastEpisode = { viewModel.playPodcastEpisode(it) },
                            onToggleSubscription = { provider, resourceId, title ->
                                viewModel.toggleSubscription(provider, resourceId, title)
                            },
                            musicRadioFeed = musicRadioFeed,
                            isLoadingMusicRadioFeed = isLoadingMusicRadioFeed,
                            onRefreshMusicRadioFeed = { playlistId ->
                                viewModel.refreshMusicRadioFeed(playlistId)
                            },
                            onPlayMusicRadioEntry = { entry ->
                                viewModel.playMusicRadioEntry(entry)
                            },
                            onToggleMusicRadioEntryFavorite = { entry ->
                                viewModel.toggleMusicRadioEntryFavorite(entry)
                            }
                        )
                    }

                    MusicDestination.LIBRARY -> {
                        MusicLibraryScreen(
                            allTracks = allTracks,
                            favoriteTracks = favoriteTracks,
                            favoriteMusicEntries = favoriteMusicEntries,
                            recentlyPlayed = recentlyPlayed,
                            albums = albums,
                            artists = artists,
                            playlists = playlists,
                            playbackState = playbackState,
                            selectedCategory = selectedLibraryCategory,
                            onSelectCategory = { viewModel.selectLibraryCategory(it) },
                            sortOption = sortOption,
                            onChangeSortOption = { viewModel.setSortOption(it) },
                            audioPermissionGranted = audioPermissionGranted,
                            youTubeAccountConnected = youTubeAccountConnected,
                            onImportAudioFiles = launchAudioPicker,
                            onScanLocalMediaStore = requestOrRunMediaStoreScan,
                            onConnectYouTubeLibrary = { viewModel.connectAndSyncYouTubeLibrary() },
                            onPlayTrackFromList = { track, list ->
                                viewModel.playTrackFromList(track, list)
                            },
                            onPlayMusicEntry = { viewModel.playMusicEntry(it) },
                            onToggleMusicEntryFavorite = { viewModel.toggleMusicEntryFavorite(it.id) },
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
                            currentUserUid = currentUserUid,
                            currentUserEmail = currentUserEmail,
                            currentUserDisplayName = currentUserDisplayName,
                            syncStatus = syncStatus,
                            otaManifest = otaManifest,
                            notificationCategories = notificationCategories,
                            marketingOptIn = marketingOptIn,
                            pushNotifications = pushNotifications,
                            youTubeAccountConnected = youTubeAccountConnected,
                            youTubeConnectedChannelLabel = youTubeConnectedChannelLabel,
                            onOpenAuthSheet = { viewModel.openAuthSheet() },
                            onTriggerCloudSync = { viewModel.syncLocalDataToCloudNow() },
                            onConnectYouTubeAccount = { viewModel.connectAndSyncYouTubeLibrary() },
                            onDisconnectYouTubeAccount = { viewModel.disconnectYouTubeAccount() },
                            onCheckOtaUpdate = { viewModel.checkForOtaUpdate() },
                            onSwitchOtaChannel = { viewModel.switchOtaChannel(it) },
                            onRollbackOtaBundle = { viewModel.rollbackOtaBundle() },
                            onToggleNotificationCategory = { viewModel.toggleNotificationCategory(it) },
                            onToggleMarketingOptIn = { viewModel.setMarketingOptIn(it) },
                            onTriggerSampleNotification = { viewModel.triggerSampleFcmNotification(it) },
                            onNavigateDeepLink = { viewModel.handleDeepLinkUri(it) },
                            onSelectAudioQuality = { viewModel.setAudioQuality(it) },
                            onToggleGaplessPlayback = { viewModel.setGaplessPlayback(it) },
                            onSetCrossfadeDurationSeconds = { viewModel.setCrossfadeDurationSeconds(it) },
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

                // Foreground FCM Push Notification Banner (tappable deep-link routing)
                val fgBanner = activeForegroundBanner
                if (fgBanner != null) {
                    Surface(
                        color = MainSurface,
                        shape = RoundedCornerShape(18.dp),
                        shadowElevation = 12.dp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth()
                            .border(1.dp, BeatifyAppleCrimson, RoundedCornerShape(18.dp))
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { viewModel.handleDeepLinkUri(fgBanner.deepLink) }
                            .testTag("foreground_fcm_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = BeatifyAppleCrimson
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = fgBanner.title,
                                    color = LabelPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = fgBanner.body,
                                    color = SecondaryTextZinc,
                                    fontSize = 11.sp
                                )
                            }
                            IconButton(
                                onClick = { viewModel.dismissForegroundBanner() }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss Notification",
                                    tint = LabelPrimary
                                )
                            }
                        }
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
                },
                onSelectEqualizerPreset = { presetName ->
                    viewModel.setEqualizerPreset(presetName)
                },
                onCyclePlaybackSpeed = {
                    viewModel.cyclePlaybackSpeed()
                },
                onCrossfadeDurationChange = { seconds ->
                    viewModel.setCrossfadeDurationSeconds(seconds)
                },
                lyricsPayload = currentLyrics,
                isLoadingLyrics = isLoadingLyrics,
                onRefreshLyrics = {
                    viewModel.refreshCurrentTrackLyrics()
                }
            )
        }

        // Google Sign-In & Cloud Sync Overlay
        AnimatedVisibility(
            visible = isAuthSheetVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            BeatifyAuthOverlay(
                currentUserUid = currentUserUid,
                currentUserEmail = currentUserEmail,
                currentUserDisplayName = currentUserDisplayName,
                isAuthenticating = isAuthenticating,
                authErrorMessage = authErrorMessage,
                syncStatus = syncStatus,
                onContinueWithGoogle = { viewModel.startGoogleSignIn(context) },
                onSignOut = { viewModel.signOut(context) },
                onTriggerManualSync = { viewModel.syncLocalDataToCloudNow() },
                onContinueWithoutAccount = { viewModel.closeAuthSheet() }
            )
        }
    }
}
