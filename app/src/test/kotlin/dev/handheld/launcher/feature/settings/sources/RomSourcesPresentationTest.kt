package dev.handheld.launcher.feature.settings.sources

import dev.handheld.launcher.core.domain.model.CatalogSourceId
import dev.handheld.launcher.core.domain.rom.RomSource
import dev.handheld.launcher.core.domain.rom.RomSourceStatus
import dev.handheld.launcher.feature.settings.emulators.ConsoleEmulatorRow
import dev.handheld.launcher.feature.settings.emulators.consoleEmulatorSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class RomSourcesPresentationTest {
    @Test
    fun `failed scan and missing storage describe retained games without claiming completion`() {
        assertEquals("Scan incomplete · 4 games retained", sourceStatusLabel(source(RomSourceStatus.ERROR)))
        assertEquals("Folder unavailable · 4 games retained", sourceStatusLabel(source(RomSourceStatus.UNAVAILABLE)))
        assertEquals("Scanning… · 4 games indexed", sourceStatusLabel(source(RomSourceStatus.SCANNING)))
    }

    @Test
    fun `removed source explains reference retention`() {
        assertEquals("Removed · Favorites and history retained", sourceStatusLabel(source(RomSourceStatus.DISABLED).copy(enabled = false)))
    }

    @Test
    fun `games with no installed emulator remain an explicit console setting`() {
        assertEquals("4 games · No compatible app installed", consoleEmulatorSummary(
            ConsoleEmulatorRow("gba", "Game Boy Advance", 4, "Choose app", 0),
        ))
    }

    private fun source(status: RomSourceStatus) = RomSource(
        CatalogSourceId("source:test"), "content://test/tree/roms", "roms", "ROMs", true, status,
        gameCount = 4,
    )
}
