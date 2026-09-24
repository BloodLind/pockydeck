package dev.handheld.launcher.feature.collection

import kotlin.math.floor

/** All values use the same units. Remaining width is shared by cards, never hidden in cells. */
internal fun collectionGridColumns(width: Float, targetWidth: Float, gap: Float): Int {
    require(width.isFinite() && width >= 0 && targetWidth.isFinite() && targetWidth > 0 && gap.isFinite() && gap >= 0)
    return floor((width + gap) / (targetWidth + gap)).toInt().coerceIn(1, 10)
}
