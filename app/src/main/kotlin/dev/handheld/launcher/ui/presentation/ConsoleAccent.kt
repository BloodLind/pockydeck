package dev.handheld.launcher.ui.presentation

import androidx.compose.ui.graphics.Color
import dev.handheld.launcher.core.domain.rom.scan.RomPlatforms
import java.util.Locale

/** Stable console accents supplement the written abbreviation; color never carries identity alone. */
object ConsoleAccent {
    private val accents = mapOf(
        "nes" to Color(0xFFFF8E8E), "snes" to Color(0xFFC0A5FF),
        "n64" to Color(0xFF86DDAA), "gb" to Color(0xFFCDDE86),
        "gbc" to Color(0xFF89E7C2), "gba" to Color(0xFFA7B2FF),
        "nds" to Color(0xFFFFABAA), "3ds" to Color(0xFFFF97B1),
        "gamecube" to Color(0xFFC5AEFF), "wii" to Color(0xFF99DEEE),
        "wiiu" to Color(0xFF83D1FF), "switch" to Color(0xFFFF9295),
        "psx" to Color(0xFFA6BEF7), "ps2" to Color(0xFF8DB4FF),
        "psp" to Color(0xFFC2BBFF), "psvita" to Color(0xFF9CD8FF),
        "megadrive" to Color(0xFFA7CBFF), "mastersystem" to Color(0xFFFFB39A),
        "saturn" to Color(0xFFB6B0FF), "dreamcast" to Color(0xFFFFB67C),
        "arcade" to Color(0xFFFFD57D), "neogeo" to Color(0xFFFFE394),
    )
    val android = Color(0xFFA4D87E)
    val unknown = Color(0xFFBAC2D2)

    fun forPlatform(platformId: String?): Color {
        val id = platformId?.trim()?.lowercase(Locale.ROOT) ?: return unknown
        if (RomPlatforms.byId(id) == null) return unknown
        return accents[id] ?: Color.hsl((id.hashCode().toLong() and 0x7fff_ffffL).rem(360).toFloat(), .65f, .76f)
    }
}

val TileUiModel.platformAccent: Color
    get() = if (platformId != null) ConsoleAccent.forPlatform(platformId)
        else if (platformLabel.startsWith("ANDROID")) ConsoleAccent.android else ConsoleAccent.unknown
