package dev.handheld.launcher.feature.collection

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import dev.handheld.launcher.core.designsystem.controls.SortSelector
import dev.handheld.launcher.core.designsystem.contract.LocalControllerInput
import dev.handheld.launcher.input.RegisterPageNavigation
import dev.handheld.launcher.input.rememberGridNavigation
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.contract.LauncherActionDescriptor
import dev.handheld.launcher.contract.LauncherActionMeaning
import dev.handheld.launcher.contract.SemanticInputAction
import dev.handheld.launcher.core.designsystem.controls.FilterChip
import dev.handheld.launcher.core.designsystem.controls.ControllerGlyph
import dev.handheld.launcher.core.designsystem.controls.LauncherButton
import dev.handheld.launcher.core.designsystem.foundation.LauncherText
import dev.handheld.launcher.core.designsystem.glyphs.LauncherGlyph
import dev.handheld.launcher.core.designsystem.glyphs.LauncherGlyphIcon
import dev.handheld.launcher.core.designsystem.layout.InlineNotice
import dev.handheld.launcher.core.designsystem.layout.PageHeading
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LauncherDestination
import dev.handheld.launcher.platform.system.SupportedSystemAction
import dev.handheld.launcher.ui.artwork.local.AndroidIconLoader
import dev.handheld.launcher.ui.components.LibraryItemCard
import dev.handheld.launcher.ui.components.LibraryItemCardVariant
import dev.handheld.launcher.ui.presentation.toTileUiModel

data class CollectionScreenCallbacks(
    val onSelect: (ItemId) -> Unit,
    val onOpen: (ItemId) -> Unit,
    val onOpenDetails: (ItemId) -> Unit,
    val onFavorite: (ItemId, Boolean) -> Unit,
    val onFilter: (String) -> Unit,
    val onToggleSort: () -> Unit,
    val onRememberAnchor: (ItemId?, Int) -> Unit,
    val onRetry: () -> Unit,
    val onOpenSystemAction: (String) -> Unit,
    val onOpenLibrary: () -> Unit = onRetry,
    val onFocusedAction: OnFocusedAction = {},
    val onOpenFilters: (() -> Unit)? = null,
    val onOpenSort: (() -> Unit)? = null,
    val activityLabels: Map<ItemId, String> = emptyMap(),
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CollectionDestinationScreen(
    title: String,
    state: CollectionUiState,
    modifier: Modifier,
    callbacks: CollectionScreenCallbacks,
    systemActions: List<SupportedSystemAction> = emptyList(),
    iconLoader: AndroidIconLoader? = null,
    restoreFocusRequest: Int = 1,
    allowFocusRequest: Boolean = true,
) = BoxWithConstraints(modifier.fillMaxSize()) {
    val columns = when {
        maxWidth >= 760.dp -> 5
        maxWidth >= 500.dp -> 4
        else -> 3
    }
    val fontScale = LocalDensity.current.fontScale
    CollectionBody(title, state, callbacks, systemActions, iconLoader,
        if (fontScale > 1.2f) (columns - 1).coerceAtLeast(2) else columns,
        restoreFocusRequest, allowFocusRequest)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CollectionBody(
    title: String,
    state: CollectionUiState,
    callbacks: CollectionScreenCallbacks,
    systemActions: List<SupportedSystemAction>,
    iconLoader: AndroidIconLoader?,
    columns: Int,
    restoreFocusRequest: Int,
    allowFocusRequest: Boolean,
) {
    val grid = rememberLazyGridState()
    val laidOutItemCount by remember { derivedStateOf { grid.layoutInfo.totalItemsCount } }
    val inputMode = LocalInputModeManager.current
    val controllerInput = LocalControllerInput.current
    val visibleSystemActions = if (state.filter == "system") systemActions else emptyList()
    // Lazy items can leave and re-enter composition while a restoration coroutine is waiting.
    // Keep the requester for a stable item ID, then only use it after that exact item is placed.
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val renderedItemIds = remember(state.items) { state.items.map { it.id } }
    val itemKeys = remember(renderedItemIds) { renderedItemIds.map { it.value } }
    val currentRenderedItemIds by rememberUpdatedState(renderedItemIds)
    var focusedGridItem by remember { mutableStateOf<ItemId?>(null) }
    var gridHasLaidOutItems by remember { mutableStateOf(false) }
    var handledFocusRequest by remember { mutableStateOf(0) }
    var viewportRestored by remember { mutableStateOf(false) }
    val navigator = rememberGridNavigation(itemKeys, focusedGridItem?.value, columns, grid, requesters,
        enabled = allowFocusRequest && controllerInput, reducedMotion = LauncherTheme.motion.reducedMotion)
    RegisterPageNavigation(navigator::move)
    suspend fun restoreSelection(restoreViewport: Boolean = false): Boolean {
        val anchor = state.firstVisibleItemId?.let { target -> state.items.indexOfFirst { it.id == target } } ?: -1
        if (restoreViewport && anchor >= 0) grid.scrollToItem(anchor, state.firstVisibleOffsetPx)
        val id = state.selectedItemId?.takeIf { selected -> state.items.any { it.id == selected } }
            ?: state.items.firstOrNull()?.id ?: return false
        val selected = state.items.indexOfFirst { it.id == id }
        if (selected >= 0) {
            withFrameNanos { }
            if (grid.layoutInfo.visibleItemsInfo.none { it.index == selected }) grid.scrollToItem(selected)
            withFrameNanos { }
            if (grid.layoutInfo.visibleItemsInfo.none { it.index == selected }) return false
            val requester = requesters[id.value] ?: return false
            inputMode.requestInputMode(InputMode.Keyboard)
            // The route may have changed after the frame. FocusRequester exposes no public
            // attachment state, so a placed-item check is paired with safe lifecycle handling.
            return runCatching { requester.requestFocus() }.isSuccess && focusedGridItem == id
        }
        return false
    }
    LaunchedEffect(restoreFocusRequest, state.loading, renderedItemIds, allowFocusRequest, controllerInput, laidOutItemCount) {
        if (!viewportRestored && !state.loading && state.items.isNotEmpty()) {
            val anchor = renderedItemIds.indexOf(state.firstVisibleItemId)
            if (anchor >= 0) grid.scrollToItem(anchor, state.firstVisibleOffsetPx)
            viewportRestored = true
        }
        if (controllerInput && allowFocusRequest && restoreFocusRequest > 0 && !state.loading && state.items.isNotEmpty() &&
            restoreFocusRequest != handledFocusRequest) {
            if (restoreSelection()) handledFocusRequest = restoreFocusRequest
        }
    }
    LaunchedEffect(state.items) {
        if (controllerInput && allowFocusRequest && focusedGridItem != null && state.items.none { it.id == focusedGridItem }) restoreSelection()
    }
    LaunchedEffect(state.filter, state.sort) {
        if (state.firstVisibleItemId == null) grid.scrollToItem(0)
    }
    LaunchedEffect(laidOutItemCount) {
        if (laidOutItemCount > 0) gridHasLaidOutItems = true
    }
    val rememberAnchor by rememberUpdatedState(callbacks.onRememberAnchor)
    LaunchedEffect(grid, gridHasLaidOutItems) {
        if (gridHasLaidOutItems) snapshotFlow { grid.firstVisibleItemIndex to grid.firstVisibleItemScrollOffset }
            .distinctUntilChanged().collect { (index, offset) -> rememberAnchor(currentRenderedItemIds.getOrNull(index), offset) }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xs)) {
        Row(Modifier.fillMaxWidth().height(54.dp), horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically) {
            PageHeading(title, countLabel(state.items.size, visibleSystemActions.size), Modifier.width(108.dp))
            CollectionFilters(state, callbacks, Modifier.weight(1f))
            val sort = callbacks.onOpenSort ?: callbacks.onToggleSort
            SortSelector(if (state.sort == "recent") "Recent" else "Title", listOf("Recent", "Title"), sort,
                Modifier.height(48.dp),
                onFocusChanged = { focused -> callbacks.focused("Sort", LauncherActionMeaning.CHANGE_FILTER, sort, focused) })
        }
        if (state.inventoryIncomplete) InlineNotice("Catalog may be incomplete.", "Retry", callbacks.onRetry)
        state.error?.let { InlineNotice(it, "Retry", callbacks.onRetry) }
        when {
            state.loading || (state.searching && state.items.isEmpty()) -> LauncherText("Loading games…", color = LauncherTheme.colors.textSecondary)
            state.items.isEmpty() && visibleSystemActions.isEmpty() -> CollectionEmptyState(
                "Nothing here yet", emptyMessage(state.destination),
                if (state.destination == LauncherDestination.FAVORITES) "Open Library" else "Retry catalog",
                if (state.destination == LauncherDestination.FAVORITES) callbacks.onOpenLibrary else callbacks.onRetry,
                callbacks.onFocusedAction,
                requestInitialFocus = allowFocusRequest && controllerInput,
                restoreFocusRequest = restoreFocusRequest,
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(columns), state = grid,
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("collection-grid"),
                contentPadding = PaddingValues(vertical = LauncherTheme.depth.focusLift + LauncherTheme.spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm),
            ) {
                items(state.items, key = { it.id.value }) { item ->
                    val model = item.toTileUiModel(state.overrides[item.id], item.id in state.recentIds)
                    val open = { callbacks.onSelect(item.id); callbacks.onOpen(item.id) }
                    LibraryItemCard(
                        model, LibraryItemCardVariant.Collection, state.selectedItemId == item.id,
                        activationEnabled = model.canOpen,
                        modifier = Modifier.fillMaxWidth()
                            .focusRequester(requesters.getOrPut(item.id.value) { FocusRequester() }),
                        iconLoader = iconLoader, onActivate = open,
                        statusLabel = callbacks.activityLabels[item.id],
                        onFocusChanged = { focused ->
                            if (focused) {
                                focusedGridItem = item.id
                                callbacks.onSelect(item.id)
                                callbacks.onFocusedAction(FocusedControlAction(
                                    LauncherActionDescriptor(SemanticInputAction.CONFIRM, LauncherActionMeaning.ACTIVATE, model.primaryActionLabel, model.canOpen),
                                    if (model.canOpen) open else null, item.id,
                                ))
                            } else if (focusedGridItem == item.id) {
                                // A filtered-out card loses focus as it leaves composition. Keep
                                // its intent until the replacement card is placed; an intentional
                                // move to a control still clears it while the card remains present.
                                if (item.id in currentRenderedItemIds) focusedGridItem = null
                                callbacks.onFocusedAction(null)
                            }
                        },
                    )
                }
                items(visibleSystemActions, key = { "system:${it.key}" }) { action ->
                    LauncherButton(action.title, { callbacks.onOpenSystemAction(action.key) }, Modifier.fillMaxWidth().height(48.dp),
                        onFocusChanged = { focused -> callbacks.focused(action.title, LauncherActionMeaning.ACTIVATE, { callbacks.onOpenSystemAction(action.key) }, focused) })
                }
            }
        }
    }
}

@Composable
private fun CollectionFilters(state: CollectionUiState, callbacks: CollectionScreenCallbacks, modifier: Modifier) {
    val options = remember(state.destination, state.allItems, state.overrides, state.favorites) { collectionFilterOptions(state) }
    if (options.isEmpty()) return
    Row(modifier.height(48.dp), horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically) {
        ControllerGlyph("L2", semanticLabel = "L2: previous filter; hold to accelerate")
        BoxWithConstraints(Modifier.weight(1f).clipToBounds()) {
            val visibleCount = (maxWidth.value / (86f * LocalDensity.current.fontScale))
                .toInt().coerceIn(1, 4).coerceAtMost(options.size)
            val width = maxWidth / visibleCount
            val looping = options.size > visibleCount
            val initial = if (looping) Int.MAX_VALUE / 2 - (Int.MAX_VALUE / 2) % options.size else 0
            val row = rememberLazyListState(initial)
            var centeredIndex by remember(options.map { it.key }) { mutableIntStateOf(initial) }
            LaunchedEffect(state.filter, options.map { it.key }) {
                val selected = options.indexOfFirst { it.key == state.filter }.coerceAtLeast(0)
                if (looping) {
                    val current = centeredIndex % options.size
                    var delta = selected - current
                    if (delta > options.size / 2) delta -= options.size
                    if (delta < -options.size / 2) delta += options.size
                    centeredIndex += delta
                    row.animateScrollToItem(centeredIndex.coerceIn(0, Int.MAX_VALUE - 1))
                } else row.animateScrollToItem(0)
            }
            LazyRow(state = row, modifier = Modifier.fillMaxWidth()) {
                items(if (looping) Int.MAX_VALUE else options.size, key = { it }) { index ->
                    val option = options[index % options.size]
                    FilterChip(option.label, option.key == state.filter, { callbacks.onFilter(option.key) },
                        Modifier.width(width).padding(horizontal = 2.dp),
                        contentDescription = "Filter ${collectionFilterLabel(option.key)}, ${option.count} items",
                        onFocusChanged = { focused -> callbacks.focused("Filter ${collectionFilterLabel(option.key)}", LauncherActionMeaning.CHANGE_FILTER,
                            { callbacks.onFilter(option.key) }, focused) })
                }
            }
        }
        ControllerGlyph("R2", semanticLabel = "R2: next filter; hold to accelerate")
        callbacks.onOpenFilters?.let { open ->
            FilterChip("All filters", false, { open() }, contentDescription = "Show all console filters in a grid",
                onFocusChanged = { focused -> callbacks.focused("All filters", LauncherActionMeaning.CHANGE_FILTER, open, focused) },
                trailingIcon = { LauncherGlyphIcon(LauncherGlyph.Apps, Modifier.size(16.dp), contentDescription = null) })
        }
    }
}

private fun CollectionScreenCallbacks.focused(label: String, meaning: LauncherActionMeaning, action: () -> Unit, focused: Boolean) {
    onFocusedAction(if (focused) FocusedControlAction(LauncherActionDescriptor(SemanticInputAction.CONFIRM, meaning, label), action) else null)
}

private fun countLabel(items: Int, actions: Int) = "${items + actions} ${if (items + actions == 1) "item" else "items"}"
private fun emptyMessage(destination: LauncherDestination) = when (destination) {
    LauncherDestination.FAVORITES -> "Favorite an app or game to keep it here."
    LauncherDestination.APPS -> "No launchable Android apps are currently available."
    else -> "No catalog entries are currently available."
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CollectionEmptyState(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    onFocusedAction: OnFocusedAction = {},
    requestInitialFocus: Boolean = true,
    restoreFocusRequest: Int = 1,
) {
    val requester = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current
    LaunchedEffect(requestInitialFocus, restoreFocusRequest) {
        if (requestInitialFocus && restoreFocusRequest > 0) {
            inputMode.requestInputMode(InputMode.Keyboard)
            withFrameNanos { }
            requester.requestFocus()
        }
    }
    Column(Modifier.fillMaxWidth().padding(LauncherTheme.spacing.xl), verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.md)) {
        LauncherText(title, style = LauncherTheme.typography.pageTitle)
        LauncherText(message, color = LauncherTheme.colors.textSecondary)
        LauncherButton(
            actionLabel,
            onAction,
            Modifier.focusRequester(requester),
            onFocusChanged = { focused ->
                onFocusedAction(
                    if (focused) {
                        FocusedControlAction(
                            LauncherActionDescriptor(
                                SemanticInputAction.CONFIRM,
                                LauncherActionMeaning.ACTIVATE,
                                actionLabel,
                            ),
                            onAction,
                        )
                    } else {
                        null
                    },
                )
            },
        )
    }
}
