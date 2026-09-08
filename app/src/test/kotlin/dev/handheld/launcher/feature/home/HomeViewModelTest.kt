package dev.handheld.launcher.feature.home

import androidx.lifecycle.SavedStateHandle
import dev.handheld.launcher.core.data.discovery.AndroidCatalogRefreshState
import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.CatalogInventory
import dev.handheld.launcher.core.domain.model.CatalogReconciliation
import dev.handheld.launcher.core.domain.model.CatalogSnapshot
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
            MutableStateFlow(AndroidCatalogRefreshState.Idle),{},SavedStateHandle())
        advanceUntilIdle()
        assertTrue(viewModel.activate(items[1].id))
        advanceUntilIdle()
        assertEquals(items.map { it.id },viewModel.state.value.items.map { it.itemId })
        assertTrue(opens.candidates.isEmpty())
        assertEquals(null,viewModel.state.value.notice)
        assertEquals(null,viewModel.state.value.pendingLaunchItemId)
    }

    @Test
    fun `cached home keeps all items and successful open preserves selected identity at front`() =
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
            )
            advanceUntilIdle()

            assertEquals(items.size, viewModel.state.value.items.size)
            assertEquals(items[1].id, viewModel.state.value.selectedItemId)
            assertTrue(viewModel.activate(items[1].id))
            advanceUntilIdle()

            assertEquals(items[1].id, viewModel.state.value.items.first().itemId)
            assertEquals(items[1].id, viewModel.state.value.selectedItemId)
            assertEquals(1, opens.candidates.size)
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
            )
            runCurrent()

            viewModel.rememberViewport(items[0].id, 0)
            releaseSnapshot.complete(Unit)
            advanceUntilIdle()

            assertEquals(items[1].id, viewModel.state.value.selectedItemId)
            assertEquals(items[1].id, viewModel.state.value.firstVisibleItemId)
            assertEquals(19, viewModel.state.value.firstVisibleOffsetPx)
        }
}

private class HomeCatalog(items: List<LibraryItem>) : CatalogRepository {
    private val current = MutableStateFlow(CatalogSnapshot(items, InventoryStatus.NotRequested))
    override val snapshot: Flow<CatalogSnapshot> = current
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
