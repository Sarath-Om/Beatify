package com.example.services

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.example.data.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Result of scanning on-device audio files via Android [MediaStore.Audio.Media.EXTERNAL_CONTENT_URI].
 */
data class LocalAudioScanResult(
    val permissionGranted: Boolean,
    val scannedTracks: List<TrackEntity>,
    val skippedUnsupportedOrCorruptedCount: Int = 0,
    val statusMessage: String
)

/**
 * Scans the Android device's local audio library (`MP3`, `M4A`, `AAC`, `FLAC`, `WAV`, `OGG`)
 * using [MediaStore.Audio.Media] with runtime permission checks (`READ_MEDIA_AUDIO` on API 33+,
 * `READ_EXTERNAL_STORAGE` on API <= 32).
 *
 * Also supports idempotent YouTube account library metadata import without duplicating playlists or tracks.
 */
object LocalMediaStoreScanner {

    private val SUPPORTED_MIME_TYPES = setOf(
        "audio/mpeg",
        "audio/mp3",
        "audio/mp4",
        "audio/x-m4a",
        "audio/aac",
        "audio/flac",
        "audio/x-flac",
        "audio/wav",
        "audio/x-wav",
        "audio/ogg",
        "application/ogg"
    )

    private val ACCENT_PALETTE = longArrayOf(
        0xFFFA2D48,
        0xFF0A84FF,
        0xFF7C5CFF,
        0xFF30D158,
        0xFFFF9F0A,
        0xFF64D2FF
    )

    fun requiredAudioPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    fun hasAudioPermission(context: Context): Boolean {
        val perm = requiredAudioPermission()
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun scanDeviceAudio(context: Context): LocalAudioScanResult = withContext(Dispatchers.IO) {
        if (!hasAudioPermission(context)) {
            return@withContext LocalAudioScanResult(
                permissionGranted = false,
                scannedTracks = emptyList(),
                skippedUnsupportedOrCorruptedCount = 0,
                statusMessage = "Grant Audio Permission to scan MP3, M4A, AAC, FLAC, WAV, and OGG files on your device."
            )
        }

        val collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        val discovered = mutableListOf<TrackEntity>()
        var skippedCount = 0

        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val displayNameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

                val albumArtBaseUri = Uri.parse("content://media/external/audio/albumart")

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val rawTitle = cursor.getString(titleCol)?.trim().orEmpty()
                    val displayName = cursor.getString(displayNameCol)?.trim().orEmpty()
                    val durationMs = cursor.getLong(durationCol)
                    val sizeBytes = cursor.getLong(sizeCol)
                    val rawMime = cursor.getString(mimeCol)?.trim()?.lowercase().orEmpty()

                    // Skip corrupted/empty files or sub-second notification clips
                    if (sizeBytes <= 0L || durationMs < 1_500L) {
                        skippedCount++
                        continue
                    }

                    val normalizedMime = inferSupportedMimeType(rawMime, displayName)
                    if (normalizedMime == null) {
                        skippedCount++
                        continue
                    }

                    val cleanTitle = rawTitle.ifBlank {
                        displayName.substringBeforeLast('.').ifBlank { "Local Track $mediaId" }
                    }
                    val rawArtist = cursor.getString(artistCol)?.trim().orEmpty()
                    val cleanArtist = if (rawArtist.isBlank() || rawArtist.equals("<unknown>", ignoreCase = true)) {
                        "Local Device Artist"
                    } else {
                        rawArtist
                    }
                    val rawAlbum = cursor.getString(albumCol)?.trim().orEmpty()
                    val cleanAlbum = if (rawAlbum.isBlank() || rawAlbum.equals("<unknown>", ignoreCase = true)) {
                        "On-Device Audio"
                    } else {
                        rawAlbum
                    }

                    val albumId = cursor.getLong(albumIdCol)
                    val contentUri = ContentUris.withAppendedId(collection, mediaId).toString()
                    val artworkUri = if (albumId > 0L) {
                        ContentUris.withAppendedId(albumArtBaseUri, albumId).toString()
                    } else {
                        null
                    }
                    val accent = ACCENT_PALETTE[abs(mediaId.hashCode()) % ACCENT_PALETTE.size]

                    discovered.add(
                        TrackEntity(
                            id = "mediastore_$mediaId",
                            title = cleanTitle,
                            artist = cleanArtist,
                            album = cleanAlbum,
                            durationMs = durationMs.coerceAtLeast(1_500L),
                            artworkUri = artworkUri,
                            contentUri = contentUri,
                            mimeType = normalizedMime,
                            isFavorite = false,
                            isLocalFile = true,
                            isDemoTrack = false,
                            isAvailable = true,
                            accentColorHex = accent,
                            sourceType = "local",
                            playbackAvailability = PlaybackAvailability.AVAILABLE.wireValue,
                            externalLaunchUri = null
                        )
                    )
                }
            }
        }

        val status = if (discovered.isEmpty()) {
            "Scanned device MediaStore • No local audio files found on device storage yet (use Import Audio File to add tracks)."
        } else {
            "Scanned ${discovered.size} local audio file${if (discovered.size == 1) "" else "s"} from device storage."
        }

        LocalAudioScanResult(
            permissionGranted = true,
            scannedTracks = discovered.distinctBy { it.id },
            skippedUnsupportedOrCorruptedCount = skippedCount,
            statusMessage = status
        )
    }

    private fun inferSupportedMimeType(rawMime: String, displayName: String): String? {
        if (rawMime in SUPPORTED_MIME_TYPES) return rawMime
        val lowerName = displayName.lowercase()
        return when {
            lowerName.endsWith(".mp3") -> "audio/mpeg"
            lowerName.endsWith(".m4a") -> "audio/mp4"
            lowerName.endsWith(".aac") -> "audio/aac"
            lowerName.endsWith(".flac") -> "audio/flac"
            lowerName.endsWith(".wav") -> "audio/wav"
            lowerName.endsWith(".ogg") || lowerName.endsWith(".oga") -> "audio/ogg"
            rawMime.startsWith("audio/") -> rawMime
            else -> null
        }
    }
}
