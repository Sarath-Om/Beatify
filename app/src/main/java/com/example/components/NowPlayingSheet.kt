package com.example.components

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PlaybackState
import com.example.data.RepeatMode
import com.example.data.TrackEntity
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
 * Full-screen Adaptive Now Playing & Queue Editor (Sections 3.3 & 7.3).
 *
 * Uses `BoxWithConstraints` to scale artwork dynamically on compact or short viewports and
 * switches to a two-column split layout on landscape / tablet screens so primary transport
 * controls never clip.
 */
@Composable
fun NowPlayingFullScreenOverlay(
    playbackState: PlaybackState,
    onDismiss: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onSetSleepTimerMinutes: (Int?) -> Unit,
    onSelectQueueTrack: (TrackEntity) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onClearUpcomingQueue: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val track = playbackState.currentTrack ?: return
    val accentColor = Color(track.accentColorHex)
    var showQueuePanel by remember { mutableStateOf(false) }
    var showSpeedSleepRow by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.34f),
                        SecondaryViolet.copy(alpha = 0.14f),
                        OledBlack
                    ),
                    radius = 1100f
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("now_playing_fullscreen")
    ) {
        val isLandscapeOrWide = maxWidth > 640.dp || (maxWidth > maxHeight && maxHeight < 520.dp)
        val isShortScreen = maxHeight < 680.dp
        val artworkSize: Dp = when {
            isLandscapeOrWide -> (maxHeight * 0.56f).coerceIn(140.dp, 260.dp)
            isShortScreen -> (maxHeight * 0.28f).coerceIn(136.dp, 210.dp)
            else -> (maxWidth * 0.72f).coerceIn(200.dp, 310.dp)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar: Collapse chevron, Now Playing badge, Queue toggle button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 880.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MainSurface.copy(alpha = 0.75f))
                        .testTag("now_playing_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Minimize Now Playing",
                        tint = LabelPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (showQueuePanel) "UP NEXT QUEUE" else "NOW PLAYING",
                        color = SecondaryTextZinc,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    )
                    Text(
                        text = track.album ?: "Synapse Studio Master",
                        color = LabelPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { showQueuePanel = !showQueuePanel },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (showQueuePanel) SystemBlue.copy(alpha = 0.28f)
                            else MainSurface.copy(alpha = 0.75f)
                        )
                        .testTag("now_playing_queue_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Toggle Queue Sheet",
                        tint = if (showQueuePanel) SystemBlue else LabelPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (showQueuePanel) {
                QueueEditorSection(
                    playbackState = playbackState,
                    onSelectQueueTrack = onSelectQueueTrack,
                    onMoveQueueItem = onMoveQueueItem,
                    onRemoveQueueItem = onRemoveQueueItem,
                    onClearUpcomingQueue = onClearUpcomingQueue,
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 680.dp)
                )
            } else if (isLandscapeOrWide) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 880.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(0.45f),
                        contentAlignment = Alignment.Center
                    ) {
                        TrackArtworkSquare(
                            artworkUri = track.artworkUri,
                            accentColorHex = track.accentColorHex,
                            size = artworkSize,
                            cornerRadius = 26.dp,
                            isPlaying = playbackState.isPlaying
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center
                    ) {
                        NowPlayingControlsColumn(
                            track = track,
                            playbackState = playbackState,
                            showSpeedSleepRow = showSpeedSleepRow,
                            onToggleSpeedSleepRow = { showSpeedSleepRow = !showSpeedSleepRow },
                            onPlayPauseToggle = onPlayPauseToggle,
                            onSkipNext = onSkipNext,
                            onSkipPrevious = onSkipPrevious,
                            onSeekTo = onSeekTo,
                            onToggleFavorite = onToggleFavorite,
                            onCycleRepeatMode = onCycleRepeatMode,
                            onToggleShuffle = onToggleShuffle,
                            onVolumeChange = onVolumeChange,
                            onPlaybackSpeedChange = onPlaybackSpeedChange,
                            onSetSleepTimerMinutes = onSetSleepTimerMinutes,
                            onOpenQueue = { showQueuePanel = true }
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.height(4.dp))

                    TrackArtworkSquare(
                        artworkUri = track.artworkUri,
                        accentColorHex = track.accentColorHex,
                        size = artworkSize,
                        cornerRadius = 28.dp,
                        isPlaying = playbackState.isPlaying
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    NowPlayingControlsColumn(
                        track = track,
                        playbackState = playbackState,
                        showSpeedSleepRow = showSpeedSleepRow,
                        onToggleSpeedSleepRow = { showSpeedSleepRow = !showSpeedSleepRow },
                        onPlayPauseToggle = onPlayPauseToggle,
                        onSkipNext = onSkipNext,
                        onSkipPrevious = onSkipPrevious,
                        onSeekTo = onSeekTo,
                        onToggleFavorite = onToggleFavorite,
                        onCycleRepeatMode = onCycleRepeatMode,
                        onToggleShuffle = onToggleShuffle,
                        onVolumeChange = onVolumeChange,
                        onPlaybackSpeedChange = onPlaybackSpeedChange,
                        onSetSleepTimerMinutes = onSetSleepTimerMinutes,
                        onOpenQueue = { showQueuePanel = true }
                    )
                }
            }
        }
    }
}

@Composable
private fun NowPlayingControlsColumn(
    track: TrackEntity,
    playbackState: PlaybackState,
    showSpeedSleepRow: Boolean,
    onToggleSpeedSleepRow: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onSetSleepTimerMinutes: (Int?) -> Unit,
    onOpenQueue: () -> Unit
) {
    val totalDuration = playbackState.durationMs.coerceAtLeast(track.durationMs).coerceAtLeast(1L)
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderFraction by remember { mutableFloatStateOf(0f) }

    val activeFraction = if (isDraggingSlider) {
        sliderFraction
    } else {
        (playbackState.positionMs.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Track Title + Artist + Favorite Star/Heart
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = track.title,
                        color = LabelPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    SourceBadgePill(isLocal = track.isLocalFile, isAvailable = track.isAvailable)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(track.artist)
                        track.album?.let {
                            append(" • ")
                            append(it)
                        }
                    },
                    color = SecondaryTextZinc,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(ElevatedSurface)
                    .testTag("now_playing_favorite_button")
            ) {
                Icon(
                    imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite Track",
                    tint = if (track.isFavorite) HighlightFavoritePink else SecondaryTextZinc
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Seek Scrubber with Tabular Elapsed & Remaining Time
        Slider(
            value = activeFraction,
            onValueChange = { fraction ->
                isDraggingSlider = true
                sliderFraction = fraction
            },
            onValueChangeFinished = {
                val targetMs = (sliderFraction * totalDuration).toLong()
                isDraggingSlider = false
                onSeekTo(targetMs)
            },
            colors = SliderDefaults.colors(
                thumbColor = LabelPrimary,
                activeTrackColor = SystemBlue,
                inactiveTrackColor = Color.White.copy(alpha = 0.16f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("now_playing_seek_slider")
        )

        val displayedPositionMs = (activeFraction * totalDuration).toLong()
        val remainingMs = (totalDuration - displayedPositionMs).coerceAtLeast(0L)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDurationMillis(displayedPositionMs),
                color = SecondaryTextZinc,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "-${formatDurationMillis(remainingMs)}",
                color = SecondaryTextZinc,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Primary Transport Row: Shuffle, Previous, Play/Pause, Next, Repeat
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleShuffle,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (playbackState.shuffleEnabled) SystemBlue.copy(alpha = 0.22f)
                        else Color.Transparent
                    )
                    .testTag("now_playing_shuffle_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Toggle Shuffle",
                    tint = if (playbackState.shuffleEnabled) SystemBlue else SecondaryTextZinc
                )
            }

            IconButton(
                onClick = onSkipPrevious,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MainSurface)
                    .testTag("now_playing_prev_button")
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous Track",
                    tint = LabelPrimary,
                    modifier = Modifier.size(30.dp)
                )
            }

            IconButton(
                onClick = onPlayPauseToggle,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(SystemBlue, SecondaryViolet)
                        )
                    )
                    .testTag("now_playing_play_pause_button")
            ) {
                Icon(
                    imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                    tint = LabelPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(
                onClick = onSkipNext,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MainSurface)
                    .testTag("now_playing_next_button")
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next Track",
                    tint = LabelPrimary,
                    modifier = Modifier.size(30.dp)
                )
            }

            IconButton(
                onClick = onCycleRepeatMode,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (playbackState.repeatMode != RepeatMode.OFF) SystemBlue.copy(alpha = 0.22f)
                        else Color.Transparent
                    )
                    .testTag("now_playing_repeat_button")
            ) {
                Icon(
                    imageVector = if (playbackState.repeatMode == RepeatMode.ONE) {
                        Icons.Default.RepeatOne
                    } else {
                        Icons.Default.Repeat
                    },
                    contentDescription = "Repeat Mode: ${playbackState.repeatMode.label}",
                    tint = if (playbackState.repeatMode != RepeatMode.OFF) SystemBlue else SecondaryTextZinc
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Volume Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = "Volume",
                tint = SecondaryTextZinc,
                modifier = Modifier.size(20.dp)
            )
            Slider(
                value = playbackState.volume,
                onValueChange = onVolumeChange,
                colors = SliderDefaults.colors(
                    thumbColor = LabelPrimary,
                    activeTrackColor = SecondaryViolet,
                    inactiveTrackColor = Color.White.copy(alpha = 0.14f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("now_playing_volume_slider")
            )
        }

        // Bottom Utility Pills: Speed, Sleep Timer, Queue Count
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MainSurface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .border(1.dp, SubtleBorderWhite, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onToggleSpeedSleepRow() }
                    .testTag("now_playing_speed_sleep_toggle")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = SystemBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${playbackState.playbackSpeed}x",
                        color = LabelPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (playbackState.sleepTimerRemainingMs != null) {
                        Text(
                            text = "• Sleep ${formatDurationMillis(playbackState.sleepTimerRemainingMs)}",
                            color = SystemGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Surface(
                color = MainSurface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .border(1.dp, SubtleBorderWhite, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onOpenQueue() }
                    .testTag("now_playing_open_queue_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = null,
                        tint = SecondaryViolet,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Queue (${playbackState.queue.size})",
                        color = LabelPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (showSpeedSleepRow) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = MainSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SubtleBorderWhite, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Playback Speed",
                        color = SecondaryTextZinc,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                            FilterChip(
                                selected = playbackState.playbackSpeed == speed,
                                onClick = { onPlaybackSpeedChange(speed) },
                                label = { Text("${speed}x", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SystemBlue.copy(alpha = 0.24f),
                                    selectedLabelColor = LabelPrimary
                                )
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = null,
                            tint = SecondaryViolet,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Sleep Timer",
                            color = SecondaryTextZinc,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(null to "Off", 15 to "15m", 30 to "30m", 45 to "45m", 60 to "60m").forEach { (mins, label) ->
                            val isSelected = (mins == null && playbackState.sleepTimerRemainingMs == null)
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSetSleepTimerMinutes(mins) },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SecondaryViolet.copy(alpha = 0.24f),
                                    selectedLabelColor = LabelPrimary
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueEditorSection(
    playbackState: PlaybackState,
    onSelectQueueTrack: (TrackEntity) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onClearUpcomingQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Active Queue (${playbackState.queue.size} tracks)",
                color = LabelPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            if (playbackState.queue.size > 1) {
                TextButton(
                    onClick = onClearUpcomingQueue,
                    modifier = Modifier.testTag("clear_upcoming_queue_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ClearAll,
                        contentDescription = null,
                        tint = HighlightFavoritePink,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Keep Current Only", color = HighlightFavoritePink, fontSize = 12.sp)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = playbackState.queue,
                key = { index, item -> "${item.id}_$index" }
            ) { index, item ->
                val isCurrent = item.id == playbackState.currentTrack?.id
                Surface(
                    color = if (isCurrent) ElevatedSurface else MainSurface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (isCurrent) SystemBlue.copy(alpha = 0.6f) else SubtleBorderWhite,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectQueueTrack(item) }
                        .testTag("queue_item_$index")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TrackArtworkSquare(
                            artworkUri = item.artworkUri,
                            accentColorHex = item.accentColorHex,
                            size = 44.dp,
                            cornerRadius = 10.dp,
                            isPlaying = isCurrent && playbackState.isPlaying
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                color = if (isCurrent) SystemBlue else LabelPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${item.artist} • ${formatDurationMillis(item.durationMs)}",
                                color = SecondaryTextZinc,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (index > 0) {
                            IconButton(
                                onClick = { onMoveQueueItem(index, index - 1) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Move Up",
                                    tint = SecondaryTextZinc,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (index < playbackState.queue.lastIndex) {
                            IconButton(
                                onClick = { onMoveQueueItem(index, index + 1) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Move Down",
                                    tint = SecondaryTextZinc,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { onRemoveQueueItem(index) },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("remove_queue_item_$index")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove from Queue",
                                tint = SecondaryTextZinc,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
