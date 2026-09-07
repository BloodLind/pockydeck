package dev.handheld.launcher.launch

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
import dev.handheld.launcher.core.domain.repository.CatalogRepository
import dev.handheld.launcher.core.domain.repository.LaunchDispatcher
import dev.handheld.launcher.core.domain.repository.NavigationSnapshotRepository
import dev.handheld.launcher.core.domain.repository.SuccessfulOpenRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LaunchCoordinatorTest {
    @Test
    fun `persists origin suppresses duplicate and records matching dispatch once`() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val item = androidItem("B")
        val catalog = FakeCatalog(item)
        val snapshots = FakeSnapshots()
        val launchDispatcher = PausingDispatcher()
        val opens = FakeSuccessfulOpens()
        val coordinator = LaunchCoordinator(
            catalog,
            snapshots,
            launchDispatcher,
            opens,
            scope,
            operationIdFactory = { LaunchOperationId("operation-b") },
        )
        val origin = DestinationSnapshot(
            LauncherDestination.HOME,
            selectedItemId = item.id,
            firstVisibleItemId = item.id,
            firstVisibleOffsetPx = 7,
        )

        assertTrue(coordinator.submit(item.id, origin))
        scope.runCurrent()
        assertEquals(origin, snapshots.saved.single())
        assertFalse(coordinator.submit(item.id, origin))
        assertEquals(1, launchDispatcher.calls)

        launchDispatcher.release.complete(Unit)
        scope.advanceUntilIdle()

        assertEquals(listOf(SuccessfulOpenCandidate(LaunchOperationId("operation-b"), item.id)), opens.candidates)
        assertTrue(coordinator.state.value is LaunchCoordinatorState.Succeeded)
    }

    @Test
    fun `mismatched acknowledgement never records recency`() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val item = androidItem("B")
        val opens = FakeSuccessfulOpens()
        val coordinator = LaunchCoordinator(
            FakeCatalog(item),
            FakeSnapshots(),
            object : LaunchDispatcher {
                override suspend fun dispatch(request: LaunchRequest): LaunchAcknowledgement =
                    LaunchAcknowledgement.Dispatched(LaunchOperationId("different-operation"))
            },
            opens,
            scope,
            operationIdFactory = { LaunchOperationId("operation-b") },
        )

        assertTrue(coordinator.submit(item.id, DestinationSnapshot(LauncherDestination.HOME)))
        scope.advanceUntilIdle()

        assertTrue(opens.candidates.isEmpty())
        assertEquals(
            LaunchCoordinatorFailure.INVALID_ACKNOWLEDGEMENT,
            (coordinator.state.value as LaunchCoordinatorState.Failed).reason,
        )
    }
}

private class FakeCatalog(item: LibraryItem) : CatalogRepository {
    private val items = listOf(item)
    override val snapshot: Flow<CatalogSnapshot> = MutableStateFlow(
        CatalogSnapshot(items, InventoryStatus.NotRequested),
    )

    override suspend fun findItem(id: ItemId): LibraryItem? = items.firstOrNull { it.id == id }

    override suspend fun applyInventory(inventory: CatalogInventory): CatalogReconciliation =
        error("Not used")
}

private class FakeSnapshots : NavigationSnapshotRepository {
    val saved = mutableListOf<DestinationSnapshot>()
    private val values = LauncherDestination.entries.associateWith {
        MutableStateFlow<DestinationSnapshot?>(null)
    }

    override fun observe(destination: LauncherDestination): Flow<DestinationSnapshot?> =
        values.getValue(destination)

    override suspend fun save(snapshot: DestinationSnapshot) {
        saved += snapshot
        values.getValue(snapshot.destination).value = snapshot
    }

    override suspend fun clear(destination: LauncherDestination) {
        values.getValue(destination).value = null
    }
}

private class PausingDispatcher : LaunchDispatcher {
    val release = CompletableDeferred<Unit>()
    var calls = 0

    override suspend fun dispatch(request: LaunchRequest): LaunchAcknowledgement {
        calls++
        release.await()
        return LaunchAcknowledgement.Dispatched(request.operationId)
    }
}

private class FakeSuccessfulOpens : SuccessfulOpenRepository {
    override val records = MutableStateFlow<List<SuccessfulOpenRecord>>(emptyList())
    val candidates = mutableListOf<SuccessfulOpenCandidate>()

    override suspend fun recordOnce(candidate: SuccessfulOpenCandidate): SuccessfulOpenWriteResult {
        candidates += candidate
        val record = SuccessfulOpenRecord(candidate.itemId, candidates.size.toLong())
        records.value = listOf(record)
        return SuccessfulOpenWriteResult.Recorded(record)
    }
}

private fun androidItem(suffix: String): LibraryItem.AndroidApp = LibraryItem.AndroidApp(
    componentId = CurrentUserAndroidComponentId("example.$suffix", "example.$suffix.MainActivity"),
    title = suffix,
    category = LibraryCategory.OTHER,
    availability = Availability.Available,
    supportedActions = setOf(SupportedItemAction.OPEN),
)
