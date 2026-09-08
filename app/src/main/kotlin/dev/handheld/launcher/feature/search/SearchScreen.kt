package dev.handheld.launcher.feature.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.contract.LauncherActionDescriptor
import dev.handheld.launcher.contract.LauncherActionMeaning
import dev.handheld.launcher.contract.SemanticInputAction
import dev.handheld.launcher.core.designsystem.controls.LauncherButton
import dev.handheld.launcher.core.designsystem.controls.SearchField
import dev.handheld.launcher.core.designsystem.cards.SearchResultCard
import dev.handheld.launcher.core.designsystem.glyphs.LauncherGlyph
import dev.handheld.launcher.core.designsystem.glyphs.LauncherGlyphIcon
import dev.handheld.launcher.core.designsystem.foundation.LauncherText
import dev.handheld.launcher.core.designsystem.layout.PageHeading
import dev.handheld.launcher.core.designsystem.controls.FilterChip
import dev.handheld.launcher.core.designsystem.controls.SortSelector
import dev.handheld.launcher.core.designsystem.controls.PlatformBadge
import dev.handheld.launcher.core.designsystem.contract.LocalControllerInput
import dev.handheld.launcher.input.RegisterPageNavigation
import dev.handheld.launcher.input.rememberGridNavigation
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.feature.collection.CollectionScreenCallbacks
import dev.handheld.launcher.feature.collection.CollectionUiState
import dev.handheld.launcher.feature.collection.FocusedControlAction
import dev.handheld.launcher.platform.system.SupportedSystemAction
import dev.handheld.launcher.ui.artwork.local.AndroidIconLoader
import dev.handheld.launcher.ui.components.LibraryItemCard
import dev.handheld.launcher.ui.components.LibraryItemCardVariant
import dev.handheld.launcher.ui.presentation.toTileUiModel
import kotlinx.coroutines.launch

class SearchEditorActions(val apply: () -> Unit, val cancel: () -> Unit)

object SearchScreenTags {
    const val Results = "search-results"
    const val Edit = "search-edit"
}

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
    restoreFocusRequest: Int = 1,
    allowFocusRequest: Boolean = true,
    onEditorActionsChanged: (SearchEditorActions?) -> Unit = {},
) {
    val list = rememberLazyGridState()
    val laidOutItemCount by remember { derivedStateOf { list.layoutInfo.totalItemsCount } }
    val queryRequester = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val controllerInput = LocalControllerInput.current
    val currentControllerInput by rememberUpdatedState(controllerInput)
    var editQuery by rememberSaveable { mutableStateOf(state.query) }
    val query = editQuery.trim()
    val matchingSystemActions = systemActions.filter { action ->
        query.isNotEmpty() && "${action.title} ${action.description}".contains(query, ignoreCase = true)
    }.filter { state.filter == "all" || state.filter == "system" }
    val results = remember(query, state.items, matchingSystemActions) {
        buildList<SearchResult> {
            if (query.isNotEmpty()) {
                if (state.query == editQuery) addAll(state.items.map(SearchResult::Catalog))
                addAll(matchingSystemActions.map(SearchResult::System))
            }
        }
    }
    // Result composition is virtualized. Requesters remain stable by result key and are only
    // invoked after the corresponding lazy item is in the placed viewport.
    val resultRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val renderedResultKeys = remember(results) { results.map(SearchResult::key) }
    var focusedResultKey by remember { mutableStateOf<String?>(null) }
    var focusedResultIndex by remember { mutableStateOf(0) }
    var selectedResultKey by rememberSaveable { mutableStateOf<String?>(null) }
    var handledPageActivation by remember { mutableStateOf(0) }
    var handledQueryFocusRequest by rememberSaveable { mutableStateOf(0) }
    var queryHasFocus by remember { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.query) { if (!editing) editQuery = state.query }
    var editEntryQuery by rememberSaveable { mutableStateOf(state.query) }
    var headerCollapsed by rememberSaveable { mutableStateOf(false) }
    var pendingResultsFocusQuery by remember { mutableStateOf<String?>(null) }
    var focusAfterScroll by remember { mutableStateOf(false) }
    var automaticScrollCount by remember { mutableIntStateOf(0) }
    val compactEditRequester = remember { FocusRequester() }
    var listHasLaidOutItems by remember { mutableStateOf(false) }

    fun finishEditing(cancel: Boolean) {
        if (!editing) return
        editing = false
        val nextQuery = if (cancel) editEntryQuery else editQuery
        editQuery = nextQuery
        pendingResultsFocusQuery = nextQuery
        headerCollapsed = true
        keyboard?.hide()
        focusManager.clearFocus(force = true)
        onQuery(nextQuery)
    }
    fun beginEditing() {
        headerCollapsed = false
        scope.launch {
            inputMode.requestInputMode(InputMode.Keyboard)
            withFrameNanos { }
            runCatching { queryRequester.requestFocus() }
            keyboard?.show()
        }
    }
    val currentApply = rememberUpdatedState { finishEditing(cancel = false) }
    val currentCancel = rememberUpdatedState { finishEditing(cancel = true) }
    val editorActions = remember { SearchEditorActions({ currentApply.value() }, { currentCancel.value() }) }
    // Observe focus/session state during composition. Reads made only inside SideEffect do
    // not invalidate this scope, leaving the Activity's controller routing stale on focus.
    val activeEditorActions = editorActions.takeIf { editing && allowFocusRequest }
    SideEffect { onEditorActionsChanged(activeEditorActions) }
    suspend fun restoreAnchor() {
        automaticScrollCount++
        try {
        val anchorId = state.firstVisibleItemId
        if (anchorId == null) {
            list.scrollToItem(0)
            return
        }
        val anchorIndex = results.indexOfFirst { result ->
            (result as? SearchResult.Catalog)?.item?.id == anchorId
        }
        if (anchorIndex >= 0) list.scrollToItem(anchorIndex, state.firstVisibleOffsetPx)
        } finally { automaticScrollCount-- }
    }
    suspend fun restoreResultFocus(): Boolean {
        automaticScrollCount++
        try {
        val selectedIndex = state.selectedItemId?.let { selectedId ->
            results.indexOfFirst { result -> (result as? SearchResult.Catalog)?.item?.id == selectedId }
        } ?: -1
        val anchorIndex = state.firstVisibleItemId?.let { anchorId ->
            results.indexOfFirst { result -> (result as? SearchResult.Catalog)?.item?.id == anchorId }
        } ?: -1
        val rememberedIndex = results.indexOfFirst { it.key == selectedResultKey }
        val targetIndex = rememberedIndex.takeIf { it >= 0 } ?: selectedIndex.takeIf { it >= 0 }
            ?: anchorIndex.takeIf { it >= 0 } ?: results.indices.firstOrNull() ?: return false
        if (targetIndex >= 0) {
            if (list.layoutInfo.visibleItemsInfo.none { it.index == targetIndex })
                list.scrollToItem(targetIndex, if (targetIndex == anchorIndex) state.firstVisibleOffsetPx else 0)
            withFrameNanos { }
            if (list.layoutInfo.visibleItemsInfo.none { it.index == targetIndex }) return false
            val requester = resultRequesters[results[targetIndex].key] ?: return false
            inputMode.requestInputMode(InputMode.Keyboard)
            return runCatching { requester.requestFocus() }.isSuccess && focusedResultKey == results[targetIndex].key
        }
        return false
        } finally { automaticScrollCount-- }
    }
    // A page activation is consumed exactly once. Later query/catalog updates cannot steal
    // the editor's focus; the Search shortcut is a separate explicit request to type.
    LaunchedEffect(restoreFocusRequest, queryFocusRequest, state.loading, renderedResultKeys, allowFocusRequest, controllerInput, laidOutItemCount) {
        if (!allowFocusRequest) return@LaunchedEffect
        if (queryFocusRequest > handledQueryFocusRequest) {
            headerCollapsed = false
            inputMode.requestInputMode(InputMode.Keyboard)
            withFrameNanos { }
            if (runCatching { queryRequester.requestFocus() }.isSuccess && queryHasFocus) {
                handledQueryFocusRequest = queryFocusRequest
                handledPageActivation = restoreFocusRequest
                keyboard?.show()
            }
        } else if (controllerInput && restoreFocusRequest > 0 && restoreFocusRequest != handledPageActivation && !state.loading) {
            if (results.isNotEmpty()) {
                if (restoreResultFocus()) handledPageActivation = restoreFocusRequest
            } else {
                headerCollapsed = false
                inputMode.requestInputMode(InputMode.Keyboard)
                withFrameNanos { }
                runCatching { queryRequester.requestFocus() }
                handledPageActivation = restoreFocusRequest
            }
            keyboard?.hide()
        }
    }
    LaunchedEffect(pendingResultsFocusQuery, state.query, renderedResultKeys, laidOutItemCount, allowFocusRequest) {
        val expectedQuery = pendingResultsFocusQuery ?: return@LaunchedEffect
        if (!controllerInput) { pendingResultsFocusQuery = null; return@LaunchedEffect }
        if (!allowFocusRequest || state.loading || state.query != expectedQuery || (state.searching && results.isEmpty())) return@LaunchedEffect
        withFrameNanos { }
        val restored = if (results.isNotEmpty()) restoreResultFocus() else {
            inputMode.requestInputMode(InputMode.Keyboard)
            runCatching { compactEditRequester.requestFocus() }.isSuccess
        }
        if (restored) pendingResultsFocusQuery = null
    }
    LaunchedEffect(list) {
        var previousIndex = list.firstVisibleItemIndex
        var previousOffset = list.firstVisibleItemScrollOffset
        snapshotFlow { Triple(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset, list.isScrollInProgress) }
            .collect { (index, offset, scrolling) ->
                val direction = when {
                    index > previousIndex -> 1
                    index < previousIndex -> -1
                    offset - previousOffset > 8 -> 1
                    previousOffset - offset > 8 -> -1
                    else -> 0
                }
                if (scrolling && automaticScrollCount == 0 && direction > 0) {
                    if (queryHasFocus) {
                        editing = false
                        focusAfterScroll = currentControllerInput
                        focusManager.clearFocus(force = true)
                        keyboard?.hide()
                    }
                    headerCollapsed = true
                } else if (scrolling && automaticScrollCount == 0 && direction < 0) headerCollapsed = false
                previousIndex = index
                previousOffset = offset
            }
    }
    LaunchedEffect(focusAfterScroll, list.isScrollInProgress) {
        if (controllerInput && focusAfterScroll && !list.isScrollInProgress) {
            val key = list.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? String
            val requester = key?.let { resultRequesters[it] }
            if (requester != null) {
                inputMode.requestInputMode(InputMode.Keyboard)
                withFrameNanos { }
                runCatching { requester.requestFocus() }
            }
            focusAfterScroll = false
        }
    }
    var anchorRestored by remember { mutableStateOf(false) }
    LaunchedEffect(state.loading, renderedResultKeys) {
        if (!anchorRestored && !state.loading && results.isNotEmpty()) { restoreAnchor(); anchorRestored = true }
    }
    LaunchedEffect(laidOutItemCount) {
        if (laidOutItemCount > 0) listHasLaidOutItems = true
    }
    val currentResults by rememberUpdatedState(results)
    val rememberAnchor by rememberUpdatedState(callbacks.onRememberAnchor)
    LaunchedEffect(listHasLaidOutItems, list) {
        if (listHasLaidOutItems) {
            snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }.collect { (index, offset) ->
                val first = currentResults.getOrNull(index) as? SearchResult.Catalog
                if (first != null) rememberAnchor(first.item.id, offset)
            }
        }
    }
    LaunchedEffect(renderedResultKeys) {
        if (controllerInput && allowFocusRequest && !queryHasFocus && focusedResultKey != null && results.isNotEmpty() && results.none { it.key == focusedResultKey }) {
            focusedResultKey = null
            val replacementIndex = focusedResultIndex.coerceIn(0, results.lastIndex)
            results.getOrNull(replacementIndex)?.let { replacement ->
                automaticScrollCount++
                try {
                list.scrollToItem(replacementIndex)
                withFrameNanos { }
                if (list.layoutInfo.visibleItemsInfo.none { it.index == replacementIndex }) return@let
                val requester = resultRequesters[replacement.key] ?: return@let
                inputMode.requestInputMode(InputMode.Keyboard)
                runCatching { requester.requestFocus() }
                } finally { automaticScrollCount-- }
            }
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
    val constrained = maxHeight < 240.dp
    val resultColumns = if (!constrained && maxWidth >= 720.dp) 2 else 1
    val navigator = rememberGridNavigation(renderedResultKeys, focusedResultKey, resultColumns, list, resultRequesters,
        enabled = allowFocusRequest && controllerInput && !editing, reducedMotion = LauncherTheme.motion.reducedMotion,
        onMoving = { if (list.firstVisibleItemIndex > 0) headerCollapsed = true })
    RegisterPageNavigation(navigator::move)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xs)) {
        if (!constrained && !headerCollapsed) Row(Modifier.fillMaxWidth().height(48.dp)) {
            PageHeading("Search", "${results.size} results", Modifier.weight(1f))
            val sort = callbacks.onOpenSort ?: callbacks.onToggleSort
            SortSelector(if (state.sort == "recent") "Recent" else "Title", listOf("Recent", "Title"), sort,
                Modifier.width(112.dp).height(48.dp),
                onFocusChanged = { focused -> if (focused) callbacks.onFocusedAction(FocusedControlAction(
                    LauncherActionDescriptor(SemanticInputAction.CONFIRM, LauncherActionMeaning.CHANGE_FILTER, "Sort"), sort,
                )) else callbacks.onFocusedAction(null) })
        }
        if (headerCollapsed) Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically) {
            LauncherButton("Edit search", ::beginEditing,
                Modifier.focusRequester(compactEditRequester).testTag(SearchScreenTags.Edit),
                onFocusChanged = { hasFocus -> if (hasFocus) callbacks.onFocusedAction(FocusedControlAction(
                    LauncherActionDescriptor(SemanticInputAction.CONFIRM, LauncherActionMeaning.OPEN_SEARCH, "Edit search"), ::beginEditing,
                )) })
            LauncherText("${results.size} results", color = LauncherTheme.colors.textSecondary)
        } else SearchField(
            query = editQuery,
            onQueryChange = {
                handledPageActivation = restoreFocusRequest
                if (!editing) { editEntryQuery = editQuery; editing = true }
                editQuery = it
                onQuery(it)
            },
            modifier = Modifier.focusRequester(queryRequester).fillMaxWidth(),
            onSearch = { finishEditing(cancel = false) },
            onFocusChanged = { focused ->
                queryHasFocus = focused
                // Clearing focus in keyboard mode can briefly return it to this first
                // field before the collapsed layout is placed. That is not a new edit.
                val entersEditing = focused && pendingResultsFocusQuery == null
                if (entersEditing) {
                    headerCollapsed = false
                    focusedResultKey = null
                    if (!editing) { editEntryQuery = editQuery; editing = true }
                }
                onSearchFocus(focused)
                if (entersEditing) callbacks.onFocusedAction(FocusedControlAction(
                    LauncherActionDescriptor(SemanticInputAction.CONFIRM, LauncherActionMeaning.ACTIVATE, "Apply"),
                    { finishEditing(cancel = false) },
                )) else callbacks.onFocusedAction(null)
            },
        )
        if (!constrained && !headerCollapsed) {
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
            Column(Modifier.fillMaxWidth().padding(LauncherTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xs)) {
                LauncherText(if (query.isEmpty()) "Search your library" else if (state.searching) "Searching…" else "No matches",
                    style = LauncherTheme.typography.pageTitle)
                LauncherText(if (query.isEmpty()) "Enter a game, app or setting name." else "Try a different search or category.",
                    color = LauncherTheme.colors.textSecondary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(resultColumns),
                state = list,
                modifier = Modifier.weight(1f).testTag(SearchScreenTags.Results),
                horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm),
            ) {
                items(results, key = SearchResult::key) { result ->
                    when (result) {
                        is SearchResult.Catalog -> {
                            val item = result.item
                            val model = item.toTileUiModel(state.overrides[item.id], item.id in state.recentIds)
                            val open = { callbacks.onSelect(item.id); callbacks.onOpen(item.id) }
                            LibraryItemCard(
                                model, LibraryItemCardVariant.SearchResult,
                                selectedResultKey?.let { it == result.key } ?: (state.selectedItemId == item.id),
                                activationEnabled = model.canOpen,
                                modifier = Modifier.focusRequester(
                                    resultRequesters.getOrPut(result.key) { FocusRequester() },
                                ),
                                iconLoader = iconLoader,
                                statusLabel = callbacks.activityLabels[item.id],
                                onActivate = open,
                                onFocusChanged = { focused -> if (focused) {
                                    focusedResultKey = result.key
                                    selectedResultKey = result.key
                                    focusedResultIndex = results.indexOf(result)
                                    callbacks.onSelect(item.id)
                                    callbacks.onFocusedAction(FocusedControlAction(
                                        LauncherActionDescriptor(SemanticInputAction.CONFIRM, LauncherActionMeaning.ACTIVATE, model.primaryActionLabel, model.canOpen),
                                        if (model.canOpen) open else null, item.id,
                                    ))
                                } else {
                                    if (focusedResultKey == result.key) {
                                        focusedResultKey = null
                                        callbacks.onFocusedAction(null)
                                    }
                                } },
                            )
                        }
                        is SearchResult.System -> {
                            val action = result.action
                            SearchResultCard(
                                title = action.title,
                                subtitle = action.description,
                                activationEnabled = true,
                                selected = selectedResultKey == result.key,
                                onActivate = { callbacks.onOpenSystemAction(action.key) },
                                modifier = Modifier.fillMaxWidth().focusRequester(
                                    resultRequesters.getOrPut(result.key) { FocusRequester() },
                                ),
                                artwork = {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        LauncherGlyphIcon(LauncherGlyph.Settings, Modifier.size(32.dp * LauncherTheme.referenceScale),
                                            contentDescription = null, tint = LauncherTheme.colors.textPrimary)
                                    }
                                },
                                badge = { PlatformBadge("SYSTEM") },
                                onFocusChanged = { focused -> if (focused) {
                                    focusedResultKey = result.key
                                    selectedResultKey = result.key
                                    focusedResultIndex = results.indexOf(result)
                                    callbacks.onFocusedAction(FocusedControlAction(
                                        LauncherActionDescriptor(SemanticInputAction.CONFIRM, LauncherActionMeaning.ACTIVATE, action.title),
                                        { callbacks.onOpenSystemAction(action.key) },
                                    ))
                                } else {
                                    if (focusedResultKey == result.key) {
                                        focusedResultKey = null
                                        callbacks.onFocusedAction(null)
                                    }
                                } },
                            )
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
