package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.BundledMusicCatalog
import com.example.data.PlaybackSnapshotEntity
import com.example.data.RepeatMode
import com.example.data.SynapseDatabase
import com.example.services.ContentBlockingService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: SynapseDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, SynapseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `app_name string resource matches Beatify`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Beatify", appName)
        assertEquals(5, com.example.data.MusicDestination.entries.size)
        assertTrue(BundledMusicCatalog.defaultRadioStations().isNotEmpty())
        assertTrue(BundledMusicCatalog.defaultPodcastEpisodes().isNotEmpty())
    }

    @Test
    fun `room database persists tracks favorites availability and playback snapshot`() = runBlocking {
        val dao = database.musicDao()
        val tracks = BundledMusicCatalog.defaultTracks()
        dao.upsertTracks(tracks)

        val loaded = dao.getAllTracksOnce()
        assertEquals(tracks.size, loaded.size)

        val target = tracks.first()
        dao.updateFavorite(target.id, false)
        assertFalse(dao.getTrackById(target.id)!!.isFavorite)

        dao.updateTrackAvailability(target.id, false)
        assertFalse(dao.getTrackById(target.id)!!.isAvailable)

        val snapshot = PlaybackSnapshotEntity(
            currentTrackId = target.id,
            queueTrackIdsCsv = tracks.joinToString(",") { it.id },
            positionMs = 42_000L,
            repeatMode = RepeatMode.ALL.wireValue,
            shuffleEnabled = true
        )
        dao.savePlaybackSnapshot(snapshot)

        val restored = dao.getPlaybackSnapshot()
        assertNotNull(restored)
        assertEquals(target.id, restored?.currentTrackId)
        assertEquals(42_000L, restored?.positionMs)
        assertEquals(RepeatMode.ALL.wireValue, restored?.repeatMode)
        assertTrue(restored?.shuffleEnabled == true)
    }

    @Test
    fun `content blocking service sanitizes tracking query parameters on android uri`() {
        val service = ContentBlockingService()
        val rawUrl = "https://cdn.example.com/audio/master.mp3?token=abc123&utm_source=share&fbclid=xyz987"
        val sanitized = service.sanitizeMediaUrl(rawUrl)
        val parsed = Uri.parse(sanitized)
        assertEquals("abc123", parsed.getQueryParameter("token"))
        assertEquals(null, parsed.getQueryParameter("utm_source"))
        assertEquals(null, parsed.getQueryParameter("fbclid"))
    }
}
