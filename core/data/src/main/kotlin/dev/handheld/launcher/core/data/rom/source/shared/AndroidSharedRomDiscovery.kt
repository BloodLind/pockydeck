package dev.handheld.launcher.core.data.rom.source.shared

import android.provider.DocumentsContract
import dev.handheld.launcher.core.data.rom.repository.RoomRomLibraryRepository
import dev.handheld.launcher.core.domain.model.CatalogSourceId
import dev.handheld.launcher.core.domain.rom.RomSource
import dev.handheld.launcher.core.domain.rom.RomSourceAccessKind
import dev.handheld.launcher.core.domain.rom.RomSourceStatus
import dev.handheld.launcher.core.domain.rom.scan.RomPlatforms
import dev.handheld.launcher.core.domain.rom.scan.RomScanPlanner
import dev.handheld.launcher.core.domain.rom.scan.RomScanRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class SharedDiscoveryState(val busy: Boolean = false, val foldersVisited: Int = 0, val error: String? = null, val hasMore: Boolean = false)

/** Bounded breadth-first discovery. The pending directory queue survives process death between runs. */
class AndroidSharedRomDiscovery(private val repository: RoomRomLibraryRepository, private val paths: SharedStoragePaths) {
    private val mutableState = MutableStateFlow(SharedDiscoveryState())
    val state: StateFlow<SharedDiscoveryState> = mutableState.asStateFlow()
    private val sourceAccess = SharedStorageRomSourceAccess(paths)

    suspend fun discover(): Unit = withContext(Dispatchers.IO) {
        if (!paths.hasAccess() || !repository.sharedDiscoveryEnabled.first()) {
            mutableState.value = SharedDiscoveryState()
            return@withContext
        }
        var sources = repository.allSources()
        if (sources.any { it.enabled && !it.automaticallyDiscovered && it.physicalRootKey == null }) {
            mutableState.value = SharedDiscoveryState(error="An existing folder provider has no comparable storage identity. Automatic discovery is paused to avoid duplicate games; use Android's storage folder picker for that source.")
            return@withContext
        }
        val volumes = paths.volumes()
        val availableKeys = volumes.map { it.key }.toSet()
        val continuation = SharedDiscoveryContinuation.resume(repository.discoveryCursor(),repository.discoveryVolumeKeys(),availableKeys)
        val queue = ArrayDeque(continuation.pending)
        val visited = HashSet<String>()
        val started = System.nanoTime()
        var count = 0
        var error: String? = null
        mutableState.value = SharedDiscoveryState(busy=true)
        try {
            while (queue.isNotEmpty() && count < 2_000 && System.nanoTime() - started < 10_000_000_000L) {
                currentCoroutineContext().ensureActive()
                val id = queue.removeFirst()
                if (!visited.add(id) || SharedDiscoveryPolicy.coveredRoot(id,sources)) continue
                if (id.substringAfter(':').count { it == '/' } > 24) {
                    error = "Some folders exceed the automatic discovery depth limit; select those console folders manually."
                    continue
                }
                count++
                try {
                    val folder = paths.resolve(id)
                    val platform = RomPlatforms.matchingFolder(folder.name)
                    if (platform != null) {
                        val treeUri = DocumentsContract.buildTreeDocumentUri(SharedDocumentId.AUTHORITY,id).toString()
                        val candidate = RomSource(CatalogSourceId("discovery:pending"),treeUri,id,folder.name,true,RomSourceStatus.NOT_SCANNED,
                            defaultPlatformId=platform.id,accessKind=RomSourceAccessKind.SHARED_STORAGE,physicalRootKey=id,automaticallyDiscovered=true)
                        val enumeration = sourceAccess.enumerate(candidate.copy(excludedPhysicalRootKeys=SharedDiscoveryPolicy.exclusions(candidate,sources)))
                        val plan = RomScanPlanner().plan(RomScanRequest(enumeration.documents,folder.name,platform.id,descriptorText=enumeration.descriptorText))
                        if (SharedDiscoveryPolicy.hasGames(plan,platform.id)) {
                            repository.upsertDiscoveredSource(treeUri,id,folder.name,platform.id)
                            sources = repository.allSources()
                            continue
                        }
                    }
                    val children = paths.children(id).filter { it.second.isDirectory }.map { it.first }
                    if (queue.size + children.size > 50_000) {
                        error = "Automatic discovery reached its folder queue limit; select a smaller console folder manually."
                    } else queue.addAll(children)
                } catch (cancelled: CancellationException) {
                    queue.addFirst(id)
                    throw cancelled
                } catch (failure: Exception) {
                    error = failure.message?.take(240) ?: "Some storage folders could not be read; existing games were kept."
                }
                mutableState.value = SharedDiscoveryState(true,count,error)
            }
        } finally {
            // No discovery omission is ever interpreted as a missing source or missing game.
            withContext(kotlinx.coroutines.NonCancellable) { repository.saveDiscoveryCursor(queue.toList(),continuation.knownVolumeKeys) }
            mutableState.value = SharedDiscoveryState(false,count,error,hasMore=queue.isNotEmpty())
        }
    }
}
