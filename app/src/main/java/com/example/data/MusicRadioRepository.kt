package com.example.data

import com.example.services.MusicRadioEntry
import com.example.services.MusicRadioFeedPayload
import com.example.services.MusicRadioRssService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Single-Source-of-Truth Repository for the `music-radio` feed.
 *
 * Responsibilities:
 * - Exposes reactive Room flows (`musicEntries`, `observeEntries`, `observeFeedPayload`) from
 *   [MusicEntryDao] so the UI **always** reads from the local Room database (`music_entries`).
 * - Seeds the Room database on first launch if empty so the UI displays content immediately.
 * - Fetches the network feed (`MusicRadioRssService` or `music-radio` JSON) in the background and
 *   updates the local Room database (`music_entries` and `music_radio_entries`), automatically
 *   emitting fresh items to the UI via Room's reactive `Flow`.
 */
class MusicRadioRepository(
    private val musicEntryDao: MusicEntryDao,
    private val discoveryDao: YouTubeDiscoveryDao? = null,
    private val rssService: MusicRadioRssService = MusicRadioRssService(discoveryDao = discoveryDao)
) {
    /**
     * Reactive stream of all cached [MusicEntry] items in the local Room database,
     * ordered with live radio first followed by playlist songs.
     */
    val musicEntries: Flow<List<MusicEntry>> = musicEntryDao.observeAllEntries()

    /**
     * Reactive stream of user-favorited [MusicEntry] items in the local Room database (`favorite = 1`).
     */
    val favoriteEntries: Flow<List<MusicEntry>> = musicEntryDao.observeFavoriteEntries()

    /**
     * Observes cached [MusicEntry] rows for a specific [playlistId] directly from the local Room database.
     */
    fun observeEntries(playlistId: String = MusicRadioRssService.DEFAULT_PLAYLIST): Flow<List<MusicEntry>> {
        val cleaned = playlistId.trim().ifEmpty { MusicRadioRssService.DEFAULT_PLAYLIST }
        return musicEntryDao.observeEntriesByPlaylist(cleaned)
    }

    /**
     * Observes the [MusicRadioFeedPayload] derived strictly from the local Room database (`music_entries`),
     * ensuring the UI always reads from local persistence while background network refreshes update Room.
     */
    fun observeFeedPayload(playlistId: String = MusicRadioRssService.DEFAULT_PLAYLIST): Flow<MusicRadioFeedPayload> {
        val cleaned = playlistId.trim().ifEmpty { MusicRadioRssService.DEFAULT_PLAYLIST }
        return musicEntryDao.observeEntriesByPlaylist(cleaned).map { rows ->
            val domainEntries = if (rows.isNotEmpty()) {
                rows.map { it.toMusicRadioEntry() }
            } else {
                MusicRadioRssService.defaultSeededEntries()
            }
            MusicRadioRssService.buildPayloadFromEntries(
                playlistId = cleaned,
                entries = domainEntries,
                isFromOfflineCache = true
            )
        }
    }

    /**
     * Ensures the local Room database (`music_entries`) has initial entries for [playlistId]
     * so the UI immediately reads cached entries on launch before network sync completes.
     */
    suspend fun ensureSeeded(playlistId: String = MusicRadioRssService.DEFAULT_PLAYLIST): List<MusicEntry> =
        withContext(Dispatchers.IO) {
            val cleaned = playlistId.trim().ifEmpty { MusicRadioRssService.DEFAULT_PLAYLIST }
            val existing = musicEntryDao.getEntriesByPlaylistOnce(cleaned)
            if (existing.isNotEmpty()) {
                return@withContext existing
            }
            val now = System.currentTimeMillis()
            val seededDomain = rssService.getCachedOrSeededFeed(cleaned).entries
            val seededEntities = seededDomain.mapIndexed { idx, entry ->
                entry.toMusicEntry(playlistId = cleaned, sortOrder = idx, cachedAtEpochMs = now)
            }
            musicEntryDao.upsertEntries(seededEntities)
            seededEntities
        }

    /**
     * Fetches the latest `music-radio` feed from the network and updates the local Room database,
     * preserving any user-saved `favorite` flags across refreshes.
     */
    suspend fun refreshFeed(
        playlistId: String = MusicRadioRssService.DEFAULT_PLAYLIST,
        count: Int = MusicRadioRssService.MAX_ITEMS,
        forceRefresh: Boolean = true
    ): Result<List<MusicEntry>> = withContext(Dispatchers.IO) {
        val cleaned = playlistId.trim().ifEmpty { MusicRadioRssService.DEFAULT_PLAYLIST }
        runCatching {
            val existingById = musicEntryDao.getAllEntriesOnce().associateBy { it.id }
            val networkPayload = rssService.fetchMusicRadioFeed(
                playlistId = cleaned,
                count = count,
                forceRefresh = forceRefresh
            )
            val now = System.currentTimeMillis()
            val entities = networkPayload.entries.mapIndexed { index, entry ->
                val wasFavorite = existingById[entry.id]?.favorite ?: entry.favorite
                entry.toMusicEntry(
                    playlistId = cleaned,
                    sortOrder = index,
                    cachedAtEpochMs = now,
                    favorite = wasFavorite
                )
            }
            if (entities.isNotEmpty()) {
                musicEntryDao.deleteEntriesForPlaylist(cleaned)
                musicEntryDao.upsertEntries(entities)
            }
            musicEntryDao.getEntriesByPlaylistOnce(cleaned)
        }
    }

    /**
     * Parses a `music-radio` JSON response (`{"playlist": "...", "entries": [...]}`) into [MusicEntry]
     * entities and updates the local Room database (`music_entries`), preserving existing `favorite` flags.
     */
    suspend fun syncFromJson(
        jsonString: String,
        fallbackPlaylistId: String = MusicRadioRssService.DEFAULT_PLAYLIST
    ): List<MusicEntry> = withContext(Dispatchers.IO) {
        val existingById = musicEntryDao.getAllEntriesOnce().associateBy { it.id }
        val parsed = parseMusicEntriesFromJson(jsonString, fallbackPlaylistId).map { entry ->
            val wasFavorite = existingById[entry.id]?.favorite ?: entry.favorite
            entry.copy(favorite = wasFavorite)
        }
        if (parsed.isNotEmpty()) {
            val targetPlaylist = parsed.first().playlistId
            musicEntryDao.deleteEntriesForPlaylist(targetPlaylist)
            musicEntryDao.upsertEntries(parsed)
        }
        parsed
    }

    /**
     * Toggles the `favorite` status for a [MusicEntry] in the Room database (`music_entries`)
     * and returns the new `favorite` boolean state.
     */
    suspend fun toggleFavorite(entryId: String): Boolean = withContext(Dispatchers.IO) {
        val existing = musicEntryDao.getEntryById(entryId)
        val nextFavorite = !(existing?.favorite ?: false)
        if (existing != null) {
            musicEntryDao.setFavorite(entryId, nextFavorite)
        }
        discoveryDao?.setMusicRadioEntryFavorite(entryId, nextFavorite)
        nextFavorite
    }

    /**
     * Explicitly sets the `favorite` status for a [MusicEntry] in the Room database (`music_entries`).
     */
    suspend fun setFavorite(entryId: String, favorite: Boolean) = withContext(Dispatchers.IO) {
        musicEntryDao.setFavorite(entryId, favorite)
        discoveryDao?.setMusicRadioEntryFavorite(entryId, favorite)
    }

    /**
     * Updates the resolved playable audio `stream_url` for a cached [MusicEntry] in Room.
     */
    suspend fun updateResolvedStreamUrl(entryId: String, streamUrl: String) = withContext(Dispatchers.IO) {
        if (streamUrl.isNotBlank()) {
            musicEntryDao.updateStreamUrl(entryId, streamUrl)
            discoveryDao?.updateMusicRadioEntryStreamUrl(entryId, streamUrl)
        }
    }

    companion object {
        fun parseMusicEntriesFromJson(
            jsonString: String,
            fallbackPlaylistId: String = MusicRadioRssService.DEFAULT_PLAYLIST
        ): List<MusicEntry> {
            if (jsonString.isBlank()) return emptyList()
            val root = JSONObject(jsonString)
            val playlistId = root.optString("playlist", fallbackPlaylistId).ifBlank { fallbackPlaylistId }
            val entriesArray = root.optJSONArray("entries") ?: return emptyList()
            val now = System.currentTimeMillis()
            val result = ArrayList<MusicEntry>(entriesArray.length())
            for (i in 0 until entriesArray.length()) {
                val obj = entriesArray.optJSONObject(i) ?: continue
                val id = obj.optString("id").trim()
                val title = obj.optString("title").trim()
                if (id.isEmpty() || title.isEmpty()) continue
                val type = obj.optString("type", "song").trim().ifEmpty { "song" }
                val rawStream = obj.optString("stream_url", "").trim()
                val resolvedStream = rawStream.ifBlank {
                    MusicRadioRssService.fallbackAudioStreamForId(id, isRadio = type == "radio")
                }
                val publishedVal = if (obj.isNull("published")) null else obj.optString("published").takeIf { it.isNotBlank() }
                result.add(
                    MusicEntry(
                        id = id,
                        type = type,
                        title = title,
                        artist = obj.optString("artist", "Unknown Artist"),
                        link = obj.optString("link", ""),
                        streamUrl = resolvedStream,
                        embedUrl = obj.optString("embed_url", ""),
                        thumbnail = obj.optString("thumbnail", ""),
                        description = obj.optString("description", "$title — ${obj.optString("artist")}"),
                        published = publishedVal,
                        source = obj.optString("source", if (type == "radio") "Akashvani" else "YouTube Music"),
                        favorite = obj.optBoolean("favorite", false),
                        playlistId = playlistId,
                        sortOrder = i,
                        cachedAtEpochMs = now
                    )
                )
            }
            return result
        }
    }
}

fun MusicEntry.toMusicRadioEntry(): MusicRadioEntry = MusicRadioEntry(
    type = type,
    id = id,
    title = title,
    artist = artist,
    link = link,
    streamUrl = playableAudioUrl(),
    embedUrl = embedUrl,
    thumbnail = thumbnail,
    description = description,
    published = published,
    source = source,
    favorite = favorite
)

fun MusicRadioEntry.toMusicEntry(
    playlistId: String = MusicRadioRssService.DEFAULT_PLAYLIST,
    sortOrder: Int = 0,
    cachedAtEpochMs: Long = System.currentTimeMillis(),
    favorite: Boolean = this.favorite
): MusicEntry = MusicEntry(
    id = id,
    type = type,
    title = title,
    artist = artist,
    link = link,
    streamUrl = playableAudioUrl(),
    embedUrl = embedUrl,
    thumbnail = thumbnail,
    description = description,
    published = published,
    source = source,
    favorite = favorite,
    playlistId = playlistId,
    sortOrder = sortOrder,
    cachedAtEpochMs = cachedAtEpochMs
)
