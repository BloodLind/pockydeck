package dev.handheld.launcher.ui.presentation

import androidx.compose.ui.graphics.luminance
import dev.handheld.launcher.core.domain.rom.scan.RomPlatforms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleAccentTest {
    @Test fun consoleTagsHaveStableReadableColorsAndUnknownValuesStayNeutral() {
        assertEquals(ConsoleAccent.forPlatform("gba"), ConsoleAccent.forPlatform(" GBA "))
        assertEquals(ConsoleAccent.unknown, ConsoleAccent.forPlatform(null))
        assertEquals(ConsoleAccent.unknown, ConsoleAccent.forPlatform("not-a-console"))
        assertEquals(5, listOf("nes", "snes", "gba", "psx", "dreamcast").map(ConsoleAccent::forPlatform).toSet().size)
        RomPlatforms.all.forEach { platform ->
            val accent = ConsoleAccent.forPlatform(platform.id)
            assertEquals(1f, accent.alpha, .0001f)
            assertTrue("${platform.id} label needs contrast on the dark tag", accent.luminance() > .3f)
        }
    }
}
