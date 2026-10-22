package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
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
    LIBRARY("library", "Library"),
    SETTINGS("settings", "Settings")
}

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
    TRACKS("Tracks"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    PLAYLISTS("Playlists"),
    FAVORITES("Favorites"),
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
 * Authoritative Track contract matching Section 4.3 `Track` interface.
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
    val accentColorHex: Long = 0xFF0A84FF
)

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
 * Authoritative PlaybackState contract matching Section 4.3 `PlaybackState`.
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
    val errorMessage: String? = null
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

    @Query("UPDATE tracks SET isFavorite = :isFavorite WHERE id = :trackId")
    suspend fun updateFavorite(trackId: String, isFavorite: Boolean)

    @Query("UPDATE tracks SET lastPlayedEpochMs = :timestampMs, lastPositionMs = :positionMs, playCount = playCount + 1 WHERE id = :trackId")
    suspend fun recordTrackPlayed(trackId: String, timestampMs: Long, positionMs: Long)

    @Query("UPDATE tracks SET lastPositionMs = :positionMs WHERE id = :trackId")
    suspend fun updateTrackPosition(trackId: String, positionMs: Long)

    @Query("UPDATE tracks SET isAvailable = :isAvailable WHERE id = :trackId")
    suspend fun updateTrackAvailability(trackId: String, isAvailable: Boolean)

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

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaybackSnapshotEntity::class,
        MusicSettingEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class SynapseDatabase : RoomDatabase() {
    abstract fun musicDao(): SynapseMusicDao

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

object BundledMusicCatalog {
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
                accentColorHex = 0xFF0A84FF
            ),
            TrackEntity(
                id = "demo_neon_horizon",
                title = "Neon Horizon",
                artist = "Sora Vance",
                album = "Glass Telemetry",
                durationMs = 425_000L,
                artworkUri = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800&q=80",
                contentUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                mimeType = "audio/mpeg",
                isFavorite = true,
                isLocalFile = false,
                isDemoTrack = true,
                addedAtEpochMs = now - 86_400_000L * 5,
                lastPlayedEpochMs = now - 3_600_000L * 5,
                lastPositionMs = 0L,
                playCount = 9,
                accentColorHex = 0xFF7C5CFF
            ),
            TrackEntity(
                id = "demo_subzero_pulse",
                title = "Subzero Pulse",
                artist = "Kinetix Ensemble",
                album = "Obsidian Architecture",
                durationMs = 344_000L,
                artworkUri = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=800&q=80",
                contentUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
                mimeType = "audio/mpeg",
                isFavorite = false,
                isLocalFile = false,
                isDemoTrack = true,
                addedAtEpochMs = now - 86_400_000L * 4,
                lastPlayedEpochMs = now - 3_600_000L * 14,
                lastPositionMs = 0L,
                playCount = 6,
                accentColorHex = 0xFFFA2D48
            ),
            TrackEntity(
                id = "demo_velvet_echoes",
                title = "Velvet Echoes (Unplugged)",
                artist = "Mira Solis",
                album = "Nocturne Sessions",
                durationMs = 302_000L,
                artworkUri = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800&q=80",
                contentUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
                mimeType = "audio/mpeg",
                isFavorite = true,
                isLocalFile = false,
                isDemoTrack = true,
                addedAtEpochMs = now - 86_400_000L * 3,
                lastPlayedEpochMs = now - 3_600_000L * 20,
                lastPositionMs = 0L,
                playCount = 11,
                accentColorHex = 0xFF30D158
            ),
            TrackEntity(
                id = "demo_liquid_prism",
                title = "Liquid Prism",
                artist = "Aether & Kora",
                album = "Glass Telemetry",
                durationMs = 358_000L,
                artworkUri = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=800&q=80",
                contentUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
                mimeType = "audio/mpeg",
                isFavorite = false,
                isLocalFile = false,
                isDemoTrack = true,
                addedAtEpochMs = now - 86_400_000L * 2,
                lastPlayedEpochMs = 0L,
                lastPositionMs = 0L,
                playCount = 3,
                accentColorHex = 0xFF00E5FF
            ),
            TrackEntity(
                id = "demo_solar_drift",
                title = "Solar Drift",
                artist = "Nova Modular",
                album = "Analog Horizons",
                durationMs = 289_000L,
                artworkUri = "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=800&q=80",
                contentUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3",
                mimeType = "audio/mpeg",
                isFavorite = false,
                isLocalFile = false,
                isDemoTrack = true,
                addedAtEpochMs = now - 86_400_000L,
                lastPlayedEpochMs = 0L,
                lastPositionMs = 0L,
                playCount = 2,
                accentColorHex = 0xFFFF9F0A
            )
        )
    }

    fun defaultPlaylists(): List<PlaylistEntity> {
        val now = System.currentTimeMillis()
        return listOf(
            PlaylistEntity(
                id = "playlist_late_night_oled",
                name = "Late Night OLED",
                description = "Deep spatial electronics and ambient glass textures.",
                trackIdsCsv = "demo_midnight_synapse,demo_neon_horizon,demo_liquid_prism",
                coverColorHex = 0xFF0A84FF,
                createdAtEpochMs = now - 86_400_000L * 4,
                updatedAtEpochMs = now - 3_600_000L
            ),
            PlaylistEntity(
                id = "playlist_focus_flow",
                name = "Focus Architecture",
                description = "High-clarity instrumentals for coding and deep work.",
                trackIdsCsv = "demo_subzero_pulse,demo_velvet_echoes,demo_solar_drift",
                coverColorHex = 0xFF7C5CFF,
                createdAtEpochMs = now - 86_400_000L * 2,
                updatedAtEpochMs = now - 7_200_000L
            )
        )
    }
}
