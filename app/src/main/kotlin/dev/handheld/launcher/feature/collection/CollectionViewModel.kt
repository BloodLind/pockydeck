package dev.handheld.launcher.feature.collection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.handheld.launcher.core.domain.model.*
import dev.handheld.launcher.core.domain.policy.LibraryItemOrdering
import dev.handheld.launcher.core.domain.repository.*
import dev.handheld.launcher.ui.presentation.RomPlatformLabels
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale

data class CollectionUiState(
    val destination: LauncherDestination,
    val items: List<LibraryItem> = emptyList(),
    val allItems: List<LibraryItem> = emptyList(),
    val favorites: Set<ItemId> = emptySet(),
    val overrides: Map<ItemId, UserItemOverrides> = emptyMap(),
    val recentIds: Set<ItemId> = emptySet(),
    val selectedItemId: ItemId? = null,
    val query: String = "",
    val filter: String = "all",
    val sort: String = "recent",
    val firstVisibleItemId: ItemId? = null,
    val firstVisibleOffsetPx: Int = 0,
    val loading: Boolean = true,
    val inventoryIncomplete: Boolean = false,
    val error: String? = null,
    val searching: Boolean = false,
) {
    val selectedItem: LibraryItem? get() = items.find { it.id == selectedItemId }
    fun snapshot() = DestinationSnapshot(destination, selectedItemId, firstVisibleItemId,
        firstVisibleOffsetPx, query, PageStateKey(filter), PageStateKey(sort))
}

private data class CollectionOptions(
    val selectedId: ItemId? = null,
    val query: String = "",
    val filter: String = "all",
    val sort: String = "recent",
    val firstVisibleId: ItemId? = null,
    val offset: Int = 0,
)

private data class CollectionCriteria(val query: String, val filter: String, val sort: String)
private fun CollectionOptions.criteria() = CollectionCriteria(query, filter, sort)
private data class CollectionIndex(
    val allItems: List<LibraryItem>,
    val scoped: List<LibraryItem>,
    val favorites: Set<ItemId>,
    val overrides: Map<ItemId, UserItemOverrides>,
    val records: List<SuccessfulOpenRecord>,
    val terms: Map<ItemId, String>,
    val filters: List<String>,
    val incomplete: Boolean,
) { val recentIds = records.map { it.itemId }.toSet() }
private data class CollectionResults(
    val index: CollectionIndex,
    val criteria: CollectionCriteria,
    val filter: String,
    val items: List<LibraryItem>,
    val searching: Boolean = false,
) {
    val ids = items.map { it.id }
    val idSet = ids.toHashSet()
}

/** Each destination has its own small state; every page reads the same persisted catalog. */
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionViewModel(
    private val destination: LauncherDestination,
    catalog: CatalogRepository,
    private val favorites: FavoriteRepository,
    private val overrides: ItemOverrideRepository,
    recent: SuccessfulOpenRepository,
    private val snapshots: NavigationSnapshotRepository,
    private val savedState: SavedStateHandle,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val options = MutableStateFlow(CollectionOptions(
        selectedId = savedState.get<String>("selected")?.let(::ItemId),
        query = savedState["query"] ?: "",
        filter = savedState["filter"] ?: "all",
        sort = savedState["sort"] ?: "recent",
        firstVisibleId = savedState.get<String>("anchor")?.let(::ItemId),
        offset = savedState["offset"] ?: 0,
    ))
    private val error = MutableStateFlow<String?>(null)
    private val restoredFromSavedState = savedState.contains("query")
    private var userChangedState = false
    private var persistJob: Job? = null
    private var previousIds = emptyList<ItemId>()
    private var previousIdSet: Set<ItemId> = emptySet()
    private var lastResolvedSelection: ItemId? = null

    // Selection/viewport changes must never rebuild a search index or re-sort thousands of games.
    private val index = combine(
        catalog.snapshot, favorites.favoriteItemIds, overrides.overridesByItemId,
        recent.records,
    ) { catalogState, favoriteIds, overrideMap, records ->
        val scoped = collectionDestinationItems(destination, catalogState.items, overrideMap, favoriteIds)
        val terms = scoped.associate { item ->
            val consoleTerms = (item as? LibraryItem.RomGame)?.let { RomPlatformLabels.searchTerms(it.platformId) }.orEmpty()
            item.id to (listOf(item.title) + consoleTerms).joinToString("\n").lowercase(Locale.ROOT)
        }
        CollectionIndex(catalogState.items, scoped, favoriteIds, overrideMap, records, terms,
            collectionFilterKeys(destination, scoped, overrideMap, favoriteIds),
            catalogState.inventoryStatus is InventoryStatus.Incomplete)
    }.flowOn(computationDispatcher)

    private val results = combine(index, options.map { it.criteria() }.distinctUntilChanged()) { data, criteria ->
        val filter = criteria.filter.takeIf { it in data.filters } ?: "all"
        val query = criteria.query.trim().lowercase(Locale.ROOT)
        val matching = if (destination == LauncherDestination.SEARCH && query.isEmpty()) emptyList() else data.scoped.filter { item ->
            collectionFilterMatches(item, filter, data.overrides) &&
                (query.isEmpty() || data.terms[item.id]?.contains(query) == true)
        }
        val ordered = if (criteria.sort == "title") matching.sortedWith(LibraryItemOrdering.titleThenId)
            else LibraryItemOrdering.recentFirst(matching, data.records)
        CollectionResults(data, criteria, filter, ordered)
    }.transformLatest { result ->
        if (destination == LauncherDestination.SEARCH && result.items.size > 128) {
            var count = 128
            while (count < result.items.size) {
                emit(result.copy(items = result.items.take(count), searching = true))
                delay(24)
                count += 256
            }
        }
        emit(result)
    }.flowOn(computationDispatcher)

    val state: StateFlow<CollectionUiState> = combine(results, options, error) { result, current, message ->
        val data = result.index
        val matchesCriteria = result.criteria == current.criteria()
        val ordered = if (matchesCriteria) result.items else emptyList()
        val ids = if (matchesCriteria) result.ids else emptyList()
        val idSet = if (matchesCriteria) result.idSet else emptySet()
        val selected = current.selectedId?.takeIf { it in idSet } ?: run {
            val oldIndex = previousIds.indexOf(current.selectedId).takeIf { it >= 0 }
                ?: previousIds.indexOf(lastResolvedSelection)
            if (oldIndex >= 0) previousIds.withIndex().filter { it.value in idSet }
                .minByOrNull { kotlin.math.abs(it.index - oldIndex) }?.value ?: ids.firstOrNull()
            else ids.firstOrNull()
        }
        if (matchesCriteria && (!result.searching || ids.isNotEmpty())) {
            previousIds = ids
            previousIdSet = idSet
            lastResolvedSelection = selected
        }
        CollectionUiState(destination, ordered, data.allItems, data.favorites, data.overrides,
            data.recentIds, selected, current.query, if (matchesCriteria) result.filter else current.filter,
            current.sort, current.firstVisibleId, current.offset, loading = false,
            inventoryIncomplete = data.incomplete, error = message,
            searching = !matchesCriteria || result.searching)
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CollectionUiState(destination))

    init {
        viewModelScope.launch {
            val stored = try {
                snapshots.observe(destination).first()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }
            stored?.let {
                if (!userChangedState && !restoredFromSavedState) {
                    options.value = CollectionOptions(it.selectedItemId, it.query,
                        it.filterKey?.value ?: "all", it.sortKey?.value ?: "recent",
                        it.firstVisibleItemId, it.firstVisibleOffsetPx)
                }
            }
        }
    }

    fun select(id: ItemId) = update(userInitiated = false) { copy(selectedId = id) }
    fun query(value: String) = update { copy(query = value, firstVisibleId = null, offset = 0) }
    fun filter(value: String) = update { copy(filter = value, firstVisibleId = null, offset = 0) }
    fun toggleSort() = update { copy(sort = if (sort == "recent") "title" else "recent") }
    fun sort(value: String) = update { copy(sort = if (value == "title") "title" else "recent", firstVisibleId = null, offset = 0) }
    fun cycleFilter(delta: Int): Boolean {
        val current = state.value
        val keys = collectionFilterKeys(destination, current.allItems, current.overrides, current.favorites)
        // All is a fixed shortcut before the finite category sequence. Reaching an end
        // consumes the input without wrapping or resetting the current grid position.
        if (keys.isNotEmpty()) {
            val index = keys.indexOf(options.value.filter).coerceAtLeast(0)
            val next = (index + delta).coerceIn(0, keys.lastIndex)
            if (next != index) { filter(keys[next]); return true }
        }
        return false
    }
    fun rememberAnchor(id: ItemId?, offset: Int) = update(userInitiated = false) {
        copy(selectedId = selectedId?.takeIf { it in previousIdSet } ?: state.value.selectedItemId,
            firstVisibleId = id, offset = offset.coerceAtLeast(0))
    }
    fun clearError() { error.value = null }

    fun setFavorite(id: ItemId, value: Boolean) {
        viewModelScope.launch {
            runCatching { favorites.setFavorite(id, value) }
                .onFailure {
                    if (it is CancellationException) throw it
                    error.value = "Could not save favorite. Try again."
                }
        }
    }

    fun setCategory(id: ItemId, category: LibraryCategory?) {
        viewModelScope.launch {
            val existing = state.value.overrides[id] ?: UserItemOverrides()
            runCatching { overrides.setOverrides(id, existing.copy(category = category)) }
                .onFailure {
                    if (it is CancellationException) throw it
                    error.value = "Could not save category. Try again."
                }
        }
    }

    private fun update(
        userInitiated: Boolean = true,
        change: CollectionOptions.() -> CollectionOptions,
    ) {
        if (userInitiated) userChangedState = true
        val next = options.value.change()
        if (next == options.value) return
        options.value = next
        val current = options.value
        savedState["selected"] = current.selectedId?.value
        savedState["query"] = current.query
        savedState["filter"] = current.filter
        savedState["sort"] = current.sort
        savedState["anchor"] = current.firstVisibleId?.value
        savedState["offset"] = current.offset
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(250)
            runCatching { snapshots.save(state.value.snapshot()) }
                .onFailure {
                    if (it is CancellationException) throw it
                    error.value = "Could not save your position. Try again."
                }
        }
    }
}
