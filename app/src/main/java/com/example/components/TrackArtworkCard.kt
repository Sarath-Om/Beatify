package com.example.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.PlaylistEntity
import com.example.data.TrackEntity
import com.example.services.BeatifyMediaCatalog
import com.example.ui.theme.BeatifyAppleCrimson
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.HighlightFavoritePink
import com.example.ui.theme.LabelPrimary
import com.example.ui.theme.MainSurface
import com.example.ui.theme.SecondaryTextZinc
import com.example.ui.theme.SecondaryViolet
import com.example.ui.theme.SubtleBorderWhite
import com.example.ui.theme.SystemBlue
import com.example.ui.theme.SystemGreen

@Composable
fun TrackArtworkSquare(
    artworkUri: String?,
    accentColorHex: Long,
    size: Dp,
    cornerRadius: Dp = 14.dp,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    val accent = Color(accentColorHex)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.65f),
                        Color(0xFF141418),
                        SecondaryViolet.copy(alpha = 0.45f)
                    )
                )
            )
            .border(1.dp, SubtleBorderWhite, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(size * 0.42f)
        )
        if (!artworkUri.isNullOrBlank()) {
            AsyncImage(
                model = artworkUri,
                contentDescription = "Track Artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (isPlaying) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Equalizer,
                    contentDescription = "Currently Playing",
                    tint = SystemBlue,
                    modifier = Modifier.size((size * 0.42f).coerceIn(18.dp, 32.dp))
                )
            }
        }
    }
}

@Composable
fun TrackListItemRow(
    track: TrackEntity,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    playlists: List<PlaylistEntity>,
    onTrackClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onOpenExternal: ((TrackEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    val isExternalOnly = track.playbackAvailability == "external-only"

    Surface(
        color = if (isCurrentTrack) ElevatedSurface else MainSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isCurrentTrack) SystemBlue.copy(alpha = 0.55f) else SubtleBorderWhite,
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                if (isExternalOnly) {
                    if (onOpenExternal != null) {
                        onOpenExternal(track)
                    } else {
                        val url = track.externalLaunchUri ?: track.contentUri
                        BeatifyMediaCatalog.openYouTubeMusicOfficial(context, url)
                    }
                } else {
                    onTrackClick()
                }
            }
            .testTag("track_row_${track.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackArtworkSquare(
                artworkUri = track.artworkUri,
                accentColorHex = track.accentColorHex,
                size = 52.dp,
                cornerRadius = 12.dp,
                isPlaying = isCurrentTrack && isPlaying
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = track.title,
                        color = if (isCurrentTrack) SystemBlue else LabelPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    SourceBadgePill(
                        isLocal = track.isLocalFile || track.sourceType == "local",
                        isAvailable = track.isAvailable,
                        sourceType = track.sourceType,
                        playbackAvailability = track.playbackAvailability
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = buildString {
                        append(track.artist)
                        track.album?.takeIf { it.isNotBlank() }?.let {
                            append(" • ")
                            append(it)
                        }
                        if (track.durationMs > 0L) {
                            append(" • ")
                            append(formatDurationMillis(track.durationMs))
                        }
                    },
                    color = SecondaryTextZinc,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isExternalOnly) {
                Button(
                    onClick = {
                        if (onOpenExternal != null) {
                            onOpenExternal(track)
                        } else {
                            val url = track.externalLaunchUri ?: track.contentUri
                            BeatifyMediaCatalog.openYouTubeMusicOfficial(context, url)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BeatifyAppleCrimson.copy(alpha = 0.22f)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .testTag("external_play_btn_${track.id}")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open in YouTube Music",
                        tint = BeatifyAppleCrimson,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "YT Music",
                        color = LabelPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("favorite_btn_${track.id}")
            ) {
                Icon(
                    imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (track.isFavorite) "Remove from Favorites" else "Add to Favorites",
                    tint = if (track.isFavorite) HighlightFavoritePink else SecondaryTextZinc,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("track_menu_${track.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Track Options",
                        tint = SecondaryTextZinc
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(ElevatedSurface)
                ) {
                    if (isExternalOnly) {
                        DropdownMenuItem(
                            text = { Text("Open in YouTube Music", color = LabelPrimary) },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = BeatifyAppleCrimson)
                            },
                            onClick = {
                                menuExpanded = false
                                val url = track.externalLaunchUri ?: track.contentUri
                                BeatifyMediaCatalog.openYouTubeMusicOfficial(context, url)
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Play Now", color = LabelPrimary) },
                            leadingIcon = {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = SystemBlue)
                            },
                            onClick = {
                                menuExpanded = false
                                onTrackClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Play Next", color = LabelPrimary) },
                            leadingIcon = {
                                Icon(Icons.Default.SkipNext, contentDescription = null, tint = SecondaryViolet)
                            },
                            onClick = {
                                menuExpanded = false
                                onPlayNext()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to Queue", color = LabelPrimary) },
                            leadingIcon = {
                                Icon(Icons.Default.QueueMusic, contentDescription = null, tint = SystemGreen)
                            },
                            onClick = {
                                menuExpanded = false
                                onAddToQueue()
                            }
                        )
                    }
                    playlists.forEach { playlist ->
                        DropdownMenuItem(
                            text = { Text("Add to ${playlist.name}", color = LabelPrimary) },
                            leadingIcon = {
                                Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = SecondaryTextZinc)
                            },
                            onClick = {
                                menuExpanded = false
                                onAddToPlaylist(playlist.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SourceBadgePill(
    isLocal: Boolean,
    isAvailable: Boolean = true,
    sourceType: String = if (isLocal) "local" else "youtube-music",
    playbackAvailability: String = if (isAvailable) "available" else "unavailable"
) {
    val badgeColor = when {
        playbackAvailability == "external-only" -> BeatifyAppleCrimson
        !isAvailable || playbackAvailability == "unavailable" -> HighlightFavoritePink
        isLocal || sourceType == "local" -> SystemGreen
        else -> SystemBlue
    }
    val label = when {
        playbackAvailability == "external-only" -> "YT Music Ext"
        !isAvailable || playbackAvailability == "unavailable" -> "Unavailable"
        isLocal || sourceType == "local" -> "Local"
        else -> "YouTube Music"
    }
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(badgeColor.copy(alpha = 0.16f))
            .border(0.5.dp, badgeColor.copy(alpha = 0.45f), CircleShape)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = badgeColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

fun formatDurationMillis(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
