package dev.handheld.launcher.core.data.android.status

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import dev.handheld.launcher.core.domain.model.StatusValue
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class StorageVolumeKind { INTERNAL, EXTERNAL }

/** IDs are only for stable in-memory ordering; presentation must never display them. */
data class StorageVolumeStatus(
    val id: String,
    val kind: StorageVolumeKind,
    val availableBytes: StatusValue<Long>,
    val readOnly: Boolean = false,
)

internal data class StorageTelemetry(
    val internalAvailableBytes: StatusValue<Long>,
    val volumes: StatusValue<List<StorageVolumeStatus>>,
)

internal data class StorageVolumeCandidate(
    val id: String?,
    val directory: File?,
    val primary: Boolean,
    val emulated: Boolean,
    val mounted: Boolean,
    val readOnly: Boolean = false,
)

/** One metadata read per distinct mounted root, without traversing user files. */
internal suspend fun readStorageTelemetry(context: Context): StorageTelemetry {
    val internalRoot = Environment.getDataDirectory()
    val internal = storageBytes { StatFs(internalRoot.absolutePath).availableBytes }
    val manager = context.getSystemService(StorageManager::class.java)
        ?: return StorageTelemetry(internal, StatusValue.Unsupported)
    val volumes = try {
        val candidates = manager.storageVolumes.map { volume ->
            currentCoroutineContext().ensureActive()
            val state = volume.state
            StorageVolumeCandidate(volume.uuid, volume.directory, volume.isPrimary, volume.isEmulated,
                state == Environment.MEDIA_MOUNTED || state == Environment.MEDIA_MOUNTED_READ_ONLY,
                state == Environment.MEDIA_MOUNTED_READ_ONLY)
        }
        val coroutineContext = currentCoroutineContext()
        StatusValue.Available(storageVolumeReadings(internalRoot, internal, candidates) { directory ->
            coroutineContext.ensureActive()
            StatFs(directory.absolutePath).availableBytes
        })
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: RuntimeException) {
        StatusValue.Unavailable
    }
    return StorageTelemetry(internal, volumes)
}

internal fun storageVolumeReadings(
    internalRoot: File,
    internalAvailableBytes: StatusValue<Long>,
    candidates: List<StorageVolumeCandidate>,
    readBytes: (File) -> Long,
): List<StorageVolumeStatus> {
    val roots = mutableSetOf(storageRootKey(internalRoot))
    val ids = mutableSetOf<String>()
    val result = mutableListOf(StorageVolumeStatus("internal", StorageVolumeKind.INTERNAL, internalAvailableBytes))
    candidates.asSequence().filter { it.mounted && !(it.primary && it.emulated) }
        .sortedWith(compareBy<StorageVolumeCandidate> { it.directory == null }
            .thenBy { it.id.orEmpty().lowercase(Locale.ROOT) }
            .thenBy { it.directory?.absolutePath.orEmpty() })
        .forEachIndexed { index, candidate ->
            val path = candidate.directory?.let(::storageRootKey)
            val volumeId = candidate.id?.trim()?.takeIf(String::isNotEmpty)?.lowercase(Locale.ROOT)
            val duplicate = (path != null && path in roots) || (volumeId != null && volumeId in ids)
            if (path != null) roots += path
            if (volumeId != null) ids += volumeId
            if (duplicate) return@forEachIndexed
            val reading = candidate.directory?.let { directory -> storageBytes { readBytes(directory) } }
                ?: StatusValue.Unavailable
            result += StorageVolumeStatus(volumeId ?: path ?: "unavailable-$index", StorageVolumeKind.EXTERNAL,
                reading, candidate.readOnly)
        }
    return result
}

private fun storageRootKey(file: File): String = try {
    file.canonicalPath
} catch (_: IOException) {
    file.absoluteFile.normalize().path
} catch (_: SecurityException) {
    file.absoluteFile.normalize().path
}

private fun storageBytes(read: () -> Long): StatusValue<Long> = try {
    read().takeIf { it >= 0 }?.let { StatusValue.Available(it) } ?: StatusValue.Unavailable
} catch (cancelled: kotlinx.coroutines.CancellationException) {
    throw cancelled
} catch (_: RuntimeException) {
    StatusValue.Unavailable
}
