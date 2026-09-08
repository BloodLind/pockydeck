package dev.handheld.launcher.core.domain.rom.scan

internal data class DescriptorReferences(val paths: List<String>, val error: String? = null)

/** Parses references only. It does not read tracks, execute commands, or interpret emulator options. */
internal object RomDescriptorParser {
    private val cueFile = Regex("""^FILE\s+(?:"([^"]+)"|(\S+))\s+(?:BINARY|MOTOROLA|AIFF|WAVE|MP3)\s*$""", RegexOption.IGNORE_CASE)
    private val cueTrack = Regex("""^TRACK\s+\d+\s+\S+\s*$""", RegexOption.IGNORE_CASE)
    private val gdiTrack = Regex("""^(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(?:"([^"]+)"|(\S+))\s+(\d+)\s*$""")
    private val tocFile = Regex("""^(?:FILE|DATAFILE|AUDIOFILE)\s+"([^"]+)"(?:\s+.*)?$""", RegexOption.IGNORE_CASE)

    fun parse(format: String, text: String): DescriptorReferences {
        if (text.toByteArray(Charsets.UTF_8).size > MAX_DESCRIPTOR_BYTES || '\u0000' in text) {
            return invalid("Descriptor is too large or contains binary data.")
        }
        val lines = text.removePrefix("\uFEFF").lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        return when (format) {
            "cue" -> cue(lines)
            "gdi" -> gdi(lines)
            "toc" -> toc(lines)
            "m3u", "m3u8" -> playlist(lines)
            else -> invalid("Unsupported descriptor format.")
        }
    }

    private fun cue(lines: List<String>): DescriptorReferences {
        val paths = mutableListOf<String>()
        var tracks = 0
        for (line in lines) {
            if (line.startsWith("FILE", ignoreCase = true) && line.getOrNull(4)?.isWhitespace() == true) {
                val match = cueFile.matchEntire(line) ?: return invalid("Invalid CUE FILE record.")
                paths += match.groupValues[1].ifEmpty { match.groupValues[2] }
            } else if (cueTrack.matches(line)) {
                if (paths.isEmpty()) return invalid("CUE track appears before a FILE record.")
                tracks++
            }
        }
        if (paths.isEmpty() || tracks == 0) return invalid("CUE needs file references and tracks.")
        return bounded(paths)
    }

    private fun gdi(lines: List<String>): DescriptorReferences {
        val count = lines.firstOrNull()?.toIntOrNull() ?: return invalid("Invalid GDI track count.")
        if (count !in 1..999 || lines.size != count + 1) return invalid("GDI track count does not match its records.")
        val paths = mutableListOf<String>()
        val numbers = mutableSetOf<Int>()
        for (line in lines.drop(1)) {
            val match = gdiTrack.matchEntire(line) ?: return invalid("Invalid GDI track record.")
            val number = match.groupValues[1].toIntOrNull() ?: return invalid("Invalid GDI track number.")
            if (number !in 1..count || !numbers.add(number)) return invalid("GDI track numbers must be unique and complete.")
            paths += match.groupValues[5].ifEmpty { match.groupValues[6] }
        }
        return bounded(paths)
    }

    private fun toc(lines: List<String>): DescriptorReferences {
        val records = lines.filter {
            it.startsWith("FILE ", true) || it.startsWith("DATAFILE ", true) || it.startsWith("AUDIOFILE ", true)
        }
        val paths = records.map { line ->
            tocFile.matchEntire(line)?.groupValues?.get(1) ?: return invalid("Unsupported TOC file reference syntax.")
        }
        if (paths.isEmpty()) return invalid("TOC has no file references.")
        return bounded(paths)
    }

    private fun playlist(lines: List<String>): DescriptorReferences {
        val paths = lines.filterNot { it.startsWith('#') }.map { line ->
            if (line.startsWith('"') && line.endsWith('"') && line.length > 1) line.substring(1, line.lastIndex) else line
        }
        if (paths.isEmpty()) return invalid("Playlist has no game references.")
        return bounded(paths)
    }

    private fun bounded(paths: List<String>): DescriptorReferences = when {
        paths.size > 999 -> invalid("Descriptor contains too many references.")
        paths.any { it.isBlank() } -> invalid("Descriptor contains an empty reference.")
        else -> DescriptorReferences(paths.distinct())
    }

    private fun invalid(message: String) = DescriptorReferences(emptyList(), message)
}
