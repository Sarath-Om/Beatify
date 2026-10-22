package com.example.services

import com.example.data.PlaybackState
import com.example.data.RepeatMode
import com.example.data.TrackEntity
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Typed Capacitor / Native Bridge events for Beatify (`com.beatify.in`, Section 4).
 */
sealed interface BridgePlaybackEvent {
    data class PlaybackStateChanged(val state: PlaybackState) : BridgePlaybackEvent
    data class CurrentTrackChanged(val track: TrackEntity?) : BridgePlaybackEvent
    data class QueueChanged(val queue: List<TrackEntity>, val currentIndex: Int) : BridgePlaybackEvent
    data class PlaybackError(val code: String, val message: String, val trackId: String?) : BridgePlaybackEvent
}

/**
 * Authoritative Native Bridge contract matching Section 4 (`Capacitor Native Bridge`).
 *
 * Exposes all required commands (`getPlaybackState`, `setQueue`, `play`, `pause`, `seekTo`,
 * `skipNext`, `skipPrevious`, `setRepeatMode`, `setShuffleEnabled`, `addToQueue`, `removeFromQueue`)
 * and events (`playbackStateChanged`, `currentTrackChanged`, `queueChanged`, `playbackError`)
 * backed directly by `MediaSessionController`.
 */
interface NativeMusicPlayer {
    val playbackState: StateFlow<PlaybackState>
    val bridgeEvents: SharedFlow<BridgePlaybackEvent>

    suspend fun getPlaybackState(): PlaybackState
    suspend fun getState(): PlaybackState = getPlaybackState()
    suspend fun setQueue(tracks: List<TrackEntity>, startIndex: Int = 0)
    suspend fun play(trackId: String? = null)
    suspend fun pause()
    suspend fun seekTo(positionMs: Long)
    suspend fun skipNext()
    suspend fun skipPrevious()
    suspend fun setRepeatMode(mode: RepeatMode)
    suspend fun setShuffleEnabled(enabled: Boolean)
    suspend fun addToQueue(track: TrackEntity)
    suspend fun removeFromQueue(index: Int)
}
