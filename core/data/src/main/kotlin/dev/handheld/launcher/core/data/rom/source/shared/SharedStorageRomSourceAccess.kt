package dev.handheld.launcher.core.data.rom.source.shared

import dev.handheld.launcher.core.data.rom.source.RomEnumeration
import dev.handheld.launcher.core.data.rom.source.RomSourceAccess
import dev.handheld.launcher.core.data.rom.source.RomSourceUnavailableException
import dev.handheld.launcher.core.domain.rom.RomSource
import dev.handheld.launcher.core.domain.rom.scan.RomDocument
import dev.handheld.launcher.core.domain.rom.scan.RomScanPlanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files

/** A complete read-only enumeration is required before omissions can be reconciled. */
class SharedStorageRomSourceAccess(private val paths: SharedStoragePaths) : RomSourceAccess {
    override suspend fun isAvailable(source: RomSource): Boolean = withContext(Dispatchers.IO) {
        try { paths.resolve(source.rootDocumentId).isDirectory }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    }

    override suspend fun enumerate(source: RomSource, onProgress: (Int) -> Unit): RomEnumeration = withContext(Dispatchers.IO) {
        if (!isAvailable(source)) throw RomSourceUnavailableException("This shared ROM folder is unavailable. Restore All files access or reconnect storage.")
        val documents = ArrayList<RomDocument>()
        val descriptors = LinkedHashMap<String,String>()
        val pending = ArrayDeque<Pair<String,Int>>()
        pending.add(source.rootDocumentId to 0)
        var descriptorBytes = 0
        val start = System.nanoTime()
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            if (System.nanoTime() - start > 30_000_000_000L) throw IOException("This source exceeded the scan time limit. Existing games were kept; choose a smaller console folder.")
            val (directory, depth) = pending.removeFirst()
            if (depth > 48) throw IOException("This source is too deep to scan safely; existing games were kept.")
            for ((id,file) in paths.children(directory)) {
                currentCoroutineContext().ensureActive()
                if (source.excludedPhysicalRootKeys.any { SharedDocumentId.contains(it,id) }) continue
                if (documents.size >= 100_000) throw IOException("This source exceeds 100,000 documents. Existing games were kept; choose a smaller console folder.")
                val relative = SharedDocumentId.relativeTo(source.rootDocumentId,id)
                val isDirectory = file.isDirectory
                documents.add(RomDocument(id,relative,isDirectory,if (isDirectory) null else Files.size(file.toPath()),
                    if (isDirectory) "vnd.android.document/directory" else "application/octet-stream"))
                if (isDirectory) pending.add(id to depth + 1)
                else if (RomScanPlanner.needsDescriptorText(relative)) {
                    val bytes = paths.resolve(id).inputStream().use { it.readNBytes(RomScanPlanner.MAX_DESCRIPTOR_BYTES + 1) }
                    if (bytes.size <= RomScanPlanner.MAX_DESCRIPTOR_BYTES) {
                        descriptorBytes += bytes.size
                        if (descriptorBytes > 32 * 1024 * 1024) throw IOException("This source contains too many disc descriptors. Existing games were kept.")
                        descriptors[id] = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
                    }
                }
            }
            onProgress(documents.size)
        }
        // Revocation/unmount between the last listing and commit must not look like an empty successful scan.
        if (!isAvailable(source)) throw RomSourceUnavailableException("Storage access changed during scanning; existing games were kept.")
        RomEnumeration(documents, descriptors)
    }
}
