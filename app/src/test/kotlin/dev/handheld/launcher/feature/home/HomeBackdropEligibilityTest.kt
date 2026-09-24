package dev.handheld.launcher.feature.home

import dev.handheld.launcher.core.domain.model.*
import dev.handheld.launcher.ui.presentation.TileArtwork
import dev.handheld.launcher.ui.presentation.toTileUiModel
import org.junit.Assert.*
import org.junit.Test

class HomeBackdropEligibilityTest {
    @Test fun `ROM artwork is eligible even before identifying its console but custom Android icons are not`() {
        val rom = LibraryItem.RomGame(ItemId("unknown-rom"), "Game", CatalogSourceId("test"),
            Availability.Available, setOf(SupportedItemAction.OPEN), platformId = null, format = "bin")
        val model = rom.toTileUiModel()
        assertTrue(homeBackdropEligible(model))
        val custom = model.copy(artwork = TileArtwork.LocalReference(UserArtworkReference("/cover.png")))
        assertTrue(homeBackdropEligible(custom))
        assertFalse(homeBackdropEligible(custom.copy(isRom = false)))
        assertFalse(homeBackdropEligible(model.copy(artwork = TileArtwork.Fallback)))
        assertFalse(homeBackdropEligible(null))
    }
}
