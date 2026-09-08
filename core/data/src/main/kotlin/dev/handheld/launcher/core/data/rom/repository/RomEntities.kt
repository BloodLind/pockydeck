package dev.handheld.launcher.core.data.rom.repository

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "rom_sources", indices = [Index(value = ["tree_uri"], unique = true), Index(value = ["physical_root_key"])])
internal data class RomSourceEntity(
    @PrimaryKey @ColumnInfo(name="source_id") val sourceId: String,
    @ColumnInfo(name="tree_uri") val treeUri: String,
    @ColumnInfo(name="root_document_id") val rootDocumentId: String,
    val name: String,
    val enabled: Boolean,
    val status: String,
    @ColumnInfo(name="default_platform_id") val defaultPlatformId: String?,
    @ColumnInfo(name="last_scan_at") val lastScanAt: Long?,
    val error: String?,
    val revision: Long,
    @ColumnInfo(name="access_kind", defaultValue="'SAF'") val accessKind: String = "SAF",
    @ColumnInfo(name="physical_root_key") val physicalRootKey: String? = null,
    @ColumnInfo(name="automatically_discovered", defaultValue="0") val automaticallyDiscovered: Boolean = false,
)

@Entity(tableName = "rom_documents", indices = [Index(value=["source_id", "document_id"], unique=true)])
internal data class RomDocumentEntity(
    @PrimaryKey @ColumnInfo(name="item_id") val itemId: String,
    @ColumnInfo(name="source_id") val sourceId: String,
    @ColumnInfo(name="document_id") val documentId: String,
    @ColumnInfo(name="document_uri") val documentUri: String,
    @ColumnInfo(name="relative_path") val relativePath: String,
    val title: String,
    @ColumnInfo(name="platform_id") val platformId: String?,
    @ColumnInfo(name="platform_override") val platformOverride: String?,
    val format: String,
    val present: Boolean,
    val issue: String?,
    @ColumnInfo(name="requires_repair") val requiresRepair: Boolean,
    val companions: String,
    @ColumnInfo(name="size_bytes") val sizeBytes: Long?,
    @ColumnInfo(name="scan_token") val scanToken: String,
)

/** Keys are namespaced; selections are user-owned and survive removal/rescans. */
@Entity(tableName = "rom_preferences")
internal data class RomPreferenceEntity(@PrimaryKey val key: String, val value: String)

@Dao
internal interface RomDao {
    @Query("SELECT * FROM rom_sources ORDER BY name, source_id") fun sources(): Flow<List<RomSourceEntity>>
    @Query("SELECT * FROM rom_sources ORDER BY name, source_id") suspend fun readSources(): List<RomSourceEntity>
    @Query("SELECT * FROM rom_sources WHERE source_id=:id") suspend fun source(id: String): RomSourceEntity?
    @Query("SELECT * FROM rom_sources WHERE tree_uri=:uri") suspend fun sourceByUri(uri: String): RomSourceEntity?
    @Upsert suspend fun upsertSource(source: RomSourceEntity)
    @Query("SELECT * FROM rom_documents") fun documents(): Flow<List<RomDocumentEntity>>
    @Query("SELECT * FROM rom_documents WHERE item_id=:id") suspend fun document(id:String): RomDocumentEntity?
    @Query("SELECT * FROM rom_documents WHERE source_id=:id") suspend fun sourceDocuments(id:String): List<RomDocumentEntity>
    @Upsert suspend fun upsertDocument(document: RomDocumentEntity)
    @Upsert suspend fun upsertDocuments(documents: List<RomDocumentEntity>)
    @Query("UPDATE rom_documents SET scan_token=:token, present=1 WHERE source_id=:sourceId AND item_id IN (:itemIds)")
    suspend fun markObserved(sourceId: String, itemIds: List<String>, token: String)
    @Query("UPDATE catalog_items SET availability_code='available', unavailability_reason_code=NULL WHERE item_id IN (:itemIds) AND availability_code!='available'")
    suspend fun restoreObservedCatalog(itemIds: List<String>)
    @Query("UPDATE rom_documents SET present=0 WHERE item_id IN (:itemIds)")
    suspend fun markGroupedMembers(itemIds: List<String>)
    @Query("UPDATE catalog_items SET availability_code='unavailable', unavailability_reason_code='removed' WHERE item_id IN (:itemIds)")
    suspend fun hideGroupedMembers(itemIds: List<String>)
    @Query("UPDATE rom_documents SET present=0 WHERE source_id=:id AND scan_token!=:token") suspend fun markMissing(id:String, token:String)
    @Query("UPDATE catalog_items SET availability_code='unavailable', unavailability_reason_code=:reason WHERE item_id IN (SELECT item_id FROM rom_documents WHERE source_id=:id)") suspend fun hideSource(id:String, reason:String)
    @Query("UPDATE catalog_items SET availability_code='unavailable', unavailability_reason_code='removed' WHERE item_id IN (SELECT item_id FROM rom_documents WHERE source_id=:id AND present=0)") suspend fun hideMissing(id:String)
    @Query("SELECT * FROM rom_preferences") fun preferences(): Flow<List<RomPreferenceEntity>>
    @Query("SELECT value FROM rom_preferences WHERE `key`=:key") suspend fun preference(key:String): String?
    @Upsert suspend fun upsertPreference(preference: RomPreferenceEntity)
    @Query("DELETE FROM rom_preferences WHERE `key`=:key") suspend fun deletePreference(key:String)
}
