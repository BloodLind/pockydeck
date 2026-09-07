package dev.handheld.launcher.feature.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import dev.handheld.launcher.core.data.discovery.AndroidCatalogRefreshState
import dev.handheld.launcher.core.domain.model.DestinationSnapshot
import dev.handheld.launcher.core.domain.model.InventoryStatus
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LauncherDestination
import dev.handheld.launcher.core.domain.model.LibraryItemKind
import dev.handheld.launcher.core.domain.policy.LibraryItemOrdering
import dev.handheld.launcher.core.domain.repository.CatalogRepository
import dev.handheld.launcher.core.domain.repository.ItemOverrideRepository
import dev.handheld.launcher.core.domain.repository.NavigationSnapshotRepository
import dev.handheld.launcher.core.domain.repository.SuccessfulOpenRepository
import dev.handheld.launcher.launch.LaunchCoordinator
import dev.handheld.launcher.launch.LaunchCoordinatorFailure
import dev.handheld.launcher.launch.LaunchCoordinatorState
import dev.handheld.launcher.ui.presentation.TileUiModel
import dev.handheld.launcher.ui.presentation.toTileUiModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

@Immutable
data class HomeUiState(
    val items: List<TileUiModel> = emptyList(),
    val selectedItemId: ItemId? = null,
    val firstVisibleItemId: ItemId? = null,
    val firstVisibleOffsetPx: Int = 0,
    val loading: Boolean = true,
    val refreshState: AndroidCatalogRefreshState = AndroidCatalogRefreshState.Idle,
    val inventoryIncomplete: Boolean = false,
    val pendingLaunchItemId: ItemId? = null,
    val notice: HomeNotice? = null,
    val focusRequestSequence: Long = 0,
) {
    val selectedItem: TileUiModel?
        get() = items.firstOrNull { it.itemId == selectedItemId }

    fun snapshot(): DestinationSnapshot = DestinationSnapshot(
        destination = LauncherDestination.HOME,
        selectedItemId = selectedItemId,
        firstVisibleItemId = firstVisibleItemId,
        firstVisibleOffsetPx = firstVisibleOffsetPx.coerceAtLeast(0),
    )
}

@Immutable
sealed interface HomeNotice {
    data object CachedCatalogRefreshing : HomeNotice
    data object InventoryRefreshFailed : HomeNotice
    data object SelectedItemRemoved : HomeNotice
    data object PositionSaveFailed : HomeNotice
    data class LaunchFailed(val reason: LaunchCoordinatorFailure) : HomeNotice
}

private data class HomePosition(
    val selectedItemId: ItemId? = null,
    val firstVisibleItemId: ItemId? = null,
    val firstVisibleOffsetPx: Int = 0,
    val focusRequestSequence: Long = 0,
)

class HomeViewModel(
    private val catalogRepository: CatalogRepository,
    successfulOpenRepository: SuccessfulOpenRepository,
    itemOverrideRepository: ItemOverrideRepository,
    private val navigationSnapshotRepository: NavigationSnapshotRepository,
    private val launchCoordinator: LaunchCoordinator,
    refreshState: StateFlow<AndroidCatalogRefreshState>,
    private val refreshCatalog: () -> Unit,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val position = MutableStateFlow(
        HomePosition(
            selectedItemId = savedStateHandle.get<String>(SELECTED_KEY)?.let(::ItemId),
            firstVisibleItemId = savedStateHandle.get<String>(ANCHOR_KEY)?.let(::ItemId),
            firstVisibleOffsetPx = savedStateHandle[OFFSET_KEY] ?: 0,
        ),
    )
    private val localNotice = MutableStateFlow<HomeNotice?>(null)
    private var priorOrderedIds: List<ItemId> = emptyList()
    private val restoredFromSavedState = savedStateHandle.contains(SELECTED_KEY)
    private var userChangedPosition = false
    private var persistJob: Job? = null

    val state: StateFlow<HomeUiState> = combine(
        catalogRepository.snapshot,
        successfulOpenRepository.records,
        itemOverrideRepository.overridesByItemId,
        refreshState,
        position,
    ) { catalog, recentRecords, overrides, refresh, currentPosition ->
        val active = catalog.activeItems.filter { it.kind != LibraryItemKind.SYSTEM_ACTION }
        val recentIds = recentRecords.mapTo(hashSetOf()) { it.itemId }
        val ordered = LibraryItemOrdering.recentFirst(active, recentRecords).take(MAX_HOME_ITEMS)
        val orderedIds = ordered.map { it.id }
        val selected = currentPosition.selectedItemId?.takeIf { it in orderedIds }
            ?: nearestRemainingId(currentPosition.selectedItemId, priorOrderedIds, orderedIds)
            ?: orderedIds.firstOrNull()
        val anchor = currentPosition.firstVisibleItemId?.takeIf { it in orderedIds }
            ?: nearestRemainingId(currentPosition.firstVisibleItemId, priorOrderedIds, orderedIds)
            ?: selected
        val selectedWasRemoved = currentPosition.selectedItemId != null &&
            currentPosition.selectedItemId !in orderedIds && priorOrderedIds.isNotEmpty()
        priorOrderedIds = orderedIds
        HomeUiState(
            items = ordered.map { item ->
                item.toTileUiModel(
                    overrides = overrides[item.id],
                    recentlyOpened = item.id in recentIds,
                )
            },
            selectedItemId = selected,
            firstVisibleItemId = anchor,
            firstVisibleOffsetPx = currentPosition.firstVisibleOffsetPx,
            loading = false,
            refreshState = refresh,
            inventoryIncomplete = catalog.inventoryStatus is InventoryStatus.Incomplete,
            notice = when {
                selectedWasRemoved -> HomeNotice.SelectedItemRemoved
                refresh is AndroidCatalogRefreshState.Error -> HomeNotice.InventoryRefreshFailed
                refresh is AndroidCatalogRefreshState.Refreshing && ordered.isNotEmpty() ->
                    HomeNotice.CachedCatalogRefreshing
                else -> null
            },
            focusRequestSequence = currentPosition.focusRequestSequence,
        )
    }.combine(launchCoordinator.state) { current, launch ->
        val homeLaunch = launch.originOrNull()?.takeIf {
            it.destination == LauncherDestination.HOME
        }
        when {
            homeLaunch == null -> current
            launch is LaunchCoordinatorState.Pending -> current.copy(
                pendingLaunchItemId = launch.itemId,
                notice = localNotice.value ?: current.notice,
            )
            launch is LaunchCoordinatorState.Failed -> current.copy(
                pendingLaunchItemId = null,
                notice = HomeNotice.LaunchFailed(launch.reason),
            )
            else -> current.copy(pendingLaunchItemId = null, notice = localNotice.value ?: current.notice)
        }
    }.combine(localNotice) { current, notice ->
        if (notice == null) current else current.copy(notice = notice)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    init {
        viewModelScope.launch {
            val stored = try {
                navigationSnapshotRepository.observe(LauncherDestination.HOME).first()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                localNotice.value = HomeNotice.PositionSaveFailed
                null
            }
            stored?.let {
                if (!userChangedPosition && !restoredFromSavedState) {
                    position.value = HomePosition(
                        selectedItemId = it.selectedItemId,
                        firstVisibleItemId = it.firstVisibleItemId,
                        firstVisibleOffsetPx = it.firstVisibleOffsetPx,
                    )
                }
            }
        }
        viewModelScope.launch {
            launchCoordinator.state.collect { launch ->
                if (launch is LaunchCoordinatorState.Succeeded &&
                    launch.origin.destination == LauncherDestination.HOME
                ) {
                    val current = position.value
                    position.value = current.copy(
                        selectedItemId = launch.itemId,
                        focusRequestSequence = current.focusRequestSequence + 1,
                    )
                    localNotice.value = null
                }
            }
        }
    }

    fun select(itemId: ItemId) {
        if (state.value.items.none { it.itemId == itemId }) return
        updatePosition { copy(selectedItemId = itemId) }
    }

    fun activate(itemId: ItemId): Boolean {
        val current = state.value
        val item = current.items.firstOrNull { it.itemId == itemId } ?: return false
        if (!item.canOpen) {
            localNotice.value = HomeNotice.LaunchFailed(LaunchCoordinatorFailure.TARGET_UNAVAILABLE)
            return false
        }
        localNotice.value = null
        return launchCoordinator.submit(itemId, current.copy(selectedItemId = itemId).snapshot())
    }

    fun rememberViewport(firstVisibleItemId: ItemId?, offsetPx: Int) {
        updatePosition(persistImmediately = false, userInitiated = false) {
            copy(
                firstVisibleItemId = firstVisibleItemId,
                firstVisibleOffsetPx = offsetPx.coerceAtLeast(0),
            )
        }
    }

    fun refresh() {
        localNotice.value = null
        refreshCatalog()
    }

    fun clearNotice() {
        localNotice.value = null
        launchCoordinator.clearResult()
    }

    private fun updatePosition(
        persistImmediately: Boolean = false,
        userInitiated: Boolean = true,
        transform: HomePosition.() -> HomePosition,
    ) {
        if (userInitiated) userChangedPosition = true
        position.value = position.value.transform()
        val current = position.value
        savedStateHandle[SELECTED_KEY] = current.selectedItemId?.value
        savedStateHandle[ANCHOR_KEY] = current.firstVisibleItemId?.value
        savedStateHandle[OFFSET_KEY] = current.firstVisibleOffsetPx
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            if (!persistImmediately) delay(SNAPSHOT_DEBOUNCE_MS)
            try {
                navigationSnapshotRepository.save(state.value.snapshot())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                localNotice.value = HomeNotice.PositionSaveFailed
            }
        }
    }

    companion object {
        const val MAX_HOME_ITEMS = 12
        private const val SNAPSHOT_DEBOUNCE_MS = 250L
        private const val SELECTED_KEY = "home.selected"
        private const val ANCHOR_KEY = "home.anchor"
        private const val OFFSET_KEY = "home.offset"
    }
}

class HomeViewModelFactory(
    private val catalogRepository: CatalogRepository,
    private val successfulOpenRepository: SuccessfulOpenRepository,
    private val itemOverrideRepository: ItemOverrideRepository,
    private val navigationSnapshotRepository: NavigationSnapshotRepository,
    private val launchCoordinator: LaunchCoordinator,
    private val refreshState: StateFlow<AndroidCatalogRefreshState>,
    private val refreshCatalog: () -> Unit,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return create(modelClass, CreationExtras.Empty)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(HomeViewModel::class.java))
        val savedStateHandle = runCatching { extras.createSavedStateHandle() }
            .getOrElse { SavedStateHandle() }
        return HomeViewModel(
            catalogRepository,
            successfulOpenRepository,
            itemOverrideRepository,
            navigationSnapshotRepository,
            launchCoordinator,
            refreshState,
            refreshCatalog,
            savedStateHandle,
        ) as T
    }
}

private fun nearestRemainingId(
    removedId: ItemId?,
    priorIds: List<ItemId>,
    currentIds: List<ItemId>,
): ItemId? {
    val oldIndex = priorIds.indexOf(removedId)
    if (oldIndex < 0) return null
    return priorIds.withIndex()
        .asSequence()
        .filter { it.value in currentIds }
        .minWithOrNull(compareBy({ kotlin.math.abs(it.index - oldIndex) }, { it.index }))
        ?.value
}

private fun LaunchCoordinatorState.originOrNull(): DestinationSnapshot? = when (this) {
    LaunchCoordinatorState.Idle -> null
    is LaunchCoordinatorState.Pending -> origin
    is LaunchCoordinatorState.Succeeded -> origin
    is LaunchCoordinatorState.Failed -> origin
}
