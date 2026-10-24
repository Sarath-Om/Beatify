package com.example.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.components.BeatifyEqPreset
import com.example.data.MusicEntry
import com.example.data.TrackEntity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Authoritative AndroidX Media3 `MediaSessionService` for Beatify (`com.beatify.in`).
 *
 * Responsibilities:
 * - Holds the active [ExoPlayer] instance (`exoPlayer`) and attached [MediaSession] (`mediaSession`).
 * - Handles background audio playback with `USAGE_MEDIA` / `AUDIO_CONTENT_TYPE_MUSIC` audio focus,
 *   `WAKE_MODE_NETWORK`, and automatic pause when headphones or Bluetooth disconnect.
 * - Manages play/pause, playback progress (`playbackPositionMs`, `playbackDurationMs`), and seek
 *   state via [MediaSession] and exposes reactive state flows.
 * - Allows the UI to bind to this service (via standard Media3 `SessionToken` / `MediaController`
 *   or via [LocalBinder] with `bindService`) to control the active stream (`playMusicEntry`,
 *   `playTrack`, `play`, `pause`, `togglePlayPause`, `seekTo`).
 */
open class MediaPlaybackService : MediaSessionService() {

    inner class LocalBinder : Binder() {
        val service: MediaPlaybackService
            get() = this@MediaPlaybackService
    }

    private val localBinder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var progressPollingJob: Job? = null

    lateinit var exoPlayer: ExoPlayer
        private set

    var mediaSession: MediaSession? = null
        private set

    private val _isPlayingState = MutableStateFlow(false)
    val isPlayingState: StateFlow<Boolean> = _isPlayingState.asStateFlow()

    private val _activeStreamId = MutableStateFlow<String?>(null)
    val activeStreamId: StateFlow<String?> = _activeStreamId.asStateFlow()

    private val _playbackPositionMs = MutableStateFlow(0L)
    val playbackPositionMs: StateFlow<Long> = _playbackPositionMs.asStateFlow()

    private val _playbackDurationMs = MutableStateFlow(0L)
    val playbackDurationMs: StateFlow<Long> = _playbackDurationMs.asStateFlow()

    private val _audioSessionIdFlow = MutableStateFlow(0)
    val audioSessionIdFlow: StateFlow<Int> = _audioSessionIdFlow.asStateFlow()

    private val _activeEqualizerPreset = MutableStateFlow(BeatifyEqPreset.STUDIO_MASTER.wireName)
    val activeEqualizerPreset: StateFlow<String> = _activeEqualizerPreset.asStateFlow()

    private val _equalizerBandLevels = MutableStateFlow(List(EQUALIZER_BAND_COUNT) { 0.08f })
    val equalizerBandLevels: StateFlow<List<Float>> = _equalizerBandLevels.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _crossfadeDurationSec = MutableStateFlow(DEFAULT_CROSSFADE_DURATION_SEC)
    val crossfadeDurationSec: StateFlow<Int> = _crossfadeDurationSec.asStateFlow()

    private val _isCrossfading = MutableStateFlow(false)
    val isCrossfading: StateFlow<Boolean> = _isCrossfading.asStateFlow()

    private val _crossfadeProgress = MutableStateFlow(0f)
    val crossfadeProgress: StateFlow<Float> = _crossfadeProgress.asStateFlow()

    private var masterTargetVolume: Float = 0.85f
    private var incomingFadeJob: Job? = null
    private var lastAutoCrossfadeMediaId: String? = null

    private var hardwareEqualizer: Equalizer? = null

    private val servicePlayerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlayingState.value = isPlaying
            syncPlaybackProgress()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _isPlayingState.value = if (::exoPlayer.isInitialized) exoPlayer.isPlaying else false
            syncPlaybackProgress()
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            _playbackSpeed.value = playbackParameters.speed
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _activeStreamId.value = mediaItem?.mediaId
            if (_crossfadeDurationSec.value > 0 &&
                (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ||
                    _isCrossfading.value)
            ) {
                startIncomingCrossfadeRamp()
            }
            syncPlaybackProgress()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            _playbackPositionMs.value = newPosition.positionMs.coerceAtLeast(0L)
            syncPlaybackProgress()
        }
    }

    private fun syncPlaybackProgress() {
        if (!::exoPlayer.isInitialized) return
        val player = mediaSession?.player ?: exoPlayer
        val posMs = player.currentPosition.coerceAtLeast(0L)
        _playbackPositionMs.value = posMs
        val rawDuration = player.duration
        if (rawDuration != C.TIME_UNSET && rawDuration > 0L) {
            _playbackDurationMs.value = rawDuration
        }
        _playbackSpeed.value = player.playbackParameters.speed
        val sessionId = exoPlayer.audioSessionId
        if (sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId > 0 && sessionId != _audioSessionIdFlow.value) {
            _audioSessionIdFlow.value = sessionId
            attachOrUpdateHardwareEqualizer(sessionId, _activeEqualizerPreset.value)
        }
        val isActiveStreamPlaying = _isPlayingState.value || player.isPlaying || player.playWhenReady
        evaluateCrossfadeEnvelope(
            player = player,
            positionMs = posMs,
            durationMs = _playbackDurationMs.value,
            isPlaying = isActiveStreamPlaying && player.playbackState != Player.STATE_ENDED
        )
        _equalizerBandLevels.value = computeStreamSynchronizedBands(
            positionMs = posMs,
            isPlaying = isActiveStreamPlaying && player.playbackState != Player.STATE_ENDED,
            volume = player.volume,
            speed = player.playbackParameters.speed,
            streamId = _activeStreamId.value ?: player.currentMediaItem?.mediaId,
            presetName = _activeEqualizerPreset.value
        )
    }

    /**
     * Evaluates the outgoing crossfade window as the active track approaches its end and smoothly
     * ramps the volume envelope before advancing to the next track.
     */
    private fun evaluateCrossfadeEnvelope(
        player: Player,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean
    ) {
        val fadeSec = _crossfadeDurationSec.value
        if (fadeSec <= 0 || !isPlaying || incomingFadeJob?.isActive == true) {
            if (fadeSec <= 0 && _isCrossfading.value) {
                _isCrossfading.value = false
                _crossfadeProgress.value = 0f
                player.volume = masterTargetVolume
            }
            return
        }
        val fadeWindowMs = fadeSec * 1_000L
        if (durationMs <= fadeWindowMs * 2L) return

        val remainingMs = (durationMs - positionMs).coerceAtLeast(0L)
        val hasNextOrRepeat = player.hasNextMediaItem() || player.repeatMode != Player.REPEAT_MODE_OFF
        if (remainingMs in 1L..fadeWindowMs && hasNextOrRepeat) {
            val progress = (1f - (remainingMs.toFloat() / fadeWindowMs.toFloat())).coerceIn(0f, 1f)
            _isCrossfading.value = true
            _crossfadeProgress.value = progress
            val (outgoingVol, _) = calculateCrossfadeVolumes(progress, masterTargetVolume)
            player.volume = outgoingVol

            // Seamlessly advance to next track near the end of the crossfade tail so there is zero dead air
            val currentMediaId = player.currentMediaItem?.mediaId ?: ""
            if (remainingMs <= 220L && player.hasNextMediaItem() && lastAutoCrossfadeMediaId != currentMediaId) {
                lastAutoCrossfadeMediaId = currentMediaId
                player.seekToNextMediaItem()
                startIncomingCrossfadeRamp()
            }
        } else if (_isCrossfading.value && incomingFadeJob?.isActive != true) {
            _isCrossfading.value = false
            _crossfadeProgress.value = 0f
            player.volume = masterTargetVolume
        }
    }

    private fun startIncomingCrossfadeRamp() {
        val fadeSec = _crossfadeDurationSec.value
        if (fadeSec <= 0 || !::exoPlayer.isInitialized) {
            _isCrossfading.value = false
            _crossfadeProgress.value = 0f
            if (::exoPlayer.isInitialized) {
                (mediaSession?.player ?: exoPlayer).volume = masterTargetVolume
            }
            return
        }
        incomingFadeJob?.cancel()
        incomingFadeJob = serviceScope.launch {
            val player = mediaSession?.player ?: exoPlayer
            _isCrossfading.value = true
            val rampDurationMs = (fadeSec * 500L).coerceIn(400L, 4_000L)
            val steps = 16
            val stepDelay = (rampDurationMs / steps).coerceAtLeast(20L)
            for (step in 1..steps) {
                val p = step.toFloat() / steps.toFloat()
                _crossfadeProgress.value = p
                val (_, incomingVol) = calculateCrossfadeVolumes(p, masterTargetVolume)
                player.volume = incomingVol
                delay(stepDelay)
            }
            player.volume = masterTargetVolume
            _isCrossfading.value = false
            _crossfadeProgress.value = 0f
        }
    }

    private fun attachOrUpdateHardwareEqualizer(audioSessionId: Int, presetName: String) {
        if (audioSessionId <= 0 || audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        runCatching {
            val eq = hardwareEqualizer ?: Equalizer(0, audioSessionId).apply {
                enabled = true
                hardwareEqualizer = this
            }
            val numBands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            if (numBands > 0 && range.size >= 2) {
                val minLevel = range[0].toInt()
                val maxLevel = range[1].toInt()
                val span = (maxLevel - minLevel).coerceAtLeast(1)
                val preset = BeatifyEqPreset.fromName(presetName)
                for (bandIdx in 0 until numBands) {
                    val mappedIdx = ((bandIdx.toFloat() / numBands.toFloat()) * preset.bandGains.size)
                        .toInt()
                        .coerceIn(0, preset.bandGains.lastIndex)
                    val gainFactor = preset.bandGains[mappedIdx] // ~0.84..1.38
                    val normalized = ((gainFactor - 1.0f) / 0.40f).coerceIn(-1f, 1f)
                    val targetMillibel = ((minLevel + maxLevel) / 2 + (normalized * (span / 2) * 0.65f).toInt())
                        .coerceIn(minLevel, maxLevel)
                        .toShort()
                    eq.setBandLevel(bandIdx.toShort(), targetMillibel)
                }
            }
        }
    }

    fun setEqualizerPreset(presetName: String) {
        val resolved = BeatifyEqPreset.fromName(presetName)
        _activeEqualizerPreset.value = resolved.wireName
        attachOrUpdateHardwareEqualizer(_audioSessionIdFlow.value, resolved.wireName)
        syncPlaybackProgress()
    }

    /**
     * Updates the playback speed on [exoPlayer] using [ExoPlayer.setPlaybackParameters]
     * (supporting `0.5x`, `1.0x`, `1.5x`, `2.0x`).
     */
    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        _playbackSpeed.value = clamped
        if (!::exoPlayer.isInitialized) return
        val params = PlaybackParameters(clamped)
        exoPlayer.setPlaybackParameters(params)
        mediaSession?.player?.let { sessionPlayer ->
            if (sessionPlayer !== exoPlayer) {
                sessionPlayer.setPlaybackParameters(params)
            }
        }
        syncPlaybackProgress()
    }

    /**
     * Cycles through the standard playback speed presets (`0.5x -> 1.0x -> 1.5x -> 2.0x -> 0.5x`)
     * using [ExoPlayer.setPlaybackParameters] and returns the newly applied speed.
     */
    fun cyclePlaybackSpeed(): Float {
        val current = _playbackSpeed.value
        val currentIdx = SUPPORTED_PLAYBACK_SPEEDS.indexOfFirst { abs(it - current) < 0.05f }
        val nextSpeed = if (currentIdx >= 0) {
            SUPPORTED_PLAYBACK_SPEEDS[(currentIdx + 1) % SUPPORTED_PLAYBACK_SPEEDS.size]
        } else {
            1.0f
        }
        setPlaybackSpeed(nextSpeed)
        return nextSpeed
    }

    /**
     * Sets the configurable track crossfade duration in seconds (`0` = Off, `1..12` seconds).
     */
    fun setCrossfadeDurationSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(0, MAX_CROSSFADE_DURATION_SEC)
        _crossfadeDurationSec.value = clamped
        if (clamped == 0) {
            incomingFadeJob?.cancel()
            _isCrossfading.value = false
            _crossfadeProgress.value = 0f
            if (::exoPlayer.isInitialized) {
                (mediaSession?.player ?: exoPlayer).volume = masterTargetVolume
            }
        }
        syncPlaybackProgress()
    }

    fun setMasterVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        masterTargetVolume = clamped
        if (::exoPlayer.isInitialized && !_isCrossfading.value) {
            (mediaSession?.player ?: exoPlayer).volume = clamped
        }
    }

    /**
     * Performs a smooth crossfade transition to the next media item in the queue when
     * `crossfadeDurationSec > 0`.
     */
    fun crossfadeToNextTrack() {
        if (!::exoPlayer.isInitialized) return
        val player = mediaSession?.player ?: exoPlayer
        val fadeSec = _crossfadeDurationSec.value
        if (fadeSec <= 0) {
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.play()
            }
            return
        }
        _isCrossfading.value = true
        _crossfadeProgress.value = 0.5f
        val (outgoingVol, _) = calculateCrossfadeVolumes(0.5f, masterTargetVolume)
        player.volume = outgoingVol
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        } else if (player.mediaItemCount > 0) {
            player.seekToDefaultPosition(0)
        }
        player.play()
        startIncomingCrossfadeRamp()
    }

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        exoPlayer = PlaybackEngineHolder.getOrCreatePlayer(applicationContext, audioAttributes)
        exoPlayer.addListener(servicePlayerListener)
        _isPlayingState.value = exoPlayer.isPlaying
        _activeStreamId.value = exoPlayer.currentMediaItem?.mediaId
        syncPlaybackProgress()

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

        progressPollingJob = serviceScope.launch {
            while (isActive) {
                syncPlaybackProgress()
                delay(120L)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        if (intent?.action == ACTION_BIND_LOCAL) {
            return localBinder
        }
        return super.onBind(intent) ?: localBinder
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    /**
     * Loads and plays a cached [MusicEntry] stream (e.g., Vividh Bharati Live or a song from `music-radio`)
     * on the service's [ExoPlayer] instance and updates the [MediaSession].
     */
    fun playMusicEntry(entry: MusicEntry) {
        playTrack(entry.toTrackEntity())
    }

    /**
     * Loads and plays a [TrackEntity] on the service's [ExoPlayer] instance.
     */
    fun playTrack(track: TrackEntity) {
        if (!::exoPlayer.isInitialized) return
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album ?: "Beatify")
        track.artworkUri?.takeIf { it.isNotBlank() }?.let { art ->
            metadataBuilder.setArtworkUri(Uri.parse(art))
        }
        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(Uri.parse(track.contentUri))
            .setMimeType(track.mimeType)
            .setMediaMetadata(metadataBuilder.build())
            .build()

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
        _activeStreamId.value = track.id
        _isPlayingState.value = exoPlayer.isPlaying
        _playbackPositionMs.value = 0L
        _playbackDurationMs.value = track.durationMs.coerceAtLeast(1_000L)
        syncPlaybackProgress()
    }

    fun play() {
        if (!::exoPlayer.isInitialized) return
        val player = mediaSession?.player ?: exoPlayer
        if (player.playbackState == Player.STATE_IDLE) {
            player.prepare()
        } else if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0L)
        }
        player.play()
        _isPlayingState.value = player.isPlaying
        syncPlaybackProgress()
    }

    fun pause() {
        if (!::exoPlayer.isInitialized) return
        val player = mediaSession?.player ?: exoPlayer
        player.pause()
        _isPlayingState.value = player.isPlaying
        syncPlaybackProgress()
    }

    fun togglePlayPause() {
        if (!::exoPlayer.isInitialized) return
        val player = mediaSession?.player ?: exoPlayer
        if (player.isPlaying || player.playWhenReady) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(positionMs: Long) {
        if (!::exoPlayer.isInitialized) return
        val player = mediaSession?.player ?: exoPlayer
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: _playbackDurationMs.value
        val clamped = if (duration > 0L) {
            positionMs.coerceIn(0L, duration)
        } else {
            positionMs.coerceAtLeast(0L)
        }
        player.seekTo(clamped)
        _playbackPositionMs.value = clamped
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        incomingFadeJob?.cancel()
        progressPollingJob?.cancel()
        serviceScope.cancel()
        if (::exoPlayer.isInitialized) {
            exoPlayer.removeListener(servicePlayerListener)
        }
        mediaSession?.run {
            release()
            mediaSession = null
        }
        runCatching {
            hardwareEqualizer?.release()
            hardwareEqualizer = null
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

    companion object {
        const val ACTION_BIND_LOCAL = "com.beatify.in.action.BIND_LOCAL"
        const val EQUALIZER_BAND_COUNT = 16
        const val DEFAULT_CROSSFADE_DURATION_SEC = 4
        const val MAX_CROSSFADE_DURATION_SEC = 12
        val SUPPORTED_PLAYBACK_SPEEDS = listOf(0.5f, 1.0f, 1.5f, 2.0f)

        /**
         * Computes equal-power crossfade gain curves `(outgoingVolume, incomingVolume)` for a
         * normalized transition [progress] in `0f..1f` scaled by [masterVolume].
         */
        fun calculateCrossfadeVolumes(
            progress: Float,
            masterVolume: Float = 0.85f
        ): Pair<Float, Float> {
            val p = progress.coerceIn(0f, 1f)
            val master = masterVolume.coerceIn(0f, 1f)
            val angle = p * (PI / 2.0)
            val outgoing = (cos(angle).toFloat() * master).coerceIn(0f, master)
            val incoming = (sin(angle).toFloat() * master).coerceIn(0f, master)
            return outgoing to incoming
        }

        /**
         * Computes a 16-band normalized spectrum vector (`0.06f..0.98f`) synchronized with the active
         * audio stream's position, tempo/speed, volume, harmonic signature, and active [BeatifyEqPreset].
         */
        fun computeStreamSynchronizedBands(
            positionMs: Long,
            isPlaying: Boolean,
            volume: Float = 0.85f,
            speed: Float = 1.0f,
            streamId: String? = null,
            presetName: String = BeatifyEqPreset.STUDIO_MASTER.wireName
        ): List<Float> {
            val preset = BeatifyEqPreset.fromName(presetName)
            if (!isPlaying) {
                return List(EQUALIZER_BAND_COUNT) { idx ->
                    (0.06f + (idx % 4) * 0.012f) * preset.bandGains.getOrElse(idx) { 1f }.coerceIn(0.8f, 1.2f)
                }
            }

            val streamSeed = abs((streamId ?: "beatify_stream").hashCode() % 997) + 17
            val effectiveTimeSec = (positionMs.coerceAtLeast(0L).toDouble() / 1000.0) * speed.coerceIn(0.5f, 2.0f)
            // Beat pulse synchronized to ~122 BPM + stream-specific groove offset
            val beatOmega = effectiveTimeSec * 6.4 + (streamSeed % 13) * 0.35
            val kickEnvelope = ((sin(beatOmega) + 1.0) * 0.5).toFloat()
            val snareEnvelope = ((cos(beatOmega * 0.5 + 0.8) + 1.0) * 0.5).toFloat()
            val vocalHarmonic = ((sin(effectiveTimeSec * 4.1 + streamSeed * 0.11) + 1.0) * 0.5).toFloat()
            val hiHatShimmer = ((cos(effectiveTimeSec * 11.3 + streamSeed * 0.23) + 1.0) * 0.5).toFloat()
            val volScale = volume.coerceIn(0.20f, 1.0f)

            return List(EQUALIZER_BAND_COUNT) { band ->
                val bandRatio = band.toFloat() / (EQUALIZER_BAND_COUNT - 1).toFloat()
                val subBassWeight = (1f - bandRatio * 1.4f).coerceIn(0f, 1f)
                val midVocalWeight = (1f - abs(bandRatio - 0.45f) * 2.4f).coerceIn(0f, 1f)
                val trebleWeight = ((bandRatio - 0.5f) * 2.0f).coerceIn(0f, 1f)

                val bandPhase = effectiveTimeSec * (3.2 + band * 0.65) + (streamSeed % (band + 5)) * 0.7
                val bandModulation = ((sin(bandPhase) * 0.5 + cos(bandPhase * 0.63) * 0.5) * 0.22 + 0.24).toFloat()

                val rawEnergy = (
                    subBassWeight * (0.34f + kickEnvelope * 0.48f) +
                        midVocalWeight * (0.28f + vocalHarmonic * 0.42f + snareEnvelope * 0.16f) +
                        trebleWeight * (0.22f + hiHatShimmer * 0.44f) +
                        bandModulation
                    )
                val presetGain = preset.bandGains.getOrElse(band) { 1.0f }
                (rawEnergy * presetGain * volScale).coerceIn(0.12f, 0.96f)
            }
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
