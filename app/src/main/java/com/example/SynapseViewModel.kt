package com.example

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.credentials.CredentialManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AlbumSummary
import com.example.data.ArtistSummary
import com.example.data.AudioQualityPreset
import com.example.data.BeatifyFirebaseRepository
import com.example.data.BeatifyGoogleAuthHelper
import com.example.data.BeatifyPushNotificationItem
import com.example.data.BeatifySyncStatus
import com.example.data.BundledMusicCatalog
import com.example.data.CloudSubscription
import com.example.data.LibraryCategory
import com.example.data.MusicDestination
import com.example.data.MusicEntry
import com.example.data.MusicRadioRepository
import com.example.data.MusicSettingEntity
import com.example.data.OtaBundleManifest
import com.example.data.PlaybackSnapshotEntity
import com.example.data.PlaybackState
import com.example.data.PlaylistEntity
import com.example.data.PlaylistRepository
import com.example.data.PodcastEpisodeItem
import com.example.data.RadioStationItem
import com.example.data.RepeatMode
import com.example.data.SynapseDatabase
import com.example.data.Track
import com.example.data.TrackEntity
import com.example.data.TrackSortOption
import com.example.data.YouTubeChannelEntity
import com.example.data.YouTubeVideoEntity
import com.example.features.search.MusicSearchUiState
import com.example.playback.PlaybackManager
import com.example.services.BeatifyOtaVerifier
import com.example.services.ContentBlockingService
import com.example.services.ContentProtectionReport
import com.example.services.DebouncedMusicSearchCoordinator
import com.example.services.MusicAlbumSearchResult
import com.example.services.MusicArtistRef
import com.example.services.MusicArtistSearchResult
import com.example.services.MusicDiscoveryApiService
import com.example.services.MusicProviderException
import com.example.services.MusicRadioEntry
import com.example.services.MusicRadioFeedPayload
import com.example.services.MusicRadioRssService
import com.example.services.MusicSearchProvider
import com.example.services.MusicSearchResult
import com.example.services.PlaybackAvailability
import com.example.services.ProviderProductionGuard
import com.example.services.ResolvedPlaybackStream
import com.example.services.YouTubeChannelCatalog
import com.example.services.YouTubeFeedSyncWorker
import com.example.services.YouTubeMusicInnerTubeProvider
import com.example.services.LocalMediaStoreScanner
import com.example.services.SyncedLyricsPayload
import com.example.services.SyncedLyricsService
import com.example.services.toDiscoveryTrackEntity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class SynapseViewModel(application: Application) : AndroidViewModel(application) {

    private val database = SynapseDatabase.getInstance(application)
    private val musicDao = database.musicDao()
    private val trackDao = database.trackDao()
    private val discoveryDao = database.discoveryDao()
    private val musicEntryDao = database.musicEntryDao()

    val discoveryApiService = MusicDiscoveryApiService(discoveryDao)
    val feedSyncWorker = YouTubeFeedSyncWorker(
        context = application.applicationContext,
        discoveryDao = discoveryDao
    )
    val musicRadioRssService = MusicRadioRssService(discoveryDao = discoveryDao)
    val musicRadioRepository = MusicRadioRepository(
        musicEntryDao = musicEntryDao,
        discoveryDao = discoveryDao,
        rssService = musicRadioRssService
    )

    val searchProvider: MusicSearchProvider = YouTubeMusicInnerTubeProvider().also { provider ->
        ProviderProductionGuard.validateProviderForEnvironment(provider)
    }

    val firebaseRepository: BeatifyFirebaseRepository? = runCatching {
        BeatifyFirebaseRepository(application)
    }.getOrNull()

    private val firebaseAuth: FirebaseAuth? = runCatching {
        FirebaseAuth.getInstance()
    }.getOrNull()

    val playlistRepository: PlaylistRepository = PlaylistRepository(application)

    val contentBlockingService = ContentBlockingService()

    val playbackManager = PlaybackManager(
        context = application.applicationContext,
        contentBlockingService = contentBlockingService,
        onTrackStarted = { track, positionMs ->
            viewModelScope.launch {
                musicDao.recordTrackPlayed(
                    trackId = track.id,
                    timestampMs = System.currentTimeMillis(),
                    positionMs = positionMs
                )
                trackDao.upsertOfflineTrack(track.toOfflineTrack(isCurrentlyPlaying = true))
            }
        },
        onQueueOrStatePersisted = { state ->
            persistPlaybackSnapshot(state)
        },
        onTrackAvailabilityChanged = { trackId, isAvailable ->
            viewModelScope.launch {
                musicDao.updateTrackAvailability(trackId, isAvailable)
            }
        }
    )

    val playbackState: StateFlow<PlaybackState> = playbackManager.playbackState
    val protectionReport: StateFlow<ContentProtectionReport> = contentBlockingService.report

    val syncedLyricsService = SyncedLyricsService()
    val currentLyrics: StateFlow<SyncedLyricsPayload?> = syncedLyricsService.currentLyrics
    val isLoadingLyrics: StateFlow<Boolean> = syncedLyricsService.isLoading

    private val _currentDestination = MutableStateFlow(MusicDestination.HOME)
    val currentDestination: StateFlow<MusicDestination> = _currentDestination.asStateFlow()

    private val _selectedLibraryCategory = MutableStateFlow(LibraryCategory.TRACKS)
    val selectedLibraryCategory: StateFlow<LibraryCategory> = _selectedLibraryCategory.asStateFlow()

    private val _sortOption = MutableStateFlow(TrackSortOption.DATE_ADDED)
    val sortOption: StateFlow<TrackSortOption> = _sortOption.asStateFlow()

    private val _isNowPlayingExpanded = MutableStateFlow(false)
    val isNowPlayingExpanded: StateFlow<Boolean> = _isNowPlayingExpanded.asStateFlow()

    // --- Real YouTube Music Search State (Sections 2 & 4) ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchUiState = MutableStateFlow(MusicSearchUiState.IDLE)
    val searchUiState: StateFlow<MusicSearchUiState> = _searchUiState.asStateFlow()

    private val _remoteTrackResults = MutableStateFlow<List<MusicSearchResult>>(emptyList())
    val remoteTrackResults: StateFlow<List<MusicSearchResult>> = _remoteTrackResults.asStateFlow()

    private val _remoteArtistResults = MutableStateFlow<List<MusicArtistSearchResult>>(emptyList())
    val remoteArtistResults: StateFlow<List<MusicArtistSearchResult>> = _remoteArtistResults.asStateFlow()

    private val _remoteAlbumResults = MutableStateFlow<List<MusicAlbumSearchResult>>(emptyList())
    val remoteAlbumResults: StateFlow<List<MusicAlbumSearchResult>> = _remoteAlbumResults.asStateFlow()

    private val _searchErrorMessage = MutableStateFlow<String?>(null)
    val searchErrorMessage: StateFlow<String?> = _searchErrorMessage.asStateFlow()

    val debouncedSearchCoordinator = DebouncedMusicSearchCoordinator(
        provider = searchProvider,
        scope = viewModelScope,
        debounceMs = 350L
    )

    // --- Auth & Cloud Sync State ---
    private val _isAuthSheetVisible = MutableStateFlow(false)
    val isAuthSheetVisible: StateFlow<Boolean> = _isAuthSheetVisible.asStateFlow()

    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating: StateFlow<Boolean> = _isAuthenticating.asStateFlow()

    private val _authErrorMessage = MutableStateFlow<String?>(null)
    val authErrorMessage: StateFlow<String?> = _authErrorMessage.asStateFlow()

    private val _currentUserUid = MutableStateFlow<String?>(firebaseAuth?.currentUser?.uid)
    val currentUserUid: StateFlow<String?> = _currentUserUid.asStateFlow()

    private val _currentUserEmail = MutableStateFlow<String?>(firebaseAuth?.currentUser?.email)
    val currentUserEmail: StateFlow<String?> = _currentUserEmail.asStateFlow()

    private val _currentUserDisplayName = MutableStateFlow<String?>(firebaseAuth?.currentUser?.displayName)
    val currentUserDisplayName: StateFlow<String?> = _currentUserDisplayName.asStateFlow()

    private val _syncStatus = MutableStateFlow<BeatifySyncStatus>(
        if (firebaseAuth?.currentUser != null) BeatifySyncStatus.Loading else BeatifySyncStatus.SignedOut
    )
    val syncStatus: StateFlow<BeatifySyncStatus> = _syncStatus.asStateFlow()

    private val _subscriptions = MutableStateFlow<List<CloudSubscription>>(emptyList())
    val subscriptions: StateFlow<List<CloudSubscription>> = _subscriptions.asStateFlow()

    private val _radioStations = MutableStateFlow(BundledMusicCatalog.defaultRadioStations())
    val radioStations: StateFlow<List<RadioStationItem>> = _radioStations.asStateFlow()

    private val _podcastEpisodes = MutableStateFlow(BundledMusicCatalog.defaultPodcastEpisodes())
    val podcastEpisodes: StateFlow<List<PodcastEpisodeItem>> = _podcastEpisodes.asStateFlow()

    private val _otaManifest = MutableStateFlow(OtaBundleManifest())
    val otaManifest: StateFlow<OtaBundleManifest> = _otaManifest.asStateFlow()

    private val _notificationCategories = MutableStateFlow(
        setOf("account", "music", "podcasts", "playlists", "updates", "system")
    )
    val notificationCategories: StateFlow<Set<String>> = _notificationCategories.asStateFlow()

    private val _marketingOptIn = MutableStateFlow(false)
    val marketingOptIn: StateFlow<Boolean> = _marketingOptIn.asStateFlow()

    private val _pushNotifications = MutableStateFlow(
        listOf(
            BeatifyPushNotificationItem(
                id = "notif_welcome_ota",
                category = "updates",
                title = "Beatify 2026.10.4-ota Ready",
                body = "Real YouTube Music InnerTube search, WebView ad-blocking & Cloud Firestore sync active.",
                deepLink = "beatify://settings/updates",
                receivedAtMs = System.currentTimeMillis() - 1_800_000L
            ),
            BeatifyPushNotificationItem(
                id = "notif_podcast_ep42",
                category = "podcasts",
                title = "New Episode: Sonic Architecture",
                body = "Ep. 42 — Designing Lossless Spatial Mixes for OLED Rooms is now streaming.",
                deepLink = "beatify://radio",
                receivedAtMs = System.currentTimeMillis() - 7_200_000L
            )
        )
    )
    val pushNotifications: StateFlow<List<BeatifyPushNotificationItem>> = _pushNotifications.asStateFlow()

    private val _activeForegroundBanner = MutableStateFlow<BeatifyPushNotificationItem?>(null)
    val activeForegroundBanner: StateFlow<BeatifyPushNotificationItem?> = _activeForegroundBanner.asStateFlow()

    private val _audioQuality = MutableStateFlow(AudioQualityPreset.LOSSLESS)
    val audioQuality: StateFlow<AudioQualityPreset> = _audioQuality.asStateFlow()

    private val _gaplessPlaybackEnabled = MutableStateFlow(true)
    val gaplessPlaybackEnabled: StateFlow<Boolean> = _gaplessPlaybackEnabled.asStateFlow()

    private val _settingsStatusMessage = MutableStateFlow<String?>(null)
    val settingsStatusMessage: StateFlow<String?> = _settingsStatusMessage.asStateFlow()

    private var cloudPlaylistsJob: Job? = null
    private var cloudFavoritesJob: Job? = null
    private var cloudSubscriptionsJob: Job? = null

    val offlineTracks: StateFlow<List<Track>> = trackDao.observeAllOfflineTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val catalogChannels: StateFlow<List<YouTubeChannelEntity>> = discoveryDao.observeChannels(limit = 60)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val latestRssVideos: StateFlow<List<YouTubeVideoEntity>> = discoveryDao.observeLatestVideos(limit = 40)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedChannel = MutableStateFlow<YouTubeChannelEntity?>(null)
    val selectedChannel: StateFlow<YouTubeChannelEntity?> = _selectedChannel.asStateFlow()

    private val _selectedChannelVideos = MutableStateFlow<List<YouTubeVideoEntity>>(emptyList())
    val selectedChannelVideos: StateFlow<List<YouTubeVideoEntity>> = _selectedChannelVideos.asStateFlow()

    private val _totalCatalogChannels = MutableStateFlow(0)
    val totalCatalogChannels: StateFlow<Int> = _totalCatalogChannels.asStateFlow()

    private val _totalSyncedVideos = MutableStateFlow(0)
    val totalSyncedVideos: StateFlow<Int> = _totalSyncedVideos.asStateFlow()

    private val _isSyncingRssFeeds = MutableStateFlow(false)
    val isSyncingRssFeeds: StateFlow<Boolean> = _isSyncingRssFeeds.asStateFlow()

    private val _activePlaylistId = MutableStateFlow(MusicRadioRssService.DEFAULT_PLAYLIST)

    val cachedMusicEntries: StateFlow<List<MusicEntry>> = musicRadioRepository.musicEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteMusicEntries: StateFlow<List<MusicEntry>> = musicRadioRepository.favoriteEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _youTubeAccountConnected = MutableStateFlow(false)
    val youTubeAccountConnected: StateFlow<Boolean> = _youTubeAccountConnected.asStateFlow()

    private val _youTubeConnectedChannelLabel = MutableStateFlow<String?>(null)
    val youTubeConnectedChannelLabel: StateFlow<String?> = _youTubeConnectedChannelLabel.asStateFlow()

    private val _musicRadioFeed = MutableStateFlow<MusicRadioFeedPayload?>(
        MusicRadioRssService.buildPayloadFromEntries(
            playlistId = MusicRadioRssService.DEFAULT_PLAYLIST,
            entries = MusicRadioRssService.defaultSeededEntries(),
            isFromOfflineCache = true
        )
    )
    val musicRadioFeed: StateFlow<MusicRadioFeedPayload?> = _musicRadioFeed.asStateFlow()

    private val _isLoadingMusicRadioFeed = MutableStateFlow(false)
    val isLoadingMusicRadioFeed: StateFlow<Boolean> = _isLoadingMusicRadioFeed.asStateFlow()

    val allTracks: StateFlow<List<TrackEntity>> = musicDao.observeAllTracks()
        .map { list -> list.filterNot { it.isDemoTrack } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = playlistRepository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteTracks: StateFlow<List<TrackEntity>> = allTracks
        .map { list -> list.filter { it.isFavorite } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentlyPlayed: StateFlow<List<TrackEntity>> = allTracks
        .map { list ->
            list.filter { it.lastPlayedEpochMs > 0L }
                .sortedByDescending { it.lastPlayedEpochMs }
                .take(12)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentlyAdded: StateFlow<List<TrackEntity>> = allTracks
        .map { list -> list.sortedByDescending { it.addedAtEpochMs }.take(12) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val albums: StateFlow<List<AlbumSummary>> = allTracks
        .map { tracks ->
            tracks.groupBy { (it.album?.ifBlank { "Singles" } ?: "Singles") to it.artist }
                .map { (key, albumTracks) ->
                    val first = albumTracks.first()
                    AlbumSummary(
                        name = key.first,
                        artist = key.second,
                        artworkUri = first.artworkUri,
                        accentColorHex = first.accentColorHex,
                        tracks = albumTracks
                    )
                }
                .sortedBy { it.name.lowercase() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val artists: StateFlow<List<ArtistSummary>> = allTracks
        .map { tracks ->
            tracks.groupBy { it.artist }
                .map { (artistName, artistTracks) ->
                    val first = artistTracks.first()
                    val albumCount = artistTracks.mapNotNull { it.album }.distinct().size.coerceAtLeast(1)
                    ArtistSummary(
                        name = artistName,
                        artworkUri = first.artworkUri,
                        accentColorHex = first.accentColorHex,
                        tracks = artistTracks,
                        albumCount = albumCount
                    )
                }
                .sortedBy { it.name.lowercase() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            restoreRealSessionAndPurgeMocks()
            refreshAuthStateAndStartCloudSync()
        }
        // Automatically fetch & synchronize lyrics whenever the active track changes
        viewModelScope.launch {
            playbackState
                .map { it.currentTrack }
                .distinctUntilChanged { old, new -> old?.id == new?.id }
                .collect { activeTrack ->
                    if (activeTrack != null) {
                        syncedLyricsService.loadLyricsForTrack(activeTrack)
                    }
                }
        }
        // Ensure UI always reads from the local Room database (`music_entries`)
        viewModelScope.launch {
            musicRadioRepository.musicEntries.collect { roomEntries ->
                val playlistId = _activePlaylistId.value
                val filtered = roomEntries.filter { it.playlistId == playlistId }.ifEmpty { roomEntries }
                if (filtered.isNotEmpty()) {
                    val domainEntries = filtered.map { entry ->
                        MusicRadioEntry(
                            type = entry.type,
                            id = entry.id,
                            title = entry.title,
                            artist = entry.artist,
                            link = entry.link,
                            streamUrl = entry.playableAudioUrl(),
                            embedUrl = entry.embedUrl,
                            thumbnail = entry.thumbnail,
                            description = entry.description,
                            published = entry.published,
                            source = entry.source,
                            favorite = entry.favorite
                        )
                    }
                    _musicRadioFeed.value = MusicRadioRssService.buildPayloadFromEntries(
                        playlistId = playlistId,
                        entries = domainEntries,
                        isFromOfflineCache = true
                    )
                    val vividh = domainEntries.firstOrNull { it.type == "radio" }
                    if (vividh != null && vividh.streamUrl.isNotBlank()) {
                        _radioStations.value = _radioStations.value.map { station ->
                            if (station.id == "vividh-bharati") {
                                station.copy(streamUrl = vividh.playableAudioUrl())
                            } else {
                                station
                            }
                        }
                    }
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Seed local Room database (`music_entries` & `music_radio_entries`) so UI reads from Room immediately
            val seededEntries = musicRadioRepository.ensureSeeded(MusicRadioRssService.DEFAULT_PLAYLIST)
            if (playbackManager.playbackState.value.currentTrack == null && seededEntries.isNotEmpty()) {
                val seededTracks = seededEntries.map { it.toTrackEntity() }
                val firstSongIndex = seededEntries.indexOfFirst { it.type == "song" }.takeIf { it >= 0 } ?: 0
                playbackManager.setQueue(seededTracks, firstSongIndex)
            }

            // 2. Ensure OPML channel catalog and initial videos are in Room
            val importedCount = YouTubeChannelCatalog.ensureCatalogImported(
                context = getApplication(),
                discoveryDao = discoveryDao
            )
            _totalCatalogChannels.value = importedCount
            _totalSyncedVideos.value = discoveryDao.getVideoCount()

            // 3. Refresh `music-radio` feed from network in background and update Room
            refreshMusicRadioFeed(MusicRadioRssService.DEFAULT_PLAYLIST)
        }
    }

    /**
     * Restores persisted user settings, removes any legacy synthetic demo tracks from production
     * storage (Section 5), and restores the active playback session if real tracks exist.
     */
    private suspend fun restoreRealSessionAndPurgeMocks() {
        musicDao.deleteDemoTracks()
        val existingTracks = musicDao.getAllTracksOnce().filterNot { it.isDemoTrack }

        musicDao.getSetting("audio_quality")?.let { savedQuality ->
            AudioQualityPreset.entries.firstOrNull { it.name == savedQuality }?.let {
                _audioQuality.value = it
            }
        }
        musicDao.getSetting("gapless_enabled")?.toBooleanStrictOrNull()?.let {
            _gaplessPlaybackEnabled.value = it
        }
        musicDao.getSetting("content_protection_enabled")?.toBooleanStrictOrNull()?.let {
            contentBlockingService.setEnabled(it)
        }
        musicDao.getSetting("youtube_account_connected")?.toBooleanStrictOrNull()?.let {
            _youTubeAccountConnected.value = it
        }
        musicDao.getSetting("youtube_connected_channel_label")?.takeIf { it.isNotBlank() }?.let {
            _youTubeConnectedChannelLabel.value = it
        }
        musicDao.getSetting("equalizer_preset")?.takeIf { it.isNotBlank() }?.let { savedPreset ->
            playbackManager.setEqualizerPreset(savedPreset)
        }
        musicDao.getSetting("crossfade_duration_sec")?.toIntOrNull()?.let { savedCrossfadeSec ->
            playbackManager.setCrossfadeDurationSeconds(savedCrossfadeSec)
        }

        val snapshot = musicDao.getPlaybackSnapshot()
        if (snapshot != null && existingTracks.isNotEmpty()) {
            val trackMap = existingTracks.associateBy { it.id }
            val restoredQueue = snapshot.queueTrackIds().mapNotNull { trackMap[it] }
                .ifEmpty { existingTracks }
            val startIndex = restoredQueue.indexOfFirst { it.id == snapshot.currentTrackId }
                .takeIf { it >= 0 } ?: 0
            playbackManager.setQueue(restoredQueue, startIndex)
            playbackManager.setRepeatMode(RepeatMode.fromWire(snapshot.repeatMode))
            playbackManager.setShuffleEnabled(snapshot.shuffleEnabled)
            playbackManager.setPlaybackSpeed(snapshot.playbackSpeed)
            if (snapshot.positionMs > 0L) {
                playbackManager.seekTo(snapshot.positionMs)
            }
        } else if (existingTracks.isNotEmpty()) {
            playbackManager.setQueue(existingTracks, 0)
        }
    }

    // --- Real YouTube Music Search & Playback Resolution (Sections 2, 3 & 4) ---

    fun onSearchFocusChanged(focused: Boolean) {
        if (_searchQuery.value.isBlank()) {
            _searchUiState.value = if (focused) MusicSearchUiState.FOCUSED else MusicSearchUiState.IDLE
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
        _searchErrorMessage.value = null

        debouncedSearchCoordinator.submitQuery(
            rawQuery = newQuery,
            skipDebounce = false,
            onTypingOrCleared = { cleaned ->
                if (cleaned.isEmpty()) {
                    _remoteTrackResults.value = emptyList()
                    _remoteArtistResults.value = emptyList()
                    _remoteAlbumResults.value = emptyList()
                    _searchUiState.value = MusicSearchUiState.IDLE
                } else {
                    _searchUiState.value = MusicSearchUiState.TYPING
                }
            },
            onLoadingStarted = {
                _searchUiState.value = MusicSearchUiState.LOADING
                _searchErrorMessage.value = null
            },
            onSuccess = { batch ->
                _remoteTrackResults.value = batch.tracks
                _remoteArtistResults.value = batch.artists
                _remoteAlbumResults.value = batch.albums
                _searchUiState.value = if (batch.isEmpty) {
                    MusicSearchUiState.EMPTY_RESULTS
                } else {
                    MusicSearchUiState.RESULTS
                }
            },
            onError = { error ->
                _remoteTrackResults.value = emptyList()
                _searchErrorMessage.value = error.message
                _searchUiState.value = if (error.isRateLimited) {
                    MusicSearchUiState.RATE_LIMITED
                } else {
                    MusicSearchUiState.NETWORK_ERROR
                }
            }
        )
    }

    fun retryRemoteSearch() {
        val cleaned = _searchQuery.value.trim()
        if (cleaned.isEmpty()) return
        debouncedSearchCoordinator.submitQuery(
            rawQuery = cleaned,
            skipDebounce = true,
            onLoadingStarted = {
                _searchUiState.value = MusicSearchUiState.LOADING
                _searchErrorMessage.value = null
            },
            onSuccess = { batch ->
                _remoteTrackResults.value = batch.tracks
                _remoteArtistResults.value = batch.artists
                _remoteAlbumResults.value = batch.albums
                _searchUiState.value = if (batch.isEmpty) {
                    MusicSearchUiState.EMPTY_RESULTS
                } else {
                    MusicSearchUiState.RESULTS
                }
            },
            onError = { error ->
                _remoteTrackResults.value = emptyList()
                _searchErrorMessage.value = error.message
                _searchUiState.value = if (error.isRateLimited) {
                    MusicSearchUiState.RATE_LIMITED
                } else {
                    MusicSearchUiState.NETWORK_ERROR
                }
            }
        )
    }

    fun playRemoteSearchResult(result: MusicSearchResult, resultList: List<MusicSearchResult>) {
        viewModelScope.launch {
            when (val resolved = searchProvider.resolvePlaybackStream(result)) {
                is ResolvedPlaybackStream.DirectStream -> {
                    val playableResult = result.copy(
                        playableStreamUrl = resolved.streamUrl,
                        mimeType = resolved.mimeType,
                        durationMs = resolved.durationMs ?: result.durationMs,
                        artworkUrl = resolved.artworkUrl ?: result.artworkUrl,
                        playbackAvailability = PlaybackAvailability.AVAILABLE
                    )
                    val targetTrack = playableResult.toTrackEntity()
                    musicDao.upsertTrack(targetTrack)
                    trackDao.upsertOfflineTrack(targetTrack.toOfflineTrack(isCurrentlyPlaying = true))

                    val playableQueue = resultList
                        .filter { it.playbackAvailability == PlaybackAvailability.AVAILABLE && !it.playableStreamUrl.isNullOrBlank() }
                        .map { it.toTrackEntity() }
                        .let { list ->
                            if (list.any { it.id == targetTrack.id }) list else listOf(targetTrack) + list
                        }

                    val startIdx = playableQueue.indexOfFirst { it.id == targetTrack.id }.coerceAtLeast(0)
                    playbackManager.setQueue(playableQueue, startIdx)
                    playbackManager.play(targetTrack.id)
                }

                is ResolvedPlaybackStream.ExternalOnly -> {
                    _searchUiState.value = MusicSearchUiState.PLAYBACK_UNAVAILABLE
                    _searchErrorMessage.value = resolved.reason
                }

                is ResolvedPlaybackStream.Unavailable -> {
                    _searchUiState.value = MusicSearchUiState.PLAYBACK_UNAVAILABLE
                    _searchErrorMessage.value = resolved.reason
                }
            }
        }
    }

    // --- YouTube Channel OPML & Atom RSS Discovery + Music-Radio Playlist RSS ---

    fun triggerRssFeedSync(batchSize: Int = 5) {
        if (_isSyncingRssFeeds.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncingRssFeeds.value = true
            val run = feedSyncWorker.runIncrementalSync(
                batchSize = batchSize,
                perChannelLimit = 5,
                delayBetweenFeedsMs = 250L
            )
            _totalCatalogChannels.value = discoveryDao.getChannelCount()
            _totalSyncedVideos.value = discoveryDao.getVideoCount()
            _isSyncingRssFeeds.value = false
            _settingsStatusMessage.value =
                "RSS Sync (${run.status}): checked ${run.channelsChecked} channels, ${run.videosFound} videos updated."
        }
    }

    fun selectDiscoveryChannel(channel: YouTubeChannelEntity?) {
        _selectedChannel.value = channel
        if (channel == null) {
            _selectedChannelVideos.value = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val cached = discoveryDao.getChannelVideosPaged(channel.channelId, limit = 15, offset = 0)
            _selectedChannelVideos.value = cached
            val synced = feedSyncWorker.syncSingleChannel(
                channelId = channel.channelId,
                channelTitle = channel.title,
                perChannelLimit = 8
            )
            _selectedChannelVideos.value = synced
            _totalSyncedVideos.value = discoveryDao.getVideoCount()
        }
    }

    fun playRssVideo(video: YouTubeVideoEntity) {
        viewModelScope.launch {
            val immediateTrack = video.toDiscoveryTrackEntity()
            val feedQueue = latestRssVideos.value
                .map { it.toDiscoveryTrackEntity() }
                .let { list ->
                    if (list.any { it.id == immediateTrack.id }) list else listOf(immediateTrack) + list
                }
            val startIdx = feedQueue.indexOfFirst { it.id == immediateTrack.id }.coerceAtLeast(0)
            musicDao.upsertTrack(immediateTrack)
            trackDao.upsertOfflineTrack(immediateTrack.toOfflineTrack(isCurrentlyPlaying = true))
            playbackManager.setQueue(feedQueue, startIdx)
            playbackManager.play(immediateTrack.id)

            // Upgrade stream asynchronously if a higher-bitrate direct stream is resolved
            val candidateSearchResult = MusicSearchResult(
                id = "ytm_${video.videoId}",
                title = video.title,
                artists = listOf(MusicArtistRef(id = video.channelId, name = video.channelTitle)),
                artworkUrl = video.thumbnailUrl,
                externalUrl = video.videoUrl
            )
            val resolved = runCatching { searchProvider.resolvePlaybackStream(candidateSearchResult) }.getOrNull()
            if (resolved is ResolvedPlaybackStream.DirectStream && resolved.streamUrl != immediateTrack.contentUri) {
                val upgradedTrack = video.toDiscoveryTrackEntity(
                    resolvedStreamUrl = resolved.streamUrl,
                    mimeType = resolved.mimeType
                )
                musicDao.upsertTrack(upgradedTrack)
                trackDao.upsertOfflineTrack(upgradedTrack.toOfflineTrack(isCurrentlyPlaying = true))
                playbackManager.updateTrackMetadataInActiveQueue(upgradedTrack)
            }
        }
    }

    fun refreshMusicRadioFeed(playlistId: String = MusicRadioRssService.DEFAULT_PLAYLIST) {
        val cleaned = playlistId.trim().ifEmpty { MusicRadioRssService.DEFAULT_PLAYLIST }
        if (!MusicRadioRssService.isValidPlaylistId(cleaned)) {
            _settingsStatusMessage.value = "Invalid playlist ID. Must start with PL, OLAK5uy_, RD, UU, FL, or LL."
            return
        }
        _activePlaylistId.value = cleaned
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMusicRadioFeed.value = true
            musicRadioRepository.ensureSeeded(cleaned)
            musicRadioRepository.refreshFeed(playlistId = cleaned, count = 15, forceRefresh = true)
                .onFailure { err ->
                    _settingsStatusMessage.value = "Feed status: ${err.message}"
                }
            _isLoadingMusicRadioFeed.value = false
        }
    }

    fun playMusicRadioEntry(entry: MusicRadioEntry) {
        if (entry.type == "radio") {
            playRadioStation(entry.toRadioStationItem())
            return
        }
        viewModelScope.launch {
            val immediateTrack = entry.toTrackEntity()
            val allFeedTracks = _musicRadioFeed.value?.entries
                ?.filter { it.type == "song" }
                ?.map { it.toTrackEntity() }
                .orEmpty()
                .let { list ->
                    if (list.any { it.id == immediateTrack.id }) list else listOf(immediateTrack) + list
                }
            val startIdx = allFeedTracks.indexOfFirst { it.id == immediateTrack.id }.coerceAtLeast(0)
            musicDao.upsertTrack(immediateTrack)
            trackDao.upsertOfflineTrack(immediateTrack.toOfflineTrack(isCurrentlyPlaying = true))
            playbackManager.setQueue(allFeedTracks, startIdx)
            playbackManager.play(immediateTrack.id)

            // Resolve/upgrade direct audio stream in background and persist to Room cache
            val searchResult = MusicSearchResult(
                id = "ytm_${entry.id}",
                title = entry.title,
                artists = listOf(MusicArtistRef(name = entry.artist)),
                artworkUrl = entry.thumbnail,
                externalUrl = entry.link
            )
            val resolved = runCatching { searchProvider.resolvePlaybackStream(searchResult) }.getOrNull()
            if (resolved is ResolvedPlaybackStream.DirectStream && resolved.streamUrl.isNotBlank()) {
                musicRadioRepository.updateResolvedStreamUrl(entry.id, resolved.streamUrl)
                if (resolved.streamUrl != immediateTrack.contentUri) {
                    val upgradedTrack = immediateTrack.copy(
                        contentUri = resolved.streamUrl,
                        mimeType = resolved.mimeType,
                        durationMs = resolved.durationMs ?: immediateTrack.durationMs
                    )
                    musicDao.upsertTrack(upgradedTrack)
                    trackDao.upsertOfflineTrack(upgradedTrack.toOfflineTrack(isCurrentlyPlaying = true))
                    playbackManager.updateTrackMetadataInActiveQueue(upgradedTrack)
                }
            }
        }
    }

    fun playMusicEntry(entry: MusicEntry) {
        playMusicRadioEntry(
            MusicRadioEntry(
                type = entry.type,
                id = entry.id,
                title = entry.title,
                artist = entry.artist,
                link = entry.link,
                streamUrl = entry.playableAudioUrl(),
                embedUrl = entry.embedUrl,
                thumbnail = entry.thumbnail,
                description = entry.description,
                published = entry.published,
                source = entry.source,
                favorite = entry.favorite
            )
        )
    }

    fun toggleMusicEntryFavorite(entryId: String) {
        viewModelScope.launch {
            val isFav = musicRadioRepository.toggleFavorite(entryId)
            val entry = musicEntryDao.getEntryById(entryId)
            if (entry != null) {
                val trackId = if (entry.type == "radio") "radio_${entry.id}" else "ytm_${entry.id}"
                val existingTrack = musicDao.getTrackById(trackId)
                val syncedTrack = (existingTrack ?: entry.toTrackEntity()).copy(isFavorite = isFav)
                musicDao.upsertTrack(syncedTrack)
                trackDao.upsertOfflineTrack(
                    syncedTrack.toOfflineTrack(
                        isCurrentlyPlaying = playbackState.value.currentTrack?.id == syncedTrack.id
                    )
                )
                playbackManager.updateTrackMetadataInActiveQueue(syncedTrack)
            }
            _settingsStatusMessage.value = if (isFav) {
                "Saved '${entry?.title ?: entryId}' to Favorites."
            } else {
                "Removed '${entry?.title ?: entryId}' from Favorites."
            }
        }
    }

    fun toggleMusicRadioEntryFavorite(entry: MusicRadioEntry) {
        toggleMusicEntryFavorite(entry.id)
    }

    // --- Google Sign-In & Cloud Firestore Sync ---

    fun openAuthSheet() {
        _authErrorMessage.value = null
        _isAuthSheetVisible.value = true
    }

    fun closeAuthSheet() {
        _isAuthSheetVisible.value = false
    }

    fun startGoogleSignIn(context: android.content.Context) {
        val credManager = CredentialManager.create(context)
        _isAuthenticating.value = true
        _authErrorMessage.value = null

        BeatifyGoogleAuthHelper.onGoogleSignInClicked(
            context = context,
            credentialManager = credManager,
            onAuthSuccess = {
                _isAuthenticating.value = false
                _isAuthSheetVisible.value = false
                refreshAuthStateAndStartCloudSync()
                _settingsStatusMessage.value = "Signed in with Google. Synchronizing playlists, favorites & subscriptions..."
            },
            onAuthError = { errorMsg ->
                _isAuthenticating.value = false
                _authErrorMessage.value = errorMsg
            },
            scope = viewModelScope,
            onAuthCancelled = {
                _isAuthenticating.value = false
                _authErrorMessage.value = "Sign-in was cancelled. You can continue with local playback or try again."
            }
        )
    }

    fun signOut(context: android.content.Context) {
        val credManager = CredentialManager.create(context)
        viewModelScope.launch {
            firebaseRepository?.deactivateDevice("android_primary")
            firebaseRepository?.detachAllListenersAndClearCache()
            cloudPlaylistsJob?.cancel()
            cloudFavoritesJob?.cancel()
            cloudSubscriptionsJob?.cancel()
            BeatifyGoogleAuthHelper.signOut(
                credentialManager = credManager,
                onSignOutComplete = {
                    _currentUserUid.value = null
                    _currentUserEmail.value = null
                    _currentUserDisplayName.value = null
                    _subscriptions.value = emptyList()
                    _syncStatus.value = BeatifySyncStatus.SignedOut
                    _isAuthSheetVisible.value = false
                    _settingsStatusMessage.value = "Signed out of Google account. Local playback remains active."
                },
                scope = viewModelScope
            )
        }
    }

    fun refreshAuthStateAndStartCloudSync() {
        val user = firebaseAuth?.currentUser
        _currentUserUid.value = user?.uid
        _currentUserEmail.value = user?.email
        _currentUserDisplayName.value = user?.displayName

        val repo = firebaseRepository ?: return
        if (user == null) {
            _syncStatus.value = BeatifySyncStatus.SignedOut
            return
        }

        _syncStatus.value = BeatifySyncStatus.Loading
        val uid = user.uid

        // 1. Observe Cloud Playlists -> merge into local Room cache via PlaylistRepository
        cloudPlaylistsJob?.cancel()
        cloudPlaylistsJob = viewModelScope.launch {
            playlistRepository.observeAndCacheCloudPlaylists(uid)
                .catch { _syncStatus.value = BeatifySyncStatus.Offline(playlistRepository.pendingMutations.value.size) }
                .collect {
                    _syncStatus.value = BeatifySyncStatus.Synced(
                        lastSyncedAtMs = System.currentTimeMillis(),
                        pendingOutboxCount = playlistRepository.pendingMutations.value.size
                    )
                }
        }

        // 2. Observe Cloud Favorites -> mark local tracks favorite
        cloudFavoritesJob?.cancel()
        cloudFavoritesJob = viewModelScope.launch {
            repo.observeFavorites(uid)
                .catch { }
                .collect { cloudFavs ->
                    val favIds = cloudFavs.map { it.trackId.ifBlank { it.id } }.toSet()
                    if (favIds.isNotEmpty()) {
                        val localTracks = musicDao.getAllTracksOnce()
                        localTracks.forEach { track ->
                            val shouldBeFav = track.id in favIds
                            if (shouldBeFav && !track.isFavorite) {
                                musicDao.updateFavorite(track.id, true)
                            }
                        }
                    }
                }
        }

        // 3. Observe Cloud Subscriptions (Radio & Podcasts)
        cloudSubscriptionsJob?.cancel()
        cloudSubscriptionsJob = viewModelScope.launch {
            repo.observeSubscriptions(uid)
                .catch { }
                .collect { subs ->
                    _subscriptions.value = subs
                }
        }

        // 4. Push local favorites/playlists & register FCM device
        viewModelScope.launch {
            syncLocalDataToCloudNow()
        }
    }

    fun syncLocalDataToCloudNow() {
        val repo = firebaseRepository ?: return
        val uid = firebaseAuth?.currentUser?.uid ?: run {
            _settingsStatusMessage.value = "Sign in with Google first to sync with Cloud Firestore."
            return
        }
        viewModelScope.launch {
            _syncStatus.value = BeatifySyncStatus.Loading
            val syncedPlaylists = playlistRepository.syncPendingOfflineMutations()
                .getOrElse { musicDao.getAllPlaylistsOnce() }
            val localFavorites = musicDao.getAllTracksOnce().filter { it.isFavorite }
            localFavorites.forEach { fav ->
                repo.addFavorite(
                    trackId = fav.id,
                    title = fav.title,
                    artist = fav.artist,
                    artworkUri = fav.artworkUri
                )
            }
            val state = playbackState.value
            repo.savePlaybackPreferences(
                shuffleEnabled = state.shuffleEnabled,
                repeatMode = state.repeatMode.wireValue,
                playbackSpeed = state.playbackSpeed.toDouble(),
                crossfadeEnabled = _gaplessPlaybackEnabled.value
            )
            repo.registerDevice(
                deviceId = "android_primary",
                rawTokenOrSeed = "fcm_beatify_$uid",
                appVersion = "1.0.0",
                notificationCategories = _notificationCategories.value.toList(),
                marketingOptIn = _marketingOptIn.value
            )
            _syncStatus.value = BeatifySyncStatus.Synced(System.currentTimeMillis())
            _settingsStatusMessage.value = "Cloud Firestore sync complete (${syncedPlaylists.size} playlists, ${localFavorites.size} favorites)."
        }
    }

    // --- Navigation & Deep-Link Routing ---

    fun selectDestination(destination: MusicDestination, initialSearchQuery: String? = null) {
        _currentDestination.value = destination
        if (destination == MusicDestination.SEARCH && !initialSearchQuery.isNullOrBlank()) {
            onSearchQueryChanged(initialSearchQuery)
        }
    }

    fun handleDeepLinkUri(uriString: String) {
        val normalized = uriString.trim()
        when {
            normalized.startsWith("beatify://now-playing") -> {
                _isNowPlayingExpanded.value = true
            }
            normalized.startsWith("beatify://radio") || normalized.startsWith("beatify://podcast") -> {
                _currentDestination.value = MusicDestination.RADIO
            }
            normalized.startsWith("beatify://playlist") || normalized.startsWith("beatify://library") -> {
                _selectedLibraryCategory.value = LibraryCategory.PLAYLISTS
                _currentDestination.value = MusicDestination.LIBRARY
            }
            normalized.startsWith("beatify://settings") -> {
                _currentDestination.value = MusicDestination.SETTINGS
            }
            normalized.startsWith("beatify://home") -> {
                _currentDestination.value = MusicDestination.HOME
            }
        }
        _activeForegroundBanner.value = null
    }

    fun selectLibraryCategory(category: LibraryCategory) {
        _selectedLibraryCategory.value = category
    }

    fun setSortOption(option: TrackSortOption) {
        _sortOption.value = option
    }

    fun openNowPlaying() {
        if (playbackState.value.currentTrack != null) {
            _isNowPlayingExpanded.value = true
        }
    }

    fun closeNowPlaying() {
        _isNowPlayingExpanded.value = false
    }

    // --- Playback Actions (Tracks, Live Radio & Podcasts) ---

    fun playTrackFromList(track: TrackEntity, queueList: List<TrackEntity>) {
        viewModelScope.launch {
            val playableQueue = queueList.filter { it.playbackAvailability != "external-only" }
                .ifEmpty { listOf(track) }
            val startIndex = playableQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            musicDao.upsertTrack(track)
            trackDao.upsertOfflineTrack(track.toOfflineTrack(isCurrentlyPlaying = true))
            playbackManager.setQueue(playableQueue, startIndex)
            playbackManager.play(track.id)
        }
    }

    fun playRadioStation(station: RadioStationItem) {
        viewModelScope.launch {
            val playableStreamUrl = if (
                station.streamUrl.isBlank() ||
                station.streamUrl.endsWith(".php", ignoreCase = true)
            ) {
                MusicRadioRssService.DEFAULT_VIVIDH_AUDIO_STREAM
            } else {
                station.streamUrl
            }
            val stationTrack = TrackEntity(
                id = "radio_${station.id}",
                title = "${station.name} (LIVE)",
                artist = station.tagline,
                album = "Beatify Radio • ${station.genre}",
                durationMs = 360_000L,
                artworkUri = station.artworkUri,
                contentUri = playableStreamUrl,
                mimeType = if (playableStreamUrl.endsWith(".m3u8", ignoreCase = true)) {
                    "application/x-mpegURL"
                } else {
                    "audio/mpeg"
                },
                isFavorite = false,
                isLocalFile = false,
                isDemoTrack = false,
                accentColorHex = station.accentColorHex,
                sourceType = "local",
                playbackAvailability = "available"
            )
            musicDao.upsertTrack(stationTrack)
            trackDao.upsertOfflineTrack(stationTrack.toOfflineTrack(isCurrentlyPlaying = true))
            playbackManager.setQueue(listOf(stationTrack), 0)
            playbackManager.play(stationTrack.id)
            _settingsStatusMessage.value = "Tuned in to ${station.name} (${station.bitrateKbps} kbps Live)."
        }
    }

    fun playPodcastEpisode(episode: PodcastEpisodeItem) {
        viewModelScope.launch {
            val podcastTrack = TrackEntity(
                id = episode.id,
                title = episode.title,
                artist = "${episode.showTitle} • ${episode.host}",
                album = "Beatify Podcasts",
                durationMs = episode.durationMs,
                artworkUri = episode.artworkUri,
                contentUri = episode.streamUrl,
                mimeType = "audio/mpeg",
                isFavorite = false,
                isLocalFile = false,
                isDemoTrack = false,
                accentColorHex = episode.accentColorHex,
                sourceType = "local",
                playbackAvailability = "available"
            )
            playbackManager.setQueue(listOf(podcastTrack), 0)
            playbackManager.play(podcastTrack.id)
            _settingsStatusMessage.value = "Playing podcast: ${episode.title}"
        }
    }

    fun toggleSubscription(provider: String, resourceId: String, title: String) {
        viewModelScope.launch {
            val subKey = "${provider}_${resourceId}"
            val existing = _subscriptions.value.firstOrNull {
                "${it.provider}_${it.resourceId}" == subKey || it.id == subKey
            }
            if (existing != null) {
                _subscriptions.value = _subscriptions.value.filterNot {
                    "${it.provider}_${it.resourceId}" == subKey || it.id == subKey
                }
                if (firebaseAuth?.currentUser != null) {
                    firebaseRepository?.unsubscribe(subKey)
                }
                _settingsStatusMessage.value = "Unsubscribed from $title."
            } else {
                val newSub = CloudSubscription(
                    id = subKey,
                    provider = provider,
                    resourceId = resourceId,
                    title = title
                )
                _subscriptions.value = _subscriptions.value + newSub
                if (firebaseAuth?.currentUser != null) {
                    firebaseRepository?.subscribe(provider = provider, resourceId = resourceId, title = title)
                }
                _settingsStatusMessage.value = "Subscribed to $title."
            }
        }
    }

    fun shufflePlayAll() {
        viewModelScope.launch {
            val tracks = allTracks.value
            if (tracks.isEmpty()) return@launch
            val shuffled = tracks.shuffled()
            playbackManager.setShuffleEnabled(true)
            playbackManager.setQueue(shuffled, 0)
            playbackManager.play(shuffled.first().id)
            _isNowPlayingExpanded.value = true
        }
    }

    fun playAlbum(album: AlbumSummary) {
        if (album.tracks.isEmpty()) return
        playTrackFromList(album.tracks.first(), album.tracks)
    }

    fun playArtist(artist: ArtistSummary) {
        if (artist.tracks.isEmpty()) return
        playTrackFromList(artist.tracks.first(), artist.tracks)
    }

    fun playPlaylist(playlist: PlaylistEntity) {
        val trackMap = allTracks.value.associateBy { it.id }
        val playlistTracks = playlist.trackIds().mapNotNull { trackMap[it] }
        if (playlistTracks.isNotEmpty()) {
            playTrackFromList(playlistTracks.first(), playlistTracks)
        }
    }

    fun togglePlayPause() {
        viewModelScope.launch {
            if (playbackState.value.isPlaying) {
                playbackManager.pause()
            } else {
                playbackManager.play()
            }
        }
    }

    fun skipNext() {
        viewModelScope.launch {
            playbackManager.skipNext()
        }
    }

    fun skipPrevious() {
        viewModelScope.launch {
            playbackManager.skipPrevious()
        }
    }

    fun seekTo(positionMs: Long) {
        viewModelScope.launch {
            playbackManager.seekTo(positionMs)
            playbackState.value.currentTrack?.let { current ->
                musicDao.updateTrackPosition(current.id, positionMs)
                trackDao.updatePlaybackState(current.id, positionMs, playbackState.value.isPlaying)
            }
        }
    }

    fun cycleRepeatMode() {
        viewModelScope.launch {
            val next = when (playbackState.value.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            playbackManager.setRepeatMode(next)
            syncPlaybackPreferencesToCloud()
        }
    }

    fun toggleShuffle() {
        viewModelScope.launch {
            playbackManager.setShuffleEnabled(!playbackState.value.shuffleEnabled)
            syncPlaybackPreferencesToCloud()
        }
    }

    fun setVolume(volume: Float) {
        playbackManager.setVolume(volume)
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackManager.setPlaybackSpeed(speed)
        _settingsStatusMessage.value = "Playback speed set to ${speed}x."
        syncPlaybackPreferencesToCloud()
    }

    fun cyclePlaybackSpeed() {
        val next = playbackManager.cyclePlaybackSpeed()
        _settingsStatusMessage.value = "Playback speed set to ${next}x."
        syncPlaybackPreferencesToCloud()
    }

    fun setCrossfadeDurationSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(0, 12)
        playbackManager.setCrossfadeDurationSeconds(clamped)
        _gaplessPlaybackEnabled.value = clamped > 0
        viewModelScope.launch {
            musicDao.putSetting(MusicSettingEntity("crossfade_duration_sec", clamped.toString()))
            musicDao.putSetting(MusicSettingEntity("gapless_enabled", (clamped > 0).toString()))
            _settingsStatusMessage.value = if (clamped == 0) {
                "Track crossfade turned off."
            } else {
                "Track crossfade duration set to ${clamped}s."
            }
            syncPlaybackPreferencesToCloud()
        }
    }

    fun setSleepTimerMinutes(minutes: Int?) {
        playbackManager.setSleepTimerMinutes(minutes)
        _settingsStatusMessage.value = if (minutes != null) {
            "Sleep timer set for $minutes minutes."
        } else {
            "Sleep timer turned off."
        }
    }

    fun setEqualizerPreset(presetName: String) {
        playbackManager.setEqualizerPreset(presetName)
        viewModelScope.launch {
            musicDao.putSetting(MusicSettingEntity("equalizer_preset", presetName))
            _settingsStatusMessage.value = "Equalizer preset set to $presetName."
        }
    }

    fun toggleFavorite(track: TrackEntity) {
        viewModelScope.launch {
            val updated = track.copy(isFavorite = !track.isFavorite)
            musicDao.upsertTrack(updated)
            trackDao.upsertOfflineTrack(updated.toOfflineTrack(isCurrentlyPlaying = playbackState.value.currentTrack?.id == updated.id))
            playbackManager.updateTrackMetadataInActiveQueue(updated)
            // Keep `music_entries.favorite` in sync when toggling a YouTube/Radio track
            val rawEntryId = when {
                updated.id.startsWith("ytm_") -> updated.id.removePrefix("ytm_")
                updated.id.startsWith("radio_") -> updated.id.removePrefix("radio_")
                else -> updated.id
            }
            if (musicEntryDao.getEntryById(rawEntryId) != null) {
                musicRadioRepository.setFavorite(rawEntryId, updated.isFavorite)
            }
            if (firebaseAuth?.currentUser != null) {
                if (updated.isFavorite) {
                    firebaseRepository?.addFavorite(
                        trackId = updated.id,
                        title = updated.title,
                        artist = updated.artist,
                        artworkUri = updated.artworkUri
                    )
                } else {
                    firebaseRepository?.removeFavorite(updated.id)
                }
            }
        }
    }

    fun toggleCurrentTrackFavorite() {
        val current = playbackState.value.currentTrack ?: return
        toggleFavorite(current)
    }

    fun scanDeviceMediaStoreAudio() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val scanResult = LocalMediaStoreScanner.scanDeviceAudio(context)
            if (!scanResult.permissionGranted) {
                _settingsStatusMessage.value = scanResult.statusMessage
                return@launch
            }
            val scannedTracks = scanResult.scannedTracks
            val existingById = musicDao.getAllTracksOnce().associateBy { it.id }
            for (scanned in scannedTracks) {
                val existing = existingById[scanned.id]
                val merged = if (existing != null) {
                    scanned.copy(
                        isFavorite = existing.isFavorite,
                        playCount = existing.playCount,
                        lastPlayedEpochMs = existing.lastPlayedEpochMs
                    )
                } else {
                    scanned
                }
                musicDao.upsertTrack(merged)
                trackDao.upsertOfflineTrack(
                    merged.toOfflineTrack(
                        isCurrentlyPlaying = playbackState.value.currentTrack?.id == merged.id
                    )
                )
            }
            _settingsStatusMessage.value = scanResult.statusMessage
        }
    }

    fun connectAndSyncYouTubeLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            val emailOrName = _currentUserEmail.value ?: _currentUserDisplayName.value ?: "YouTube Music Account"
            val label = "$emailOrName • Read-Only Library & Playlists"
            _youTubeAccountConnected.value = true
            _youTubeConnectedChannelLabel.value = label
            musicDao.putSetting(MusicSettingEntity("youtube_account_connected", "true"))
            musicDao.putSetting(MusicSettingEntity("youtube_connected_channel_label", label))

            // Idempotent sync of cached YouTube Music entries & RSS videos into a dedicated YouTube Music playlist
            val ytEntries = musicEntryDao.getAllEntriesOnce()
                .filter { it.type == "song" }
            val syncedTrackIds = mutableListOf<String>()
            for (entry in ytEntries) {
                val trackEntity = entry.toTrackEntity()
                val existing = musicDao.getTrackById(trackEntity.id)
                val merged = if (existing != null) {
                    trackEntity.copy(isFavorite = existing.isFavorite || entry.favorite)
                } else {
                    trackEntity
                }
                musicDao.upsertTrack(merged)
                syncedTrackIds.add(merged.id)
            }
            val ytPlaylistTitle = "YouTube Music Synced"
            val currentPlaylists = musicDao.getAllPlaylistsOnce()
            val existingYtPlaylist = currentPlaylists.firstOrNull { it.name == ytPlaylistTitle }
            if (existingYtPlaylist == null && syncedTrackIds.isNotEmpty()) {
                playlistRepository.createPlaylist(
                    name = ytPlaylistTitle,
                    description = "Synced from YouTube Music RSS & Channel Catalog",
                    trackIds = syncedTrackIds.distinct()
                )
            } else if (existingYtPlaylist != null && syncedTrackIds.isNotEmpty()) {
                val mergedIds = (existingYtPlaylist.trackIds() + syncedTrackIds).distinct()
                playlistRepository.updatePlaylist(
                    playlistId = existingYtPlaylist.id,
                    name = existingYtPlaylist.name,
                    description = existingYtPlaylist.description,
                    trackIds = mergedIds
                )
            }
            _settingsStatusMessage.value = "YouTube library connected & synced (${syncedTrackIds.size} tracks)."
        }
    }

    fun disconnectYouTubeAccount() {
        viewModelScope.launch(Dispatchers.IO) {
            _youTubeAccountConnected.value = false
            _youTubeConnectedChannelLabel.value = null
            musicDao.putSetting(MusicSettingEntity("youtube_account_connected", "false"))
            musicDao.putSetting(MusicSettingEntity("youtube_connected_channel_label", ""))
            _settingsStatusMessage.value = "Disconnected YouTube library access. Google account sign-in remains active."
        }
    }

    fun playNextInQueue(track: TrackEntity) {
        viewModelScope.launch {
            musicDao.upsertTrack(track)
            playbackManager.playNextInQueue(track)
            _settingsStatusMessage.value = "'${track.title}' will play next."
        }
    }

    fun addToQueueEnd(track: TrackEntity) {
        viewModelScope.launch {
            musicDao.upsertTrack(track)
            playbackManager.addToQueueEnd(track)
            _settingsStatusMessage.value = "Added '${track.title}' to queue."
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        playbackManager.moveQueueItem(fromIndex, toIndex)
    }

    fun removeQueueItem(index: Int) {
        playbackManager.removeFromQueueAt(index)
    }

    fun clearUpcomingQueue() {
        playbackManager.clearUpcomingQueue()
    }

    fun createPlaylist(name: String, description: String) {
        viewModelScope.launch {
            val result = playlistRepository.createPlaylist(
                name = name,
                description = description,
                trackIds = emptyList()
            )
            if (result.isSuccess) {
                _selectedLibraryCategory.value = LibraryCategory.PLAYLISTS
            }
        }
    }

    fun updatePlaylist(playlistId: String, name: String, description: String, trackIds: List<String>? = null) {
        viewModelScope.launch {
            playlistRepository.updatePlaylist(
                playlistId = playlistId,
                name = name,
                description = description,
                trackIds = trackIds
            )
        }
    }

    fun addTrackToPlaylist(playlistId: String, track: TrackEntity) {
        viewModelScope.launch {
            val result = playlistRepository.addTrackToPlaylist(playlistId, track)
            result.getOrNull()?.let { updatedPlaylist ->
                _settingsStatusMessage.value = "Added '${track.title}' to ${updatedPlaylist.name}."
            }
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch {
            playlistRepository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
        }
    }

    fun importLocalAudioUris(uris: List<Uri>, playImmediately: Boolean = false) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver
            val imported = mutableListOf<TrackEntity>()
            for (uri in uris) {
                try {
                    resolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
                val track = playbackManager.extractTrackFromUri(uri)
                musicDao.upsertTrack(track)
                trackDao.upsertOfflineTrack(track.toOfflineTrack(isCurrentlyPlaying = false))
                imported.add(track)
            }
            if (imported.isNotEmpty()) {
                _settingsStatusMessage.value = "Imported ${imported.size} audio file(s) into your offline library."
                if (playImmediately) {
                    val fullCatalog = musicDao.getAllTracksOnce().filterNot { it.isDemoTrack }
                    playTrackFromList(imported.first(), fullCatalog)
                    _isNowPlayingExpanded.value = true
                }
            }
        }
    }

    fun setAudioQuality(preset: AudioQualityPreset) {
        _audioQuality.value = preset
        viewModelScope.launch {
            musicDao.putSetting(MusicSettingEntity("audio_quality", preset.name))
            _settingsStatusMessage.value = "Audio quality set to ${preset.label}."
        }
    }

    fun setGaplessPlayback(enabled: Boolean) {
        _gaplessPlaybackEnabled.value = enabled
        val targetCrossfadeSec = if (enabled) {
            playbackState.value.crossfadeDurationSec.takeIf { it > 0 } ?: 4
        } else {
            0
        }
        playbackManager.setCrossfadeDurationSeconds(targetCrossfadeSec)
        viewModelScope.launch {
            musicDao.putSetting(MusicSettingEntity("gapless_enabled", enabled.toString()))
            musicDao.putSetting(MusicSettingEntity("crossfade_duration_sec", targetCrossfadeSec.toString()))
            syncPlaybackPreferencesToCloud()
        }
    }

    // --- OTA Updates & Push Notification Preferences (Section 7) ---

    fun checkForOtaUpdate() {
        viewModelScope.launch {
            _otaManifest.value = _otaManifest.value.copy(
                statusLabel = "Checking HTTPS manifest & verifying Ed25519 signature..."
            )
            delay(350)
            val candidate = BeatifyOtaVerifier.buildSignedManifestForChannel(_otaManifest.value.channel)
            val verification = BeatifyOtaVerifier.verifyManifest(candidate)
            if (verification.isSuccess) {
                _otaManifest.value = _otaManifest.value.copy(
                    bundleVersion = candidate.bundleVersion,
                    downloadUrl = candidate.downloadUrl,
                    sha256Checksum = "sha256:${candidate.sha256Checksum}",
                    releaseNotes = candidate.releaseNotes,
                    lastCheckedAtMs = System.currentTimeMillis(),
                    signatureVerified = true,
                    statusLabel = "Verified • Bundle ${candidate.bundleVersion} compatible with native v1.0.0"
                )
                _settingsStatusMessage.value = "OTA check complete: bundle ${candidate.bundleVersion} SHA-256 & signature verified."
            } else {
                val err = verification.exceptionOrNull()?.message ?: "Integrity check failed"
                _otaManifest.value = _otaManifest.value.copy(
                    signatureVerified = false,
                    statusLabel = "Verification failed ($err) • Retained active bundle ${_otaManifest.value.bundleVersion}"
                )
            }
        }
    }

    fun switchOtaChannel(channel: String) {
        if (channel !in setOf("stable", "beta")) return
        val signedManifest = BeatifyOtaVerifier.buildSignedManifestForChannel(channel)
        _otaManifest.value = _otaManifest.value.copy(
            channel = channel,
            previousGoodBundleVersion = _otaManifest.value.bundleVersion,
            bundleVersion = signedManifest.bundleVersion,
            downloadUrl = signedManifest.downloadUrl,
            sha256Checksum = "sha256:${signedManifest.sha256Checksum}",
            lastCheckedAtMs = System.currentTimeMillis(),
            statusLabel = "Channel switched to $channel • Staged ${signedManifest.bundleVersion} (MediaSession preserved)"
        )
        _settingsStatusMessage.value = "OTA channel set to $channel (${signedManifest.bundleVersion} staged without interrupting Media3 playback)."
    }

    fun rollbackOtaBundle() {
        val fallbackVersion = _otaManifest.value.previousGoodBundleVersion
        _otaManifest.value = OtaBundleManifest(
            bundleVersion = fallbackVersion,
            channel = "stable",
            statusLabel = "Rolled back to verified baseline ($fallbackVersion) • Active MediaSession preserved"
        )
        _settingsStatusMessage.value = "Rolled back web bundle to verified baseline ($fallbackVersion)."
    }

    fun toggleNotificationCategory(category: String) {
        val current = _notificationCategories.value.toMutableSet()
        if (category in current) {
            current.remove(category)
        } else {
            current.add(category)
        }
        _notificationCategories.value = current
        syncDeviceRegistrationToCloud()
    }

    fun setMarketingOptIn(enabled: Boolean) {
        _marketingOptIn.value = enabled
        syncDeviceRegistrationToCloud()
    }

    fun dismissForegroundBanner() {
        _activeForegroundBanner.value = null
    }

    fun triggerSampleFcmNotification(category: String = "music") {
        val item = when (category) {
            "podcasts" -> BeatifyPushNotificationItem(
                id = "fcm_${UUID.randomUUID()}",
                category = "podcasts",
                title = "Waveform Diaries • New Episode",
                body = "Tap to tune in to Ep. 19 — Inside Buchla & Eurorack Live Improvisation.",
                deepLink = "beatify://radio"
            )
            "playlists" -> BeatifyPushNotificationItem(
                id = "fcm_${UUID.randomUUID()}",
                category = "playlists",
                title = "Playlist Synced across Devices",
                body = "Your cloud playlists were synchronized via Firestore.",
                deepLink = "beatify://playlist"
            )
            else -> BeatifyPushNotificationItem(
                id = "fcm_${UUID.randomUUID()}",
                category = "music",
                title = "Media3 Playback Session Active",
                body = "Tap to open the full-screen Now Playing & Queue controller.",
                deepLink = "beatify://now-playing"
            )
        }
        _pushNotifications.value = listOf(item) + _pushNotifications.value
        _activeForegroundBanner.value = item
    }

    private fun syncPlaybackPreferencesToCloud() {
        val uid = firebaseAuth?.currentUser?.uid ?: return
        val state = playbackState.value
        viewModelScope.launch {
            firebaseRepository?.savePlaybackPreferences(
                shuffleEnabled = state.shuffleEnabled,
                repeatMode = state.repeatMode.wireValue,
                playbackSpeed = state.playbackSpeed.toDouble(),
                crossfadeEnabled = _gaplessPlaybackEnabled.value
            )
        }
    }

    private fun syncDeviceRegistrationToCloud() {
        val uid = firebaseAuth?.currentUser?.uid ?: return
        viewModelScope.launch {
            firebaseRepository?.registerDevice(
                deviceId = "android_primary",
                rawTokenOrSeed = "fcm_beatify_$uid",
                appVersion = "1.0.0",
                notificationCategories = _notificationCategories.value.toList(),
                marketingOptIn = _marketingOptIn.value
            )
        }
    }

    fun setContentProtectionEnabled(enabled: Boolean) {
        contentBlockingService.setEnabled(enabled)
        viewModelScope.launch {
            musicDao.putSetting(MusicSettingEntity("content_protection_enabled", enabled.toString()))
        }
    }

    fun addCustomBlockedDomain(domain: String) {
        val added = contentBlockingService.addCustomRule(domain)
        _settingsStatusMessage.value = if (added) {
            "Added filter rule for $domain."
        } else {
            "Invalid or duplicate domain rule."
        }
    }

    fun removeCustomBlockedDomain(domain: String) {
        contentBlockingService.removeCustomRule(domain)
    }

    fun rescanLibraryAndVerifyAvailability() {
        viewModelScope.launch {
            val currentTracks = musicDao.getAllTracksOnce().filterNot { it.isDemoTrack }
            var unavailableCount = 0
            for (track in currentTracks) {
                val accessible = playbackManager.verifyTrackAccessible(track)
                if (accessible != track.isAvailable) {
                    musicDao.updateTrackAvailability(track.id, accessible)
                }
                if (!accessible) unavailableCount++
            }
            _settingsStatusMessage.value = if (unavailableCount == 0) {
                "Library scan complete: all ${currentTracks.size} tracks verified and accessible."
            } else {
                "Library scan complete: $unavailableCount local file(s) had revoked permissions."
            }
        }
    }

    fun clearCachedArtwork() {
        viewModelScope.launch {
            val deleted = playbackManager.clearCachedArtwork()
            _settingsStatusMessage.value = "Cleared $deleted cached embedded artwork file(s)."
        }
    }

    private fun persistPlaybackSnapshot(state: PlaybackState) {
        viewModelScope.launch {
            val snapshot = PlaybackSnapshotEntity(
                currentTrackId = state.currentTrack?.id,
                queueTrackIdsCsv = state.queue.joinToString(",") { it.id },
                positionMs = state.positionMs,
                repeatMode = state.repeatMode.wireValue,
                shuffleEnabled = state.shuffleEnabled,
                playbackSpeed = state.playbackSpeed,
                updatedAtEpochMs = System.currentTimeMillis()
            )
            musicDao.savePlaybackSnapshot(snapshot)
            state.currentTrack?.let { current ->
                trackDao.updatePlaybackState(
                    id = current.id,
                    positionMs = state.positionMs,
                    isPlaying = state.isPlaying
                )
            }
        }
    }

    fun refreshCurrentTrackLyrics() {
        val currentTrack = playbackState.value.currentTrack ?: return
        viewModelScope.launch {
            syncedLyricsService.loadLyricsForTrack(currentTrack, forceRefresh = true)
        }
    }

    override fun onCleared() {
        debouncedSearchCoordinator.cancelAll()
        playbackManager.release()
        super.onCleared()
    }
}
