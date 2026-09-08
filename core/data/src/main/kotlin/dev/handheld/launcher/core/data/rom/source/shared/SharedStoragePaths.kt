package dev.handheld.launcher.core.data.rom.source.shared

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.Locale

data class SharedStorageVolume(val key: String, val directory: File, val name: String)

/** Volume-relative keys can be compared with Android's external-storage provider, never other providers. */
object SharedDocumentId {
    const val AUTHORITY = "dev.handheld.launcher.sharedroms"

    fun normalize(value: String): String {
        val separator = value.indexOf(':')
        require(separator > 0) { "Invalid shared-storage identity." }
        val volume = value.substring(0, separator).lowercase(Locale.ROOT)
        require(volume.matches(Regex("[a-z0-9][a-z0-9-]*"))) { "Invalid storage volume." }
        val relative = value.substring(separator + 1)
        require(!relative.startsWith('/') && !relative.endsWith('/') && '\\' !in relative && '\u0000' !in relative || relative.isEmpty()) { "Invalid storage path." }
        require(relative.isEmpty() || relative.split('/').all { it.isNotEmpty() && it != "." && it != ".." }) { "Invalid storage path." }
        return "$volume:$relative"
    }

    fun contains(root: String, child: String): Boolean {
        val parent = normalize(root)
        val target = normalize(child)
        return target == parent || target.startsWith(if (parent.endsWith(':')) parent else "$parent/")
    }

    fun child(parent: String, name: String): String = normalize(
        parent + (if (parent.endsWith(':')) "" else "/") + name,
    )

    fun relativeTo(root: String, child: String): String {
        require(contains(root, child)) { "Document is outside the source." }
        return normalize(child).removePrefix(normalize(root)).removePrefix("/")
    }

    fun excluded(id: String): Boolean = normalize(id).substringAfter(':').split('/').any {
        it.startsWith('.') || it.equals("Android", true) || it.equals("LOST.DIR", true)
    }
}

/** Read-only access to mounted public volumes. StorageManager's returned directory is the sole base. */
class SharedStoragePaths(
    private val accessGranted: () -> Boolean,
    private val volumeProvider: () -> List<SharedStorageVolume>,
) {
    constructor(context: Context) : this(
        { Environment.isExternalStorageManager() },
        {
            val app = context.applicationContext
            app.getSystemService(StorageManager::class.java).storageVolumes.mapNotNull { volume ->
                val directory = volume.directory ?: return@mapNotNull null
                if (volume.state != Environment.MEDIA_MOUNTED && volume.state != Environment.MEDIA_MOUNTED_READ_ONLY) return@mapNotNull null
                val key = if (volume.isPrimary) "primary" else volume.uuid?.lowercase(Locale.ROOT) ?: return@mapNotNull null
                SharedStorageVolume(key, directory, volume.getDescription(app))
            }
        },
    )

    fun hasAccess(): Boolean = accessGranted()
    fun volumes(): List<SharedStorageVolume> = if (hasAccess()) volumeProvider().distinctBy { it.key } else emptyList()

    fun resolve(documentId: String): File {
        if (!hasAccess()) throw SecurityException("All files access is not enabled.")
        val id = try { SharedDocumentId.normalize(documentId) } catch (error: IllegalArgumentException) {
            throw IOException("Invalid shared-storage document.", error)
        }
        if (SharedDocumentId.excluded(id)) throw IOException("Private or hidden folders are excluded from shared-storage discovery.")
        val volume = volumes().firstOrNull { it.key.equals(id.substringBefore(':'), true) }
            ?: throw IOException("This storage volume is disconnected.")
        val root = volume.directory.canonicalFile.toPath()
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw IOException("This storage volume is unavailable.")
        var target = root
        for (part in id.substringAfter(':').split('/').filter { it.isNotEmpty() }) {
            target = target.resolve(part)
            if (Files.isSymbolicLink(target)) throw IOException("Symbolic links are excluded from shared-storage access.")
        }
        if (!target.normalize().startsWith(root) || !Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw IOException("This shared-storage document is unavailable.")
        val canonical = target.toFile().canonicalFile
        if (canonical.toPath() != target.normalize() || !canonical.toPath().startsWith(root)) {
            throw IOException("Shared-storage path changed during access.")
        }
        return canonical
    }

    /** Streaming listing is bounded even when a directory contains an unexpectedly large number of entries. */
    fun children(documentId: String, maxEntries: Int = 20_000): List<Pair<String, File>> {
        val directory = resolve(documentId)
        if (!directory.isDirectory) throw IOException("Not a shared-storage folder.")
        val parentPath = directory.toPath()
        val result = ArrayList<Pair<String, File>>()
        Files.newDirectoryStream(parentPath).use { stream ->
            var visited = 0
            for (child in stream) {
                if (++visited > maxEntries) throw IOException("A folder exceeds the $maxEntries-entry discovery limit; select a smaller ROM folder manually.")
                if (visited % 128 == 0 && !hasAccess()) throw SecurityException("All files access changed during listing.")
                val id = SharedDocumentId.child(documentId, child.fileName.toString())
                if (SharedDocumentId.excluded(id) || Files.isSymbolicLink(child)) continue
                val canonical = child.toFile().canonicalFile
                if (canonical.toPath() != child.normalize() || !canonical.toPath().startsWith(parentPath) ||
                    !Files.exists(child, LinkOption.NOFOLLOW_LINKS)) {
                    throw IOException("A shared-storage path changed during listing.")
                }
                result.add(id to canonical)
            }
        }
        if (!hasAccess()) throw SecurityException("All files access changed during listing.")
        return result.sortedBy { it.first }
    }
}
