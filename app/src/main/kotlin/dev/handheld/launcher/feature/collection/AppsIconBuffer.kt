package dev.handheld.launcher.feature.collection

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.ui.artwork.ArtworkDecodePolicy
import dev.handheld.launcher.ui.artwork.local.AndroidIconLoader
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.job

// Forty maximum-size icons occupy at most 5.625 MiB of the existing 8 MiB icon cache.
internal fun appsIconBufferWindow(count: Int, firstVisible: Int): IntRange {
    if (count <= 0) return IntRange.EMPTY
    val start = (firstVisible.coerceIn(0, count - 1) / 8 * 8 - 8).coerceIn(0, (count - 40).coerceAtLeast(0))
    return start until minOf(count, start + 40)
}

/** Warm small Apps catalogs completely; larger catalogs keep a bounded window ahead of scrolling. */
@Composable
internal fun AppsIconBuffer(items: List<LibraryItem>, loader: AndroidIconLoader, grid: LazyGridState, targetSizePx: Int) {
    val components = remember(items) { items.map { (it as? LibraryItem.AndroidApp)?.componentId } }
    val target = ArtworkDecodePolicy.target(targetSizePx)
    val foreground = loader.foreground
    LaunchedEffect(loader, components, target, foreground, loader.generation, loader.memoryOwner.generation) {
        if (!foreground) return@LaunchedEffect
        val job = currentCoroutineContext().job
        val unregister = loader.memoryOwner.onBackground { job.cancel() }
        try {
            snapshotFlow { appsIconBufferWindow(components.size, grid.firstVisibleItemIndex) }
                .distinctUntilChanged().collectLatest { window ->
                    for (index in window) {
                        val component = components[index] ?: continue
                        if (loader.cached(component, target) != null) continue
                        loader.load(component, target)
                        // Pace speculative work; visible requests retain their own immediate path.
                        delay(8)
                    }
                }
        } finally { unregister() }
    }
}
