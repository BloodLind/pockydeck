package dev.handheld.launcher.ui.artwork

import androidx.compose.runtime.*
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.ui.artwork.enriched.LocalEnrichedArtworkLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal val LocalArtworkLoadOrder = compositionLocalOf<ArtworkLoadOrder?> { null }

/** Page-local admission order, containing identities only, never images or view references. */
internal class ArtworkLoadOrder(ids: List<ItemId>, private val scope: CoroutineScope) {
    private val ids = ids.distinct()
    private val positions = this.ids.withIndex().associate { it.value to it.index }
    private val completed = mutableSetOf<ItemId>()
    private val settling = mutableMapOf<ItemId, Job>()
    private val deadlines = mutableMapOf<ItemId, Job>()
    private var nextIndex by mutableIntStateOf(0)
    private var closed by mutableStateOf(false)

    fun allowed(id: ItemId): Boolean = positions[id]?.let { it <= nextIndex } ?: true

    /** Advance the worker directly; do not wait for a card to recompose/relaunch it. */
    suspend fun awaitTurn(id: ItemId): Boolean {
        snapshotFlow { closed || allowed(id) }.first { it }
        return !closed
    }

    fun started(id: ItemId) {
        if (closed || id !in positions || id in completed || id in deadlines) return
        // A slow provider must not hold every later cover hostage. Its eventual
        // result can still appear; navigation never waits on this deadline.
        deadlines[id] = scope.launch { delay(250); finish(id) }
    }

    fun complete(id: ItemId, freshImage: Boolean = false) {
        if (closed || id !in positions || id in completed || id in settling) return
        deadlines.remove(id)?.cancel()
        if (!freshImage) finish(id)
        else settling[id] = scope.launch {
            delay(16) // Let this reveal begin before admitting the next image.
            finish(id)
        }
    }

    private fun finish(id: ItemId) {
        if (closed) return
        deadlines.remove(id)?.cancel()
        settling.remove(id)?.cancel()
        completed += id
        while (nextIndex < ids.size && ids[nextIndex] in completed) nextIndex++
    }

    fun close() {
        closed = true
        deadlines.values.forEach(Job::cancel)
        settling.values.forEach(Job::cancel)
        deadlines.clear()
        settling.clear()
    }
}

@Composable
internal fun rememberArtworkLoadOrder(ids: List<ItemId>): ArtworkLoadOrder {
    val scope = rememberCoroutineScope()
    val loader = LocalEnrichedArtworkLoader.current
    // A stopped composition may survive while Android reclaims its image cache.
    // Resume must sequence new decodes again; surviving cache hits still bypass it.
    val generation = loader?.memoryOwner?.generation
    val order = remember(ids, scope, loader, generation) { ArtworkLoadOrder(ids, scope) }
    DisposableEffect(order) { onDispose(order::close) }
    return order
}

@Composable
internal fun artworkTurnAllowed(order: ArtworkLoadOrder?, id: ItemId): Boolean {
    val allowed by remember(order, id) { derivedStateOf { order?.allowed(id) != false } }
    return allowed
}
