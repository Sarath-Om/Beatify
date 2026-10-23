package com.example.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Authoritative AndroidX Media3 `MediaSessionService` for Beatify (`com.beatify.in`).
 *
 * Responsibilities:
 * - Creates and owns the single authoritative [ExoPlayer] instance and attached [MediaSession].
 * - Publishes the standard Media3 playback notification with album artwork, seek bar, and
 *   Play/Pause/Previous/Next transport controls.
 * - Configures audio focus (`USAGE_MEDIA`, `AUDIO_CONTENT_TYPE_MUSIC`), network wake lock
 *   (`WAKE_MODE_NETWORK`), and noisy-audio handling (pausing when headphones/Bluetooth disconnect).
 * - Continues background audio playback across screen lock, app switching, and Activity recreation.
 */
open class MediaPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val exoPlayer = PlaybackEngineHolder.getOrCreatePlayer(applicationContext, audioAttributes)

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(MediaPlaybackSessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    /**
     * Custom [MediaSession.Callback] ensuring media items added via [androidx.media3.session.MediaController]
     * retain their resolved stream URIs and metadata for background playback.
     */
    private inner class MediaPlaybackSessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolvedItems = mediaItems.map { item ->
                if (item.localConfiguration != null) {
                    item
                } else {
                    val requestUri = item.requestMetadata.mediaUri
                    if (requestUri != null) {
                        item.buildUpon().setUri(requestUri).build()
                    } else {
                        item
                    }
                }
            }.toMutableList()
            return Futures.immediateFuture(resolvedItems)
        }
    }
}

/**
 * Process-level holder ensuring [MediaPlaybackService] owns the single authoritative [ExoPlayer] instance.
 * `MediaSessionController` never creates a second ExoPlayer instance.
 */
object PlaybackEngineHolder {
    @Volatile
    private var sharedPlayer: ExoPlayer? = null

    fun getOrCreatePlayer(
        context: Context,
        audioAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
    ): ExoPlayer {
        return sharedPlayer ?: synchronized(this) {
            sharedPlayer ?: ExoPlayer.Builder(context.applicationContext)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build()
                .also { sharedPlayer = it }
        }
    }
}
