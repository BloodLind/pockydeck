package dev.handheld.launcher.ui.presentation

import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.CatalogSourceId
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LibraryCategory
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.SupportedItemAction
import dev.handheld.launcher.core.domain.model.UnavailabilityReason
import dev.handheld.launcher.core.domain.model.UserItemOverrides
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RomTilePresentationTest {
    @Test
    fun `console metadata reaches Home badge and collection subtitle`() {
        val model = game("gba").toTileUiModel(UserItemOverrides(category = LibraryCategory.OTHER))
        assertEquals("Game Boy Advance", model.subtitle)
        assertEquals("GAME BOY ADVANCE", model.platformLabel)
        assertEquals("Play", model.primaryActionLabel)
        assertTrue(model.canOpen)
    }

    @Test
    fun `unassigned ROM remains openable for console and emulator chooser`() {
        val model = game(null).toTileUiModel()
        assertEquals("Unassigned console", model.subtitle)
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

    private fun game(platform: String?) = LibraryItem.RomGame(
        ItemId("rom:test"), "Test game", CatalogSourceId("source:test"),
        Availability.Available, setOf(SupportedItemAction.OPEN), platformId = platform, format = "rom",
    )
}
