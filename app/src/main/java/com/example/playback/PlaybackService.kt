package com.example.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity

/**
 * Authoritative AndroidX Media3 `MediaSessionService` for Beatify (`com.beatify.in`, Section 3).
 *
 * Responsibilities:
 * - Creates and owns the single ExoPlayer instance and attached MediaSession.
 * - Publishes the standard Media3 playback notification with album artwork, seek bar, and
 *   Play/Pause/Previous/Next actions.
 * - Handles audio focus (`USAGE_MEDIA`, `AUDIO_CONTENT_TYPE_MUSIC`) and noisy-audio events
 *   (automatically pausing when headphones or Bluetooth speakers disconnect).
 * - Continues audio playback across screen lock, app switching, Activity recreation, and WebView reload.
 */
class PlaybackService : MediaSessionService() {

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
}

/**
 * Process-level holder ensuring `PlaybackService` owns the single authoritative ExoPlayer instance.
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
                .build()
                .also { sharedPlayer = it }
        }
    }
}
