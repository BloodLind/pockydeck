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
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
    val focusAllowed by rememberUpdatedState(allowFocusRequest && controllerInput)
    val visibleSystemActions = if (state.filter == "system") systemActions else emptyList()
    // Lazy items can leave and re-enter composition while a restoration coroutine is waiting.
    // Keep the requester for a stable item ID, then only use it after that exact item is placed.
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val renderedItemIds = remember(state.items) { state.items.map { it.id } }
    val itemKeys = remember(renderedItemIds) { renderedItemIds.map { it.value } }
    val currentRenderedItemIds by rememberUpdatedState(renderedItemIds)
    val currentCriteria by rememberUpdatedState(state.filter to state.sort)
    var focusedGridItem by remember { mutableStateOf<ItemId?>(null) }
    var gridHasLaidOutItems by remember { mutableStateOf(false) }
    var handledFocusRequest by remember { mutableStateOf<Triple<Int, String, String>?>(null) }
    var viewportRestored by remember { mutableStateOf(false) }
    var filterStripFocused by remember { mutableStateOf(false) }
    val allFilterFocus = remember { FocusRequester() }
    val allFiltersFocus = remember { FocusRequester() }
    val sortFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val navigator = rememberGridNavigation(itemKeys, focusedGridItem?.value, columns, grid, requesters,
        enabled = allowFocusRequest && controllerInput, reducedMotion = LauncherTheme.motion.reducedMotion)
    suspend fun restoreSelection(restoreViewport: Boolean = false): Boolean {
        if (!focusAllowed) return false
        val anchor = state.firstVisibleItemId?.let { target -> state.items.indexOfFirst { it.id == target } } ?: -1
        if (restoreViewport && anchor >= 0) grid.scrollToItem(anchor, state.firstVisibleOffsetPx)
        val id = state.selectedItemId?.takeIf { selected -> state.items.any { it.id == selected } }
            ?: state.items.firstOrNull()?.id ?: return false
        val requestedCriteria = state.filter to state.sort
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
        if (filterStripFocused) {
            // Directional navigation leaves the strip. Only triggers change categories.
            when (direction) {
                FocusDirection.Left, FocusDirection.Up -> allFilterFocus.requestFocus()
                FocusDirection.Right -> (if (callbacks.onOpenFilters != null) allFiltersFocus else sortFocus).requestFocus()
                FocusDirection.Down -> scope.launch { if (!restoreSelection() && focusAllowed) allFilterFocus.requestFocus() }
            }
            true
        } else navigator.move(direction)
    }
    val focusRequest = Triple(restoreFocusRequest, state.filter, state.sort)
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
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val sortControl: @Composable () -> Unit = {
                val sort = callbacks.onOpenSort ?: callbacks.onToggleSort
                SortSelector(if (state.sort == "recent") "Recent" else "Title", listOf("Recent", "Title"), sort,
                    Modifier.height(48.dp).focusRequester(sortFocus),
                    onFocusChanged = { focused -> callbacks.focused("Sort", LauncherActionMeaning.CHANGE_FILTER, sort, focused) })
            }
            if (maxWidth < 560.dp) Column {
                Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) {
                    PageHeading(title, countLabel(state.items.size, visibleSystemActions.size), Modifier.weight(1f))
                    sortControl()
                }
                CollectionFilters(state, callbacks, Modifier.fillMaxWidth(), allFilterFocus, allFiltersFocus,
                    onStripFocused = { filterStripFocused = it })
            } else Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically) {
                PageHeading(title, countLabel(state.items.size, visibleSystemActions.size), Modifier.width(102.dp))
                CollectionFilters(state, callbacks, Modifier.weight(1f), allFilterFocus, allFiltersFocus,
                    onStripFocused = { filterStripFocused = it })
                sortControl()
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
private fun CollectionFilters(
    state: CollectionUiState,
    callbacks: CollectionScreenCallbacks,
    modifier: Modifier,
    allFocus: FocusRequester,
    moreFocus: FocusRequester,
    onStripFocused: (Boolean) -> Unit,
) {
    val options = remember(state.destination, state.allItems, state.overrides, state.favorites) { collectionFilterOptions(state) }
    if (options.isEmpty()) return
    val categories = remember(options) { options.filter { it.key != "all" } }
    Row(modifier.height(48.dp), horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically) {
        FilterChip("All", state.filter == "all", { callbacks.onFilter("all") },
            Modifier.focusRequester(allFocus).testTag("collection-filter-all"),
            contentDescription = "Filter All, ${options.firstOrNull { it.key == "all" }?.count ?: 0} items",
            onFocusChanged = { focused -> callbacks.focused("Filter All", LauncherActionMeaning.CHANGE_FILTER,
                { callbacks.onFilter("all") }, focused) })
        if (categories.isNotEmpty()) {
            Box(Modifier.width(1.dp).height(20.dp).border(1.dp, LauncherTheme.colors.borderEmphasis))
            ControllerGlyph("L2", semanticLabel = "L2: previous filter; hold to accelerate")
            val row = rememberLazyListState()
            val reducedMotion = LauncherTheme.motion.reducedMotion
            LaunchedEffect(state.filter, categories.map { it.key }) {
                val selected = categories.indexOfFirst { it.key == state.filter }
                if (selected >= 0) {
                    val item = row.layoutInfo.visibleItemsInfo.firstOrNull { it.index == selected }
                    if (item == null || item.offset < 0 || item.offset + item.size > row.layoutInfo.viewportEndOffset) {
                        if (reducedMotion) row.scrollToItem(selected) else row.animateScrollToItem(selected)
                    }
                }
            }
            LazyRow(state = row,
                modifier = Modifier.weight(1f, fill = false).clipToBounds().testTag("collection-filter-strip")
                    .onFocusChanged { onStripFocused(it.hasFocus) }.focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                items(categories.size, key = { categories[it].key }) { index ->
                    val option = categories[index]
                    FilterChip(option.label, option.key == state.filter, { callbacks.onFilter(option.key) },
                        Modifier.testTag("collection-filter-${option.key}"),
                        contentDescription = "Filter ${collectionFilterLabel(option.key)}, ${option.count} items",
                        onFocusChanged = { focused -> callbacks.focused("Filter ${collectionFilterLabel(option.key)}", LauncherActionMeaning.CHANGE_FILTER,
                            { callbacks.onFilter(option.key) }, focused) })
                }
            }
            ControllerGlyph("R2", semanticLabel = "R2: next filter; hold to accelerate")
        } else androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        callbacks.onOpenFilters?.let { open ->
            FilterChip("All filters", false, { open() }, Modifier.focusRequester(moreFocus),
                contentDescription = "Show all console filters in a grid",
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
