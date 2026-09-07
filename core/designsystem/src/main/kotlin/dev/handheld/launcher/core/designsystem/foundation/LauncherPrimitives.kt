package dev.handheld.launcher.core.designsystem.foundation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme

/** Descendants inherit the appropriate foreground for their actual surface. */
val LocalLauncherContentColor = staticCompositionLocalOf { Color.Unspecified }

@Composable
fun launcherContentColor(): Color =
    LocalLauncherContentColor.current.takeUnless { it == Color.Unspecified }
        ?: LauncherTheme.colors.textPrimary

@Composable
fun LauncherText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LauncherTheme.typography.body,
    color: Color = launcherContentColor(),
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    unavailable: Boolean = false,
    unavailableReason: String = "Unavailable",
) {
    BasicText(
        text = text,
        modifier = modifier.then(if (unavailable) Modifier.semantics {
            stateDescription = unavailableReason
        } else Modifier),
        style = style.copy(color = color),
        maxLines = maxLines,
        overflow = overflow,
    )
}

/** Null descriptions are decorative; they neither announce a glyph nor hide a parent control. */
@Composable
fun LauncherIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = launcherContentColor(),
) {
    Image(
        painter = rememberVectorPainter(imageVector),
        contentDescription = null,
        modifier = modifier.clearAndSetSemantics {
            if (contentDescription != null) this.contentDescription = contentDescription
        },
        colorFilter = ColorFilter.tint(tint),
    )
}

/** State-driven surface. The consuming control owns activation and actual focus. */
@Composable
fun LauncherSurface(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    focused: Boolean = false,
    pressed: Boolean = false,
    enabled: Boolean = true,
    unavailable: Boolean = false,
    unavailableReason: String = "Unavailable",
    contentDescription: String? = null,
    shape: Shape = RoundedCornerShape(LauncherTheme.shapes.homeOuter),
    focusFrameWidth: Dp = LauncherTheme.depth.focusedElevation / 2f,
    focusLift: Dp = LauncherTheme.depth.focusLift,
    content: @Composable BoxScope.() -> Unit,
) {
    FocusFrame(
        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).then(modifier),
        selected = selected,
        focused = focused,
        pressed = pressed,
        enabled = enabled,
        unavailable = unavailable,
        unavailableReason = unavailableReason,
        contentDescription = contentDescription,
        shape = shape,
        focusFrameWidth = focusFrameWidth,
        focusLift = focusLift,
        content = content,
    )
}

/**
 * The allocation includes frame padding plus lift/depth slack on both sides. Square allocations
 * therefore retain square content. Focus changes drawing/placement only, never measurement.
 * The frame appears immediately; only decorative lift is suppressed for reduced motion.
 * This wrapper does not create a focus target or install an activation handler.
 */
@Composable
fun FocusFrame(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    focused: Boolean = false,
    pressed: Boolean = false,
    enabled: Boolean = true,
    unavailable: Boolean = false,
    unavailableReason: String = "Unavailable",
    contentDescription: String? = null,
    shape: Shape = RoundedCornerShape(LauncherTheme.shapes.homeOuter),
    focusFrameWidth: Dp = LauncherTheme.depth.focusedElevation / 2f,
    focusLift: Dp = LauncherTheme.depth.focusLift,
    content: @Composable BoxScope.() -> Unit,
) {
    require(focusFrameWidth.value.isFinite() && focusFrameWidth >= 0.dp)
    require(focusLift.value.isFinite() && focusLift >= 0.dp)
    val colors = LauncherTheme.colors
    val base = if (selected) colors.destinationSelected else colors.surfaceCard
    val background = if (pressed) colors.borderEmphasis.compositeOver(base) else base
    val foreground = when {
        selected -> colors.destinationSelectedContent
        unavailable || !enabled -> colors.textSecondary
        else -> colors.textPrimary
    }
    val lift = if (focused && !LauncherTheme.motion.reducedMotion) focusLift else 0.dp
    val lowerEdge = if (focused) minOf(focusFrameWidth, focusLift + lift) else focusLift
    val edgeColor = if (focused) colors.focusLowerEdge else colors.surfaceArtwork
    val highlight = Color.White.copy(alpha = if (focused) .24f else .08f)
    val elevation = LauncherTheme.depth.cardElevation
    Box(
        modifier = modifier.clipToBounds().semantics(mergeDescendants = true) {
            this.selected = selected
            if (contentDescription != null) this.contentDescription = contentDescription
            if (!enabled) disabled()
            if (unavailable) stateDescription = unavailableReason
        },
        propagateMinConstraints = true,
    ) {
        Box(
            modifier = Modifier
                .padding(focusLift)
                .offset(y = -lift)
                .shadow(elevation, shape, clip = false)
                .drawWithCache {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    onDrawBehind {
                        translate(top = lowerEdge.toPx()) { drawOutline(outline, edgeColor) }
                    }
                }
                .background(background, shape)
                .then(if (focused) Modifier.border(focusFrameWidth, colors.focus, shape) else Modifier)
                .drawWithCache {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    onDrawWithContent {
                        drawContent()
                        clipRect(bottom = 1.dp.toPx()) {
                            drawOutline(outline, highlight, style = Stroke(2.dp.toPx()))
                        }
                    }
                }
                .padding(focusFrameWidth),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalLauncherContentColor provides foreground) { content() }
        }
    }
}
