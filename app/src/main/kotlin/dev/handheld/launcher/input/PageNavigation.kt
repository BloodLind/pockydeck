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

/** A held repeat must not wait behind a longer decorative scroll animation. */
internal fun animateGridMove(previousMoveNanos: Long?, moveNanos: Long): Boolean =
    previousMoveNanos == null || moveNanos - previousMoveNanos >= 150_000_000L

/** Prefer the newest placed destination, but keep focus progressing during a sustained hold. */
internal fun gridFocusTarget(
    requestedKey: String?,
    scrolledKey: String,
    placedKeys: Set<String>,
    fullyVisibleKeys: Set<String>,
    directionUnchanged: Boolean,
): String? = requestedKey?.takeIf { it in fullyVisibleKeys }
    ?: scrolledKey.takeIf { directionUnchanged && it in placedKeys }

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
    val pendingDirection = remember { arrayOfNulls<FocusDirection>(1) }
    val previousMoveNanos = remember { arrayOfNulls<Long>(1) }
    val animateMove = remember { booleanArrayOf(true) }
    val moves = remember { Channel<Unit>(Channel.CONFLATED) }
    LaunchedEffect(enabled) {
        if (!enabled) { pending[0] = null; previousMoveNanos[0] = null; return@LaunchedEffect }
        for (ignored in moves) {
          try {
            var attempts = 0
            while (isActive && latestEnabled && pending[0] != null && attempts++ < 8) {
                val key = pending[0] ?: break
                val direction = pendingDirection[0]
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
                        if (latestReducedMotion || !animateMove[0]) grid.scroll { scrollBy(delta.toFloat()) }
                        else grid.animateScrollBy(delta.toFloat(), tween(90))
                    }
                }
                withFrameNanos { }
                val placedLayout = grid.layoutInfo
                val contentStart = placedLayout.viewportStartOffset + placedLayout.beforeContentPadding
                val contentEnd = placedLayout.viewportEndOffset - placedLayout.afterContentPadding
                val focusKey = gridFocusTarget(
                    pending[0], key,
                    placedLayout.visibleItemsInfo.mapNotNullTo(mutableSetOf()) { it.key as? String },
                    placedLayout.visibleItemsInfo.filter {
                        it.offset.y >= contentStart && it.offset.y + it.size.height <= contentEnd
                    }.mapNotNullTo(mutableSetOf()) { it.key as? String },
                    directionUnchanged = pendingDirection[0] == direction,
                )
                if (focusKey != null &&
                    requesters[focusKey]?.let { runCatching { it.requestFocus() }.isSuccess } == true) {
                    withFrameNanos { }
                    if (latestFocused == focusKey && pending[0] == focusKey) {
                        pending[0] = null
                        break
                    }
                }
                if (pending[0] != key) attempts = 0
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
                // An edge reached by the pending cursor is not yet the focused edge.
                // Let the worker finish it before native focus may leave for header/dock.
                if (target == null) pending[0] != null else {
                    if (target != index) {
                        latestMoving()
                        val now = System.nanoTime()
                        animateMove[0] = animateGridMove(previousMoveNanos[0], now)
                        previousMoveNanos[0] = now
                        pending[0] = latestKeys[target]
                        pendingDirection[0] = direction
                        moves.trySend(Unit)
                    }
                    true
                }
            }
        }
    }
}
