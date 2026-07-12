package com.boom.anydown.ui.brutalist

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws a flat, hard-edged offset shadow (no blur) behind the content and
 * reserves the extra layout space for it — this is the whole neo-brutalist
 * look, replacing the earlier soft/blurred neumorphic shadows.
 *
 * Apply this BEFORE .background()/.border() in the modifier chain so those
 * paint onto the reduced content area rather than the full shadow bounds.
 */
fun Modifier.brutalistShadow(
    cornerRadius: Dp = 12.dp,
    offset: Dp = 6.dp,
    color: Color = Color(0xFFF5F5F0)
): Modifier = this
    .layout { measurable, constraints ->
        val offsetPx = offset.roundToPx()
        val reduced = Constraints(
            minWidth = (constraints.minWidth - offsetPx).coerceAtLeast(0),
            maxWidth = if (constraints.maxWidth == Constraints.Infinity) constraints.maxWidth else (constraints.maxWidth - offsetPx).coerceAtLeast(0),
            minHeight = (constraints.minHeight - offsetPx).coerceAtLeast(0),
            maxHeight = if (constraints.maxHeight == Constraints.Infinity) constraints.maxHeight else (constraints.maxHeight - offsetPx).coerceAtLeast(0)
        )
        val placeable = measurable.measure(reduced)
        layout(placeable.width + offsetPx, placeable.height + offsetPx) {
            placeable.placeRelative(0, 0)
        }
    }
    .drawWithContent {
        val offsetPx = offset.toPx()
        val cornerPx = cornerRadius.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(offsetPx, offsetPx),
            size = Size(size.width - offsetPx, size.height - offsetPx),
            cornerRadius = CornerRadius(cornerPx, cornerPx)
        )
        drawContent()
    }

/** Static container: shadow + fill + border, no press behavior. For cards, thumbnails, list rows. */
fun Modifier.brutalistBox(
    cornerRadius: Dp = 16.dp,
    shadowOffset: Dp = 6.dp,
    borderColor: Color = Color(0xFFF5F5F0),
    backgroundColor: Color = Color(0xFF1B1C24),
    borderWidth: Dp = 3.dp
): Modifier = this
    .brutalistShadow(cornerRadius, shadowOffset, borderColor)
    .background(backgroundColor, RoundedCornerShape(cornerRadius))
    .border(borderWidth, borderColor, RoundedCornerShape(cornerRadius))

/**
 * The shadow offset shrinks toward [pressedOffset] while a button is held
 * down, giving the "sinking into the shadow" press feel used throughout
 * the app instead of a neumorphic sunken effect.
 */
@Composable
fun rememberPressedShadowOffset(
    interactionSource: MutableInteractionSource,
    restingOffset: Dp = 6.dp,
    pressedOffset: Dp = 1.dp
): State<Dp> {
    val isPressed by interactionSource.collectIsPressedAsState()
    return animateDpAsState(if (isPressed) pressedOffset else restingOffset, label = "brutalistPress")
}

/** Interactive container: same as [brutalistBox] but with click + press-shrink behavior baked in. */
@Composable
fun Modifier.brutalistClickable(
    onClick: () -> Unit,
    cornerRadius: Dp = 16.dp,
    shadowOffset: Dp = 6.dp,
    borderColor: Color = Color(0xFFF5F5F0),
    backgroundColor: Color = Color(0xFF1B1C24),
    borderWidth: Dp = 3.dp,
    enabled: Boolean = true
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val offset by rememberPressedShadowOffset(interactionSource, shadowOffset)
    return this
        .brutalistShadow(cornerRadius, offset, borderColor)
        .background(backgroundColor, RoundedCornerShape(cornerRadius))
        .border(borderWidth, borderColor, RoundedCornerShape(cornerRadius))
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}
