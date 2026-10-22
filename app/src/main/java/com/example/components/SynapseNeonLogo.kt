package com.example.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Renders the custom Beatify 3D Liquid Glass logo (OLED obsidian squircle with Apple Music
 * crimson `#FA2D48`, electric blue `#0A84FF`, and violet `#7C5CFF` acoustic wave & 'B' ribbon).
 */
@Composable
fun SynapseNeonLogo(
    size: Dp = 38.dp,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(size)
            .testTag("synapse_neon_glass_logo")
    ) {
        val w = this.size.width
        val h = this.size.height
        val corner = w * 0.26f

        // 1. Deep OLED Obsidian Squircle Background
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF180810),
                    Color(0xFF07070C),
                    Color(0xFF0D1024)
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            ),
            topLeft = Offset.Zero,
            size = Size(w, h),
            cornerRadius = CornerRadius(corner, corner)
        )

        // 2. Ambient Inner Crimson/Violet Glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x66FA2D48),
                    Color(0x337C5CFF),
                    Color.Transparent
                ),
                center = Offset(w * 0.52f, h * 0.5f),
                radius = w * 0.46f
            ),
            radius = w * 0.46f,
            center = Offset(w * 0.52f, h * 0.5f)
        )

        // 3. Outer Glass Squircle Specular Rim (Crimson -> Violet -> Electric Blue)
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFA2D48),
                    Color(0xFFFF5E7E),
                    Color(0xFF7C5CFF),
                    Color(0xFF0A84FF)
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            ),
            topLeft = Offset(w * 0.03f, h * 0.03f),
            size = Size(w * 0.94f, h * 0.94f),
            cornerRadius = CornerRadius(corner * 0.94f, corner * 0.94f),
            style = Stroke(width = w * 0.045f)
        )

        // 4. Stylized Beatify 'B' & Acoustic Pulse Stem
        val bPath = Path().apply {
            moveTo(w * 0.34f, h * 0.24f)
            lineTo(w * 0.34f, h * 0.76f)
            moveTo(w * 0.34f, h * 0.24f)
            cubicTo(
                w * 0.66f, h * 0.22f,
                w * 0.68f, h * 0.47f,
                w * 0.38f, h * 0.49f
            )
            cubicTo(
                w * 0.72f, h * 0.50f,
                w * 0.72f, h * 0.78f,
                w * 0.34f, h * 0.76f
            )
        }

        // Outer Crimson/Violet Glow
        drawPath(
            path = bPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0x66FA2D48),
                    Color(0x667C5CFF),
                    Color(0x660A84FF)
                ),
                startY = h * 0.20f,
                endY = h * 0.80f
            ),
            style = Stroke(
                width = w * 0.19f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Core Liquid Glass 'B' Ribbon Body
        drawPath(
            path = bPath,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFF4D6D),
                    Color(0xFFFA2D48),
                    Color(0xFF7C5CFF),
                    Color(0xFF0A84FF)
                ),
                start = Offset(w * 0.30f, h * 0.22f),
                end = Offset(w * 0.70f, h * 0.78f)
            ),
            style = Stroke(
                width = w * 0.125f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // 5. Crisp White Specular Highlight along the top loop
        val highlightPath = Path().apply {
            moveTo(w * 0.34f, h * 0.26f)
            lineTo(w * 0.34f, h * 0.72f)
        }
        drawPath(
            path = highlightPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xEEFFFFFF),
                    Color(0x88FFB3C6),
                    Color.Transparent
                ),
                startY = h * 0.24f,
                endY = h * 0.74f
            ),
            style = Stroke(
                width = w * 0.035f,
                cap = StrokeCap.Round
            )
        )
    }
}
