package com.example.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val Ios18DarkColorScheme = darkColorScheme(
    primary = SystemBlue,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = PrimaryContainerBlue,
    onPrimaryContainer = Color(0xFF002957),
    secondary = NeonPink,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = SecondaryContainerCrimson,
    onSecondaryContainer = Color(0xFFFFE7E6),
    tertiary = SystemGreen,
    onTertiary = Color(0xFF003910),
    background = OledBlack,
    onBackground = OnSurfaceText,
    surface = OledBlack,
    onSurface = OnSurfaceText,
    surfaceVariant = SurfaceContainerHighest,
    onSurfaceVariant = OnSurfaceVariantText,
    outline = OutlineText,
    outlineVariant = OutlineVariantBorder,
    error = ErrorSoft
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = Ios18DarkColorScheme,
        typography = Typography,
        content = content
    )
}

/**
 * Tactile iOS 18 press-scale (0.97) interaction modifier with spring physics.
 */
fun Modifier.iosPressScale(
    targetScale: Float = 0.97f,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) targetScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "iosPressScale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )
}

/**
 * iOS 18 Frosted Glass Card surface with 1px inner top specular highlight.
 */
fun Modifier.iosGlassCard(
    cornerRadius: Dp = 20.dp,
    fillColor: Color = Color(0xB81F1F21),
    borderColor: Color = SpecularBorderLight
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(fillColor)
    .border(0.75.dp, borderColor, RoundedCornerShape(cornerRadius))
    .drawWithContent {
        drawContent()
        // Specular top inner edge highlight: inset 0 1px 0 0 rgba(255,255,255,0.14)
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    Color(0x26FFFFFF),
                    Color(0x26FFFFFF),
                    Color.Transparent
                )
            ),
            start = Offset(cornerRadius.toPx() * 0.5f, 1f),
            end = Offset(size.width - cornerRadius.toPx() * 0.5f, 1f),
            strokeWidth = 1.dp.toPx()
        )
    }

/**
 * iOS 18 Floating Glass Pill (used for Address Bar, Dynamic Island, Bottom Toolbars).
 */
fun Modifier.iosGlassPill(
    shape: Shape = RoundedCornerShape(999.dp),
    fillColor: Color = Color(0xCC26262A),
    borderColor: Color = Color(0x29FFFFFF)
): Modifier = this
    .clip(shape)
    .background(fillColor)
    .border(0.75.dp, borderColor, shape)
    .drawWithContent {
        drawContent()
        drawLine(
            color = Color(0x29FFFFFF),
            start = Offset(24f, 1f),
            end = Offset(size.width - 24f, 1f),
            strokeWidth = 1.dp.toPx()
        )
    }
