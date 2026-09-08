package dev.handheld.launcher.feature.collection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.handheld.launcher.core.domain.model.*
import dev.handheld.launcher.core.domain.policy.LibraryItemOrdering
import dev.handheld.launcher.core.domain.repository.*
import dev.handheld.launcher.ui.presentation.RomPlatformLabels
import kotlinx.coroutines.Job
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

/** Each destination has its own small state; every page reads the same persisted catalog. */
class CollectionViewModel(
    private val destination: LauncherDestination,
    catalog: CatalogRepository,
    private val favorites: FavoriteRepository,
    private val overrides: ItemOverrideRepository,
    recent: SuccessfulOpenRepository,
    private val snapshots: NavigationSnapshotRepository,
    private val savedState: SavedStateHandle,
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

    val state: StateFlow<CollectionUiState> = combine(
        catalog.snapshot, favorites.favoriteItemIds, overrides.overridesByItemId,
        recent.records, options,
    ) { catalogState, favoriteIds, overrideMap, records, current ->
        val scoped = collectionDestinationItems(destination, catalogState.items, overrideMap, favoriteIds)
        val effectiveFilter = current.filter.takeIf {
            it in collectionFilterKeys(destination, scoped, overrideMap, favoriteIds)
        } ?: "all"
        val query = current.query.trim().lowercase(Locale.ROOT)
        val matching = if (destination == LauncherDestination.SEARCH && query.isEmpty()) emptyList() else scoped.filter { item ->
            val consoleTerms = (item as? LibraryItem.RomGame)?.let { RomPlatformLabels.searchTerms(it.platformId) }.orEmpty()
            collectionFilterMatches(item, effectiveFilter, overrideMap) &&
                (item.title.lowercase(Locale.ROOT).contains(query) ||
                    consoleTerms.any { it.lowercase(Locale.ROOT).contains(query) })
        }
        val ordered = if (current.sort == "title") matching.sortedWith(LibraryItemOrdering.titleThenId)
            else LibraryItemOrdering.recentFirst(matching, records)
        val ids = ordered.map { it.id }
        val selected = current.selectedId?.takeIf { it in ids } ?: run {
            val oldIndex = previousIds.indexOf(current.selectedId)
            if (oldIndex >= 0) previousIds.withIndex().filter { it.value in ids }
                .minByOrNull { kotlin.math.abs(it.index - oldIndex) }?.value ?: ids.firstOrNull()
            else ids.firstOrNull()
        }
        previousIds = ids
        CollectionUiState(destination, ordered, catalogState.items, favoriteIds, overrideMap,
            records.map { it.itemId }.toSet(), selected, current.query, effectiveFilter,
            current.sort, current.firstVisibleId, current.offset, loading = false,
            inventoryIncomplete = catalogState.inventoryStatus is InventoryStatus.Incomplete)
    }.combine(error) { current, message -> current.copy(error = message) }
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
    fun rememberAnchor(id: ItemId?, offset: Int) = update(userInitiated = false) {
        copy(firstVisibleId = id, offset = offset.coerceAtLeast(0))
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
        options.value = options.value.change()
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
