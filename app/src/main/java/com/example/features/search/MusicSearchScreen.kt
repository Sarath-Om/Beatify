package com.example.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.components.TrackListItemRow
import com.example.data.AlbumSummary
import com.example.data.PlaybackState
import com.example.data.PlaylistEntity
import com.example.data.TrackEntity
import com.example.data.TrackSortOption
import com.example.ui.theme.LabelPrimary
import com.example.ui.theme.MainSurface
import com.example.ui.theme.OledBlack
import com.example.ui.theme.SecondaryTextZinc
import com.example.ui.theme.SecondaryViolet
import com.example.ui.theme.SubtleBorderWhite
import com.example.ui.theme.SystemBlue

private enum class SearchScopeFilter(val label: String) {
    ALL("All"),
    TRACKS("Tracks"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    FAVORITES("Favorites"),
    LOCAL("Local Files")
}

@Composable
fun MusicSearchScreen(
    allTracks: List<TrackEntity>,
    albums: List<AlbumSummary>,
    playlists: List<PlaylistEntity>,
    playbackState: PlaybackState,
    sortOption: TrackSortOption,
    onChangeSortOption: (TrackSortOption) -> Unit,
    onPlayTrackFromList: (TrackEntity, List<TrackEntity>) -> Unit,
    onPlayAlbum: (AlbumSummary) -> Unit,
    onPlayPlaylist: (PlaylistEntity) -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onPlayNext: (TrackEntity) -> Unit,
    onAddToQueue: (TrackEntity) -> Unit,
    onAddToPlaylist: (String, TrackEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var scopeFilter by remember { mutableStateOf(SearchScopeFilter.ALL) }

    val matchingTracks = remember(allTracks, query, scopeFilter, sortOption) {
        val q = query.trim().lowercase()
        val filtered = allTracks.filter { track ->
            val matchesScope = when (scopeFilter) {
                SearchScopeFilter.ALL -> true
                SearchScopeFilter.TRACKS -> true
                SearchScopeFilter.ARTISTS -> q.isEmpty() || track.artist.lowercase().contains(q)
                SearchScopeFilter.ALBUMS -> q.isEmpty() || (track.album?.lowercase()?.contains(q) == true)
                SearchScopeFilter.FAVORITES -> track.isFavorite
                SearchScopeFilter.LOCAL -> track.isLocalFile
            }
            val matchesQuery = q.isEmpty() ||
                track.title.lowercase().contains(q) ||
                track.artist.lowercase().contains(q) ||
                (track.album?.lowercase()?.contains(q) == true)
            matchesScope && matchesQuery
        }

        when (sortOption) {
            TrackSortOption.TITLE -> filtered.sortedBy { it.title.lowercase() }
            TrackSortOption.ARTIST -> filtered.sortedBy { it.artist.lowercase() }
            TrackSortOption.ALBUM -> filtered.sortedBy { (it.album ?: "").lowercase() }
            TrackSortOption.DATE_ADDED -> filtered.sortedByDescending { it.addedAtEpochMs }
            TrackSortOption.DURATION -> filtered.sortedByDescending { it.durationMs }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .statusBarsPadding()
                .testTag("music_search_screen"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 164.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Search Catalog",
                    color = LabelPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Find tracks, artists, albums, and custom playlists instantly",
                    color = SecondaryTextZinc,
                    fontSize = 13.sp
                )
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            text = "Search by title, artist, or album...",
                            color = SecondaryTextZinc
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = SystemBlue
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear Search",
                                    tint = SecondaryTextZinc
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MainSurface,
                        unfocusedContainerColor = MainSurface,
                        focusedBorderColor = SystemBlue,
                        unfocusedBorderColor = SubtleBorderWhite,
                        focusedTextColor = LabelPrimary,
                        unfocusedTextColor = LabelPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_screen_query_input")
                )
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
                                selectedContainerColor = SystemBlue.copy(alpha = 0.24f),
                                selectedLabelColor = LabelPrimary,
                                containerColor = MainSurface,
                                labelColor = SecondaryTextZinc
                            )
                        )
                    }
                }
            }

            // Sort Row
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
                            text = "Sort by:",
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

            if (matchingTracks.isEmpty()) {
                item {
                    Surface(
                        color = MainSurface,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SubtleBorderWhite, RoundedCornerShape(18.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No matching tracks found",
                                color = LabelPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Try another artist, album, or import audio files from your device.",
                                color = SecondaryTextZinc,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                items(matchingTracks, key = { "search_${it.id}" }) { track ->
                    TrackListItemRow(
                        track = track,
                        isCurrentTrack = playbackState.currentTrack?.id == track.id,
                        isPlaying = playbackState.isPlaying,
                        playlists = playlists,
                        onTrackClick = { onPlayTrackFromList(track, matchingTracks) },
                        onToggleFavorite = { onToggleFavorite(track) },
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) },
                        onAddToPlaylist = { playlistId -> onAddToPlaylist(playlistId, track) }
                    )
                }
            }
        }
    }
}
