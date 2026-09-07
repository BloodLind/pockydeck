package dev.handheld.launcher.core.data.repository

import androidx.room.withTransaction
import dev.handheld.launcher.core.data.local.CatalogItemRecord
import dev.handheld.launcher.core.data.local.FavoriteReferenceEntity
import dev.handheld.launcher.core.data.local.LauncherDatabase
import dev.handheld.launcher.core.data.local.toDomain
import dev.handheld.launcher.core.data.local.toEntity
import dev.handheld.launcher.core.data.local.toStoredCatalogItem
import dev.handheld.launcher.core.domain.model.CatalogInventory
import dev.handheld.launcher.core.domain.model.CatalogReconciliation
import dev.handheld.launcher.core.domain.model.CatalogSnapshot
import dev.handheld.launcher.core.domain.model.InventoryStatus
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.UserItemOverrides
import dev.handheld.launcher.core.domain.policy.CatalogReconciliationPolicy
import dev.handheld.launcher.core.domain.policy.LibraryItemOrdering
import dev.handheld.launcher.core.domain.repository.CatalogRepository
import dev.handheld.launcher.core.domain.repository.FavoriteRepository
import dev.handheld.launcher.core.domain.repository.ItemOverrideRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RoomCatalogRepository(
    private val database: LauncherDatabase,
) : CatalogRepository {
    private val dao = database.catalogDao()

    override val snapshot: Flow<CatalogSnapshot> = database.invalidationTracker.createFlow(
        "catalog_items",
        "catalog_provenance",
        "catalog_item_actions",
        "inventory_status",
        emitInitialState = true,
    ).map {
        database.withTransaction { readSnapshot() }
    }.distinctUntilChanged()

    override suspend fun findItem(id: ItemId): LibraryItem? =
        dao.readItem(id.value)?.toDomain()

    override suspend fun applyInventory(inventory: CatalogInventory): CatalogReconciliation =
        database.withTransaction {
            val cachedItems = dao.readCatalog().toDomainItems()
            val reconciliation = CatalogReconciliationPolicy.reconcile(cachedItems, inventory)

            when (inventory) {
                is CatalogInventory.Incomplete -> Unit
                is CatalogInventory.Complete -> {
                    inventory.observedItems.forEach { observedItem ->
                        val stored = observedItem.toStoredCatalogItem()
                        dao.upsertItem(stored.item)
                        dao.upsertProvenance(stored.provenance)
                        dao.deleteActions(stored.item.itemId)
                        if (stored.actions.isNotEmpty()) dao.insertActions(stored.actions)
                    }
                    if (reconciliation.removedItemIds.isNotEmpty()) {
                        reconciliation.removedItemIds
                            .asSequence()
                            .map(ItemId::value)
                            .chunked(MAX_REMOVAL_BINDINGS)
                            .forEach { itemIds -> dao.markRemoved(itemIds.toSet()) }
                    }
                }
            }

            requireNotNull(reconciliation.snapshot.inventoryStatus.toEntity()).also {
                dao.upsertInventoryStatus(it)
            }
            reconciliation
        }

    private suspend fun readSnapshot(): CatalogSnapshot = CatalogSnapshot(
        items = dao.readCatalog().toDomainItems(),
        inventoryStatus = dao.readInventoryStatus()?.toDomain() ?: InventoryStatus.NotRequested,
    )

    private fun List<CatalogItemRecord>.toDomainItems(): List<LibraryItem> =
        map(CatalogItemRecord::toDomain).sortedWith(LibraryItemOrdering.titleThenId)

    private companion object {
        /** Leaves headroom below Android SQLite's commonly configured 999 bind-variable limit. */
        const val MAX_REMOVAL_BINDINGS = 900
    }
}

class RoomFavoriteRepository(
    database: LauncherDatabase,
) : FavoriteRepository {
    private val dao = database.catalogReferenceDao()

    override val favoriteItemIds: Flow<Set<ItemId>> = dao.observeFavorites()
        .map { rows -> rows.mapTo(linkedSetOf()) { ItemId(it.itemId) } }
        .distinctUntilChanged()

    override suspend fun setFavorite(itemId: ItemId, favorite: Boolean) {
        if (favorite) {
            dao.insertFavorite(FavoriteReferenceEntity(itemId.value))
        } else {
            dao.deleteFavorite(itemId.value)
        }
    }
}

class RoomItemOverrideRepository(
    database: LauncherDatabase,
) : ItemOverrideRepository {
    private val dao = database.catalogReferenceDao()

    override val overridesByItemId: Flow<Map<ItemId, UserItemOverrides>> = dao.observeOverrides()
        .map { rows -> rows.associate { ItemId(it.itemId) to it.toDomain() } }
        .distinctUntilChanged()

    override suspend fun setOverrides(itemId: ItemId, overrides: UserItemOverrides?) {
        if (overrides == null) {
            dao.deleteOverride(itemId.value)
        } else {
            dao.upsertOverride(overrides.toEntity(itemId))
        }
    }
}
