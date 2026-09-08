package dev.handheld.launcher.core.data.rom.repository

import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import dev.handheld.launcher.core.data.local.*
import dev.handheld.launcher.core.domain.model.*
import dev.handheld.launcher.core.domain.rom.*
import dev.handheld.launcher.core.domain.rom.scan.*
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
    val cacheLimitBytes: Flow<Long> = dao.preferences().map { list ->
        list.firstOrNull { it.key == "cache:limit" }?.value?.toLongOrNull()?.takeIf { it in CACHE_LIMITS } ?: DEFAULT_CACHE_LIMIT
    }.distinctUntilChanged()

    private fun preferences(prefix: String): Flow<Map<String,String>> = dao.preferences().map { list ->
        list.filter { it.key.startsWith(prefix) }.associate { it.key.removePrefix(prefix) to it.value }
    }.distinctUntilChanged()

    override suspend fun findEntry(itemId: ItemId) = dao.document(itemId.value)?.domain()
    override suspend fun findSource(sourceId: CatalogSourceId) = dao.source(sourceId.value)?.domain()
    suspend fun enabledSources() = dao.readSources().filter { it.enabled }.map { it.domain() }

    override suspend fun addSource(treeUri: String, rootDocumentId: String, name: String): CatalogSourceId = database.withTransaction {
        require(treeUri.startsWith("content://") && rootDocumentId.isNotBlank() && name.isNotBlank())
        val existing = dao.sourceByUri(treeUri)
        val source = existing?.copy(enabled=true, status="NOT_SCANNED", error=null, revision=existing.revision+1)
            ?: RomSourceEntity("source:${UUID.randomUUID()}",treeUri,rootDocumentId,name,true,"NOT_SCANNED",null,null,null,0)
        dao.upsertSource(source)
        CatalogSourceId(source.sourceId)
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
    runCatching { RomSourceStatus.valueOf(status) }.getOrDefault(RomSourceStatus.NOT_SCANNED),defaultPlatformId,lastScanAt,error,count)
private fun RomDocumentEntity.domain() = RomEntry(ItemId(itemId),CatalogSourceId(sourceId),documentId,documentUri,relativePath,title,
    platformOverride ?: platformId,format,present,if(platformOverride!=null && !requiresRepair) null else issue,
    runCatching { JSONArray(companions).let { a -> List(a.length()) { a.getString(it) } } }.getOrDefault(emptyList()),sizeBytes,requiresRepair)
