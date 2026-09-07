package dev.handheld.launcher.core.domain.repository

import dev.handheld.launcher.core.domain.model.CatalogInventory
import dev.handheld.launcher.core.domain.model.CatalogReconciliation
import dev.handheld.launcher.core.domain.model.CatalogSnapshot
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.UserItemOverrides
import kotlinx.coroutines.flow.Flow

interface CatalogRepository {
    val snapshot: Flow<CatalogSnapshot>

    suspend fun findItem(id: ItemId): LibraryItem?

    /** Only a complete inventory may infer that an unseen active item was removed. */
    suspend fun applyInventory(inventory: CatalogInventory): CatalogReconciliation
}

interface FavoriteRepository {
    /** References may outlive an active catalog entry and must not be cascade-deleted. */
    val favoriteItemIds: Flow<Set<ItemId>>

    suspend fun setFavorite(itemId: ItemId, favorite: Boolean)
}

interface ItemOverrideRepository {
    /** Overrides may outlive an active catalog entry and never replace its stable identity. */
    val overridesByItemId: Flow<Map<ItemId, UserItemOverrides>>

    suspend fun setOverrides(itemId: ItemId, overrides: UserItemOverrides?)
}
