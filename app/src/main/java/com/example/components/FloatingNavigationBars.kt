package com.example.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.example.services.SyncedLyricsPayload
import com.example.services.SyncedLyricsService
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

/**
 * Floating Persistent Mini-Player + 4-destination Bottom Navigation Bar (`Home`, `Search`, `Library`, `Settings`).
 *
 * Automatically displays the [PersistentMiniPlayer] docked directly above the bottom navigation
 * bar whenever a track is active and the user has navigated away from the full Now Playing screen
 * (`!isNowPlayingExpanded`).
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
    onSkipPrevious: () -> Unit = {},
    onSeekTo: (Long) -> Unit = {},
    isNowPlayingExpanded: Boolean = false,
    lyricsPayload: SyncedLyricsPayload? = null,
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
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("synapse_bottom_chrome"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnimatedVisibility(
            visible = !isNowPlayingExpanded && playbackState.currentTrack != null,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
        ) {
            PersistentMiniPlayer(
                playbackState = playbackState,
                onOpenNowPlaying = onOpenNowPlaying,
                onPlayPauseToggle = onPlayPauseToggle,
                onSkipPrevious = onSkipPrevious,
                onSkipNext = onSkipNext,
                onToggleFavorite = onToggleFavorite,
                onSeekTo = onSeekTo,
                lyricsPayload = lyricsPayload
            )
        }

        BottomNavigationDock(
            currentDestination = currentDestination,
            onSelectDestination = onSelectDestination
        )
    }
}

/**
 * Persistent Mini-Player component docked at the bottom of the main navigation area.
 *
 * Displays:
 * - Current track artwork, title, artist/album, source badge, and live synchronized lyric snippet.
 * - Full playback transport controls: Previous, Play/Pause, Next, Favorite toggle, Expand to Now Playing,
 *   and an interactive seek [Slider] with elapsed/total duration timestamps.
 */
@Composable
fun PersistentMiniPlayer(
    playbackState: PlaybackState,
    onOpenNowPlaying: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSkipPrevious: () -> Unit = {},
    onSeekTo: (Long) -> Unit = {},
    lyricsPayload: SyncedLyricsPayload? = null,
    modifier: Modifier = Modifier
) {
    MiniPlayerPill(
        playbackState = playbackState,
        onOpenNowPlaying = onOpenNowPlaying,
        onPlayPauseToggle = onPlayPauseToggle,
        onSkipPrevious = onSkipPrevious,
        onSkipNext = onSkipNext,
        onToggleFavorite = onToggleFavorite,
        onSeekTo = onSeekTo,
        lyricsPayload = lyricsPayload,
        modifier = modifier.testTag("persistent_mini_player")
    )
}

@Composable
fun MiniPlayerPill(
    playbackState: PlaybackState,
    onOpenNowPlaying: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSkipPrevious: () -> Unit = {},
    onSeekTo: (Long) -> Unit = {},
    lyricsPayload: SyncedLyricsPayload? = null,
    modifier: Modifier = Modifier
) {
    val track = playbackState.currentTrack ?: return
    val durationMs = playbackState.durationMs.takeIf { it > 0L } ?: track.durationMs.coerceAtLeast(1_000L)
    val clampedPositionMs = playbackState.positionMs.coerceIn(0L, durationMs)
    val progress = if (durationMs > 0L) {
        (clampedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val activeLyricLine = remember(lyricsPayload, clampedPositionMs, track.id) {
        val validPayload = lyricsPayload?.takeIf { it.trackId == track.id && it.lines.isNotEmpty() }
        validPayload?.let { payload ->
            val idx = SyncedLyricsService.findActiveLineIndex(payload.lines, clampedPositionMs)
            payload.lines.getOrNull(idx)?.text
        }
    }

    Surface(
        color = ElevatedSurface.copy(alpha = 0.96f),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                SpecularBorderLight,
                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
            )
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
            .clickable { onOpenNowPlaying() }
            .testTag("mini_player_bar")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Persistent Material3 Bottom Sheet Drag Handle + Expand indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, start = 12.dp, end = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        playbackState.isCrossfading -> "CROSSFADING (${playbackState.crossfadeDurationSec}s)"
                        kotlin.math.abs(playbackState.playbackSpeed - 1.0f) >= 0.05f ->
                            "NOW PLAYING • ${playbackState.playbackSpeed}x"
                        else -> "NOW PLAYING"
                    },
                    color = if (playbackState.isCrossfading) SystemGreen else SecondaryTextZinc,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp
                )

                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.24f))
                        .testTag("persistent_player_sheet_handle")
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenNowPlaying() }
                        .testTag("mini_player_expand_button")
                ) {
                    Text(
                        text = "Expand",
                        color = SystemBlue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Expand Full Now Playing Screen",
                        tint = SystemBlue,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrackArtworkSquare(
                    artworkUri = track.artworkUri,
                    accentColorHex = track.accentColorHex,
                    size = 46.dp,
                    cornerRadius = 11.dp,
                    isPlaying = playbackState.isPlaying
                )

                Spacer(modifier = Modifier.width(10.dp))

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
                            color = LabelPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .testTag("mini_player_track_title")
                        )
                        SourceBadgePill(
                            isLocal = track.isLocalFile,
                            isAvailable = track.isAvailable,
                            sourceType = track.sourceType,
                            playbackAvailability = track.playbackAvailability
                        )
                    }
                    Text(
                        text = if (playbackState.isBuffering) {
                            "Buffering stream • ${track.artist}"
                        } else {
                            buildString {
                                append(track.artist)
                                track.album?.takeIf { it.isNotBlank() }?.let {
                                    append(" • ")
                                    append(it)
                                }
                            }
                        },
                        color = if (playbackState.isBuffering) SystemBlue else SecondaryTextZinc,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("mini_player_track_artist")
                    )

                    if (!activeLyricLine.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lyrics,
                                contentDescription = null,
                                tint = HighlightFavoritePink,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = activeLyricLine,
                                color = LabelPrimary.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("mini_player_synced_lyric_line")
                            )
                        }
                    }
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
                    onClick = onSkipPrevious,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("mini_player_prev_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Track",
                        tint = LabelPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = onPlayPauseToggle,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(BeatifyAppleCrimson.copy(alpha = 0.22f))
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

            // Interactive Seek Slider + Elapsed / Total Duration labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formatDurationMillis(clampedPositionMs),
                    color = SecondaryTextZinc,
                    fontSize = 10.sp,
                    modifier = Modifier.testTag("mini_player_position_text")
                )
                Slider(
                    value = progress,
                    onValueChange = { fraction ->
                        val targetMs = (fraction.coerceIn(0f, 1f) * durationMs.toFloat()).toLong()
                        onSeekTo(targetMs)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = BeatifyAppleCrimson,
                        activeTrackColor = BeatifyAppleCrimson,
                        inactiveTrackColor = Color.White.copy(alpha = 0.16f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .testTag("mini_player_seek_slider")
                )
                Text(
                    text = formatDurationMillis(durationMs),
                    color = SecondaryTextZinc,
                    fontSize = 10.sp,
                    modifier = Modifier.testTag("mini_player_duration_text")
                )
            }

            if (playbackState.isBuffering) {
                LinearProgressIndicator(
                    color = BeatifyAppleCrimson,
                    trackColor = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                )
            }
        }
    }
}

private val PRIMARY_NAV_DESTINATIONS = listOf(
    MusicDestination.HOME,
    MusicDestination.SEARCH,
    MusicDestination.LIBRARY,
    MusicDestination.SETTINGS
)

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
            PRIMARY_NAV_DESTINATIONS.forEach { destination ->
                val selected = destination == currentDestination ||
                    (currentDestination == MusicDestination.RADIO && destination == MusicDestination.HOME)
                val icon = when (destination) {
                    MusicDestination.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
                    MusicDestination.SEARCH -> if (selected) Icons.Filled.Search else Icons.Outlined.Search
                    MusicDestination.RADIO -> if (selected) Icons.Filled.Radio else Icons.Outlined.Radio
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
                                        BeatifyAppleCrimson.copy(alpha = 0.24f),
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
                            tint = if (selected) BeatifyAppleCrimson else SecondaryTextZinc,
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
