package dev.handheld.launcher.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "catalog_items",
    indices = [
        Index(value = ["kind_code"]),
        Index(value = ["availability_code"]),
    ],
    primaryKeys = ["item_id"],
)
internal data class CatalogItemEntity(
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "kind_code") val kindCode: String,
    val title: String,
    @ColumnInfo(name = "category_code") val categoryCode: String,
    @ColumnInfo(name = "availability_code") val availabilityCode: String,
    @ColumnInfo(name = "unavailability_reason_code") val unavailabilityReasonCode: String?,
)

@Entity(
    tableName = "catalog_provenance",
    primaryKeys = ["item_id"],
    foreignKeys = [
        ForeignKey(
            entity = CatalogItemEntity::class,
            parentColumns = ["item_id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class CatalogProvenanceEntity(
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "provenance_code") val provenanceCode: String,
    @ColumnInfo(name = "source_id") val sourceId: String?,
    @ColumnInfo(name = "android_package_name") val androidPackageName: String?,
    @ColumnInfo(name = "android_activity_class_name") val androidActivityClassName: String?,
    @ColumnInfo(name = "system_action_id") val systemActionId: String?,
)

@Entity(
    tableName = "catalog_item_actions",
    primaryKeys = ["item_id", "action_code"],
    foreignKeys = [
        ForeignKey(
            entity = CatalogItemEntity::class,
            parentColumns = ["item_id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class CatalogItemActionEntity(
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "action_code") val actionCode: String,
)

/**
 * One persisted record describes the latest attempted inventory. A missing row means that no
 * inventory has been requested yet.
 */
@Entity(tableName = "inventory_status")
internal data class InventoryStatusEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "singleton_id")
    val singletonId: Int = SINGLETON_ID,
    @ColumnInfo(name = "state_code") val stateCode: String,
    @ColumnInfo(name = "scope_code") val scopeCode: String,
    @ColumnInfo(name = "source_id") val sourceId: String?,
    @ColumnInfo(name = "incomplete_reason_code") val incompleteReasonCode: String?,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

/** User-owned references intentionally have no cascade path from discovered catalog rows. */
@Entity(tableName = "favorite_references")
internal data class FavoriteReferenceEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "item_id")
    val itemId: String,
)

/** User-owned values intentionally have no cascade path from discovered catalog rows. */
@Entity(tableName = "item_overrides")
internal data class ItemOverrideEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "item_id")
    val itemId: String,
    @ColumnInfo(name = "category_code") val categoryCode: String?,
    @ColumnInfo(name = "artwork_reference") val artworkReference: String?,
)

/**
 * US-017 reserves the independent history-reference representation needed to prove that catalog
 * reconciliation cannot delete recency. US-018 owns recording and operation-id deduplication.
 */
@Entity(
    tableName = "successful_open_history",
    indices = [Index(value = ["open_order"], unique = true)],
)
internal data class SuccessfulOpenReferenceEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "item_id")
    val itemId: String,
    @ColumnInfo(name = "open_order") val openOrder: Long,
)
