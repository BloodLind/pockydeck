package dev.handheld.launcher.core.domain.rom

import dev.handheld.launcher.core.domain.model.CatalogSourceId
import dev.handheld.launcher.core.domain.model.ItemId
import kotlinx.coroutines.flow.Flow

enum class RomSourceStatus { NOT_SCANNED, SCANNING, READY, UNAVAILABLE, ERROR, DISABLED }
enum class RomSourceAccessKind { SAF, SHARED_STORAGE }

data class RomSource(
    val id: CatalogSourceId,
    val treeUri: String,
    val rootDocumentId: String,
    val name: String,
    val enabled: Boolean,
    val status: RomSourceStatus,
    val defaultPlatformId: String? = null,
    val lastScanAtMillis: Long? = null,
    val error: String? = null,
    val gameCount: Int = 0,
    val accessKind: RomSourceAccessKind = RomSourceAccessKind.SAF,
    /** Proven volume-relative identity only; null for opaque third-party SAF providers. */
    val physicalRootKey: String? = null,
    val automaticallyDiscovered: Boolean = false,
    /** Scan-local exclusions derived from manual roots and disabled discovery tombstones. */
    val excludedPhysicalRootKeys: Set<String> = emptySet(),
    /** Kept for correction in source settings, excluded from identified game counts. */
    val unidentifiedCount: Int = 0,
)

/** Document identity is provider-owned; the generated item ID survives rescans and source re-adds. */
data class RomEntry(
    val itemId: ItemId,
    val sourceId: CatalogSourceId,
    val documentId: String,
    val documentUri: String,
    val relativePath: String,
    val title: String,
    val platformId: String?,
    val format: String,
    val present: Boolean,
    val issue: String? = null,
    val companionDocumentIds: List<String> = emptyList(),
    val sizeBytes: Long? = null,
    val requiresRepair: Boolean = false,
)

interface RomLibraryRepository {
    val sources: Flow<List<RomSource>>
    val entries: Flow<List<RomEntry>>
    val consoleEmulatorDefaults: Flow<Map<String, String>>
    val itemEmulatorOverrides: Flow<Map<ItemId, String>>
    suspend fun findEntry(itemId: ItemId): RomEntry?
    suspend fun findSource(sourceId: CatalogSourceId): RomSource?
    suspend fun addSource(treeUri: String, rootDocumentId: String, name: String): CatalogSourceId
    /** Retains identities and user references, and never deletes document-provider files. */
    suspend fun removeSource(sourceId: CatalogSourceId)
    suspend fun setSourcePlatform(sourceId: CatalogSourceId, platformId: String?)
    suspend fun setItemPlatform(itemId: ItemId, platformId: String?)
    suspend fun setConsoleEmulator(platformId: String, emulatorId: String?)
    suspend fun setItemEmulator(itemId: ItemId, emulatorId: String?)
}
