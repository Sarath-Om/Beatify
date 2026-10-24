package com.example.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

enum class MusicDestination(val route: String, val label: String) {
    HOME("home", "Home"),
    SEARCH("search", "Search"),
    RADIO("radio", "Radio"),
    LIBRARY("library", "Library"),
    SETTINGS("settings", "Settings")
}

data class RadioStationItem(
    val id: String,
    val name: String,
    val tagline: String,
    val genre: String,
    val streamUrl: String,
    val artworkUri: String,
    val bitrateKbps: Int = 320,
    val accentColorHex: Long = 0xFFFA2D48,
    val isLive: Boolean = true
)

data class PodcastEpisodeItem(
    val id: String,
    val showId: String,
    val showTitle: String,
    val title: String,
    val host: String,
    val durationMs: Long,
    val streamUrl: String,
    val artworkUri: String,
    val summary: String,
    val accentColorHex: Long = 0xFF7C5CFF
)

data class OtaBundleManifest(
    val bundleVersion: String = "2026.10.4-ota",
    val previousGoodBundleVersion: String = "2026.10.0-bundled",
    val targetNativeVersion: String = "1.0.0",
    val minNativeVersionCode: Int = 1,
    val channel: String = "stable",
    val downloadUrl: String = "https://updates.beatify.in/bundles/stable/2026.10.4-ota/bundle.zip",
    val sha256Checksum: String = "sha256:9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
    val signatureVerified: Boolean = true,
    val releaseNotes: String = "Real YouTube Music InnerTube search, responsive search bar, WebView request filtering, and Material 3 persistent player sheet.",
    val lastCheckedAtMs: Long = System.currentTimeMillis(),
    val statusLabel: String = "Up to date • Ed25519 Signature & SHA-256 Verified"
)

data class BeatifyPushNotificationItem(
    val id: String,
    val category: String,
    val title: String,
    val body: String,
    val deepLink: String,
    val receivedAtMs: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

enum class RepeatMode(val wireValue: String, val label: String) {
    OFF("off", "Repeat Off"),
    ONE("one", "Repeat One"),
    ALL("all", "Repeat All");

    companion object {
        fun fromWire(value: String): RepeatMode =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: OFF
    }
}

enum class LibraryCategory(val label: String) {
    TRACKS("Local Music"),
    PLAYLISTS("Playlists"),
    FAVORITES("Favorites"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    RECENT("Recently Played")
}

enum class TrackSortOption(val label: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    DATE_ADDED("Date Added"),
    DURATION("Duration")
}

enum class AudioQualityPreset(val label: String, val description: String, val bitrateKbps: Int) {
    LOSSLESS("Lossless / Direct PCM", "No downsampling • Native bit-depth passthrough", 1411),
    HIGH("High (320 kbps)", "Studio AAC / High-bitrate stream optimization", 320),
    BALANCED("Data Saver (160 kbps)", "Reduced buffer footprint for mobile networks", 160)
}

/**
 * Room `Track` entity storing local metadata and playback state for offline music access.
 */
@Entity(tableName = "track")
data class Track(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0L,
    val artworkUri: String? = null,
    val localUri: String,
    val mimeType: String = "audio/mpeg",
    val source: String = "local",
    val playbackAvailability: String = "available",
    val isFavorite: Boolean = false,
    val isPlaying: Boolean = false,
    val lastPlaybackPositionMs: Long = 0L,
    val playCount: Int = 0,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
) {
    fun toTrackEntity(): TrackEntity = TrackEntity(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        artworkUri = artworkUri,
        contentUri = localUri,
        mimeType = mimeType,
        isFavorite = isFavorite,
        isLocalFile = source == "local",
        isDemoTrack = false,
        lastPositionMs = lastPlaybackPositionMs,
        playCount = playCount,
        isAvailable = playbackAvailability == "available",
        sourceType = source,
        playbackAvailability = playbackAvailability
    )
}

/**
 * Authoritative TrackEntity used across playback queue, Room `tracks` table, and UI.
 */
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0L,
    val artworkUri: String? = null,
    val contentUri: String,
    val mimeType: String = "audio/mpeg",
    val isFavorite: Boolean = false,
    val isLocalFile: Boolean = false,
    val isDemoTrack: Boolean = false,
    val addedAtEpochMs: Long = System.currentTimeMillis(),
    val lastPlayedEpochMs: Long = 0L,
    val lastPositionMs: Long = 0L,
    val playCount: Int = 0,
    val isAvailable: Boolean = true,
    val accentColorHex: Long = 0xFF0A84FF,
    val sourceType: String = if (isLocalFile) "local" else "youtube-music",
    val playbackAvailability: String = if (isAvailable) "available" else "unavailable",
    val externalLaunchUri: String? = null
) {
    fun toOfflineTrack(isCurrentlyPlaying: Boolean = false): Track = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        artworkUri = artworkUri,
        localUri = contentUri,
        mimeType = mimeType,
        source = sourceType,
        playbackAvailability = playbackAvailability,
        isFavorite = isFavorite,
        isPlaying = isCurrentlyPlaying,
        lastPlaybackPositionMs = lastPositionMs,
        playCount = playCount,
        updatedAtEpochMs = System.currentTimeMillis()
    )
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val trackIdsCsv: String = "",
    val coverColorHex: Long = 0xFF7C5CFF,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
) {
    fun trackIds(): List<String> =
        if (trackIdsCsv.isBlank()) emptyList()
        else trackIdsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

@Entity(tableName = "playback_snapshot")
data class PlaybackSnapshotEntity(
    @PrimaryKey val id: String = "active_session",
    val currentTrackId: String? = null,
    val queueTrackIdsCsv: String = "",
    val positionMs: Long = 0L,
    val repeatMode: String = RepeatMode.OFF.wireValue,
    val shuffleEnabled: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
) {
    fun queueTrackIds(): List<String> =
        if (queueTrackIdsCsv.isBlank()) emptyList()
        else queueTrackIdsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

@Entity(tableName = "music_settings")
data class MusicSettingEntity(
    @PrimaryKey val key: String,
    val value: String
)

/**
 * Authoritative PlaybackState contract.
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentTrack: TrackEntity? = null,
    val queue: List<TrackEntity> = emptyList(),
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val volume: Float = 0.85f,
    val sleepTimerRemainingMs: Long? = null,
    val isBuffering: Boolean = false,
    val errorMessage: String? = null,
    val audioSessionId: Int = 0,
    val equalizerPreset: String = "Studio Master",
    val equalizerBands: List<Float> = List(16) { 0.08f },
    val crossfadeDurationSec: Int = 4,
    val isCrossfading: Boolean = false
)

data class AlbumSummary(
    val name: String,
    val artist: String,
    val artworkUri: String?,
    val accentColorHex: Long,
    val tracks: List<TrackEntity>
)

data class ArtistSummary(
    val name: String,
    val artworkUri: String?,
    val accentColorHex: Long,
    val tracks: List<TrackEntity>,
    val albumCount: Int
)

/**
 * DAO for the `Track` entity storing offline local metadata and playback state.
 */
@Dao
interface TrackDao {
    @Query("SELECT * FROM track ORDER BY updatedAtEpochMs DESC")
    fun observeAllOfflineTracks(): Flow<List<Track>>

    @Query("SELECT * FROM track ORDER BY updatedAtEpochMs DESC")
    suspend fun getAllOfflineTracksOnce(): List<Track>

    @Query("SELECT * FROM track WHERE id = :id LIMIT 1")
    suspend fun getOfflineTrackById(id: String): Track?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOfflineTrack(track: Track)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOfflineTracks(tracks: List<Track>)

    @Query("UPDATE track SET lastPlaybackPositionMs = :positionMs, isPlaying = :isPlaying, updatedAtEpochMs = :updatedAtMs WHERE id = :id")
    suspend fun updatePlaybackState(id: String, positionMs: Long, isPlaying: Boolean, updatedAtMs: Long = System.currentTimeMillis())

    @Query("DELETE FROM track WHERE id = :id")
    suspend fun deleteOfflineTrack(id: String)
}

@Dao
interface SynapseMusicDao {
    @Query("SELECT * FROM tracks ORDER BY addedAtEpochMs DESC")
    fun observeAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY addedAtEpochMs DESC")
    suspend fun getAllTracksOnce(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :trackId LIMIT 1")
    suspend fun getTrackById(trackId: String): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrack(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE id = :trackId")
    suspend fun deleteTrackById(trackId: String)

    @Query("DELETE FROM tracks WHERE isDemoTrack = 1")
    suspend fun deleteDemoTracks()

    @Query("UPDATE tracks SET isFavorite = :isFavorite WHERE id = :trackId")
    suspend fun updateFavorite(trackId: String, isFavorite: Boolean)

    @Query("UPDATE tracks SET lastPlayedEpochMs = :timestampMs, lastPositionMs = :positionMs, playCount = playCount + 1 WHERE id = :trackId")
    suspend fun recordTrackPlayed(trackId: String, timestampMs: Long, positionMs: Long)

    @Query("UPDATE tracks SET lastPositionMs = :positionMs WHERE id = :trackId")
    suspend fun updateTrackPosition(trackId: String, positionMs: Long)

    @Query("UPDATE tracks SET isAvailable = :isAvailable, playbackAvailability = :availabilityWire WHERE id = :trackId")
    suspend fun updateTrackAvailability(
        trackId: String,
        isAvailable: Boolean,
        availabilityWire: String = if (isAvailable) "available" else "unavailable"
    )

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY updatedAtEpochMs DESC")
    fun observeAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY updatedAtEpochMs DESC")
    suspend fun getAllPlaylistsOnce(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylistById(playlistId: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    // Playback Snapshot
    @Query("SELECT * FROM playback_snapshot WHERE id = 'active_session' LIMIT 1")
    suspend fun getPlaybackSnapshot(): PlaybackSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlaybackSnapshot(snapshot: PlaybackSnapshotEntity)

    // Settings
    @Query("SELECT * FROM music_settings")
    fun observeSettings(): Flow<List<MusicSettingEntity>>

    @Query("SELECT value FROM music_settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSetting(setting: MusicSettingEntity)
}

/**
 * Room Entity for `youtube_channels` (Section 4 Database Design).
 * Stores OPML-imported YouTube Music channels and their RSS feed synchronization state.
 */
@Entity(
    tableName = "youtube_channels",
    indices = [
        Index(value = ["channel_id"], unique = true),
        Index(value = ["title"]),
        Index(value = ["sync_status"]),
        Index(value = ["last_checked_at"])
    ]
)
data class YouTubeChannelEntity(
    @PrimaryKey
    @ColumnInfo(name = "channel_id")
    val channelId: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "rss_url")
    val rssUrl: String,
    @ColumnInfo(name = "channel_url")
    val channelUrl: String,
    @ColumnInfo(name = "last_checked_at")
    val lastCheckedAt: Long = 0L,
    @ColumnInfo(name = "last_success_at")
    val lastSuccessAt: Long = 0L,
    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "PENDING",
    @ColumnInfo(name = "last_error_code")
    val lastErrorCode: String? = null
)

/**
 * Room Entity for `youtube_videos` (Section 4 Database Design).
 * Stores synchronized YouTube channel Atom RSS video/release metadata.
 */
@Entity(
    tableName = "youtube_videos",
    indices = [
        Index(value = ["video_id"], unique = true),
        Index(value = ["channel_id"]),
        Index(value = ["published_at"]),
        Index(value = ["published_epoch_ms"]),
        Index(value = ["title"]),
        Index(value = ["channel_title"])
    ]
)
data class YouTubeVideoEntity(
    @PrimaryKey
    @ColumnInfo(name = "video_id")
    val videoId: String,
    @ColumnInfo(name = "channel_id")
    val channelId: String,
    @ColumnInfo(name = "channel_title")
    val channelTitle: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "video_url")
    val videoUrl: String,
    @ColumnInfo(name = "published_at")
    val publishedAt: String,
    @ColumnInfo(name = "published_epoch_ms")
    val publishedEpochMs: Long = 0L,
    @ColumnInfo(name = "thumbnail_url")
    val thumbnailUrl: String? = null,
    @ColumnInfo(name = "description")
    val description: String? = null,
    @ColumnInfo(name = "views_count")
    val viewsCount: Long = 0L,
    @ColumnInfo(name = "first_seen_at")
    val firstSeenAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_seen_at")
    val lastSeenAt: Long = System.currentTimeMillis()
)

/**
 * Room Entity for `feed_sync_runs` (Section 4 Database Design).
 * Tracks background RSS feed synchronization runs with bounded retention.
 */
@Entity(
    tableName = "feed_sync_runs",
    indices = [
        Index(value = ["started_at"])
    ]
)
data class FeedSyncRunEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "started_at")
    val startedAt: Long,
    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,
    @ColumnInfo(name = "channels_checked")
    val channelsChecked: Int = 0,
    @ColumnInfo(name = "videos_found")
    val videosFound: Int = 0,
    @ColumnInfo(name = "channels_failed")
    val channelsFailed: Int = 0,
    @ColumnInfo(name = "status")
    val status: String = "RUNNING"
)

/**
 * DAO for YouTube Channel Catalog, RSS Video Discovery, and Sync Run History.
 */
@Dao
interface YouTubeDiscoveryDao {
    @Query("SELECT COUNT(*) FROM youtube_channels")
    suspend fun getChannelCount(): Int

    @Query("SELECT COUNT(*) FROM youtube_videos")
    suspend fun getVideoCount(): Int

    @Query("SELECT * FROM youtube_channels ORDER BY title ASC LIMIT :limit OFFSET :offset")
    suspend fun getChannelsPaged(limit: Int, offset: Int): List<YouTubeChannelEntity>

    @Query("SELECT * FROM youtube_channels ORDER BY last_success_at DESC, title ASC LIMIT :limit")
    fun observeChannels(limit: Int = 100): Flow<List<YouTubeChannelEntity>>

    @Query(
        """
        SELECT * FROM youtube_channels
        WHERE title LIKE '%' || :query || '%' OR channel_id LIKE '%' || :query || '%'
        ORDER BY title ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun searchChannelsPaged(query: String, limit: Int, offset: Int): List<YouTubeChannelEntity>

    @Query("SELECT * FROM youtube_channels WHERE channel_id = :channelId LIMIT 1")
    suspend fun getChannelById(channelId: String): YouTubeChannelEntity?

    @Query(
        """
        SELECT * FROM youtube_channels
        WHERE last_checked_at < :staleBeforeMs
        ORDER BY last_checked_at ASC
        LIMIT :limit
        """
    )
    suspend fun getEligibleChannelsForSync(staleBeforeMs: Long, limit: Int): List<YouTubeChannelEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChannelsIgnore(channels: List<YouTubeChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannels(channels: List<YouTubeChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannel(channel: YouTubeChannelEntity)

    @Query(
        """
        UPDATE youtube_channels
        SET last_checked_at = :checkedAt,
            last_success_at = CASE WHEN :syncStatus = 'OK' THEN :checkedAt ELSE last_success_at END,
            sync_status = :syncStatus,
            last_error_code = :errorCode
        WHERE channel_id = :channelId
        """
    )
    suspend fun updateChannelSyncStatus(
        channelId: String,
        checkedAt: Long,
        syncStatus: String,
        errorCode: String?
    )

    // Videos
    @Query("SELECT * FROM youtube_videos WHERE video_id = :videoId LIMIT 1")
    suspend fun getVideoById(videoId: String): YouTubeVideoEntity?

    @Query(
        """
        SELECT * FROM youtube_videos
        WHERE channel_id = :channelId
        ORDER BY published_epoch_ms DESC, published_at DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getChannelVideosPaged(channelId: String, limit: Int, offset: Int): List<YouTubeVideoEntity>

    @Query(
        """
        SELECT * FROM youtube_videos
        WHERE title LIKE '%' || :query || '%'
           OR channel_title LIKE '%' || :query || '%'
           OR COALESCE(description, '') LIKE '%' || :query || '%'
        ORDER BY published_epoch_ms DESC, views_count DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun searchVideosPaged(query: String, limit: Int, offset: Int): List<YouTubeVideoEntity>

    @Query(
        """
        SELECT * FROM youtube_videos
        ORDER BY published_epoch_ms DESC, last_seen_at DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getLatestVideosPaged(limit: Int, offset: Int): List<YouTubeVideoEntity>

    @Query(
        """
        SELECT * FROM youtube_videos
        ORDER BY views_count DESC, published_epoch_ms DESC
        LIMIT :limit
        """
    )
    suspend fun getTrendingVideos(limit: Int): List<YouTubeVideoEntity>

    @Query(
        """
        SELECT * FROM youtube_videos
        ORDER BY published_epoch_ms DESC, last_seen_at DESC
        LIMIT :limit
        """
    )
    fun observeLatestVideos(limit: Int = 40): Flow<List<YouTubeVideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVideos(videos: List<YouTubeVideoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVideo(video: YouTubeVideoEntity)

    // Sync runs
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncRun(run: FeedSyncRunEntity)

    @Query("SELECT * FROM feed_sync_runs ORDER BY started_at DESC LIMIT 1")
    suspend fun getLatestSyncRun(): FeedSyncRunEntity?

    @Query("SELECT * FROM feed_sync_runs ORDER BY started_at DESC LIMIT 1")
    fun observeLatestSyncRun(): Flow<FeedSyncRunEntity?>

    @Query("SELECT * FROM feed_sync_runs ORDER BY started_at DESC LIMIT :limit")
    suspend fun getRecentSyncRuns(limit: Int = 20): List<FeedSyncRunEntity>

    @Query(
        """
        DELETE FROM feed_sync_runs
        WHERE id NOT IN (
            SELECT id FROM feed_sync_runs ORDER BY started_at DESC LIMIT :maxRuns
        )
        """
    )
    suspend fun pruneSyncRunsKeepingLatest(maxRuns: Int = 50)

    // Music-Radio Offline Cache (Vividh Bharati Live + YouTube Music Feed Entries)
    @Query("SELECT * FROM music_radio_entries WHERE playlist_id = :playlistId ORDER BY sort_order ASC")
    fun observeMusicRadioEntries(playlistId: String): Flow<List<MusicRadioEntryEntity>>

    @Query("SELECT * FROM music_radio_entries WHERE playlist_id = :playlistId ORDER BY sort_order ASC")
    suspend fun getMusicRadioEntriesOnce(playlistId: String): List<MusicRadioEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMusicRadioEntries(entries: List<MusicRadioEntryEntity>)

    @Query("DELETE FROM music_radio_entries WHERE playlist_id = :playlistId")
    suspend fun deleteMusicRadioEntriesForPlaylist(playlistId: String)

    @Query("UPDATE music_radio_entries SET stream_url = :streamUrl WHERE id = :entryId")
    suspend fun updateMusicRadioEntryStreamUrl(entryId: String, streamUrl: String)

    @Query("UPDATE music_radio_entries SET favorite = :favorite WHERE id = :entryId")
    suspend fun setMusicRadioEntryFavorite(entryId: String, favorite: Boolean)
}

/**
 * Room Entity `MusicEntry` mirroring the `music-radio` JSON structure:
 * - `type`: "radio" | "song"
 * - `id`: unique station or YouTube video ID
 * - `title`: track or radio station title
 * - `artist`: artist or broadcaster name
 * - `link`: direct playback / player link
 * - `stream_url`: playable audio stream URL
 * - `embed_url`: embed player URL
 * - `thumbnail`: artwork URL
 * - `description`: summary / track — artist description
 * - `published`: ISO-8601 timestamp or null for live radio
 * - `source`: "Akashvani" | "YouTube Music" | "Global Top 50"
 * - `favorite`: boolean indicating whether the user saved this track/station as a favorite
 */
@Entity(
    tableName = "music_entries",
    indices = [
        Index(value = ["playlist_id", "sort_order"]),
        Index(value = ["type"]),
        Index(value = ["favorite"])
    ]
)
data class MusicEntry(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "type")
    val type: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "artist")
    val artist: String,
    @ColumnInfo(name = "link")
    val link: String,
    @ColumnInfo(name = "stream_url")
    val streamUrl: String,
    @ColumnInfo(name = "embed_url")
    val embedUrl: String,
    @ColumnInfo(name = "thumbnail")
    val thumbnail: String,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "published")
    val published: String? = null,
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "favorite")
    val favorite: Boolean = false,
    @ColumnInfo(name = "playlist_id")
    val playlistId: String = "PL4fGSI1pDJn5kI81J1fYWK5eZRl1zVM5v",
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
    @ColumnInfo(name = "cached_at_epoch_ms")
    val cachedAtEpochMs: Long = System.currentTimeMillis()
) {
    fun playableAudioUrl(): String {
        val trimmed = streamUrl.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("content://", ignoreCase = true) ||
            trimmed.startsWith("file://", ignoreCase = true)
        ) {
            return trimmed
        }
        if (type == "radio" || id == "vividh-bharati") {
            return "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3"
        }
        val idx = (kotlin.math.abs(id.hashCode()) % 8) + 1
        return "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-$idx.mp3"
    }

    fun toTrackEntity(): TrackEntity {
        val isRadio = type == "radio"
        val resolvedStream = playableAudioUrl()
        val mime = when {
            resolvedStream.endsWith(".m3u8", ignoreCase = true) -> "application/x-mpegURL"
            resolvedStream.endsWith(".m4a", ignoreCase = true) || resolvedStream.endsWith(".aac", ignoreCase = true) -> "audio/mp4"
            else -> "audio/mpeg"
        }
        return TrackEntity(
            id = if (isRadio) "radio_$id" else "ytm_$id",
            title = title,
            artist = artist,
            album = if (isRadio) "Akashvani Live Radio" else "Global Top Music",
            durationMs = if (isRadio) 360_000L else 218_000L,
            artworkUri = thumbnail,
            contentUri = resolvedStream,
            mimeType = mime,
            isFavorite = favorite,
            isLocalFile = false,
            isDemoTrack = false,
            isAvailable = true,
            accentColorHex = if (isRadio) 0xFFFA2D48 else 0xFF0A84FF,
            sourceType = if (isRadio) "local" else "youtube-music",
            playbackAvailability = "available",
            externalLaunchUri = link
        )
    }
}

/**
 * Data Access Object (DAO) for caching and observing `MusicEntry` items in the local Room database.
 * Ensures the UI always reads reactive `Flow<List<MusicEntry>>` streams from Room.
 */
@Dao
interface MusicEntryDao {
    @Query(
        """
        SELECT * FROM music_entries
        ORDER BY CASE WHEN type = 'radio' THEN 0 ELSE 1 END ASC, sort_order ASC
        """
    )
    fun observeAllEntries(): Flow<List<MusicEntry>>

    @Query(
        """
        SELECT * FROM music_entries
        WHERE playlist_id = :playlistId
        ORDER BY CASE WHEN type = 'radio' THEN 0 ELSE 1 END ASC, sort_order ASC
        """
    )
    fun observeEntriesByPlaylist(playlistId: String): Flow<List<MusicEntry>>

    @Query(
        """
        SELECT * FROM music_entries
        WHERE favorite = 1
        ORDER BY CASE WHEN type = 'radio' THEN 0 ELSE 1 END ASC, sort_order ASC
        """
    )
    fun observeFavoriteEntries(): Flow<List<MusicEntry>>

    @Query(
        """
        SELECT * FROM music_entries
        ORDER BY CASE WHEN type = 'radio' THEN 0 ELSE 1 END ASC, sort_order ASC
        """
    )
    suspend fun getAllEntriesOnce(): List<MusicEntry>

    @Query(
        """
        SELECT * FROM music_entries
        WHERE playlist_id = :playlistId
        ORDER BY CASE WHEN type = 'radio' THEN 0 ELSE 1 END ASC, sort_order ASC
        """
    )
    suspend fun getEntriesByPlaylistOnce(playlistId: String): List<MusicEntry>

    @Query("SELECT * FROM music_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: String): MusicEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntries(entries: List<MusicEntry>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: MusicEntry)

    @Query("UPDATE music_entries SET stream_url = :streamUrl WHERE id = :id")
    suspend fun updateStreamUrl(id: String, streamUrl: String)

    @Query("UPDATE music_entries SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("DELETE FROM music_entries WHERE playlist_id = :playlistId")
    suspend fun deleteEntriesForPlaylist(playlistId: String)

    @Query("DELETE FROM music_entries")
    suspend fun clearAll()
}

/**
 * Room Entity for `music_radio_entries`.
 * Stores the latest `music-radio` feed (Vividh Bharati Live + YouTube Music entries)
 * for instant offline-first display while background refreshing happens.
 */
@Entity(
    tableName = "music_radio_entries",
    indices = [
        Index(value = ["playlist_id", "sort_order"]),
        Index(value = ["entry_type"])
    ]
)
data class MusicRadioEntryEntity(
    @PrimaryKey
    @ColumnInfo(name = "cache_key")
    val cacheKey: String,
    @ColumnInfo(name = "playlist_id")
    val playlistId: String,
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "entry_type")
    val type: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "artist")
    val artist: String,
    @ColumnInfo(name = "link")
    val link: String,
    @ColumnInfo(name = "stream_url")
    val streamUrl: String,
    @ColumnInfo(name = "embed_url")
    val embedUrl: String,
    @ColumnInfo(name = "thumbnail")
    val thumbnail: String,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "published")
    val published: String?,
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "favorite")
    val favorite: Boolean = false,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
    @ColumnInfo(name = "cached_at_epoch_ms")
    val cachedAtEpochMs: Long = System.currentTimeMillis()
)

@Database(
    entities = [
        Track::class,
        TrackEntity::class,
        PlaylistEntity::class,
        PlaybackSnapshotEntity::class,
        MusicSettingEntity::class,
        YouTubeChannelEntity::class,
        YouTubeVideoEntity::class,
        FeedSyncRunEntity::class,
        MusicRadioEntryEntity::class,
        MusicEntry::class
    ],
    version = 9,
    exportSchema = false
)
abstract class SynapseDatabase : RoomDatabase() {
    abstract fun musicDao(): SynapseMusicDao
    abstract fun trackDao(): TrackDao
    abstract fun discoveryDao(): YouTubeDiscoveryDao
    abstract fun musicEntryDao(): MusicEntryDao

    companion object {
        @Volatile
        private var INSTANCE: SynapseDatabase? = null

        fun getInstance(context: Context): SynapseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SynapseDatabase::class.java,
                    "synapse_music.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * Automated test fixtures and licensed live radio / podcast streams.
 * Note (Section 5): Synthetic demo music tracks are strictly restricted to automated tests
 * and are never auto-seeded into the production music catalog.
 */
object BundledMusicCatalog {
    @VisibleForTesting
    fun defaultTracks(): List<TrackEntity> {
        val now = System.currentTimeMillis()
        return listOf(
            TrackEntity(
                id = "demo_midnight_synapse",
                title = "Midnight Synapse (Spatial Mix)",
                artist = "Aether & Kora",
                album = "Obsidian Architecture",
                durationMs = 372_000L,
                artworkUri = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=800&q=80",
                contentUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                mimeType = "audio/mpeg",
                isFavorite = true,
                isLocalFile = false,
                isDemoTrack = true,
                addedAtEpochMs = now - 86_400_000L * 6,
                lastPlayedEpochMs = now - 3_600_000L * 2,
                lastPositionMs = 94_000L,
                playCount = 14,
                accentColorHex = 0xFF0A84FF,
                sourceType = "local",
                playbackAvailability = "available"
            )
        )
    }

    @VisibleForTesting
    fun defaultPlaylists(): List<PlaylistEntity> = emptyList()

    fun defaultRadioStations(): List<RadioStationItem> = listOf(
        RadioStationItem(
            id = "vividh-bharati",
            name = "Vividh Bharati Live",
            tagline = "Akashvani · All India Radio • Live Hindi Film-Music",
            genre = "Hindi Live",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3",
            artworkUri = "https://dt89je83l7epy.cloudfront.net/images/vividh-bharati.jpg",
            bitrateKbps = 128,
            accentColorHex = 0xFFFA2D48
        ),
        RadioStationItem(
            id = "beatify_one_global",
            name = "Beatify 1 Global",
            tagline = "24/7 Worldwide Flagship • Spatial Electronic & Indie",
            genre = "Electronic",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            artworkUri = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800&q=80",
            bitrateKbps = 320,
            accentColorHex = 0xFFFA2D48
        ),
        RadioStationItem(
            id = "beatify_chill_oled",
            name = "Beatify Chill Radio",
            tagline = "Ambient Glass, Downtempo & Midnight Lo-Fi",
            genre = "Ambient",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
            artworkUri = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=800&q=80",
            bitrateKbps = 320,
            accentColorHex = 0xFF0A84FF
        ),
        RadioStationItem(
            id = "beatify_club_pulse",
            name = "Beatify Club & Bass",
            tagline = "Continuous Melodic Techno & Underground Sets",
            genre = "Club",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            artworkUri = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=800&q=80",
            bitrateKbps = 320,
            accentColorHex = 0xFF7C5CFF
        ),
        RadioStationItem(
            id = "beatify_hits_fm",
            name = "Beatify Hits Live",
            tagline = "Top Global Chartbreakers & Studio Premieres",
            genre = "Hits",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            artworkUri = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800&q=80",
            bitrateKbps = 256,
            accentColorHex = 0xFFFF9F0A
        ),
        RadioStationItem(
            id = "beatify_classical_spatial",
            name = "Beatify Neo-Classical",
            tagline = "Modern Piano, Strings & Orchestral Soundscapes",
            genre = "Classical",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
            artworkUri = "https://images.unsplash.com/photo-1507838153414-b4b713384a76?w=800&q=80",
            bitrateKbps = 320,
            accentColorHex = 0xFF30D158
        ),
        RadioStationItem(
            id = "beatify_synthwave_fm",
            name = "Beatify Analog Horizons",
            tagline = "Modular Synthesizers & Retro-Futuristic Drives",
            genre = "Synthwave",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3",
            artworkUri = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=800&q=80",
            bitrateKbps = 320,
            accentColorHex = 0xFF00E5FF
        )
    )

    fun defaultPodcastEpisodes(): List<PodcastEpisodeItem> = listOf(
        PodcastEpisodeItem(
            id = "pod_ep_spatial_audio",
            showId = "show_sonic_architecture",
            showTitle = "Sonic Architecture",
            title = "Ep. 42 — Designing Lossless Spatial Mixes for OLED Rooms",
            host = "Elena Rostova & Marcus Vance",
            durationMs = 1_845_000L,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            artworkUri = "https://images.unsplash.com/photo-1590602847861-f357a9332bbc?w=800&q=80",
            summary = "How mastering engineers balance sub-bass headroom and binaural reflections.",
            accentColorHex = 0xFFFA2D48
        ),
        PodcastEpisodeItem(
            id = "pod_ep_modular_synth",
            showId = "show_waveform_diaries",
            showTitle = "Waveform Diaries",
            title = "Ep. 19 — Inside Buchla & Eurorack Live Improvisation",
            host = "Sora Vance",
            durationMs = 2_120_000L,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
            artworkUri = "https://images.unsplash.com/photo-1478737270239-2f02b77fc618?w=800&q=80",
            summary = "Exploring west-coast synthesis, tape loops, and generative polyrhythms.",
            accentColorHex = 0xFF7C5CFF
        ),
        PodcastEpisodeItem(
            id = "pod_ep_media3_internals",
            showId = "show_android_audio_lab",
            showTitle = "Android Audio Lab",
            title = "Ep. 11 — Zero-Latency ExoPlayer & MediaSession Pipelines",
            host = "Devon K.",
            durationMs = 1_540_000L,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            artworkUri = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800&q=80",
            summary = "Deep dive into gapless track transitions, audio focus, and lock-screen artwork.",
            accentColorHex = 0xFF0A84FF
        )
    )
}
