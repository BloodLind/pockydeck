package dev.handheld.launcher.core.domain.rom.scan

/** GameNative/ES-DE exports contain one positive game ID; the extension identifies its source. */
object PcGameShortcut {
    const val MAX_BYTES = 128
    val sources = mapOf("steam" to "STEAM", "epic" to "EPIC", "gog" to "GOG", "amazon" to "AMAZON", "pcgame" to "CUSTOM_GAME")

    fun gameId(text: String): Int? {
        if (text.length > MAX_BYTES || text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return null
        val value = text.removePrefix("\uFEFF").trim()
        if (value.isEmpty() || value.length > 10 || value.any { it !in '0'..'9' }) return null
        return value.toIntOrNull()?.takeIf { it > 0 }
    }
}
