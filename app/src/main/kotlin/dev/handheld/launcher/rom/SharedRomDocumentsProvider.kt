package dev.handheld.launcher.rom

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.system.Os
import android.webkit.MimeTypeMap
import dev.handheld.launcher.core.data.rom.source.shared.SharedDocumentId
import dev.handheld.launcher.core.data.rom.source.shared.SharedStoragePaths
import dev.handheld.launcher.core.data.rom.source.shared.SharedDiscoveryPolicy
import dev.handheld.launcher.core.domain.rom.RomSource
import dev.handheld.launcher.core.domain.rom.RomSourceAccessKind
import dev.handheld.launcher.di.LauncherApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException
import java.util.Locale

/** Read-only files beneath registered console roots, reachable only through explicit URI grants. */
class SharedRomDocumentsProvider : DocumentsProvider() {
    private val container get() = (requireNotNull(context).applicationContext as LauncherApplication).appContainer
    private val access by lazy {
        SharedRomDocumentAccess(container.sharedStoragePaths) {
            // Provider methods are synchronous Binder calls; this small source-only query observes
            // removals immediately without maintaining an independently stale grant registry.
            runBlocking(Dispatchers.IO) { container.romRepository.allSources() }
        }
    }

    override fun onCreate() = true

    // Never advertise broad storage roots to arbitrary picker clients.
    override fun queryRoots(projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: arrayOf(Root.COLUMN_ROOT_ID))

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        cursor(projection).apply { addDocument(documentId, access.resolve(documentId).file) }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
        cursor(projection).apply {
            access.children(parentDocumentId).forEach { (id, file) -> addDocument(id, file) }
        }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        access.open(documentId, mode, signal)

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        access.isChild(parentDocumentId, documentId)

    override fun getDocumentType(documentId: String): String = mime(access.resolve(documentId).file)

    private fun cursor(projection: Array<out String>?) = MatrixCursor(projection ?: COLUMNS)
    private fun MatrixCursor.addDocument(id: String, file: File) {
        val values = mapOf<String, Any?>(
            Document.COLUMN_DOCUMENT_ID to id, Document.COLUMN_DISPLAY_NAME to file.name,
            Document.COLUMN_MIME_TYPE to mime(file), Document.COLUMN_SIZE to if (file.isFile) file.length() else null,
            Document.COLUMN_LAST_MODIFIED to file.lastModified(), Document.COLUMN_FLAGS to 0,
        )
        addRow(columnNames.map { values[it] }.toTypedArray())
    }
    private fun mime(file: File) = if (file.isDirectory) Document.MIME_TYPE_DIR
        else MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase(Locale.ROOT)) ?: "application/octet-stream"

    companion object {
        const val AUTHORITY = SharedDocumentId.AUTHORITY
        private val COLUMNS = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS)
    }
}

/** Shared boundary for metadata and opened descriptors, independent of the provider's Binder glue. */
internal class SharedRomDocumentAccess(
    private val paths: SharedStoragePaths,
    private val registeredSources: () -> List<RomSource>,
) {
    data class Resolved(val root: File, val file: File, val excludedRoots: Set<String>)

    fun resolve(documentId: String): Resolved {
        try {
            if (!paths.hasAccess()) throw FileNotFoundException("Allow storage access in ROM folders settings")
            val sources = registeredSources()
            val source = sources.filter {
                it.enabled && it.accessKind == RomSourceAccessKind.SHARED_STORAGE &&
                    SharedDocumentId.contains(it.rootDocumentId, documentId)
            }.maxByOrNull { it.rootDocumentId.length }
                ?: throw FileNotFoundException("This console folder is no longer registered")
            val excluded = SharedDiscoveryPolicy.exclusions(source, sources)
            if (excluded.any { SharedDocumentId.contains(it, documentId) }) {
                throw FileNotFoundException("This folder is excluded from automatic storage access")
            }
            val root = paths.resolve(source.rootDocumentId)
            val target = paths.resolve(documentId)
            if (!root.isDirectory || !target.toPath().startsWith(root.toPath()) || !target.exists()) {
                throw FileNotFoundException("Reconnect this storage and rescan")
            }
            return Resolved(root, target, excluded)
        } catch (missing: FileNotFoundException) { throw missing }
        catch (error: Exception) {
            throw FileNotFoundException("This ROM folder is unavailable").apply { initCause(error) }
        }
    }

    fun children(documentId: String): List<Pair<String, File>> {
        val parent = resolve(documentId)
        if (!parent.file.isDirectory) throw FileNotFoundException("Not a console folder")
        // Bounded listing excludes links/protected folders and reads registration once per query.
        return paths.children(documentId).filterNot { (id, _) -> parent.excludedRoots.any { SharedDocumentId.contains(it, id) } }.map { (id, child) ->
            val file = child
            if (!file.toPath().startsWith(parent.root.toPath())) throw FileNotFoundException("Invalid child document")
            id to file
        }
    }

    fun isChild(parentId: String, documentId: String): Boolean = runCatching {
        if (!SharedDocumentId.contains(parentId, documentId)) return@runCatching false
        val parent = resolve(parentId)
        if (parent.excludedRoots.any { SharedDocumentId.contains(it, documentId) }) return@runCatching false
        val child = resolve(documentId).file.toPath()
        child != parent.file.toPath() && child.startsWith(parent.file.toPath())
    }.getOrDefault(false)

    fun open(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("ROM folders are read-only")
        signal?.throwIfCanceled()
        val resolved = resolve(documentId)
        if (!resolved.file.isFile) throw FileNotFoundException("Not a ROM file")
        val descriptor = ParcelFileDescriptor.open(resolved.file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            // Check the opened descriptor too, so a raced parent/link replacement cannot expand
            // a previously validated grant beyond the registered console root.
            val opened = File(Os.readlink("/proc/self/fd/${descriptor.fd}")).canonicalFile
            if (opened != resolved.file || !opened.toPath().startsWith(resolved.root.toPath())) {
                throw FileNotFoundException("This ROM moved while it was being opened. Rescan its folder.")
            }
            signal?.throwIfCanceled()
            return descriptor
        } catch (error: Exception) {
            descriptor.close()
            throw error
        }
    }
}
