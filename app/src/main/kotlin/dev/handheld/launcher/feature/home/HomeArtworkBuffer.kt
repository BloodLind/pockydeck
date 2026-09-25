package dev.handheld.launcher.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import dev.handheld.launcher.ui.artwork.ArtworkDecodePolicy
import dev.handheld.launcher.ui.artwork.LocalArtworkLoadingAllowed
import dev.handheld.launcher.ui.artwork.LocalArtworkLoadOrder
import dev.handheld.launcher.ui.artwork.rememberArtworkLoadOrder
import dev.handheld.launcher.ui.artwork.enriched.rememberEnrichedArtwork
import dev.handheld.launcher.ui.artwork.local.AndroidIconLoader
import dev.handheld.launcher.ui.artwork.local.rememberAndroidIconPainter
import dev.handheld.launcher.ui.presentation.TileArtwork
import dev.handheld.launcher.ui.presentation.TileUiModel

// Twenty 512px square covers use at most 20 MiB, leaving space in the shared 32 MiB
// cache for backdrops and other views. Use the same bucket for preloads and cards.
internal fun homeArtworkTargetSizePx(requestedPx: Int): Int =
    ArtworkDecodePolicy.target(requestedPx).coerceAtMost(512)

/** Warm the entire bounded Home strip, including cards LazyRow hasn't composed yet.
 * Requests still run off the UI thread and stop when the launcher is backgrounded.
 */
@Composable
internal fun HomeArtworkBuffer(items: List<TileUiModel>, iconLoader: AndroidIconLoader, targetSizePx: Int) {
    val target = homeArtworkTargetSizePx(targetSizePx)
    val order = LocalArtworkLoadOrder.current ?: rememberArtworkLoadOrder(items.take(HOME_CARD_LIMIT).map { it.itemId })
    CompositionLocalProvider(LocalArtworkLoadingAllowed provides true, LocalArtworkLoadOrder provides order) {
        items.take(HOME_CARD_LIMIT).forEach { item -> key(item.itemId) {
            when (val art = item.artwork) {
                is TileArtwork.AndroidIcon -> rememberAndroidIconPainter(iconLoader, art.componentId, targetSizePx = target)
                else -> rememberEnrichedArtwork(item, targetSizePx = target)
            }
        } }
    }
}
