package dev.handheld.launcher.core.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.handheld.launcher.core.data.local.LauncherDatabase
import dev.handheld.launcher.core.data.local.SuccessfulOpenReferenceEntity
import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.CatalogInventory
import dev.handheld.launcher.core.domain.model.CatalogSourceId
import dev.handheld.launcher.core.domain.model.CurrentUserAndroidComponentId
import dev.handheld.launcher.core.domain.model.IncompleteInventoryReason
import dev.handheld.launcher.core.domain.model.InventoryScope
import dev.handheld.launcher.core.domain.model.InventoryStatus
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LibraryCategory
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.SupportedItemAction
import dev.handheld.launcher.core.domain.model.UnavailabilityReason
import dev.handheld.launcher.core.domain.model.UserArtworkReference
import dev.handheld.launcher.core.domain.model.UserItemOverrides
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCatalogRepositoryInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: LauncherDatabase
    private lateinit var catalog: RoomCatalogRepository
    private lateinit var favorites: RoomFavoriteRepository
    private lateinit var overrides: RoomItemOverrideRepository
    private lateinit var databaseName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "us017-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun cachedRowsEmitAfterReopenBeforeAnotherInventoryCompletes() = runBlocking {
        val alpha = androidApp("example.alpha", "example.alpha.Main", "Alpha")
        val beta = androidApp("example.beta", "example.beta.Main", "Beta")
        catalog.applyInventory(completeAndroid(alpha, beta))

        database.close()
        openDatabase()

        val inventoryCanComplete = CompletableDeferred<Unit>()
        val inventoryStarted = CompletableDeferred<Unit>()
        val pendingInventory = async(start = CoroutineStart.UNDISPATCHED) {
            inventoryStarted.complete(Unit)
            inventoryCanComplete.await()
            catalog.applyInventory(completeAndroid(alpha))
        }
        inventoryStarted.await()

        val cached = withTimeout(5_000) { catalog.snapshot.first() }
        assertFalse(pendingInventory.isCompleted)
        assertEquals(listOf(alpha.id, beta.id), cached.items.map(LibraryItem::id))
        assertEquals(listOf(alpha.id, beta.id), cached.activeItems.map(LibraryItem::id))
        assertEquals(InventoryStatus.Complete(InventoryScope.CurrentUserAndroid), cached.inventoryStatus)

        inventoryCanComplete.complete(Unit)
        pendingInventory.await()
        Unit
    }

    @Test
    fun samePackageComponentsRoundTripAsDistinctStableItems() = runBlocking {
        val standard = androidApp("example.game", "example.game.StandardActivity", "Game")
        val arcade = androidApp("example.game", "example.game.ArcadeActivity", "Game")

        catalog.applyInventory(completeAndroid(standard, arcade))
        database.close()
        openDatabase()

        val restored = catalog.snapshot.first().items
        assertEquals(2, restored.size)
        assertEquals(setOf(standard.id, arcade.id), restored.mapTo(mutableSetOf(), LibraryItem::id))
        assertEquals(standard, catalog.findItem(standard.id))
        assertEquals(arcade, catalog.findItem(arcade.id))
    }

    @Test
    fun completedInventoryReconcilesItsScopeInOneObservableCommit() = runBlocking {
        val alpha = androidApp("example.alpha", "example.alpha.Main", "Alpha")
        val beta = androidApp("example.beta", "example.beta.Main", "Beta")
        val sourceId = CatalogSourceId("source-one")
        val rom = romGame(ItemId("rom:one"), "ROM one", sourceId)
        catalog.applyInventory(completeAndroid(alpha, beta))
        catalog.applyInventory(CatalogInventory.Complete(InventoryScope.UserSource(sourceId), listOf(rom)))

        val collectorStarted = CompletableDeferred<Unit>()
        val emissions = async(start = CoroutineStart.UNDISPATCHED) {
            catalog.snapshot
                .onEach { collectorStarted.complete(Unit) }
                .take(2)
                .toList()
        }
        collectorStarted.await()

        val renamedAlpha = alpha.copy(title = "Alpha updated")
        val gamma = androidApp("example.gamma", "example.gamma.Main", "Gamma")
        val result = catalog.applyInventory(completeAndroid(renamedAlpha, gamma))
        val observed = withTimeout(5_000) { emissions.await() }

        assertEquals(2, observed.size)
        val committed = observed.last()
        assertEquals(
            setOf(alpha.id, beta.id, gamma.id, rom.id),
            committed.items.mapTo(mutableSetOf(), LibraryItem::id),
        )
        assertEquals(setOf(alpha.id, gamma.id, rom.id), committed.activeItems.mapTo(mutableSetOf()) { it.id })
        assertEquals("Alpha updated", catalog.findItem(alpha.id)?.title)
        assertEquals(
            Availability.Unavailable(UnavailabilityReason.REMOVED),
            catalog.findItem(beta.id)?.availability,
        )
        assertEquals(rom, catalog.findItem(rom.id))
        assertEquals(setOf(beta.id), result.removedItemIds)
    }

    @Test
    fun incompleteInventoriesOnlyUpdateStatusAndPreserveCatalogAndReferences() = runBlocking {
        val alpha = androidApp("example.alpha", "example.alpha.Main", "Alpha")
        val beta = androidApp("example.beta", "example.beta.Main", "Beta")
        catalog.applyInventory(completeAndroid(alpha, beta))
        favorites.setFavorite(beta.id, true)
        val betaOverrides = UserItemOverrides(
            category = LibraryCategory.GAME,
            artworkReference = UserArtworkReference("user-art:beta"),
        )
        overrides.setOverrides(beta.id, betaOverrides)
        database.catalogReferenceDao().upsertHistoryReference(
            SuccessfulOpenReferenceEntity(beta.id.value, openOrder = 7),
        )
        val expectedItems = catalog.snapshot.first().items

        IncompleteInventoryReason.entries.forEach { reason ->
            val conflictingAlpha = alpha.copy(
                title = "Uncommitted $reason",
                availability = Availability.Unavailable(UnavailabilityReason.UNKNOWN),
            )
            val uncommitted = androidApp("example.new", "example.new.Main", "Uncommitted")
            val result = catalog.applyInventory(
                CatalogInventory.Incomplete(
                    scope = InventoryScope.CurrentUserAndroid,
                    observedItems = listOf(conflictingAlpha, uncommitted),
                    reason = reason,
                ),
            )

            assertEquals(expectedItems, result.snapshot.items)
            val stored = catalog.snapshot.first()
            assertEquals(expectedItems, stored.items)
            assertEquals(InventoryStatus.Incomplete(InventoryScope.CurrentUserAndroid, reason), stored.inventoryStatus)
            assertEquals(setOf(beta.id), favorites.favoriteItemIds.first())
            assertEquals(betaOverrides, overrides.overridesByItemId.first()[beta.id])
            assertEquals(
                listOf(SuccessfulOpenReferenceEntity(beta.id.value, 7)),
                database.catalogReferenceDao().readHistoryReferences(),
            )
            assertEquals(null, catalog.findItem(uncommitted.id))
        }
    }

    @Test
    fun updateAndTemporaryRemovalPreserveReferencesAndRediscoveryRecoversIdentity() = runBlocking {
        val alpha = androidApp("example.alpha", "example.alpha.Main", "Alpha")
        val beta = androidApp("example.beta", "example.beta.Main", "Beta")
        catalog.applyInventory(completeAndroid(alpha, beta))
        favorites.setFavorite(beta.id, true)
        val betaOverrides = UserItemOverrides(
            category = LibraryCategory.GAME,
            artworkReference = UserArtworkReference("user-art:beta"),
        )
        overrides.setOverrides(beta.id, betaOverrides)
        database.catalogReferenceDao().upsertHistoryReference(
            SuccessfulOpenReferenceEntity(beta.id.value, openOrder = 11),
        )

        catalog.applyInventory(completeAndroid(alpha.copy(title = "Alpha updated")))

        val unavailableBeta = catalog.findItem(beta.id)
        assertNotNull(unavailableBeta)
        assertEquals(Availability.Unavailable(UnavailabilityReason.REMOVED), unavailableBeta?.availability)
        assertFalse(beta.id in catalog.snapshot.first().activeItems.map(LibraryItem::id))
        assertEquals(setOf(beta.id), favorites.favoriteItemIds.first())
        assertEquals(betaOverrides, overrides.overridesByItemId.first()[beta.id])
        assertEquals(11, database.catalogReferenceDao().readHistoryReferences().single().openOrder)

        val rediscoveredBeta = beta.copy(title = "Beta rediscovered")
        catalog.applyInventory(completeAndroid(alpha, rediscoveredBeta))

        assertEquals(rediscoveredBeta, catalog.findItem(beta.id))
        assertTrue(beta.id in catalog.snapshot.first().activeItems.map(LibraryItem::id))
        assertEquals(setOf(beta.id), favorites.favoriteItemIds.first())
        assertEquals(betaOverrides, overrides.overridesByItemId.first()[beta.id])
        assertEquals(11, database.catalogReferenceDao().readHistoryReferences().single().openOrder)

        favorites.setFavorite(beta.id, false)
        assertTrue(favorites.favoriteItemIds.first().isEmpty())
        assertEquals(rediscoveredBeta, catalog.findItem(beta.id))
    }

    @Test
    fun completeInventoryRejectsOutsideScopeIdentityCollisionWithoutWriting() = runBlocking {
        val sourceId = CatalogSourceId("source-one")
        val collidingId = ItemId("android:example.same/example.same.Main")
        val retainedRom = romGame(collidingId, "Retained ROM", sourceId)
        catalog.applyInventory(
            CatalogInventory.Complete(InventoryScope.UserSource(sourceId), listOf(retainedRom)),
        )
        val android = androidApp("example.same", "example.same.Main", "Android")

        var thrown: IllegalArgumentException? = null
        try {
            catalog.applyInventory(completeAndroid(android))
        } catch (error: IllegalArgumentException) {
            thrown = error
        }

        assertNotNull(thrown)
        assertEquals(listOf(retainedRom), catalog.snapshot.first().items)
        assertEquals(retainedRom, catalog.findItem(collidingId))
    }

    @Test
    fun completedEmptyInventoryMarksMoreThanSqliteBindLimitRemoved() = runBlocking {
        val items = (0 until 1_005).map { index ->
            androidApp(
                packageName = "example.batch$index",
                activityClassName = "example.batch$index.Main",
                title = "Batch $index",
            )
        }
        catalog.applyInventory(
            CatalogInventory.Complete(InventoryScope.CurrentUserAndroid, items),
        )

        val result = catalog.applyInventory(
            CatalogInventory.Complete(InventoryScope.CurrentUserAndroid, emptyList()),
        )
        val stored = catalog.snapshot.first()

        assertEquals(items.mapTo(mutableSetOf(), LibraryItem::id), result.removedItemIds)
        assertEquals(1_005, stored.items.size)
        assertTrue(stored.activeItems.isEmpty())
        assertTrue(
            stored.items.all {
                it.availability == Availability.Unavailable(UnavailabilityReason.REMOVED)
            },
        )
    }

    private fun openDatabase() {
        database = LauncherDatabase.open(context, databaseName)
        catalog = RoomCatalogRepository(database)
        favorites = RoomFavoriteRepository(database)
        overrides = RoomItemOverrideRepository(database)
    }

    private fun completeAndroid(vararg items: LibraryItem.AndroidApp): CatalogInventory.Complete =
        CatalogInventory.Complete(InventoryScope.CurrentUserAndroid, items.toList())

    private fun androidApp(
        packageName: String,
        activityClassName: String,
        title: String,
    ): LibraryItem.AndroidApp = LibraryItem.AndroidApp(
        componentId = CurrentUserAndroidComponentId(packageName, activityClassName),
        title = title,
        category = LibraryCategory.OTHER,
        availability = Availability.Available,
        supportedActions = setOf(
            SupportedItemAction.OPEN,
            SupportedItemAction.VIEW_DETAILS,
            SupportedItemAction.TOGGLE_FAVORITE,
            SupportedItemAction.OPEN_APP_INFO,
        ),
    )

    private fun romGame(
        id: ItemId,
        title: String,
        sourceId: CatalogSourceId,
    ): LibraryItem.RomGame = LibraryItem.RomGame(
        id = id,
        title = title,
        sourceId = sourceId,
        availability = Availability.Available,
        supportedActions = setOf(SupportedItemAction.OPEN),
    )
}
