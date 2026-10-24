package com.example.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.services.LyricLine
import com.example.services.LyricsSyncSnapshot
import com.example.services.SyncedLyricsPayload
import com.example.services.SyncedLyricsService
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.HighlightFavoritePink
import com.example.ui.theme.LabelPrimary
import com.example.ui.theme.MainSurface
import com.example.ui.theme.SecondaryTextZinc
import com.example.ui.theme.SecondaryViolet
import com.example.ui.theme.SubtleBorderWhite
import com.example.ui.theme.SystemBlue
import com.example.ui.theme.SystemGreen

/**
 * Full-screen / panel Synchronized Lyrics UI Component in the Now Playing screen.
 *
 * Automatically scrolls the active lyric line into focus using `LazyListState.animateScrollToItem`
 * as [positionMs] advances, animates active line scale and intra-line karaoke progress,
 * and supports tapping any lyric line to seek directly to its timestamp.
 */
@Composable
fun SynchronizedLyricsPanel(
    lyricsPayload: SyncedLyricsPayload?,
    isLoading: Boolean,
    positionMs: Long,
    isPlaying: Boolean,
    accentColor: Color,
    onSeekTo: (Long) -> Unit,
    onRefreshLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val snapshot: LyricsSyncSnapshot? = remember(lyricsPayload, positionMs) {
        lyricsPayload?.let { payload ->
            val activeIndex = SyncedLyricsService.findActiveLineIndex(payload.lines, positionMs)
            val activeLine = payload.lines.getOrNull(activeIndex)
            val upcomingLine = payload.lines.getOrNull(activeIndex + 1)
            val progress = activeLine?.let { SyncedLyricsService.calculateLineProgress(it, positionMs) } ?: 0f
            LyricsSyncSnapshot(
                payload = payload,
                activeLineIndex = activeIndex,
                activeLineProgress = progress,
                activeLine = activeLine,
                upcomingLine = upcomingLine
            )
        }
    }

    val activeIndex = snapshot?.activeLineIndex ?: -1

    // Smoothly auto-scroll the active lyric line toward the center of the viewport when timestamp advances
    LaunchedEffect(activeIndex, lyricsPayload?.trackId) {
        if (activeIndex >= 0 && (lyricsPayload?.lines?.isNotEmpty() == true)) {
            val targetScrollIndex = (activeIndex - 1).coerceAtLeast(0)
            runCatching {
                listState.animateScrollToItem(index = targetScrollIndex, scrollOffset = 0)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("synchronized_lyrics_panel"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header row: Sync status badge + Timestamp + Refresh action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = accentColor.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isPlaying) SystemGreen else SystemBlue)
                        )
                        Text(
                            text = lyricsPayload?.sourceProvider ?: "TIME-SYNCED LRC",
                            color = LabelPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "Tap any verse to seek • ${formatDurationMillis(positionMs)}",
                    color = SecondaryTextZinc,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            IconButton(
                onClick = onRefreshLyrics,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MainSurface)
                    .testTag("refresh_lyrics_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Synchronized Lyrics",
                    tint = LabelPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (isLoading && lyricsPayload == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MainSurface.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
                    .border(1.dp, SubtleBorderWhite, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        color = SystemBlue,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "Synchronizing LRC timestamps...",
                        color = SecondaryTextZinc,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else if (lyricsPayload == null || lyricsPayload.lines.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MainSurface.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
                    .border(1.dp, SubtleBorderWhite, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lyrics,
                        contentDescription = null,
                        tint = SecondaryTextZinc,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "No synchronized lyrics available for this stream",
                        color = LabelPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MainSurface.copy(alpha = 0.88f),
                                ElevatedSurface.copy(alpha = 0.68f),
                                MainSurface.copy(alpha = 0.92f)
                            )
                        )
                    )
                    .border(1.dp, SubtleBorderWhite, RoundedCornerShape(24.dp))
                    .testTag("synchronized_lyrics_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(
                    items = lyricsPayload.lines,
                    key = { index, line -> "lyric_${lyricsPayload.trackId}_${index}_${line.startTimeMs}" }
                ) { index, line ->
                    val isCurrent = index == activeIndex
                    val isPast = index < activeIndex
                    val lineProgress = if (isCurrent) (snapshot?.activeLineProgress ?: 0f) else if (isPast) 1f else 0f

                    SynchronizedLyricLineRow(
                        line = line,
                        isCurrent = isCurrent,
                        isPast = isPast,
                        lineProgress = lineProgress,
                        accentColor = accentColor,
                        onClick = { onSeekTo(line.startTimeMs) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SynchronizedLyricLineRow(
    line: LyricLine,
    isCurrent: Boolean,
    isPast: Boolean,
    lineProgress: Float,
    accentColor: Color,
    onClick: () -> Unit
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (isCurrent) 1.03f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "lyric_scale"
    )

    val animatedAlpha by animateFloatAsState(
        targetValue = when {
            isCurrent -> 1.0f
            isPast -> 0.42f
            else -> 0.68f
        },
        animationSpec = tween(durationMillis = 240),
        label = "lyric_alpha"
    )

    Surface(
        color = if (isCurrent) {
            accentColor.copy(alpha = 0.18f)
        } else {
            Color.Transparent
        },
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .alpha(animatedAlpha)
            .then(
                if (isCurrent) {
                    Modifier.border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            colors = listOf(accentColor, SystemBlue, SecondaryViolet)
                        ),
                        shape = RoundedCornerShape(18.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .testTag("lyric_line_${line.index}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = if (isCurrent) 14.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isCurrent) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(HighlightFavoritePink)
                                .testTag("active_lyric_indicator_${line.index}")
                        )
                    } else if (line.isInstrumental) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = SecondaryTextZinc,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = formatDurationMillis(line.startTimeMs),
                        color = if (isCurrent) SystemBlue else SecondaryTextZinc,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                    )
                }

                if (isCurrent) {
                    Text(
                        text = "${(lineProgress * 100).toInt()}%",
                        color = SecondaryTextZinc,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Text(
                text = line.text,
                color = if (isCurrent) LabelPrimary else SecondaryTextZinc,
                fontSize = if (isCurrent) 19.sp else 16.sp,
                fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold,
                lineHeight = if (isCurrent) 25.sp else 22.sp
            )

            if (isCurrent) {
                LinearProgressIndicator(
                    progress = { lineProgress.coerceIn(0f, 1f) },
                    color = accentColor,
                    trackColor = Color.White.copy(alpha = 0.14f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .testTag("active_lyric_progress_bar")
                )
            }
        }
    }
}

/**
 * Compact live-synchronized lyrics ticker embedded directly in the main Now Playing screen.
 * Animates vertical transitions as [positionMs] crosses LRC timestamps and expands the
 * full [SynchronizedLyricsPanel] when tapped.
 */
@Composable
fun CompactSyncedLyricsTicker(
    lyricsPayload: SyncedLyricsPayload?,
    positionMs: Long,
    accentColor: Color,
    onExpandLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lines = lyricsPayload?.lines.orEmpty()
    val activeIndex = remember(lines, positionMs) {
        SyncedLyricsService.findActiveLineIndex(lines, positionMs)
    }
    val activeLine = lines.getOrNull(activeIndex)
    val upcomingLine = lines.getOrNull(activeIndex + 1)
    val progress = remember(activeLine, positionMs) {
        activeLine?.let { SyncedLyricsService.calculateLineProgress(it, positionMs) } ?: 0f
    }

    Surface(
        color = MainSurface,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, SubtleBorderWhite, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .clickable { onExpandLyrics() }
            .testTag("now_playing_compact_lyrics_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
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
                        imageVector = Icons.Default.Lyrics,
                        contentDescription = null,
                        tint = HighlightFavoritePink,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "LIVE SYNCED LYRICS",
                        color = SecondaryTextZinc,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.0.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Full View",
                        color = SystemBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.Default.OpenInFull,
                        contentDescription = "Expand Synchronized Lyrics",
                        tint = SystemBlue,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            AnimatedContent(
                targetState = activeLine?.text ?: "Synchronizing lyrics with audio stream...",
                transitionSpec = {
                    (slideInVertically { height -> height / 2 } + fadeIn(tween(220)))
                        .togetherWith(slideOutVertically { height -> -height / 2 } + fadeOut(tween(180)))
                },
                label = "compact_lyric_line_transition"
            ) { currentLineText ->
                Text(
                    text = currentLineText,
                    color = LabelPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("compact_active_lyric_text")
                )
            }

            if (upcomingLine != null) {
                Text(
                    text = upcomingLine.text,
                    color = SecondaryTextZinc.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                color = accentColor,
                trackColor = Color.White.copy(alpha = 0.12f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
        }
    }
}
