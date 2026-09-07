package dev.handheld.launcher.core.domain.repository

import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.CatalogInventory
import dev.handheld.launcher.core.domain.model.ContractFixtures
import dev.handheld.launcher.core.domain.model.IncompleteInventoryReason
import dev.handheld.launcher.core.domain.model.InventoryScope
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LibraryCategory
import dev.handheld.launcher.core.domain.model.LaunchOperationId
import dev.handheld.launcher.core.domain.model.SuccessfulOpenCandidate
import dev.handheld.launcher.core.domain.model.UnavailabilityReason
import dev.handheld.launcher.core.domain.model.UserArtworkReference
import dev.handheld.launcher.core.domain.model.UserItemOverrides
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class InventoryPreservationContractTest {
    @Test
    fun incompletePayloadCannotReplaceCacheOrUserOwnedReferences() = runBlocking {
        val alpha = ContractFixtures.androidApp("example.alpha", "example.alpha.Main", "Alpha")
        val beta = ContractFixtures.androidApp("example.beta", "example.beta.Main", "Beta")
        val rom = ContractFixtures.rom("rom:one", "ROM one")
        val system = ContractFixtures.systemAction("settings", "Settings")
        val initialCatalog = listOf(system, beta, rom, alpha)
        val catalog = InMemoryCatalogRepository(initialCatalog)
        val favorites = InMemoryFavoriteRepository(setOf(beta.id, rom.id))
        val overrides = InMemoryItemOverrideRepository(
            mapOf(
                beta.id to UserItemOverrides(
                    category = LibraryCategory.GAME,
                    artworkReference = UserArtworkReference("user-art:beta"),
                ),
            ),
        )
        val history = InMemorySuccessfulOpenRepository()
        history.recordOnce(SuccessfulOpenCandidate(LaunchOperationId("opened-beta"), beta.id))

        val conflictingAlpha = alpha.copy(
            title = "Conflicting partial title",
            availability = Availability.Unavailable(UnavailabilityReason.UNKNOWN),
        )
        val uncommittedNewItem = ContractFixtures.androidApp(
            "example.new",
            "example.new.Main",
            "Uncommitted",
        )
        catalog.applyInventory(
            CatalogInventory.Incomplete(
                scope = InventoryScope.CurrentUserAndroid,
                observedItems = listOf(conflictingAlpha, uncommittedNewItem),
                reason = IncompleteInventoryReason.PARTIAL,
            ),
        )

        assertEquals(alpha, catalog.findItem(alpha.id))
        assertEquals(beta, catalog.findItem(beta.id))
        assertEquals(null, catalog.findItem(uncommittedNewItem.id))
        assertEquals(setOf(beta.id, rom.id), favorites.current())
        assertTrue(beta.id in overrides.current())
        assertEquals(listOf(beta.id), history.current().map { it.itemId })
        assertEquals(initialCatalog.map { it.id }, catalog.current().items.map { it.id })

        catalog.applyInventory(
            CatalogInventory.Incomplete(
                scope = InventoryScope.CurrentUserAndroid,
                observedItems = emptyList(),
                reason = IncompleteInventoryReason.FAILED,
            ),
        )
        assertEquals(4, catalog.current().items.size)
    }

    @Test
    fun completeAndroidInventoryUpdatesScopeAndRetainsOmittedRecordAsRemoved() = runBlocking {
        val alpha = ContractFixtures.androidApp("example.alpha", "example.alpha.Main", "Alpha")
        val beta = ContractFixtures.androidApp("example.beta", "example.beta.Main", "Beta")
        val rom = ContractFixtures.rom("rom:one", "ROM one")
        val system = ContractFixtures.systemAction("settings", "Settings")
        val catalog = InMemoryCatalogRepository(listOf(alpha, beta, rom, system))
        val favorites = InMemoryFavoriteRepository(setOf(beta.id))
        val history = InMemorySuccessfulOpenRepository()
        history.recordOnce(SuccessfulOpenCandidate(LaunchOperationId("opened-beta"), beta.id))

        val changedAlpha = alpha.copy(
            availability = Availability.Unavailable(UnavailabilityReason.UNKNOWN),
        )
        val result = catalog.applyInventory(
            CatalogInventory.Complete(
                scope = InventoryScope.CurrentUserAndroid,
                observedItems = listOf(changedAlpha),
            ),
        )

        assertEquals(changedAlpha, catalog.findItem(alpha.id))
        assertTrue(beta.id in result.removedItemIds)
        assertEquals(
            Availability.Unavailable(UnavailabilityReason.REMOVED),
            catalog.findItem(beta.id)?.availability,
        )
        assertFalse(beta.id in result.snapshot.activeItems.map { it.id })
        assertEquals(rom, catalog.findItem(rom.id))
        assertEquals(system, catalog.findItem(system.id))
        assertEquals(setOf(beta.id), favorites.current())
        assertEquals(listOf(beta.id), history.current().map { it.itemId })
    }

    @Test
    fun activeLaunchListExcludesEveryUnavailableReason() {
        val available = ContractFixtures.androidApp("example.ok", "example.ok.Main", "Available")
        val sourceUnavailable = ContractFixtures.androidApp(
            "example.source",
            "example.source.Main",
            "Source unavailable",
            Availability.Unavailable(UnavailabilityReason.SOURCE_UNAVAILABLE),
        )
        val unsupported = ContractFixtures.androidApp(
            "example.unsupported",
            "example.unsupported.Main",
            "Unsupported",
            Availability.Unavailable(UnavailabilityReason.UNSUPPORTED),
        )
        val removed = ContractFixtures.androidApp(
            "example.removed",
            "example.removed.Main",
            "Removed",
            Availability.Unavailable(UnavailabilityReason.REMOVED),
        )

        val snapshot = InMemoryCatalogRepository(
            listOf(sourceUnavailable, available, unsupported, removed),
        ).current()

        assertEquals(listOf(available.id), snapshot.activeItems.map { it.id })
        assertEquals(4, snapshot.items.size)
    }

    @Test
    fun removingFavoriteDoesNotDeleteItsCatalogRecord() = runBlocking {
        val item = ContractFixtures.androidApp("example.keep", "example.keep.Main", "Keep")
        val catalog = InMemoryCatalogRepository(listOf(item))
        val favorites = InMemoryFavoriteRepository(setOf(item.id))

        favorites.setFavorite(item.id, favorite = false)

        assertTrue(favorites.current().isEmpty())
        assertEquals(item, catalog.findItem(item.id))
    }

    @Test
    fun completeInventoryRejectsAnIdCollisionWithAnotherScope() {
        val android = ContractFixtures.androidApp("example.same", "example.same.Main", "Android")
        val collidingRom = ContractFixtures.rom(android.id.value, "ROM collision")

        assertThrows(IllegalArgumentException::class.java) {
            dev.handheld.launcher.core.domain.policy.CatalogReconciliationPolicy.reconcile(
                cachedItems = listOf(collidingRom),
                inventory = CatalogInventory.Complete(
                    scope = InventoryScope.CurrentUserAndroid,
                    observedItems = listOf(android),
                ),
            )
        }
    }
}
