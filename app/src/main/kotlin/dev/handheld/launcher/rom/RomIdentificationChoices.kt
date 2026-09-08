package dev.handheld.launcher.rom

import dev.handheld.launcher.core.domain.model.CatalogSourceId
import dev.handheld.launcher.core.domain.rom.RomEntry
import dev.handheld.launcher.core.domain.rom.scan.RomPlatforms
import java.util.Locale

internal data class RomIdentificationChoices(val entries: List<RomEntry>, val total: Int) {
    companion object {
        fun forSource(entries: List<RomEntry>, sourceId: CatalogSourceId): RomIdentificationChoices {
            val unresolved = entries.filter { it.sourceId == sourceId && it.present && it.platformId?.let(RomPlatforms::byId) == null }
                .sortedWith(compareBy<RomEntry> { it.title.lowercase(Locale.ROOT) }.thenBy { it.relativePath }.thenBy { it.itemId.value })
            return RomIdentificationChoices(unresolved.take(100), unresolved.size)
        }
    }
}
