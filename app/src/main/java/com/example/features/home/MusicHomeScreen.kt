package com.example.features.home

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
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.components.SourceBadgePill
import com.example.components.SynapseNeonLogo
import com.example.components.TrackArtworkSquare
import com.example.components.TrackListItemRow
import com.example.components.formatDurationMillis
import com.example.data.AlbumSummary
import com.example.data.PlaybackState
import com.example.data.PlaylistEntity
import com.example.data.TrackEntity
import com.example.ui.theme.ElevatedSurface
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Radio
import com.example.ui.theme.BeatifyAppleCrimson
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
import java.util.Calendar

@Composable
fun MusicHomeScreen(
    allTracks: List<TrackEntity>,
    recentlyPlayed: List<TrackEntity>,
    recentlyAdded: List<TrackEntity>,
    favoriteTracks: List<TrackEntity>,
    albums: List<AlbumSummary>,
    playlists: List<PlaylistEntity>,
    playbackState: PlaybackState,
    currentUserUid: String? = null,
    onPlayTrackFromList: (TrackEntity, List<TrackEntity>) -> Unit,
    onShufflePlayAll: () -> Unit,
    onPlayPlaylist: (PlaylistEntity) -> Unit,
    onPlayAlbum: (AlbumSummary) -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onPlayNext: (TrackEntity) -> Unit,
    onAddToQueue: (TrackEntity) -> Unit,
    onAddToPlaylist: (String, TrackEntity) -> Unit,
    onImportAudioFiles: () -> Unit,
    onNavigateToSearch: (String) -> Unit,
    onNavigateToRadio: () -> Unit = {},
    onNavigateToSettings: () -> Unit,
    onOpenAuthSheet: () -> Unit = {},
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier
) {
    var quickQuery by remember { mutableStateOf("") }
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good Morning"
            in 12..17 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    val continueTrack = playbackState.currentTrack
        ?: recentlyPlayed.firstOrNull { it.lastPositionMs > 0L }
        ?: allTracks.firstOrNull()

    val filteredCatalog = remember(allTracks, quickQuery) {
        if (quickQuery.isBlank()) {
            allTracks
        } else {
            val q = quickQuery.trim().lowercase()
            allTracks.filter {
                it.title.lowercase().contains(q) ||
                    it.artist.lowercase().contains(q) ||
                    (it.album?.lowercase()?.contains(q) == true)
            }
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
                .testTag("music_home_screen"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 164.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // 1. App Header with Beatify Logo, Greeting, Account Sync, Import Audio & Settings Shortcut
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SynapseNeonLogo(size = 42.dp)
                        Column {
                            Text(
                                text = greeting.uppercase(),
                                color = BeatifyAppleCrimson,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = "Beatify",
                                color = LabelPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = onOpenAuthSheet,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MainSurface)
                                .border(
                                    1.dp,
                                    if (currentUserUid != null) SystemGreen.copy(alpha = 0.6f) else SubtleBorderWhite,
                                    CircleShape
                                )
                                .testTag("home_account_button")
                        ) {
                            Icon(
                                imageVector = if (currentUserUid != null) Icons.Default.CloudDone else Icons.Default.AccountCircle,
                                contentDescription = "Google Account & Cloud Sync",
                                tint = if (currentUserUid != null) SystemGreen else BeatifyAppleCrimson
                            )
                        }

                        IconButton(
                            onClick = onImportAudioFiles,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MainSurface)
                                .border(1.dp, SubtleBorderWhite, CircleShape)
                                .testTag("home_import_audio_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Import Device Audio",
                                tint = SystemGreen
                            )
                        }

                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MainSurface)
                                .border(1.dp, SubtleBorderWhite, CircleShape)
                                .testTag("home_settings_shortcut")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Open Settings",
                                tint = LabelPrimary
                            )
                        }
                    }
                }
            }

            // 1b. Quick Cloud Sync & Live Radio Action Strip
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = MainSurface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, SubtleBorderWhite, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onOpenAuthSheet() }
                            .testTag("home_cloud_sync_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (currentUserUid != null) Icons.Default.CloudDone else Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = if (currentUserUid != null) SystemGreen else SystemBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = if (currentUserUid != null) "Cloud Synced" else "Google Sign-In",
                                    color = LabelPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (currentUserUid != null) "Firestore active" else "Sync playlists & favs",
                                    color = SecondaryTextZinc,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    Surface(
                        color = MainSurface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, BeatifyAppleCrimson.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onNavigateToRadio() }
                            .testTag("home_live_radio_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radio,
                                contentDescription = null,
                                tint = BeatifyAppleCrimson,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "Beatify 1 Radio",
                                    color = LabelPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Live 320 kbps & Shows",
                                    color = SecondaryTextZinc,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            // 2. Quick Search Field + Shuffle All Action
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = quickQuery,
                        onValueChange = { quickQuery = it },
                        placeholder = {
                            Text(
                                text = "Filter tracks, artists, or albums...",
                                color = SecondaryTextZinc,
                                fontSize = 14.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = SystemBlue
                            )
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
                            .weight(1f)
                            .testTag("home_search_input")
                    )

                    Button(
                        onClick = onShufflePlayAll,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SecondaryViolet
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("home_shuffle_all_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle Play All",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Shuffle",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 3. Continue Listening Hero Card
            if (continueTrack != null && quickQuery.isBlank()) {
                item {
                    ContinueListeningHeroCard(
                        track = continueTrack,
                        playbackState = playbackState,
                        onResume = {
                            if (playbackState.currentTrack?.id == continueTrack.id) {
                                onOpenNowPlaying()
                            } else {
                                onPlayTrackFromList(continueTrack, allTracks)
                            }
                        },
                        onImportAudioFiles = onImportAudioFiles
                    )
                }
            }

            // 4. Favorite Tracks Quick Grid
            if (favoriteTracks.isNotEmpty() && quickQuery.isBlank()) {
                item {
                    SectionHeaderRow(
                        title = "Favorite Tracks",
                        subtitle = "${favoriteTracks.size} starred in your library"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(favoriteTracks, key = { "fav_${it.id}" }) { track ->
                            FavoriteQuickCard(
                                track = track,
                                isPlaying = playbackState.currentTrack?.id == track.id && playbackState.isPlaying,
                                onClick = { onPlayTrackFromList(track, favoriteTracks) }
                            )
                        }
                    }
                }
            }

            // 5. Recently Played Carousel
            if (recentlyPlayed.isNotEmpty() && quickQuery.isBlank()) {
                item {
                    SectionHeaderRow(
                        title = "Recently Played",
                        subtitle = "Instant resume from your listening history"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(recentlyPlayed, key = { "recent_${it.id}" }) { track ->
                            HorizontalTrackCard(
                                track = track,
                                isCurrent = playbackState.currentTrack?.id == track.id,
                                isPlaying = playbackState.isPlaying,
                                onClick = { onPlayTrackFromList(track, recentlyPlayed) }
                            )
                        }
                    }
                }
            }

            // 6. Recently Added Carousel
            if (recentlyAdded.isNotEmpty() && quickQuery.isBlank()) {
                item {
                    SectionHeaderRow(
                        title = "Recently Added",
                        subtitle = "Newest device imports & studio tracks"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(recentlyAdded, key = { "added_${it.id}" }) { track ->
                            HorizontalTrackCard(
                                track = track,
                                isCurrent = playbackState.currentTrack?.id == track.id,
                                isPlaying = playbackState.isPlaying,
                                onClick = { onPlayTrackFromList(track, recentlyAdded) }
                            )
                        }
                    }
                }
            }

            // 7. Albums Carousel
            if (albums.isNotEmpty() && quickQuery.isBlank()) {
                item {
                    SectionHeaderRow(
                        title = "Albums",
                        subtitle = "${albums.size} collections"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(albums, key = { "album_${it.name}_${it.artist}" }) { album ->
                            AlbumCarouselCard(
                                album = album,
                                onClick = { onPlayAlbum(album) }
                            )
                        }
                    }
                }
            }

            // 8. Playlists Carousel
            if (playlists.isNotEmpty() && quickQuery.isBlank()) {
                item {
                    SectionHeaderRow(
                        title = "Playlists",
                        subtitle = "Custom & curated mixes"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(playlists, key = { "pl_${it.id}" }) { playlist ->
                            PlaylistCarouselCard(
                                playlist = playlist,
                                onClick = { onPlayPlaylist(playlist) }
                            )
                        }
                    }
                }
            }

            // 9. Library & Streamed Tracks (Real Data Only)
            item {
                SectionHeaderRow(
                    title = if (quickQuery.isBlank()) "Your Library & Streamed Tracks" else "Matching Tracks (${filteredCatalog.size})",
                    subtitle = "Real YouTube Music streams & imported device audio played via Media3 ExoPlayer"
                )
            }

            if (filteredCatalog.isEmpty()) {
                item {
                    Surface(
                        color = MainSurface,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SubtleBorderWhite, RoundedCornerShape(20.dp))
                            .testTag("home_empty_catalog_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = if (quickQuery.isBlank()) "No Local or Saved Tracks Yet" else "No matching tracks in your library",
                                color = LabelPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Search YouTube Music for real artists, songs, and albums, or import audio files from your device.",
                                color = SecondaryTextZinc,
                                fontSize = 13.sp
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { onNavigateToSearch(quickQuery) },
                                    colors = ButtonDefaults.buttonColors(containerColor = BeatifyAppleCrimson),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.testTag("home_search_ytm_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Search YouTube Music", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = onImportAudioFiles,
                                    colors = ButtonDefaults.buttonColors(containerColor = ElevatedSurface),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.border(1.dp, SubtleBorderWhite, RoundedCornerShape(14.dp))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = SystemGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Import Audio", color = LabelPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else {
                items(filteredCatalog, key = { "catalog_${it.id}" }) { track ->
                    TrackListItemRow(
                        track = track,
                        isCurrentTrack = playbackState.currentTrack?.id == track.id,
                        isPlaying = playbackState.isPlaying,
                        playlists = playlists,
                        onTrackClick = { onPlayTrackFromList(track, filteredCatalog) },
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

@Composable
private fun ContinueListeningHeroCard(
    track: TrackEntity,
    playbackState: PlaybackState,
    onResume: () -> Unit,
    onImportAudioFiles: () -> Unit
) {
    val isActiveTrack = playbackState.currentTrack?.id == track.id
    val posMs = if (isActiveTrack) playbackState.positionMs else track.lastPositionMs
    val durMs = track.durationMs.coerceAtLeast(1L)
    val progress = (posMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f)

    Surface(
        color = MainSurface,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SpecularBorderLight, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .clickable { onResume() }
            .testTag("continue_listening_card")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(track.accentColorHex).copy(alpha = 0.28f),
                            SecondaryViolet.copy(alpha = 0.16f),
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
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = SystemBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "CONTINUE LISTENING",
                            color = SystemBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp
                        )
                    }
                    SourceBadgePill(isLocal = track.isLocalFile, isAvailable = track.isAvailable)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrackArtworkSquare(
                        artworkUri = track.artworkUri,
                        accentColorHex = track.accentColorHex,
                        size = 74.dp,
                        cornerRadius = 16.dp,
                        isPlaying = isActiveTrack && playbackState.isPlaying
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            color = LabelPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${track.artist} • ${track.album ?: "Single"}",
                            color = SecondaryTextZinc,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            color = SystemBlue,
                            trackColor = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(SystemBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Resume Track",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${formatDurationMillis(posMs)} / ${formatDurationMillis(durMs)}",
                        color = SecondaryTextZinc,
                        fontSize = 11.sp
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onImportAudioFiles() }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircleOutline,
                            contentDescription = null,
                            tint = SystemGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Import Local MP3/FLAC/WAV",
                            color = SystemGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteQuickCard(
    track: TrackEntity,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = MainSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .width(210.dp)
            .border(1.dp, SubtleBorderWhite, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackArtworkSquare(
                artworkUri = track.artworkUri,
                accentColorHex = track.accentColorHex,
                size = 46.dp,
                cornerRadius = 10.dp,
                isPlaying = isPlaying
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = LabelPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    color = SecondaryTextZinc,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = HighlightFavoritePink,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun HorizontalTrackCard(
    track: TrackEntity,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isCurrent) ElevatedSurface else MainSurface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .width(148.dp)
            .border(
                width = 1.dp,
                color = if (isCurrent) SystemBlue.copy(alpha = 0.6f) else SubtleBorderWhite,
                shape = RoundedCornerShape(18.dp)
            )
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            TrackArtworkSquare(
                artworkUri = track.artworkUri,
                accentColorHex = track.accentColorHex,
                size = 128.dp,
                cornerRadius = 14.dp,
                isPlaying = isCurrent && isPlaying
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = track.title,
                color = LabelPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                color = SecondaryTextZinc,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AlbumCarouselCard(
    album: AlbumSummary,
    onClick: () -> Unit
) {
    Surface(
        color = MainSurface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .width(156.dp)
            .border(1.dp, SubtleBorderWhite, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            TrackArtworkSquare(
                artworkUri = album.artworkUri,
                accentColorHex = album.accentColorHex,
                size = 132.dp,
                cornerRadius = 14.dp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Album,
                    contentDescription = null,
                    tint = SystemBlue,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = album.name,
                    color = LabelPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "${album.artist} • ${album.tracks.size} tracks",
                color = SecondaryTextZinc,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PlaylistCarouselCard(
    playlist: PlaylistEntity,
    onClick: () -> Unit
) {
    val accent = Color(playlist.coverColorHex)
    Surface(
        color = MainSurface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .width(172.dp)
            .border(1.dp, SubtleBorderWhite, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.28f),
                            MainSurface
                        )
                    )
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = null,
                    tint = LabelPrimary
                )
            }
            Text(
                text = playlist.name,
                color = LabelPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = playlist.description.ifBlank { "${playlist.trackIds().size} tracks" },
                color = SecondaryTextZinc,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${playlist.trackIds().size} tracks",
                color = SystemBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    subtitle: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = LabelPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            color = SecondaryTextZinc,
            fontSize = 12.sp
        )
    }
}
