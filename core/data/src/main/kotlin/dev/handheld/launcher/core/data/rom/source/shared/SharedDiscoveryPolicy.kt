package dev.handheld.launcher.core.data.rom.source.shared

import dev.handheld.launcher.core.domain.rom.RomSource
import dev.handheld.launcher.core.domain.rom.scan.RomScanPlan

/** Manual sources and removed sources reserve their physical subtree from future automatic additions. */
object SharedDiscoveryPolicy {
    fun hasGames(plan: RomScanPlan, platformId: String): Boolean = plan.entries.isNotEmpty() ||
        plan.unresolved.any { !it.requiresRepair && platformId in it.candidatePlatformIds }

    fun coveredRoot(root: String, sources: List<RomSource>): Boolean = sources.any { source ->
        source.physicalRootKey?.let { SharedDocumentId.contains(it,root) } == true
    }

    fun exclusions(source: RomSource, sources: List<RomSource>): Set<String> {
        val root = source.physicalRootKey ?: return emptySet()
        if (!source.automaticallyDiscovered) return emptySet()
        return sources.filter { it.id != source.id }.mapNotNull { other ->
            other.physicalRootKey?.takeIf { otherRoot ->
                SharedDocumentId.contains(root,otherRoot) ||
                    ((!other.automaticallyDiscovered || !other.enabled) && SharedDocumentId.contains(otherRoot,root))
            }
        }.toSet()
    }
}
