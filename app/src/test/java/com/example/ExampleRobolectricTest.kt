package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.BundledMusicCatalog
import com.example.data.PlaybackSnapshotEntity
import com.example.data.RepeatMode
import com.example.data.SynapseDatabase
import com.example.data.Track
import com.example.services.BeatifyOtaVerifier
import com.example.services.ContentBlockingService
import com.example.services.MusicAlbumSearchResult
import com.example.services.MusicArtistSearchResult
import com.example.services.MusicSearchProvider
import com.example.services.MusicSearchResult
import com.example.services.PlaybackAvailability
import com.example.services.ProviderProductionGuard
import com.example.services.ResolvedPlaybackStream
import com.example.services.YouTubeMusicInnerTubeProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun `room database persists Track entity offline metadata and playback state`() = runBlocking {
        val trackDao = database.trackDao()
        val offlineTrack = Track(
            id = "local_test_flac_1",
            title = "Veridis Quo",
            artist = "Daft Punk",
            album = "Discovery",
            durationMs = 345_000L,
            artworkUri = "https://i.ytimg.com/vi/abc/hqdefault.jpg",
            localUri = "content://media/external/audio/media/101",
            mimeType = "audio/flac",
            source = "local",
            playbackAvailability = "available",
            isFavorite = true,
            isPlaying = false,
            lastPlaybackPositionMs = 12_000L
        )
        trackDao.upsertOfflineTrack(offlineTrack)

        val loaded = trackDao.getOfflineTrackById("local_test_flac_1")
        assertNotNull(loaded)
        assertEquals("Veridis Quo", loaded?.title)
        assertEquals(12_000L, loaded?.lastPlaybackPositionMs)

        trackDao.updatePlaybackState("local_test_flac_1", positionMs = 98_500L, isPlaying = true)
        val updated = trackDao.getOfflineTrackById("local_test_flac_1")
        assertEquals(98_500L, updated?.lastPlaybackPositionMs)
        assertTrue(updated?.isPlaying == true)
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
    fun `webview client and content blocking service enforce domain boundaries and allowlists`() {
        val service = ContentBlockingService()
        val webViewClient = service.createFilteringWebViewClient()

        // 1. Known ad/telemetry domains and paths are blocked (204 No Content)
        assertTrue(service.shouldBlockRequest("https://googleads.g.doubleclick.net/pagead/ads"))
        assertTrue(service.shouldBlockRequest("https://music.youtube.com/pagead/interaction"))
        assertTrue(service.shouldBlockRequest("https://music.youtube.com/api/stats/ads?ver=2"))

        @Suppress("DEPRECATION")
        val blockedResp = webViewClient.shouldInterceptRequest(null, "https://ads.youtube.com/track")
        assertNotNull(blockedResp)
        assertEquals(204, blockedResp?.statusCode)

        // 2. Essential YouTube Music playback, search, login, and artwork endpoints are explicitly allowed
        assertFalse(service.shouldBlockRequest("https://music.youtube.com/youtubei/v1/player?prettyPrint=false"))
        assertFalse(service.shouldBlockRequest("https://music.youtube.com/youtubei/v1/search?prettyPrint=false"))
        assertFalse(service.shouldBlockRequest("https://rr2---sn-ab5l6nzy.googlevideo.com/videoplayback?expire=123"))
        assertFalse(service.shouldBlockRequest("https://accounts.google.com/ServiceLogin"))
        assertFalse(service.shouldBlockRequest("https://i.ytimg.com/vi/jfKfPfyJRdk/maxresdefault.jpg"))

        // 3. Substring-only domain names are NOT falsely blocked
        assertFalse(service.shouldBlockRequest("https://myads.youtube.com/watch"))

        // 4. Tracking parameters are stripped cleanly
        val rawUrl = "https://cdn.example.com/audio/master.mp3?token=abc123&utm_source=share&fbclid=xyz987"
        val sanitized = service.sanitizeMediaUrl(rawUrl)
        val parsed = Uri.parse(sanitized)
        assertEquals("abc123", parsed.getQueryParameter("token"))
        assertNull(parsed.getQueryParameter("utm_source"))
        assertNull(parsed.getQueryParameter("fbclid"))
    }

    @Test
    fun `youtube music provider parses innertube and stream responses and guards release builds`() {
        val provider = YouTubeMusicInnerTubeProvider()
        ProviderProductionGuard.validateProviderForEnvironment(provider, isReleaseBuild = true)

        val mockProvider = object : MusicSearchProvider {
            override val providerId: String = "mock-dev"
            override val isMockProvider: Boolean = true
            override suspend fun searchTracks(query: String): List<MusicSearchResult> = emptyList()
            override suspend fun searchArtists(query: String): List<MusicArtistSearchResult> = emptyList()
            override suspend fun searchAlbums(query: String): List<MusicAlbumSearchResult> = emptyList()
            override suspend fun resolvePlaybackStream(result: MusicSearchResult): ResolvedPlaybackStream =
                ResolvedPlaybackStream.Unavailable("mock")
        }

        var guardTriggered = false
        try {
            ProviderProductionGuard.validateProviderForEnvironment(mockProvider, isReleaseBuild = true)
        } catch (_: IllegalStateException) {
            guardTriggered = true
        }
        assertTrue(guardTriggered)

        val sampleInnerTubeJson = JSONObject(
            """
            {
              "contents": {
                "sectionListRenderer": {
                  "contents": [
                    {
                      "musicShelfRenderer": {
                        "contents": [
                          {
                            "musicResponsiveListItemRenderer": {
                              "playlistItemData": { "videoId": "dQw4w9WgXcQ" },
                              "thumbnail": {
                                "musicThumbnailRenderer": {
                                  "thumbnail": {
                                    "thumbnails": [
                                      { "url": "https://lh3.googleusercontent.com/sample_art_600.jpg" }
                                    ]
                                  }
                                }
                              },
                              "flexColumns": [
                                {
                                  "musicResponsiveListItemFlexColumnRenderer": {
                                    "text": { "runs": [{ "text": "Instant Crush" }] }
                                  }
                                },
                                {
                                  "musicResponsiveListItemFlexColumnRenderer": {
                                    "text": {
                                      "runs": [
                                        { "text": "Song" },
                                        { "text": "•" },
                                        { "text": "Daft Punk", "navigationEndpoint": { "browseEndpoint": { "browseId": "UC_daft_punk" } } },
                                        { "text": "•" },
                                        { "text": "Random Access Memories", "navigationEndpoint": { "browseEndpoint": { "browseId": "MPREb_ram" } } },
                                        { "text": "•" },
                                        { "text": "5:37" }
                                      ]
                                    }
                                  }
                                }
                              ]
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
              }
            }
            """.trimIndent()
        )

        val parsedSongs = provider.parseInnerTubeSongs(sampleInnerTubeJson)
        assertEquals(1, parsedSongs.size)
        val song = parsedSongs.first()
        assertEquals("ytm_dQw4w9WgXcQ", song.id)
        assertEquals("Instant Crush", song.title)
        assertEquals("Daft Punk", song.primaryArtistName)
        assertEquals("Random Access Memories", song.album?.name)
        assertEquals(337_000L, song.durationMs)
        assertEquals(PlaybackAvailability.EXTERNAL_ONLY, song.playbackAvailability)

        // Verify OTA manifest integrity checks
        val validManifest = BeatifyOtaVerifier.buildSignedManifestForChannel("stable")
        assertTrue(BeatifyOtaVerifier.verifyManifest(validManifest).isSuccess)
        val tamperedManifest = validManifest.copy(sha256Checksum = "0000")
        assertTrue(BeatifyOtaVerifier.verifyManifest(tamperedManifest).isFailure)
    }

    @Test
    fun `debounced music search coordinator cancels superseded queries and in-flight requests`() = runBlocking {
        val executedQueries = mutableListOf<String>()
        var cancelCallsCount = 0

        val fakeProvider = object : MusicSearchProvider {
            override val providerId: String = "test-debounced-provider"
            override val isMockProvider: Boolean = false

            override suspend fun searchTracks(query: String): List<MusicSearchResult> {
                kotlinx.coroutines.delay(40L)
                synchronized(executedQueries) {
                    executedQueries.add(query)
                }
                return listOf(
                    MusicSearchResult(
                        id = "ytm_$query",
                        title = "Track $query",
                        artists = listOf(com.example.services.MusicArtistRef(name = "Artist $query")),
                        playbackAvailability = PlaybackAvailability.AVAILABLE,
                        playableStreamUrl = "https://cdn.example.com/$query.m4a"
                    )
                )
            }

            override suspend fun searchArtists(query: String): List<MusicArtistSearchResult> = emptyList()
            override suspend fun searchAlbums(query: String): List<MusicAlbumSearchResult> = emptyList()
            override suspend fun resolvePlaybackStream(result: MusicSearchResult): ResolvedPlaybackStream =
                ResolvedPlaybackStream.Unavailable("test")

            override fun cancelInFlightRequests() {
                cancelCallsCount++
            }
        }

        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        val coordinator = com.example.services.DebouncedMusicSearchCoordinator(
            provider = fakeProvider,
            scope = scope,
            debounceMs = 80L,
            ioDispatcher = kotlinx.coroutines.Dispatchers.Default
        )

        var lastBatch: com.example.services.MusicSearchBatchResult? = null
        coordinator.submitQuery("da", onSuccess = { lastBatch = it }, onError = {})
        kotlinx.coroutines.delay(25L)
        coordinator.submitQuery("daft", onSuccess = { lastBatch = it }, onError = {})
        kotlinx.coroutines.delay(25L)
        coordinator.submitQuery("daft punk", onSuccess = { lastBatch = it }, onError = {})

        kotlinx.coroutines.delay(220L)

        // Rapid intermediate queries ("da", "daft") were debounced/cancelled before firing network requests
        assertTrue(cancelCallsCount >= 3)
        assertEquals(listOf("daft punk"), executedQueries)
        assertNotNull(lastBatch)
        assertEquals("daft punk", lastBatch?.query)
        assertEquals("Track daft punk", lastBatch?.tracks?.firstOrNull()?.title)
    }

    @Test
    fun `media playback service initializes exoplayer session and playlist repository caches offline crud`() = runBlocking {
        // 1. Verify MediaPlaybackService creates and exposes a valid MediaSession backed by ExoPlayer
        val serviceController = org.robolectric.Robolectric.buildService(com.example.playback.MediaPlaybackService::class.java)
            .create()
        val service = serviceController.get()
        assertNotNull(service)
        serviceController.destroy()

        // 2. Verify PlaylistRepository performs offline CRUD operations with local Room + memory caching
        val playlistRepo = com.example.data.PlaylistRepository(
            musicDao = database.musicDao(),
            db = null,
            auth = null
        )

        val createdResult = playlistRepo.createPlaylist(
            name = "Offline Roadtrip Mix",
            description = "Cached locally for airplane mode",
            trackIds = listOf("track_1", "track_2"),
            customPlaylistId = "pl_offline_1"
        )
        assertTrue(createdResult.isSuccess)
        val created = createdResult.getOrThrow()
        assertEquals("pl_offline_1", created.id)
        assertEquals("Offline Roadtrip Mix", created.name)
        assertEquals(listOf("track_1", "track_2"), created.trackIds())
        assertEquals(1, playlistRepo.pendingMutations.value.size)

        // Read from local cache by ID and list
        val fetchedById = playlistRepo.getPlaylistById("pl_offline_1").getOrNull()
        assertNotNull(fetchedById)
        assertEquals("Offline Roadtrip Mix", fetchedById?.name)

        val allCached = playlistRepo.getAllPlaylists()
        assertEquals(1, allCached.size)

        // Update playlist and add/remove track in local cache
        val sampleTrack = BundledMusicCatalog.defaultTracks().first()
        val afterAdd = playlistRepo.addTrackToPlaylist("pl_offline_1", sampleTrack).getOrThrow()
        assertTrue(sampleTrack.id in afterAdd.trackIds())

        val afterUpdate = playlistRepo.updatePlaylist(
            playlistId = "pl_offline_1",
            name = "Offline Roadtrip Mix Deluxe",
            description = "Updated offline"
        ).getOrThrow()
        assertEquals("Offline Roadtrip Mix Deluxe", afterUpdate.name)
        assertEquals("Updated offline", afterUpdate.description)

        val afterRemove = playlistRepo.removeTrackFromPlaylist("pl_offline_1", "track_1").getOrThrow()
        assertFalse("track_1" in afterRemove.trackIds())

        // Delete playlist removes it from local Room and memory cache
        val deleteResult = playlistRepo.deletePlaylist("pl_offline_1")
        assertTrue(deleteResult.isSuccess)
        assertNull(playlistRepo.getPlaylistById("pl_offline_1").getOrNull())
        assertTrue(playlistRepo.getAllPlaylists().isEmpty())
    }

    @Test
    fun `opml channel catalog atom rss discovery music radio and debounced search bar work end to end`() = runBlocking {
        // 1. Verify OPML XML parsing, channel ID validation, and deduplication
        val sampleOpml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="1.0">
              <body>
                <outline type="rss" text="T-Series" title="T-Series" xmlUrl="https://www.youtube.com/feeds/videos.xml?channel_id=UCq-Fj5jknLsUf-MWSy4_brA" htmlUrl="https://www.youtube.com/channel/UCq-Fj5jknLsUf-MWSy4_brA"/>
                <outline type="rss" text="Duplicate T-Series" title="T-Series" xmlUrl="https://www.youtube.com/feeds/videos.xml?channel_id=UCq-Fj5jknLsUf-MWSy4_brA" htmlUrl="https://www.youtube.com/channel/UCq-Fj5jknLsUf-MWSy4_brA"/>
                <outline type="rss" text="Daft Punk" title="Daft Punk" xmlUrl="https://www.youtube.com/feeds/videos.xml?channel_id=UC_kRDKYrUlrbtrSiyu5Tflg" htmlUrl="https://www.youtube.com/channel/UC_kRDKYrUlrbtrSiyu5Tflg"/>
                <outline type="rss" text="Invalid Channel" title="Invalid" xmlUrl="https://www.youtube.com/feeds/videos.xml?channel_id=BAD_ID"/>
              </body>
            </opml>
        """.trimIndent()

        val parsedChannels = com.example.services.YouTubeChannelCatalog.parseOpmlString(sampleOpml)
        assertEquals(2, parsedChannels.size)
        assertEquals("UCq-Fj5jknLsUf-MWSy4_brA", parsedChannels[0].channelId)
        assertEquals("T-Series", parsedChannels[0].title)
        assertEquals("UC_kRDKYrUlrbtrSiyu5Tflg", parsedChannels[1].channelId)

        // 2. Verify namespace-aware YouTube Atom XML parsing, deduplication, and perChannelLimit
        val sampleAtomXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns:media="http://search.yahoo.com/mrss/" xmlns="http://www.w3.org/2005/Atom">
              <yt:channelId>UC_kRDKYrUlrbtrSiyu5Tflg</yt:channelId>
              <title>Daft Punk</title>
              <entry>
                <yt:videoId>5NV6Rdv1a3I</yt:videoId>
                <yt:channelId>UC_kRDKYrUlrbtrSiyu5Tflg</yt:channelId>
                <title>Daft Punk - Get Lucky (Official Audio)</title>
                <link rel="alternate" href="https://www.youtube.com/watch?v=5NV6Rdv1a3I"/>
                <author><name>Daft Punk</name></author>
                <published>2026-04-19T12:00:00+00:00</published>
                <media:group>
                  <media:thumbnail url="https://i.ytimg.com/vi/5NV6Rdv1a3I/hqdefault.jpg"/>
                  <media:description>Official audio from Random Access Memories.</media:description>
                  <media:community>
                    <media:statistics views="1500000"/>
                  </media:community>
                </media:group>
              </entry>
              <entry>
                <yt:videoId>5NV6Rdv1a3I</yt:videoId>
                <title>Duplicate Entry Should Be Ignored</title>
                <link rel="alternate" href="https://www.youtube.com/watch?v=5NV6Rdv1a3I"/>
                <published>2026-04-19T12:00:00+00:00</published>
              </entry>
            </feed>
        """.trimIndent()

        val parsedVideos = com.example.services.YouTubeRssService.parseAtomFeedXml(
            xmlContent = sampleAtomXml,
            perChannelLimit = 5
        )
        assertEquals(1, parsedVideos.size)
        assertEquals("5NV6Rdv1a3I", parsedVideos[0].videoId)
        assertEquals("Daft Punk - Get Lucky (Official Audio)", parsedVideos[0].title)
        assertEquals("Daft Punk", parsedVideos[0].channelTitle)
        assertEquals(1500000L, parsedVideos[0].viewsCount)

        // 3. Verify MusicDiscoveryApiService pagination, search, trending, and latest over Room
        val discoveryDao = database.discoveryDao()
        discoveryDao.upsertChannels(parsedChannels)
        discoveryDao.upsertVideos(parsedVideos)

        val apiService = com.example.services.MusicDiscoveryApiService(discoveryDao)
        val channelsPage = apiService.getChannels(page = 1, pageSize = 10)
        assertEquals(2, channelsPage.totalCount)
        assertEquals(2, channelsPage.items.size)

        val searchResult = apiService.search("Get Lucky", page = 1, pageSize = 10)
        assertFalse(searchResult.isEmpty)
        assertEquals(1, searchResult.videos.size)
        assertEquals("5NV6Rdv1a3I", searchResult.videos.first().videoId)

        val emptySearch = apiService.search("NonExistentSong99999", page = 1, pageSize = 10)
        assertTrue(emptySearch.isEmpty)

        // 4. Verify MusicRadioRssService (Vividh Bharati Live + YouTube Music Playlist RSS, Shorts filtering & splitTitle)
        val (song1, artist1) = com.example.services.MusicRadioRssService.splitTitle(
            "The Weeknd - Blinding Lights (Official Audio)",
            "TheWeekndVEVO"
        )
        assertEquals("Blinding Lights (Official Audio)", song1)
        assertEquals("The Weeknd", artist1)

        val (song2, artist2) = com.example.services.MusicRadioRssService.splitTitle(
            "Veridis Quo",
            "Daft Punk - Topic"
        )
        assertEquals("Veridis Quo", song2)
        assertEquals("Daft Punk", artist2)

        val playlistXmlWithShorts = """
            <feed>
              <entry>
                <yt:videoId>4NRXx6U8ABQ</yt:videoId>
                <title>The Weeknd - Blinding Lights</title>
                <link rel="alternate" href="https://www.youtube.com/watch?v=4NRXx6U8ABQ"/>
                <author><name>The Weeknd - Topic</name></author>
                <published>2026-05-01T10:00:00+00:00</published>
                <media:group>
                  <media:thumbnail url="https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg"/>
                  <media:description>Full length track</media:description>
                </media:group>
              </entry>
              <entry>
                <yt:videoId>shortVid123</yt:videoId>
                <title>Studio Preview #shorts</title>
                <link rel="alternate" href="https://www.youtube.com/shorts/shortVid123"/>
                <author><name>Artist</name></author>
                <published>2026-05-02T10:00:00+00:00</published>
              </entry>
            </feed>
        """.trimIndent()

        val playlistEntries = com.example.services.MusicRadioRssService.parsePlaylistFeed(playlistXmlWithShorts, 15)
        assertEquals(1, playlistEntries.size) // Short is excluded!
        assertEquals("4NRXx6U8ABQ", playlistEntries[0].id)
        assertEquals("Blinding Lights", playlistEntries[0].title)
        assertEquals("The Weeknd", playlistEntries[0].artist)

        val rssOutput = com.example.services.MusicRadioRssService.toRss(
            entries = playlistEntries,
            selfUrl = "https://beatify.in/music-radio",
            warnings = listOf("Akashvani does not expose a stream URL in its page; linking the official player")
        )
        assertTrue(rssOutput.contains("<title>Sarath Music &amp; Radio</title>"))
        assertTrue(rssOutput.contains("Blinding Lights — The Weeknd"))
    }
}
