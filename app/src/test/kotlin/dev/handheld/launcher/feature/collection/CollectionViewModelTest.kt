package dev.handheld.launcher.feature.collection

import androidx.lifecycle.SavedStateHandle
import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.CatalogInventory
import dev.handheld.launcher.core.domain.model.CatalogReconciliation
import dev.handheld.launcher.core.domain.model.CatalogSnapshot
import dev.handheld.launcher.core.domain.model.CurrentUserAndroidComponentId
import dev.handheld.launcher.core.domain.model.DestinationSnapshot
import dev.handheld.launcher.core.domain.model.InventoryStatus
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LauncherDestination
import dev.handheld.launcher.core.domain.model.LibraryCategory
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.PageStateKey
import dev.handheld.launcher.core.domain.model.SuccessfulOpenCandidate
import dev.handheld.launcher.core.domain.model.SuccessfulOpenRecord
import dev.handheld.launcher.core.domain.model.SuccessfulOpenWriteResult
import dev.handheld.launcher.core.domain.model.SupportedItemAction
import dev.handheld.launcher.core.domain.model.UserItemOverrides
import dev.handheld.launcher.core.domain.repository.CatalogRepository
import dev.handheld.launcher.core.domain.repository.FavoriteRepository
import dev.handheld.launcher.core.domain.repository.ItemOverrideRepository
import dev.handheld.launcher.core.domain.repository.NavigationSnapshotRepository
import dev.handheld.launcher.core.domain.repository.SuccessfulOpenRepository
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `initial selection and viewport callbacks do not suppress delayed snapshot`() =
        runTest(dispatcher) {
            val items = collectionItems()
            val snapshots = DelayedCollectionSnapshots(
                DestinationSnapshot(
                    LauncherDestination.LIBRARY,
                    selectedItemId = items[1].id,
                    firstVisibleItemId = items[1].id,
                    firstVisibleOffsetPx = 23,
                ),
            )
            val viewModel = collectionViewModel(items, snapshots)
            runCurrent()

            viewModel.select(items[0].id)
            viewModel.rememberAnchor(items[0].id, 0)
            snapshots.release()
            advanceUntilIdle()

            assertEquals(items[1].id, viewModel.state.value.selectedItemId)
            assertEquals(items[1].id, viewModel.state.value.firstVisibleItemId)
            assertEquals(23, viewModel.state.value.firstVisibleOffsetPx)
        }

    @Test
    fun `explicit query and filter before delayed snapshot win`() = runTest(dispatcher) {
        val items = collectionItems()
        val snapshots = DelayedCollectionSnapshots(
            DestinationSnapshot(
                LauncherDestination.LIBRARY,
                selectedItemId = items[0].id,
                query = "A",
                filterKey = PageStateKey("games"),
            ),
        )
        val viewModel = collectionViewModel(items, snapshots)
        runCurrent()

        viewModel.query("B")
        viewModel.filter("other")
        snapshots.release()
        advanceUntilIdle()

        assertEquals("B", viewModel.state.value.query)
        assertEquals("other", viewModel.state.value.filter)
        assertEquals(listOf(items[1].id), viewModel.state.value.items.map { it.id })
    }
}

private fun TestScope.collectionViewModel(
    items: List<LibraryItem>,
    snapshots: NavigationSnapshotRepository,
) = CollectionViewModel(
    LauncherDestination.LIBRARY,
    CollectionCatalog(items),
    EmptyFavorites,
    EmptyOverrides,
    EmptyOpens,
    snapshots,
    SavedStateHandle(),
)

private class DelayedCollectionSnapshots(
    private val snapshot: DestinationSnapshot,
) : NavigationSnapshotRepository {
    private val gate = CompletableDeferred<Unit>()
    fun release() { gate.complete(Unit) }
    override fun observe(destination: LauncherDestination): Flow<DestinationSnapshot?> = flow {
        gate.await()
        emit(snapshot)
    }
    override suspend fun save(snapshot: DestinationSnapshot) = Unit
    override suspend fun clear(destination: LauncherDestination) = Unit
}

private class CollectionCatalog(items: List<LibraryItem>) : CatalogRepository {
    private val state = MutableStateFlow(CatalogSnapshot(items, InventoryStatus.NotRequested))
    override val snapshot: Flow<CatalogSnapshot> = state
    override suspend fun findItem(id: ItemId): LibraryItem? = state.value.items.find { it.id == id }
    override suspend fun applyInventory(inventory: CatalogInventory): CatalogReconciliation = error("Not used")
}

private data object EmptyFavorites : FavoriteRepository {
    override val favoriteItemIds = MutableStateFlow<Set<ItemId>>(emptySet())
    override suspend fun setFavorite(itemId: ItemId, favorite: Boolean) = Unit
}

private data object EmptyOverrides : ItemOverrideRepository {
    override val overridesByItemId = MutableStateFlow<Map<ItemId, UserItemOverrides>>(emptyMap())
    override suspend fun setOverrides(itemId: ItemId, overrides: UserItemOverrides?) = Unit
}

private data object EmptyOpens : SuccessfulOpenRepository {
    override val records = MutableStateFlow<List<SuccessfulOpenRecord>>(emptyList())
    override suspend fun recordOnce(candidate: SuccessfulOpenCandidate): SuccessfulOpenWriteResult = error("Not used")
}

private fun collectionItems(): List<LibraryItem> = listOf(
    collectionItem("a", "A", LibraryCategory.GAME),
    collectionItem("b", "B", LibraryCategory.OTHER),
)

private fun collectionItem(
    suffix: String,
    title: String,
    category: LibraryCategory,
): LibraryItem.AndroidApp = LibraryItem.AndroidApp(
    componentId = CurrentUserAndroidComponentId("example.$suffix", "example.$suffix.MainActivity"),
    title = title,
    category = category,
    availability = Availability.Available,
    supportedActions = setOf(SupportedItemAction.OPEN),
)
