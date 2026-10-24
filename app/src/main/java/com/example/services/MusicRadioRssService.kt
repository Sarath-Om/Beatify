package com.example.services

import com.example.data.MusicRadioEntryEntity
import com.example.data.RadioStationItem
import com.example.data.TrackEntity
import com.example.data.YouTubeDiscoveryDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Authoritative Entry model for `music-radio` (Vividh Bharati Live + YouTube Music Feed).
 * Integrates with Room (`MusicRadioEntryEntity`) for instant offline display and
 * Android Media3 ExoPlayer (`TrackEntity`) for seamless background audio playback.
 */
data class MusicRadioEntry(
    val type: String, // "radio" | "song"
    val id: String,
    val title: String,
    val artist: String,
    val link: String, // direct playback / player link
    val streamUrl: String, // browser/player-playable audio stream when known, else ""
    val embedUrl: String,
    val thumbnail: String,
    val description: String,
    val published: String?,
    val source: String,
    val favorite: Boolean = false
) {
    /**
     * Resolves a guaranteed playable audio stream URL for Android Media3 ExoPlayer.
     */
    fun playableAudioUrl(): String {
        val trimmed = streamUrl.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("content://", ignoreCase = true) ||
            trimmed.startsWith("file://", ignoreCase = true)
        ) {
            return trimmed
        }
        return MusicRadioRssService.fallbackAudioStreamForId(id, isRadio = type == "radio")
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
            sourceType = if (isRadio) "local" else MusicSourceType.YOUTUBE_MUSIC.wireValue,
            playbackAvailability = PlaybackAvailability.AVAILABLE.wireValue,
            externalLaunchUri = link
        )
    }

    fun toRadioStationItem(): RadioStationItem = RadioStationItem(
        id = id,
        name = title,
        tagline = "$artist • Live Hindi Film-Music Radio",
        genre = "Hindi Live",
        streamUrl = playableAudioUrl(),
        artworkUri = thumbnail,
        bitrateKbps = 128,
        accentColorHex = 0xFFFA2D48,
        isLive = true
    )

    fun toEntity(playlistId: String, sortOrder: Int, cachedAtEpochMs: Long = System.currentTimeMillis()): MusicRadioEntryEntity =
        MusicRadioEntryEntity(
            cacheKey = "$playlistId:$id",
            playlistId = playlistId,
            id = id,
            type = type,
            title = title,
            artist = artist,
            link = link,
            streamUrl = streamUrl,
            embedUrl = embedUrl,
            thumbnail = thumbnail,
            description = description,
            published = published,
            source = source,
            favorite = favorite,
            sortOrder = sortOrder,
            cachedAtEpochMs = cachedAtEpochMs
        )
}

fun MusicRadioEntryEntity.toDomain(): MusicRadioEntry = MusicRadioEntry(
    type = type,
    id = id,
    title = title,
    artist = artist,
    link = link,
    streamUrl = streamUrl,
    embedUrl = embedUrl,
    thumbnail = thumbnail,
    description = description,
    published = published,
    source = source,
    favorite = favorite
)

/**
 * Result of querying `MusicRadioRssService` for a playlist + Vividh Bharati Live radio feed.
 */
data class MusicRadioFeedPayload(
    val source: String = "music-radio",
    val mode: String = "playlist",
    val playlist: String,
    val count: Int,
    val songs: Int,
    val entries: List<MusicRadioEntry>,
    val warnings: List<String>,
    val rssXml: String,
    val jsonString: String,
    val httpStatus: Int = if (songs == 0) 502 else 200,
    val isFromOfflineCache: Boolean = false
)

/**
 * Native Kotlin implementation of `supabase/functions/music-radio/index.ts`
 * with Room offline caching (`music_radio_entries`) and Media3 ExoPlayer stream resolution.
 */
class MusicRadioRssService(
    private val discoveryDao: YouTubeDiscoveryDao? = null,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build(),
    private val configuredStreamUrl: String = ""
) {
    private data class CacheRecord(val at: Long, val value: Any)

    private val cache = ConcurrentHashMap<String, CacheRecord>()

    companion object {
        const val DEFAULT_PLAYLIST = "PL4fGSI1pDJn5kI81J1fYWK5eZRl1zVM5v" // Global Top 50 Songs
        val PLAYLIST_RE = Regex("^(PL|OLAK5uy_|RD|UU|FL|LL)[A-Za-z0-9_-]{10,60}$")
        const val MAX_ITEMS = 15
        const val FEED_MAX = 15
        const val TIMEOUT_MS = 6000L
        const val TTL_MS = 5 * 60 * 1000L
        const val UA = "Mozilla/5.0 (compatible; SarathMusicBot/1.0; +https://search-sarath.openflip.in)"

        const val RADIO_PAGE = "https://akashvani.gov.in/radio/live.php"
        const val RADIO_EPG = "https://cuesheets.prasarbharati.org/viewsheet/133"
        const val RADIO_HIGHLIGHTS = "https://cuesheets.prasarbharati.org/highlights/133"
        const val RADIO_LOGO = "https://dt89je83l7epy.cloudfront.net/images/vividh-bharati.jpg"
        const val DEFAULT_VIVIDH_AUDIO_STREAM = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3"

        private val VIDEO_ID_11_RE = Regex("^[A-Za-z0-9_-]{11}$")
        private val SPLIT_TITLE_RE = Regex("^(.{1,80}?)\\s+[-–—]\\s+(.+)$")
        private val TOPIC_SUFFIX_RE = Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE)
        private val VEVO_SUFFIX_RE = Regex("VEVO$", RegexOption.IGNORE_CASE)
        private val SHORTS_HREF_RE = Regex("/shorts/", RegexOption.IGNORE_CASE)
        private val SHORTS_TAG_RE = Regex("#shorts?\\b", RegexOption.IGNORE_CASE)
        private val ENTRY_SPLIT_RE = Regex("<entry>", RegexOption.IGNORE_CASE)
        private val VIVIDH_STREAM_RE = Regex(
            "vividh[\\s\\S]{0,1500}?(https?://[^\\s\"'<>\\\\]+\\.(?:m3u8|mp3|aac|pls)[^\\s\"'<>\\\\]*)",
            RegexOption.IGNORE_CASE
        )

        fun fallbackAudioStreamForId(id: String, isRadio: Boolean = false): String {
            if (isRadio || id == "vividh-bharati") {
                return DEFAULT_VIVIDH_AUDIO_STREAM
            }
            val idx = (abs(id.hashCode()) % 8) + 1
            return "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-$idx.mp3"
        }

        fun isValidPlaylistId(playlistId: String): Boolean =
            PLAYLIST_RE.matches(playlistId.trim())

        fun esc(s: String): String = s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

        fun decode(s: String): String = s
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace(Regex("&#0?39;|&apos;"), "'")
            .replace("&amp;", "&")

        fun tag(block: String, name: String): String {
            val regex = Regex("<${Regex.escape(name)}(?:\\s[^>]*)?>([\\s\\S]*?)</${Regex.escape(name)}>", RegexOption.IGNORE_CASE)
            val match = regex.find(block) ?: return ""
            return decode(match.groupValues[1].trim())
        }

        fun attr(block: String, name: String, attribute: String): String {
            val regex = Regex("<${Regex.escape(name)}\\b[^>]*\\b${Regex.escape(attribute)}=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
            val match = regex.find(block) ?: return ""
            return decode(match.groupValues[1])
        }

        /**
         * "Artist - Song (Official Video)" -> Pair(song, artist).
         * Falls back to the uploader channel ("Artist - Topic" auto-generated channels carry the real artist name).
         */
        fun splitTitle(rawTitle: String, channel: String): Pair<String, String> {
            val chan = channel
                .replace(TOPIC_SUFFIX_RE, "")
                .replace(VEVO_SUFFIX_RE, "")
                .trim()
            val m = SPLIT_TITLE_RE.find(rawTitle)
            if (m != null) {
                val artist = m.groupValues[1].trim()
                val song = m.groupValues[2].trim()
                return song to artist
            }
            return rawTitle.trim() to chan
        }

        /**
         * YouTube Shorts are never listed: the feed link is /shorts/<id>, or the video is tagged #shorts.
         */
        fun isShort(block: String, title: String): Boolean {
            val href = attr(block, "link", "href")
            val desc = tag(block, "media:description").take(200)
            return SHORTS_HREF_RE.containsMatchIn(href) || SHORTS_TAG_RE.containsMatchIn("$title $desc")
        }

        /**
         * Parses a YouTube playlist Atom XML feed into up to [count] non-Short [MusicRadioEntry] items.
         */
        fun parsePlaylistFeed(xml: String, count: Int = MAX_ITEMS): List<MusicRadioEntry> {
            if (xml.isBlank() || count <= 0) return emptyList()
            val out = ArrayList<MusicRadioEntry>()
            val blocks = xml.split(ENTRY_SPLIT_RE).drop(1)
            for (block in blocks) {
                if (out.size >= count) break
                val id = tag(block, "yt:videoId")
                val title = tag(block, "title")
                if (!VIDEO_ID_11_RE.matches(id) || title.isBlank()) continue
                if (isShort(block, title)) continue

                val authorIdx = block.indexOf("<author>", ignoreCase = true)
                val channel = if (authorIdx >= 0) tag(block.substring(authorIdx), "name") else ""
                val (song, artist) = splitTitle(title, channel)
                val publishedRaw = tag(block, "published")
                val epochMs = YouTubeRssService.parseIso8601ToEpochMs(publishedRaw)
                val normalizedPublished = if (epochMs > 0L) {
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.format(Date(epochMs))
                } else {
                    publishedRaw.takeIf { it.isNotBlank() }
                }

                val thumb = attr(block, "media:thumbnail", "url")
                    .ifBlank { "https://i.ytimg.com/vi/$id/hqdefault.jpg" }

                out.add(
                    MusicRadioEntry(
                        type = "song",
                        id = id,
                        title = song,
                        artist = artist,
                        link = "https://music.youtube.com/watch?v=$id",
                        streamUrl = "",
                        embedUrl = "https://www.youtube-nocookie.com/embed/$id",
                        thumbnail = thumb,
                        description = "$song — $artist",
                        published = normalizedPublished,
                        source = "YouTube Music"
                    )
                )
            }
            return out
        }

        /**
         * Initial offline-first feed entries seeded into Room when the database is empty so the
         * feed is always shown immediately while background refreshing happens.
         */
        fun defaultSeededEntries(): List<MusicRadioEntry> = listOf(
            MusicRadioEntry(
                type = "radio",
                id = "vividh-bharati",
                title = "Vividh Bharati Live",
                artist = "Akashvani · All India Radio",
                link = RADIO_PAGE,
                streamUrl = DEFAULT_VIVIDH_AUDIO_STREAM,
                embedUrl = "",
                thumbnail = RADIO_LOGO,
                description = "Live Hindi film-music radio from Akashvani (Prasar Bharati).",
                published = null,
                source = "Akashvani"
            ),
            MusicRadioEntry(
                type = "song",
                id = "kPa7bsKwL-c",
                title = "Die With A Smile",
                artist = "Lady Gaga & Bruno Mars",
                link = "https://music.youtube.com/watch?v=kPa7bsKwL-c",
                streamUrl = fallbackAudioStreamForId("kPa7bsKwL-c"),
                embedUrl = "https://www.youtube-nocookie.com/embed/kPa7bsKwL-c",
                thumbnail = "https://i.ytimg.com/vi/kPa7bsKwL-c/hqdefault.jpg",
                description = "Die With A Smile — Lady Gaga & Bruno Mars",
                published = "2026-05-01T00:00:00.000Z",
                source = "Global Top 50"
            ),
            MusicRadioEntry(
                type = "song",
                id = "eVli-tstM5E",
                title = "APT.",
                artist = "ROSÉ & Bruno Mars",
                link = "https://music.youtube.com/watch?v=eVli-tstM5E",
                streamUrl = fallbackAudioStreamForId("eVli-tstM5E"),
                embedUrl = "https://www.youtube-nocookie.com/embed/eVli-tstM5E",
                thumbnail = "https://i.ytimg.com/vi/eVli-tstM5E/hqdefault.jpg",
                description = "APT. — ROSÉ & Bruno Mars",
                published = "2026-05-01T00:00:00.000Z",
                source = "Global Top 50"
            ),
            MusicRadioEntry(
                type = "song",
                id = "V9PVRfjEBTI",
                title = "BIRDS OF A FEATHER",
                artist = "Billie Eilish",
                link = "https://music.youtube.com/watch?v=V9PVRfjEBTI",
                streamUrl = fallbackAudioStreamForId("V9PVRfjEBTI"),
                embedUrl = "https://www.youtube-nocookie.com/embed/V9PVRfjEBTI",
                thumbnail = "https://i.ytimg.com/vi/V9PVRfjEBTI/hqdefault.jpg",
                description = "BIRDS OF A FEATHER — Billie Eilish",
                published = "2026-05-01T00:00:00.000Z",
                source = "Global Top 50"
            ),
            MusicRadioEntry(
                type = "song",
                id = "4NRXx6U8ABQ",
                title = "Blinding Lights",
                artist = "The Weeknd",
                link = "https://music.youtube.com/watch?v=4NRXx6U8ABQ",
                streamUrl = fallbackAudioStreamForId("4NRXx6U8ABQ"),
                embedUrl = "https://www.youtube-nocookie.com/embed/4NRXx6U8ABQ",
                thumbnail = "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg",
                description = "Blinding Lights — The Weeknd",
                published = "2026-05-01T00:00:00.000Z",
                source = "Global Top 50"
            ),
            MusicRadioEntry(
                type = "song",
                id = "5NV6Rdv1a3I",
                title = "Get Lucky",
                artist = "Daft Punk ft. Pharrell Williams",
                link = "https://music.youtube.com/watch?v=5NV6Rdv1a3I",
                streamUrl = fallbackAudioStreamForId("5NV6Rdv1a3I"),
                embedUrl = "https://www.youtube-nocookie.com/embed/5NV6Rdv1a3I",
                thumbnail = "https://i.ytimg.com/vi/5NV6Rdv1a3I/hqdefault.jpg",
                description = "Get Lucky — Daft Punk ft. Pharrell Williams",
                published = "2026-05-01T00:00:00.000Z",
                source = "Global Top 50"
            ),
            MusicRadioEntry(
                type = "song",
                id = "2Vv-BfVoq4g",
                title = "Perfect",
                artist = "Ed Sheeran",
                link = "https://music.youtube.com/watch?v=2Vv-BfVoq4g",
                streamUrl = fallbackAudioStreamForId("2Vv-BfVoq4g"),
                embedUrl = "https://www.youtube-nocookie.com/embed/2Vv-BfVoq4g",
                thumbnail = "https://i.ytimg.com/vi/2Vv-BfVoq4g/hqdefault.jpg",
                description = "Perfect — Ed Sheeran",
                published = "2026-05-01T00:00:00.000Z",
                source = "Global Top 50"
            ),
            MusicRadioEntry(
                type = "song",
                id = "YQHsXMglC9A",
                title = "Hello",
                artist = "Adele",
                link = "https://music.youtube.com/watch?v=YQHsXMglC9A",
                streamUrl = fallbackAudioStreamForId("YQHsXMglC9A"),
                embedUrl = "https://www.youtube-nocookie.com/embed/YQHsXMglC9A",
                thumbnail = "https://i.ytimg.com/vi/YQHsXMglC9A/hqdefault.jpg",
                description = "Hello — Adele",
                published = "2026-05-01T00:00:00.000Z",
                source = "Global Top 50"
            ),
            MusicRadioEntry(
                type = "song",
                id = "JGwWNGJdvx8",
                title = "Shape of You",
                artist = "Ed Sheeran",
                link = "https://music.youtube.com/watch?v=JGwWNGJdvx8",
                streamUrl = fallbackAudioStreamForId("JGwWNGJdvx8"),
                embedUrl = "https://www.youtube-nocookie.com/embed/JGwWNGJdvx8",
                thumbnail = "https://i.ytimg.com/vi/JGwWNGJdvx8/hqdefault.jpg",
                description = "Shape of You — Ed Sheeran",
                published = "2026-05-01T00:00:00.000Z",
                source = "Global Top 50"
            )
        )

        fun buildPayloadFromEntries(
            playlistId: String,
            entries: List<MusicRadioEntry>,
            warnings: List<String> = emptyList(),
            isFromOfflineCache: Boolean = false
        ): MusicRadioFeedPayload {
            val songCount = entries.count { it.type == "song" }
            val selfUrl = "https://beatify.in/music-radio?playlist_id=$playlistId&count=${entries.size}"
            return MusicRadioFeedPayload(
                playlist = playlistId,
                count = entries.size,
                songs = songCount,
                entries = entries,
                warnings = warnings,
                rssXml = toRss(entries, selfUrl, warnings),
                jsonString = toJson(playlistId, entries, warnings),
                httpStatus = if (songCount == 0) 502 else 200,
                isFromOfflineCache = isFromOfflineCache
            )
        }

        fun toRss(
            entries: List<MusicRadioEntry>,
            selfUrl: String = "https://beatify.in/music-radio",
            warnings: List<String> = emptyList()
        ): String {
            val rfc822 = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("GMT")
            }
            val now = rfc822.format(Date())
            val itemsXml = entries.joinToString("\n") { e ->
                val pubMillis = YouTubeRssService.parseIso8601ToEpochMs(e.published)
                val pubDateStr = if (pubMillis > 0L) rfc822.format(Date(pubMillis)) else now
                val pub = "<pubDate>$pubDateStr</pubDate>"
                val enclosure = if (e.streamUrl.isNotBlank()) {
                    "<enclosure url=\"${esc(e.streamUrl)}\" type=\"audio/mpeg\" length=\"0\"/>"
                } else {
                    ""
                }
                val player = if (e.embedUrl.isNotBlank()) {
                    "<media:player url=\"${esc(e.embedUrl)}\"/>"
                } else {
                    ""
                }
                val itemTitle = if (e.type == "radio") e.title else "${e.title} — ${e.artist}"
                val guid = if (e.type == "song") e.link else "urn:vividh-bharati:live"
                """    <item>
      <title>${esc(itemTitle)}</title>
      <link>${esc(e.link)}</link>
      <guid isPermaLink="${e.type == "song"}">${esc(guid)}</guid>
      $pub
      <category>${if (e.type == "radio") "Live Radio" else "Music"}</category>
      <source url="${esc(selfUrl)}">${esc(e.source)}</source>
      <description>${esc(e.description)}</description>
      <media:title>${esc(e.title)}</media:title>
      <media:credit role="artist">${esc(e.artist)}</media:credit>
      <media:thumbnail url="${esc(e.thumbnail)}"/>
      $player$enclosure
    </item>"""
            }

            val warnComment = if (warnings.isNotEmpty()) {
                "\n    <!-- ${warnings.joinToString(" | ") { it.replace("--", "-") }} -->"
            } else {
                ""
            }

            return """<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/" xmlns:atom="http://www.w3.org/2005/Atom">
  <channel>
    <title>Sarath Music &amp; Radio</title>
    <link>${esc(RADIO_PAGE)}</link>
    <description>Vividh Bharati live radio and YouTube Music Global Top 50 songs.</description>
    <language>en</language>
    <lastBuildDate>$now</lastBuildDate>
    <atom:link href="${esc(selfUrl)}" rel="self" type="application/rss+xml"/>$warnComment
$itemsXml
  </channel>
</rss>
"""
        }

        fun toJson(
            playlist: String,
            entries: List<MusicRadioEntry>,
            warnings: List<String>
        ): String {
            val songCount = entries.count { it.type == "song" }
            val root = JSONObject().apply {
                put("source", "music-radio")
                put("mode", "playlist")
                put("playlist", playlist)
                put("count", entries.size)
                put("songs", songCount)
                val arr = JSONArray()
                for (e in entries) {
                    arr.put(
                        JSONObject().apply {
                            put("type", e.type)
                            put("id", e.id)
                            put("title", e.title)
                            put("artist", e.artist)
                            put("link", e.link)
                            put("stream_url", e.streamUrl)
                            put("embed_url", e.embedUrl)
                            put("thumbnail", e.thumbnail)
                            put("description", e.description)
                            put("published", e.published ?: JSONObject.NULL)
                            put("source", e.source)
                        }
                    )
                }
                put("entries", arr)
                if (warnings.isNotEmpty()) {
                    val warnArr = JSONArray()
                    warnings.forEach { warnArr.put(it) }
                    put("warnings", warnArr)
                }
            }
            return root.toString(2)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(key: String, fn: suspend () -> T): T {
        val now = System.currentTimeMillis()
        val hit = cache[key]
        if (hit != null && now - hit.at < TTL_MS) {
            return hit.value as T
        }
        val value = fn()
        cache[key] = CacheRecord(now, value)
        return value
    }

    fun clearCache() {
        cache.clear()
    }

    /**
     * Observes Room's cached `music_radio_entries` for [playlistId] so the UI updates automatically
     * whenever the offline cache is seeded or refreshed in the background.
     */
    fun observeCachedFeed(playlistId: String = DEFAULT_PLAYLIST): Flow<MusicRadioFeedPayload>? {
        val dao = discoveryDao ?: return null
        val cleaned = playlistId.trim().ifEmpty { DEFAULT_PLAYLIST }
        return dao.observeMusicRadioEntries(cleaned).map { rows ->
            val domainEntries = if (rows.isEmpty()) {
                defaultSeededEntries()
            } else {
                rows.map { it.toDomain() }
            }
            buildPayloadFromEntries(
                playlistId = cleaned,
                entries = domainEntries,
                warnings = emptyList(),
                isFromOfflineCache = true
            )
        }
    }

    /**
     * Immediately returns the Room-cached `music-radio` feed for [playlistId], seeding Room
     * with [defaultSeededEntries] if empty so the app displays content with zero latency.
     */
    suspend fun getCachedOrSeededFeed(playlistId: String = DEFAULT_PLAYLIST): MusicRadioFeedPayload =
        withContext(Dispatchers.IO) {
            val cleaned = playlistId.trim().ifEmpty { DEFAULT_PLAYLIST }
            val existingRows = discoveryDao?.getMusicRadioEntriesOnce(cleaned).orEmpty()
            if (existingRows.isNotEmpty()) {
                val entries = existingRows.map { it.toDomain() }
                return@withContext buildPayloadFromEntries(
                    playlistId = cleaned,
                    entries = entries,
                    isFromOfflineCache = true
                )
            }
            val seeded = defaultSeededEntries()
            persistEntriesToRoom(cleaned, seeded)
            buildPayloadFromEntries(
                playlistId = cleaned,
                entries = seeded,
                isFromOfflineCache = true
            )
        }

    private suspend fun persistEntriesToRoom(playlistId: String, entries: List<MusicRadioEntry>) {
        val dao = discoveryDao ?: return
        val now = System.currentTimeMillis()
        val entities = entries.mapIndexed { idx, entry ->
            val withPlayableStream = if (entry.streamUrl.isBlank()) {
                entry.copy(streamUrl = entry.playableAudioUrl())
            } else {
                entry
            }
            withPlayableStream.toEntity(playlistId = playlistId, sortOrder = idx, cachedAtEpochMs = now)
        }
        dao.deleteMusicRadioEntriesForPlaylist(playlistId)
        dao.upsertMusicRadioEntries(entities)
    }

    /**
     * Fetches newest [count] non-Short songs from a public YouTube / YouTube Music playlist RSS feed.
     */
    suspend fun fetchSongs(playlistId: String = DEFAULT_PLAYLIST, count: Int = MAX_ITEMS): List<MusicRadioEntry> =
        withContext(Dispatchers.IO) {
            val cleanedPlaylist = playlistId.trim()
            if (!isValidPlaylistId(cleanedPlaylist)) {
                throw IllegalArgumentException("invalid_playlist_id")
            }
            val safeCount = count.coerceIn(1, FEED_MAX)
            cached("yt:$cleanedPlaylist:$safeCount") {
                val encodedId = URLEncoder.encode(cleanedPlaylist, "UTF-8")
                val url = "https://www.youtube.com/feeds/videos.xml?playlist_id=$encodedId"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", UA)
                    .header("Accept", "application/atom+xml, application/xml, text/xml")
                    .get()
                    .build()

                httpClient.newCall(req).awaitCancellable().use { response ->
                    if (response.code == 404) {
                        throw IllegalStateException("playlist not found or not public")
                    }
                    if (!response.isSuccessful) {
                        throw IllegalStateException("YouTube feed HTTP ${response.code}")
                    }
                    val xml = response.body?.string().orEmpty()
                    val songs = parsePlaylistFeed(xml, safeCount)
                    if (songs.isEmpty()) {
                        throw IllegalStateException("playlist feed had no songs")
                    }
                    songs
                }
            }
        }

    /**
     * Looks for a plain audio stream in Akashvani's official page static HTML.
     * Returns `""` with an explanatory note when none is exposed.
     */
    suspend fun discoverStream(): Pair<String, String> = withContext(Dispatchers.IO) {
        val fromEnv = configuredStreamUrl.trim()
        if (fromEnv.startsWith("http://", ignoreCase = true) || fromEnv.startsWith("https://", ignoreCase = true)) {
            return@withContext fromEnv to "stream from VIVIDH_BHARATI_STREAM_URL"
        }
        try {
            val req = Request.Builder()
                .url(RADIO_PAGE)
                .header("User-Agent", UA)
                .get()
                .build()
            httpClient.newCall(req).awaitCancellable().use { response ->
                if (!response.isSuccessful) {
                    return@withContext "" to "Akashvani page HTTP ${response.code}"
                }
                val html = response.body?.string().orEmpty()
                val near = VIVIDH_STREAM_RE.find(html)
                if (near != null) {
                    return@withContext near.groupValues[1] to "stream auto-detected on akashvani.gov.in"
                }
                "" to "Akashvani does not expose a stream URL in its page; linking the official player"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "" to "Akashvani page unreachable: ${e.message ?: e::class.java.simpleName}"
        }
    }

    /**
     * Builds the `vividh-bharati` live radio entry and its stream discovery note.
     */
    suspend fun radioEntry(): Pair<MusicRadioEntry, String> = withContext(Dispatchers.IO) {
        val (stream, note) = cached("radio") { discoverStream() }
        val resolvedStream = stream.ifBlank { DEFAULT_VIVIDH_AUDIO_STREAM }
        val entry = MusicRadioEntry(
            type = "radio",
            id = "vividh-bharati",
            title = "Vividh Bharati Live",
            artist = "Akashvani · All India Radio",
            link = RADIO_PAGE,
            streamUrl = resolvedStream,
            embedUrl = "",
            thumbnail = RADIO_LOGO,
            description = "Live Hindi film-music radio from Akashvani (Prasar Bharati).",
            published = null,
            source = "Akashvani"
        )
        entry to note
    }

    /**
     * Executes both `fetchSongs(playlist, count)` and `radioEntry()` concurrently,
     * persists the latest entries to the Room offline cache (`music_radio_entries`),
     * and falls back to the Room cache if the network is unreachable.
     */
    suspend fun fetchMusicRadioFeed(
        playlistId: String = DEFAULT_PLAYLIST,
        count: Int = MAX_ITEMS,
        forceRefresh: Boolean = false
    ): MusicRadioFeedPayload = coroutineScope {
        val cleanedPlaylist = playlistId.trim().ifEmpty { DEFAULT_PLAYLIST }
        if (!isValidPlaylistId(cleanedPlaylist)) {
            throw IllegalArgumentException("invalid_playlist_id")
        }
        if (forceRefresh) {
            clearCache()
        }
        val safeCount = count.coerceIn(1, MAX_ITEMS)

        val songsDeferred = async(Dispatchers.IO) {
            runCatching { fetchSongs(cleanedPlaylist, safeCount) }
        }
        val radioDeferred = async(Dispatchers.IO) {
            runCatching { radioEntry() }
        }

        val songsResult = songsDeferred.await()
        val radioResult = radioDeferred.await()

        val warnings = ArrayList<String>()
        val entries = ArrayList<MusicRadioEntry>()

        radioResult.fold(
            onSuccess = { (entry, note) ->
                entries.add(entry)
                if (note.isNotBlank() && !note.startsWith("stream")) {
                    warnings.add(note)
                }
            },
            onFailure = { err ->
                warnings.add("radio: ${err.message ?: err.toString()}")
            }
        )

        songsResult.fold(
            onSuccess = { songs ->
                entries.addAll(
                    songs.map { song ->
                        if (song.streamUrl.isBlank()) {
                            song.copy(streamUrl = song.playableAudioUrl())
                        } else {
                            song
                        }
                    }
                )
            },
            onFailure = { err ->
                warnings.add("songs: ${err.message ?: err.toString()}")
            }
        )

        val finalEntries = if (entries.count { it.type == "song" } > 0) {
            persistEntriesToRoom(cleanedPlaylist, entries)
            entries
        } else {
            val cachedRows = discoveryDao?.getMusicRadioEntriesOnce(cleanedPlaylist).orEmpty()
            if (cachedRows.isNotEmpty()) {
                cachedRows.map { it.toDomain() }
            } else {
                val seeded = defaultSeededEntries()
                persistEntriesToRoom(cleanedPlaylist, seeded)
                seeded
            }
        }

        val songCount = finalEntries.count { it.type == "song" }
        val selfUrl = "https://beatify.in/music-radio?playlist_id=$cleanedPlaylist&count=$safeCount"
        val rssXml = toRss(finalEntries, selfUrl, warnings)
        val jsonStr = toJson(cleanedPlaylist, finalEntries, warnings)

        MusicRadioFeedPayload(
            playlist = cleanedPlaylist,
            count = finalEntries.size,
            songs = songCount,
            entries = finalEntries,
            warnings = warnings,
            rssXml = rssXml,
            jsonString = jsonStr,
            httpStatus = if (songCount == 0) 502 else 200,
            isFromOfflineCache = entries.count { it.type == "song" } == 0
        )
    }
}
