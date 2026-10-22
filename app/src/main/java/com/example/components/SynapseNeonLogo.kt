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
 * Renders the custom Synapse "Neon Glass S" logo (dark obsidian glass squircle with glowing
 * cyan/violet/magenta rim and 3D liquid glass 'S' ribbon with specular white highlights).
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

        // 1. Deep Obsidian Glass Squircle Background
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF0A1226),
                    Color(0xFF060711),
                    Color(0xFF1A071E)
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            ),
            topLeft = Offset.Zero,
            size = Size(w, h),
            cornerRadius = CornerRadius(corner, corner)
        )

        // 2. Ambient Inner Glass Glow behind the S
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x557C5CFF),
                    Color(0x220A84FF),
                    Color.Transparent
                ),
                center = Offset(w * 0.5f, h * 0.5f),
                radius = w * 0.46f
            ),
            radius = w * 0.46f,
            center = Offset(w * 0.5f, h * 0.5f)
        )

        // 3. Outer Neon Glass Squircle Specular Rim (Cyan -> Blue -> Purple -> Pink)
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF64E9FF),
                    Color(0xFF0A84FF),
                    Color(0xFF8C52FF),
                    Color(0xFFFA2D48),
                    Color(0xFF3E90FF)
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            ),
            topLeft = Offset(w * 0.03f, h * 0.03f),
            size = Size(w * 0.94f, h * 0.94f),
            cornerRadius = CornerRadius(corner * 0.94f, corner * 0.94f),
            style = Stroke(width = w * 0.045f)
        )

        // 4. 3D Liquid Glass 'S' Ribbon Path
        val sRibbonPath = Path().apply {
            moveTo(w * 0.67f, h * 0.31f)
            cubicTo(
                w * 0.63f, h * 0.20f,
                w * 0.34f, h * 0.19f,
                w * 0.33f, h * 0.35f
            )
            cubicTo(
                w * 0.32f, h * 0.49f,
                w * 0.69f, h * 0.49f,
                w * 0.68f, h * 0.66f
            )
            cubicTo(
                w * 0.67f, h * 0.81f,
                w * 0.36f, h * 0.81f,
                w * 0.32f, h * 0.68f
            )
        }

        // Outer Neon Glow around the S ribbon
        drawPath(
            path = sRibbonPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0x6600E5FF),
                    Color(0x667C5CFF),
                    Color(0x66FA2D48)
                ),
                startY = h * 0.18f,
                endY = h * 0.82f
            ),
            style = Stroke(
                width = w * 0.22f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Core Liquid Glass S Ribbon Body
        drawPath(
            path = sRibbonPath,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF00F0FF),
                    Color(0xFF0A84FF),
                    Color(0xFF7C5CFF),
                    Color(0xFFE01E5A),
                    Color(0xFFFF4D6D)
                ),
                start = Offset(w * 0.35f, h * 0.20f),
                end = Offset(w * 0.65f, h * 0.82f)
            ),
            style = Stroke(
                width = w * 0.155f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // 5. Crisp White Translucent Glass Specular Highlight along the S spine
        val highlightPath = Path().apply {
            moveTo(w * 0.64f, h * 0.29f)
            cubicTo(
                w * 0.59f, h * 0.21f,
                w * 0.36f, h * 0.22f,
                w * 0.35f, h * 0.35f
            )
            cubicTo(
                w * 0.34f, h * 0.47f,
                w * 0.66f, h * 0.48f,
                w * 0.65f, h * 0.65f
            )
            cubicTo(
                w * 0.64f, h * 0.77f,
                w * 0.40f, h * 0.78f,
                w * 0.35f, h * 0.67f
            )
        }

        drawPath(
            path = highlightPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xEEFFFFFF),
                    Color(0x88C8B6FF),
                    Color(0xCCFFB3C6)
                ),
                startY = h * 0.20f,
                endY = h * 0.80f
            ),
            style = Stroke(
                width = w * 0.038f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
