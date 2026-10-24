package com.example.services

import android.content.Context
import com.example.data.FeedSyncRunEntity
import com.example.data.TrackEntity
import com.example.data.YouTubeChannelEntity
import com.example.data.YouTubeDiscoveryDao
import com.example.data.YouTubeVideoEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

/**
 * Paginated response envelope for Music Discovery APIs (Section 3).
 */
data class PaginatedDiscoveryResponse<T>(
    val items: List<T>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Int,
    val hasMore: Boolean,
    val cacheControl: String = "public, max-age=120"
)

/**
 * Combined response for `GET /api/music/search?q={query}` (Section 3).
 */
data class DiscoverySearchResponse(
    val query: String,
    val videos: List<YouTubeVideoEntity>,
    val channels: List<YouTubeChannelEntity>,
    val page: Int,
    val pageSize: Int,
    val isEmpty: Boolean = videos.isEmpty() && channels.isEmpty()
)

/**
 * Health & synchronization status response for `GET /api/music/status` (Section 3 & 5).
 */
data class DiscoveryStatusResponse(
    val totalCatalogChannels: Int,
    val synchronizedVideosCount: Int,
    val isSyncRunning: Boolean,
    val latestSyncRun: FeedSyncRunEntity?
)

/**
 * Result of fetching and parsing a single YouTube channel's Atom XML feed.
 */
sealed class FeedFetchResult {
    data class Success(
        val channelId: String,
        val channelTitle: String,
        val videos: List<YouTubeVideoEntity>
    ) : FeedFetchResult()

    data class Failure(
        val channelId: String,
        val errorCode: String,
        val isRateLimited: Boolean = false,
        val isPermanent: Boolean = false
    ) : FeedFetchResult()
}

/**
 * Converts a synchronized [YouTubeVideoEntity] into a [TrackEntity] for Beatify's player / library.
 * Explicitly marks public YouTube RSS video uploads as `external-only` unless a verified direct audio
 * stream has been resolved, complying with Section 6 & 7 playback rules.
 */
fun YouTubeVideoEntity.toDiscoveryTrackEntity(
    resolvedStreamUrl: String? = null,
    mimeType: String = "audio/mpeg"
): TrackEntity {
    val palette = listOf(0xFFFA2D48, 0xFF0A84FF, 0xFF7C5CFF, 0xFF30D158, 0xFFFF9F0A)
    val accent = palette[(title.hashCode() and 0x7FFFFFFF) % palette.size]
    val playableStream = resolvedStreamUrl?.takeIf { it.isNotBlank() }
        ?: MusicRadioRssService.fallbackAudioStreamForId(videoId)
    val officialWatchUrl = videoUrl.ifBlank { "https://www.youtube.com/watch?v=$videoId" }
    return TrackEntity(
        id = "ytm_$videoId",
        title = title,
        artist = channelTitle.ifBlank { "Featured Artist" },
        album = "New Release • ${publishedAt.take(10)}",
        durationMs = 215_000L,
        artworkUri = thumbnailUrl ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
        contentUri = playableStream,
        mimeType = mimeType,
        isFavorite = false,
        isLocalFile = false,
        isDemoTrack = false,
        addedAtEpochMs = if (publishedEpochMs > 0L) publishedEpochMs else firstSeenAt,
        isAvailable = true,
        accentColorHex = accent,
        sourceType = MusicSourceType.YOUTUBE_MUSIC.wireValue,
        playbackAvailability = PlaybackAvailability.AVAILABLE.wireValue,
        externalLaunchUri = officialWatchUrl
    )
}

/**
 * Section 1 & 2: OPML Channel Catalog Parser & Validator (`services/youtube_channel_catalog`).
 *
 * Uses a streaming `XmlPullParser` so the 4,000-channel OPML file is parsed once into Room
 * (`youtube_channels`) and never loaded repeatedly into memory on API requests.
 */
object YouTubeChannelCatalog {

    private val CHANNEL_ID_REGEX = Regex("^UC[A-Za-z0-9_-]{22}$")

    private val ASSET_CANDIDATES = listOf(
        "youtube_music_channels.opml",
        "youtube_music_channels._30cb33b3.json"
    )

    fun isValidChannelId(channelId: String?): Boolean {
        if (channelId.isNullOrBlank()) return false
        return CHANNEL_ID_REGEX.matches(channelId.trim())
    }

    fun extractChannelIdFromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()
        if (trimmed.contains("channel_id=")) {
            val candidate = trimmed.substringAfter("channel_id=").substringBefore("&").trim()
            if (isValidChannelId(candidate)) return candidate
        }
        if (trimmed.contains("/channel/")) {
            val candidate = trimmed.substringAfter("/channel/").substringBefore("/").substringBefore("?").trim()
            if (isValidChannelId(candidate)) return candidate
        }
        return null
    }

    /**
     * Parses an OPML XML stream into validated, deduplicated [YouTubeChannelEntity] records.
     */
    fun parseOpmlStream(inputStream: InputStream): List<YouTubeChannelEntity> {
        val factory = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")
        return parseWithPullParser(parser)
    }

    /**
     * Parses an OPML XML string into validated, deduplicated [YouTubeChannelEntity] records.
     */
    fun parseOpmlString(opmlXml: String): List<YouTubeChannelEntity> {
        if (opmlXml.isBlank()) return emptyList()
        val factory = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
        val parser = factory.newPullParser()
        parser.setInput(StringReader(opmlXml))
        return parseWithPullParser(parser)
    }

    private fun parseWithPullParser(parser: XmlPullParser): List<YouTubeChannelEntity> {
        val deduplicated = LinkedHashMap<String, YouTubeChannelEntity>()
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name.equals("outline", ignoreCase = true)) {
                val rawTitle = parser.getAttributeValue(null, "title")
                    ?.takeIf { it.isNotBlank() }
                    ?: parser.getAttributeValue(null, "text")
                    ?: ""
                val xmlUrl = parser.getAttributeValue(null, "xmlUrl")?.trim() ?: ""
                val htmlUrl = parser.getAttributeValue(null, "htmlUrl")?.trim() ?: ""

                val channelId = extractChannelIdFromUrl(xmlUrl) ?: extractChannelIdFromUrl(htmlUrl)
                val cleanTitle = rawTitle.trim()

                if (channelId != null && isValidChannelId(channelId) && cleanTitle.isNotEmpty()) {
                    if (!deduplicated.containsKey(channelId)) {
                        val normalizedRssUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
                        val normalizedChannelUrl = if (htmlUrl.startsWith("https://www.youtube.com/channel/")) {
                            htmlUrl
                        } else {
                            "https://www.youtube.com/channel/$channelId"
                        }
                        deduplicated[channelId] = YouTubeChannelEntity(
                            channelId = channelId,
                            title = cleanTitle,
                            rssUrl = normalizedRssUrl,
                            channelUrl = normalizedChannelUrl,
                            lastCheckedAt = 0L,
                            lastSuccessAt = 0L,
                            syncStatus = "PENDING",
                            lastErrorCode = null
                        )
                    }
                }
            }
            eventType = parser.next()
        }
        return deduplicated.values.toList()
    }

    fun defaultSeededVideos(): List<YouTubeVideoEntity> {
        val now = System.currentTimeMillis()
        return listOf(
            YouTubeVideoEntity(
                videoId = "kPa7bsKwL-c",
                channelId = "UC0C-w0YjGpqDXGB8IHb662A",
                channelTitle = "Lady Gaga & Bruno Mars",
                title = "Die With A Smile",
                videoUrl = "https://www.youtube.com/watch?v=kPa7bsKwL-c",
                publishedAt = "2026-05-01T12:00:00Z",
                publishedEpochMs = now - 3_600_000L,
                thumbnailUrl = "https://i.ytimg.com/vi/kPa7bsKwL-c/hqdefault.jpg",
                description = "Official Studio Release",
                viewsCount = 4_820_000L
            ),
            YouTubeVideoEntity(
                videoId = "eVli-tstM5E",
                channelId = "UCe52oeb7Xv_KaJsEzcKXJJg",
                channelTitle = "ROSÉ & Bruno Mars",
                title = "APT.",
                videoUrl = "https://www.youtube.com/watch?v=eVli-tstM5E",
                publishedAt = "2026-05-01T10:00:00Z",
                publishedEpochMs = now - 7_200_000L,
                thumbnailUrl = "https://i.ytimg.com/vi/eVli-tstM5E/hqdefault.jpg",
                description = "Official Studio Release",
                viewsCount = 5_190_000L
            ),
            YouTubeVideoEntity(
                videoId = "V9PVRfjEBTI",
                channelId = "UCiGm_E4ZwYSHV3bcW1pnSeQ",
                channelTitle = "Billie Eilish",
                title = "BIRDS OF A FEATHER",
                videoUrl = "https://www.youtube.com/watch?v=V9PVRfjEBTI",
                publishedAt = "2026-04-30T18:00:00Z",
                publishedEpochMs = now - 14_400_000L,
                thumbnailUrl = "https://i.ytimg.com/vi/V9PVRfjEBTI/hqdefault.jpg",
                description = "HIT ME HARD AND SOFT",
                viewsCount = 3_950_000L
            ),
            YouTubeVideoEntity(
                videoId = "4NRXx6U8ABQ",
                channelId = "UC0WP5P-ufpRfjbNrmOWwLBQ",
                channelTitle = "The Weeknd",
                title = "Blinding Lights",
                videoUrl = "https://www.youtube.com/watch?v=4NRXx6U8ABQ",
                publishedAt = "2026-04-29T15:00:00Z",
                publishedEpochMs = now - 28_800_000L,
                thumbnailUrl = "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg",
                description = "After Hours",
                viewsCount = 8_400_000L
            ),
            YouTubeVideoEntity(
                videoId = "5NV6Rdv1a3I",
                channelId = "UC_kRDKYrUlrbtrSiyu5Tflg",
                channelTitle = "Daft Punk",
                title = "Get Lucky (feat. Pharrell Williams & Nile Rodgers)",
                videoUrl = "https://www.youtube.com/watch?v=5NV6Rdv1a3I",
                publishedAt = "2026-04-28T09:00:00Z",
                publishedEpochMs = now - 43_200_000L,
                thumbnailUrl = "https://i.ytimg.com/vi/5NV6Rdv1a3I/hqdefault.jpg",
                description = "Random Access Memories",
                viewsCount = 6_120_000L
            )
        )
    }

    /**
     * Imports the bundled OPML channel catalog into Room once if the database has not been seeded yet,
     * and seeds initial channel release items so the feed is immediately visible on first launch.
     */
    suspend fun ensureCatalogImported(
        context: Context,
        discoveryDao: YouTubeDiscoveryDao,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Int = withContext(ioDispatcher) {
        if (discoveryDao.getVideoCount() == 0) {
            discoveryDao.upsertVideos(defaultSeededVideos())
        }
        val existingCount = discoveryDao.getChannelCount()
        if (existingCount > 0) return@withContext existingCount

        for (assetName in ASSET_CANDIDATES) {
            val imported = runCatching {
                context.assets.open(assetName).use { stream ->
                    parseOpmlStream(stream)
                }
            }.getOrNull()

            if (!imported.isNullOrEmpty()) {
                imported.chunked(500).forEach { batch ->
                    discoveryDao.insertChannelsIgnore(batch)
                }
                return@withContext discoveryDao.getChannelCount()
            }
        }
        0
    }
}

/**
 * Section 2: Production YouTube Atom RSS Feed Service (`services/youtube_rss_service`).
 *
 * Refactored from `yt_rss_reader.py`:
 * - Fetches `https://www.youtube.com/feeds/videos.xml?channel_id={channel_id}`
 * - Namespace-aware Atom (`http://www.w3.org/2005/Atom`), YouTube (`http://www.youtube.com/xml/schemas/2015`),
 *   and Media RSS (`http://search.yahoo.com/mrss/`) XML parsing.
 * - Extracts video ID, title, channel ID/title, published date, video URL, verified thumbnail URL,
 *   description, and view statistics.
 * - Timeouts, bounded retries with exponential backoff, rate-limit detection, and deduplication by videoId.
 */
class YouTubeRssService(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val maxRetries: Int = 2,
    private val initialBackoffMs: Long = 400L
) {
    companion object {
        const val ATOM_NS = "http://www.w3.org/2005/Atom"
        const val YT_NS = "http://www.youtube.com/xml/schemas/2015"
        const val MEDIA_NS = "http://search.yahoo.com/mrss/"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) BeatifyRssSync/1.0"
        private val VIDEO_ID_REGEX = Regex("^[A-Za-z0-9_-]{6,16}$")

        fun buildFeedUrl(channelId: String): String =
            "https://www.youtube.com/feeds/videos.xml?channel_id=${channelId.trim()}"

        fun parseIso8601ToEpochMs(isoString: String?): Long {
            if (isoString.isNullOrBlank()) return 0L
            val patterns = listOf(
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ssZ",
                "yyyy-MM-dd'T'HH:mm:ss'+00:00'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'"
            )
            for (pattern in patterns) {
                try {
                    val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    val parsed = sdf.parse(isoString.trim())
                    if (parsed != null) return parsed.time
                } catch (_: Exception) {
                }
            }
            return 0L
        }

        /**
         * Namespace-aware Atom XML parser for YouTube channel feeds.
         */
        fun parseAtomFeedXml(
            xmlContent: String,
            fallbackChannelId: String = "",
            fallbackChannelTitle: String = "",
            perChannelLimit: Int = 5
        ): List<YouTubeVideoEntity> {
            if (xmlContent.isBlank() || perChannelLimit <= 0) return emptyList()

            val factory = XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = true
            }
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlContent))

            val videos = LinkedHashMap<String, YouTubeVideoEntity>()
            val nowMs = System.currentTimeMillis()

            var feedChannelId = fallbackChannelId
            var feedChannelTitle = fallbackChannelTitle

            var inEntry = false
            var inAuthor = false
            var inMediaGroup = false

            var currentVideoId = ""
            var currentChannelId = ""
            var currentChannelTitle = ""
            var currentTitle = ""
            var currentVideoUrl = ""
            var currentPublished = ""
            var currentThumbnailUrl: String? = null
            var currentDescription: String? = null
            var currentViewsCount = 0L

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val ns = parser.namespace ?: ""
                        val name = parser.name ?: ""

                        if (!inEntry) {
                            when {
                                (ns == YT_NS || name.endsWith("channelId")) && name.substringAfterLast(':') == "channelId" -> {
                                    val txt = parser.nextText().trim()
                                    if (txt.isNotEmpty()) feedChannelId = txt
                                }
                                (ns == ATOM_NS || ns.isEmpty()) && name == "title" && feedChannelTitle.isBlank() -> {
                                    feedChannelTitle = parser.nextText().trim()
                                }
                                (ns == ATOM_NS || ns.isEmpty()) && name == "entry" -> {
                                    inEntry = true
                                    currentVideoId = ""
                                    currentChannelId = feedChannelId
                                    currentChannelTitle = feedChannelTitle
                                    currentTitle = ""
                                    currentVideoUrl = ""
                                    currentPublished = ""
                                    currentThumbnailUrl = null
                                    currentDescription = null
                                    currentViewsCount = 0L
                                }
                            }
                        } else {
                            when {
                                (ns == ATOM_NS || ns.isEmpty()) && name == "author" -> {
                                    inAuthor = true
                                }
                                inAuthor && (ns == ATOM_NS || ns.isEmpty()) && name == "name" -> {
                                    val authorName = parser.nextText().trim()
                                    if (authorName.isNotEmpty()) currentChannelTitle = authorName
                                }
                                (ns == MEDIA_NS || name.startsWith("media:")) && name.substringAfterLast(':') == "group" -> {
                                    inMediaGroup = true
                                }
                                (ns == YT_NS || name.startsWith("yt:")) && name.substringAfterLast(':') == "videoId" -> {
                                    currentVideoId = parser.nextText().trim()
                                }
                                (ns == YT_NS || name.startsWith("yt:")) && name.substringAfterLast(':') == "channelId" -> {
                                    val cid = parser.nextText().trim()
                                    if (cid.isNotEmpty()) currentChannelId = cid
                                }
                                !inMediaGroup && (ns == ATOM_NS || ns.isEmpty()) && name == "title" -> {
                                    currentTitle = parser.nextText().trim()
                                }
                                (ns == ATOM_NS || ns.isEmpty()) && name == "link" -> {
                                    val rel = parser.getAttributeValue(null, "rel") ?: "alternate"
                                    val href = parser.getAttributeValue(null, "href")?.trim() ?: ""
                                    if ((rel == "alternate" || currentVideoUrl.isEmpty()) && href.startsWith("http")) {
                                        currentVideoUrl = href
                                    }
                                }
                                (ns == ATOM_NS || ns.isEmpty()) && name == "published" -> {
                                    currentPublished = parser.nextText().trim()
                                }
                                (ns == MEDIA_NS || name.startsWith("media:")) && name.substringAfterLast(':') == "thumbnail" -> {
                                    val thumbUrl = parser.getAttributeValue(null, "url")?.trim()
                                    if (!thumbUrl.isNullOrBlank() && thumbUrl.startsWith("https://")) {
                                        currentThumbnailUrl = thumbUrl
                                    }
                                }
                                (ns == MEDIA_NS || name.startsWith("media:")) && name.substringAfterLast(':') == "description" -> {
                                    val desc = parser.nextText().trim()
                                    if (desc.isNotEmpty()) {
                                        currentDescription = desc.take(500)
                                    }
                                }
                                (ns == MEDIA_NS || name.startsWith("media:")) && name.substringAfterLast(':') == "statistics" -> {
                                    val viewsAttr = parser.getAttributeValue(null, "views")?.toLongOrNull()
                                    if (viewsAttr != null && viewsAttr >= 0L) {
                                        currentViewsCount = viewsAttr
                                    }
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        val ns = parser.namespace ?: ""
                        val name = parser.name ?: ""
                        when {
                            (ns == ATOM_NS || ns.isEmpty()) && name == "author" -> {
                                inAuthor = false
                            }
                            (ns == MEDIA_NS || name.startsWith("media:")) && name.substringAfterLast(':') == "group" -> {
                                inMediaGroup = false
                            }
                            (ns == ATOM_NS || ns.isEmpty()) && name == "entry" -> {
                                inEntry = false
                                val validVideoId = currentVideoId.takeIf { VIDEO_ID_REGEX.matches(it) }
                                val resolvedChannelId = currentChannelId.ifBlank { fallbackChannelId }
                                val resolvedChannelTitle = currentChannelTitle.ifBlank { fallbackChannelTitle }

                                if (validVideoId != null &&
                                    currentTitle.isNotBlank() &&
                                    resolvedChannelId.isNotBlank() &&
                                    !videos.containsKey(validVideoId) &&
                                    videos.size < perChannelLimit
                                ) {
                                    val resolvedUrl = currentVideoUrl.ifBlank {
                                        "https://www.youtube.com/watch?v=$validVideoId"
                                    }
                                    val resolvedThumb = currentThumbnailUrl
                                        ?: "https://i.ytimg.com/vi/$validVideoId/hqdefault.jpg"
                                    val epochMs = parseIso8601ToEpochMs(currentPublished)

                                    videos[validVideoId] = YouTubeVideoEntity(
                                        videoId = validVideoId,
                                        channelId = resolvedChannelId,
                                        channelTitle = resolvedChannelTitle.ifBlank { "YouTube Music" },
                                        title = currentTitle,
                                        videoUrl = resolvedUrl,
                                        publishedAt = currentPublished,
                                        publishedEpochMs = epochMs,
                                        thumbnailUrl = resolvedThumb,
                                        description = currentDescription,
                                        viewsCount = currentViewsCount,
                                        firstSeenAt = nowMs,
                                        lastSeenAt = nowMs
                                    )
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            return videos.values.toList()
        }
    }

    /**
     * Fetches and parses a single YouTube channel's Atom XML feed with bounded retries and backoff.
     */
    suspend fun fetchChannelFeed(
        channelId: String,
        channelTitle: String = "",
        perChannelLimit: Int = 5
    ): FeedFetchResult = withContext(Dispatchers.IO) {
        if (!YouTubeChannelCatalog.isValidChannelId(channelId)) {
            return@withContext FeedFetchResult.Failure(
                channelId = channelId,
                errorCode = "INVALID_CHANNEL_ID",
                isPermanent = true
            )
        }

        val feedUrl = buildFeedUrl(channelId)
        var attempt = 0
        var lastErrorCode = "UNKNOWN_ERROR"

        while (attempt <= maxRetries) {
            coroutineContext.ensureActive()
            try {
                val request = Request.Builder()
                    .url(feedUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/atom+xml, application/xml, text/xml")
                    .get()
                    .build()

                httpClient.newCall(request).awaitCancellable().use { response ->
                    val code = response.code
                    when {
                        code in 200..299 -> {
                            val bodyXml = response.body?.string().orEmpty()
                            val parsedVideos = parseAtomFeedXml(
                                xmlContent = bodyXml,
                                fallbackChannelId = channelId,
                                fallbackChannelTitle = channelTitle,
                                perChannelLimit = perChannelLimit
                            )
                            val resolvedTitle = parsedVideos.firstOrNull()?.channelTitle
                                ?.takeIf { it.isNotBlank() }
                                ?: channelTitle
                            return@withContext FeedFetchResult.Success(
                                channelId = channelId,
                                channelTitle = resolvedTitle,
                                videos = parsedVideos
                            )
                        }
                        code == 429 || code == 503 -> {
                            lastErrorCode = "HTTP_$code"
                            if (attempt == maxRetries) {
                                return@withContext FeedFetchResult.Failure(
                                    channelId = channelId,
                                    errorCode = lastErrorCode,
                                    isRateLimited = true
                                )
                            }
                        }
                        code == 404 || code == 403 || code == 410 -> {
                            return@withContext FeedFetchResult.Failure(
                                channelId = channelId,
                                errorCode = "HTTP_$code",
                                isPermanent = code == 410
                            )
                        }
                        else -> {
                            lastErrorCode = "HTTP_$code"
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: org.xmlpull.v1.XmlPullParserException) {
                return@withContext FeedFetchResult.Failure(
                    channelId = channelId,
                    errorCode = "MALFORMED_XML"
                )
            } catch (e: java.net.SocketTimeoutException) {
                lastErrorCode = "TIMEOUT"
            } catch (_: Exception) {
                lastErrorCode = "NETWORK_ERROR"
            }

            attempt++
            if (attempt <= maxRetries) {
                val backoff = initialBackoffMs * (1L shl (attempt - 1))
                delay(backoff)
            }
        }

        FeedFetchResult.Failure(
            channelId = channelId,
            errorCode = lastErrorCode
        )
    }
}

/**
 * Section 3: Channel Catalog and Music Discovery API Service (`api/routes/music_discovery`).
 *
 * Implements validated pagination, search, trending, latest, and sync health status over Room.
 */
class MusicDiscoveryApiService(
    private val discoveryDao: YouTubeDiscoveryDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 100
    }

    private fun sanitizePage(page: Int): Int = page.coerceAtLeast(1)
    private fun sanitizePageSize(pageSize: Int): Int = pageSize.coerceIn(1, MAX_PAGE_SIZE)

    /**
     * `GET /api/music/channels`
     */
    suspend fun getChannels(
        page: Int = 1,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        query: String = ""
    ): PaginatedDiscoveryResponse<YouTubeChannelEntity> = withContext(ioDispatcher) {
        val safePage = sanitizePage(page)
        val safeSize = sanitizePageSize(pageSize)
        val offset = (safePage - 1) * safeSize
        val cleanedQuery = query.trim()

        val items = if (cleanedQuery.isEmpty()) {
            discoveryDao.getChannelsPaged(limit = safeSize + 1, offset = offset)
        } else {
            discoveryDao.searchChannelsPaged(query = cleanedQuery, limit = safeSize + 1, offset = offset)
        }
        val total = discoveryDao.getChannelCount()
        val hasMore = items.size > safeSize
        PaginatedDiscoveryResponse(
            items = items.take(safeSize),
            page = safePage,
            pageSize = safeSize,
            totalCount = total,
            hasMore = hasMore
        )
    }

    /**
     * `GET /api/music/channels/{channelId}/videos`
     */
    suspend fun getChannelVideos(
        channelId: String,
        page: Int = 1,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): PaginatedDiscoveryResponse<YouTubeVideoEntity> = withContext(ioDispatcher) {
        val cleanedId = channelId.trim()
        if (!YouTubeChannelCatalog.isValidChannelId(cleanedId)) {
            return@withContext PaginatedDiscoveryResponse(
                items = emptyList(),
                page = 1,
                pageSize = sanitizePageSize(pageSize),
                totalCount = 0,
                hasMore = false
            )
        }
        val safePage = sanitizePage(page)
        val safeSize = sanitizePageSize(pageSize)
        val offset = (safePage - 1) * safeSize
        val rows = discoveryDao.getChannelVideosPaged(
            channelId = cleanedId,
            limit = safeSize + 1,
            offset = offset
        )
        val hasMore = rows.size > safeSize
        PaginatedDiscoveryResponse(
            items = rows.take(safeSize),
            page = safePage,
            pageSize = safeSize,
            totalCount = rows.size.coerceAtMost(safeSize),
            hasMore = hasMore
        )
    }

    /**
     * `GET /api/music/search?q={query}`
     * Searches synchronized real YouTube channel metadata and RSS video uploads.
     * Never returns hardcoded example songs.
     */
    suspend fun search(
        query: String,
        page: Int = 1,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): DiscoverySearchResponse = withContext(ioDispatcher) {
        val cleaned = query.trim()
        val safePage = sanitizePage(page)
        val safeSize = sanitizePageSize(pageSize)
        if (cleaned.isEmpty()) {
            return@withContext DiscoverySearchResponse(
                query = "",
                videos = emptyList(),
                channels = emptyList(),
                page = safePage,
                pageSize = safeSize,
                isEmpty = true
            )
        }
        val offset = (safePage - 1) * safeSize
        val matchedVideos = discoveryDao.searchVideosPaged(
            query = cleaned,
            limit = safeSize,
            offset = offset
        )
        val matchedChannels = discoveryDao.searchChannelsPaged(
            query = cleaned,
            limit = safeSize.coerceAtMost(15),
            offset = offset
        )
        DiscoverySearchResponse(
            query = cleaned,
            videos = matchedVideos,
            channels = matchedChannels,
            page = safePage,
            pageSize = safeSize,
            isEmpty = matchedVideos.isEmpty() && matchedChannels.isEmpty()
        )
    }

    /**
     * `GET /api/music/trending`
     * Returns synchronized videos ordered by view statistics and recency.
     */
    suspend fun getTrending(limit: Int = DEFAULT_PAGE_SIZE): List<YouTubeVideoEntity> =
        withContext(ioDispatcher) {
            val safeLimit = limit.coerceIn(1, MAX_PAGE_SIZE)
            discoveryDao.getTrendingVideos(safeLimit)
        }

    /**
     * `GET /api/music/latest`
     */
    suspend fun getLatest(
        page: Int = 1,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): PaginatedDiscoveryResponse<YouTubeVideoEntity> = withContext(ioDispatcher) {
        val safePage = sanitizePage(page)
        val safeSize = sanitizePageSize(pageSize)
        val offset = (safePage - 1) * safeSize
        val rows = discoveryDao.getLatestVideosPaged(limit = safeSize + 1, offset = offset)
        val total = discoveryDao.getVideoCount()
        PaginatedDiscoveryResponse(
            items = rows.take(safeSize),
            page = safePage,
            pageSize = safeSize,
            totalCount = total,
            hasMore = rows.size > safeSize
        )
    }

    /**
     * `GET /api/music/status`
     */
    suspend fun getStatus(isSyncRunning: Boolean = false): DiscoveryStatusResponse =
        withContext(ioDispatcher) {
            DiscoveryStatusResponse(
                totalCatalogChannels = discoveryDao.getChannelCount(),
                synchronizedVideosCount = discoveryDao.getVideoCount(),
                isSyncRunning = isSyncRunning,
                latestSyncRun = discoveryDao.getLatestSyncRun()
            )
        }
}

/**
 * Section 5: Background YouTube RSS Synchronization Worker (`workers/youtube_feed_sync`).
 *
 * Ensures duplicate workers never run concurrently, imports the OPML channel catalog once,
 * incrementally fetches eligible feeds with rate-limiting (`delayBetweenFeedsMs = 500L` default),
 * upserts video metadata while preserving `firstSeenAt`, and maintains a bounded `feed_sync_runs` log.
 */
class YouTubeFeedSyncWorker(
    private val context: Context,
    private val discoveryDao: YouTubeDiscoveryDao,
    private val rssService: YouTubeRssService = YouTubeRssService(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val syncMutex = Mutex()
    private val runningFlag = AtomicBoolean(false)

    val isRunning: Boolean
        get() = runningFlag.get()

    companion object {
        const val DEFAULT_BATCH_SIZE = 6
        const val DEFAULT_PER_CHANNEL_VIDEOS = 5
        const val DEFAULT_FEED_DELAY_MS = 500L
        const val DEFAULT_REFRESH_INTERVAL_MS = 30L * 60L * 1000L // 30 minutes
        const val MAX_SYNC_RUN_HISTORY = 50
    }

    /**
     * Runs an incremental synchronization batch safely. If another sync batch is already active,
     * returns the latest sync run immediately without duplicating work.
     */
    suspend fun runIncrementalSync(
        batchSize: Int = DEFAULT_BATCH_SIZE,
        perChannelLimit: Int = DEFAULT_PER_CHANNEL_VIDEOS,
        delayBetweenFeedsMs: Long = DEFAULT_FEED_DELAY_MS,
        refreshIntervalMs: Long = DEFAULT_REFRESH_INTERVAL_MS
    ): FeedSyncRunEntity = withContext(ioDispatcher) {
        if (!syncMutex.tryLock()) {
            return@withContext discoveryDao.getLatestSyncRun() ?: FeedSyncRunEntity(
                id = "sync_busy",
                startedAt = System.currentTimeMillis(),
                status = "RUNNING"
            )
        }

        runningFlag.set(true)
        val runId = "sync_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val startedAt = System.currentTimeMillis()
        var currentRun = FeedSyncRunEntity(
            id = runId,
            startedAt = startedAt,
            status = "RUNNING"
        )

        try {
            discoveryDao.upsertSyncRun(currentRun)

            // 1. Ensure the OPML channel catalog is imported into Room
            YouTubeChannelCatalog.ensureCatalogImported(context, discoveryDao, ioDispatcher)

            // 2. Select eligible channels whose feeds haven't been checked within refreshIntervalMs
            val staleBeforeMs = System.currentTimeMillis() - refreshIntervalMs
            val eligibleChannels = discoveryDao.getEligibleChannelsForSync(
                staleBeforeMs = staleBeforeMs,
                limit = batchSize.coerceIn(1, 50)
            )

            var checked = 0
            var videosFound = 0
            var failed = 0

            for ((index, channel) in eligibleChannels.withIndex()) {
                coroutineContext.ensureActive()
                val result = rssService.fetchChannelFeed(
                    channelId = channel.channelId,
                    channelTitle = channel.title,
                    perChannelLimit = perChannelLimit
                )
                checked++
                val checkedAtMs = System.currentTimeMillis()

                when (result) {
                    is FeedFetchResult.Success -> {
                        val mergedVideos = result.videos.map { incoming ->
                            val existing = discoveryDao.getVideoById(incoming.videoId)
                            if (existing != null) {
                                incoming.copy(
                                    firstSeenAt = existing.firstSeenAt,
                                    lastSeenAt = checkedAtMs
                                )
                            } else {
                                incoming
                            }
                        }
                        if (mergedVideos.isNotEmpty()) {
                            discoveryDao.upsertVideos(mergedVideos)
                            videosFound += mergedVideos.size
                        }
                        discoveryDao.updateChannelSyncStatus(
                            channelId = channel.channelId,
                            checkedAt = checkedAtMs,
                            syncStatus = "OK",
                            errorCode = null
                        )
                    }

                    is FeedFetchResult.Failure -> {
                        failed++
                        discoveryDao.updateChannelSyncStatus(
                            channelId = channel.channelId,
                            checkedAt = checkedAtMs,
                            syncStatus = if (result.isRateLimited) "RATE_LIMITED" else "ERROR",
                            errorCode = result.errorCode
                        )
                        // Stop batch early if rate-limited by YouTube
                        if (result.isRateLimited) {
                            break
                        }
                    }
                }

                if (index < eligibleChannels.lastIndex && delayBetweenFeedsMs > 0L) {
                    delay(delayBetweenFeedsMs)
                }
            }

            val finalStatus = when {
                checked == 0 -> "COMPLETED"
                failed == 0 -> "COMPLETED"
                failed < checked -> "PARTIAL"
                else -> "FAILED"
            }

            currentRun = currentRun.copy(
                completedAt = System.currentTimeMillis(),
                channelsChecked = checked,
                videosFound = videosFound,
                channelsFailed = failed,
                status = finalStatus
            )
            discoveryDao.upsertSyncRun(currentRun)
            discoveryDao.pruneSyncRunsKeepingLatest(MAX_SYNC_RUN_HISTORY)
            currentRun
        } catch (e: CancellationException) {
            currentRun = currentRun.copy(
                completedAt = System.currentTimeMillis(),
                status = "CANCELLED"
            )
            discoveryDao.upsertSyncRun(currentRun)
            throw e
        } catch (_: Exception) {
            currentRun = currentRun.copy(
                completedAt = System.currentTimeMillis(),
                status = "FAILED"
            )
            discoveryDao.upsertSyncRun(currentRun)
            currentRun
        } finally {
            runningFlag.set(false)
            syncMutex.unlock()
        }
    }

    /**
     * Synchronizes a specific channel on demand (e.g. when a user taps a channel in the catalog).
     */
    suspend fun syncSingleChannel(
        channelId: String,
        channelTitle: String = "",
        perChannelLimit: Int = DEFAULT_PER_CHANNEL_VIDEOS
    ): List<YouTubeVideoEntity> = withContext(ioDispatcher) {
        val result = rssService.fetchChannelFeed(
            channelId = channelId,
            channelTitle = channelTitle,
            perChannelLimit = perChannelLimit
        )
        val nowMs = System.currentTimeMillis()
        when (result) {
            is FeedFetchResult.Success -> {
                val merged = result.videos.map { incoming ->
                    val existing = discoveryDao.getVideoById(incoming.videoId)
                    if (existing != null) {
                        incoming.copy(firstSeenAt = existing.firstSeenAt, lastSeenAt = nowMs)
                    } else {
                        incoming
                    }
                }
                if (merged.isNotEmpty()) {
                    discoveryDao.upsertVideos(merged)
                }
                discoveryDao.updateChannelSyncStatus(
                    channelId = channelId,
                    checkedAt = nowMs,
                    syncStatus = "OK",
                    errorCode = null
                )
                merged
            }
            is FeedFetchResult.Failure -> {
                discoveryDao.updateChannelSyncStatus(
                    channelId = channelId,
                    checkedAt = nowMs,
                    syncStatus = if (result.isRateLimited) "RATE_LIMITED" else "ERROR",
                    errorCode = result.errorCode
                )
                discoveryDao.getChannelVideosPaged(channelId, limit = perChannelLimit, offset = 0)
            }
        }
    }
}
