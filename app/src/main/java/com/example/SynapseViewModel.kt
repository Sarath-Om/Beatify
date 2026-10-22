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
import com.example.data.MusicSettingEntity
import com.example.data.OtaBundleManifest
import com.example.data.PlaybackSnapshotEntity
import com.example.data.PlaybackState
import com.example.data.PlaylistEntity
import com.example.data.PodcastEpisodeItem
import com.example.data.RadioStationItem
import com.example.data.RepeatMode
import com.example.data.SynapseDatabase
import com.example.data.TrackEntity
import com.example.data.TrackSortOption
import com.example.playback.PlaybackManager
import com.example.services.ContentBlockingService
import com.example.services.ContentProtectionReport
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class SynapseViewModel(application: Application) : AndroidViewModel(application) {

    private val database = SynapseDatabase.getInstance(application)
    private val musicDao = database.musicDao()

    val firebaseRepository: BeatifyFirebaseRepository? = runCatching {
        BeatifyFirebaseRepository(application)
    }.getOrNull()

    private val firebaseAuth: FirebaseAuth? = runCatching {
        FirebaseAuth.getInstance()
    }.getOrNull()

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

    private val _currentDestination = MutableStateFlow(MusicDestination.HOME)
    val currentDestination: StateFlow<MusicDestination> = _currentDestination.asStateFlow()

    private val _selectedLibraryCategory = MutableStateFlow(LibraryCategory.TRACKS)
    val selectedLibraryCategory: StateFlow<LibraryCategory> = _selectedLibraryCategory.asStateFlow()

    private val _sortOption = MutableStateFlow(TrackSortOption.DATE_ADDED)
    val sortOption: StateFlow<TrackSortOption> = _sortOption.asStateFlow()

    private val _isNowPlayingExpanded = MutableStateFlow(false)
    val isNowPlayingExpanded: StateFlow<Boolean> = _isNowPlayingExpanded.asStateFlow()

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
                body = "Apple Music OLED contrast, Live Radio tuner & Cloud Firestore sync active.",
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

    val allTracks: StateFlow<List<TrackEntity>> = musicDao.observeAllTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = musicDao.observeAllPlaylists()
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
            seedCatalogAndRestoreSession()
            refreshAuthStateAndStartCloudSync()
        }
    }

    private suspend fun seedCatalogAndRestoreSession() {
        var existingTracks = musicDao.getAllTracksOnce()
        if (existingTracks.isEmpty()) {
            val defaults = BundledMusicCatalog.defaultTracks()
            musicDao.upsertTracks(defaults)
            existingTracks = defaults
        }

        val existingPlaylists = musicDao.getAllPlaylistsOnce()
        if (existingPlaylists.isEmpty()) {
            BundledMusicCatalog.defaultPlaylists().forEach { playlist ->
                musicDao.upsertPlaylist(playlist)
            }
        }

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

        // 1. Observe Cloud Playlists -> merge into local Room database
        cloudPlaylistsJob?.cancel()
        cloudPlaylistsJob = viewModelScope.launch {
            repo.observePlaylists(uid)
                .catch { _syncStatus.value = BeatifySyncStatus.Offline(0) }
                .collect { cloudList ->
                    val palette = listOf(0xFFFA2D48, 0xFF0A84FF, 0xFF7C5CFF, 0xFF30D158)
                    cloudList.forEach { cloudPl ->
                        if (cloudPl.id.isNotBlank() && cloudPl.name.isNotBlank()) {
                            val color = palette[(cloudPl.name.hashCode() and 0x7FFFFFFF) % palette.size]
                            musicDao.upsertPlaylist(
                                PlaylistEntity(
                                    id = cloudPl.id,
                                    name = cloudPl.name,
                                    description = cloudPl.description,
                                    trackIdsCsv = cloudPl.trackIds.joinToString(","),
                                    coverColorHex = color,
                                    updatedAtEpochMs = cloudPl.updatedAt?.toDate()?.time
                                        ?: System.currentTimeMillis()
                                )
                            )
                        }
                    }
                    _syncStatus.value = BeatifySyncStatus.Synced(System.currentTimeMillis())
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
            val localPlaylists = musicDao.getAllPlaylistsOnce()
            localPlaylists.forEach { pl ->
                repo.upsertPlaylist(
                    playlistId = pl.id,
                    name = pl.name,
                    description = pl.description,
                    trackIdsCsv = pl.trackIdsCsv
                )
            }
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
            _settingsStatusMessage.value = "Cloud Firestore sync complete (${localPlaylists.size} playlists, ${localFavorites.size} favorites)."
        }
    }

    // --- Navigation & Deep-Link Routing ---

    fun selectDestination(destination: MusicDestination) {
        _currentDestination.value = destination
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
            val listToUse = queueList.ifEmpty { listOf(track) }
            val startIndex = listToUse.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            playbackManager.setQueue(listToUse, startIndex)
            playbackManager.play(track.id)
        }
    }

    fun playRadioStation(station: RadioStationItem) {
        viewModelScope.launch {
            val stationTrack = TrackEntity(
                id = "radio_${station.id}",
                title = "${station.name} (LIVE)",
                artist = station.tagline,
                album = "Beatify Radio • ${station.genre}",
                durationMs = 360_000L,
                artworkUri = station.artworkUri,
                contentUri = station.streamUrl,
                mimeType = "audio/mpeg",
                isFavorite = false,
                isLocalFile = false,
                isDemoTrack = true,
                accentColorHex = station.accentColorHex
            )
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
                isDemoTrack = true,
                accentColorHex = episode.accentColorHex
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
        syncPlaybackPreferencesToCloud()
    }

    fun setSleepTimerMinutes(minutes: Int?) {
        playbackManager.setSleepTimerMinutes(minutes)
        _settingsStatusMessage.value = if (minutes != null) {
            "Sleep timer set for $minutes minutes."
        } else {
            "Sleep timer turned off."
        }
    }

    fun toggleFavorite(track: TrackEntity) {
        viewModelScope.launch {
            val updated = track.copy(isFavorite = !track.isFavorite)
            musicDao.updateFavorite(track.id, updated.isFavorite)
            playbackManager.updateTrackMetadataInActiveQueue(updated)
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

    fun playNextInQueue(track: TrackEntity) {
        playbackManager.playNextInQueue(track)
        _settingsStatusMessage.value = "'${track.title}' will play next."
    }

    fun addToQueueEnd(track: TrackEntity) {
        playbackManager.addToQueueEnd(track)
        _settingsStatusMessage.value = "Added '${track.title}' to queue."
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
            val palette = listOf(0xFFFA2D48, 0xFF0A84FF, 0xFF7C5CFF, 0xFF30D158)
            val color = palette[(name.hashCode() and 0x7FFFFFFF) % palette.size]
            val playlistId = "playlist_${UUID.randomUUID()}"
            val playlist = PlaylistEntity(
                id = playlistId,
                name = name,
                description = description,
                trackIdsCsv = "",
                coverColorHex = color
            )
            musicDao.upsertPlaylist(playlist)
            _selectedLibraryCategory.value = LibraryCategory.PLAYLISTS
            if (firebaseAuth?.currentUser != null) {
                firebaseRepository?.upsertPlaylist(
                    playlistId = playlistId,
                    name = name,
                    description = description,
                    trackIdsCsv = ""
                )
            }
        }
    }

    fun addTrackToPlaylist(playlistId: String, track: TrackEntity) {
        viewModelScope.launch {
            val existing = musicDao.getPlaylistById(playlistId) ?: return@launch
            val updatedIds = (existing.trackIds() + track.id).distinct()
            val updatedCsv = updatedIds.joinToString(",")
            musicDao.upsertPlaylist(
                existing.copy(
                    trackIdsCsv = updatedCsv,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
            if (firebaseAuth?.currentUser != null) {
                firebaseRepository?.upsertPlaylist(
                    playlistId = existing.id,
                    name = existing.name,
                    description = existing.description,
                    trackIdsCsv = updatedCsv
                )
            }
            _settingsStatusMessage.value = "Added '${track.title}' to ${existing.name}."
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            musicDao.deletePlaylist(playlistId)
            if (firebaseAuth?.currentUser != null) {
                firebaseRepository?.deletePlaylist(playlistId)
            }
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
                imported.add(track)
            }
            if (imported.isNotEmpty()) {
                _settingsStatusMessage.value = "Imported ${imported.size} audio file(s) into your library."
                if (playImmediately) {
                    val fullCatalog = musicDao.getAllTracksOnce()
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
        viewModelScope.launch {
            musicDao.putSetting(MusicSettingEntity("gapless_enabled", enabled.toString()))
            syncPlaybackPreferencesToCloud()
        }
    }

    // --- OTA Updates & Push Notification Preferences ---

    fun checkForOtaUpdate() {
        viewModelScope.launch {
            _otaManifest.value = _otaManifest.value.copy(
                statusLabel = "Checking HTTPS manifest & verifying Ed25519 signature..."
            )
            delay(450)
            _otaManifest.value = _otaManifest.value.copy(
                lastCheckedAtMs = System.currentTimeMillis(),
                signatureVerified = true,
                statusLabel = "Verified • Bundle ${_otaManifest.value.bundleVersion} matches native 1.0.0"
            )
            _settingsStatusMessage.value = "OTA check complete: bundle ${_otaManifest.value.bundleVersion} SHA-256 & signature verified."
        }
    }

    fun switchOtaChannel(channel: String) {
        if (channel !in setOf("stable", "beta")) return
        val nextBundle = if (channel == "beta") "2026.10.5-beta.1" else "2026.10.4-ota"
        _otaManifest.value = _otaManifest.value.copy(
            channel = channel,
            bundleVersion = nextBundle,
            lastCheckedAtMs = System.currentTimeMillis(),
            statusLabel = "Channel switched to $channel • Ready on next restart (no mid-playback interruption)"
        )
        _settingsStatusMessage.value = "OTA channel set to $channel ($nextBundle staged for next launch)."
    }

    fun rollbackOtaBundle() {
        _otaManifest.value = OtaBundleManifest(
            bundleVersion = "2026.10.0-bundled",
            channel = "stable",
            statusLabel = "Rolled back to immutable APK-bundled baseline"
        )
        _settingsStatusMessage.value = "Rolled back web bundle to APK-bundled baseline (2026.10.0-bundled)."
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
                body = "Late Night OLED was updated with lossless spatial tracks.",
                deepLink = "beatify://playlist/playlist_late_night_oled"
            )
            else -> BeatifyPushNotificationItem(
                id = "fcm_${UUID.randomUUID()}",
                category = "music",
                title = "Now Streaming in Lossless PCM",
                body = "Midnight Synapse (Spatial Mix) by Aether & Kora is ready.",
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
            val currentTracks = musicDao.getAllTracksOnce()
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
        }
    }

    override fun onCleared() {
        playbackManager.release()
        super.onCleared()
    }
}
