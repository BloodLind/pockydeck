package dev.handheld.launcher.feature.collection

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme

internal data class CatalogScrollPosition(val progress: Float, val visibleFraction: Float)

/** Estimate uniform row extent; native scroll boundaries keep both endpoints exact. */
internal fun catalogScrollPosition(
    totalItems: Int, columns: Int, firstIndex: Int, firstOffset: Float,
    rowHeight: Float, rowGap: Float, viewportHeight: Float,
    canScrollBackward: Boolean, canScrollForward: Boolean,
): CatalogScrollPosition {
    if (totalItems <= 0 || columns <= 0 || rowHeight <= 0 || viewportHeight <= 0 ||
        (!canScrollBackward && !canScrollForward)) return CatalogScrollPosition(0f, 1f)
    val rows = (totalItems + columns - 1) / columns
    val stride = rowHeight + rowGap.coerceAtLeast(0f)
    val extent = (rows * stride - rowGap).coerceAtLeast(viewportHeight)
    val fraction = (viewportHeight / extent).coerceIn(0f, 1f)
    val progress = when {
        !canScrollBackward -> 0f
        !canScrollForward -> 1f
        extent <= viewportHeight -> 0f
        else -> ((firstIndex / columns * stride - firstOffset) / (extent - viewportHeight)).coerceIn(0f, 1f)
    }
    return CatalogScrollPosition(progress, fraction)
}

/** Read-only rail. Scroll offsets are read during drawing, not in catalog composition. */
@Composable
internal fun CatalogScrollIndicator(state: LazyGridState, columns: Int, modifier: Modifier = Modifier) {
    val showIndicator by remember(state) { derivedStateOf {
        val layout = state.layoutInfo
        // Padding and slightly clipped edge rows can leave a small scroll range
        // even when every catalog row is already represented on screen.
        val visibleCount = layout.visibleItemsInfo.count {
            it.offset.y + it.size.height > layout.viewportStartOffset && it.offset.y < layout.viewportEndOffset
        }
        visibleCount > 0 && visibleCount < layout.totalItemsCount &&
            (state.canScrollBackward || state.canScrollForward)
    } }
    if (!showIndicator) return
    val trackColor = LauncherTheme.colors.textPrimary.copy(alpha = .12f)
    val thumbColor = LauncherTheme.colors.textSecondary.copy(alpha = .85f)
    fun position(): CatalogScrollPosition {
        val layout = state.layoutInfo
        val first = layout.visibleItemsInfo.firstOrNull { it.offset.y + it.size.height > layout.viewportStartOffset }
            ?: return CatalogScrollPosition(0f, 1f)
        return catalogScrollPosition(layout.totalItemsCount, columns, first.index,
            (first.offset.y - layout.viewportStartOffset).toFloat(), first.size.height.toFloat(),
            layout.mainAxisItemSpacing.toFloat(), (layout.viewportEndOffset - layout.viewportStartOffset).toFloat(),
            state.canScrollBackward, state.canScrollForward)
    }
    Canvas(modifier.width(10.dp).fillMaxHeight().padding(vertical = LauncherTheme.spacing.xs)
        .testTag("catalog-scroll-indicator").semantics {
            contentDescription = "Catalog position"
            progressBarRangeInfo = ProgressBarRangeInfo(position().progress, 0f..1f)
            val layout = state.layoutInfo
            val visible = layout.visibleItemsInfo.filter {
                it.offset.y + it.size.height > layout.viewportStartOffset && it.offset.y < layout.viewportEndOffset
            }
            if (visible.isNotEmpty()) stateDescription = "${visible.first().index + 1}–${visible.last().index + 1} of ${layout.totalItemsCount}"
        }) {
        val position = position()
        val trackWidth = 3.dp.toPx().coerceAtMost(size.width)
        val thumbWidth = 4.dp.toPx().coerceAtMost(size.width)
        val thumbHeight = (size.height * position.visibleFraction).coerceIn(20.dp.toPx().coerceAtMost(size.height), size.height)
        drawRoundRect(trackColor, Offset((size.width - trackWidth) / 2f, 0f),
            Size(trackWidth, size.height), CornerRadius(trackWidth))
        drawRoundRect(thumbColor, Offset((size.width - thumbWidth) / 2f, (size.height - thumbHeight) * position.progress),
            Size(thumbWidth, thumbHeight), CornerRadius(thumbWidth))
    }
}
