package dev.handheld.launcher.core.data.discovery

import dev.handheld.launcher.core.data.android.apps.AndroidPackageChangeMonitor
import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.CatalogInventory
import dev.handheld.launcher.core.domain.model.CatalogReconciliation
import dev.handheld.launcher.core.domain.model.CatalogSnapshot
import dev.handheld.launcher.core.domain.model.CurrentUserAndroidComponentId
import dev.handheld.launcher.core.domain.model.IncompleteInventoryReason
import dev.handheld.launcher.core.domain.model.InventoryScope
import dev.handheld.launcher.core.domain.model.InventoryStatus
import dev.handheld.launcher.core.domain.model.LibraryCategory
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.SupportedItemAction
import dev.handheld.launcher.core.domain.policy.CatalogReconciliationPolicy
import dev.handheld.launcher.core.domain.repository.CatalogRepository
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidCatalogRefreshCoordinatorTest {
    @Test
    fun `startup reads persisted snapshot before discovery`() = runHarness { scope ->
        val cacheRead = CompletableDeferred<Unit>()
        val discoveryStarted = CompletableDeferred<Unit>()
        val repository = object : CatalogRepository {
            override val snapshot: Flow<CatalogSnapshot> = flow {
                cacheRead.complete(Unit)
                emit(CatalogSnapshot(emptyList(), InventoryStatus.NotRequested))
            }

            override suspend fun findItem(id: dev.handheld.launcher.core.domain.model.ItemId) = null

            override suspend fun applyInventory(inventory: CatalogInventory) =
                CatalogReconciliationPolicy.reconcile(emptyList(), inventory)
        }
        val coordinator = AndroidCatalogRefreshCoordinator(
            repository,
            AndroidAppDiscovery {
                assertTrue(cacheRead.isCompleted)
                discoveryStarted.complete(Unit)
                complete()
            },
            FakePackageChangeMonitor(),
            scope,
        )

        coordinator.start()
        withTimeout(TIMEOUT_MS) { discoveryStarted.await() }
        coordinator.awaitReady()
    }

    @Test
    fun `cached catalog is observable while a slow startup scan is refreshing`() = runHarness { scope ->
        val cached = androidItem("cached", "Cached")
        val repository = RecordingCatalogRepository(listOf(cached))
        val scanStarted = CompletableDeferred<Unit>()
        val finishScan = CompletableDeferred<Unit>()
        val coordinator = coordinator(
            repository = repository,
            scope = scope,
            discovery = AndroidAppDiscovery {
                scanStarted.complete(Unit)
                finishScan.await()
                complete(cached)
            },
        )

        coordinator.start()
        withTimeout(TIMEOUT_MS) { scanStarted.await() }

        val visible = withTimeout(TIMEOUT_MS) {
            coordinator.state.first {
                it.refreshState is AndroidCatalogRefreshState.Refreshing
            }
        }
        assertEquals(listOf(cached), visible.snapshot.items)
        assertEquals(Availability.Available, visible.snapshot.activeItems.single().availability)

        finishScan.complete(Unit)
        coordinator.awaitReady()
    }

    @Test
    fun `failed and partial scans retain cache and publish explicit errors`() = runHarness { scope ->
        val cached = androidItem("cached", "Cached")
        val partialObservation = androidItem("partial", "Must not commit")
        val repository = RecordingCatalogRepository(listOf(cached))
        val scans = ArrayDeque<CatalogInventory>().apply {
            addLast(incomplete(IncompleteInventoryReason.FAILED))
            addLast(
                CatalogInventory.Incomplete(
                    InventoryScope.CurrentUserAndroid,
                    listOf(partialObservation),
                    IncompleteInventoryReason.PARTIAL,
                ),
            )
        }
        val coordinator = coordinator(
            repository,
            scope,
            AndroidAppDiscovery { scans.removeFirst() },
        )

        coordinator.start()
        coordinator.awaitError(IncompleteInventoryReason.FAILED)
        assertEquals(listOf(cached), repository.currentSnapshot.value.items)
        assertFalse(repository.currentSnapshot.value.items.contains(partialObservation))

        coordinator.refresh()
        repository.awaitApplications(2)
        coordinator.awaitError(IncompleteInventoryReason.PARTIAL)
        assertEquals(listOf(cached), repository.currentSnapshot.value.items)
        assertEquals(
            InventoryStatus.Incomplete(
                InventoryScope.CurrentUserAndroid,
                IncompleteInventoryReason.PARTIAL,
            ),
            repository.currentSnapshot.value.inventoryStatus,
        )
    }

    @Test
    fun `cancelled scan records cancellation without clearing cache and worker continues`() = runHarness { scope ->
        val cached = androidItem("cached", "Cached")
        val repository = RecordingCatalogRepository(listOf(cached))
        val calls = AtomicInteger()
        val coordinator = coordinator(
            repository,
            scope,
            AndroidAppDiscovery {
                calls.incrementAndGet()
                throw CancellationException("provider cancelled")
            },
        )

        coordinator.start()
        coordinator.awaitError(IncompleteInventoryReason.CANCELLED)
        assertEquals(listOf(cached), repository.currentSnapshot.value.items)

        coordinator.refresh()
        repository.awaitApplications(2)
        assertEquals(2, calls.get())
        assertEquals(listOf(cached), repository.currentSnapshot.value.items)
    }

    @Test
    fun `overlapping triggers serialize and coalesce with startup resume suppression`() = runHarness { scope ->
        val repository = RecordingCatalogRepository()
        val monitor = FakePackageChangeMonitor()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val active = AtomicInteger()
        val maximumActive = AtomicInteger()
        val calls = AtomicInteger()
        val discovery = AndroidAppDiscovery {
            val call = calls.incrementAndGet()
            val nowActive = active.incrementAndGet()
            maximumActive.updateAndGet { current -> maxOf(current, nowActive) }
            try {
                if (call == 1) {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                } else {
                    secondStarted.complete(Unit)
                }
                complete(androidItem("app", "App"))
            } finally {
                active.decrementAndGet()
            }
        }
        val coordinator = AndroidCatalogRefreshCoordinator(repository, discovery, monitor, scope)

        coordinator.start()
        coordinator.onResume()
        withTimeout(TIMEOUT_MS) { firstStarted.await() }
        repeat(20) { monitor.emit() }
        repeat(20) { coordinator.refresh() }
        releaseFirst.complete(Unit)
        withTimeout(TIMEOUT_MS) { secondStarted.await() }
        repository.awaitApplications(2)

        assertEquals(2, calls.get())
        assertEquals(1, maximumActive.get())
        assertEquals(1, monitor.startCount)
    }

    @Test
    fun `stop and immediate restart retain one reconciliation owner`() = runHarness { scope ->
        val repository = RecordingCatalogRepository()
        val monitor = FakePackageChangeMonitor()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val active = AtomicInteger()
        val maximumActive = AtomicInteger()
        val calls = AtomicInteger()
        val coordinator = AndroidCatalogRefreshCoordinator(
            repository,
            AndroidAppDiscovery {
                val call = calls.incrementAndGet()
                val nowActive = active.incrementAndGet()
                maximumActive.updateAndGet { maxOf(it, nowActive) }
                try {
                    if (call == 1) {
                        firstStarted.complete(Unit)
                        releaseFirst.await()
                    } else {
                        secondStarted.complete(Unit)
                    }
                    complete(androidItem("app", "App"))
                } finally {
                    active.decrementAndGet()
                }
            },
            monitor,
            scope,
        )

        coordinator.start()
        withTimeout(TIMEOUT_MS) { firstStarted.await() }
        coordinator.stop()
        coordinator.start()
        monitor.emit()
        releaseFirst.complete(Unit)
        withTimeout(TIMEOUT_MS) { secondStarted.await() }
        repository.awaitApplications(2)

        assertEquals(1, maximumActive.get())
        assertEquals(2, monitor.startCount)
        assertEquals(1, monitor.stopCount)
    }

    @Test
    fun `resume after inactive missed change is queued behind a slow startup scan`() = runHarness { scope ->
        val original = androidItem("original", "Original")
        val installedWhileInactive = androidItem("installed", "Installed while inactive")
        val repository = RecordingCatalogRepository()
        val monitor = FakePackageChangeMonitor()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val coordinator = AndroidCatalogRefreshCoordinator(
            repository,
            AndroidAppDiscovery {
                when (calls.incrementAndGet()) {
                    1 -> {
                        firstStarted.complete(Unit)
                        releaseFirst.await()
                        complete(original)
                    }

                    else -> complete(original, installedWhileInactive)
                }
            },
            monitor,
            scope,
        )

        coordinator.start()
        withTimeout(TIMEOUT_MS) { firstStarted.await() }
        coordinator.stop()
        monitor.emit()
        coordinator.start()
        coordinator.onResume()
        releaseFirst.complete(Unit)
        repository.awaitApplications(2)

        assertEquals(2, calls.get())
        assertEquals(
            setOf(original.id, installedWhileInactive.id),
            repository.currentSnapshot.value.activeItems.map { it.id }.toSet(),
        )
    }

    @Test
    fun `complete install update removal and resume scans converge by stable identity`() = runHarness { scope ->
        val originalA = androidItem("a", "A")
        val updatedA = androidItem("a", "A updated")
        val installedB = androidItem("b", "B")
        val repository = RecordingCatalogRepository()
        val monitor = FakePackageChangeMonitor()
        val scans = ArrayDeque<CatalogInventory>().apply {
            addLast(complete(originalA))
            addLast(complete(updatedA, installedB))
            addLast(complete(installedB))
            addLast(complete(updatedA, installedB))
        }
        val coordinator = AndroidCatalogRefreshCoordinator(
            repository,
            AndroidAppDiscovery { scans.removeFirst() },
            monitor,
            scope,
        )

        coordinator.start()
        repository.awaitApplications(1)
        assertEquals(listOf(originalA.id), repository.currentSnapshot.value.activeItems.map { it.id })

        monitor.emit()
        repository.awaitApplications(2)
        assertEquals(setOf(originalA.id, installedB.id), repository.currentSnapshot.value.activeItems.map { it.id }.toSet())
        assertEquals(originalA.id, repository.currentSnapshot.value.items.first { it.id == originalA.id }.id)
        assertEquals("A updated", repository.currentSnapshot.value.items.first { it.id == originalA.id }.title)

        monitor.emit()
        repository.awaitApplications(3)
        assertEquals(listOf(installedB.id), repository.currentSnapshot.value.activeItems.map { it.id })
        assertTrue(repository.currentSnapshot.value.items.first { it.id == originalA.id }.availability is Availability.Unavailable)

        coordinator.stop()
        monitor.emit()
        assertEquals(3, repository.applicationCount.value)
        coordinator.start()
        coordinator.onResume()
        repository.awaitApplications(4)
        assertEquals(setOf(originalA.id, installedB.id), repository.currentSnapshot.value.activeItems.map { it.id }.toSet())
        assertEquals("A updated", repository.currentSnapshot.value.items.first { it.id == originalA.id }.title)
    }

    private fun coordinator(
        repository: RecordingCatalogRepository,
        scope: CoroutineScope,
        discovery: AndroidAppDiscovery,
    ) = AndroidCatalogRefreshCoordinator(repository, discovery, FakePackageChangeMonitor(), scope)

    private suspend fun AndroidCatalogRefreshCoordinator.awaitReady() = withTimeout(TIMEOUT_MS) {
        refreshState.filter { it is AndroidCatalogRefreshState.Ready }.first()
    }

    private suspend fun AndroidCatalogRefreshCoordinator.awaitError(reason: IncompleteInventoryReason) =
        withTimeout(TIMEOUT_MS) {
            refreshState.filter {
                it is AndroidCatalogRefreshState.Error && it.reason == reason
            }.first()
        }

    private fun complete(vararg items: LibraryItem.AndroidApp) = CatalogInventory.Complete(
        InventoryScope.CurrentUserAndroid,
        items.toList(),
    )

    private fun incomplete(reason: IncompleteInventoryReason) = CatalogInventory.Incomplete(
        InventoryScope.CurrentUserAndroid,
        emptyList(),
        reason,
    )

    private fun androidItem(key: String, title: String) = LibraryItem.AndroidApp(
        componentId = CurrentUserAndroidComponentId("dev.fixture.$key", "dev.fixture.$key.Main"),
        title = title,
        category = LibraryCategory.OTHER,
        availability = Availability.Available,
        supportedActions = setOf(SupportedItemAction.OPEN),
    )

    private fun runHarness(block: suspend (CoroutineScope) -> Unit) {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            val scope = CoroutineScope(SupervisorJob() + dispatcher)
            try {
                runBlocking { block(scope) }
            } finally {
                scope.cancel()
            }
        }
    }

    private class FakePackageChangeMonitor : AndroidPackageChangeMonitor {
        var startCount = 0
            private set
        var stopCount = 0
            private set
        private var callback: (() -> Unit)? = null

        override fun start(onPackageChanged: () -> Unit) {
            startCount += 1
            callback = onPackageChanged
        }

        override fun stop() {
            stopCount += 1
            callback = null
        }

        fun emit() {
            callback?.invoke()
        }
    }

    private class RecordingCatalogRepository(
        initialItems: List<LibraryItem> = emptyList(),
    ) : CatalogRepository {
        val currentSnapshot = MutableStateFlow(CatalogSnapshot(initialItems, InventoryStatus.NotRequested))
        override val snapshot: Flow<CatalogSnapshot> = currentSnapshot
        val applicationCount = MutableStateFlow(0)

        override suspend fun findItem(id: dev.handheld.launcher.core.domain.model.ItemId): LibraryItem? =
            currentSnapshot.value.items.firstOrNull { it.id == id }

        override suspend fun applyInventory(inventory: CatalogInventory): CatalogReconciliation {
            val reconciliation = CatalogReconciliationPolicy.reconcile(currentSnapshot.value.items, inventory)
            currentSnapshot.value = reconciliation.snapshot
            applicationCount.value += 1
            return reconciliation
        }

        suspend fun awaitApplications(count: Int) = withTimeout(TIMEOUT_MS) {
            applicationCount.filter { it >= count }.first()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
