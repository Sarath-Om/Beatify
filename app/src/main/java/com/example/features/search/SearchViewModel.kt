package com.example.features.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FeedSyncRunEntity
import com.example.data.SynapseDatabase
import com.example.data.YouTubeChannelEntity
import com.example.data.YouTubeVideoEntity
import com.example.services.DebouncedMusicSearchCoordinator
import com.example.services.MusicAlbumSearchResult
import com.example.services.MusicArtistSearchResult
import com.example.services.MusicDiscoveryApiService
import com.example.services.MusicRadioEntry
import com.example.services.MusicRadioRssService
import com.example.services.MusicSearchProvider
import com.example.services.MusicSearchResult
import com.example.services.YouTubeChannelCatalog
import com.example.services.YouTubeFeedSyncWorker
import com.example.services.YouTubeMusicInnerTubeProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Music News & Release feed item derived from YouTube channel Atom RSS uploads and playlist RSS feeds.
 */
data class MusicNewsReleaseItem(
    val id: String,
    val title: String,
    val sourceChannel: String,
    val publishedAt: String,
    val linkUrl: String,
    val thumbnailUrl: String?,
    val summary: String
)

/**
 * Authoritative immutable Search State managed by [SearchViewModel].
 */
data class SearchState(
    val query: String = "",
    val debouncedQuery: String = "",
    val uiState: MusicSearchUiState = MusicSearchUiState.IDLE,
    val isLoading: Boolean = false,
    val isSyncingFeeds: Boolean = false,
    val tracks: List<MusicSearchResult> = emptyList(),
    val artists: List<MusicArtistSearchResult> = emptyList(),
    val albums: List<MusicAlbumSearchResult> = emptyList(),
    val channelVideos: List<YouTubeVideoEntity> = emptyList(),
    val matchedChannels: List<YouTubeChannelEntity> = emptyList(),
    val catalogChannels: List<YouTubeChannelEntity> = emptyList(),
    val latestVideos: List<YouTubeVideoEntity> = emptyList(),
    val trendingVideos: List<YouTubeVideoEntity> = emptyList(),
    val newsItems: List<MusicNewsReleaseItem> = emptyList(),
    val selectedChannel: YouTubeChannelEntity? = null,
    val selectedChannelVideos: List<YouTubeVideoEntity> = emptyList(),
    val totalCatalogChannels: Int = 0,
    val totalSyncedVideos: Int = 0,
    val latestSyncRun: FeedSyncRunEntity? = null,
    val errorMessage: String? = null
)

/**
 * ViewModel handling debounced search state across Music News/Releases, YouTube Channel Atom RSS feeds,
 * OPML Channel Catalog, and YouTube Music metadata.
 */
@OptIn(FlowPreview::class)
class SearchViewModel(
    application: Application,
    private val searchProvider: MusicSearchProvider = YouTubeMusicInnerTubeProvider(),
    private val discoveryApi: MusicDiscoveryApiService = MusicDiscoveryApiService(
        SynapseDatabase.getInstance(application).discoveryDao()
    ),
    private val feedSyncWorker: YouTubeFeedSyncWorker = YouTubeFeedSyncWorker(
        context = application.applicationContext,
        discoveryDao = SynapseDatabase.getInstance(application).discoveryDao()
    ),
    private val radioRssService: MusicRadioRssService = MusicRadioRssService(),
    private val debounceMs: Long = 350L,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {

    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val rawQueryFlow = MutableStateFlow("")
    private var activeQueryJob: Job? = null

    val debouncedCoordinator = DebouncedMusicSearchCoordinator(
        provider = searchProvider,
        scope = viewModelScope,
        debounceMs = debounceMs,
        ioDispatcher = ioDispatcher
    )

    init {
        // Observe rawQueryFlow with coroutine debounce for news & YouTube content queries
        viewModelScope.launch {
            rawQueryFlow
                .debounce(debounceMs)
                .distinctUntilChanged()
                .collectLatest { debounced ->
                    executeSearchForQuery(debounced)
                }
        }

        // Initialize OPML catalog & load initial discovery state in background
        viewModelScope.launch(ioDispatcher) {
            val discoveryDao = SynapseDatabase.getInstance(getApplication()).discoveryDao()
            val count = YouTubeChannelCatalog.ensureCatalogImported(
                context = getApplication(),
                discoveryDao = discoveryDao,
                ioDispatcher = ioDispatcher
            )
            refreshDiscoveryCatalogOverview(catalogCountOverride = count)
        }
    }

    /**
     * Updates the raw search query immediately and schedules a debounced search across
     * YouTube Music, synchronized YouTube Channel RSS videos, and music news releases.
     */
    fun onQueryChanged(newQuery: String) {
        val cleaned = newQuery.trim()
        _searchState.update { current ->
            current.copy(
                query = newQuery,
                uiState = if (cleaned.isEmpty()) MusicSearchUiState.IDLE else MusicSearchUiState.TYPING,
                errorMessage = null
            )
        }
        rawQueryFlow.value = newQuery
        if (cleaned.isEmpty()) {
            activeQueryJob?.cancel()
            debouncedCoordinator.cancelAll()
            _searchState.update { current ->
                current.copy(
                    debouncedQuery = "",
                    isLoading = false,
                    uiState = MusicSearchUiState.IDLE,
                    tracks = emptyList(),
                    artists = emptyList(),
                    albums = emptyList(),
                    channelVideos = emptyList(),
                    matchedChannels = emptyList(),
                    errorMessage = null
                )
            }
        }
    }

    /**
     * Triggered when the `SearchBar` component emits a debounced query or when user submits immediately.
     */
    fun onDebouncedQuerySubmitted(cleanedQuery: String) {
        val trimmed = cleanedQuery.trim()
        if (trimmed == _searchState.value.debouncedQuery && _searchState.value.uiState == MusicSearchUiState.RESULTS) {
            return
        }
        executeSearchForQuery(trimmed)
    }

    fun retrySearch() {
        val current = _searchState.value.query.trim()
        if (current.isNotEmpty()) {
            executeSearchForQuery(current)
        } else {
            refreshDiscoveryCatalogOverview()
        }
    }

    private fun executeSearchForQuery(rawQuery: String) {
        val cleaned = rawQuery.trim()
        if (cleaned.isEmpty()) return

        activeQueryJob?.cancel()
        activeQueryJob = viewModelScope.launch(ioDispatcher) {
            _searchState.update {
                it.copy(
                    debouncedQuery = cleaned,
                    isLoading = true,
                    uiState = MusicSearchUiState.LOADING,
                    errorMessage = null
                )
            }

            try {
                coroutineScope {
                    val discoveryDeferred = async {
                        runCatching { discoveryApi.search(query = cleaned, page = 1, pageSize = 25) }
                            .getOrNull()
                    }
                    val remoteBatchDeferred = async {
                        runCatching { debouncedCoordinator.executeParallelSearch(cleaned) }
                    }

                    val discoveryResult = discoveryDeferred.await()
                    val remoteResult = remoteBatchDeferred.await()

                    val syncedVideos = discoveryResult?.videos.orEmpty()
                    val matchedChans = discoveryResult?.channels.orEmpty()
                    val remoteBatch = remoteResult.getOrNull()

                    val newsMatches = syncedVideos.map { video ->
                        video.toNewsReleaseItem()
                    }

                    val tracks = remoteBatch?.tracks.orEmpty()
                    val artists = remoteBatch?.artists.orEmpty()
                    val albums = remoteBatch?.albums.orEmpty()

                    val hasAnyResult = tracks.isNotEmpty() ||
                        artists.isNotEmpty() ||
                        albums.isNotEmpty() ||
                        syncedVideos.isNotEmpty() ||
                        matchedChans.isNotEmpty()

                    val remoteError = remoteResult.exceptionOrNull()

                    _searchState.update { prev ->
                        prev.copy(
                            isLoading = false,
                            tracks = tracks,
                            artists = artists,
                            albums = albums,
                            channelVideos = syncedVideos,
                            matchedChannels = matchedChans,
                            newsItems = if (newsMatches.isNotEmpty()) newsMatches else prev.newsItems,
                            uiState = when {
                                hasAnyResult -> MusicSearchUiState.RESULTS
                                remoteError != null -> MusicSearchUiState.NETWORK_ERROR
                                else -> MusicSearchUiState.EMPTY_RESULTS
                            },
                            errorMessage = if (!hasAnyResult && remoteError != null) {
                                remoteError.message
                            } else {
                                null
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                _searchState.update {
                    it.copy(
                        isLoading = false,
                        uiState = MusicSearchUiState.NETWORK_ERROR,
                        errorMessage = e.message ?: "Unable to complete search."
                    )
                }
            }
        }
    }

    /**
     * Refreshes the channel catalog, latest synchronized RSS videos, trending videos, and news items.
     */
    fun refreshDiscoveryCatalogOverview(catalogCountOverride: Int? = null) {
        viewModelScope.launch(ioDispatcher) {
            val status = discoveryApi.getStatus(feedSyncWorker.isRunning)
            val channelsPage = discoveryApi.getChannels(page = 1, pageSize = 30)
            val latestPage = discoveryApi.getLatest(page = 1, pageSize = 20)
            val trending = discoveryApi.getTrending(limit = 15)

            val newsFromRss = latestPage.items.map { it.toNewsReleaseItem() }

            _searchState.update { prev ->
                prev.copy(
                    catalogChannels = channelsPage.items,
                    latestVideos = latestPage.items,
                    trendingVideos = trending,
                    newsItems = newsFromRss,
                    totalCatalogChannels = catalogCountOverride ?: status.totalCatalogChannels,
                    totalSyncedVideos = status.synchronizedVideosCount,
                    latestSyncRun = status.latestSyncRun
                )
            }
        }
    }

    /**
     * Triggers an incremental background RSS feed synchronization run.
     */
    fun triggerFeedSyncBatch(batchSize: Int = 6) {
        if (_searchState.value.isSyncingFeeds) return
        viewModelScope.launch(ioDispatcher) {
            _searchState.update { it.copy(isSyncingFeeds = true) }
            val run = feedSyncWorker.runIncrementalSync(
                batchSize = batchSize,
                perChannelLimit = 5,
                delayBetweenFeedsMs = 250L
            )
            val status = discoveryApi.getStatus(false)
            val latestPage = discoveryApi.getLatest(page = 1, pageSize = 20)
            val trending = discoveryApi.getTrending(limit = 15)
            val channelsPage = discoveryApi.getChannels(page = 1, pageSize = 30)

            _searchState.update { prev ->
                prev.copy(
                    isSyncingFeeds = false,
                    latestSyncRun = run,
                    totalCatalogChannels = status.totalCatalogChannels,
                    totalSyncedVideos = status.synchronizedVideosCount,
                    latestVideos = latestPage.items,
                    trendingVideos = trending,
                    catalogChannels = channelsPage.items,
                    newsItems = latestPage.items.map { it.toNewsReleaseItem() }
                )
            }
        }
    }

    /**
     * Selects a YouTube Music channel from the OPML catalog and loads/synchronizes its latest RSS videos.
     */
    fun selectChannel(channel: YouTubeChannelEntity?) {
        if (channel == null) {
            _searchState.update { it.copy(selectedChannel = null, selectedChannelVideos = emptyList()) }
            return
        }
        viewModelScope.launch(ioDispatcher) {
            val cached = discoveryApi.getChannelVideos(channel.channelId, page = 1, pageSize = 15).items
            _searchState.update {
                it.copy(selectedChannel = channel, selectedChannelVideos = cached)
            }
            val synced = feedSyncWorker.syncSingleChannel(
                channelId = channel.channelId,
                channelTitle = channel.title,
                perChannelLimit = 8
            )
            _searchState.update {
                it.copy(selectedChannelVideos = synced)
            }
            refreshDiscoveryCatalogOverview()
        }
    }

    private fun YouTubeVideoEntity.toNewsReleaseItem(): MusicNewsReleaseItem = MusicNewsReleaseItem(
        id = videoId,
        title = title,
        sourceChannel = channelTitle,
        publishedAt = publishedAt.take(10),
        linkUrl = videoUrl,
        thumbnailUrl = thumbnailUrl,
        summary = description?.takeIf { it.isNotBlank() }
            ?: "New official upload from $channelTitle on YouTube Music."
    )
}
