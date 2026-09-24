package dev.handheld.launcher.ui.presentation

import dev.handheld.launcher.core.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class LastPlayedTest {
    private fun game(id: String, console: String?) = LibraryItem.RomGame(
        ItemId(id), id, CatalogSourceId("source:test"), Availability.Available,
        setOf(SupportedItemAction.OPEN), platformId = console, format = "iso")
    private fun open(id: String, order: Long) = SuccessfulOpenRecord(ItemId(id), order)

    @Test fun `latest successful dispatch wins independently per console regardless of input order`() {
        val items = listOf(game("ps2-a", "ps2"), game("ps2-b", "ps2"), game("gc", "gamecube"))
        val history = listOf(open("gc", 3), open("ps2-a", 1), open("ps2-b", 2))
        assertEquals(setOf(ItemId("ps2-b"), ItemId("gc")), lastPlayedByConsole(items, history))
        assertEquals(setOf(ItemId("ps2-a"), ItemId("gc")),
            lastPlayedByConsole(items, history.filterNot { it.itemId == ItemId("ps2-a") } + open("ps2-a", 4)))
    }

    @Test fun `never opened and unassigned games are not marked`() {
        val items = listOf(game("new", "ps2"), game("unknown", null))
        assertEquals(emptySet<ItemId>(), lastPlayedByConsole(items, listOf(open("unknown", 4), open("removed", 5))))
    }

    @Test fun `unavailable latest ROM does not incorrectly promote an older game`() {
        val older = game("old", "ps2")
        val recent = game("new", "ps2").copy(availability = Availability.Unavailable(UnavailabilityReason.SOURCE_UNAVAILABLE))
        assertEquals(setOf(recent.id), lastPlayedByConsole(listOf(older, recent), listOf(open("old", 1), open("new", 2))))
    }
}
