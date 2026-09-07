package dev.handheld.launcher.core.designsystem.foundation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
@Immutable
data class ShellMetricsInput(
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val fontScale: Float = 1f,
)

@Immutable
data class ShellBounds(val left: Dp, val top: Dp, val right: Dp, val bottom: Dp) {
    val width: Dp get() = (right - left).coerceAtLeast(0.dp)
    val height: Dp get() = (bottom - top).coerceAtLeast(0.dp)
}

/** One root-computed geometry snapshot shared by every destination. */
@Immutable
data class ShellMetrics(
    val windowWidth: Dp,
    val windowHeight: Dp,
    val density: Float,
    val fontScale: Float,
    val compact: Boolean,
    val gutter: Dp,
    val statusBounds: ShellBounds,
    val contentBounds: ShellBounds,
    val dockBounds: ShellBounds,
    val footerBounds: ShellBounds,
    val homeCardArtworkSize: Dp,
    val homeCardGap: Dp,
    val focusFrameReservation: Dp,
    val focusLiftReservation: Dp,
    val homeCardAllocatedSize: Dp,
    val homeCardAllocatedBounds: ShellBounds,
    val metadataReservation: Dp,
    val hasUsableHomeCard: Boolean,
    val minimumTouchTarget: Dp = 48.dp,
) {
    val controlsCanReachMinimumTouchTarget: Boolean
        get() = dockBounds.height >= minimumTouchTarget && footerBounds.height >= minimumTouchTarget &&
            dockBounds.width >= minimumTouchTarget && footerBounds.width >= minimumTouchTarget

    companion object {
        /**
         * Calculates all shell anchors from one window snapshot. Reference proportions are
         * initial guidance from Home; they are intentionally not device-calibrated defaults.
         */
        fun calculate(input: ShellMetricsInput): ShellMetrics {
            val pixelWidth = input.widthPx.coerceAtLeast(0).toDouble()
            val pixelHeight = input.heightPx.coerceAtLeast(0).toDouble()
            val requestedDensity = input.density.takeIf { it.isFinite() && it > 0f } ?: 1f
            val safeDensity = requestedDensity.takeIf {
                (pixelWidth / it).toFloat().isFinite() && (pixelHeight / it).toFloat().isFinite()
            } ?: 1f
            val safeFontScale = input.fontScale.takeIf { it.isFinite() && it > 0f } ?: 1f
            // Double intermediates avoid overflow when malformed-but-finite scales are supplied.
            val w = pixelWidth / safeDensity
            val h = pixelHeight / safeDensity
            val fs = safeFontScale.toDouble()
            fun Double.asDp(): Dp = coerceIn(0.0, Float.MAX_VALUE.toDouble()).toFloat().dp
            val width = w.asDp()
            val height = h.asDp()
            val compact = h < 600.0 || fs > 1.15 || w < 900.0
            val g = w * .0375
            val gutter = g.asDp()

            // Preserve Home's 85.2% dock center / 92.8% footer start in the standard fixture.
            // Compact layouts spend height on readable labels and real target areas first.
            val desiredStatus = maxOf(h * if (compact) .16 else .13, 18.0 * fs + 16.0).coerceAtMost(h)
            val desiredDock = maxOf(h * if (compact) .18 else .152, 68.0).coerceAtMost(h)
            val desiredFooter = maxOf(h * if (compact) .10 else .072, 48.0, 18.0 * fs + 16.0).coerceAtMost(h)
            val total = desiredStatus + desiredDock + desiredFooter
            val bandScale = if (total > h && total > 0.0) h / total else 1.0
            val status = desiredStatus * bandScale
            val dock = desiredDock * bandScale
            val footer = desiredFooter * bandScale
            val contentEnd = (h - dock - footer).coerceIn(status, h)
            val statusHeight = status.asDp()
            val dockHeight = dock.asDp()
            val contentTop = status.asDp()
            val contentBottom = contentEnd.asDp()

            val contentWidth = (w - g * 2.0).coerceAtLeast(0.0)
            // Two 40sp Home-title lines, a 14sp platform line and 12dp separation.
            // This text region remains useful for a short-window fallback when artwork is absent.
            val metadata = (94.0 * fs + 12.0).coerceAtMost(contentEnd - status).coerceAtLeast(0.0)
            val metadataEnd = status + metadata
            val provisionalCardTop = if (compact) metadataEnd else maxOf(metadataEnd, h * .356)
            val frame = 8.0
            val lift = 4.0
            val maxArtwork = (minOf(contentWidth, contentEnd - provisionalCardTop) - frame * 2.0 - lift)
                .coerceAtLeast(0.0)
            // The reference measures the OUTER frame. Artwork is the square inside that frame.
            val desiredArtwork = maxOf(w * .187 - frame * 2.0, 48.0)
            val artwork = minOf(desiredArtwork, maxArtwork)
            val hasCard = artwork >= 48.0
            val cardSize = if (hasCard) artwork.asDp() else 0.dp
            val actualFrame = if (hasCard) frame.asDp() else 0.dp
            val actualLift = if (hasCard) lift.asDp() else 0.dp
            val allocated = if (hasCard) (artwork + frame * 2.0 + lift).asDp() else 0.dp
            val cardTop = provisionalCardTop.coerceIn(status, contentEnd).asDp()
            // The square allocation includes lift slack; subtract it from the inter-slot gap.
            val gapReference = (w * .019 - lift).coerceAtLeast(0.0).asDp()
            return ShellMetrics(
                windowWidth = width,
                windowHeight = height,
                density = safeDensity,
                fontScale = safeFontScale,
                compact = compact,
                gutter = gutter,
                statusBounds = ShellBounds(0.dp, 0.dp, width, statusHeight),
                contentBounds = ShellBounds(gutter, contentTop, width - gutter, contentBottom),
                dockBounds = ShellBounds(0.dp, contentBottom, width, contentBottom + dockHeight),
                footerBounds = ShellBounds(0.dp, contentBottom + dockHeight, width, height),
                homeCardArtworkSize = cardSize,
                homeCardGap = if (hasCard) gapReference.coerceAtLeast(0.dp) else 0.dp,
                focusFrameReservation = actualFrame,
                focusLiftReservation = actualLift,
                homeCardAllocatedSize = allocated,
                homeCardAllocatedBounds = ShellBounds(
                    gutter,
                    cardTop,
                    (gutter + allocated).coerceAtMost(width - gutter),
                    (cardTop + allocated).coerceAtMost(contentBottom),
                ),
                metadataReservation = metadata.asDp(),
                hasUsableHomeCard = hasCard,
            )
        }
    }
}
