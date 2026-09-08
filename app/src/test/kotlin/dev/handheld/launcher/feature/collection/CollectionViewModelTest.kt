package dev.handheld.launcher.feature.collection

import androidx.lifecycle.SavedStateHandle
import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.CatalogInventory
import dev.handheld.launcher.core.domain.model.CatalogReconciliation
import dev.handheld.launcher.core.domain.model.CatalogSnapshot
import dev.handheld.launcher.core.domain.model.CatalogSourceId
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
import dev.handheld.launcher.core.domain.model.UnavailabilityReason
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
            val items = collectionItems().map { (it as LibraryItem.AndroidApp).copy(category = LibraryCategory.GAME) }
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
        val items = collectionItems().map { (it as LibraryItem.AndroidApp).copy(category = LibraryCategory.GAME) }
        val snapshots = DelayedCollectionSnapshots(
            DestinationSnapshot(
                LauncherDestination.LIBRARY,
                selectedItemId = items[0].id,
                query = "A",
                filterKey = PageStateKey("all"),
            ),
        )
        val viewModel = collectionViewModel(items, snapshots)
        runCurrent()

        viewModel.query("B")
        viewModel.filter("android")
        snapshots.release()
        advanceUntilIdle()

        assertEquals("B", viewModel.state.value.query)
        assertEquals("android", viewModel.state.value.filter)
        assertEquals(listOf(items[1].id), viewModel.state.value.items.map { it.id })
    }

    @Test
    fun `unidentified games stay out of console filters and game grids until assigned`() = runTest(dispatcher) {
        val gba = collectionRom("gba-game", "gba")
        val ambiguous = collectionRom("unassigned-game", null)
        val removed = collectionRom("removed-psx", "psx").copy(
            availability = Availability.Unavailable(UnavailabilityReason.SOURCE_UNAVAILABLE),
        )
        val items = collectionItems() + listOf(gba, ambiguous, removed)
        val snapshots = DelayedCollectionSnapshots(DestinationSnapshot(LauncherDestination.LIBRARY)).apply { release() }
        val viewModel = collectionViewModel(items, snapshots)
        advanceUntilIdle()

        assertEquals(listOf("console:gba"), detectedConsoleFilterKeys(items))
        assertFalse(collectionFilterKeys(LauncherDestination.APPS, items).any { it.startsWith("console:") })
        viewModel.filter("console:gba")
        advanceUntilIdle()
        assertEquals(listOf(gba.id), viewModel.state.value.items.map { it.id })

        viewModel.filter("console:unassigned")
        advanceUntilIdle()
        assertFalse(viewModel.state.value.items.any { it.id == ambiguous.id })
        assertEquals("all", viewModel.state.value.snapshot().filterKey?.value)
    }

    @Test
    fun `console loss falls back to all without retaining an empty unavailable filter`() = runTest(dispatcher) {
        val game = collectionRom("gba-game", "gba")
        val items = collectionItems() + game
        val catalog = CollectionCatalog(items)
        val snapshots = DelayedCollectionSnapshots(DestinationSnapshot(LauncherDestination.LIBRARY)).apply { release() }
        val viewModel = collectionViewModel(items, snapshots, catalog)
        advanceUntilIdle()
        viewModel.filter("console:gba")
        advanceUntilIdle()

        catalog.replaceItems(collectionItems() + game.copy(
            availability = Availability.Unavailable(UnavailabilityReason.SOURCE_UNAVAILABLE),
        ))
        advanceUntilIdle()

        assertEquals("all", viewModel.state.value.filter)
        assertEquals(collectionItems().filter { it.category == LibraryCategory.GAME }.map { it.id }, viewModel.state.value.items.map { it.id })
        assertFalse(collectionFilterKeys(LauncherDestination.LIBRARY, viewModel.state.value.allItems).contains("console:gba"))
    }

    @Test
    fun `console display names can be searched without changing game titles`() = runTest(dispatcher) {
        val game = collectionRom("n64-game", "n64")
        val items = collectionItems() + listOf(game, collectionRom("gba-game", "gba"))
        val snapshots = DelayedCollectionSnapshots(DestinationSnapshot(LauncherDestination.LIBRARY)).apply { release() }
        val viewModel = collectionViewModel(items, snapshots)
        advanceUntilIdle()
        viewModel.query("Nintendo 64")
        advanceUntilIdle()

        assertEquals(listOf(game.id), viewModel.state.value.items.map { it.id })
        assertEquals("n64-game", viewModel.state.value.items.single().title)
    }

    @Test
    fun `Library and Apps partition Android categories while retaining every ROM in Library`() = runTest(dispatcher) {
        val game = collectionItem("game", "Game", LibraryCategory.GAME)
        val emulator = collectionItem("emulator", "Emulator", LibraryCategory.EMULATOR)
        val app = collectionItem("app", "App", LibraryCategory.OTHER)
        val rom = collectionRom("rom", "gba")
        val items = listOf(game, emulator, app, rom)
        val overrides = CollectionOverrides(mapOf(rom.id to UserItemOverrides(category = LibraryCategory.OTHER)))
        val library = collectionViewModel(items, releasedSnapshots(), overrides = overrides)
        val apps = collectionViewModel(items, releasedSnapshots(LauncherDestination.APPS),
            destination = LauncherDestination.APPS, overrides = overrides)
        advanceUntilIdle()

        assertEquals(setOf(game.id, rom.id), library.state.value.items.map { it.id }.toSet())
        assertEquals(setOf(emulator.id, app.id), apps.state.value.items.map { it.id }.toSet())
        assertEquals(items, library.state.value.allItems) // Cross-page detail lookup retains the full catalog.
        library.filter("android")
        advanceUntilIdle()
        assertEquals(listOf(game.id), library.state.value.items.map { it.id })
        library.filter("console:gba")
        advanceUntilIdle()
        assertEquals(listOf(rom.id), library.state.value.items.map { it.id })
    }

    @Test
    fun `Android category overrides move items between destinations immediately`() = runTest(dispatcher) {
        val game = collectionItem("game", "Game", LibraryCategory.GAME)
        val app = collectionItem("app", "App", LibraryCategory.OTHER)
        val items = listOf(game, app)
        val overrides = CollectionOverrides()
        val library = collectionViewModel(items, releasedSnapshots(), overrides = overrides)
        val apps = collectionViewModel(items, releasedSnapshots(LauncherDestination.APPS),
            destination = LauncherDestination.APPS, overrides = overrides)
        advanceUntilIdle()
        assertEquals(listOf(game.id), library.state.value.items.map { it.id })
        assertEquals(listOf(app.id), apps.state.value.items.map { it.id })

        overrides.setOverrides(game.id, UserItemOverrides(category = LibraryCategory.EMULATOR))
        overrides.setOverrides(app.id, UserItemOverrides(category = LibraryCategory.GAME))
        advanceUntilIdle()
        assertEquals(listOf(app.id), library.state.value.items.map { it.id })
        assertEquals(listOf(game.id), apps.state.value.items.map { it.id })
    }

    @Test
    fun `Favorites includes every favorite app and console but excludes unavailable and nonfavorite records`() = runTest(dispatcher) {
        val game = collectionItem("game", "Game", LibraryCategory.GAME)
        val app = collectionItem("app", "App", LibraryCategory.OTHER)
        val emulator = collectionItem("emulator", "Emulator", LibraryCategory.EMULATOR)
        val gba = collectionRom("gba", "gba")
        val psx = collectionRom("psx", "psx")
        val unavailable = collectionRom("unavailable", "nes").copy(
            availability = Availability.Unavailable(UnavailabilityReason.SOURCE_UNAVAILABLE),
        )
        val nonfavorite = collectionRom("nonfavorite", "gba")
        val favoriteItems = listOf(game, app, emulator, gba, psx, unavailable)
        val favorites = CollectionFavorites(favoriteItems.map { it.id }.toSet())
        val viewModel = collectionViewModel(favoriteItems + nonfavorite, releasedSnapshots(LauncherDestination.FAVORITES),
            destination = LauncherDestination.FAVORITES, favorites = favorites)
        advanceUntilIdle()

        assertEquals(setOf(game.id, app.id, emulator.id, gba.id, psx.id), viewModel.state.value.items.map { it.id }.toSet())
        favorites.setFavorite(gba.id, false)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.items.any { it.id == gba.id })
        assertTrue(viewModel.state.value.items.any { it.id == psx.id })
    }

    @Test
    fun `Search stays empty for blank whitespace and unmatched queries without selecting a fallback`() = runTest(dispatcher) {
        val items = collectionItems() + collectionRom("rom", "gba")
        val viewModel = collectionViewModel(items, releasedSnapshots(LauncherDestination.SEARCH),
            destination = LauncherDestination.SEARCH)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.items.isEmpty())
        assertNull(viewModel.state.value.selectedItemId)

        viewModel.query("A")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.items.isNotEmpty())
        listOf("", " \t\n ", "no-such-title-or-platform").forEach { query ->
            viewModel.query(query)
            viewModel.toggleSort()
            advanceUntilIdle()
            assertTrue("Unexpected rows for '$query'", viewModel.state.value.items.isEmpty())
            assertNull(viewModel.state.value.selectedItemId)
        }
    }

    @Test
    fun `real Search query retains apps emulators Android games and ROMs`() = runTest(dispatcher) {
        val items = listOf(
            collectionItem("game", "Shared Game", LibraryCategory.GAME),
            collectionItem("emulator", "Shared Emulator", LibraryCategory.EMULATOR),
            collectionItem("app", "Shared App", LibraryCategory.OTHER),
            collectionRom("Shared ROM", "gba"),
        )
        val viewModel = collectionViewModel(items, releasedSnapshots(LauncherDestination.SEARCH),
            destination = LauncherDestination.SEARCH)
        viewModel.query(" Shared ")
        advanceUntilIdle()
        assertEquals(items.map { it.id }.toSet(), viewModel.state.value.items.map { it.id }.toSet())
    }

    @Test
    fun `large searches publish early results and cancel batches from an obsolete query`() = runTest(dispatcher) {
        val items = (1..800).map { collectionItem("game$it", "Shared game $it", LibraryCategory.GAME) } +
            collectionItem("specific", "Unique result", LibraryCategory.OTHER)
        val viewModel = collectionViewModel(items, releasedSnapshots(LauncherDestination.SEARCH), destination = LauncherDestination.SEARCH)
        advanceUntilIdle()
        viewModel.query("Shared")
        runCurrent()
        assertTrue(viewModel.state.value.items.isNotEmpty())
        assertTrue(viewModel.state.value.items.size < 800)
        assertTrue(viewModel.state.value.searching)
        viewModel.query("Unique")
        runCurrent()
        advanceUntilIdle()
        assertEquals(listOf("Unique result"), viewModel.state.value.items.map { it.title })
        assertFalse(viewModel.state.value.searching)
    }

    @Test
    fun `selection and viewport updates preserve the prepared result list`() = runTest(dispatcher) {
        val items = (1..400).map { collectionItem("game$it", "Game $it", LibraryCategory.GAME) }
        val viewModel = collectionViewModel(items, releasedSnapshots())
        advanceUntilIdle()
        val prepared = viewModel.state.value.items
        repeat(20) { viewModel.select(items[it].id); viewModel.rememberAnchor(items[it].id, it * 3); runCurrent() }
        org.junit.Assert.assertSame(prepared, viewModel.state.value.items)
        assertEquals(items[19].id, viewModel.state.value.selectedItemId)
    }

    @Test
    fun `Search matches stored platform IDs short captions and full console names`() = runTest(dispatcher) {
        val game = collectionRom("A racing game", "gamecube")
        val items = listOf(game, collectionRom("A different game", "psx"))
        val viewModel = collectionViewModel(items, releasedSnapshots(LauncherDestination.SEARCH),
            destination = LauncherDestination.SEARCH)
        listOf("gamecube", " GCN ", "nInTeNdO gAmEcUbE").forEach { query ->
            viewModel.query(query)
            advanceUntilIdle()
            assertEquals(query, listOf(game.id), viewModel.state.value.items.map { it.id })
        }
        assertEquals("A racing game", viewModel.state.value.items.single().title)
    }

    @Test
    fun `stale category filters cannot put Library and Apps into irrelevant categories`() = runTest(dispatcher) {
        val items = collectionItems() + collectionItem("emulator", "Emulator", LibraryCategory.EMULATOR)
        listOf(LauncherDestination.LIBRARY to "emulators", LauncherDestination.LIBRARY to "games",
            LauncherDestination.APPS to "games").forEach { (destination, filter) ->
            val snapshots = DelayedCollectionSnapshots(DestinationSnapshot(destination, filterKey = PageStateKey(filter))).apply { release() }
            val viewModel = collectionViewModel(items, snapshots, destination = destination)
            advanceUntilIdle()
            assertEquals("all", viewModel.state.value.filter)
            val expected = items.filter {
                (it.category == LibraryCategory.GAME) == (destination == LauncherDestination.LIBRARY)
            }.map { it.id }.toSet()
            assertEquals(expected, viewModel.state.value.items.map { it.id }.toSet())
        }
    }
}

private fun TestScope.collectionViewModel(
    items: List<LibraryItem>,
    snapshots: NavigationSnapshotRepository,
    catalog: CollectionCatalog = CollectionCatalog(items),
    destination: LauncherDestination = LauncherDestination.LIBRARY,
    favorites: FavoriteRepository = EmptyFavorites,
    overrides: ItemOverrideRepository = EmptyOverrides,
) = CollectionViewModel(
    destination,
    catalog,
    favorites,
    overrides,
    EmptyOpens,
    snapshots,
    SavedStateHandle(),
    computationDispatcher = StandardTestDispatcher(testScheduler),
)

private fun releasedSnapshots(destination: LauncherDestination = LauncherDestination.LIBRARY) =
    DelayedCollectionSnapshots(DestinationSnapshot(destination)).apply { release() }

private class CollectionFavorites(initial: Set<ItemId>) : FavoriteRepository {
    override val favoriteItemIds = MutableStateFlow(initial)
    override suspend fun setFavorite(itemId: ItemId, favorite: Boolean) {
        favoriteItemIds.value = if (favorite) favoriteItemIds.value + itemId else favoriteItemIds.value - itemId
    }
}

private class CollectionOverrides(initial: Map<ItemId, UserItemOverrides> = emptyMap()) : ItemOverrideRepository {
    override val overridesByItemId = MutableStateFlow(initial)
    override suspend fun setOverrides(itemId: ItemId, overrides: UserItemOverrides?) {
        overridesByItemId.value = if (overrides == null) overridesByItemId.value - itemId else overridesByItemId.value + (itemId to overrides)
    }
}

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
    fun replaceItems(items: List<LibraryItem>) { state.value = state.value.copy(items = items) }
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

private fun collectionRom(id: String, platform: String?) = LibraryItem.RomGame(
    id = ItemId("rom:$id"),
    title = id,
    sourceId = CatalogSourceId("rom:source"),
    availability = Availability.Available,
    supportedActions = setOf(SupportedItemAction.OPEN, SupportedItemAction.VIEW_DETAILS, SupportedItemAction.TOGGLE_FAVORITE),
    platformId = platform,
    format = "rom",
)
