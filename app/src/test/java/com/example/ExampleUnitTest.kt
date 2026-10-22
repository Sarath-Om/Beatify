package com.example

import com.example.components.formatDurationMillis
import com.example.data.BundledMusicCatalog
import com.example.data.PlaybackSnapshotEntity
import com.example.data.PlaylistEntity
import com.example.data.RepeatMode
import com.example.data.TrackEntity
import com.example.services.ContentBlockingService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Synapse Music (`com.synapse.in`) covering Section 9:
 * 1. Track importing, metadata structure, and duration formatting
 * 2. Playlist track ID parsing and updates
 * 3. Queue snapshot serialization and restoration
 * 4. Repeat mode wire mapping and cycle behavior
 * 5. ContentBlockingService telemetry/ad domain filtering and custom rules
 */
class ExampleUnitTest {

    @Test
    fun `bundled music catalog and local track metadata model distinguish demo vs local files`() {
        val defaultTracks = BundledMusicCatalog.defaultTracks()
        assertTrue(defaultTracks.isNotEmpty())
        assertTrue(defaultTracks.all { it.isDemoTrack && !it.isLocalFile })

        val importedLocal = TrackEntity(
            id = "local_test_flac",
            title = "Studio Master Take 1",
            artist = "Synapse Lab",
            album = "Direct Sessions",
            durationMs = 245_000L,
            contentUri = "content://com.android.providers.media.documents/document/audio%3A101",
            mimeType = "audio/flac",
            isLocalFile = true,
            isDemoTrack = false,
            isAvailable = true
        )
        assertTrue(importedLocal.isLocalFile)
        assertFalse(importedLocal.isDemoTrack)
        assertEquals("4:05", formatDurationMillis(importedLocal.durationMs))
    }

    @Test
    fun `playlist entity parses and updates ordered track ids cleanly`() {
        val playlist = PlaylistEntity(
            id = "pl_1",
            name = "Late Night Coding",
            description = "Deep synthwave & ambient",
            trackIdsCsv = "demo_midnight_synapse, demo_neon_horizon , demo_subzero_pulse"
        )
        assertEquals(
            listOf("demo_midnight_synapse", "demo_neon_horizon", "demo_subzero_pulse"),
            playlist.trackIds()
        )

        val emptyPlaylist = PlaylistEntity(id = "pl_empty", name = "Empty", trackIdsCsv = "   ")
        assertTrue(emptyPlaylist.trackIds().isEmpty())
    }

    @Test
    fun `playback snapshot entity serializes queue order repeat and shuffle state`() {
        val snapshot = PlaybackSnapshotEntity(
            currentTrackId = "demo_neon_horizon",
            queueTrackIdsCsv = "demo_midnight_synapse,demo_neon_horizon,demo_subzero_pulse",
            positionMs = 128_500L,
            repeatMode = RepeatMode.ONE.wireValue,
            shuffleEnabled = true,
            playbackSpeed = 1.25f
        )

        assertEquals(3, snapshot.queueTrackIds().size)
        assertEquals("demo_neon_horizon", snapshot.currentTrackId)
        assertEquals(RepeatMode.ONE, RepeatMode.fromWire(snapshot.repeatMode))
        assertEquals(RepeatMode.OFF, RepeatMode.fromWire("unknown_mode"))
        assertTrue(snapshot.shuffleEnabled)
    }

    @Test
    fun `content blocking service filters known audio ad domains and supports custom rules`() {
        val service = ContentBlockingService()
        assertTrue(service.isEnabled())

        assertTrue(service.shouldBlockRequest("https://audio.adswizz.com/preroll.mp3"))
        assertTrue(service.shouldBlockRequest("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"))
        assertFalse(service.shouldBlockRequest("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"))

        // Custom domain rule lifecycle
        assertTrue(service.addCustomRule("||tracker.custommusic.net^"))
        assertTrue(service.shouldBlockRequest("https://sub.tracker.custommusic.net/beacon"))

        service.removeCustomRule("tracker.custommusic.net")
        assertFalse(service.shouldBlockRequest("https://sub.tracker.custommusic.net/beacon"))

        // Disabling protection allows all requests
        service.setEnabled(false)
        assertFalse(service.shouldBlockRequest("https://audio.adswizz.com/preroll.mp3"))
    }
}
