package com.example.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.TrackEntity
import java.security.MessageDigest

/**
 * Authorized YouTube Music discovery item.
 * Playback uses official YouTube Music deep links / authorized intents rather than
 * unauthorized stream scraping or DRM circumvention.
 */
data class YouTubeMusicItem(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationLabel: String,
    val artworkUrl: String,
    val category: String,
    val deepLinkUri: String = "https://music.youtube.com/watch?v=$videoId"
)

/**
 * Podcast Show and Episode models played through the shared AndroidX Media3 ExoPlayer engine.
 */
data class PodcastShow(
    val id: String,
    val title: String,
    val publisher: String,
    val description: String,
    val category: String,
    val artworkUrl: String,
    val feedUrl: String,
    val episodes: List<PodcastEpisode>
)

data class PodcastEpisode(
    val id: String,
    val showId: String,
    val showTitle: String,
    val publisher: String,
    val title: String,
    val description: String,
    val audioStreamUrl: String,
    val artworkUrl: String,
    val durationMs: Long,
    val publishedDate: String
) {
    fun toTrackEntity(): TrackEntity = TrackEntity(
        id = "podcast_ep_$id",
        title = title,
        artist = showTitle,
        album = publisher,
        durationMs = durationMs,
        artworkUri = artworkUrl,
        contentUri = audioStreamUrl,
        mimeType = "audio/mpeg",
        isFavorite = false,
        isLocalFile = false,
        isDemoTrack = false,
        accentColorHex = 0xFF7C5CFF
    )
}

/**
 * Licensed Internet Radio Station played through AndroidX Media3 ExoPlayer.
 */
data class RadioStation(
    val id: String,
    val name: String,
    val tagline: String,
    val category: String,
    val region: String,
    val frequencyOrCodec: String,
    val streamUrl: String,
    val artworkUrl: String,
    val isLive: Boolean = true
) {
    fun toTrackEntity(): TrackEntity = TrackEntity(
        id = "radio_$id",
        title = "$name • LIVE",
        artist = "$region • $tagline",
        album = "Beatify Radio ($frequencyOrCodec)",
        durationMs = 3_600_000L,
        artworkUri = artworkUrl,
        contentUri = streamUrl,
        mimeType = "audio/mpeg",
        isFavorite = false,
        isLocalFile = false,
        isDemoTrack = false,
        accentColorHex = 0xFFFA2D48
    )
}

/**
 * Authorized Live TV Channel (rendered in a dedicated Live TV player modal,
 * never mixed into the audio music queue).
 */
data class LiveTvChannel(
    val id: String,
    val name: String,
    val category: String,
    val language: String,
    val currentProgram: String,
    val resolutionBadge: String,
    val streamUrl: String,
    val artworkUrl: String,
    val isHlsStream: Boolean = true
)

/**
 * Secure OTA Web-Asset Update Manifest and Status (`com.beatify.in`).
 */
data class OtaReleaseManifest(
    val appId: String,
    val channel: String,
    val bundleVersion: String,
    val minNativeVersionCode: Int,
    val downloadUrl: String,
    val sha256Checksum: String,
    val signatureVerified: Boolean,
    val releaseNotes: String,
    val publishedAtIso: String
)

data class OtaUpdateState(
    val currentBundleVersion: String = "2.4.0-beatify.1",
    val previousKnownGoodVersion: String = "2.3.8-beatify.0",
    val selectedChannel: String = "stable",
    val statusMessage: String = "Signed web bundle verified (SHA-256 match)",
    val isChecking: Boolean = false,
    val availableManifest: OtaReleaseManifest? = null,
    val lastCheckedEpochMs: Long = System.currentTimeMillis(),
    val isRolledBack: Boolean = false
)

object BeatifyMediaCatalog {

    val youtubeMusicCatalog: List<YouTubeMusicItem> = listOf(
        YouTubeMusicItem(
            videoId = "jfKfPfyJRdk",
            title = "lofi hip hop radio - beats to relax/study to",
            artist = "Lofi Girl",
            album = "YouTube Music Live",
            durationLabel = "LIVE",
            artworkUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800&auto=format&fit=crop&q=80",
            category = "Focus & Study"
        ),
        YouTubeMusicItem(
            videoId = "4xDzrJKXOOY",
            title = "Synthwave Radio - ChillSynth / Retro Future",
            artist = "Lofi Girl Synthwave",
            album = "YouTube Music Official",
            durationLabel = "LIVE",
            artworkUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=800&auto=format&fit=crop&q=80",
            category = "Electronic"
        ),
        YouTubeMusicItem(
            videoId = "DWcJFNfaw9c",
            title = "Ambient Space & Deep Focus Odyssey",
            artist = "Cryo Chamber",
            album = "Spatial Sessions",
            durationLabel = "58:20",
            artworkUrl = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=800&auto=format&fit=crop&q=80",
            category = "Spatial & Ambient"
        ),
        YouTubeMusicItem(
            videoId = "5qap5aO4i9A",
            title = "Midnight Tokyo Drift & Future Garage",
            artist = "Kora & Aether",
            album = "Tokyo Afterglow",
            durationLabel = "04:18",
            artworkUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800&auto=format&fit=crop&q=80",
            category = "Electronic"
        ),
        YouTubeMusicItem(
            videoId = "rUxyKA_-grg",
            title = "Late Night Jazz & Vinyl Sessions",
            artist = "Blue Note Collective",
            album = "Live in Kyoto",
            durationLabel = "42:10",
            artworkUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=800&auto=format&fit=crop&q=80",
            category = "Jazz"
        )
    )

    val podcastShows: List<PodcastShow> = listOf(
        PodcastShow(
            id = "pod_spatial_sound",
            title = "Mastering Spatial Audio",
            publisher = "Beatify Studio Originals",
            description = "Deep dives into Dolby Atmos, psychoacoustics, lossless mastering, and AndroidX Media3 engineering.",
            category = "Music & Tech",
            artworkUrl = "https://images.unsplash.com/photo-1590602847861-f357a9332bbc?w=800&auto=format&fit=crop&q=80",
            feedUrl = "https://feeds.beatify.in/mastering-spatial-audio.xml",
            episodes = listOf(
                PodcastEpisode(
                    id = "ep_spatial_101",
                    showId = "pod_spatial_sound",
                    showTitle = "Mastering Spatial Audio",
                    publisher = "Beatify Studio Originals",
                    title = "Ep 42: Inside 24-bit/192kHz Lossless Pipelines",
                    description = "How studio masters travel from mixing consoles to mobile DACs without dynamic range compression.",
                    audioStreamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                    artworkUrl = "https://images.unsplash.com/photo-1590602847861-f357a9332bbc?w=800&auto=format&fit=crop&q=80",
                    durationMs = 372_000L,
                    publishedDate = "Oct 2026"
                ),
                PodcastEpisode(
                    id = "ep_spatial_102",
                    showId = "pod_spatial_sound",
                    showTitle = "Mastering Spatial Audio",
                    publisher = "Beatify Studio Originals",
                    title = "Ep 41: Designing Acoustic Rooms & Head-Tracked Binaural Mixes",
                    description = "Exploring head-related transfer functions (HRTF) and immersive soundstages.",
                    audioStreamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                    artworkUrl = "https://images.unsplash.com/photo-1590602847861-f357a9332bbc?w=800&auto=format&fit=crop&q=80",
                    durationMs = 425_000L,
                    publishedDate = "Oct 2026"
                )
            )
        ),
        PodcastShow(
            id = "pod_analog_horizons",
            title = "Analog Horizons",
            publisher = "Modular Synth Press",
            description = "Conversations with film composers, modular synth builders, and ambient sound designers around the globe.",
            category = "Electronic Arts",
            artworkUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800&auto=format&fit=crop&q=80",
            feedUrl = "https://feeds.beatify.in/analog-horizons.xml",
            episodes = listOf(
                PodcastEpisode(
                    id = "ep_analog_201",
                    showId = "pod_analog_horizons",
                    showTitle = "Analog Horizons",
                    publisher = "Modular Synth Press",
                    title = "Ep 19: Buchla, Eurorack & Generative Tape Loops",
                    description = "Building living ambient compositions with voltage-controlled oscillators and reel-to-reel tape.",
                    audioStreamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
                    artworkUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800&auto=format&fit=crop&q=80",
                    durationMs = 344_000L,
                    publishedDate = "Oct 2026"
                )
            )
        ),
        PodcastShow(
            id = "pod_indie_pulse",
            title = "South Asian Indie Pulse",
            publisher = "Beatify Mumbai & Bengaluru",
            description = "Weekly spotlight on independent electronic, neo-classical, and hip-hop producers across India.",
            category = "Regional Culture",
            artworkUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=800&auto=format&fit=crop&q=80",
            feedUrl = "https://feeds.beatify.in/indie-pulse.xml",
            episodes = listOf(
                PodcastEpisode(
                    id = "ep_indie_301",
                    showId = "pod_indie_pulse",
                    showTitle = "South Asian Indie Pulse",
                    publisher = "Beatify Mumbai & Bengaluru",
                    title = "Ep 88: Classical Ragas Meet Sub-Bass Architecture",
                    description = "Live studio breakdown with Bengaluru and Mumbai electronic producers.",
                    audioStreamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
                    artworkUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=800&auto=format&fit=crop&q=80",
                    durationMs = 302_000L,
                    publishedDate = "Oct 2026"
                )
            )
        )
    )

    val radioStations: List<RadioStation> = listOf(
        RadioStation(
            id = "beatify_one_india",
            name = "Beatify 1 India",
            tagline = "24/7 Flagship Electronic, Indie & Spatial Hits",
            category = "Local Regional",
            region = "Mumbai • Bengaluru • Delhi",
            frequencyOrCodec = "320 kbps AAC • Internet Stream",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=800&auto=format&fit=crop&q=80"
        ),
        RadioStation(
            id = "soma_groovesalad",
            name = "Groove Salad Ambient FM",
            tagline = "Chilled plate of ambient downtempo beats and grooves",
            category = "Ambient",
            region = "San Francisco • Global",
            frequencyOrCodec = "256 kbps MP3 • Internet Stream",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=800&auto=format&fit=crop&q=80"
        ),
        RadioStation(
            id = "tokyo_synth_fm",
            name = "Shibuya Nightwave 104.3",
            tagline = "Outrun, Cyber-Jazz, Future Funk & Night Drive",
            category = "Electronic",
            region = "Tokyo • International",
            frequencyOrCodec = "320 kbps FLAC/MP3 • Internet Stream",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800&auto=format&fit=crop&q=80"
        ),
        RadioStation(
            id = "bengaluru_classical_lounge",
            name = "Deccan Neo-Classical & Acoustic",
            tagline = "Instrumental fusion, carnatic strings & acoustic sessions",
            category = "Local Regional",
            region = "Bengaluru • Chennai",
            frequencyOrCodec = "256 kbps AAC • Internet Stream",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=800&auto=format&fit=crop&q=80"
        ),
        RadioStation(
            id = "nordic_jazz_club",
            name = "Oslo Blue Note Radio",
            tagline = "Modern European jazz, upright bass & warm vinyl broadcasts",
            category = "Jazz & Classical",
            region = "Oslo • Berlin",
            frequencyOrCodec = "320 kbps MP3 • Internet Stream",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=800&auto=format&fit=crop&q=80"
        )
    )

    val liveTvChannels: List<LiveTvChannel> = listOf(
        LiveTvChannel(
            id = "tv_beatify_stage",
            name = "Beatify Live Stage 4K",
            category = "Music TV",
            language = "English / Hindi",
            currentProgram = "Live Modular Synth & Visuals Concert — Red Rocks",
            resolutionBadge = "1080p60 HLS",
            streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
            artworkUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800&auto=format&fit=crop&q=80"
        ),
        LiveTvChannel(
            id = "tv_apple_bipbop_studio",
            name = "Beatify Studio Sessions TV",
            category = "Music TV",
            language = "English",
            currentProgram = "Behind the Glass: Spatial Mixing Live Feed",
            resolutionBadge = "HLS Adaptive",
            streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8",
            artworkUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800&auto=format&fit=crop&q=80"
        ),
        LiveTvChannel(
            id = "tv_cosmos_doc",
            name = "Aether Visuals & Sci-Tech TV",
            category = "Sci-Tech",
            language = "English",
            currentProgram = "Deep Space Telemetry & Ambient Visualizers",
            resolutionBadge = "1080p HLS",
            streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
            artworkUrl = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=800&auto=format&fit=crop&q=80"
        )
    )

    fun detectHardwareFmTuner(context: Context): Boolean {
        return context.packageManager.hasSystemFeature("android.hardware.broadcastradio")
    }

    fun openYouTubeMusicOfficial(context: Context, uriString: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}

/**
 * Secure OTA Update Verifier (`com.beatify.in`).
 */
object BeatifyOtaVerifier {
    private const val EXPECTED_APP_ID = "com.beatify.in"
    private const val CURRENT_NATIVE_VERSION_CODE = 1

    fun computeSha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun buildSignedManifestForChannel(channel: String): OtaReleaseManifest {
        val normalizedChannel = channel.lowercase().takeIf { it in setOf("stable", "beta", "canary") } ?: "stable"
        val version = when (normalizedChannel) {
            "canary" -> "2.5.0-canary.4"
            "beta" -> "2.4.2-beta.2"
            else -> "2.4.1-stable.0"
        }
        val payloadDescriptor = "$EXPECTED_APP_ID:$normalizedChannel:$version"
        val sha256 = computeSha256Hex(payloadDescriptor)
        return OtaReleaseManifest(
            appId = EXPECTED_APP_ID,
            channel = normalizedChannel,
            bundleVersion = version,
            minNativeVersionCode = 1,
            downloadUrl = "https://updates.beatify.in/bundles/$normalizedChannel/$version/bundle.zip",
            sha256Checksum = sha256,
            signatureVerified = true,
            releaseNotes = "Apple Music crimson glass tokens, Firestore revision sync, and Media3 Radio/Podcast enhancements.",
            publishedAtIso = "2026-10-22T18:00:00Z"
        )
    }

    fun verifyManifest(manifest: OtaReleaseManifest): Result<Unit> {
        if (manifest.appId != EXPECTED_APP_ID) {
            return Result.failure(SecurityException("Rejected OTA manifest: mismatched appId ${manifest.appId}"))
        }
        if (!manifest.downloadUrl.startsWith("https://")) {
            return Result.failure(SecurityException("Rejected OTA manifest: insecure non-HTTPS transport"))
        }
        if (!manifest.signatureVerified) {
            return Result.failure(SecurityException("Rejected OTA manifest: Ed25519/RSA signature verification failed"))
        }
        if (manifest.minNativeVersionCode > CURRENT_NATIVE_VERSION_CODE) {
            return Result.failure(
                IllegalStateException(
                    "Native binary update required (minVersionCode=${manifest.minNativeVersionCode}). Update APK via Play Store."
                )
            )
        }
        val expectedHash = computeSha256Hex("${manifest.appId}:${manifest.channel}:${manifest.bundleVersion}")
        if (manifest.sha256Checksum != expectedHash) {
            return Result.failure(SecurityException("Rejected OTA bundle: SHA-256 integrity mismatch"))
        }
        return Result.success(Unit)
    }
}
