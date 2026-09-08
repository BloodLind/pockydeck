package dev.handheld.launcher.core.data.rom.emulator

import dev.handheld.launcher.core.data.rom.archive.ArchiveExtractor
import java.nio.charset.StandardCharsets
import java.util.Locale

/** Android-independent eligibility; selected console is never inferred from a shared disc suffix. */
internal object RomLaunchPolicy {
    val archiveExtensions: Set<String> = ArchiveExtractor.supportedFormats
    private val companionFormats = setOf("cue", "gdi", "m3u", "ccd", "scummvm")

    fun extensions(profile: EmulatorProfile, platformId: String, coreId: String?): Set<String> {
        if (profile.contract != EmulatorContract.RETROARCH) return profile.extensions
        val cores = EmulatorRegistry.coresForPlatform(platformId)
        val selected = cores.firstOrNull { it.id == coreId }
        return if (coreId == null) cores.flatMap { it.supportedExtensions }.toSet()
        else selected?.supportedExtensions.orEmpty()
    }

    fun unsupportedReason(profile: EmulatorProfile, input: RomLaunchInput): String? {
        if (input.platformId !in profile.platforms) return "This emulator does not support this console."
        if (profile.contract == EmulatorContract.DETECTION_ONLY) return profile.unavailableReason
        val extension = input.extension.removePrefix(".").lowercase(Locale.ROOT)
        if (profile.contract == EmulatorContract.RETROARCH && input.coreId != null &&
            EmulatorRegistry.coresForPlatform(input.platformId).none { it.id == input.coreId }) {
            return "Choose a compatible RetroArch core for this console in Settings."
        }
        if (extension !in extensions(profile, input.platformId, input.coreId)) {
            return if (extension in archiveExtensions) "This archive must be prepared before this emulator can open it."
            else "This emulator does not support .$extension games for this console."
        }
        if (extension in companionFormats && (input.treeUri == null || input.relativePath == null)) {
            return "This game needs its companion files. Add its containing folder and rescan."
        }
        return null
    }

    fun validRelativePath(path: String): Boolean = path.isNotBlank() &&
        !path.startsWith('/') && !path.startsWith('\\') && '\\' !in path &&
        path.split('/').all { it.isNotEmpty() && it != "." && it != ".." && ':' !in it && it.none(Char::isISOControl) }

    /** RetroArch's own SAF path serialization, not a guessed Android filesystem location. */
    fun retroArchPath(treeUri: String, relativePath: String): String? {
        if (!treeUri.startsWith("content://") || !validRelativePath(relativePath)) return null
        val encoded = buildString {
            for (byte in treeUri.toByteArray(StandardCharsets.UTF_8)) {
                val unsigned = byte.toInt() and 0xff
                val character = unsigned.toChar()
                if (character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9' || character in "-._~") append(character)
                else append('%').append("0123456789ABCDEF"[unsigned ushr 4]).append("0123456789ABCDEF"[unsigned and 15])
            }
        }
        return "saf://$encoded/$relativePath"
    }
}
