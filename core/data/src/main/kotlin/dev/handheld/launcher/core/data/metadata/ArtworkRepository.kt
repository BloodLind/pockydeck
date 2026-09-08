package dev.handheld.launcher.core.data.metadata

import android.graphics.BitmapFactory
import androidx.room.withTransaction
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.repository.ItemOverrideRepository
import dev.handheld.launcher.core.domain.rom.RomLibraryRepository
import dev.handheld.launcher.core.domain.rom.RomSourceStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class ArtworkSummary(val ready: Int = 0, val pending: Int = 0, val missing: Int = 0, val failed: Int = 0, val paused: Boolean = false)

/** Persistent item queue; catalog ordering and user overrides never depend on this database. */
class ArtworkRepository(
    private val database: ArtworkDatabase,
    private val roms: RomLibraryRepository,
    private val resolver: EsDeArtworkResolver,
    private val cacheDirectory: File,
    private val provider: LibretroArtworkProvider,
    private val workNeeded: () -> Unit,
) {
    private val dao = database.artworkDao()
    private val localMutex = Mutex()
    private val processing = Mutex()
    private var cacheBytesEstimate: Long? = null
    private var observing: Job? = null
    val paused = dao.observeSetting("paused").map { it == "true" }.distinctUntilChanged()
    val summary: Flow<ArtworkSummary> = combine(dao.counts(), paused) { counts, paused ->
        val map = counts.associate { it.state to it.count }
        ArtworkSummary(map[ArtworkRecord.READY] ?: 0, (map[ArtworkRecord.QUEUED] ?: 0) + (map[ArtworkRecord.RETRY] ?: 0),
            (map[ArtworkRecord.MISSING] ?: 0) + (map[ArtworkRecord.UNSUPPORTED] ?: 0), map[ArtworkRecord.FAILED] ?: 0, paused)
    }
    fun observe(id: ItemId): Flow<ArtworkRecord?> = dao.observe(id.value).distinctUntilChanged()

    @Synchronized
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope, overrides: ItemOverrideRepository) {
        if (observing != null) return
        observing = scope.launch(Dispatchers.IO) {
            combine(roms.entries, roms.sources, overrides.overridesByItemId) { entries, sources, corrections ->
                val enabled = sources.filter { it.enabled && it.status !in setOf(RomSourceStatus.DISABLED, RomSourceStatus.UNAVAILABLE) }.map { it.id }.toSet()
                entries.filter { it.present && it.sourceId in enabled && corrections[it.itemId]?.artworkReference == null }
                    .map { ArtworkRecord(it.itemId.value, it.title, it.platformId) }
            }.distinctUntilChanged().debounce(350).onEach { wanted ->
                database.withTransaction {
                    val previous = dao.all().associateBy { it.itemId }
                    dao.deactivateAll()
                    dao.insert(wanted)
                    wanted.chunked(400).forEach { dao.activate(it.map(ArtworkRecord::itemId)) }
                    wanted.forEach { item ->
                        previous[item.itemId]?.takeIf { it.platformId != item.platformId || it.title != item.title }?.let {
                            dao.put(item.copy(priority = it.priority, lastAccessAt = it.lastAccessAt))
                        }
                    }
                }
                if (dao.pendingCount() > 0) workNeeded()
            }.retryWhen { cause, attempt ->
                if (cause is CancellationException) throw cause
                delay(30_000)
                attempt < 3
            }.catch { /* Artwork storage failure must not take down local launching. */ }.collect()
        }
    }

    suspend fun request(id: ItemId) = withContext(Dispatchers.IO) {
        val entry = roms.findEntry(id)?.takeIf { it.present } ?: return@withContext
        dao.insert(listOf(ArtworkRecord(id.value, entry.title, entry.platformId)))
        dao.prioritize(id.value, System.currentTimeMillis())
        val current = dao.find(id.value) ?: return@withContext
        if (current.state == "evicted") dao.put(current.copy(state = ArtworkRecord.QUEUED, attempts = 0, nextAttemptAt = 0))
        if (current.fileReference == null) findLocal(current)
        if (dao.find(id.value)?.state in setOf(ArtworkRecord.QUEUED, ArtworkRecord.RETRY)) workNeeded()
    }

    suspend fun setPaused(value: Boolean) {
        dao.putSetting(ArtworkSetting("paused", value.toString()))
        if (!value) workNeeded()
    }

    suspend fun retryMissing() = withContext(Dispatchers.IO) {
        processing.withLock {
            localMutex.withLock { resolver.invalidate() }
            provider.invalidateIndexes()
            dao.retryMissing()
        }
        workNeeded()
    }

    /** Returns the delay for a durable continuation, or null when there is no work. */
    suspend fun process(online: () -> Boolean, maximumMillis: Long = 90_000): Long? = withContext(Dispatchers.IO) {
        processing.withLock {
            val deadline = System.currentTimeMillis() + maximumMillis
            var locallyChecked = 0
            while (System.currentTimeMillis() < deadline) {
                currentCoroutineContext().ensureActive()
                if (dao.setting("paused") == "true") return@withLock null
                val item = dao.pending(System.currentTimeMillis()).firstOrNull() ?: break
                if (findLocal(item)) { locallyChecked++; continue }
                val current = dao.find(item.itemId) ?: continue
                if (!current.active || current.state == ArtworkRecord.READY) continue
                if (current.platformId !in LibretroArtworkProvider.systems) {
                    dao.put(current.copy(state = ArtworkRecord.UNSUPPORTED, message = "No online artwork collection for this console"))
                    continue
                }
                if (!online()) {
                    // Keep looking for local artwork in this slice without retrying the same row.
                    dao.put(current.copy(nextAttemptAt = System.currentTimeMillis() + 60_000))
                    if (++locallyChecked >= 128) return@withLock 60_000L
                    continue
                }
                try {
                    val download = provider.find(current.platformId, listOfNotNull(current.title, current.matchTitle))
                    if (download == null) finish(current.itemId) { it.copy(state = ArtworkRecord.MISSING, message = "No matching artwork found") }
                    else saveDownload(current, download)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    val attempts = current.attempts + 1
                    val wait = maxOf((error as? ArtworkRequestFailure)?.retryAfterMillis ?: 0, 30_000L * (1L shl attempts.coerceAtMost(8)))
                    finish(current.itemId) { it.copy(state = if (attempts >= 5) ArtworkRecord.FAILED else ArtworkRecord.RETRY,
                        attempts = attempts, nextAttemptAt = System.currentTimeMillis() + wait,
                        message = if (attempts >= 5) "Download failed; retry from Artwork settings" else "Download will retry") }
                    if (error is IOException) return@withLock wait
                }
            }
            trimCache()
            if (dao.pendingCount() == 0) null else ((dao.nextAttempt() ?: 0) - System.currentTimeMillis()).coerceIn(1_000, 86_400_000)
        }
    }

    private suspend fun findLocal(item: ArtworkRecord): Boolean = localMutex.withLock {
        val current = dao.find(item.itemId) ?: return@withLock false
        if (current.state == ArtworkRecord.READY && file(current)?.isFile == true) return@withLock true
        val entry = roms.findEntry(ItemId(item.itemId)) ?: return@withLock false
        val source = roms.findSource(entry.sourceId) ?: return@withLock false
        // Do not rediscover a local file that the bitmap loader has already rejected.
        val rejected = current.sourceReference.takeIf { current.provider == "ES-DE" && current.fileReference == null }
        val found = runCatching { resolver.resolve(entry, source, rejected) }.getOrElse { LocalArtwork(null, null) }
        if (found.file != null) {
            finish(item.itemId) { it.copy(state = ArtworkRecord.READY, provider = "ES-DE", fileReference = found.file.path,
                sourceReference = found.file.path, matchTitle = found.title, attempts = 0, message = null) }
            true
        } else {
            if (found.title != null && current.matchTitle != found.title) finish(item.itemId) { it.copy(matchTitle = found.title) }
            false
        }
    }

    fun file(record: ArtworkRecord): File? = record.fileReference?.let { reference ->
        when (record.provider) {
            "ES-DE" -> File(reference).takeIf { resolver.readable(it) }
            "Libretro" -> reference.takeIf { it.matches(Regex("[a-f0-9]{64}\\.png")) }?.let { File(cacheDirectory, it) }
            else -> null
        }
    }

    suspend fun invalidFile(id: ItemId, reference: String?) {
        val current = dao.find(id.value) ?: return
        if (current.fileReference != reference) return
        dao.put(current.copy(state = ArtworkRecord.QUEUED, fileReference = null, attempts = 0, nextAttemptAt = 0))
        workNeeded()
    }

    private suspend fun finish(id: String, change: (ArtworkRecord) -> ArtworkRecord) {
        database.withTransaction { dao.find(id)?.let { dao.put(change(it)) } }
    }

    private suspend fun saveDownload(item: ArtworkRecord, download: ArtworkDownload) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(download.bytes, 0, download.bytes.size, bounds)
        if (bounds.outWidth !in 1..8192 || bounds.outHeight !in 1..8192 || bounds.outWidth.toLong() * bounds.outHeight > 24_000_000) {
            throw IOException("Invalid or oversized artwork image")
        }
        // A local result that arrived during the HTTP request always wins.
        val latest = dao.find(item.itemId) ?: return
        if (!latest.active || (latest.provider == "ES-DE" && latest.state == ArtworkRecord.READY && latest.fileReference != null)
            || latest.title != item.title || latest.platformId != item.platformId) return
        val decode = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 768) inSampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeByteArray(download.bytes, 0, download.bytes.size, decode)
            ?: throw IOException("Invalid artwork image")
        cacheDirectory.mkdirs()
        val name = artworkHash(item.itemId + download.url) + ".png"
        val target = File(cacheDirectory, name)
        val beforeBytes = cacheBytesEstimate ?: (cacheDirectory.listFiles()?.sumOf { it.length() } ?: 0L)
        val oldLength = target.length()
        val temporary = File(cacheDirectory, "$name.tmp")
        try { temporary.outputStream().use {
            if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) throw IOException("Could not cache artwork")
        } }
        finally { bitmap.recycle() }
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        finish(item.itemId) { it.copy(state = ArtworkRecord.READY, provider = "Libretro", fileReference = name,
            sourceReference = download.url, matchTitle = download.title, attempts = 0, message = null) }
        cacheBytesEstimate = beforeBytes - oldLength + target.length()
        if (requireNotNull(cacheBytesEstimate) > 192L * 1024 * 1024) trimCache()
    }

    private suspend fun trimCache() {
        val records = dao.downloaded()
        var size = cacheDirectory.listFiles()?.sumOf { it.length() } ?: 0L
        val referenced = records.mapNotNull { it.fileReference }.toSet()
        cacheDirectory.listFiles()?.filter { it.name !in referenced }?.forEach { size -= it.length(); it.delete() }
        for (record in records) {
            if (size <= 192L * 1024 * 1024) break
            val file = file(record) ?: continue
            size -= file.length()
            // Evicted entries wait for an actual visible-card request; no download/evict loop.
            finish(record.itemId) { it.copy(state = "evicted", fileReference = null) }
            file.delete()
        }
        cacheBytesEstimate = size
    }
}
