package dev.handheld.launcher.feature.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.contract.LauncherActionDescriptor
import dev.handheld.launcher.contract.LauncherActionMeaning
import dev.handheld.launcher.contract.SemanticInputAction
import dev.handheld.launcher.core.designsystem.controls.LauncherButton
import dev.handheld.launcher.core.designsystem.controls.SearchField
import dev.handheld.launcher.core.designsystem.foundation.LauncherText
import dev.handheld.launcher.core.designsystem.layout.PageHeading
import dev.handheld.launcher.core.designsystem.controls.FilterChip
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.feature.collection.CollectionScreenCallbacks
import dev.handheld.launcher.feature.collection.CollectionUiState
import dev.handheld.launcher.feature.collection.CollectionEmptyState
import dev.handheld.launcher.feature.collection.FocusedControlAction
import dev.handheld.launcher.platform.system.SupportedSystemAction
import dev.handheld.launcher.ui.artwork.local.AndroidIconLoader
import dev.handheld.launcher.ui.components.LibraryItemCard
import dev.handheld.launcher.ui.components.LibraryItemCardVariant
import dev.handheld.launcher.ui.presentation.toTileUiModel

/** Query state remains in CollectionViewModel, so IME edits do not reset selection or filters. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SearchScreen(
    state: CollectionUiState,
    modifier: Modifier,
    callbacks: CollectionScreenCallbacks,
    onQuery: (String) -> Unit,
    systemActions: List<SupportedSystemAction>,
    iconLoader: AndroidIconLoader? = null,
    queryFocusRequest: Int = 0,
    onSearchFocus: (Boolean) -> Unit = {},
) {
    val list = rememberLazyGridState()
    val queryRequester = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val matchingSystemActions = systemActions.filter { action ->
        val query = state.query.trim()
        query.isEmpty() || "${action.title} ${action.description}".contains(query, ignoreCase = true)
    }.filter { state.filter == "all" || state.filter == "system" }
    val results = remember(state.items, matchingSystemActions) {
        buildList<SearchResult> {
            addAll(state.items.map(SearchResult::Catalog))
            addAll(matchingSystemActions.map(SearchResult::System))
        }
    }
    // Result composition is virtualized. Requesters remain stable by result key and are only
    // invoked after the corresponding lazy item is in the placed viewport.
    val resultRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val renderedResultKeys = results.map(SearchResult::key)
    var focusedResultKey by remember { mutableStateOf<String?>(null) }
    var focusedResultIndex by remember { mutableStateOf(0) }
    var hasEnteredSearch by rememberSaveable { mutableStateOf(false) }
    var handledQueryFocusRequest by rememberSaveable { mutableStateOf(0) }
    var restoreResultsOnEntry by rememberSaveable { mutableStateOf(false) }
    var listHasLaidOutItems by remember { mutableStateOf(false) }
    suspend fun restoreAnchor() {
        val anchorId = state.firstVisibleItemId
        if (anchorId == null) {
            list.scrollToItem(0)
            return
        }
        val anchorIndex = results.indexOfFirst { result ->
            (result as? SearchResult.Catalog)?.item?.id == anchorId
        }
        if (anchorIndex >= 0) list.scrollToItem(anchorIndex, state.firstVisibleOffsetPx)
    }
    suspend fun restoreResultFocus() {
        val selectedIndex = state.selectedItemId?.let { selectedId ->
            results.indexOfFirst { result -> (result as? SearchResult.Catalog)?.item?.id == selectedId }
        } ?: -1
        val anchorIndex = state.firstVisibleItemId?.let { anchorId ->
            results.indexOfFirst { result -> (result as? SearchResult.Catalog)?.item?.id == anchorId }
        } ?: -1
        val targetIndex = selectedIndex.takeIf { it >= 0 } ?: anchorIndex.takeIf { it >= 0 } ?: -1
        if (targetIndex >= 0) {
            list.scrollToItem(targetIndex, if (targetIndex == anchorIndex) state.firstVisibleOffsetPx else 0)
            withFrameNanos { }
            if (list.layoutInfo.visibleItemsInfo.none { it.index == targetIndex }) return
            val requester = resultRequesters[results[targetIndex].key] ?: return
            inputMode.requestInputMode(InputMode.Keyboard)
            runCatching { requester.requestFocus() }
        } else {
            restoreAnchor()
        }
    }
    // The first arrival gives the query controller focus without summoning the IME. A later
    // recreation (for example after Details) restores the saved list target instead.
    LaunchedEffect(Unit) {
        if (hasEnteredSearch) {
            restoreResultsOnEntry = true
        } else {
            hasEnteredSearch = true
            withFrameNanos { }
            runCatching { queryRequester.requestFocus() }
            keyboard?.hide()
        }
    }
    LaunchedEffect(queryFocusRequest) {
        if (queryFocusRequest > handledQueryFocusRequest) {
            handledQueryFocusRequest = queryFocusRequest
            restoreResultsOnEntry = false
            inputMode.requestInputMode(InputMode.Keyboard)
            withFrameNanos { }
            runCatching { queryRequester.requestFocus() }
            keyboard?.show()
        }
    }
    LaunchedEffect(restoreResultsOnEntry, state.loading, renderedResultKeys) {
        if (restoreResultsOnEntry && !state.loading && results.isNotEmpty()) {
            restoreResultsOnEntry = false
            restoreResultFocus()
        }
    }
    LaunchedEffect(state.loading, results) {
        if (!state.loading && results.isNotEmpty()) restoreAnchor()
    }
    LaunchedEffect(list.layoutInfo.totalItemsCount) {
        if (list.layoutInfo.totalItemsCount > 0) listHasLaidOutItems = true
    }
    LaunchedEffect(listHasLaidOutItems, list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset, results) {
        if (listHasLaidOutItems) {
            val first = results.getOrNull(list.firstVisibleItemIndex) as? SearchResult.Catalog
            if (first != null) {
                callbacks.onRememberAnchor(first.item.id, list.firstVisibleItemScrollOffset)
            }
        }
    }
    LaunchedEffect(renderedResultKeys) {
        if (focusedResultKey != null && results.isNotEmpty() && results.none { it.key == focusedResultKey }) {
            focusedResultKey = null
            val replacementIndex = focusedResultIndex.coerceIn(0, results.lastIndex)
            results.getOrNull(replacementIndex)?.let { replacement ->
                list.scrollToItem(replacementIndex)
                withFrameNanos { }
                if (list.layoutInfo.visibleItemsInfo.none { it.index == replacementIndex }) return@let
                val requester = resultRequesters[replacement.key] ?: return@let
                inputMode.requestInputMode(InputMode.Keyboard)
                runCatching { requester.requestFocus() }
            }
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
    val constrained = maxHeight < 240.dp
    val resultColumns = if (!constrained && maxWidth >= 720.dp) 2 else 1
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xs)) {
        if (!constrained) Row(Modifier.fillMaxWidth().height(48.dp)) {
            PageHeading("Search", "${state.items.size + matchingSystemActions.size} results", Modifier.weight(1f))
            LauncherButton(if (state.sort == "recent") "Recent" else "Title", callbacks.onToggleSort,
                Modifier.width(112.dp).height(48.dp),
                onFocusChanged = { focused -> if (focused) callbacks.onFocusedAction(FocusedControlAction(
                    LauncherActionDescriptor(SemanticInputAction.CONFIRM, LauncherActionMeaning.CHANGE_FILTER, "Sort"), callbacks.onToggleSort,
                )) else callbacks.onFocusedAction(null) })
        }
        SearchField(
            query = state.query,
            onQueryChange = onQuery,
            modifier = Modifier.focusRequester(queryRequester).fillMaxWidth(),
            onSearch = { onQuery(state.query) },
            onFocusChanged = { focused ->
                onSearchFocus(focused)
                if (focused) callbacks.onFocusedAction(FocusedControlAction(
                    LauncherActionDescriptor(SemanticInputAction.CONFIRM, LauncherActionMeaning.OPEN_SEARCH, "Search"),
                    null,
                )) else callbacks.onFocusedAction(null)
            },
        )
        if (!constrained) {
        val filters = dev.handheld.launcher.feature.collection.collectionFilterKeys(dev.handheld.launcher.core.domain.model.LauncherDestination.SEARCH)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xs),
        ) {
            filters.forEach { filter ->
                FilterChip(
                    filter.replaceFirstChar { it.uppercase() },
                    selected = filter == state.filter,
                    onSelectedChange = { callbacks.onFilter(filter) },
                    onFocusChanged = { focused -> if (focused) callbacks.onFocusedAction(FocusedControlAction(
                        LauncherActionDescriptor(SemanticInputAction.CONFIRM, LauncherActionMeaning.CHANGE_FILTER, "Filter ${filter.replaceFirstChar { it.uppercase() }}"),
                        { callbacks.onFilter(filter) },
                    )) else callbacks.onFocusedAction(null) },
                )
            }
        }
        }
        if (results.isEmpty()) {
            CollectionEmptyState(
                "No matches",
                "Try a different search or category.",
                "Clear search",
                { onQuery("") },
                callbacks.onFocusedAction,
                requestInitialFocus = false,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(resultColumns),
                state = list,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm),
            ) {
                items(results, key = SearchResult::key) { result ->
                    when (result) {
                        is SearchResult.Catalog -> {
                            val item = result.item
                            val model = item.toTileUiModel(state.overrides[item.id], item.id in state.recentIds)
                            val open = { callbacks.onOpen(item.id) }
                            LibraryItemCard(
                                model, LibraryItemCardVariant.SearchResult, state.selectedItemId == item.id,
                                activationEnabled = model.canOpen,
                                modifier = Modifier.focusRequester(
                                    resultRequesters.getOrPut(result.key) { FocusRequester() },
                                ),
                                iconLoader = iconLoader,
                                onActivate = open,
                                onFocusChanged = { focused -> if (focused) {
                                    focusedResultKey = result.key
                                    focusedResultIndex = results.indexOf(result)
                                    callbacks.onSelect(item.id)
                                    callbacks.onFocusedAction(FocusedControlAction(
                                        LauncherActionDescriptor(SemanticInputAction.CONFIRM, LauncherActionMeaning.ACTIVATE, model.primaryActionLabel, model.canOpen),
                                        if (model.canOpen) open else null, item.id,
                                    ))
                                } else {
                                    if (focusedResultKey == result.key) focusedResultKey = null
                                    callbacks.onFocusedAction(null)
                                } },
                            )
                        }
                        is SearchResult.System -> {
                            val action = result.action
                            Column {
                            LauncherButton(action.title, { callbacks.onOpenSystemAction(action.key) },
                                Modifier.fillMaxWidth().focusRequester(
                                    resultRequesters.getOrPut(result.key) { FocusRequester() },
                                ),
                                onFocusChanged = { focused -> if (focused) {
                                    focusedResultKey = result.key
                                    focusedResultIndex = results.indexOf(result)
                                    callbacks.onFocusedAction(FocusedControlAction(
                                        LauncherActionDescriptor(SemanticInputAction.CONFIRM, LauncherActionMeaning.ACTIVATE, action.title),
                                        { callbacks.onOpenSystemAction(action.key) },
                                    ))
                                } else {
                                    if (focusedResultKey == result.key) focusedResultKey = null
                                    callbacks.onFocusedAction(null)
                                } })
                            LauncherText(action.description, color = LauncherTheme.colors.textSecondary,
                                modifier = Modifier.padding(start = LauncherTheme.spacing.md))
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

private sealed interface SearchResult {
    val key: String
    data class Catalog(val item: dev.handheld.launcher.core.domain.model.LibraryItem) : SearchResult {
        override val key: String = "item:${item.id.value}"
    }
    data class System(val action: SupportedSystemAction) : SearchResult {
        override val key: String = "system:${action.key}"
    }
}
