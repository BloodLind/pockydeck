package dev.handheld.launcher.feature.collection

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.ui.artwork.LocalArtworkLoadOrder
import dev.handheld.launcher.ui.artwork.rememberArtworkLoadOrder

/** Viewport changes invalidate artwork consumers, never the page header or navigation. */
@Composable
internal fun CollectionArtworkOrder(grid: LazyGridState, enabled: Boolean, content: @Composable () -> Unit) {
    val order = if (enabled) {
        val visibleIds by remember(grid) { derivedStateOf {
            val layout = grid.layoutInfo
            layout.visibleItemsInfo.asSequence()
                .filter { it.offset.y + it.size.height > layout.viewportStartOffset && it.offset.y < layout.viewportEndOffset }
                .sortedBy { it.index }
                .mapNotNull { (it.key as? String)?.takeUnless { key -> key.startsWith("system:") }?.let(::ItemId) }
                .toList()
        } }
        rememberArtworkLoadOrder(visibleIds)
    } else null // Apps has a separate native-icon buffer and no cover reveal queue.
    CompositionLocalProvider(LocalArtworkLoadOrder provides order, content = content)
}
