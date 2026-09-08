package dev.handheld.launcher.feature.home

import androidx.lifecycle.SavedStateHandle
import dev.handheld.launcher.core.data.discovery.AndroidCatalogRefreshState
import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.CatalogInventory
import dev.handheld.launcher.core.domain.model.CatalogReconciliation
import dev.handheld.launcher.core.domain.model.CatalogSnapshot
import dev.handheld.launcher.core.domain.model.CatalogSourceId
import dev.handheld.launcher.core.domain.model.CurrentUserAndroidComponentId
import dev.handheld.launcher.core.domain.model.DestinationSnapshot
import dev.handheld.launcher.core.domain.model.InventoryStatus
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LaunchAcknowledgement
import dev.handheld.launcher.core.domain.model.LaunchOperationId
import dev.handheld.launcher.core.domain.model.LaunchRequest
import dev.handheld.launcher.core.domain.model.LauncherDestination
import dev.handheld.launcher.core.domain.model.LibraryCategory
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.SuccessfulOpenCandidate
import dev.handheld.launcher.core.domain.model.SuccessfulOpenRecord
import dev.handheld.launcher.core.domain.model.SuccessfulOpenWriteResult
import dev.handheld.launcher.core.domain.model.SupportedItemAction
import dev.handheld.launcher.core.domain.model.UserItemOverrides
import dev.handheld.launcher.core.domain.repository.CatalogRepository
import dev.handheld.launcher.core.domain.repository.ItemOverrideRepository
import dev.handheld.launcher.core.domain.repository.LaunchDispatcher
import dev.handheld.launcher.core.domain.repository.NavigationSnapshotRepository
import dev.handheld.launcher.core.domain.repository.SuccessfulOpenRepository
import dev.handheld.launcher.launch.LaunchCoordinator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `cancelled emulator choice preserves order without an error notice`() = runTest(dispatcher) {
        val items=listOf(homeAndroidItem('A'),homeAndroidItem('B'))
        val catalog=HomeCatalog(items)
        val opens=HomeOpens()
        val snapshots=HomeSnapshots(null)
        val coordinator=LaunchCoordinator(catalog,snapshots,object:LaunchDispatcher {
            override suspend fun dispatch(request:LaunchRequest)=LaunchAcknowledgement.Failed(
                request.operationId,dev.handheld.launcher.core.domain.model.LaunchFailureReason.CANCELLED)
        },opens,this)
        val viewModel=HomeViewModel(catalog,opens,HomeOverrides(),snapshots,coordinator,
            MutableStateFlow(AndroidCatalogRefreshState.Idle),{},SavedStateHandle(), dispatcher)
        advanceUntilIdle()
        assertTrue(viewModel.activate(items[1].id))
        advanceUntilIdle()
        assertEquals(items.map { it.id },viewModel.state.value.items.map { it.itemId })
        assertTrue(opens.candidates.isEmpty())
        assertEquals(null,viewModel.state.value.notice)
        assertEquals(null,viewModel.state.value.pendingLaunchItemId)
    }

    @Test
    fun `cached home under the cap keeps its items and successful open preserves selected identity at front`() =
        runTest(dispatcher) {
            val items = ('A'..'M').map(::homeAndroidItem)
            val catalog = HomeCatalog(items)
            val opens = HomeOpens()
            val snapshots = HomeSnapshots(
                DestinationSnapshot(
                    LauncherDestination.HOME,
                    selectedItemId = items[1].id,
                    firstVisibleItemId = items.first().id,
                ),
            )
            val coordinator = LaunchCoordinator(
                catalogRepository = catalog,
                navigationSnapshotRepository = snapshots,
                launchDispatcher = object : LaunchDispatcher {
                    override suspend fun dispatch(request: LaunchRequest): LaunchAcknowledgement =
                        LaunchAcknowledgement.Dispatched(request.operationId)
                },
                successfulOpenRepository = opens,
                scope = this,
                operationIdFactory = { LaunchOperationId("open-b") },
            )
            val viewModel = HomeViewModel(
                catalogRepository = catalog,
                successfulOpenRepository = opens,
                itemOverrideRepository = HomeOverrides(),
                navigationSnapshotRepository = snapshots,
                launchCoordinator = coordinator,
                refreshState = MutableStateFlow(AndroidCatalogRefreshState.Refreshing(
                    dev.handheld.launcher.core.data.discovery.AndroidCatalogRefreshTrigger.STARTUP,
                )),
                refreshCatalog = {},
                savedStateHandle = SavedStateHandle(),
                computationDispatcher = dispatcher,
            )
            advanceUntilIdle()

            assertEquals(items.size, viewModel.state.value.items.size)
            assertEquals(items[1].id, viewModel.state.value.selectedItemId)
            assertTrue(viewModel.activate(items[1].id))
            advanceUntilIdle()

            assertEquals(items[1].id, viewModel.state.value.items.first().itemId)
            assertEquals(items[1].id, viewModel.state.value.selectedItemId)
            assertEquals(1, opens.candidates.size)
            assertEquals(1L, viewModel.state.value.focusRequestSequence)
        }

    @Test
    fun `initial viewport callback does not suppress delayed durable snapshot`() =
        runTest(dispatcher) {
            val items = listOf(homeAndroidItem('A'), homeAndroidItem('B'))
            val releaseSnapshot = CompletableDeferred<Unit>()
            val snapshots = HomeSnapshots(
                DestinationSnapshot(
                    LauncherDestination.HOME,
                    selectedItemId = items[1].id,
                    firstVisibleItemId = items[1].id,
                    firstVisibleOffsetPx = 19,
                ),
                releaseSnapshot,
            )
            val opens = HomeOpens()
            val viewModel = HomeViewModel(
                catalogRepository = HomeCatalog(items),
                successfulOpenRepository = opens,
                itemOverrideRepository = HomeOverrides(),
                navigationSnapshotRepository = snapshots,
                launchCoordinator = LaunchCoordinator(
                    HomeCatalog(items), snapshots,
                    object : LaunchDispatcher {
                        override suspend fun dispatch(request: LaunchRequest): LaunchAcknowledgement =
                            LaunchAcknowledgement.Dispatched(request.operationId)
                    },
                    opens,
                    this,
                ),
                refreshState = MutableStateFlow(AndroidCatalogRefreshState.Idle),
                refreshCatalog = {},
                savedStateHandle = SavedStateHandle(),
                computationDispatcher = dispatcher,
            )
            runCurrent()

            viewModel.rememberViewport(items[0].id, 0)
            releaseSnapshot.complete(Unit)
            advanceUntilIdle()

            assertEquals(items[1].id, viewModel.state.value.selectedItemId)
            assertEquals(items[1].id, viewModel.state.value.firstVisibleItemId)
            assertEquals(19, viewModel.state.value.firstVisibleOffsetPx)
        }

    @Test
    fun `home caps the existing newest game rom app order at twenty and excludes unassigned roms`() =
        runTest(dispatcher) {
            val apps = ('A'..'Z').map(::homeAndroidItem)
            val assigned = homeRomItem("Assigned", "gba")
            val catalog = HomeCatalog(
                apps + assigned + homeRomItem("Unassigned", null) + homeRomItem("Unknown", "unknown"),
            )
            val opens = HomeOpens().apply {
                records.value = listOf(SuccessfulOpenRecord(apps[24].id, 1L))
            }
            val overrides = HomeOverrides().apply {
                overridesByItemId.value = mapOf(apps[25].id to UserItemOverrides(category = LibraryCategory.GAME))
            }
            val viewModel = createHome(catalog, opens = opens, overrides = overrides)
            advanceUntilIdle()

            val expected = listOf(apps[24].id, apps[25].id, assigned.id) + apps.take(17).map { it.id }
            assertEquals(20, viewModel.state.value.items.size)
            assertEquals(expected, viewModel.state.value.items.map { it.itemId })
            assertFalse(viewModel.activate(apps[23].id))
        }

    @Test
    fun `selection viewport and refresh reuse off main catalog projection and save position immediately`() =
        runTest(dispatcher) {
            val computation = TrackingDispatcher(dispatcher)
            var catalogReadOutsideComputation = false
            val apps = ('A'..'Z').map(::homeAndroidItem)
            val countedItems = ReadCountingList<LibraryItem>(apps) {
                if (!computation.isRunning) catalogReadOutsideComputation = true
            }
            val catalog = HomeCatalog(countedItems)
            val overrides = HomeOverrides()
            val saved = SavedStateHandle()
            val refresh = MutableStateFlow<AndroidCatalogRefreshState>(AndroidCatalogRefreshState.Idle)
            val viewModel = createHome(
                catalog,
                overrides = overrides,
                savedStateHandle = saved,
                refresh = refresh,
                computationDispatcher = computation,
            )
            advanceUntilIdle()
            val presented = viewModel.state.value.items
            val readsAfterProjection = countedItems.reads
            assertTrue(readsAfterProjection > 0)

            // These callbacks can occur in one frame, before the state flow publishes either one.
            viewModel.select(apps[19].id)
            viewModel.rememberViewport(apps[3].id, 31)
            assertEquals(apps[19].id.value, saved.get<String>("home.selected"))
            assertEquals(apps[3].id.value, saved.get<String>("home.anchor"))
            assertEquals(31, saved.get<Int>("home.offset"))
            runCurrent()
            refresh.value = AndroidCatalogRefreshState.Refreshing(
                dev.handheld.launcher.core.data.discovery.AndroidCatalogRefreshTrigger.STARTUP,
            )
            advanceUntilIdle()

            assertEquals(apps[19].id, viewModel.state.value.selectedItemId)
            assertEquals(apps[3].id, viewModel.state.value.firstVisibleItemId)
            assertEquals(31, viewModel.state.value.firstVisibleOffsetPx)
            assertEquals(HomeNotice.CachedCatalogRefreshing, viewModel.state.value.notice)
            assertSame(presented, viewModel.state.value.items)
            assertEquals(readsAfterProjection, countedItems.reads)
            assertFalse(catalogReadOutsideComputation)

            // A real ordering input must still invalidate the projection.
            overrides.overridesByItemId.value = mapOf(
                apps.last().id to UserItemOverrides(category = LibraryCategory.GAME),
            )
            advanceUntilIdle()
            assertEquals(apps.last().id, viewModel.state.value.items.first().itemId)
            assertEquals(20, viewModel.state.value.items.size)
            assertTrue(countedItems.reads > readsAfterProjection)
            assertFalse(catalogReadOutsideComputation)
        }

    @Test
    fun `successive removals retain the nearest surviving selection and persist that recovery`() =
        runTest(dispatcher) {
            val apps = ('A'..'F').map(::homeAndroidItem)
            val catalog = HomeCatalog(apps)
            val saved = SavedStateHandle()
            val snapshots = HomeSnapshots(DestinationSnapshot(
                LauncherDestination.HOME,
                selectedItemId = apps[3].id,
                firstVisibleItemId = apps[3].id,
                firstVisibleOffsetPx = 12,
            ))
            val viewModel = createHome(catalog, snapshots = snapshots, savedStateHandle = saved)
            advanceUntilIdle()

            catalog.replaceItems(apps.filterNot { it.id == apps[3].id })
            advanceUntilIdle()
            assertEquals(apps[2].id, viewModel.state.value.selectedItemId)
            assertEquals(apps[2].id, viewModel.state.value.firstVisibleItemId)
            assertEquals(HomeNotice.SelectedItemRemoved, viewModel.state.value.notice)

            catalog.replaceItems(apps.filterNot { it.id in setOf(apps[2].id, apps[3].id) })
            advanceUntilIdle()
            assertEquals(apps[1].id, viewModel.state.value.selectedItemId)
            viewModel.rememberViewport(apps[1].id, 27)
            assertEquals(apps[1].id.value, saved.get<String>("home.selected"))
            advanceUntilIdle()
            assertEquals(apps[1].id, viewModel.state.value.selectedItemId)
            assertEquals(apps[1].id, viewModel.state.value.firstVisibleItemId)
            assertEquals(apps[1].id, snapshots.latest?.selectedItemId)
            assertEquals(27, snapshots.latest?.firstVisibleOffsetPx)
        }

    @Test
    fun `viewport before catalog computation retains the durable restored selection`() = runTest(dispatcher) {
        val apps = listOf(homeAndroidItem('A'), homeAndroidItem('B'))
        val releaseCatalog = CompletableDeferred<Unit>()
        val catalog = HomeCatalog(apps, releaseCatalog)
        val snapshots = HomeSnapshots(DestinationSnapshot(
            LauncherDestination.HOME,
            selectedItemId = apps[1].id,
            firstVisibleItemId = apps[1].id,
        ))
        val saved = SavedStateHandle()
        val viewModel = createHome(catalog, snapshots = snapshots, savedStateHandle = saved)
        runCurrent()
        assertTrue(viewModel.state.value.loading)

        viewModel.rememberViewport(null, 0)
        assertEquals(apps[1].id.value, saved.get<String>("home.selected"))
        releaseCatalog.complete(Unit)
        advanceUntilIdle()
        assertEquals(apps[1].id, viewModel.state.value.selectedItemId)
    }

    private fun TestScope.createHome(
        catalog: HomeCatalog,
        opens: HomeOpens = HomeOpens(),
        overrides: HomeOverrides = HomeOverrides(),
        snapshots: HomeSnapshots = HomeSnapshots(null),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        refresh: MutableStateFlow<AndroidCatalogRefreshState> = MutableStateFlow(AndroidCatalogRefreshState.Idle),
        computationDispatcher: CoroutineDispatcher = dispatcher,
    ): HomeViewModel = HomeViewModel(
        catalogRepository = catalog,
        successfulOpenRepository = opens,
        itemOverrideRepository = overrides,
        navigationSnapshotRepository = snapshots,
        launchCoordinator = LaunchCoordinator(
            catalog, snapshots,
            object : LaunchDispatcher {
                override suspend fun dispatch(request: LaunchRequest): LaunchAcknowledgement =
                    LaunchAcknowledgement.Dispatched(request.operationId)
            },
            opens, this,
        ),
        refreshState = refresh,
        refreshCatalog = {},
        savedStateHandle = savedStateHandle,
        computationDispatcher = computationDispatcher,
    )
}

private class HomeCatalog(
    items: List<LibraryItem>,
    observeGate: CompletableDeferred<Unit>? = null,
) : CatalogRepository {
    private val current = MutableStateFlow(CatalogSnapshot(items, InventoryStatus.NotRequested))
    override val snapshot: Flow<CatalogSnapshot> =
        observeGate?.let { gate -> flow { gate.await(); emitAll(current) } } ?: current
    fun replaceItems(items: List<LibraryItem>) {
        current.value = current.value.copy(items = items)
    }
    override suspend fun findItem(id: ItemId): LibraryItem? = current.value.items.firstOrNull { it.id == id }
    override suspend fun applyInventory(inventory: CatalogInventory): CatalogReconciliation = error("Not used")
}

private class HomeOpens : SuccessfulOpenRepository {
    override val records = MutableStateFlow<List<SuccessfulOpenRecord>>(emptyList())
    val candidates = mutableListOf<SuccessfulOpenCandidate>()
    override suspend fun recordOnce(candidate: SuccessfulOpenCandidate): SuccessfulOpenWriteResult {
        candidates += candidate
        val record = SuccessfulOpenRecord(candidate.itemId, candidates.size.toLong())
        records.value = listOf(record)
        return SuccessfulOpenWriteResult.Recorded(record)
    }
}

private class HomeSnapshots(
    initial: DestinationSnapshot?,
    private val observeGate: CompletableDeferred<Unit>? = null,
) : NavigationSnapshotRepository {
    private val current = MutableStateFlow(initial)
    val latest: DestinationSnapshot? get() = current.value
    override fun observe(destination: LauncherDestination): Flow<DestinationSnapshot?> =
        observeGate?.let { gate -> flow { gate.await(); emit(current.value) } } ?: current
    override suspend fun save(snapshot: DestinationSnapshot) { current.value = snapshot }
    override suspend fun clear(destination: LauncherDestination) { current.value = null }
}

private class HomeOverrides : ItemOverrideRepository {
    override val overridesByItemId = MutableStateFlow<Map<ItemId, UserItemOverrides>>(emptyMap())
    override suspend fun setOverrides(itemId: ItemId, overrides: UserItemOverrides?) = Unit
}

private fun homeAndroidItem(suffix: Char): LibraryItem.AndroidApp = LibraryItem.AndroidApp(
    componentId = CurrentUserAndroidComponentId(
        packageName = "example.${suffix.lowercaseChar()}",
        activityClassName = "example.${suffix.lowercaseChar()}.MainActivity",
    ),
    title = suffix.toString(),
    category = LibraryCategory.OTHER,
    availability = Availability.Available,
    supportedActions = setOf(SupportedItemAction.OPEN),
)

private fun homeRomItem(title: String, platformId: String?) = LibraryItem.RomGame(
    id = ItemId("rom:$title"),
    title = title,
    sourceId = CatalogSourceId("source:home-test"),
    availability = Availability.Available,
    supportedActions = setOf(SupportedItemAction.OPEN),
    platformId = platformId,
)

private class ReadCountingList<T>(
    private val delegate: List<T>,
    private val onRead: () -> Unit,
) : AbstractList<T>() {
    var reads = 0
        private set
    override val size: Int get() = delegate.size
    override fun get(index: Int): T {
        reads++
        onRead()
        return delegate[index]
    }
}

/** Shares the test clock while distinguishing computation from Main dispatch. */
private class TrackingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
    var isRunning = false
        private set
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        delegate.dispatch(context) {
            isRunning = true
            try {
                block.run()
            } finally {
                isRunning = false
            }
        }
    }
}
