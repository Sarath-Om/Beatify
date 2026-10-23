package com.example.services

import com.example.data.RadioStationItem
import com.example.data.TrackEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

/**
 * Authoritative Entry model for `music-radio` (Vividh Bharati Live + YouTube Music Playlist RSS).
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
    val source: String
) {
    fun toTrackEntity(): TrackEntity {
        val isRadio = type == "radio"
        val hasDirectStream = streamUrl.isNotBlank() && streamUrl.startsWith("http")
        val resolvedUri = if (hasDirectStream) streamUrl else link
        return TrackEntity(
            id = if (isRadio) "radio_$id" else "ytm_$id",
            title = title,
            artist = artist,
            album = if (isRadio) "Akashvani Live Radio" else "YouTube Music Playlist",
            durationMs = 0L,
            artworkUri = thumbnail,
            contentUri = resolvedUri,
            mimeType = "audio/mpeg",
            isFavorite = false,
            isLocalFile = false,
            isDemoTrack = false,
            isAvailable = hasDirectStream,
            accentColorHex = if (isRadio) 0xFFFA2D48 else 0xFF0A84FF,
            sourceType = if (isRadio) "local" else MusicSourceType.YOUTUBE_MUSIC.wireValue,
            playbackAvailability = if (hasDirectStream) {
                PlaybackAvailability.AVAILABLE.wireValue
            } else {
                PlaybackAvailability.EXTERNAL_ONLY.wireValue
            },
            externalLaunchUri = link
        )
    }

    fun toRadioStationItem(): RadioStationItem = RadioStationItem(
        id = id,
        name = title,
        tagline = "$artist • Live Hindi Film-Music Radio (Prasar Bharati)",
        genre = "Hindi Live",
        streamUrl = streamUrl.ifBlank { link },
        artworkUri = thumbnail,
        bitrateKbps = 128,
        accentColorHex = 0xFFFA2D48,
        isLive = true
    )
}

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
    val httpStatus: Int = if (songs == 0) 502 else 200
)

/**
 * Native Kotlin implementation of `supabase/functions/music-radio/index.ts`
 * (music playlist + live radio RSS, keyless).
 *
 * Capabilities:
 * - `Vividh Bharati Live` (Akashvani · All India Radio) with static HTML stream auto-detection,
 *   official player link (`https://akashvani.gov.in/radio/live.php`), EPG schedule
 *   (`https://cuesheets.prasarbharati.org/viewsheet/133`), and programme highlights
 *   (`https://cuesheets.prasarbharati.org/highlights/133`).
 * - `YouTube Music: Global Top 50 Songs` (`PL4fGSI1pDJn5kI81J1fYWK5eZRl1zVM5v`) or any validated
 *   public YouTube / YouTube Music playlist (`^(PL|OLAK5uy_|RD|UU|FL|LL)[A-Za-z0-9_-]{10,60}$`),
 *   returning up to 15 newest non-Short songs.
 * - Strict YouTube Shorts exclusion (`/shorts/` links or `#shorts` / `#short` in title/description).
 * - Artist & Song title splitting (`"Artist - Song"` or fallback to cleaned `- Topic` / `VEVO` channel).
 * - 5-minute TTL in-memory cache (`TTL_MS = 5 * 60 * 1000L`) and honest source failure warnings.
 */
class MusicRadioRssService(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build(),
    private val configuredStreamUrl: String = ""
) {
    private data class CacheRecord(val at: Long, val value: Any)

    private val cache = ConcurrentHashMap<String, CacheRecord>()

    companion object {
        const val DEFAULT_PLAYLIST = "PL4fGSI1pDJn5kI81J1fYWK5eZRl1zVM5v" // YouTube Music: Global Top 50 Songs
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
        val entry = MusicRadioEntry(
            type = "radio",
            id = "vividh-bharati",
            title = "Vividh Bharati Live",
            artist = "Akashvani · All India Radio",
            link = RADIO_PAGE,
            streamUrl = stream,
            embedUrl = "",
            thumbnail = RADIO_LOGO,
            description = "Live Hindi film-music radio from Akashvani (Prasar Bharati). " +
                "Open the official player: $RADIO_PAGE · Today's programme schedule: $RADIO_EPG · Highlights: $RADIO_HIGHLIGHTS. " +
                "A live \"now playing\" title is not published by the broadcaster; see the schedule for what is on air.",
            published = null,
            source = "Akashvani"
        )
        entry to note
    }

    /**
     * Executes both `fetchSongs(playlist, count)` and `radioEntry()` concurrently (`Promise.allSettled` equivalent),
     * returning the unified [MusicRadioFeedPayload] with RSS 2.0 XML, JSON, entries, and warnings.
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
                if (entry.streamUrl.isBlank()) {
                    warnings.add(note)
                }
            },
            onFailure = { err ->
                warnings.add("radio: ${err.message ?: err.toString()}")
            }
        )

        songsResult.fold(
            onSuccess = { songs ->
                entries.addAll(songs)
            },
            onFailure = { err ->
                warnings.add("songs: ${err.message ?: err.toString()}")
            }
        )

        val songCount = entries.count { it.type == "song" }
        val selfUrl = "https://beatify.in/music-radio?playlist_id=$cleanedPlaylist&count=$safeCount"
        val rssXml = toRss(entries, selfUrl, warnings)
        val jsonStr = toJson(cleanedPlaylist, entries, warnings)

        MusicRadioFeedPayload(
            playlist = cleanedPlaylist,
            count = entries.size,
            songs = songCount,
            entries = entries,
            warnings = warnings,
            rssXml = rssXml,
            jsonString = jsonStr,
            httpStatus = if (songCount == 0) 502 else 200
        )
    }
}
