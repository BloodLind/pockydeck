package dev.handheld.launcher.core.domain.repository

import dev.handheld.launcher.core.domain.model.CatalogInventory
import dev.handheld.launcher.core.domain.model.CatalogReconciliation
import dev.handheld.launcher.core.domain.model.CatalogSnapshot
import dev.handheld.launcher.core.domain.model.InventoryStatus
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.LaunchOperationId
import dev.handheld.launcher.core.domain.model.SuccessfulOpenCandidate
import dev.handheld.launcher.core.domain.model.SuccessfulOpenRecord
import dev.handheld.launcher.core.domain.model.SuccessfulOpenWriteResult
import dev.handheld.launcher.core.domain.model.UserItemOverrides
import dev.handheld.launcher.core.domain.policy.CatalogReconciliationPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class InMemoryCatalogRepository(
    initialItems: List<LibraryItem>,
) : CatalogRepository {
    private val mutableSnapshot = MutableStateFlow(
        CatalogSnapshot(initialItems, InventoryStatus.NotRequested),
    )
    override val snapshot: Flow<CatalogSnapshot> = mutableSnapshot.asStateFlow()

    override suspend fun findItem(id: ItemId): LibraryItem? =
        mutableSnapshot.value.items.firstOrNull { it.id == id }

    override suspend fun applyInventory(inventory: CatalogInventory): CatalogReconciliation {
        val reconciliation = CatalogReconciliationPolicy.reconcile(
            cachedItems = mutableSnapshot.value.items,
            inventory = inventory,
        )
        mutableSnapshot.value = reconciliation.snapshot
        return reconciliation
    }

    fun current(): CatalogSnapshot = mutableSnapshot.value
}

internal class InMemoryFavoriteRepository(
    initialItemIds: Set<ItemId> = emptySet(),
) : FavoriteRepository {
    private val mutableFavorites = MutableStateFlow(initialItemIds)
    override val favoriteItemIds: Flow<Set<ItemId>> = mutableFavorites.asStateFlow()

    override suspend fun setFavorite(itemId: ItemId, favorite: Boolean) {
        mutableFavorites.value = if (favorite) {
            mutableFavorites.value + itemId
        } else {
            mutableFavorites.value - itemId
        }
    }

    fun current(): Set<ItemId> = mutableFavorites.value
}

internal class InMemoryItemOverrideRepository(
    initialOverrides: Map<ItemId, UserItemOverrides> = emptyMap(),
) : ItemOverrideRepository {
    private val mutableOverrides = MutableStateFlow(initialOverrides)
    override val overridesByItemId: Flow<Map<ItemId, UserItemOverrides>> =
        mutableOverrides.asStateFlow()

    override suspend fun setOverrides(itemId: ItemId, overrides: UserItemOverrides?) {
        mutableOverrides.value = if (overrides == null) {
            mutableOverrides.value - itemId
        } else {
            mutableOverrides.value + (itemId to overrides)
        }
    }

    fun current(): Map<ItemId, UserItemOverrides> = mutableOverrides.value
}

internal class InMemorySuccessfulOpenRepository(
    initialRecords: List<SuccessfulOpenRecord> = emptyList(),
) : SuccessfulOpenRepository {
    private val lock = Mutex()
    private val acknowledgedOperations = mutableSetOf<LaunchOperationId>()
    private var nextOpenOrder = initialRecords.maxOfOrNull(SuccessfulOpenRecord::openOrder) ?: 0L
    private val mutableRecords = MutableStateFlow(initialRecords)
    override val records: Flow<List<SuccessfulOpenRecord>> = mutableRecords.asStateFlow()

    override suspend fun recordOnce(candidate: SuccessfulOpenCandidate): SuccessfulOpenWriteResult =
        lock.withLock {
        if (!acknowledgedOperations.add(candidate.operationId)) {
            return@withLock SuccessfulOpenWriteResult.AlreadyRecorded
        }
        val record = SuccessfulOpenRecord(candidate.itemId, ++nextOpenOrder)
        mutableRecords.value = (mutableRecords.value.filterNot { it.itemId == candidate.itemId } + record)
            .sortedByDescending(SuccessfulOpenRecord::openOrder)
        SuccessfulOpenWriteResult.Recorded(record)
        }

    fun current(): List<SuccessfulOpenRecord> = mutableRecords.value
}
