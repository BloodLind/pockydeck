package dev.handheld.launcher.feature.collection

import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import dev.handheld.launcher.core.designsystem.controls.SortSelector
import dev.handheld.launcher.core.designsystem.contract.LocalControllerInput
import dev.handheld.launcher.input.RegisterPageNavigation
import dev.handheld.launcher.input.rememberGridNavigation
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
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
import dev.handheld.launcher.core.designsystem.controls.filterChipGeometry
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
    isList: Boolean = false,
    onLayoutChange: (Boolean) -> Unit = {},
) = BoxWithConstraints(modifier.fillMaxSize()) {
    val columns = when {
        maxWidth >= 760.dp -> 5
        maxWidth >= 500.dp -> 4
        else -> 3
    }
    val fontScale = LocalDensity.current.fontScale
    CollectionBody(title, state, callbacks, systemActions, iconLoader,
        if (isList) 1 else if (fontScale > 1.2f) (columns - 1).coerceAtLeast(2) else columns,
        restoreFocusRequest, allowFocusRequest, isList, onLayoutChange)
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
    isList: Boolean,
    onLayoutChange: (Boolean) -> Unit,
) {
    val grid = rememberLazyGridState()
    val laidOutItemCount by remember { derivedStateOf { grid.layoutInfo.totalItemsCount } }
    val inputMode = LocalInputModeManager.current
    val controllerInput = LocalControllerInput.current
    val focusAllowed by rememberUpdatedState(allowFocusRequest && controllerInput)
    val visibleSystemActions = if (state.filter == "system") systemActions else emptyList()
    // Lazy items can leave and re-enter composition while a restoration coroutine is waiting.
    // Keep the requester for a stable item ID, then only use it after that exact item is placed.
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val renderedItemIds = remember(state.items) { state.items.map { it.id } }
    val itemKeys = remember(renderedItemIds) { renderedItemIds.map { it.value } }
    val currentRenderedItemIds by rememberUpdatedState(renderedItemIds)
    val currentCriteria by rememberUpdatedState(Triple(state.filter, state.sort, isList))
    var focusedGridItem by remember { mutableStateOf<ItemId?>(null) }
    var gridHasLaidOutItems by remember { mutableStateOf(false) }
    var handledFocusRequest by remember { mutableStateOf<Pair<Triple<Int, String, String>, Boolean>?>(null) }
    var viewportRestored by remember { mutableStateOf(false) }
    var filterStripFocused by remember { mutableStateOf(false) }
    var focusedHeaderKey by remember { mutableStateOf<String?>(null) }
    val allFilterFocus = remember { FocusRequester() }
    val allFiltersFocus = remember { FocusRequester() }
    val sortFocus = remember { FocusRequester() }
    val layoutFocus = remember { FocusRequester() }
    val filterRow = rememberLazyListState()
    val categoryRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val scope = rememberCoroutineScope()
    val navigator = rememberGridNavigation(itemKeys, focusedGridItem?.value, columns, grid, requesters,
        enabled = allowFocusRequest && controllerInput && !state.searching, reducedMotion = LauncherTheme.motion.reducedMotion)
    suspend fun restoreSelection(restoreViewport: Boolean = false): Boolean {
        if (!focusAllowed) return false
        val anchor = state.firstVisibleItemId?.let { target -> state.items.indexOfFirst { it.id == target } } ?: -1
        if (restoreViewport && anchor >= 0) grid.scrollToItem(anchor, state.firstVisibleOffsetPx)
        val id = state.selectedItemId?.takeIf { selected -> state.items.any { it.id == selected } }
            ?: state.items.firstOrNull()?.id ?: return false
        val requestedCriteria = Triple(state.filter, state.sort, isList)
        repeat(4) {
            withFrameNanos { }
            if (!focusAllowed || currentCriteria != requestedCriteria) return false
            val selected = currentRenderedItemIds.indexOf(id)
            if (selected < 0) return false
            if (grid.layoutInfo.visibleItemsInfo.none { it.key == id.value }) grid.scrollToItem(selected)
            withFrameNanos { }
            if (!focusAllowed || currentCriteria != requestedCriteria) return false
            if (grid.layoutInfo.visibleItemsInfo.none { it.key == id.value }) return@repeat
            val requester = requesters[id.value] ?: return@repeat
            inputMode.requestInputMode(InputMode.Keyboard)
            // Placement and focus callbacks may land on adjacent frames after a filter.
            // Retry only the same stable key, and stop if a modal/touch/criteria change wins.
            if (runCatching { requester.requestFocus() }.isSuccess) {
                withFrameNanos { }
                if (focusedGridItem == id) return true
            }
        }
        return false
    }
    RegisterPageNavigation { direction ->
        if (filterStripFocused || focusedHeaderKey != null) {
            // Only fully visible categories belong to the D-pad path. Moving through the
            // strip must never bring a clipped/off-screen chip into view implicitly.
            val layout = filterRow.layoutInfo
            val visible = layout.visibleItemsInfo.filter {
                it.offset >= layout.viewportStartOffset && it.offset + it.size <= layout.viewportEndOffset
            }.map { it.key as String }
            val path = buildList {
                add("all"); addAll(visible)
                if (callbacks.onOpenFilters != null) add("header-more")
                add("header-layout")
                add("header-sort")
            }
            fun requestHeader(key: String) {
                when (key) {
                    "all" -> allFilterFocus
                    "header-more" -> allFiltersFocus
                    "header-sort" -> sortFocus
                    "header-layout" -> layoutFocus
                    else -> categoryRequesters[key]
                }?.requestFocus()
            }
            when (direction) {
                FocusDirection.Left, FocusDirection.Right -> {
                    val index = path.indexOf(focusedHeaderKey)
                    val next = if (index < 0) {
                        if (direction == FocusDirection.Left) "all" else visible.firstOrNull() ?: "header-sort"
                    } else path.getOrNull(index + if (direction == FocusDirection.Right) 1 else -1)
                    if (next == null) false else { requestHeader(next); true }
                }
                FocusDirection.Down -> {
                    scope.launch { if (!restoreSelection() && focusAllowed) allFilterFocus.requestFocus() }
                    true
                }
                else -> false
            }
        } else if (direction == FocusDirection.Up && !state.searching &&
            itemKeys.indexOf(focusedGridItem?.value) in 0 until columns) {
            val layout = filterRow.layoutInfo
            val activeVisible = layout.visibleItemsInfo.any {
                it.key == state.filter && it.offset >= layout.viewportStartOffset &&
                    it.offset + it.size <= layout.viewportEndOffset
            }
            // Entering from the first grid row also skips clipped chips; Android's
            // geometric default can otherwise reveal an off-screen category on focus.
            ((if (activeVisible) categoryRequesters[state.filter] else null) ?: allFilterFocus).requestFocus()
            true
        } else navigator.move(direction)
    }
    val focusRequest = Triple(restoreFocusRequest, state.filter, state.sort) to isList
    val restoringCardFocus = controllerInput && allowFocusRequest && restoreFocusRequest > 0 &&
        focusRequest != handledFocusRequest && (state.items.isNotEmpty() || state.searching)
    LaunchedEffect(focusRequest, state.loading, state.searching, renderedItemIds, allowFocusRequest, controllerInput, laidOutItemCount) {
        if (!viewportRestored && !state.loading && state.items.isNotEmpty()) {
            val anchor = renderedItemIds.indexOf(state.firstVisibleItemId)
            if (anchor >= 0) grid.scrollToItem(anchor, state.firstVisibleOffsetPx)
            viewportRestored = true
        }
        if (controllerInput && allowFocusRequest && restoreFocusRequest > 0 && !state.loading && !state.searching && state.items.isNotEmpty() &&
            focusRequest != handledFocusRequest) {
            if (restoreSelection()) handledFocusRequest = focusRequest
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
        // Keep header native targets available, but do not paint Android's temporary
        // fallback focus while a changed filter is restoring the resulting card.
        CompositionLocalProvider(LocalControllerInput provides (controllerInput && !restoringCardFocus)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val measureText = rememberTextWidth()
            val controls = collectionControlWidths(state, callbacks, measureText)
            val titleWidth = measureText(title, LauncherTheme.typography.pageTitle)
            val sortWidth = (measureText(if (state.sort == "recent") "Recent" else "Title", LauncherTheme.typography.controlLabel) +
                32.dp * LauncherTheme.referenceScale * LauncherTheme.smallControlScale +
                (LauncherTheme.spacing.sm + LauncherTheme.spacing.xxs) * 2f).coerceAtLeast(48.dp)
            val compactHeader = maxWidth < titleWidth + controls.minimumWidth + sortWidth + 48.dp + LauncherTheme.spacing.xs * 3f
            val headerCallbacks = callbacks.copy(onFocusedAction = {
                if (!restoringCardFocus) callbacks.onFocusedAction(it)
            })
            val headerFocus: (String, Boolean) -> Unit = { key, focused ->
                if (focused) focusedHeaderKey = key else if (focusedHeaderKey == key) focusedHeaderKey = null
            }
            val sortControl: @Composable () -> Unit = {
                val sort = callbacks.onOpenSort ?: callbacks.onToggleSort
                SortSelector(if (state.sort == "recent") "Recent" else "Title", listOf("Recent", "Title"), sort,
                    Modifier.heightIn(min = 48.dp).focusRequester(sortFocus).testTag("collection-sort"),
                    onFocusChanged = { focused ->
                        headerFocus("header-sort", focused)
                        headerCallbacks.focused("Sort", LauncherActionMeaning.CHANGE_FILTER, sort, focused)
                    })
            }
            Column {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("collection-header"),
                    horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically) {
                    LauncherText(title, style = LauncherTheme.typography.pageTitle,
                        modifier = (if (compactHeader) Modifier.weight(1f) else Modifier).testTag("collection-title"),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!compactHeader) CollectionFilters(state, headerCallbacks, Modifier.weight(1f), allFilterFocus, allFiltersFocus,
                        filterRow, categoryRequesters, headerFocus, onStripFocused = { filterStripFocused = it }, controls = controls)
                    val switchLayout = { onLayoutChange(!isList) }
                    val layoutLabel = if (isList) "Switch to grid view" else "Switch to list view"
                    FilterChip("", isList, { switchLayout() }, Modifier.focusRequester(layoutFocus).testTag("collection-layout"),
                        contentDescription = layoutLabel,
                        onFocusChanged = { focused ->
                            headerFocus("header-layout", focused)
                            headerCallbacks.focused(layoutLabel, LauncherActionMeaning.CHANGE_FILTER, switchLayout, focused)
                        },
                        trailingIcon = {
                            LauncherGlyphIcon(if (isList) LauncherGlyph.Sort else LauncherGlyph.Library,
                                Modifier.size(16.dp * LauncherTheme.referenceScale * LauncherTheme.smallControlScale), contentDescription = null)
                        })
                    sortControl()
                }
                LauncherText(countLabel(state.items.size, visibleSystemActions.size),
                    style = LauncherTheme.typography.tileSubtitle, color = LauncherTheme.colors.textSecondary)
                if (compactHeader) CollectionFilters(state, headerCallbacks, Modifier.fillMaxWidth(), allFilterFocus, allFiltersFocus,
                    filterRow, categoryRequesters, headerFocus, onStripFocused = { filterStripFocused = it }, controls = controls)
            }
        }
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
            else -> BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val availableCardHeight = (maxHeight - (LauncherTheme.depth.focusLift + LauncherTheme.spacing.xs) * 2f).coerceAtLeast(0.dp)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns), state = grid,
                modifier = Modifier.fillMaxSize().testTag("collection-grid"),
                contentPadding = PaddingValues(vertical = LauncherTheme.depth.focusLift + LauncherTheme.spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xs),
            ) {
                items(state.items, key = { it.id.value }) { item ->
                    val model = item.toTileUiModel(state.overrides[item.id], item.id in state.recentIds)
                    val open = { callbacks.onSelect(item.id); callbacks.onOpen(item.id) }
                    LibraryItemCard(
                        model, if (isList) LibraryItemCardVariant.SearchResult else LibraryItemCardVariant.Collection, state.selectedItemId == item.id,
                        // Keep the native target attached during filtering. The ViewModel's
                        // synchronous launch guard rejects activation until criteria settle.
                        activationEnabled = model.canOpen,
                        maxCollectionCardHeight = availableCardHeight,
                        modifier = Modifier.fillMaxWidth()
                            .focusRequester(requesters.getOrPut(item.id.value) { FocusRequester() }),
                        iconLoader = iconLoader, onActivate = open,
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
}

@Composable
private fun CollectionFilters(
    state: CollectionUiState,
    callbacks: CollectionScreenCallbacks,
    modifier: Modifier,
    allFocus: FocusRequester,
    moreFocus: FocusRequester,
    row: LazyListState,
    categoryRequesters: MutableMap<String, FocusRequester>,
    onHeaderFocus: (String, Boolean) -> Unit,
    onStripFocused: (Boolean) -> Unit,
    controls: CollectionControlWidths,
) {
    val options = remember(state.destination, state.allItems, state.overrides, state.favorites) { collectionFilterOptions(state) }
    if (options.isEmpty()) return
    val categories = remember(options) { options.filter { it.key != "all" } }
    Row(modifier.heightIn(min = 48.dp).testTag("collection-filters"), horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically) {
        FilterChip("All", state.filter == "all", { callbacks.onFilter("all") },
            Modifier.focusRequester(allFocus).testTag("collection-filter-all"),
            contentDescription = "Filter All, ${options.firstOrNull { it.key == "all" }?.count ?: 0} items",
            onFocusChanged = { focused ->
                onHeaderFocus("all", focused)
                callbacks.focused("Filter All", LauncherActionMeaning.CHANGE_FILTER, { callbacks.onFilter("all") }, focused)
            })
        if (categories.isNotEmpty()) {
            Box(Modifier.width(1.dp).height(20.dp).border(1.dp, LauncherTheme.colors.borderEmphasis))
            ControllerGlyph("L2", semanticLabel = "L2: previous filter; hold to accelerate")
            BoxWithConstraints(Modifier.weight(1f, fill = false)) {
            val density = LocalDensity.current
            val widthsPx = with(density) { controls.categoryWidths.map { it.roundToPx() } }
            val availablePx = with(density) { maxWidth.roundToPx() }
            val gapPx = with(density) { 2.dp.roundToPx() }
            var windowStart by remember { mutableIntStateOf(0) }
            val currentFilter by rememberUpdatedState(state.filter)
            val stripWidth = with(density) { collectionStripWidth(availablePx, widthsPx, windowStart, gapPx).toDp() }
            // One owner settles the viewport after a selection or touch scroll. Size
            // the actual visible window, not the prefix of the complete category list.
            LaunchedEffect(row, categories.map { it.key }, widthsPx, availablePx, gapPx) {
                var handledFilter: String? = null
                snapshotFlow { Triple(currentFilter, row.isScrollInProgress,
                    row.firstVisibleItemIndex to row.firstVisibleItemScrollOffset) }
                    .distinctUntilChanged().collect { (filter, scrolling, position) ->
                        val restoreSelection = handledFilter == null
                        val selectionChanged = handledFilter != filter
                        if (!selectionChanged && scrolling) return@collect
                        handledFilter = filter
                        val selected = categories.indexOfFirst { it.key == filter }
                        val layout = row.layoutInfo
                        val selectedVisible = layout.visibleItemsInfo.any {
                            it.index == selected && it.offset >= layout.viewportStartOffset &&
                                it.offset + it.size <= layout.viewportEndOffset
                        }
                        val first = position.first.coerceIn(widthsPx.indices)
                        val nearest = (first + if (position.second > widthsPx[first] / 2) 1 else 0)
                            .coerceAtMost(widthsPx.lastIndex)
                        val target = if (selectionChanged && selected >= 0 && (restoreSelection || !selectedVisible)) selected else nearest
                        if (windowStart == target && position == (target to 0) && !scrolling) return@collect
                        windowStart = target
                        // Let the new width reach measurement before a near-end jump;
                        // otherwise LazyRow clamps it against the previous wider window.
                        repeat(2) { withFrameNanos { } }
                        if (currentFilter != filter || (!selectionChanged && row.isScrollInProgress)) return@collect
                        row.scrollToItem(target)
                    }
            }
            LazyRow(state = row,
                modifier = Modifier.width(stripWidth).clipToBounds().testTag("collection-filter-strip")
                    .onFocusChanged { onStripFocused(it.hasFocus) }.focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                items(categories.size, key = { categories[it].key }) { index ->
                    val option = categories[index]
                    FilterChip(option.label, option.key == state.filter, { callbacks.onFilter(option.key) },
                        Modifier.width(with(density) { widthsPx[index].toDp() }).testTag("collection-filter-${option.key}")
                            .focusRequester(categoryRequesters.getOrPut(option.key) { FocusRequester() }),
                        contentDescription = "Filter ${collectionFilterLabel(option.key)}, ${option.count} items",
                        onFocusChanged = { focused ->
                            onHeaderFocus(option.key, focused)
                            callbacks.focused("Filter ${collectionFilterLabel(option.key)}", LauncherActionMeaning.CHANGE_FILTER,
                                { callbacks.onFilter(option.key) }, focused)
                        })
                }
            }
            }
            ControllerGlyph("R2", semanticLabel = "R2: next filter; hold to accelerate")
        } else androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        callbacks.onOpenFilters?.let { open ->
            FilterChip("All filters", false, { open() }, Modifier.focusRequester(moreFocus).testTag("collection-all-filters"),
                contentDescription = "Show all console filters in a grid",
                onFocusChanged = { focused ->
                    onHeaderFocus("header-more", focused)
                    callbacks.focused("All filters", LauncherActionMeaning.CHANGE_FILTER, open, focused)
                },
                trailingIcon = { LauncherGlyphIcon(LauncherGlyph.Apps, Modifier.size(16.dp), contentDescription = null) })
        }
    }
}

private data class CollectionControlWidths(val categoryWidths: List<Dp>, val minimumWidth: Dp)

@Composable
private fun rememberTextWidth(): (String, TextStyle) -> Dp {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(textMeasurer, density) {
        { text, style -> with(density) { textMeasurer.measure(text, style, maxLines = 1).size.width.toDp() } }
    }
}

@Composable
private fun collectionControlWidths(
    state: CollectionUiState,
    callbacks: CollectionScreenCallbacks,
    textWidth: (String, TextStyle) -> Dp,
): CollectionControlWidths {
    val spacing = LauncherTheme.spacing
    val style = LauncherTheme.typography.controlLabel
    val geometry = filterChipGeometry()
    fun chipWidth(label: String, trailingWidth: Dp = 0.dp) =
        geometry.widthFor(textWidth(label, style), trailingWidth)
    val categories = collectionFilterOptions(state).filter { it.key != "all" }
    val categoryWidths = categories.map { chipWidth(it.label) }
    val moreWidth = if (callbacks.onOpenFilters != null) chipWidth("All filters", 16.dp) else 0.dp
    val minimumWidth = if (categories.isEmpty()) chipWidth("All") + moreWidth + spacing.xs
        else chipWidth("All") + 1.dp +
            textWidth("L2", style) + textWidth("R2", style) + spacing.xs * 4f +
            moreWidth + (categoryWidths.maxOrNull() ?: 48.dp) +
            spacing.xs * if (callbacks.onOpenFilters != null) 5f else 4f
    return CollectionControlWidths(categoryWidths, minimumWidth)
}

private fun collectionStripWidth(available: Int, widths: List<Int>, firstIndex: Int, gap: Int): Int {
    if (widths.isEmpty()) return 0
    var used = 0
    val window = widths.drop(firstIndex.coerceIn(widths.indices))
    val fitted = window.takeWhile { width ->
        val next = used + (if (used > 0) gap else 0) + width
        (next <= available).also { if (it) used = next }
    }
    val keep = if (fitted.size > 1) fitted.dropLast(1) else fitted
    // Sum the same rounded widths and gap used by LazyRow. A widest-category floor
    // or per-item rounding mismatch would expose part of the following cell.
    return (if (keep.isEmpty()) window.first() else keep.sum() + gap * (keep.size - 1))
        .coerceIn(0, available.coerceAtLeast(0))
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
