package com.example.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.PlaybackState
import com.example.data.RepeatMode
import com.example.data.TrackEntity
import com.example.services.BridgePlaybackEvent
import com.example.services.ContentBlockingService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Authoritative `MediaSessionController.kt` (Section 2).
 *
 * Responsibilities:
 * - Connects asynchronously to the active Android Media3 `MediaSession` owned by `PlaybackService`
 *   using `MediaController.Builder(context, sessionToken).buildAsync()` without blocking the main thread.
 * - Never creates a second ExoPlayer instance.
 * - Exposes a typed `PlaybackState` and `BridgePlaybackEvent` stream (`playbackStateChanged`,
 *   `currentTrackChanged`, `queueChanged`, `playbackError`).
 * - Supports seamless reconnection after Activity recreation or WebView reload and prevents
 *   duplicate event listeners.
 */
class MediaSessionController(
    private val context: Context,
    private val contentBlockingService: ContentBlockingService,
    private val onTrackStarted: (TrackEntity, Long) -> Unit = { _, _ -> },
    private val onQueueOrStatePersisted: (PlaybackState) -> Unit = {},
    private val onTrackAvailabilityChanged: (String, Boolean) -> Unit = { _, _ -> }
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _bridgeEvents = MutableSharedFlow<BridgePlaybackEvent>(extraBufferCapacity = 32)
    val bridgeEvents: SharedFlow<BridgePlaybackEvent> = _bridgeEvents.asSharedFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var listenerAttachedPlayer: Player? = null
    private var progressPollingJob: Job? = null

    private val activePlayer: Player
        get() = mediaController ?: PlaybackEngineHolder.getOrCreatePlayer(appContext)

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncStateFromPlayer(emitStateEvent = true)
            if (isPlaying) {
                startProgressLoop()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncStateFromPlayer(emitStateEvent = true)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncStateFromPlayer(emitStateEvent = true)
            val current = _playbackState.value.currentTrack
            if (current != null) {
                _bridgeEvents.tryEmit(BridgePlaybackEvent.CurrentTrackChanged(current))
                onTrackStarted(current, activePlayer.currentPosition.coerceAtLeast(0L))
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            syncStateFromPlayer(emitStateEvent = true)
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            syncStateFromPlayer(emitStateEvent = true)
        }

        override fun onPlayerError(error: PlaybackException) {
            val failedTrack = _playbackState.value.currentTrack
            val message = if (failedTrack?.isLocalFile == true) {
                onTrackAvailabilityChanged(failedTrack.id, false)
                "Local audio file '${failedTrack.title}' is unavailable or permission was revoked."
            } else {
                "Playback error for '${failedTrack?.title ?: "media"}': ${error.errorCodeName}"
            }
            _playbackState.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = message
                )
            }
            _bridgeEvents.tryEmit(
                BridgePlaybackEvent.PlaybackError(
                    code = error.errorCodeName,
                    message = message,
                    trackId = failedTrack?.id
                )
            )
            if (activePlayer.hasNextMediaItem()) {
                activePlayer.seekToNextMediaItem()
                activePlayer.prepare()
            }
        }
    }

    init {
        attachSingleListener(PlaybackEngineHolder.getOrCreatePlayer(appContext))
        connectOrReconnectAsync()
        startProgressLoop()
    }

    /**
     * Connects or reconnects asynchronously to `PlaybackService` using Media3's non-blocking
     * `ListenableFuture<MediaController>` API. Safe across Activity recreation and WebView reload.
     */
    fun connectOrReconnectAsync() {
        if (mediaController?.isConnected == true) {
            syncStateFromPlayer(emitStateEvent = true)
            return
        }
        try {
            val sessionToken = SessionToken(
                appContext,
                ComponentName(appContext, MediaPlaybackService::class.java)
            )
            val future = MediaController.Builder(appContext, sessionToken)
                .setListener(object : MediaController.Listener {
                    override fun onDisconnected(controller: MediaController) {
                        mediaController = null
                        attachSingleListener(PlaybackEngineHolder.getOrCreatePlayer(appContext))
                    }
                })
                .buildAsync()
            controllerFuture = future
            future.addListener(
                {
                    try {
                        val controller = future.get()
                        mediaController = controller
                        attachSingleListener(controller)
                        syncStateFromPlayer(emitStateEvent = true)
                    } catch (_: Exception) {
                        // Direct shared player remains attached
                    }
                },
                ContextCompat.getMainExecutor(appContext)
            )
        } catch (_: Exception) {
            // Safe fallback in unit/Robolectric environments
        }
    }

    private fun attachSingleListener(target: Player) {
        if (listenerAttachedPlayer === target) return
        listenerAttachedPlayer?.removeListener(playerListener)
        target.addListener(playerListener)
        listenerAttachedPlayer = target
    }

    private fun ensureServiceStarted() {
        try {
            val intent = Intent(appContext, MediaPlaybackService::class.java)
            appContext.startService(intent)
        } catch (_: Exception) {
        }
    }

    private fun startProgressLoop() {
        if (progressPollingJob?.isActive == true) return
        progressPollingJob = scope.launch {
            while (isActive) {
                if (activePlayer.isPlaying) {
                    val pos = activePlayer.currentPosition.coerceAtLeast(0L)
                    val dur = activePlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L }
                        ?: _playbackState.value.currentTrack?.durationMs
                        ?: 0L
                    val buf = activePlayer.bufferedPosition.coerceAtLeast(pos)
                    _playbackState.update { state ->
                        state.copy(
                            positionMs = pos,
                            durationMs = dur,
                            bufferedPositionMs = buf
                        )
                    }
                }
                // Throttled 500ms progress interval so UI progress bars update smoothly
                // without triggering excessive recompositions on every native frame tick.
                delay(500L)
            }
        }
    }

    fun getCurrentState(): PlaybackState {
        syncStateFromPlayer(emitStateEvent = false)
        return _playbackState.value
    }

    fun setQueue(tracks: List<TrackEntity>, startIndex: Int = 0) {
        if (tracks.isEmpty()) {
            activePlayer.clearMediaItems()
            _playbackState.update {
                it.copy(
                    isPlaying = false,
                    currentTrack = null,
                    queue = emptyList(),
                    positionMs = 0L,
                    durationMs = 0L,
                    errorMessage = null
                )
            }
            _bridgeEvents.tryEmit(BridgePlaybackEvent.QueueChanged(emptyList(), -1))
            return
        }

        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        val mediaItems = tracks.map { it.toMediaItem() }
        val resumePos = tracks[safeIndex].lastPositionMs
            .takeIf { pos -> pos > 0L && pos < tracks[safeIndex].durationMs - 5_000L } ?: 0L

        _playbackState.update {
            it.copy(
                queue = tracks,
                currentTrack = tracks[safeIndex],
                positionMs = resumePos,
                durationMs = tracks[safeIndex].durationMs,
                errorMessage = null
            )
        }

        activePlayer.setMediaItems(mediaItems, safeIndex, resumePos)
        activePlayer.prepare()
        syncStateFromPlayer(emitStateEvent = true)
        _bridgeEvents.tryEmit(BridgePlaybackEvent.QueueChanged(tracks, safeIndex))
        _bridgeEvents.tryEmit(BridgePlaybackEvent.CurrentTrackChanged(tracks[safeIndex]))
        onQueueOrStatePersisted(_playbackState.value)
    }

    fun play(trackId: String? = null, isTrackAccessible: (TrackEntity) -> Boolean = { true }) {
        ensureServiceStarted()
        val currentQueue = _playbackState.value.queue
        if (trackId != null) {
            val targetIndex = currentQueue.indexOfFirst { it.id == trackId }
            if (targetIndex >= 0 && targetIndex != activePlayer.currentMediaItemIndex) {
                activePlayer.seekToDefaultPosition(targetIndex)
            }
        }

        val activeTrack = _playbackState.value.currentTrack
        if (activeTrack != null && !isTrackAccessible(activeTrack)) {
            onTrackAvailabilityChanged(activeTrack.id, false)
            val errMsg = "Audio file '${activeTrack.title}' was moved or permission expired."
            _playbackState.update {
                it.copy(isPlaying = false, errorMessage = errMsg)
            }
            _bridgeEvents.tryEmit(BridgePlaybackEvent.PlaybackError("FILE_UNAVAILABLE", errMsg, activeTrack.id))
            return
        }

        if (activePlayer.playbackState == Player.STATE_IDLE) {
            activePlayer.prepare()
        } else if (activePlayer.playbackState == Player.STATE_ENDED) {
            activePlayer.seekTo(0L)
        }
        activePlayer.play()
        syncStateFromPlayer(emitStateEvent = true)
        _playbackState.value.currentTrack?.let { track ->
            onTrackStarted(track, activePlayer.currentPosition.coerceAtLeast(0L))
        }
        onQueueOrStatePersisted(_playbackState.value)
    }

    fun pause() {
        activePlayer.pause()
        syncStateFromPlayer(emitStateEvent = true)
        onQueueOrStatePersisted(_playbackState.value)
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0L)
        activePlayer.seekTo(target)
        _playbackState.update { it.copy(positionMs = target) }
        syncStateFromPlayer(emitStateEvent = true)
        onQueueOrStatePersisted(_playbackState.value)
    }

    fun skipNext() {
        val queue = _playbackState.value.queue
        if (queue.isEmpty()) return
        if (activePlayer.hasNextMediaItem()) {
            activePlayer.seekToNextMediaItem()
        } else if (_playbackState.value.repeatMode == RepeatMode.ALL && queue.isNotEmpty()) {
            activePlayer.seekToDefaultPosition(0)
        }
        activePlayer.play()
        syncStateFromPlayer(emitStateEvent = true)
        onQueueOrStatePersisted(_playbackState.value)
    }

    fun skipPrevious() {
        val queue = _playbackState.value.queue
        if (queue.isEmpty()) return
        if (activePlayer.currentPosition > 3_500L) {
            activePlayer.seekTo(0L)
        } else if (activePlayer.hasPreviousMediaItem()) {
            activePlayer.seekToPreviousMediaItem()
        } else if (_playbackState.value.repeatMode == RepeatMode.ALL && queue.isNotEmpty()) {
            activePlayer.seekToDefaultPosition(queue.lastIndex)
        } else {
            activePlayer.seekTo(0L)
        }
        activePlayer.play()
        syncStateFromPlayer(emitStateEvent = true)
        onQueueOrStatePersisted(_playbackState.value)
    }

    fun setRepeatMode(mode: RepeatMode) {
        val media3Repeat = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
        activePlayer.repeatMode = media3Repeat
        _playbackState.update { it.copy(repeatMode = mode) }
        syncStateFromPlayer(emitStateEvent = true)
        onQueueOrStatePersisted(_playbackState.value)
    }

    fun setShuffleEnabled(enabled: Boolean) {
        activePlayer.shuffleModeEnabled = enabled
        _playbackState.update { it.copy(shuffleEnabled = enabled) }
        syncStateFromPlayer(emitStateEvent = true)
        onQueueOrStatePersisted(_playbackState.value)
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        activePlayer.playbackParameters = PlaybackParameters(clamped)
        _playbackState.update { it.copy(playbackSpeed = clamped) }
        syncStateFromPlayer(emitStateEvent = true)
        onQueueOrStatePersisted(_playbackState.value)
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        activePlayer.volume = clamped
        _playbackState.update { it.copy(volume = clamped) }
    }

    fun updateSleepTimerRemaining(remainingMs: Long?) {
        _playbackState.update { it.copy(sleepTimerRemainingMs = remainingMs) }
    }

    fun playNextInQueue(track: TrackEntity) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        if (currentQueue.isEmpty()) {
            setQueue(listOf(track), 0)
            play(track.id)
            return
        }
        val currentIndex = activePlayer.currentMediaItemIndex.coerceIn(0, currentQueue.lastIndex)
        val insertIndex = (currentIndex + 1).coerceAtMost(currentQueue.size)
        currentQueue.add(insertIndex, track)
        activePlayer.addMediaItem(insertIndex, track.toMediaItem())
        _playbackState.update { it.copy(queue = currentQueue) }
        _bridgeEvents.tryEmit(BridgePlaybackEvent.QueueChanged(currentQueue, currentIndex))
        onQueueOrStatePersisted(_playbackState.value)
    }

    fun addToQueue(track: TrackEntity) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        if (currentQueue.isEmpty()) {
            setQueue(listOf(track), 0)
            return
        }
        currentQueue.add(track)
        activePlayer.addMediaItem(track.toMediaItem())
        _playbackState.update { it.copy(queue = currentQueue) }
        _bridgeEvents.tryEmit(
            BridgePlaybackEvent.QueueChanged(
                currentQueue,
                activePlayer.currentMediaItemIndex.coerceAtLeast(0)
            )
        )
        onQueueOrStatePersisted(_playbackState.value)
    }

    fun removeFromQueue(index: Int) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        if (index !in currentQueue.indices) return
        currentQueue.removeAt(index)
        if (index < activePlayer.mediaItemCount) {
            activePlayer.removeMediaItem(index)
        }
        val currentIdx = activePlayer.currentMediaItemIndex.coerceAtLeast(0)
        _playbackState.update {
            it.copy(
                queue = currentQueue,
                currentTrack = currentQueue.getOrNull(currentIdx)
            )
        }
        syncStateFromPlayer(emitStateEvent = true)
        _bridgeEvents.tryEmit(BridgePlaybackEvent.QueueChanged(currentQueue, currentIdx))
        onQueueOrStatePersisted(_playbackState.value)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        if (fromIndex !in currentQueue.indices || toIndex !in currentQueue.indices || fromIndex == toIndex) return
        val moved = currentQueue.removeAt(fromIndex)
        currentQueue.add(toIndex, moved)
        if (fromIndex < activePlayer.mediaItemCount && toIndex < activePlayer.mediaItemCount) {
            activePlayer.moveMediaItem(fromIndex, toIndex)
        }
        _playbackState.update { it.copy(queue = currentQueue) }
        syncStateFromPlayer(emitStateEvent = true)
        _bridgeEvents.tryEmit(
            BridgePlaybackEvent.QueueChanged(
                currentQueue,
                activePlayer.currentMediaItemIndex.coerceAtLeast(0)
            )
        )
        onQueueOrStatePersisted(_playbackState.value)
    }

    fun clearUpcomingQueue() {
        val currentQueue = _playbackState.value.queue
        if (currentQueue.size <= 1) return
        val currentIndex = activePlayer.currentMediaItemIndex.coerceIn(0, currentQueue.lastIndex)
        val activeTrack = currentQueue[currentIndex]
        if (currentIndex + 1 < activePlayer.mediaItemCount) {
            activePlayer.removeMediaItems(currentIndex + 1, activePlayer.mediaItemCount)
        }
        if (currentIndex > 0) {
            activePlayer.removeMediaItems(0, currentIndex)
        }
        val singleQueue = listOf(activeTrack)
        _playbackState.update {
            it.copy(queue = singleQueue, currentTrack = activeTrack)
        }
        _bridgeEvents.tryEmit(BridgePlaybackEvent.QueueChanged(singleQueue, 0))
        onQueueOrStatePersisted(_playbackState.value)
    }

    fun updateTrackMetadataInActiveQueue(updatedTrack: TrackEntity) {
        val currentQueue = _playbackState.value.queue.map {
            if (it.id == updatedTrack.id) updatedTrack else it
        }
        val currentActive = _playbackState.value.currentTrack?.let {
            if (it.id == updatedTrack.id) updatedTrack else it
        }
        _playbackState.update {
            it.copy(queue = currentQueue, currentTrack = currentActive)
        }
    }

    fun dismissError() {
        _playbackState.update { it.copy(errorMessage = null) }
    }

    private fun TrackEntity.toMediaItem(): MediaItem {
        val sanitizedStreamUrl = contentBlockingService.sanitizeMediaUrl(contentUri)
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album ?: "Beatify")
        artworkUri?.let { art ->
            val sanitizedArt = contentBlockingService.sanitizeMediaUrl(art)
            if (!contentBlockingService.shouldBlockRequest(sanitizedArt)) {
                metadataBuilder.setArtworkUri(Uri.parse(sanitizedArt))
            }
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(Uri.parse(sanitizedStreamUrl))
            .setMimeType(mimeType)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private fun syncStateFromPlayer(emitStateEvent: Boolean) {
        val currentQueue = _playbackState.value.queue
        val mediaId = activePlayer.currentMediaItem?.mediaId
        val activeTrack = currentQueue.firstOrNull { it.id == mediaId }
            ?: currentQueue.getOrNull(activePlayer.currentMediaItemIndex)
            ?: _playbackState.value.currentTrack

        val playerDuration = activePlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L }
            ?: activeTrack?.durationMs
            ?: 0L
        val playerPosition = activePlayer.currentPosition.coerceAtLeast(0L)
        val repeat = when (activePlayer.repeatMode) {
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            else -> RepeatMode.OFF
        }

        _playbackState.update { state ->
            state.copy(
                isPlaying = activePlayer.isPlaying,
                currentTrack = activeTrack,
                positionMs = playerPosition,
                durationMs = playerDuration,
                bufferedPositionMs = activePlayer.bufferedPosition.coerceAtLeast(playerPosition),
                repeatMode = repeat,
                shuffleEnabled = activePlayer.shuffleModeEnabled,
                isBuffering = activePlayer.playbackState == Player.STATE_BUFFERING
            )
        }
        if (emitStateEvent) {
            _bridgeEvents.tryEmit(BridgePlaybackEvent.PlaybackStateChanged(_playbackState.value))
        }
    }

    fun release() {
        progressPollingJob?.cancel()
        listenerAttachedPlayer?.removeListener(playerListener)
        listenerAttachedPlayer = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }
}
