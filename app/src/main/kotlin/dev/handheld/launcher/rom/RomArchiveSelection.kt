package dev.handheld.launcher.rom

import dev.handheld.launcher.core.data.rom.archive.ArchiveExtractor
import dev.handheld.launcher.core.domain.rom.scan.PlannedRomEntry
import dev.handheld.launcher.core.domain.rom.scan.RomEntryKind
import dev.handheld.launcher.core.domain.rom.scan.RomScanPlan
import java.io.IOException
import java.util.Locale

/** Keeps archive content selection in the launcher instead of delegating an arbitrary member. */
internal object RomArchiveSelection {
    fun isArchive(format: String): Boolean = normalized(format) in ArchiveExtractor.supportedFormats || normalized(format) == "rar"

    fun preserveNativeContainer(platform: String, format: String): Boolean {
        val suffix = normalized(format)
        return platform in setOf("arcade", "neogeo", "naomi", "atomiswave") && suffix in setOf("zip", "7z") ||
            platform == "dos" && suffix in setOf("zip", "dosz")
    }

    // Even an emulator that accepts ZIP/7Z may select the first member without asking the user.
    fun requiresPreparation(platform: String, format: String): Boolean =
        normalized(format) in ArchiveExtractor.supportedFormats && !preserveNativeContainer(platform, format)

    fun selectableGames(plan: RomScanPlan): List<PlannedRomEntry> {
        plan.unresolved.firstOrNull { it.requiresRepair }?.let { damaged ->
            throw IOException("${damaged.title}: ${damaged.reason}")
        }
        val games = plan.entries.filter { it.kind != RomEntryKind.ARCHIVE && !isArchive(it.format) }
        if (games.isEmpty()) {
            throw IOException(plan.unresolved.firstOrNull()?.reason
                ?: "No supported game was found inside this archive. Nested or encrypted archives need to be unpacked first.")
        }
        return games
    }

    private fun normalized(format: String) = format.removePrefix(".").lowercase(Locale.ROOT)
}
