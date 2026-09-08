package dev.handheld.launcher.input

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.abs

fun interface PageNavigation { fun move(direction: FocusDirection): Boolean }
val LocalPageNavigation = staticCompositionLocalOf<(PageNavigation?) -> Unit> { {} }

@Composable
fun RegisterPageNavigation(move: (FocusDirection) -> Boolean) {
    val current by rememberUpdatedState(move)
    val publish = LocalPageNavigation.current
    DisposableEffect(publish) {
        val navigation = PageNavigation { current(it) }
        publish(navigation)
        onDispose { publish(null) }
    }
}

/** Horizontal movement follows reading order; vertical movement preserves the column. */
internal fun gridMoveTarget(index: Int, count: Int, columns: Int, direction: FocusDirection): Int? {
    if (index !in 0 until count || columns < 1) return null
    return when (direction) {
        FocusDirection.Right -> (index + 1).coerceAtMost(count - 1)
        FocusDirection.Left -> (index - 1).coerceAtLeast(0)
        FocusDirection.Up -> (index - columns).takeIf { it >= 0 }
        FocusDirection.Down -> if (index / columns < (count - 1) / columns) (index + columns).coerceAtMost(count - 1) else null
        else -> null
    }
}

/** One scrolling worker owns focus. Repeats update its destination, never queue animations. */
@Composable
fun rememberGridNavigation(
    keys: List<String>,
    focusedKey: String?,
    columns: Int,
    grid: LazyGridState,
    requesters: Map<String, FocusRequester>,
    enabled: Boolean,
    reducedMotion: Boolean,
    onMoving: () -> Unit = {},
): PageNavigation {
    val latestKeys by rememberUpdatedState(keys)
    val latestFocused by rememberUpdatedState(focusedKey)
    val latestColumns by rememberUpdatedState(columns)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestMoving by rememberUpdatedState(onMoving)
    val latestReducedMotion by rememberUpdatedState(reducedMotion)
    val pending = remember { arrayOfNulls<String>(1) }
    val moves = remember { Channel<Unit>(Channel.CONFLATED) }
    LaunchedEffect(enabled) {
        if (!enabled) { pending[0] = null; return@LaunchedEffect }
        for (ignored in moves) {
          try {
            var attempts = 0
            while (isActive && latestEnabled && pending[0] != null && attempts++ < 8) {
                val key = pending[0] ?: break
                val target = latestKeys.indexOf(key)
                if (target < 0) { pending[0] = null; break }
                val layout = grid.layoutInfo
                val visible = layout.visibleItemsInfo
                val item = visible.firstOrNull { it.index == target }
                val reference = item ?: visible.firstOrNull()
                if (reference == null) grid.scrollToItem(target)
                else {
                    val rows = visible.groupBy { it.index / latestColumns }.values.map { it.first() }.sortedBy { it.index }
                    val stride = if (rows.size > 1) abs(rows[1].offset.y - rows[0].offset.y)
                        else reference.size.height + layout.mainAxisItemSpacing
                    val top = reference.offset.y + (target / latestColumns - reference.index / latestColumns) * stride
                    val start = layout.viewportStartOffset + layout.beforeContentPadding
                    val end = layout.viewportEndOffset - layout.afterContentPadding
                    val delta = when {
                        top < start -> top - start
                        top + reference.size.height > end -> top + reference.size.height - end
                        else -> 0
                    }
                    if (delta != 0) {
                        if (latestReducedMotion) grid.scroll { scrollBy(delta.toFloat()) }
                        else grid.animateScrollBy(delta.toFloat(), tween(90))
                    }
                }
                withFrameNanos { }
                if (pending[0] != key) { attempts = 0; continue }
                if (grid.layoutInfo.visibleItemsInfo.any { it.index == target } &&
                    requesters[key]?.let { runCatching { it.requestFocus() }.isSuccess } == true) {
                    withFrameNanos { }
                    if (latestFocused != key) continue
                    pending[0] = null
                    break
                }
            }
          } catch (cancelled: CancellationException) {
              // Another scroll mutation may interrupt an animation without disposing the page.
              currentCoroutineContext().ensureActive()
          } finally { pending[0] = null }
        }
    }
    DisposableEffect(Unit) { onDispose { moves.close() } }
    return remember {
        PageNavigation { direction ->
            val current = pending[0] ?: latestFocused
            if (!latestEnabled || current == null) false
            else {
                val index = latestKeys.indexOf(current)
                val target = gridMoveTarget(index, latestKeys.size, latestColumns, direction)
                if (target == null) false else {
                    if (target != index) {
                        latestMoving()
                        pending[0] = latestKeys[target]
                        moves.trySend(Unit)
                    }
                    true
                }
            }
        }
    }
}
