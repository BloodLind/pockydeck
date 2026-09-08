package dev.handheld.launcher.ui.presentation

import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.CatalogSourceId
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LibraryCategory
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.SupportedItemAction
import dev.handheld.launcher.core.domain.model.UnavailabilityReason
import dev.handheld.launcher.core.domain.model.UserItemOverrides
import dev.handheld.launcher.core.domain.rom.scan.RomPlatforms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RomTilePresentationTest {
    @Test
    fun `console metadata reaches Home badge and collection subtitle`() {
        val model = game("gba").toTileUiModel(UserItemOverrides(category = LibraryCategory.OTHER))
        assertEquals("GBA", model.subtitle)
        assertEquals("GBA", model.platformLabel)
        assertEquals("Play", model.primaryActionLabel)
        assertTrue(model.canOpen)
    }

    @Test
    fun `unassigned ROM remains openable for console and emulator chooser`() {
        val model = game(null).toTileUiModel()
        assertEquals("ROM", model.subtitle)
        assertTrue(model.canOpen)
        assertEquals("Reopen", game(null).toTileUiModel(recentlyOpened = true).primaryActionLabel)
    }

    @Test
    fun `unavailable storage still blocks direct activation`() {
        val model = game("psx").copy(
            availability = Availability.Unavailable(UnavailabilityReason.SOURCE_UNAVAILABLE),
        ).toTileUiModel()
        assertFalse(model.canOpen)
        assertTrue("Unavailable" in model.badges)
    }

    @Test
    fun `every supported platform has a compact caption in both card locations`() {
        assertEquals(56, RomPlatforms.all.size)
        RomPlatforms.all.forEach { platform ->
            val model = game(platform.id).toTileUiModel()
            assertEquals(platform.id, model.subtitle, model.platformLabel)
            assertTrue("Missing abbreviation for ${platform.id}", model.platformLabel != "ROM")
            assertTrue("Caption too long for ${platform.id}", model.platformLabel.length in 2..7)
        }
    }

    @Test
    fun `unknown platforms have a bounded fallback instead of exposing a long ID`() {
        val model = game("unrecognized-platform-with-a-long-name").toTileUiModel()
        assertEquals("ROM", model.platformLabel)
        assertEquals("ROM", model.subtitle)
        assertTrue(model.canOpen)
    }

    @Test
    fun `abbreviations distinguish similar handheld and disc platforms`() {
        val ids = listOf("gb", "gbc", "gba", "nds", "3ds", "psx", "ps2", "psp", "psvita", "pce", "pcecd")
        assertEquals(listOf("GB", "GBC", "GBA", "NDS", "3DS", "PSX", "PS2", "PSP", "PSV", "PCE", "PCE CD"),
            ids.map(RomPlatformLabels::shortLabel))
    }

    private fun game(platform: String?) = LibraryItem.RomGame(
        ItemId("rom:test"), "Test game", CatalogSourceId("source:test"),
        Availability.Available, setOf(SupportedItemAction.OPEN), platformId = platform, format = "rom",
    )
}
