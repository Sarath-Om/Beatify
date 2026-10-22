package com.example

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AlbumSummary
import com.example.data.ArtistSummary
import com.example.data.AudioQualityPreset
import com.example.data.BundledMusicCatalog
import com.example.data.LibraryCategory
import com.example.data.MusicDestination
import com.example.data.MusicSettingEntity
import com.example.data.PlaybackSnapshotEntity
import com.example.data.PlaybackState
import com.example.data.PlaylistEntity
import com.example.data.RepeatMode
import com.example.data.SynapseDatabase
import com.example.data.TrackEntity
import com.example.data.TrackSortOption
import com.example.playback.PlaybackManager
import com.example.services.ContentBlockingService
import com.example.services.ContentProtectionReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class SynapseViewModel(application: Application) : AndroidViewModel(application) {

    private val database = SynapseDatabase.getInstance(application)
    private val musicDao = database.musicDao()

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

    private val _audioQuality = MutableStateFlow(AudioQualityPreset.LOSSLESS)
    val audioQuality: StateFlow<AudioQualityPreset> = _audioQuality.asStateFlow()

    private val _gaplessPlaybackEnabled = MutableStateFlow(true)
    val gaplessPlaybackEnabled: StateFlow<Boolean> = _gaplessPlaybackEnabled.asStateFlow()

    private val _settingsStatusMessage = MutableStateFlow<String?>(null)
    val settingsStatusMessage: StateFlow<String?> = _settingsStatusMessage.asStateFlow()

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

    fun selectDestination(destination: MusicDestination) {
        _currentDestination.value = destination
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

    fun playTrackFromList(track: TrackEntity, queueList: List<TrackEntity>) {
        viewModelScope.launch {
            val listToUse = queueList.ifEmpty { listOf(track) }
            val startIndex = listToUse.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            playbackManager.setQueue(listToUse, startIndex)
            playbackManager.play(track.id)
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
        }
    }

    fun toggleShuffle() {
        viewModelScope.launch {
            playbackManager.setShuffleEnabled(!playbackState.value.shuffleEnabled)
        }
    }

    fun setVolume(volume: Float) {
        playbackManager.setVolume(volume)
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackManager.setPlaybackSpeed(speed)
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
            val palette = listOf(0xFF0A84FF, 0xFF7C5CFF, 0xFFFA2D48, 0xFF30D158)
            val color = palette[(name.hashCode() and 0x7FFFFFFF) % palette.size]
            val playlist = PlaylistEntity(
                id = "playlist_${UUID.randomUUID()}",
                name = name,
                description = description,
                trackIdsCsv = "",
                coverColorHex = color
            )
            musicDao.upsertPlaylist(playlist)
            _selectedLibraryCategory.value = LibraryCategory.PLAYLISTS
        }
    }

    fun addTrackToPlaylist(playlistId: String, track: TrackEntity) {
        viewModelScope.launch {
            val existing = musicDao.getPlaylistById(playlistId) ?: return@launch
            val updatedIds = (existing.trackIds() + track.id).distinct()
            musicDao.upsertPlaylist(
                existing.copy(
                    trackIdsCsv = updatedIds.joinToString(","),
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
            _settingsStatusMessage.value = "Added '${track.title}' to ${existing.name}."
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            musicDao.deletePlaylist(playlistId)
        }
    }

    /**
     * Imports user-selected local audio files (`content://` URIs), taking persistable read
     * permission and extracting ID3/container metadata via `MediaMetadataRetriever`.
     */
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
                    // Some providers grant one-time read access without persistable flags
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
