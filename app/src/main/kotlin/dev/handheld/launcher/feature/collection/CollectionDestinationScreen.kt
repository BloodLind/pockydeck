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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
        maxWidth >= 760.dp -> 6
        maxWidth >= 500.dp -> 4
        else -> 3
    }
    CollectionBody(title, state, callbacks, systemActions, iconLoader, columns, restoreFocusRequest, allowFocusRequest)
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
    val inputMode = LocalInputModeManager.current
    val visibleSystemActions = if (state.filter == "system") systemActions else emptyList()
    // Lazy items can leave and re-enter composition while a restoration coroutine is waiting.
    // Keep the requester for a stable item ID, then only use it after that exact item is placed.
    val requesters = remember { mutableMapOf<ItemId, FocusRequester>() }
    val renderedItemIds = state.items.map { it.id }
    val currentRenderedItemIds by rememberUpdatedState(renderedItemIds)
    var focusedGridItem by remember { mutableStateOf<ItemId?>(null) }
    var gridHasLaidOutItems by remember { mutableStateOf(false) }
    var handledFocusRequest by remember { mutableStateOf(0) }
    suspend fun restoreSelection(): Boolean {
        val anchor = state.firstVisibleItemId?.let { target -> state.items.indexOfFirst { it.id == target } } ?: -1
        if (anchor >= 0) grid.scrollToItem(anchor, state.firstVisibleOffsetPx)
        val id = state.selectedItemId?.takeIf { selected -> state.items.any { it.id == selected } }
            ?: state.items.firstOrNull()?.id ?: return false
        val selected = state.items.indexOfFirst { it.id == id }
        if (selected >= 0) {
            withFrameNanos { }
            if (grid.layoutInfo.visibleItemsInfo.none { it.index == selected }) grid.scrollToItem(selected)
            withFrameNanos { }
            if (grid.layoutInfo.visibleItemsInfo.none { it.index == selected }) return false
            val requester = requesters[id] ?: return false
            inputMode.requestInputMode(InputMode.Keyboard)
            // The route may have changed after the frame. FocusRequester exposes no public
            // attachment state, so a placed-item check is paired with safe lifecycle handling.
            return runCatching { requester.requestFocus() }.isSuccess
        }
        return false
    }
    LaunchedEffect(restoreFocusRequest, state.loading, renderedItemIds, allowFocusRequest, grid.layoutInfo.totalItemsCount) {
        if (allowFocusRequest && restoreFocusRequest > 0 && !state.loading && state.items.isNotEmpty() &&
            (restoreFocusRequest != handledFocusRequest || focusedGridItem != null)) {
            if (restoreSelection()) handledFocusRequest = restoreFocusRequest
        }
    }
    LaunchedEffect(state.items) {
        if (allowFocusRequest && focusedGridItem != null && state.items.none { it.id == focusedGridItem }) restoreSelection()
    }
    LaunchedEffect(state.filter, state.sort) {
        if (state.firstVisibleItemId == null) grid.scrollToItem(0)
    }
    LaunchedEffect(grid.layoutInfo.totalItemsCount) {
        if (grid.layoutInfo.totalItemsCount > 0) gridHasLaidOutItems = true
    }
    LaunchedEffect(gridHasLaidOutItems, grid.firstVisibleItemIndex, grid.firstVisibleItemScrollOffset, state.items) {
        if (gridHasLaidOutItems && state.items.isNotEmpty()) {
            callbacks.onRememberAnchor(
                state.items.getOrNull(grid.firstVisibleItemIndex)?.id,
                grid.firstVisibleItemScrollOffset,
            )
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xs)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            PageHeading(title, countLabel(state.items.size, visibleSystemActions.size), Modifier.weight(1f))
            LauncherButton(if (state.sort == "recent") "Recent" else "Title", callbacks.onToggleSort,
                Modifier.width(112.dp).height(48.dp),
                onFocusChanged = { focused -> callbacks.focused("Sort", LauncherActionMeaning.CHANGE_FILTER, callbacks.onToggleSort, focused) })
        }
        CollectionFilters(state, callbacks)
        if (state.inventoryIncomplete) InlineNotice("Catalog may be incomplete.", "Retry", callbacks.onRetry)
        state.error?.let { InlineNotice(it, "Retry", callbacks.onRetry) }
        when {
            state.loading -> LauncherText("Loading catalog…", color = LauncherTheme.colors.textSecondary)
            state.items.isEmpty() && visibleSystemActions.isEmpty() -> CollectionEmptyState(
                "Nothing here yet", emptyMessage(state.destination),
                if (state.destination == LauncherDestination.FAVORITES) "Open Library" else "Retry catalog",
                if (state.destination == LauncherDestination.FAVORITES) callbacks.onOpenLibrary else callbacks.onRetry,
                callbacks.onFocusedAction,
                requestInitialFocus = allowFocusRequest,
                restoreFocusRequest = restoreFocusRequest,
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(columns), state = grid, modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = LauncherTheme.depth.focusLift + LauncherTheme.spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm),
            ) {
                items(state.items, key = { it.id.value }) { item ->
                    val model = item.toTileUiModel(state.overrides[item.id], item.id in state.recentIds)
                    val open = { callbacks.onSelect(item.id); callbacks.onOpen(item.id) }
                    LibraryItemCard(
                        model, LibraryItemCardVariant.Collection, state.selectedItemId == item.id,
                        activationEnabled = model.canOpen,
                        modifier = Modifier.fillMaxWidth()
                            .focusRequester(requesters.getOrPut(item.id) { FocusRequester() }),
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
private fun CollectionFilters(state: CollectionUiState, callbacks: CollectionScreenCallbacks) {
    val options = remember(state.destination, state.allItems, state.overrides, state.favorites) { collectionFilterOptions(state) }
    val primary = remember(options, state.filter, callbacks.onOpenFilters != null) {
        if (callbacks.onOpenFilters != null) primaryCollectionFilters(options, state.filter) else options
    }
    fun cycle(delta: Int) {
        if (options.isEmpty()) return
        val index = options.indexOfFirst { it.key == state.filter }.coerceAtLeast(0)
        callbacks.onFilter(options[(index + delta + options.size) % options.size].key)
    }
    Row(Modifier.fillMaxWidth().height(48.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically) {
        FilterDirectionHint("L2", "Previous filter", { cycle(-1) }, callbacks)
        primary.forEach { option ->
            key(option.key) {
                FilterChip(option.label, option.key == state.filter, { callbacks.onFilter(option.key) },
                    contentDescription = "Filter ${collectionFilterLabel(option.key)}, ${option.count} items",
                    onFocusChanged = { focused -> callbacks.focused("Filter ${collectionFilterLabel(option.key)}", LauncherActionMeaning.CHANGE_FILTER,
                        { callbacks.onFilter(option.key) }, focused) })
            }
        }
        if (primary.size < options.size) callbacks.onOpenFilters?.let { open ->
            FilterChip("More", false, { open() }, contentDescription = "More filters",
                onFocusChanged = { focused -> callbacks.focused("More filters", LauncherActionMeaning.CHANGE_FILTER, open, focused) },
                trailingIcon = { LauncherGlyphIcon(LauncherGlyph.ExpandMore,
                    Modifier.size(13.dp * LauncherTheme.referenceScale * LauncherTheme.smallControlScale), contentDescription = null) })
        }
        FilterDirectionHint("R2", "Next filter", { cycle(1) }, callbacks)
    }
}

@Composable
private fun FilterDirectionHint(glyph: String, label: String, onClick: () -> Unit, callbacks: CollectionScreenCallbacks) {
    var focused by remember { mutableStateOf(false) }
    Box(Modifier.size(48.dp).onFocusChanged {
        focused = it.isFocused
        callbacks.focused(label, LauncherActionMeaning.CHANGE_FILTER, onClick, focused)
    }.clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label }
        .then(if (focused) Modifier.border(2.dp, LauncherTheme.colors.focus, RoundedCornerShape(LauncherTheme.shapes.smallControl)) else Modifier),
        contentAlignment = Alignment.Center) {
        ControllerGlyph(glyph, semanticLabel = null)
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
