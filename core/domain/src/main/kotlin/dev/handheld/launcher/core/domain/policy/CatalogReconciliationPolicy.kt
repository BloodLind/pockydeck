package dev.handheld.launcher.core.domain.policy

import dev.handheld.launcher.core.domain.model.CatalogInventory
import dev.handheld.launcher.core.domain.model.CatalogReconciliation
import dev.handheld.launcher.core.domain.model.CatalogSnapshot
import dev.handheld.launcher.core.domain.model.InventoryStatus
import dev.handheld.launcher.core.domain.model.InventoryScope
import dev.handheld.launcher.core.domain.model.LibraryItem

object CatalogReconciliationPolicy {
    fun reconcile(
        cachedItems: List<LibraryItem>,
        inventory: CatalogInventory,
    ): CatalogReconciliation {
        requireUniqueIds(cachedItems)
        requireUniqueIds(inventory.observedItems)
        require(inventory.observedItems.all { inventory.scope.contains(it) }) {
            "An inventory may contain only items in its declared scope"
        }

        return when (inventory) {
            is CatalogInventory.Complete -> {
                val observedIds = inventory.observedItems.mapTo(mutableSetOf()) { it.id }
                val outsideScope = cachedItems.filterNot { inventory.scope.contains(it) }
                require(outsideScope.none { it.id in observedIds }) {
                    "An inventory item ID cannot collide with an item retained outside its scope"
                }
                val retainedRemovedItems = cachedItems
                    .asSequence()
                    .filter { inventory.scope.contains(it) }
                    .filterNot { it.id in observedIds }
                    .map { it.asRemoved() }
                    .toList()
                val knownItems = outsideScope + inventory.observedItems + retainedRemovedItems
                CatalogReconciliation(
                    snapshot = CatalogSnapshot(
                        items = knownItems.sortedWith(LibraryItemOrdering.titleThenId),
                        inventoryStatus = InventoryStatus.Complete(inventory.scope),
                    ),
                    removedItemIds = cachedItems
                        .asSequence()
                        .filter { inventory.scope.contains(it) }
                        .map(LibraryItem::id)
                        .filterNot(observedIds::contains)
                        .toSet(),
                )
            }

            is CatalogInventory.Incomplete -> {
                CatalogReconciliation(
                    snapshot = CatalogSnapshot(
                        items = cachedItems,
                        inventoryStatus = InventoryStatus.Incomplete(inventory.scope, inventory.reason),
                    ),
                    removedItemIds = emptySet(),
                )
            }
        }
    }

    private fun requireUniqueIds(items: List<LibraryItem>) {
        require(items.map { it.id }.distinct().size == items.size) {
            "An inventory cannot contain duplicate item IDs"
        }
    }

    private fun InventoryScope.contains(item: LibraryItem): Boolean = when (this) {
        InventoryScope.CurrentUserAndroid ->
            item.provenance == dev.handheld.launcher.core.domain.model.CatalogProvenance.AndroidDiscovery

        is InventoryScope.UserSource ->
            item.provenance == dev.handheld.launcher.core.domain.model.CatalogProvenance.UserSource(sourceId)
    }

    private fun LibraryItem.asRemoved(): LibraryItem = when (this) {
        is LibraryItem.AndroidApp -> copy(
            availability = dev.handheld.launcher.core.domain.model.Availability.Unavailable(
                dev.handheld.launcher.core.domain.model.UnavailabilityReason.REMOVED,
            ),
        )

        is LibraryItem.RomGame -> copy(
            availability = dev.handheld.launcher.core.domain.model.Availability.Unavailable(
                dev.handheld.launcher.core.domain.model.UnavailabilityReason.REMOVED,
            ),
        )

        is LibraryItem.SystemAction -> copy(
            availability = dev.handheld.launcher.core.domain.model.Availability.Unavailable(
                dev.handheld.launcher.core.domain.model.UnavailabilityReason.REMOVED,
            ),
        )
    }
}
