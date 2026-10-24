package com.example.features.radio

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.components.TrackArtworkSquare
import com.example.components.formatDurationMillis
import com.example.data.CloudSubscription
import com.example.data.PlaybackState
import com.example.data.PodcastEpisodeItem
import com.example.data.RadioStationItem
import com.example.services.BeatifyMediaCatalog
import com.example.services.MusicRadioEntry
import com.example.services.MusicRadioFeedPayload
import com.example.services.MusicRadioRssService
import com.example.ui.theme.BeatifyAppleCrimson
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.HighlightFavoritePink
import com.example.ui.theme.LabelPrimary
import com.example.ui.theme.MainSurface
import com.example.ui.theme.OledBlack
import com.example.ui.theme.SecondaryTextZinc
import com.example.ui.theme.SecondaryViolet
import com.example.ui.theme.SpecularBorderLight
import com.example.ui.theme.SubtleBorderWhite
import com.example.ui.theme.SystemBlue
import com.example.ui.theme.SystemGreen

@Composable
fun MusicRadioScreen(
    radioStations: List<RadioStationItem>,
    podcastEpisodes: List<PodcastEpisodeItem>,
    subscriptions: List<CloudSubscription>,
    playbackState: PlaybackState,
    onPlayRadioStation: (RadioStationItem) -> Unit,
    onPlayPodcastEpisode: (PodcastEpisodeItem) -> Unit,
    onToggleSubscription: (provider: String, resourceId: String, title: String) -> Unit,
    musicRadioFeed: MusicRadioFeedPayload? = null,
    isLoadingMusicRadioFeed: Boolean = false,
    onRefreshMusicRadioFeed: (playlistId: String) -> Unit = {},
    onPlayMusicRadioEntry: (MusicRadioEntry) -> Unit = {},
    onToggleMusicRadioEntryFavorite: (MusicRadioEntry) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedGenre by remember { mutableStateOf("All") }
    val activePlaylistId = musicRadioFeed?.playlist ?: MusicRadioRssService.DEFAULT_PLAYLIST

    val genres = remember(radioStations) {
        listOf("All") + radioStations.map { it.genre }.distinct()
    }

    val filteredStations = remember(radioStations, selectedGenre) {
        if (selectedGenre == "All") radioStations
        else radioStations.filter { it.genre.equals(selectedGenre, ignoreCase = true) }
    }

    val subscribedKeys = remember(subscriptions) {
        subscriptions.map { "${it.provider}_${it.resourceId}" }.toSet()
    }

    val flagshipStation = radioStations.firstOrNull()
    val effectiveEntries = remember(musicRadioFeed) {
        (musicRadioFeed?.entries?.takeIf { it.isNotEmpty() }
            ?: MusicRadioRssService.defaultSeededEntries()).distinctBy { it.id }
    }
    val playlistSongs = remember(effectiveEntries) {
        effectiveEntries.filter { it.type == "song" }
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
                .testTag("music_radio_screen"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 164.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "LIVE BROADCAST & TOP CHARTS",
                            color = BeatifyAppleCrimson,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "Radio & Top Feed",
                            color = LabelPrimary,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Surface(
                        color = BeatifyAppleCrimson.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.border(
                            1.dp,
                            BeatifyAppleCrimson.copy(alpha = 0.45f),
                            RoundedCornerShape(999.dp)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = BeatifyAppleCrimson,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "LIVE ON AIR",
                                color = BeatifyAppleCrimson,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Flagship Live Station Hero Card (Vividh Bharati Live — Direct Background Media3 Playback)
            if (flagshipStation != null) {
                item {
                    val isPlayingStation = playbackState.currentTrack?.id == "radio_${flagshipStation.id}" && playbackState.isPlaying
                    val isSubscribed = "radio_${flagshipStation.id}" in subscribedKeys

                    Surface(
                        color = MainSurface,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SpecularBorderLight, RoundedCornerShape(24.dp))
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { onPlayRadioStation(flagshipStation) }
                            .testTag("flagship_radio_hero_card")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            BeatifyAppleCrimson.copy(alpha = 0.30f),
                                            SecondaryViolet.copy(alpha = 0.18f),
                                            MainSurface
                                        )
                                    )
                                )
                                .padding(18.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                            imageVector = Icons.Default.Radio,
                                            contentDescription = null,
                                            tint = BeatifyAppleCrimson,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "AKASHVANI • PRASAR BHARATI LIVE",
                                            color = BeatifyAppleCrimson,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.1.sp
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            onToggleSubscription("radio", flagshipStation.id, flagshipStation.name)
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("subscribe_flagship_radio_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isSubscribed) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                            contentDescription = if (isSubscribed) "Unsubscribe station" else "Subscribe to station",
                                            tint = if (isSubscribed) SystemGreen else LabelPrimary
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TrackArtworkSquare(
                                        artworkUri = flagshipStation.artworkUri,
                                        accentColorHex = flagshipStation.accentColorHex,
                                        size = 76.dp,
                                        cornerRadius = 16.dp,
                                        isPlaying = isPlayingStation
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = flagshipStation.name,
                                            color = LabelPrimary,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            text = flagshipStation.tagline,
                                            color = SecondaryTextZinc,
                                            fontSize = 13.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = if (isPlayingStation) {
                                                "Playing in Background • Media3 ExoPlayer"
                                            } else {
                                                "Tap to Stream Live in Background • 128 kbps"
                                            },
                                            color = SystemBlue,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(CircleShape)
                                            .background(BeatifyAppleCrimson),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isPlayingStation) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Tune In Live",
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Always-Visible Top Music Feed (Cached in Room & Background Refreshed, Plays via Media3 ExoPlayer)
            item {
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
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = null,
                            tint = SystemBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Global Top 50 Feed (${playlistSongs.size} Tracks)",
                                color = LabelPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Offline cached • Background Media3 ExoPlayer playback",
                                color = SecondaryTextZinc,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Button(
                        onClick = { onRefreshMusicRadioFeed(activePlaylistId) },
                        colors = ButtonDefaults.buttonColors(containerColor = BeatifyAppleCrimson),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("music_radio_load_playlist_button")
                    ) {
                        if (isLoadingMusicRadioFeed) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Feed",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Refresh", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            items(playlistSongs, key = { "rss_song_${it.id}" }) { songEntry ->
                val isCurrent = playbackState.currentTrack?.id == "ytm_${songEntry.id}"
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
                        .clickable { onPlayMusicRadioEntry(songEntry) }
                        .testTag("music_radio_song_${songEntry.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TrackArtworkSquare(
                            artworkUri = songEntry.thumbnail,
                            accentColorHex = 0xFF0A84FF,
                            size = 52.dp,
                            cornerRadius = 10.dp,
                            isPlaying = isPlaying
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = songEntry.title,
                                color = if (isCurrent) SystemBlue else LabelPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = songEntry.artist,
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
                        IconButton(
                            onClick = { onToggleMusicRadioEntryFavorite(songEntry) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("radio_feed_favorite_${songEntry.id}")
                        ) {
                            Icon(
                                imageVector = if (songEntry.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (songEntry.favorite) "Remove from favorites" else "Save to favorites",
                                tint = if (songEntry.favorite) HighlightFavoritePink else SecondaryTextZinc,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = { onPlayMusicRadioEntry(songEntry) },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(SystemBlue.copy(alpha = 0.18f))
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play in Background",
                                tint = SystemBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Genre Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(genres, key = { "genre_$it" }) { genre ->
                        val selected = genre == selectedGenre
                        FilterChip(
                            selected = selected,
                            onClick = { selectedGenre = genre },
                            label = {
                                Text(
                                    text = genre,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
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

            // Live Radio Stations List
            item {
                Text(
                    text = "Live Radio Stations (${filteredStations.size})",
                    color = LabelPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Tap any station to stream live or bookmark to sync with Cloud Firestore",
                    color = SecondaryTextZinc,
                    fontSize = 12.sp
                )
            }

            items(filteredStations, key = { "station_${it.id}" }) { station ->
                val isCurrent = playbackState.currentTrack?.id == "radio_${station.id}"
                val isPlaying = isCurrent && playbackState.isPlaying
                val isSubscribed = "radio_${station.id}" in subscribedKeys

                Surface(
                    color = if (isCurrent) ElevatedSurface else MainSurface,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (isCurrent) BeatifyAppleCrimson.copy(alpha = 0.6f) else SubtleBorderWhite,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onPlayRadioStation(station) }
                        .testTag("radio_station_row_${station.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TrackArtworkSquare(
                            artworkUri = station.artworkUri,
                            accentColorHex = station.accentColorHex,
                            size = 56.dp,
                            cornerRadius = 12.dp,
                            isPlaying = isPlaying
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = station.name,
                                    color = LabelPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    color = Color(station.accentColorHex).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = station.genre.uppercase(),
                                        color = Color(station.accentColorHex),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = station.tagline,
                                color = SecondaryTextZinc,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { onToggleSubscription("radio", station.id, station.name) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (isSubscribed) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (isSubscribed) "Unsubscribe" else "Subscribe",
                                tint = if (isSubscribed) SystemGreen else SecondaryTextZinc
                            )
                        }

                        IconButton(
                            onClick = { onPlayRadioStation(station) },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(BeatifyAppleCrimson.copy(alpha = 0.20f))
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = BeatifyAppleCrimson
                            )
                        }
                    }
                }
            }

            // Featured Podcasts & Shows Section
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Podcasts,
                        contentDescription = null,
                        tint = SecondaryViolet,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Beatify Original Podcasts",
                            color = LabelPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Subscribe to receive new episode alerts via Firebase Cloud Messaging",
                            color = SecondaryTextZinc,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            items(podcastEpisodes, key = { "podcast_${it.id}" }) { episode ->
                val isCurrent = playbackState.currentTrack?.id == episode.id
                val isPlaying = isCurrent && playbackState.isPlaying
                val isSubscribed = "podcast_${episode.showId}" in subscribedKeys

                Surface(
                    color = if (isCurrent) ElevatedSurface else MainSurface,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (isCurrent) SecondaryViolet.copy(alpha = 0.6f) else SubtleBorderWhite,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onPlayPodcastEpisode(episode) }
                        .testTag("podcast_episode_row_${episode.id}")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TrackArtworkSquare(
                                artworkUri = episode.artworkUri,
                                accentColorHex = episode.accentColorHex,
                                size = 56.dp,
                                cornerRadius = 12.dp,
                                isPlaying = isPlaying
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = episode.showTitle.uppercase(),
                                    color = SecondaryViolet,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.9.sp
                                )
                                Text(
                                    text = episode.title,
                                    color = LabelPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${episode.host} • ${formatDurationMillis(episode.durationMs)}",
                                    color = SecondaryTextZinc,
                                    fontSize = 11.sp
                                )
                            }

                            IconButton(
                                onClick = {
                                    onToggleSubscription("podcast", episode.showId, episode.showTitle)
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSubscribed) Icons.Default.NotificationsActive else Icons.Default.BookmarkBorder,
                                    contentDescription = if (isSubscribed) "Subscribed to show" else "Subscribe to show",
                                    tint = if (isSubscribed) SystemGreen else SecondaryTextZinc
                                )
                            }
                        }

                        Text(
                            text = episode.summary,
                            color = SecondaryTextZinc,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
