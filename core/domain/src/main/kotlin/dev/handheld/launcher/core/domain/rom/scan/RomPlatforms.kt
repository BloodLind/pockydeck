package dev.handheld.launcher.core.domain.rom.scan

import java.util.Locale

/** Recognition is deliberately separate from emulator launch support and content validation. */
data class RomPlatformDefinition(
    val id: String,
    val displayName: String,
    val folderAliases: Set<String>,
    /** Lowercase suffixes without a leading dot. Compound suffixes use their longest match. */
    val extensions: Set<String>,
)

object RomPlatforms {
    /** Recognition includes RAR so it can report unsupported extraction instead of disappearing. */
    val archiveExtensions: Set<String> = setOf(
        "zip", "7z", "rar", "gz", "gzip", "xz", "bz2", "bzip2", "tar", "tar.gz", "tgz",
        "tar.xz", "txz", "tar.bz2", "tbz2", "tbz",
    )

    val all: List<RomPlatformDefinition> = listOf(
        platform("nes", "Nintendo Entertainment System", "nes|famicom|fc|nintendo entertainment system|fds|famicom disk system", "nes|fds|unf|unif"),
        platform("snes", "Super Nintendo", "snes|sfc|super nintendo|super famicom|super nintendo entertainment system", "sfc|smc|fig|swc|bs|st"),
        platform("n64", "Nintendo 64", "n64|nintendo 64", "z64|n64|v64|ndd"),
        platform("gb", "Game Boy", "gb|game boy|gameboy|sgb|super game boy", "gb|sgb"),
        platform("gbc", "Game Boy Color", "gbc|game boy color|gameboy color", "gbc|cgb"),
        platform("gba", "Game Boy Advance", "gba|game boy advance|gameboy advance", "gba|agb"),
        platform("virtualboy", "Virtual Boy", "virtualboy|virtual boy|vb", "vb|vboy"),
        platform("nds", "Nintendo DS", "nds|nintendo ds|ds|dsi", "nds|dsi|ids|srl"),
        platform("3ds", "Nintendo 3DS", "3ds|nintendo 3ds|n3ds", "3ds|cci|cxi|cia|3dsx"),
        platform("gamecube", "Nintendo GameCube", "gamecube|nintendo gamecube|gc|ngc", "gcm|iso|gcz|rvz|wia|ciso|nkit.iso|nkit.gcz|dol|elf|m3u"),
        platform("wii", "Nintendo Wii", "wii|nintendo wii|wiiware", "iso|wbfs|gcz|rvz|wia|ciso|nkit.iso|nkit.gcz|wad|dol|elf|m3u"),
        platform("wiiu", "Nintendo Wii U", "wiiu|wii u|nintendo wii u", "wud|wux|wua|rpx"),
        platform("switch", "Nintendo Switch", "switch|nintendo switch|nsw", "xci|nsp|nro|nso|nca|nsz|xcz"),
        platform("psx", "Sony PlayStation", "psx|ps1|psone|playstation|sony playstation", "cue|bin|img|iso|chd|pbp|ccd|mds|mdf|toc|cbn|ecm|exe|m3u|m3u8"),
        platform("ps2", "Sony PlayStation 2", "ps2|playstation 2|sony playstation 2", "iso|chd|cso|zso|bin|img|mdf|elf|gz|m3u|m3u8"),
        platform("psp", "Sony PSP", "psp|playstation portable|sony psp|psp iso", "iso|cso|chd|pbp|elf|prx"),
        platform("psvita", "Sony PlayStation Vita", "psvita|ps vita|vita|playstation vita", "vpk|pkg"),
        platform("sg1000", "Sega SG-1000", "sg1000|sg-1000|sega sg-1000", "sg|bin"),
        platform("mastersystem", "Sega Master System", "mastersystem|master system|sega master system|sms|mark iii", "sms|bms|bin"),
        platform("megadrive", "Sega Mega Drive / Genesis", "megadrive|mega drive|genesis|sega genesis|sega mega drive|md", "md|mdx|gen|smd|bin|68k|sgd"),
        platform("gamegear", "Sega Game Gear", "gamegear|game gear|sega game gear|gg", "gg|bin"),
        platform("segacd", "Sega CD", "segacd|sega cd|megacd|mega cd", "cue|iso|chd|bin|m3u|m3u8"),
        platform("sega32x", "Sega 32X", "sega32x|sega 32x|32x|megadrive 32x", "32x|bin|smd|md"),
        platform("saturn", "Sega Saturn", "saturn|sega saturn|ss", "cue|iso|chd|bin|ccd|mds|mdf|m3u|m3u8"),
        platform("dreamcast", "Sega Dreamcast", "dreamcast|sega dreamcast|dc", "gdi|cdi|chd|cue|bin|elf|m3u|m3u8"),
        platform("naomi", "Sega NAOMI", "naomi|naomi2|sega naomi|sega naomi 2", "zip|7z|chd|lst|dat"),
        platform("atomiswave", "Sammy Atomiswave", "atomiswave|sammy atomiswave|aw", "zip|7z|lst|dat"),
        platform("arcade", "Arcade", "arcade|mame|mame2003|mame2003plus|mame2010|mame2015|mame2016|fbneo|fba|final burn neo|final burn alpha|cps1|cps2|cps3", "zip|7z|chd|cmd"),
        platform("neogeo", "SNK Neo Geo", "neogeo|neo geo|neo geo aes|neo geo mvs", "neo|zip|7z"),
        platform("neogeocd", "SNK Neo Geo CD", "neogeocd|neo geo cd|ngcd", "cue|chd|iso|bin|m3u|m3u8"),
        platform("pce", "PC Engine / TurboGrafx-16", "pce|pc engine|pcengine|turbografx16|turbografx 16|tg16", "pce|bin"),
        platform("pcecd", "PC Engine CD", "pcecd|pc engine cd|pcenginecd|turbografxcd|turbografx cd|tgcd", "cue|chd|ccd|iso|bin|m3u|m3u8"),
        platform("supergrafx", "PC Engine SuperGrafx", "supergrafx|super grafx|sgx", "sgx|pce"),
        platform("atari2600", "Atari 2600", "atari2600|atari 2600|2600|vcs", "a26|bin|rom"),
        platform("atari5200", "Atari 5200", "atari5200|atari 5200|5200", "a52|bin|rom|car"),
        platform("atari7800", "Atari 7800", "atari7800|atari 7800|7800", "a78|bin"),
        platform("atari800", "Atari 8-bit", "atari800|atari 800|atari 8 bit|atari 8bit|atari xl|atari xe", "atr|atx|xfd|dcm|xex|car|rom|cas|bas|com|bin|m3u"),
        platform("atarist", "Atari ST", "atarist|atari st|st", "st|stx|msa|dim|ipf|m3u|m3u8"),
        platform("atarilynx", "Atari Lynx", "atarilynx|atari lynx|lynx", "lnx|lyx|o"),
        platform("atarijaguar", "Atari Jaguar", "atarijaguar|atari jaguar|jaguar", "j64|jag|rom|abs|cof|bin"),
        platform("wonderswan", "WonderSwan", "wonderswan|wonder swan|ws|bandai wonderswan", "ws"),
        platform("wonderswancolor", "WonderSwan Color", "wonderswancolor|wonder swan color|wsc", "wsc"),
        platform("neogeopocket", "Neo Geo Pocket", "neogeopocket|neo geo pocket|ngp", "ngp|ngpc"),
        platform("neogeopocketcolor", "Neo Geo Pocket Color", "neogeopocketcolor|neo geo pocket color|ngpc", "ngc|ngpc|ngp|npc"),
        platform("colecovision", "ColecoVision", "colecovision|coleco vision|coleco", "col|cv|rom|bin"),
        platform("intellivision", "Intellivision", "intellivision|mattel intellivision|intv", "int|itv|bin|rom"),
        platform("vectrex", "Vectrex", "vectrex|vec", "vec|bin"),
        platform("3do", "3DO", "3do|panasonic 3do|3do interactive multiplayer", "iso|chd|cue|bin"),
        platform("amiga", "Commodore Amiga", "amiga|commodore amiga|amiga500|amiga1200|amigacd32|amiga cd32", "adf|adz|dms|fdi|ipf|hdf|hdz|lha|slave|cue|iso|chd|ccd|mds|nrg|m3u|m3u8|uae|rp9"),
        platform("c64", "Commodore 64", "c64|commodore 64|commodore64", "d64|d71|d81|d80|d82|g64|g41|x64|t64|tap|prg|p00|crt|bin|m3u|m3u8"),
        platform("zxspectrum", "ZX Spectrum", "zxspectrum|zx spectrum|spectrum|sinclair zx spectrum", "tzx|tap|z80|sna|szx|trd|scl|dsk|dck|pzx|rzx|ipf"),
        platform("amstradcpc", "Amstrad CPC", "amstradcpc|amstrad cpc|cpc", "dsk|sna|tap|cdt|voc|cpr|m3u"),
        platform("msx", "MSX", "msx|msx1|microsoft msx", "rom|ri|mx1|dsk|cas|m3u|m3u8"),
        platform("msx2", "MSX2", "msx2|msx2+|msx turbo r|msxturbor", "rom|ri|mx2|dsk|cas|m3u|m3u8"),
        platform("dos", "DOS", "dos|msdos|ms dos|dosbox|pc dos", "dosz|exe|com|bat|iso|cue|ins|img|ima|vhd|jrc|tc|conf|m3u|m3u8"),
        platform("windows", "PC games", "windows|steam|epic|gog|amazon|pc|pc games|pcgames", "steam|epic|gog|amazon|pcgame"),
        platform("scummvm", "ScummVM", "scummvm|scumm vm", "scummvm"),
    )

    private val indexed = all.associateBy { it.id }
    private val aliases = all.flatMap { p -> p.folderAliases.map { normalizeAlias(it) to p } }
        .groupBy({ it.first }, { it.second }).mapValues { (_, values) -> values.distinctBy { it.id } }
    private val suffixes = (all.flatMap { it.extensions } + archiveExtensions).distinct()
        .sortedWith(compareByDescending<String> { it.length }.thenBy { it })

    fun byId(id: String): RomPlatformDefinition? = indexed[id]

    fun matchingFolder(name: String): RomPlatformDefinition? = aliases[normalizeAlias(name)]?.singleOrNull()

    fun formatOf(name: String): String? {
        val lower = name.lowercase(Locale.ROOT)
        return suffixes.firstOrNull { lower.endsWith(".$it") }
    }

    fun candidatesFor(format: String): Set<String> = if (format in archiveExtensions) {
        all.filter { it.id != "windows" }.mapTo(linkedSetOf()) { it.id }
    } else {
        all.filter { format in it.extensions }.mapTo(linkedSetOf()) { it.id }
    }

    fun supports(platformId: String, format: String): Boolean =
        byId(platformId)?.let { format in it.extensions || platformId != "windows" && format in archiveExtensions } == true

    private fun platform(id: String, name: String, aliases: String, extensions: String) =
        RomPlatformDefinition(id, name, (aliases.split('|') + id + name).toSet(), extensions.split('|').toSet())

    private fun normalizeAlias(value: String): String =
        value.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
}
