package dev.handheld.launcher.core.domain.model

enum class IncompleteInventoryReason {
    PARTIAL,
    FAILED,
    CANCELLED,
}

sealed interface InventoryScope {
    /** The complete launchable-component inventory for the Android user running the app. */
    data object CurrentUserAndroid : InventoryScope

    /** One user-selected source; its meaning is defined when ROM indexing is implemented. */
    data class UserSource(val sourceId: CatalogSourceId) : InventoryScope
}

sealed interface CatalogInventory {
    val scope: InventoryScope
    val observedItems: List<LibraryItem>

    data class Complete(
        override val scope: InventoryScope,
        override val observedItems: List<LibraryItem>,
    ) : CatalogInventory

    data class Incomplete(
        override val scope: InventoryScope,
        override val observedItems: List<LibraryItem>,
        val reason: IncompleteInventoryReason,
    ) : CatalogInventory
}

sealed interface InventoryStatus {
    data object NotRequested : InventoryStatus
    data class Complete(val scope: InventoryScope) : InventoryStatus
    data class Incomplete(
        val scope: InventoryScope,
        val reason: IncompleteInventoryReason,
    ) : InventoryStatus
}

data class CatalogSnapshot(
    /** Persisted catalog records, including items retained after a confirmed removal. */
    val items: List<LibraryItem>,
    val inventoryStatus: InventoryStatus,
) {
    /** Only available records participate in active launch lists. */
    val activeItems: List<LibraryItem>
        get() = items.filter { it.availability == Availability.Available }
}

data class CatalogReconciliation(
    val snapshot: CatalogSnapshot,
    val removedItemIds: Set<ItemId>,
)
