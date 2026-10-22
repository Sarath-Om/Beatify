package com.example.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.data.MusicDestination
import com.example.data.PlaybackState
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

/**
 * Floating Mini-Player + 4-destination Bottom Navigation Bar (Home, Search, Library, Settings).
 *
 * Ensures the mini-player never overlaps or obscures list content or navigation buttons.
 */
@Composable
fun SynapseBottomChrome(
    currentDestination: MusicDestination,
    playbackState: PlaybackState,
    onSelectDestination: (MusicDestination) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        OledBlack.copy(alpha = 0.92f),
                        OledBlack
                    )
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val currentTrack = playbackState.currentTrack
        if (currentTrack != null) {
            MiniPlayerPill(
                playbackState = playbackState,
                onOpenNowPlaying = onOpenNowPlaying,
                onPlayPauseToggle = onPlayPauseToggle,
                onSkipNext = onSkipNext,
                onToggleFavorite = onToggleFavorite
            )
        }

        BottomNavigationDock(
            currentDestination = currentDestination,
            onSelectDestination = onSelectDestination
        )
    }
}

@Composable
fun MiniPlayerPill(
    playbackState: PlaybackState,
    onOpenNowPlaying: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playbackState.currentTrack ?: return
    val progress = if (playbackState.durationMs > 0L) {
        (playbackState.positionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(
        color = ElevatedSurface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 12.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, SpecularBorderLight, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .clickable { onOpenNowPlaying() }
            .testTag("mini_player_bar")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrackArtworkSquare(
                    artworkUri = track.artworkUri,
                    accentColorHex = track.accentColorHex,
                    size = 44.dp,
                    cornerRadius = 10.dp,
                    isPlaying = playbackState.isPlaying
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = track.title,
                        color = LabelPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        color = SecondaryTextZinc,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("mini_player_favorite_button")
                ) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (track.isFavorite) "Unfavorite track" else "Favorite track",
                        tint = if (track.isFavorite) HighlightFavoritePink else SecondaryTextZinc,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onPlayPauseToggle,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(SystemBlue.copy(alpha = 0.18f))
                        .testTag("mini_player_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        tint = LabelPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("mini_player_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Track",
                        tint = LabelPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                color = SystemBlue,
                trackColor = Color.White.copy(alpha = 0.08f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
            )
        }
    }
}

@Composable
fun BottomNavigationDock(
    currentDestination: MusicDestination,
    onSelectDestination: (MusicDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MainSurface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, SubtleBorderWhite, RoundedCornerShape(24.dp))
            .testTag("music_bottom_nav_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MusicDestination.entries.forEach { destination ->
                val selected = destination == currentDestination
                val icon = when (destination) {
                    MusicDestination.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
                    MusicDestination.SEARCH -> if (selected) Icons.Filled.Search else Icons.Outlined.Search
                    MusicDestination.LIBRARY -> if (selected) Icons.Filled.LibraryMusic else Icons.Outlined.LibraryMusic
                    MusicDestination.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (selected) {
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        SystemBlue.copy(alpha = 0.22f),
                                        SecondaryViolet.copy(alpha = 0.18f)
                                    )
                                )
                            } else {
                                Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                            }
                        )
                        .clickable { onSelectDestination(destination) }
                        .testTag("nav_tab_${destination.route}"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = destination.label,
                            tint = if (selected) SystemBlue else SecondaryTextZinc,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = destination.label,
                            color = if (selected) LabelPrimary else SecondaryTextZinc,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
