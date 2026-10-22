package com.example.features.library

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.components.TrackArtworkSquare
import com.example.components.TrackListItemRow
import com.example.data.AlbumSummary
import com.example.data.ArtistSummary
import com.example.data.LibraryCategory
import com.example.data.PlaybackState
import com.example.data.PlaylistEntity
import com.example.data.TrackEntity
import com.example.data.TrackSortOption
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
fun MusicLibraryScreen(
    allTracks: List<TrackEntity>,
    favoriteTracks: List<TrackEntity>,
    recentlyPlayed: List<TrackEntity>,
    albums: List<AlbumSummary>,
    artists: List<ArtistSummary>,
    playlists: List<PlaylistEntity>,
    playbackState: PlaybackState,
    selectedCategory: LibraryCategory,
    onSelectCategory: (LibraryCategory) -> Unit,
    sortOption: TrackSortOption,
    onChangeSortOption: (TrackSortOption) -> Unit,
    onImportAudioFiles: () -> Unit,
    onPlayTrackFromList: (TrackEntity, List<TrackEntity>) -> Unit,
    onPlayAlbum: (AlbumSummary) -> Unit,
    onPlayArtist: (ArtistSummary) -> Unit,
    onPlayPlaylist: (PlaylistEntity) -> Unit,
    onCreatePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onPlayNext: (TrackEntity) -> Unit,
    onAddToQueue: (TrackEntity) -> Unit,
    onAddToPlaylist: (String, TrackEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var newPlaylistDesc by remember { mutableStateOf("") }

    val sortedTracks = remember(allTracks, sortOption) {
        when (sortOption) {
            TrackSortOption.TITLE -> allTracks.sortedBy { it.title.lowercase() }
            TrackSortOption.ARTIST -> allTracks.sortedBy { it.artist.lowercase() }
            TrackSortOption.ALBUM -> allTracks.sortedBy { (it.album ?: "").lowercase() }
            TrackSortOption.DATE_ADDED -> allTracks.sortedByDescending { it.addedAtEpochMs }
            TrackSortOption.DURATION -> allTracks.sortedByDescending { it.durationMs }
        }
    }

    val localTrackCount = remember(allTracks) { allTracks.count { it.isLocalFile } }

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
                .testTag("music_library_screen"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 164.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Header + Create Playlist + Import Local Audio
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Your Library",
                            color = LabelPrimary,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${allTracks.size} tracks ($localTrackCount imported local files) • ${playlists.size} playlists",
                            color = SecondaryTextZinc,
                            fontSize = 12.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showCreatePlaylistDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = SecondaryViolet),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("library_create_playlist_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Playlist", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 2. Import Local Audio Banner (MP3, M4A/AAC, FLAC, WAV, OGG, Opus)
            item {
                Surface(
                    color = MainSurface,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SpecularBorderLight, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onImportAudioFiles() }
                        .testTag("library_import_audio_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        SystemBlue.copy(alpha = 0.22f),
                                        SecondaryViolet.copy(alpha = 0.16f),
                                        MainSurface
                                    )
                                )
                            )
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(SystemGreen.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = SystemGreen
                                )
                            }
                            Column {
                                Text(
                                    text = "Import Device Audio Files",
                                    color = LabelPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Supports MP3, M4A/AAC, FLAC, WAV, OGG & Opus with ID3 artwork extraction",
                                    color = SecondaryTextZinc,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            color = SystemBlue,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Browse",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // 3. Category Filter Pills
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(LibraryCategory.entries) { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { onSelectCategory(category) },
                            label = { Text(category.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SystemBlue.copy(alpha = 0.26f),
                                selectedLabelColor = LabelPrimary,
                                containerColor = MainSurface,
                                labelColor = SecondaryTextZinc
                            ),
                            modifier = Modifier.testTag("library_cat_${category.name.lowercase()}")
                        )
                    }
                }
            }

            // 4. Sort selector when viewing Tracks
            if (selectedCategory == LibraryCategory.TRACKS) {
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
                                text = "Sort tracks:",
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

            // 5. Category Content
            when (selectedCategory) {
                LibraryCategory.TRACKS -> {
                    items(sortedTracks, key = { "lib_track_${it.id}" }) { track ->
                        TrackListItemRow(
                            track = track,
                            isCurrentTrack = playbackState.currentTrack?.id == track.id,
                            isPlaying = playbackState.isPlaying,
                            playlists = playlists,
                            onTrackClick = { onPlayTrackFromList(track, sortedTracks) },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onPlayNext = { onPlayNext(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            onAddToPlaylist = { playlistId -> onAddToPlaylist(playlistId, track) }
                        )
                    }
                }

                LibraryCategory.FAVORITES -> {
                    if (favoriteTracks.isEmpty()) {
                        item {
                            EmptyLibraryCard("No favorite tracks yet. Tap the heart icon on any track to save it here.")
                        }
                    } else {
                        items(favoriteTracks, key = { "lib_fav_${it.id}" }) { track ->
                            TrackListItemRow(
                                track = track,
                                isCurrentTrack = playbackState.currentTrack?.id == track.id,
                                isPlaying = playbackState.isPlaying,
                                playlists = playlists,
                                onTrackClick = { onPlayTrackFromList(track, favoriteTracks) },
                                onToggleFavorite = { onToggleFavorite(track) },
                                onPlayNext = { onPlayNext(track) },
                                onAddToQueue = { onAddToQueue(track) },
                                onAddToPlaylist = { playlistId -> onAddToPlaylist(playlistId, track) }
                            )
                        }
                    }
                }

                LibraryCategory.RECENT -> {
                    if (recentlyPlayed.isEmpty()) {
                        item {
                            EmptyLibraryCard("Tracks you play will automatically appear in Recently Played.")
                        }
                    } else {
                        items(recentlyPlayed, key = { "lib_rec_${it.id}" }) { track ->
                            TrackListItemRow(
                                track = track,
                                isCurrentTrack = playbackState.currentTrack?.id == track.id,
                                isPlaying = playbackState.isPlaying,
                                playlists = playlists,
                                onTrackClick = { onPlayTrackFromList(track, recentlyPlayed) },
                                onToggleFavorite = { onToggleFavorite(track) },
                                onPlayNext = { onPlayNext(track) },
                                onAddToQueue = { onAddToQueue(track) },
                                onAddToPlaylist = { playlistId -> onAddToPlaylist(playlistId, track) }
                            )
                        }
                    }
                }

                LibraryCategory.ARTISTS -> {
                    items(artists, key = { "artist_${it.name}" }) { artist ->
                        Surface(
                            color = MainSurface,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, SubtleBorderWhite, RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onPlayArtist(artist) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TrackArtworkSquare(
                                    artworkUri = artist.artworkUri,
                                    accentColorHex = artist.accentColorHex,
                                    size = 54.dp,
                                    cornerRadius = 27.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = SystemBlue,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = artist.name,
                                            color = LabelPrimary,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = "${artist.tracks.size} tracks • ${artist.albumCount} albums",
                                        color = SecondaryTextZinc,
                                        fontSize = 12.sp
                                    )
                                }
                                IconButton(
                                    onClick = { onPlayArtist(artist) },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(SystemBlue.copy(alpha = 0.2f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play Artist",
                                        tint = SystemBlue
                                    )
                                }
                            }
                        }
                    }
                }

                LibraryCategory.ALBUMS -> {
                    items(albums, key = { "album_row_${it.name}_${it.artist}" }) { album ->
                        Surface(
                            color = MainSurface,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, SubtleBorderWhite, RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onPlayAlbum(album) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TrackArtworkSquare(
                                    artworkUri = album.artworkUri,
                                    accentColorHex = album.accentColorHex,
                                    size = 58.dp,
                                    cornerRadius = 14.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Album,
                                            contentDescription = null,
                                            tint = SecondaryViolet,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = album.name,
                                            color = LabelPrimary,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "${album.artist} • ${album.tracks.size} tracks",
                                        color = SecondaryTextZinc,
                                        fontSize = 12.sp
                                    )
                                }
                                IconButton(
                                    onClick = { onPlayAlbum(album) },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(SecondaryViolet.copy(alpha = 0.22f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play Album",
                                        tint = LabelPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                LibraryCategory.PLAYLISTS -> {
                    if (playlists.isEmpty()) {
                        item {
                            EmptyLibraryCard("No playlists yet. Tap 'New Playlist' above to create one.")
                        }
                    } else {
                        items(playlists, key = { "playlist_row_${it.id}" }) { playlist ->
                            Surface(
                                color = MainSurface,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, SubtleBorderWhite, RoundedCornerShape(18.dp))
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { onPlayPlaylist(playlist) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color(playlist.coverColorHex).copy(alpha = 0.26f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.QueueMusic,
                                            contentDescription = null,
                                            tint = LabelPrimary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = playlist.name,
                                            color = LabelPrimary,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = buildString {
                                                append("${playlist.trackIds().size} tracks")
                                                if (playlist.description.isNotBlank()) {
                                                    append(" • ")
                                                    append(playlist.description)
                                                }
                                            },
                                            color = SecondaryTextZinc,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { onPlayPlaylist(playlist) },
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play Playlist",
                                            tint = SystemBlue
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeletePlaylist(playlist.id) },
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete Playlist",
                                            tint = HighlightFavoritePink
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            containerColor = ElevatedSurface,
            title = {
                Text("Create New Playlist", color = LabelPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Playlist Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = LabelPrimary,
                            unfocusedTextColor = LabelPrimary,
                            focusedBorderColor = SystemBlue,
                            unfocusedBorderColor = SubtleBorderWhite
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_playlist_name_input")
                    )
                    OutlinedTextField(
                        value = newPlaylistDesc,
                        onValueChange = { newPlaylistDesc = it },
                        label = { Text("Description (optional)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = LabelPrimary,
                            unfocusedTextColor = LabelPrimary,
                            focusedBorderColor = SystemBlue,
                            unfocusedBorderColor = SubtleBorderWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            onCreatePlaylist(newPlaylistName.trim(), newPlaylistDesc.trim())
                            newPlaylistName = ""
                            newPlaylistDesc = ""
                            showCreatePlaylistDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SystemBlue),
                    modifier = Modifier.testTag("confirm_create_playlist_button")
                ) {
                    Text("Create", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel", color = SecondaryTextZinc)
                }
            }
        )
    }
}

@Composable
private fun EmptyLibraryCard(message: String) {
    Surface(
        color = MainSurface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SubtleBorderWhite, RoundedCornerShape(18.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(26.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                color = SecondaryTextZinc,
                fontSize = 13.sp
            )
        }
    }
}
