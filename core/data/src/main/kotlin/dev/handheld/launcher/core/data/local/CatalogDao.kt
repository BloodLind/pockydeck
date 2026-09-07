package dev.handheld.launcher.core.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

internal data class CatalogItemRecord(
    @Embedded val item: CatalogItemEntity,
    @Relation(parentColumn = "item_id", entityColumn = "item_id")
    val provenance: CatalogProvenanceEntity?,
    @Relation(parentColumn = "item_id", entityColumn = "item_id")
    val actions: List<CatalogItemActionEntity>,
)

@Dao
internal abstract class CatalogDao {
    @Transaction
    @Query("SELECT * FROM catalog_items")
    abstract suspend fun readCatalog(): List<CatalogItemRecord>

    @Transaction
    @Query("SELECT * FROM catalog_items WHERE item_id = :itemId")
    abstract suspend fun readItem(itemId: String): CatalogItemRecord?

    @Upsert
    abstract suspend fun upsertItem(item: CatalogItemEntity)

    @Upsert
    abstract suspend fun upsertProvenance(provenance: CatalogProvenanceEntity)

    @Query("DELETE FROM catalog_item_actions WHERE item_id = :itemId")
    abstract suspend fun deleteActions(itemId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertActions(actions: List<CatalogItemActionEntity>)

    @Query(
        """
        UPDATE catalog_items
        SET availability_code = 'unavailable',
            unavailability_reason_code = 'removed'
        WHERE item_id IN (:itemIds)
        """,
    )
    abstract suspend fun markRemoved(itemIds: Set<String>)

    @Query("SELECT * FROM inventory_status WHERE singleton_id = 1")
    abstract suspend fun readInventoryStatus(): InventoryStatusEntity?

    @Upsert
    abstract suspend fun upsertInventoryStatus(status: InventoryStatusEntity)
}

@Dao
internal interface CatalogReferenceDao {
    @Query("SELECT * FROM favorite_references ORDER BY item_id")
    fun observeFavorites(): Flow<List<FavoriteReferenceEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFavorite(reference: FavoriteReferenceEntity)

    @Query("DELETE FROM favorite_references WHERE item_id = :itemId")
    suspend fun deleteFavorite(itemId: String)

    @Query("SELECT * FROM item_overrides ORDER BY item_id")
    fun observeOverrides(): Flow<List<ItemOverrideEntity>>

    @Upsert
    suspend fun upsertOverride(overrides: ItemOverrideEntity)

    @Query("DELETE FROM item_overrides WHERE item_id = :itemId")
    suspend fun deleteOverride(itemId: String)

    @Upsert
    suspend fun upsertHistoryReference(reference: SuccessfulOpenReferenceEntity)

    @Query("SELECT * FROM successful_open_history ORDER BY open_order DESC")
    suspend fun readHistoryReferences(): List<SuccessfulOpenReferenceEntity>
}

@Dao
internal interface SuccessfulOpenDao {
    @Query("SELECT * FROM successful_open_history ORDER BY open_order DESC")
    fun observeHistory(): Flow<List<SuccessfulOpenReferenceEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM successful_open_operations WHERE operation_id = :operationId)")
    suspend fun operationExists(operationId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun ensureOrderState(state: SuccessfulOpenOrderStateEntity)

    @Query("SELECT last_open_order FROM successful_open_order_state WHERE singleton_id = 1")
    suspend fun readLastOpenOrder(): Long?

    @Query(
        """
        UPDATE successful_open_order_state
        SET last_open_order = :nextOpenOrder
        WHERE singleton_id = 1 AND last_open_order = :expectedOpenOrder
        """,
    )
    suspend fun updateOpenOrder(expectedOpenOrder: Long, nextOpenOrder: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOperation(operation: SuccessfulOpenOperationEntity)

    @Query(
        """
        UPDATE successful_open_history
        SET open_order = :openOrder
        WHERE item_id = :itemId
        """,
    )
    suspend fun updateHistory(itemId: String, openOrder: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHistory(reference: SuccessfulOpenReferenceEntity)

    @Query("SELECT COUNT(*) FROM successful_open_operations")
    suspend fun operationCount(): Int
}
