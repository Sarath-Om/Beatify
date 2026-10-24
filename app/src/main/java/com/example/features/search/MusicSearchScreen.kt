package com.example.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.components.SearchBar
import com.example.components.TrackArtworkSquare
import com.example.components.TrackListItemRow
import com.example.data.AlbumSummary
import com.example.data.MusicEntry
import com.example.data.PlaybackState
import com.example.data.PlaylistEntity
import com.example.data.TrackEntity
import com.example.data.TrackSortOption
import com.example.data.YouTubeChannelEntity
import com.example.data.YouTubeVideoEntity
import com.example.services.BeatifyMediaCatalog
import com.example.services.MusicAlbumSearchResult
import com.example.services.MusicArtistSearchResult
import com.example.services.MusicSearchResult
import com.example.ui.theme.BeatifyAppleCrimson
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.HighlightFavoritePink
import com.example.ui.theme.LabelPrimary
import com.example.ui.theme.MainSurface
import com.example.ui.theme.OledBlack
import com.example.ui.theme.SecondaryTextZinc
import com.example.ui.theme.SecondaryViolet
import com.example.ui.theme.SubtleBorderWhite
import com.example.ui.theme.SystemBlue
import com.example.ui.theme.SystemGreen

/**
 * Authoritative 9 Search UI States required by Section 4.
 */
enum class MusicSearchUiState(val statusBadge: String) {
    IDLE("Idle"),
    FOCUSED("Focused"),
    TYPING("Typing"),
    LOADING("Searching..."),
    RESULTS("Results"),
    EMPTY_RESULTS("No tracks found"),
    NETWORK_ERROR("Network error"),
    RATE_LIMITED("Rate limited"),
    PLAYBACK_UNAVAILABLE("Playback unavailable")
}

private enum class SearchScopeFilter(val label: String) {
    ALL("All"),
    YOUTUBE_MUSIC("Top Tracks"),
    RSS_DISCOVERY("New Releases"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    LOCAL("Local Files")
}

@Composable
fun MusicSearchScreen(
    allTracks: List<TrackEntity>,
    albums: List<AlbumSummary>,
    playlists: List<PlaylistEntity>,
    playbackState: PlaybackState,
    sortOption: TrackSortOption,
    searchQuery: String = "",
    searchUiState: MusicSearchUiState = MusicSearchUiState.IDLE,
    remoteTrackResults: List<MusicSearchResult> = emptyList(),
    remoteArtistResults: List<MusicArtistSearchResult> = emptyList(),
    remoteAlbumResults: List<MusicAlbumSearchResult> = emptyList(),
    searchErrorMessage: String? = null,
    catalogChannels: List<YouTubeChannelEntity> = emptyList(),
    latestRssVideos: List<YouTubeVideoEntity> = emptyList(),
    selectedChannel: YouTubeChannelEntity? = null,
    selectedChannelVideos: List<YouTubeVideoEntity> = emptyList(),
    totalCatalogChannels: Int = 0,
    totalSyncedVideos: Int = 0,
    isSyncingRssFeeds: Boolean = false,
    musicEntries: List<MusicEntry> = emptyList(),
    onSearchQueryChange: (String) -> Unit = {},
    onSearchFocusChange: (Boolean) -> Unit = {},
    onRetryRemoteSearch: () -> Unit = {},
    onTriggerRssFeedSync: () -> Unit = {},
    onSelectChannel: (YouTubeChannelEntity?) -> Unit = {},
    onPlayRssVideo: (YouTubeVideoEntity) -> Unit = {},
    onPlayMusicEntry: (MusicEntry) -> Unit = {},
    onToggleMusicEntryFavorite: (MusicEntry) -> Unit = {},
    onChangeSortOption: (TrackSortOption) -> Unit,
    onPlayTrackFromList: (TrackEntity, List<TrackEntity>) -> Unit,
    onPlayRemoteSearchResult: (MusicSearchResult, List<MusicSearchResult>) -> Unit = { _, _ -> },
    onPlayAlbum: (AlbumSummary) -> Unit,
    onPlayPlaylist: (PlaylistEntity) -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onPlayNext: (TrackEntity) -> Unit,
    onAddToQueue: (TrackEntity) -> Unit,
    onAddToPlaylist: (String, TrackEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Keep a stable MutableState across recompositions so IME composition spans do not reset mid-typing
    var internalQuery by remember { mutableStateOf(searchQuery) }
    LaunchedEffect(searchQuery) {
        if (searchQuery != internalQuery) {
            internalQuery = searchQuery
        }
    }
    var isFocused by remember { mutableStateOf(false) }
    var scopeFilter by remember { mutableStateOf(SearchScopeFilter.ALL) }

    val activeQuery = internalQuery

    // Real local files only (never fake/demo tracks), deduplicated by id
    val localMatchingTracks = remember(allTracks, activeQuery, sortOption) {
        val q = activeQuery.trim().lowercase()
        val realLocal = allTracks.filter { it.isLocalFile && !it.isDemoTrack }.distinctBy { it.id }
        val filtered = if (q.isEmpty()) {
            realLocal
        } else {
            realLocal.filter { track ->
                track.title.lowercase().contains(q) ||
                    track.artist.lowercase().contains(q) ||
                    (track.album?.lowercase()?.contains(q) == true)
            }
        }
        when (sortOption) {
            TrackSortOption.TITLE -> filtered.sortedBy { it.title.lowercase() }
            TrackSortOption.ARTIST -> filtered.sortedBy { it.artist.lowercase() }
            TrackSortOption.ALBUM -> filtered.sortedBy { (it.album ?: "").lowercase() }
            TrackSortOption.DATE_ADDED -> filtered.sortedByDescending { it.addedAtEpochMs }
            TrackSortOption.DURATION -> filtered.sortedByDescending { it.durationMs }
        }
    }

    // Always show latest releases (defaulting to seeded releases if Room is still loading), deduplicated by videoId
    val displayedRssVideos = remember(latestRssVideos, selectedChannel, selectedChannelVideos, activeQuery) {
        val base = if (selectedChannel != null) {
            selectedChannelVideos
        } else {
            latestRssVideos.takeIf { it.isNotEmpty() }
                ?: com.example.services.YouTubeChannelCatalog.defaultSeededVideos()
        }.distinctBy { it.videoId }
        val q = activeQuery.trim().lowercase()
        if (q.isEmpty()) {
            base
        } else {
            base.filter { v ->
                v.title.lowercase().contains(q) ||
                    v.channelTitle.lowercase().contains(q) ||
                    (v.description?.lowercase()?.contains(q) == true)
            }
        }
    }

    // Filter OPML channels by active query when typing, deduplicated by channelId
    val displayedChannels = remember(catalogChannels, activeQuery) {
        val uniqueChannels = catalogChannels.distinctBy { it.channelId }
        val q = activeQuery.trim().lowercase()
        if (q.isEmpty()) {
            uniqueChannels
        } else {
            uniqueChannels.filter { c ->
                c.title.lowercase().contains(q) || c.channelId.lowercase().contains(q)
            }
        }
    }

    // Filter cached MusicEntry items by query (matches "music" via source = "YouTube Music" or title/artist)
    val matchingMusicEntries = remember(musicEntries, activeQuery) {
        val uniqueEntries = musicEntries.distinctBy { it.id }
        val q = activeQuery.trim().lowercase()
        if (q.isEmpty()) {
            uniqueEntries
        } else {
            uniqueEntries.filter { entry ->
                entry.title.lowercase().contains(q) ||
                    entry.artist.lowercase().contains(q) ||
                    entry.source.lowercase().contains(q) ||
                    entry.description.lowercase().contains(q)
            }
        }
    }

    val remoteTrackEntities = remember(remoteTrackResults, allTracks, musicEntries) {
        val favSet = allTracks.filter { it.isFavorite }.map { it.id }.toSet() +
            musicEntries.filter { it.favorite }.flatMap { listOf(it.id, "ytm_${it.id}", "radio_${it.id}") }.toSet()
        remoteTrackResults
            .distinctBy { it.id }
            .map { res ->
                res.toTrackEntity().copy(isFavorite = res.id in favSet)
            }
            .distinctBy { it.id }
    }

    val uniqueRemoteArtists = remember(remoteArtistResults) {
        remoteArtistResults.distinctBy { it.id }
    }

    val uniqueRemoteAlbums = remember(remoteAlbumResults) {
        remoteAlbumResults.distinctBy { it.id }
    }

    val effectiveState = when {
        searchUiState != MusicSearchUiState.IDLE -> searchUiState
        activeQuery.isBlank() && isFocused -> MusicSearchUiState.FOCUSED
        else -> MusicSearchUiState.IDLE
    }

    val quickSuggestions = remember {
        listOf(
            "Daft Punk",
            "The Weeknd",
            "T-Series",
            "Coldplay",
            "Radiohead",
            "Tame Impala"
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack),
        contentAlignment = Alignment.TopCenter
    ) {
        val isCompactPhone = maxWidth < 360.dp
        val screenHorizontalGutter = if (isCompactPhone) 12.dp else 16.dp

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .statusBarsPadding()
                .imePadding()
                .testTag("music_search_screen"),
            contentPadding = PaddingValues(
                start = screenHorizontalGutter,
                end = screenHorizontalGutter,
                top = 14.dp,
                bottom = 164.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Search & Discovery",
                            color = LabelPrimary,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Songs • Artist Channels • New Releases & Local Audio",
                            color = SecondaryTextZinc,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Search State Pill
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(ElevatedSurface)
                            .border(1.dp, SubtleBorderWhite, CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("search_state_badge")
                    ) {
                        Text(
                            text = effectiveState.statusBadge,
                            color = when (effectiveState) {
                                MusicSearchUiState.NETWORK_ERROR,
                                MusicSearchUiState.RATE_LIMITED,
                                MusicSearchUiState.PLAYBACK_UNAVAILABLE -> HighlightFavoritePink
                                MusicSearchUiState.LOADING,
                                MusicSearchUiState.TYPING -> SystemBlue
                                MusicSearchUiState.RESULTS -> SystemGreen
                                else -> SecondaryTextZinc
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Reusable Debounced SearchBar Component (Section 4 & 8)
            item {
                SearchBar(
                    query = activeQuery,
                    onQueryChange = { updated ->
                        internalQuery = updated
                        onSearchQueryChange(updated)
                    },
                    onSearchSubmit = {
                        onRetryRemoteSearch()
                    },
                    onFocusChange = { focused ->
                        isFocused = focused
                        onSearchFocusChange(focused)
                    },
                    onQuickSuggestClick = {
                        val sample = quickSuggestions.first()
                        internalQuery = sample
                        onSearchQueryChange(sample)
                    },
                    isLoading = effectiveState == MusicSearchUiState.LOADING,
                    isError = effectiveState == MusicSearchUiState.NETWORK_ERROR ||
                        effectiveState == MusicSearchUiState.RATE_LIMITED
                )
            }

            // Quick Search Suggestions (fit viewport cleanly when Idle or Focused)
            if (activeQuery.isBlank()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Try searching real artists, channels & tracks:",
                            color = SecondaryTextZinc,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(quickSuggestions) { suggestion ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        internalQuery = suggestion
                                        onSearchQueryChange(suggestion)
                                    },
                                    label = { Text(suggestion, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MainSurface,
                                        labelColor = LabelPrimary
                                    ),
                                    modifier = Modifier.testTag("search_suggestion_$suggestion")
                                )
                            }
                        }
                    }
                }
            }

            // Scope Filter Chips
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SearchScopeFilter.entries) { filter ->
                        FilterChip(
                            selected = scopeFilter == filter,
                            onClick = { scopeFilter = filter },
                            label = { Text(filter.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BeatifyAppleCrimson.copy(alpha = 0.24f),
                                selectedLabelColor = LabelPrimary,
                                containerColor = MainSurface,
                                labelColor = SecondaryTextZinc
                            )
                        )
                    }
                }
            }

            // Artist Channels & New Releases Feed (Plays directly in background via Media3 ExoPlayer)
            if (scopeFilter == SearchScopeFilter.ALL || scopeFilter == SearchScopeFilter.RSS_DISCOVERY) {
                item {
                    Surface(
                        color = MainSurface,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SubtleBorderWhite, RoundedCornerShape(18.dp))
                            .testTag("opml_rss_discovery_status_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Newspaper,
                                        contentDescription = null,
                                        tint = BeatifyAppleCrimson,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Featured Artist Channels & Feed",
                                            color = LabelPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${totalCatalogChannels.coerceAtLeast(displayedChannels.size)} artist channels • ${totalSyncedVideos.coerceAtLeast(displayedRssVideos.size)} tracks ready",
                                            color = SecondaryTextZinc,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Button(
                                    onClick = onTriggerRssFeedSync,
                                    colors = ButtonDefaults.buttonColors(containerColor = BeatifyAppleCrimson),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("sync_rss_feeds_button")
                                ) {
                                    if (isSyncingRssFeeds) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Updating...", color = Color.White, fontSize = 11.sp)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Refresh Feed",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Refresh", color = Color.White, fontSize = 11.sp)
                                    }
                                }
                            }

                            if (displayedChannels.isNotEmpty()) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (selectedChannel != null) {
                                        item {
                                            FilterChip(
                                                selected = true,
                                                onClick = { onSelectChannel(null) },
                                                label = { Text("All Channels", fontSize = 11.sp) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = SystemBlue.copy(alpha = 0.25f),
                                                    selectedLabelColor = LabelPrimary
                                                )
                                            )
                                        }
                                    }
                                    items(
                                        displayedChannels.take(25),
                                        key = { "opml_chan_${it.channelId}" }
                                    ) { channel ->
                                        val isSelected = selectedChannel?.channelId == channel.channelId
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                onSelectChannel(if (isSelected) null else channel)
                                            },
                                            label = {
                                                Text(
                                                    text = channel.title,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = BeatifyAppleCrimson.copy(alpha = 0.28f),
                                                selectedLabelColor = LabelPrimary,
                                                containerColor = ElevatedSurface,
                                                labelColor = LabelPrimary
                                            ),
                                            modifier = Modifier.testTag("opml_channel_chip_${channel.channelId}")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Always-Visible Latest Releases Feed (Plays in Background via Media3 ExoPlayer)
                if (displayedRssVideos.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Newspaper,
                                    contentDescription = null,
                                    tint = SystemGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (selectedChannel != null) {
                                        "${selectedChannel.title} • Latest Releases (${displayedRssVideos.size})"
                                    } else {
                                        "Latest Music Releases (${displayedRssVideos.size})"
                                    },
                                    color = LabelPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Background Audio",
                                color = SystemGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    items(
                        displayedRssVideos.take(15),
                        key = { "rss_video_${it.videoId}" }
                    ) { rssVideo ->
                        val isCurrent = playbackState.currentTrack?.id == "ytm_${rssVideo.videoId}"
                        val isPlaying = isCurrent && playbackState.isPlaying

                        Surface(
                            color = if (isCurrent) ElevatedSurface else MainSurface,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = if (isCurrent) SystemBlue.copy(alpha = 0.6f) else SubtleBorderWhite,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onPlayRssVideo(rssVideo) }
                                .testTag("rss_video_row_${rssVideo.videoId}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TrackArtworkSquare(
                                    artworkUri = rssVideo.thumbnailUrl,
                                    accentColorHex = 0xFFFA2D48,
                                    size = 54.dp,
                                    cornerRadius = 10.dp,
                                    isPlaying = isPlaying
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rssVideo.title,
                                        color = if (isCurrent) SystemBlue else LabelPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${rssVideo.channelTitle} • ${rssVideo.publishedAt.take(10)}",
                                        color = SecondaryTextZinc,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isPlaying) "Playing in Background" else "Tap to play in background",
                                        color = SystemBlue,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Sort Row for Local Files
            if (scopeFilter == SearchScopeFilter.LOCAL && localMatchingTracks.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = null,
                                tint = SecondaryViolet,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Sort local:",
                                color = SecondaryTextZinc,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(TrackSortOption.entries) { option ->
                                FilterChip(
                                    selected = sortOption == option,
                                    onClick = { onChangeSortOption(option) },
                                    label = { Text(option.label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SecondaryViolet.copy(alpha = 0.24f),
                                        selectedLabelColor = LabelPrimary,
                                        containerColor = MainSurface,
                                        labelColor = SecondaryTextZinc
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Explicit Search Error / Rate Limit / Playback Unavailable Banners
            if (effectiveState == MusicSearchUiState.NETWORK_ERROR ||
                effectiveState == MusicSearchUiState.RATE_LIMITED ||
                effectiveState == MusicSearchUiState.PLAYBACK_UNAVAILABLE
            ) {
                item {
                    Surface(
                        color = MainSurface,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, HighlightFavoritePink.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                            .testTag("search_error_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = when (effectiveState) {
                                    MusicSearchUiState.RATE_LIMITED -> Icons.Default.HourglassTop
                                    MusicSearchUiState.NETWORK_ERROR -> Icons.Default.CloudOff
                                    else -> Icons.Default.ErrorOutline
                                },
                                contentDescription = null,
                                tint = HighlightFavoritePink,
                                modifier = Modifier.size(26.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = effectiveState.statusBadge,
                                    color = LabelPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = searchErrorMessage
                                        ?: "Unable to complete search request. Check your connection and retry.",
                                    color = SecondaryTextZinc,
                                    fontSize = 12.sp
                                )
                            }
                            Button(
                                onClick = onRetryRemoteSearch,
                                colors = ButtonDefaults.buttonColors(containerColor = BeatifyAppleCrimson),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("search_retry_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retry", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Artists Section
            if ((scopeFilter == SearchScopeFilter.ALL || scopeFilter == SearchScopeFilter.ARTISTS) &&
                uniqueRemoteArtists.isNotEmpty()
            ) {
                item {
                    Text(
                        text = "Artists",
                        color = LabelPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(uniqueRemoteArtists, key = { "yt_artist_${it.id}" }) { artist ->
                            Surface(
                                color = MainSurface,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .width(140.dp)
                                    .border(1.dp, SubtleBorderWhite, RoundedCornerShape(16.dp))
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        internalQuery = artist.name
                                        onSearchQueryChange(artist.name)
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    TrackArtworkSquare(
                                        artworkUri = artist.artworkUrl,
                                        accentColorHex = 0xFFFA2D48,
                                        size = 76.dp,
                                        cornerRadius = 38.dp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = BeatifyAppleCrimson,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = artist.name,
                                            color = LabelPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    artist.subscriberCountText?.let { sub ->
                                        Text(
                                            text = sub,
                                            color = SecondaryTextZinc,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Albums Section
            if ((scopeFilter == SearchScopeFilter.ALL || scopeFilter == SearchScopeFilter.ALBUMS) &&
                uniqueRemoteAlbums.isNotEmpty()
            ) {
                item {
                    Text(
                        text = "Albums",
                        color = LabelPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(uniqueRemoteAlbums, key = { "yt_album_${it.id}" }) { album ->
                            Surface(
                                color = MainSurface,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .width(156.dp)
                                    .border(1.dp, SubtleBorderWhite, RoundedCornerShape(16.dp))
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        val nextQuery = "${album.title} ${album.artistName}".trim()
                                        internalQuery = nextQuery
                                        onSearchQueryChange(nextQuery)
                                    }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    TrackArtworkSquare(
                                        artworkUri = album.artworkUrl,
                                        accentColorHex = 0xFF7C5CFF,
                                        size = 128.dp,
                                        cornerRadius = 12.dp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Album,
                                            contentDescription = null,
                                            tint = SecondaryViolet,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = album.title,
                                            color = LabelPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = album.artistName,
                                        color = SecondaryTextZinc,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Cached Music-Radio Feed Matches with Heart Favorite Button
            if ((scopeFilter == SearchScopeFilter.ALL || scopeFilter == SearchScopeFilter.YOUTUBE_MUSIC) &&
                matchingMusicEntries.isNotEmpty()
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Featured Charts & Radio (${matchingMusicEntries.size})",
                            color = LabelPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Instant Stream",
                            color = BeatifyAppleCrimson,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                items(
                    matchingMusicEntries.take(12),
                    key = { "search_music_entry_${it.id}" }
                ) { entry ->
                    val expectedTrackId = if (entry.type == "radio") "radio_${entry.id}" else "ytm_${entry.id}"
                    val isCurrent = playbackState.currentTrack?.id == expectedTrackId
                    val isPlaying = isCurrent && playbackState.isPlaying

                    Surface(
                        color = if (isCurrent) ElevatedSurface else MainSurface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = if (isCurrent) BeatifyAppleCrimson.copy(alpha = 0.6f) else SubtleBorderWhite,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onPlayMusicEntry(entry) }
                            .testTag("search_music_entry_${entry.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TrackArtworkSquare(
                                artworkUri = entry.thumbnail,
                                accentColorHex = if (entry.type == "radio") 0xFFFA2D48 else 0xFF0A84FF,
                                size = 50.dp,
                                cornerRadius = 10.dp,
                                isPlaying = isPlaying
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.title,
                                    color = if (isCurrent) BeatifyAppleCrimson else LabelPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${entry.artist} • ${entry.source}",
                                    color = SecondaryTextZinc,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { onToggleMusicEntryFavorite(entry) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("favorite_music_entry_${entry.id}")
                            ) {
                                Icon(
                                    imageVector = if (entry.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = if (entry.favorite) "Remove from favorites" else "Save to favorites",
                                    tint = if (entry.favorite) HighlightFavoritePink else SecondaryTextZinc,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Streaming Track Results Section
            if (scopeFilter == SearchScopeFilter.ALL || scopeFilter == SearchScopeFilter.YOUTUBE_MUSIC) {
                if (remoteTrackEntities.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Streaming Songs (${remoteTrackEntities.size})",
                                color = LabelPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Background Audio",
                                color = SystemBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    items(remoteTrackEntities, key = { "yt_track_${it.id}" }) { trackEntity ->
                        val rawResult = remoteTrackResults.firstOrNull { it.id == trackEntity.id }
                        TrackListItemRow(
                            track = trackEntity,
                            isCurrentTrack = playbackState.currentTrack?.id == trackEntity.id,
                            isPlaying = playbackState.isPlaying,
                            playlists = playlists,
                            onTrackClick = {
                                if (rawResult != null) {
                                    onPlayRemoteSearchResult(rawResult, remoteTrackResults)
                                } else {
                                    onPlayTrackFromList(trackEntity, remoteTrackEntities)
                                }
                            },
                            onToggleFavorite = { onToggleFavorite(trackEntity) },
                            onPlayNext = { onPlayNext(trackEntity) },
                            onAddToQueue = { onAddToQueue(trackEntity) },
                            onAddToPlaylist = { playlistId -> onAddToPlaylist(playlistId, trackEntity) }
                        )
                    }
                }
            }

            // Local Audio Files Section
            if (scopeFilter == SearchScopeFilter.ALL || scopeFilter == SearchScopeFilter.LOCAL) {
                if (localMatchingTracks.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Local Device Audio (${localMatchingTracks.size})",
                                color = LabelPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Offline Ready",
                                color = SystemGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    items(localMatchingTracks, key = { "local_search_${it.id}" }) { track ->
                        TrackListItemRow(
                            track = track,
                            isCurrentTrack = playbackState.currentTrack?.id == track.id,
                            isPlaying = playbackState.isPlaying,
                            playlists = playlists,
                            onTrackClick = { onPlayTrackFromList(track, localMatchingTracks) },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onPlayNext = { onPlayNext(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            onAddToPlaylist = { playlistId -> onAddToPlaylist(playlistId, track) }
                        )
                    }
                }
            }

            // Empty / Idle State
            val hasAnyDisplayedItems =
                ((scopeFilter == SearchScopeFilter.ALL || scopeFilter == SearchScopeFilter.YOUTUBE_MUSIC) && (remoteTrackEntities.isNotEmpty() || matchingMusicEntries.isNotEmpty())) ||
                    ((scopeFilter == SearchScopeFilter.ALL || scopeFilter == SearchScopeFilter.RSS_DISCOVERY) && displayedRssVideos.isNotEmpty()) ||
                    ((scopeFilter == SearchScopeFilter.ALL || scopeFilter == SearchScopeFilter.ARTISTS) && uniqueRemoteArtists.isNotEmpty()) ||
                    ((scopeFilter == SearchScopeFilter.ALL || scopeFilter == SearchScopeFilter.ALBUMS) && uniqueRemoteAlbums.isNotEmpty()) ||
                    ((scopeFilter == SearchScopeFilter.ALL || scopeFilter == SearchScopeFilter.LOCAL) && localMatchingTracks.isNotEmpty())

            if (!hasAnyDisplayedItems && effectiveState != MusicSearchUiState.LOADING) {
                item {
                    Surface(
                        color = MainSurface,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SubtleBorderWhite, RoundedCornerShape(18.dp))
                            .testTag("search_empty_or_idle_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (activeQuery.isNotBlank() || effectiveState == MusicSearchUiState.EMPTY_RESULTS) {
                                    "No tracks found"
                                } else {
                                    "Search Songs, Artists & Channels"
                                },
                                color = LabelPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (activeQuery.isNotBlank()) {
                                    "No matching songs or channels found for \"$activeQuery\"."
                                } else {
                                    "Enter an artist, song, or channel above, or tap any track in the feed to play in the background."
                                },
                                color = SecondaryTextZinc,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
