package dev.handheld.launcher.feature.collection

internal const val VisibleCategoryCount = 3

internal data class CollectionFilterWindow(val start: Int, val count: Int, val width: Int)

/** Whole category cells, including a full final window rather than an isolated last cell. */
internal fun collectionFilterWindow(
    available: Int, widths: List<Int>, firstIndex: Int, gap: Int, selectedIndex: Int? = null,
): CollectionFilterWindow {
    if (widths.isEmpty() || available <= 0) return CollectionFilterWindow(0, 0, 0)
    val selected = selectedIndex?.takeIf { it in widths.indices }
    for (count in minOf(VisibleCategoryCount, widths.size) downTo 1) {
        val starts = if (selected == null) listOf(firstIndex.coerceIn(0, widths.size - count)) else
            ((selected - count + 1).coerceAtLeast(0)..minOf(selected, widths.size - count))
                .sortedBy { kotlin.math.abs(it - firstIndex) }
        for (start in starts) {
            val width = widths.subList(start, start + count).sum() + gap * (count - 1)
            if (width <= available) return CollectionFilterWindow(start, count, width)
        }
    }
    // All filters remains available when even one complete cell cannot fit.
    return CollectionFilterWindow(firstIndex.coerceIn(widths.indices), 0, 0)
}
