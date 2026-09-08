package dev.handheld.launcher.ui.presentation

import dev.handheld.launcher.core.domain.rom.scan.RomPlatforms
import java.util.Locale

/** Compact card captions are presentation data; persisted platform IDs remain unchanged. */
object RomPlatformLabels {
    private val shortLabels = mapOf(
        "nes" to "NES",
        "snes" to "SNES",
        "n64" to "N64",
        "gb" to "GB",
        "gbc" to "GBC",
        "gba" to "GBA",
        "virtualboy" to "VB",
        "nds" to "NDS",
        "3ds" to "3DS",
        "gamecube" to "GCN",
        "wii" to "WII",
        "wiiu" to "WII U",
        "switch" to "NSW",
        "psx" to "PSX",
        "ps2" to "PS2",
        "psp" to "PSP",
        "psvita" to "PSV",
        "sg1000" to "SG-1000",
        "mastersystem" to "SMS",
        "megadrive" to "MD",
        "gamegear" to "GG",
        "segacd" to "SCD",
        "sega32x" to "32X",
        "saturn" to "SS",
        "dreamcast" to "DC",
        "naomi" to "NAOMI",
        "atomiswave" to "AW",
        "arcade" to "ARC",
        "neogeo" to "NG",
        "neogeocd" to "NGCD",
        "pce" to "PCE",
        "pcecd" to "PCE CD",
        "supergrafx" to "SGX",
        "atari2600" to "A2600",
        "atari5200" to "A5200",
        "atari7800" to "A7800",
        "atari800" to "A800",
        "atarist" to "ST",
        "atarilynx" to "LYNX",
        "atarijaguar" to "JAG",
        "wonderswan" to "WS",
        "wonderswancolor" to "WSC",
        "neogeopocket" to "NGP",
        "neogeopocketcolor" to "NGPC",
        "colecovision" to "CV",
        "intellivision" to "INTV",
        "vectrex" to "VEC",
        "3do" to "3DO",
        "amiga" to "AMI",
        "c64" to "C64",
        "zxspectrum" to "ZX",
        "amstradcpc" to "CPC",
        "msx" to "MSX",
        "msx2" to "MSX2",
        "dos" to "DOS",
        "windows" to "PC",
        "scummvm" to "SCUMMVM",
    )

    fun shortLabel(platformId: String?): String = shortLabels[normalizedId(platformId)] ?: "ROM"

    /** Search recognizes stored IDs and full names even though cards show abbreviations. */
    fun searchTerms(platformId: String?): List<String> {
        val id = normalizedId(platformId) ?: return listOf("ROM", "Unassigned console")
        val platform = RomPlatforms.byId(id)
        return (listOfNotNull(id, shortLabel(id), platform?.displayName) + platform?.folderAliases.orEmpty()).distinct()
    }

    private fun normalizedId(platformId: String?): String? =
        platformId?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
}
