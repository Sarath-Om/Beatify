package com.example.services

import com.example.BuildConfig
import com.example.data.TrackEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Source type for search and library tracks (Section 2).
 */
enum class MusicSourceType(val wireValue: String, val displayLabel: String) {
    YOUTUBE_MUSIC("youtube-music", "YouTube Music"),
    LOCAL("local", "Local")
}

/**
 * Track playback availability state (Section 2 & 3).
 * Metadata being searchable does not guarantee a direct playable stream inside ExoPlayer.
 */
enum class PlaybackAvailability(val wireValue: String, val displayLabel: String) {
    AVAILABLE("available", "Stream Ready"),
    EXTERNAL_ONLY("external-only", "YouTube Music App"),
    UNAVAILABLE("unavailable", "Unavailable")
}

/**
 * Validated artist reference inside a track search result.
 */
data class MusicArtistRef(
    val id: String? = null,
    val name: String
)

/**
 * Validated album reference inside a track search result.
 */
data class MusicAlbumRef(
    val id: String? = null,
    val name: String
)

/**
 * Authoritative `MusicSearchResult` contract (Section 2).
 */
data class MusicSearchResult(
    val id: String,
    val title: String,
    val artists: List<MusicArtistRef>,
    val album: MusicAlbumRef? = null,
    val artworkUrl: String? = null,
    val durationMs: Long? = null,
    val source: MusicSourceType = MusicSourceType.YOUTUBE_MUSIC,
    val playbackAvailability: PlaybackAvailability = PlaybackAvailability.AVAILABLE,
    val playableStreamUrl: String? = null,
    val mimeType: String = "audio/mp4",
    val externalUrl: String? = null
) {
    val primaryArtistName: String
        get() = artists.joinToString(", ") { it.name }.ifBlank { "Unknown Artist" }

    fun toTrackEntity(): TrackEntity {
        val palette = listOf(0xFFFA2D48, 0xFF0A84FF, 0xFF7C5CFF, 0xFF30D158, 0xFF00E5FF)
        val accent = palette[(title.hashCode() and 0x7FFFFFFF) % palette.size]
        val resolvedUri = playableStreamUrl?.takeIf { it.isNotBlank() }
            ?: externalUrl
            ?: "https://music.youtube.com/watch?v=${id.removePrefix("ytm_")}"
        return TrackEntity(
            id = id,
            title = title,
            artist = primaryArtistName,
            album = album?.name,
            durationMs = durationMs ?: 0L,
            artworkUri = artworkUrl,
            contentUri = resolvedUri,
            mimeType = mimeType,
            isFavorite = false,
            isLocalFile = source == MusicSourceType.LOCAL,
            isDemoTrack = false,
            isAvailable = playbackAvailability == PlaybackAvailability.AVAILABLE,
            sourceType = source.wireValue,
            playbackAvailability = playbackAvailability.wireValue,
            externalLaunchUri = externalUrl ?: "https://music.youtube.com/watch?v=${id.removePrefix("ytm_")}",
            accentColorHex = accent
        )
    }
}

/**
 * Validated Artist search result in `MusicSearchProvider.searchArtists`.
 */
data class MusicArtistSearchResult(
    val id: String,
    val name: String,
    val subscriberCountText: String? = null,
    val artworkUrl: String? = null,
    val browseEndpointUrl: String = "https://music.youtube.com/channel/$id"
)

/**
 * Validated Album search result in `MusicSearchProvider.searchAlbums`.
 */
data class MusicAlbumSearchResult(
    val id: String,
    val title: String,
    val artistName: String,
    val year: String? = null,
    val artworkUrl: String? = null,
    val browseEndpointUrl: String = "https://music.youtube.com/browse/$id"
)

/**
 * Combined search payload returned by debounced search operations.
 */
data class MusicSearchBatchResult(
    val query: String,
    val tracks: List<MusicSearchResult>,
    val artists: List<MusicArtistSearchResult>,
    val albums: List<MusicAlbumSearchResult>
) {
    val isEmpty: Boolean
        get() = tracks.isEmpty() && artists.isEmpty() && albums.isEmpty()
}

/**
 * Resolved playback stream result from `MusicSearchProvider`.
 */
sealed class ResolvedPlaybackStream {
    data class DirectStream(
        val streamUrl: String,
        val mimeType: String,
        val durationMs: Long?,
        val artworkUrl: String?
    ) : ResolvedPlaybackStream()

    data class ExternalOnly(
        val externalUrl: String,
        val reason: String
    ) : ResolvedPlaybackStream()

    data class Unavailable(
        val reason: String
    ) : ResolvedPlaybackStream()
}

/**
 * Search provider exception for rate limits and network errors.
 */
class MusicProviderException(
    message: String,
    val isRateLimited: Boolean = false,
    val isNetworkError: Boolean = true,
    cause: Throwable? = null
) : IOException(message, cause)

/**
 * Authoritative `MusicSearchProvider` abstraction (Section 2).
 *
 * Defines the contract for querying real YouTube Music metadata (tracks, artists, albums),
 * resolving playable Media3 streams, and cancelling in-flight HTTP calls when a search query
 * is superseded or cancelled.
 */
interface MusicSearchProvider {
    val providerId: String
    val isMockProvider: Boolean
        get() = false

    suspend fun searchTracks(query: String): List<MusicSearchResult>
    suspend fun searchArtists(query: String): List<MusicArtistSearchResult>
    suspend fun searchAlbums(query: String): List<MusicAlbumSearchResult>
    suspend fun resolvePlaybackStream(result: MusicSearchResult): ResolvedPlaybackStream

    /**
     * Immediately aborts any in-flight network requests associated with active searches.
     */
    fun cancelInFlightRequests() {}
}

/**
 * Production guard preventing any development mock provider from being used in release builds (Section 5).
 */
object ProviderProductionGuard {
    fun validateProviderForEnvironment(
        provider: MusicSearchProvider,
        isReleaseBuild: Boolean = !BuildConfig.DEBUG
    ) {
        if (isReleaseBuild && provider.isMockProvider) {
            throw IllegalStateException(
                "Security Violation: Mock MusicSearchProvider (${provider.providerId}) is strictly prohibited in production release builds."
            )
        }
    }
}

/**
 * Coroutine-aware OkHttp extension that executes a [Call] asynchronously and guarantees
 * immediate socket/HTTP cancellation when the calling coroutine [Job] is cancelled.
 */
suspend fun Call.awaitCancellable(
    inFlightTracker: MutableSet<Call>? = null
): Response = suspendCancellableCoroutine { continuation ->
    inFlightTracker?.add(this)
    continuation.invokeOnCancellation {
        inFlightTracker?.remove(this)
        runCatching { cancel() }
    }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            inFlightTracker?.remove(call)
            if (!continuation.isActive) {
                response.close()
                return
            }
            continuation.resume(response)
        }

        override fun onFailure(call: Call, e: IOException) {
            inFlightTracker?.remove(call)
            if (continuation.isCancelled) return
            continuation.resumeWithException(e)
        }
    })
}

/**
 * Coordinates debounced YouTube Music metadata queries and deterministic cancellation of
 * stale coroutines and in-flight HTTP requests.
 *
 * Guarantees:
 * 1. Typing a new character or clearing the query immediately cancels any pending debounce timer,
 *    active coroutine [Job], and underlying OkHttp sockets via [MusicSearchProvider.cancelInFlightRequests].
 * 2. Monotonic request token validation prevents out-of-order responses from overwriting newer state.
 * 3. Concurrent `searchTracks`, `searchArtists`, and `searchAlbums` requests run inside a structured
 *    `coroutineScope` so any cancellation or fatal provider error cancels sibling requests immediately.
 */
class DebouncedMusicSearchCoordinator(
    private val provider: MusicSearchProvider,
    private val scope: CoroutineScope,
    private val debounceMs: Long = 350L,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val requestSequence = AtomicLong(0L)
    private var activeJob: Job? = null

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    /**
     * Submits a new query with debounce. Immediately cancels any previous pending or in-flight search.
     */
    fun submitQuery(
        rawQuery: String,
        skipDebounce: Boolean = false,
        onTypingOrCleared: (cleanedQuery: String) -> Unit = {},
        onLoadingStarted: (cleanedQuery: String) -> Unit = {},
        onSuccess: (MusicSearchBatchResult) -> Unit,
        onError: (MusicProviderException) -> Unit
    ) {
        val token = requestSequence.incrementAndGet()
        cancelActiveSearchInternal()

        val cleaned = rawQuery.trim()
        onTypingOrCleared(cleaned)
        if (cleaned.isEmpty()) {
            _isSearching.value = false
            return
        }

        activeJob = scope.launch(ioDispatcher) {
            try {
                if (!skipDebounce && debounceMs > 0L) {
                    delay(debounceMs)
                }
                coroutineContext.ensureActive()
                if (requestSequence.get() != token) return@launch

                _isSearching.value = true
                onLoadingStarted(cleaned)

                val batch = executeParallelSearch(cleaned)
                coroutineContext.ensureActive()
                if (requestSequence.get() != token) return@launch

                _isSearching.value = false
                onSuccess(batch)
            } catch (e: CancellationException) {
                if (requestSequence.get() == token) {
                    _isSearching.value = false
                }
                throw e
            } catch (e: MusicProviderException) {
                if (requestSequence.get() == token) {
                    _isSearching.value = false
                    onError(e)
                }
            } catch (e: Exception) {
                if (requestSequence.get() == token) {
                    _isSearching.value = false
                    onError(
                        MusicProviderException(
                            message = e.message ?: "Network error while querying YouTube Music.",
                            isRateLimited = false,
                            isNetworkError = true,
                            cause = e
                        )
                    )
                }
            }
        }
    }

    /**
     * Executes track, artist, and album searches concurrently with structured cancellation.
     */
    suspend fun executeParallelSearch(query: String): MusicSearchBatchResult = coroutineScope {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) {
            return@coroutineScope MusicSearchBatchResult(
                query = "",
                tracks = emptyList(),
                artists = emptyList(),
                albums = emptyList()
            )
        }
        val tracksDeferred = async(ioDispatcher) { provider.searchTracks(cleaned) }
        val artistsDeferred = async(ioDispatcher) { provider.searchArtists(cleaned) }
        val albumsDeferred = async(ioDispatcher) { provider.searchAlbums(cleaned) }

        MusicSearchBatchResult(
            query = cleaned,
            tracks = tracksDeferred.await(),
            artists = artistsDeferred.await(),
            albums = albumsDeferred.await()
        )
    }

    /**
     * Cancels any pending debounce timer, active coroutine job, and in-flight network calls.
     */
    fun cancelAll() {
        requestSequence.incrementAndGet()
        cancelActiveSearchInternal()
        _isSearching.value = false
    }

    private fun cancelActiveSearchInternal() {
        activeJob?.cancel()
        activeJob = null
        provider.cancelInFlightRequests()
    }
}
