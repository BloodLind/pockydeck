package dev.handheld.launcher.core.data.rom.repository

import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import dev.handheld.launcher.core.data.local.*
import dev.handheld.launcher.core.domain.model.*
import dev.handheld.launcher.core.domain.rom.*
import dev.handheld.launcher.core.domain.rom.scan.*
import dev.handheld.launcher.core.data.rom.source.shared.SharedDocumentId
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import java.util.UUID

class RoomRomLibraryRepository(private val database: LauncherDatabase) : RomLibraryRepository {
    private val dao = database.romDao()
    override val sources: Flow<List<RomSource>> = combine(dao.sources(), dao.documents()) { sources, docs ->
        sources.map { it.domain(docs.count { doc -> doc.sourceId == it.sourceId && doc.present }) }
    }.distinctUntilChanged()
    override val entries = dao.documents().map { it.map(RomDocumentEntity::domain) }.distinctUntilChanged()
    override val consoleEmulatorDefaults = preferences("console:")
    override val itemEmulatorOverrides = preferences("item:").map { map -> map.mapKeys { ItemId(it.key) } }
    val consoleCores: Flow<Map<String,String>> = preferences("core:")
    val sharedDiscoveryEnabled: Flow<Boolean> = dao.preferences().map { list ->
        list.firstOrNull { it.key == "shared:enabled" }?.value != "false"
    }.distinctUntilChanged()
    suspend fun setSharedDiscoveryEnabled(enabled: Boolean) = putPreference("shared:enabled", enabled.toString())
    internal suspend fun discoveryCursor(): List<String> = dao.preference("shared:cursor")?.let { value ->
        runCatching { JSONArray(value).let { array -> List(array.length()) { array.getString(it) } } }.getOrNull()
    }.orEmpty()
    internal suspend fun discoveryVolumeKeys(): Set<String> = dao.preference("shared:cursor-volumes")?.let { value ->
        runCatching { JSONArray(value).let { array -> List(array.length()) { array.getString(it) }.toSet() } }.getOrNull()
    }.orEmpty()
    internal suspend fun saveDiscoveryCursor(ids: List<String>, volumeKeys: Set<String>) = database.withTransaction {
        putPreference("shared:cursor", JSONArray(ids).toString())
        putPreference("shared:cursor-volumes", JSONArray(if (ids.isEmpty()) emptyList<String>() else volumeKeys.toList()).toString())
    }
    val cacheLimitBytes: Flow<Long> = dao.preferences().map { list ->
        list.firstOrNull { it.key == "cache:limit" }?.value?.toLongOrNull()?.takeIf { it in CACHE_LIMITS } ?: DEFAULT_CACHE_LIMIT
    }.distinctUntilChanged()

    private fun preferences(prefix: String): Flow<Map<String,String>> = dao.preferences().map { list ->
        list.filter { it.key.startsWith(prefix) }.associate { it.key.removePrefix(prefix) to it.value }
    }.distinctUntilChanged()

    override suspend fun findEntry(itemId: ItemId) = dao.document(itemId.value)?.domain()
    override suspend fun findSource(sourceId: CatalogSourceId) = dao.source(sourceId.value)?.domain()
    suspend fun enabledSources() = dao.readSources().filter { it.enabled }.map { it.domain() }
    suspend fun allSources(): List<RomSource> {
        reconcilePhysicalRoots()
        return dao.readSources().map { it.domain() }
    }

    /** Existing opaque providers stay opaque. Only Android's documented external-storage IDs map to volumes. */
    private fun physicalKey(treeUri: String, documentId: String): String? =
        if (Uri.parse(treeUri).authority in setOf("com.android.externalstorage.documents", SharedDocumentId.AUTHORITY)) {
            runCatching { SharedDocumentId.normalize(documentId) }.getOrNull()
        } else null

    private suspend fun reconcilePhysicalRoots() = database.withTransaction {
        dao.readSources().filter { it.physicalRootKey == null }.forEach { source ->
            physicalKey(source.treeUri, source.rootDocumentId)?.let { key -> dao.upsertSource(source.copy(physicalRootKey=key)) }
        }
    }

    override suspend fun addSource(treeUri: String, rootDocumentId: String, name: String): CatalogSourceId = database.withTransaction {
        require(treeUri.startsWith("content://") && rootDocumentId.isNotBlank() && name.isNotBlank())
        val key = physicalKey(treeUri, rootDocumentId)
        val known = dao.readSources().map { old -> old.copy(physicalRootKey = old.physicalRootKey ?: physicalKey(old.treeUri, old.rootDocumentId)) }
        require(key != null || known.none { it.enabled && it.automaticallyDiscovered }) {
            "This provider cannot verify overlap with discovered folders. Choose Android's storage provider or remove the automatic sources first."
        }
        val existing = dao.sourceByUri(treeUri) ?: known.firstOrNull { key != null && it.physicalRootKey == key && it.automaticallyDiscovered }
        require(known.none { it.sourceId != existing?.sourceId && it.enabled && !it.automaticallyDiscovered && key != null &&
            it.physicalRootKey?.let { old -> SharedDocumentId.contains(old,key) || SharedDocumentId.contains(key,old) } == true }) {
            "This folder overlaps an existing manual source. Use that source to avoid duplicate games."
        }
        val kind = if (Uri.parse(treeUri).authority == SharedDocumentId.AUTHORITY) "SHARED_STORAGE" else "SAF"
        val source = existing?.copy(treeUri=treeUri, rootDocumentId=rootDocumentId, name=name, enabled=true, status="NOT_SCANNED", error=null,
            revision=existing.revision+1, accessKind=kind, physicalRootKey=key, automaticallyDiscovered=false)
            ?: RomSourceEntity("source:${UUID.randomUUID()}",treeUri,rootDocumentId,name,true,"NOT_SCANNED",null,null,null,0,kind,key,false)
        dao.upsertSource(source)
        if (key != null) {
            // Move only proven physical overlaps. Preserve each ItemId and all ID-keyed user references.
            known.filter { it.automaticallyDiscovered && it.physicalRootKey != null }.forEach { old ->
                val oldKey = requireNotNull(old.physicalRootKey)
                if (SharedDocumentId.contains(key,oldKey) || SharedDocumentId.contains(oldKey,key)) {
                    dao.sourceDocuments(old.sourceId).forEach { document ->
                        val physical = if (document.relativePath.isEmpty()) oldKey else SharedDocumentId.child(oldKey,document.relativePath)
                        if (SharedDocumentId.contains(key,physical)) {
                            val relative = SharedDocumentId.relativeTo(key,physical)
                            val newId = if (relative.isEmpty()) rootDocumentId else rootDocumentId + (if (rootDocumentId.endsWith(':')) "" else "/") + relative
                            val previousCompanions = document.domain().companionDocumentIds
                            val outsideCompanion = previousCompanions.any { companion ->
                                runCatching { SharedDocumentId.contains(key,companion) }.getOrDefault(false).not()
                            }
                            val companionIds = previousCompanions.map { companion ->
                                val normalized = runCatching { SharedDocumentId.normalize(companion) }.getOrNull()
                                if (normalized != null && SharedDocumentId.contains(key,normalized)) {
                                    val suffix = SharedDocumentId.relativeTo(key,normalized)
                                    rootDocumentId + (if (rootDocumentId.endsWith(':')) "" else "/") + suffix
                                } else companion
                            }
                            val moved = document.copy(sourceId=source.sourceId,documentId=newId,
                                documentUri=DocumentsContract.buildDocumentUriUsingTree(Uri.parse(treeUri),newId).toString(),
                                relativePath=relative, companions=JSONArray(companionIds).toString(),
                                requiresRepair=document.requiresRepair || outsideCompanion,
                                issue=if (outsideCompanion) "Some companion files are outside the selected folder. Select their common parent folder or repair this game." else document.issue)
                            dao.upsertDocument(moved)
                            val available = document.present && database.catalogDao().readItem(document.itemId)?.item?.availabilityCode == "available"
                            writeCatalog(moved,available)
                        }
                    }
                    if (old.sourceId != source.sourceId) {
                        val fullyCovered = SharedDocumentId.contains(key,oldKey)
                        dao.upsertSource(old.copy(enabled=old.enabled && !fullyCovered,
                            status=if (fullyCovered) "DISABLED" else "NOT_SCANNED", revision=old.revision+1))
                    }
                }
            }
        }
        CatalogSourceId(source.sourceId)
    }

    /** Automatic discovery cannot resurrect disabled sources or supersede explicit manual roots. */
    suspend fun upsertDiscoveredSource(treeUri: String, rootDocumentId: String, name: String, platformId: String): CatalogSourceId? = database.withTransaction {
        require(Uri.parse(treeUri).authority == SharedDocumentId.AUTHORITY && RomPlatforms.byId(platformId) != null)
        val key = SharedDocumentId.normalize(rootDocumentId)
        val known = dao.readSources().map { old -> old.copy(physicalRootKey = old.physicalRootKey ?: physicalKey(old.treeUri,old.rootDocumentId)) }
        if (known.any { it.enabled && !it.automaticallyDiscovered && it.physicalRootKey == null }) return@withTransaction null
        val existing = known.firstOrNull { it.physicalRootKey == key } ?: known.firstOrNull { it.treeUri == treeUri }
        if (known.any { source -> source.physicalRootKey?.let { root ->
            source.sourceId != existing?.sourceId && SharedDocumentId.contains(root,key)
        } == true }) return@withTransaction null
        if (existing != null) return@withTransaction if (existing.enabled && existing.automaticallyDiscovered) CatalogSourceId(existing.sourceId) else null
        val source = RomSourceEntity("source:${UUID.randomUUID()}",treeUri,rootDocumentId,name,true,"NOT_SCANNED",platformId,null,null,0,
            "SHARED_STORAGE",key,true)
        dao.upsertSource(source)
        CatalogSourceId(source.sourceId)
    }

    /** User action only; unlike discovery, an explicit restore may clear this root's tombstone. */
    suspend fun restoreSource(sourceId: CatalogSourceId) = database.withTransaction {
        val source = dao.source(sourceId.value) ?: return@withTransaction
        val key = source.physicalRootKey
        require(!source.automaticallyDiscovered || dao.readSources().none {
            it.sourceId != source.sourceId && it.enabled && !it.automaticallyDiscovered && it.physicalRootKey == null
        }) { "An existing folder provider cannot verify shared-storage overlap. Use Android's storage provider before restoring automatic discovery." }
        require(key == null || dao.readSources().none { it.sourceId != source.sourceId && it.enabled && !it.automaticallyDiscovered &&
            it.physicalRootKey?.let { root -> SharedDocumentId.contains(root,key) } == true }) {
            "This folder is already covered by a manual source. Use that source."
        }
        dao.upsertSource(source.copy(enabled=true,status="NOT_SCANNED",error=null,revision=source.revision+1))
    }

    override suspend fun removeSource(sourceId: CatalogSourceId) = database.withTransaction {
        dao.source(sourceId.value)?.let {
            dao.upsertSource(it.copy(enabled=false,status="DISABLED",error=null,revision=it.revision+1))
            dao.hideSource(it.sourceId, "source_unavailable")
        }
        Unit
    }

    override suspend fun setSourcePlatform(sourceId: CatalogSourceId, platformId: String?) = database.withTransaction {
        require(platformId == null || RomPlatforms.byId(platformId) != null)
        dao.source(sourceId.value)?.let {
            dao.upsertSource(it.copy(defaultPlatformId=platformId,status="NOT_SCANNED",revision=it.revision+1,error=null))
        }
        Unit
    }

    override suspend fun setItemPlatform(itemId: ItemId, platformId: String?) = database.withTransaction {
        require(platformId == null || RomPlatforms.byId(platformId) != null)
        dao.document(itemId.value)?.let { old ->
            val changed = old.copy(platformOverride=platformId)
            dao.upsertDocument(changed)
            val available = dao.source(old.sourceId)?.enabled == true && old.present &&
                database.catalogDao().readItem(old.itemId)?.item?.availabilityCode == "available"
            writeCatalog(changed, available)
        }
        Unit
    }

    override suspend fun setConsoleEmulator(platformId:String, emulatorId:String?) = putPreference("console:$platformId", emulatorId)
    override suspend fun setItemEmulator(itemId:ItemId, emulatorId:String?) = putPreference("item:${itemId.value}", emulatorId)
    suspend fun setConsoleCore(platformId:String, coreId:String?) = putPreference("core:$platformId",coreId)
    suspend fun setCacheLimit(bytes:Long) { require(bytes in CACHE_LIMITS); putPreference("cache:limit",bytes.toString()) }
    private suspend fun putPreference(key:String, value:String?) {
        if (value == null) dao.deletePreference(key) else { require(value.isNotBlank()); dao.upsertPreference(RomPreferenceEntity(key,value)) }
    }

    internal suspend fun beginScan(id:CatalogSourceId): Long? = database.withTransaction {
        val source = dao.source(id.value)?.takeIf { it.enabled } ?: return@withTransaction null
        dao.upsertSource(source.copy(status="SCANNING",error=null))
        source.revision
    }

    /** Finished entries are batched; only the final successful source commit marks omissions. */
    suspend fun commitScan(source:RomSource, revision:Long, documents:List<RomDocument>, plan:RomScanPlan) {
        val token = UUID.randomUUID().toString()
        val existing = dao.sourceDocuments(source.id.value).associateBy { it.documentId }
        val observed = documents.associateBy { it.documentId }
        val prepared = plan.entries.map { e ->
            newDocument(source, existing[e.documentId], e.documentId, e.relativePath,e.title,e.platformId,e.format,null,e.companionDocumentIds,observed[e.documentId]?.sizeBytes,token,false)
        } + plan.unresolved.map { e ->
            newDocument(source, existing[e.documentId],e.documentId,e.relativePath,e.title,null,e.format,e.reason,e.companionDocumentIds,observed[e.documentId]?.sizeBytes,token,e.requiresRepair)
        }
        prepared.chunked(128).forEach { batch ->
            currentCoroutineContext().ensureActive()
            database.withTransaction {
                checkCurrent(source.id,revision)
                batch.forEach { fresh ->
                    // Re-read user assignment so a correction made during enumeration wins.
                    val current = dao.document(fresh.itemId)
                    val entry = if(current!=null) fresh.copy(platformOverride=current.platformOverride) else fresh
                    dao.upsertDocument(entry)
                    writeCatalog(entry,true)
                }
            }
        }
        currentCoroutineContext().ensureActive()
        database.withTransaction {
            val current = checkCurrent(source.id,revision)
            dao.markMissing(source.id.value,token)
            dao.hideMissing(source.id.value)
            dao.upsertSource(current.copy(status="READY",error=null,lastScanAt=System.currentTimeMillis()))
        }
    }

    internal suspend fun failScan(source:CatalogSourceId, revision:Long, message:String, unavailable:Boolean) = database.withTransaction {
        dao.source(source.value)?.takeIf { it.enabled && it.revision == revision }?.let {
            dao.upsertSource(it.copy(status=if(unavailable) "UNAVAILABLE" else "ERROR",error=message))
            if(unavailable) dao.hideSource(source.value,"source_unavailable")
        }
        Unit
    }

    private suspend fun checkCurrent(id:CatalogSourceId,revision:Long):RomSourceEntity =
        dao.source(id.value)?.takeIf { it.enabled && it.revision==revision }
            ?: throw ScanSupersededException()

    private suspend fun writeCatalog(entry:RomDocumentEntity, available:Boolean) {
        val item = LibraryItem.RomGame(ItemId(entry.itemId),entry.title,CatalogSourceId(entry.sourceId),
            if(available) Availability.Available else Availability.Unavailable(UnavailabilityReason.SOURCE_UNAVAILABLE),
            setOf(SupportedItemAction.OPEN,SupportedItemAction.VIEW_DETAILS,SupportedItemAction.TOGGLE_FAVORITE),
            entry.platformOverride ?: entry.platformId,entry.format).toStoredCatalogItem()
        database.catalogDao().apply {
            upsertItem(item.item); upsertProvenance(item.provenance); deleteActions(item.item.itemId); insertActions(item.actions)
        }
    }

    private fun newDocument(source:RomSource,old:RomDocumentEntity?,documentId:String,path:String,title:String,platform:String?,format:String,issue:String?,companions:List<String>,size:Long?,token:String,requiresRepair:Boolean) =
        RomDocumentEntity(old?.itemId ?: "rom:${UUID.randomUUID()}",source.id.value,documentId,
            DocumentsContract.buildDocumentUriUsingTree(Uri.parse(source.treeUri),documentId).toString(),path,title,platform,old?.platformOverride,format,true,issue,requiresRepair,JSONArray(companions).toString(),size,token)

    companion object {
        const val GIB = 1024L * 1024 * 1024
        const val DEFAULT_CACHE_LIMIT = 8 * GIB
        val CACHE_LIMITS = setOf(2*GIB,4*GIB,8*GIB,16*GIB)
    }
}

internal class ScanSupersededException : Exception()
private fun RomSourceEntity.domain(count:Int=0) = RomSource(CatalogSourceId(sourceId),treeUri,rootDocumentId,name,enabled,
    runCatching { RomSourceStatus.valueOf(status) }.getOrDefault(RomSourceStatus.NOT_SCANNED),defaultPlatformId,lastScanAt,error,count,
    runCatching { RomSourceAccessKind.valueOf(accessKind) }.getOrDefault(RomSourceAccessKind.SAF),physicalRootKey,automaticallyDiscovered)
private fun RomDocumentEntity.domain() = RomEntry(ItemId(itemId),CatalogSourceId(sourceId),documentId,documentUri,relativePath,title,
    platformOverride ?: platformId,format,present,if(platformOverride!=null && !requiresRepair) null else issue,
    runCatching { JSONArray(companions).let { a -> List(a.length()) { a.getString(it) } } }.getOrDefault(emptyList()),sizeBytes,requiresRepair)
