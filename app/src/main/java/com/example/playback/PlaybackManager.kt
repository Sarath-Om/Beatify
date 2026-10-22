package com.example.playback

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.PlaybackState
import com.example.data.RepeatMode
import com.example.data.TrackEntity
import com.example.services.BridgePlaybackEvent
import com.example.services.ContentBlockingService
import com.example.services.NativeMusicPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Authoritative `PlaybackManager` implementing the Capacitor `NativeMusicPlayer` bridge contract
 * and delegating session & player transport to `MediaSessionController`.
 */
class PlaybackManager(
    private val context: Context,
    private val contentBlockingService: ContentBlockingService,
    private val onTrackStarted: (TrackEntity, Long) -> Unit = { _, _ -> },
    private val onQueueOrStatePersisted: (PlaybackState) -> Unit = {},
    private val onTrackAvailabilityChanged: (String, Boolean) -> Unit = { _, _ -> }
) : NativeMusicPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sleepTimerJob: Job? = null

    val mediaSessionController = MediaSessionController(
        context = context,
        contentBlockingService = contentBlockingService,
        onTrackStarted = onTrackStarted,
        onQueueOrStatePersisted = onQueueOrStatePersisted,
        onTrackAvailabilityChanged = onTrackAvailabilityChanged
    )

    override val playbackState: StateFlow<PlaybackState> = mediaSessionController.playbackState
    override val bridgeEvents: SharedFlow<BridgePlaybackEvent> = mediaSessionController.bridgeEvents

    override suspend fun getPlaybackState(): PlaybackState {
        return mediaSessionController.getCurrentState()
    }

    override suspend fun setQueue(tracks: List<TrackEntity>, startIndex: Int) {
        mediaSessionController.setQueue(tracks, startIndex)
    }

    override suspend fun play(trackId: String?) {
        mediaSessionController.play(trackId) { track -> verifyTrackAccessible(track) }
    }

    override suspend fun pause() {
        mediaSessionController.pause()
    }

    override suspend fun seekTo(positionMs: Long) {
        mediaSessionController.seekTo(positionMs)
    }

    override suspend fun skipNext() {
        mediaSessionController.skipNext()
    }

    override suspend fun skipPrevious() {
        mediaSessionController.skipPrevious()
    }

    override suspend fun setRepeatMode(mode: RepeatMode) {
        mediaSessionController.setRepeatMode(mode)
    }

    override suspend fun setShuffleEnabled(enabled: Boolean) {
        mediaSessionController.setShuffleEnabled(enabled)
    }

    override suspend fun addToQueue(track: TrackEntity) {
        mediaSessionController.addToQueue(track)
    }

    override suspend fun removeFromQueue(index: Int) {
        mediaSessionController.removeFromQueue(index)
    }

    fun setPlaybackSpeed(speed: Float) {
        mediaSessionController.setPlaybackSpeed(speed)
    }

    fun setVolume(volume: Float) {
        mediaSessionController.setVolume(volume)
    }

    fun setSleepTimerMinutes(minutes: Int?) {
        sleepTimerJob?.cancel()
        if (minutes == null || minutes <= 0) {
            mediaSessionController.updateSleepTimerRemaining(null)
            return
        }
        val totalMs = minutes * 60_000L
        mediaSessionController.updateSleepTimerRemaining(totalMs)
        sleepTimerJob = scope.launch {
            var remaining = totalMs
            while (remaining > 0L && isActive) {
                delay(1_000L)
                remaining = (remaining - 1_000L).coerceAtLeast(0L)
                mediaSessionController.updateSleepTimerRemaining(remaining)
            }
            if (remaining <= 0L) {
                pause()
                mediaSessionController.updateSleepTimerRemaining(null)
            }
        }
    }

    fun playNextInQueue(track: TrackEntity) {
        mediaSessionController.playNextInQueue(track)
    }

    fun addToQueueEnd(track: TrackEntity) {
        mediaSessionController.addToQueue(track)
    }

    fun removeFromQueueAt(index: Int) {
        mediaSessionController.removeFromQueue(index)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        mediaSessionController.moveQueueItem(fromIndex, toIndex)
    }

    fun clearUpcomingQueue() {
        mediaSessionController.clearUpcomingQueue()
    }

    fun updateTrackMetadataInActiveQueue(updatedTrack: TrackEntity) {
        mediaSessionController.updateTrackMetadataInActiveQueue(updatedTrack)
    }

    fun dismissError() {
        mediaSessionController.dismissError()
    }

    fun reconnectSessionController() {
        mediaSessionController.connectOrReconnectAsync()
    }

    /**
     * Extracts real audio metadata (title, artist, album, duration, MIME type, embedded cover art)
     * from a user-selected `content://` URI using Android's `MediaMetadataRetriever` without copying
     * the audio file into memory.
     */
    suspend fun extractTrackFromUri(uri: Uri): TrackEntity = withContext(Dispatchers.IO) {
        var displayName = "Imported Audio"
        var mimeType = "audio/mpeg"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    displayName = cursor.getString(nameIndex) ?: displayName
                }
            }
            context.contentResolver.getType(uri)?.let { resolvedMime ->
                mimeType = resolvedMime
            }
        } catch (_: Exception) {
        }

        val fallbackTitle = displayName
            .substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .ifBlank { "Local Track" }

        var title = fallbackTitle
        var artist = "Local Artist"
        var album: String? = "Device Audio"
        var durationMs = 0L
        var extractedArtworkUri: String? = null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?.let { title = it.trim() }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }
                ?.let { artist = it.trim() }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() }
                ?.let { album = it.trim() }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.let { durationMs = it.coerceAtLeast(0L) }

            val embeddedBytes = retriever.embeddedPicture
            if (embeddedBytes != null && embeddedBytes.isNotEmpty()) {
                val artworkDir = File(context.cacheDir, "embedded_artwork").apply { mkdirs() }
                val artFile = File(artworkDir, "art_${uri.toString().hashCode()}.jpg")
                FileOutputStream(artFile).use { out ->
                    out.write(embeddedBytes)
                }
                extractedArtworkUri = Uri.fromFile(artFile).toString()
            }
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        val palette = listOf(0xFFFA2D48, 0xFF0A84FF, 0xFF7C5CFF, 0xFF30D158, 0xFF00E5FF)
        val accent = palette[(title.hashCode().and(0x7FFFFFFF)) % palette.size]

        TrackEntity(
            id = "local_${UUID.nameUUIDFromBytes(uri.toString().toByteArray())}",
            title = title,
            artist = artist,
            album = album,
            durationMs = if (durationMs > 0L) durationMs else 210_000L,
            artworkUri = extractedArtworkUri,
            contentUri = uri.toString(),
            mimeType = mimeType,
            isFavorite = false,
            isLocalFile = true,
            isDemoTrack = false,
            addedAtEpochMs = System.currentTimeMillis(),
            isAvailable = true,
            accentColorHex = accent
        )
    }

    suspend fun clearCachedArtwork(): Int = withContext(Dispatchers.IO) {
        val artworkDir = File(context.cacheDir, "embedded_artwork")
        if (!artworkDir.exists()) return@withContext 0
        val files = artworkDir.listFiles() ?: return@withContext 0
        var count = 0
        for (file in files) {
            if (file.delete()) count++
        }
        count
    }

    fun verifyTrackAccessible(track: TrackEntity): Boolean {
        if (!track.isLocalFile) {
            return !contentBlockingService.shouldBlockRequest(track.contentUri)
        }
        return try {
            val uri = Uri.parse(track.contentUri)
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun release() {
        sleepTimerJob?.cancel()
        mediaSessionController.release()
    }
}
