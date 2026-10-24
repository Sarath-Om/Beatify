package com.example.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BeatifyAppleCrimson
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.LabelPrimary
import com.example.ui.theme.MainSurface
import com.example.ui.theme.SecondaryTextZinc
import com.example.ui.theme.SecondaryViolet
import com.example.ui.theme.SubtleBorderWhite
import com.example.ui.theme.SystemBlue
import com.example.ui.theme.SystemGreen
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Studio Equalizer Presets synchronized with `MediaPlaybackService` (`MediaSessionService`).
 */
enum class BeatifyEqPreset(
    val wireName: String,
    val displayName: String,
    val bandGains: FloatArray
) {
    STUDIO_MASTER(
        wireName = "Studio Master",
        displayName = "Studio Master",
        bandGains = floatArrayOf(
            1.00f, 1.02f, 1.04f, 1.02f, 1.00f, 0.98f, 1.00f, 1.03f,
            1.05f, 1.06f, 1.04f, 1.02f, 1.05f, 1.08f, 1.06f, 1.02f
        )
    ),
    BASS_BOOST(
        wireName = "Bass Boost",
        displayName = "Bass Boost",
        bandGains = floatArrayOf(
            1.38f, 1.35f, 1.28f, 1.20f, 1.10f, 1.00f, 0.95f, 0.94f,
            0.96f, 0.98f, 1.00f, 1.02f, 1.04f, 1.05f, 1.04f, 1.00f
        )
    ),
    VOCAL_CLARITY(
        wireName = "Vocal Clarity",
        displayName = "Vocal Clarity",
        bandGains = floatArrayOf(
            0.84f, 0.86f, 0.90f, 0.96f, 1.06f, 1.18f, 1.28f, 1.34f,
            1.32f, 1.26f, 1.18f, 1.10f, 1.04f, 0.98f, 0.94f, 0.90f
        )
    ),
    ELECTRONIC_VIVID(
        wireName = "Electronic",
        displayName = "Electronic",
        bandGains = floatArrayOf(
            1.32f, 1.30f, 1.22f, 1.08f, 0.94f, 0.88f, 0.92f, 1.02f,
            1.12f, 1.20f, 1.26f, 1.30f, 1.34f, 1.32f, 1.28f, 1.22f
        )
    ),
    LATE_NIGHT(
        wireName = "Late Night",
        displayName = "Late Night",
        bandGains = floatArrayOf(
            0.92f, 0.94f, 0.98f, 1.02f, 1.04f, 1.06f, 1.06f, 1.04f,
            1.02f, 1.00f, 0.98f, 0.96f, 0.94f, 0.90f, 0.88f, 0.85f
        )
    );

    companion object {
        fun fromName(name: String?): BeatifyEqPreset =
            entries.firstOrNull { it.wireName.equals(name, ignoreCase = true) } ?: STUDIO_MASTER
    }
}

private val FREQUENCY_LABELS = listOf("32", "64", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

/**
 * Real-time animated Visual Equalizer synchronized with the audio stream processed by `MediaPlaybackService`
 * (`MediaSessionService`).
 *
 * Features:
 * - 16-band spectrum analyzer bars driven by `equalizerBands` emitted by `MediaPlaybackService`,
 *   modulated by harmonic micro-oscillations while `isPlaying == true`, and smoothly damped when paused.
 * - Floating peak-hold indicators, harmonic spline wave overlay, and subtle bottom reflection.
 * - Interactive acoustic EQ preset selector (`Studio Master`, `Bass Boost`, `Vocal Clarity`, `Electronic`, `Late Night`)
 *   that updates the active `MediaPlaybackService` equalizer profile.
 */
@Composable
fun NowPlayingVisualEqualizer(
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMs: Long,
    playbackSpeed: Float,
    volume: Float,
    accentColor: Color,
    equalizerBands: List<Float>,
    activePresetName: String,
    audioSessionId: Int,
    onSelectPreset: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activePreset = BeatifyEqPreset.fromName(activePresetName)
    val infiniteTransition = rememberInfiniteTransition(label = "beatify_eq_InfiniteTransition")

    // Continuous phase clock for 60fps harmonic micro-motion between MediaSessionService state ticks
    val harmonicPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (1150 / playbackSpeed.coerceIn(0.5f, 2.0f)).toInt().coerceAtLeast(450),
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "eq_harmonic_phase"
    )

    val secondaryPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 780,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq_secondary_phase"
    )

    val masterActivityGain by animateFloatAsState(
        targetValue = when {
            isPlaying && !isBuffering -> volume.coerceIn(0.25f, 1.0f)
            isBuffering -> 0.22f
            else -> 0.08f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "eq_master_activity_gain"
    )

    val normalizedBands = if (equalizerBands.size >= 16) {
        equalizerBands.take(16)
    } else {
        List(16) { index -> equalizerBands.getOrElse(index) { 0.12f } }
    }

    // Smoothly animate each of the 16 bands from MediaPlaybackService telemetry
    val animatedBands = normalizedBands.mapIndexed { idx, rawLevel ->
        val presetGain = activePreset.bandGains.getOrElse(idx) { 1.0f }
        val target = if (isPlaying) {
            (rawLevel * presetGain).coerceIn(0.10f, 1.0f)
        } else {
            0.07f + (idx % 3) * 0.015f
        }
        animateFloatAsState(
            targetValue = target,
            animationSpec = spring(
                dampingRatio = 0.68f,
                stiffness = Spring.StiffnessMedium
            ),
            label = "eq_band_$idx"
        ).value
    }

    Surface(
        color = MainSurface.copy(alpha = 0.86f),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = if (isPlaying) 0.48f else 0.20f),
                        SystemBlue.copy(alpha = if (isPlaying) 0.38f else 0.16f),
                        SubtleBorderWhite
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .clip(RoundedCornerShape(20.dp))
            .testTag("now_playing_visual_equalizer")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Status Header: Live Stream Sync Badge + Audio Session Indicator
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
                        imageVector = if (isPlaying) Icons.Default.GraphicEq else Icons.Default.Equalizer,
                        contentDescription = "Visual Equalizer",
                        tint = if (isPlaying) BeatifyAppleCrimson else SecondaryTextZinc,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "SPECTRUM ANALYZER",
                        color = LabelPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isPlaying -> SystemGreen
                                    isBuffering -> SystemBlue
                                    else -> SecondaryTextZinc.copy(alpha = 0.5f)
                                }
                            )
                    )
                    Text(
                        text = when {
                            isPlaying -> "MEDIA3 STREAM SYNC • ${activePreset.displayName.uppercase()}"
                            isBuffering -> "BUFFERING STREAM..."
                            else -> "STANDBY • ${activePreset.displayName.uppercase()}"
                        },
                        color = if (isPlaying) SystemGreen else SecondaryTextZinc,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // 16-Band Spectrum Canvas with Peak Caps, Reflection & Spline Envelope
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .testTag("now_playing_equalizer_canvas")
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                if (canvasWidth <= 0f || canvasHeight <= 0f) return@Canvas

                val bandCount = 16
                val mainRegionHeight = canvasHeight * 0.78f
                val reflectionRegionTop = mainRegionHeight + 3.dp.toPx()
                val reflectionRegionHeight = (canvasHeight - reflectionRegionTop).coerceAtLeast(4f)

                val horizontalSpacing = 4.dp.toPx()
                val totalSpacing = horizontalSpacing * (bandCount - 1)
                val barWidth = ((canvasWidth - totalSpacing) / bandCount).coerceAtLeast(4f)
                val cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)

                // Subtle horizontal dB grid lines
                val gridLevels = listOf(0.25f, 0.55f, 0.85f)
                gridLevels.forEach { level ->
                    val y = mainRegionHeight * (1f - level)
                    drawLine(
                        color = Color.White.copy(alpha = 0.05f),
                        start = Offset(0f, y),
                        end = Offset(canvasWidth, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                val splinePath = Path()
                val timeSeed = (positionMs / 90L).toFloat()

                for (i in 0 until bandCount) {
                    val baseBand = animatedBands[i]
                    val liveOscillation = if (isPlaying && !isBuffering) {
                        val primaryWave = sin(harmonicPhase + i * 0.52f + (timeSeed % 6.28f) * 0.15f)
                        val overtoneWave = cos(secondaryPhase * 1.3f - i * 0.77f)
                        (primaryWave * 0.14f + overtoneWave * 0.09f) * masterActivityGain
                    } else {
                        0f
                    }

                    val normalizedMagnitude = (baseBand * masterActivityGain + liveOscillation)
                        .coerceIn(0.08f, 0.98f)
                    val barHeight = (normalizedMagnitude * mainRegionHeight).coerceAtLeast(5.dp.toPx())
                    val barLeft = i * (barWidth + horizontalSpacing)
                    val barTop = (mainRegionHeight - barHeight).coerceAtLeast(0f)
                    val barCenterX = barLeft + barWidth / 2f

                    if (i == 0) {
                        splinePath.moveTo(barCenterX, barTop)
                    } else {
                        splinePath.lineTo(barCenterX, barTop)
                    }

                    // Primary Spectrum Bar with Vertical Multi-Stop Studio Gradient
                    val barBrush = Brush.verticalGradient(
                        colors = listOf(
                            BeatifyAppleCrimson,
                            accentColor,
                            SystemBlue,
                            SecondaryViolet.copy(alpha = 0.85f)
                        ),
                        startY = 0f,
                        endY = mainRegionHeight
                    )

                    drawRoundRect(
                        brush = barBrush,
                        topLeft = Offset(barLeft, barTop),
                        size = Size(barWidth, barHeight),
                        cornerRadius = cornerRadius
                    )

                    // Floating Peak-Hold Cap above each bar
                    val peakOffset = if (isPlaying) {
                        val peakBounce = abs(sin(harmonicPhase * 0.8f + i * 0.4f)) * 5.dp.toPx()
                        (barTop - 4.dp.toPx() - peakBounce).coerceAtLeast(0f)
                    } else {
                        (barTop - 3.dp.toPx()).coerceAtLeast(0f)
                    }

                    drawRoundRect(
                        color = if (normalizedMagnitude > 0.72f) BeatifyAppleCrimson else LabelPrimary.copy(alpha = 0.85f),
                        topLeft = Offset(barLeft, peakOffset),
                        size = Size(barWidth, 2.5.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )

                    // Mirrored Sub-Surface Reflection
                    val reflectionHeight = (barHeight * 0.24f).coerceAtMost(reflectionRegionHeight)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                SystemBlue.copy(alpha = 0.24f),
                                Color.Transparent
                            ),
                            startY = reflectionRegionTop,
                            endY = reflectionRegionTop + reflectionHeight
                        ),
                        topLeft = Offset(barLeft, reflectionRegionTop),
                        size = Size(barWidth, reflectionHeight),
                        cornerRadius = cornerRadius
                    )
                }

                // Luminous Harmonic Envelope Line connecting bar peaks when playing
                if (isPlaying) {
                    drawPath(
                        path = splinePath,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                BeatifyAppleCrimson.copy(alpha = 0.55f),
                                SystemBlue.copy(alpha = 0.65f),
                                SecondaryViolet.copy(alpha = 0.55f)
                            )
                        ),
                        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // Frequency Axis Labels (32Hz -> 16kHz)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FREQUENCY_LABELS.forEach { freq ->
                    Text(
                        text = freq,
                        color = SecondaryTextZinc.copy(alpha = 0.75f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Interactive Acoustic EQ Preset Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BeatifyEqPreset.entries.forEach { preset ->
                    val selected = preset == activePreset
                    val slug = preset.wireName.lowercase().replace(' ', '_')
                    Surface(
                        color = if (selected) {
                            BeatifyAppleCrimson.copy(alpha = 0.22f)
                        } else {
                            ElevatedSurface.copy(alpha = 0.75f)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = if (selected) BeatifyAppleCrimson else SubtleBorderWhite,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectPreset(preset.wireName) }
                            .testTag("eq_preset_chip_$slug")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(BeatifyAppleCrimson)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                            }
                            Text(
                                text = preset.displayName,
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
}
