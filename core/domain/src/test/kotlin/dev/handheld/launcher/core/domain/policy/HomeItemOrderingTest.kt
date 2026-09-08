package dev.handheld.launcher.core.domain.policy

import dev.handheld.launcher.core.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeItemOrderingTest {
    private fun app(id: String, title: String, game: Boolean = false) = ContractFixtures.androidApp(
        "example.$id", "example.$id.Main", title,
        category = if (game) LibraryCategory.GAME else LibraryCategory.OTHER,
    )

    private fun rom(id: String, title: String, platform: String) = ContractFixtures.rom(id, title).copy(platformId = platform)

    @Test fun allAvailableHistoryLeadsTheShowcaseWithoutLosingEarlierGames() {
        val newest = app("last", "Z utility")
        val old = app("old", "B utility")
        val utility = app("alpha", "A utility")
        val gameZ = app("gamez", "Z game", true)
        val gameA = app("gamea", "A game", true)
        val psp = rom("psp", "Z PSP game", "psp")
        val anotherPsp = rom("psp-a", "A PSP game", "psp")
        val gba = rom("gba", "A GBA game", "gba")
        val history = listOf(SuccessfulOpenRecord(old.id, 8), SuccessfulOpenRecord(psp.id, 9),
            SuccessfulOpenRecord(newest.id, 10), SuccessfulOpenRecord(psp.id, 3), SuccessfulOpenRecord(ItemId("removed"), 99))
        val result = HomeItemOrdering.order(listOf(old, psp, gameZ, utility, newest, gba, gameA, anotherPsp), history)
        assertEquals(listOf(newest, psp, old, gameA, gba, utility), result)
    }

    @Test fun recentItemsFromTheSameConsoleAreAllKeptAndOverridesChooseTheAndroidShowcase() {
        val native = app("native", "A native", true)
        val utility = app("utility", "Z utility")
        val first = rom("rom-first", "A ROM", "gba")
        val second = rom("rom-second", "B ROM", "gba")
        val filler = rom("rom-filler", "C ROM", "gba")
        val overrides = mapOf(native.id to UserItemOverrides(category = LibraryCategory.OTHER),
            utility.id to UserItemOverrides(category = LibraryCategory.GAME))
        assertEquals(listOf(second, first, native, utility), HomeItemOrdering.order(listOf(native, first, second, filler, utility),
            listOf(SuccessfulOpenRecord(first.id, 10), SuccessfulOpenRecord(native.id, 9), SuccessfulOpenRecord(second.id, 11)), overrides))
    }

    @Test fun noHistoryUsesOneStableRepresentativePerConsoleRegardlessOfInputOrder() {
        val native = app("game", "Z game", true)
        val firstNative = app("firstgame", "A game", true)
        val gba = rom("gba", "Z GBA", "gba")
        val firstGba = rom("first-gba", "A GBA", "gba")
        val psp = rom("psp", "B PSP", "psp")
        val unavailable = firstGba.copy(id = ItemId("missing"), title = "0 Missing", availability = Availability.Unavailable(UnavailabilityReason.REMOVED))
        val others = (1..20).map { app("app$it", "App ${it.toString().padStart(2, '0')}") }
        val items = others + native + firstNative + gba + firstGba + psp + unavailable + ContractFixtures.systemAction("settings", "Settings")
        val expected = listOf(firstNative, firstGba, psp) + others
        assertEquals(expected, HomeItemOrdering.order(items, emptyList()))
        assertEquals(expected, HomeItemOrdering.order(items.reversed(), listOf(SuccessfulOpenRecord(unavailable.id, 99))))
    }

    @Test fun tiedHistoryAndShowcaseTitlesUseStableItemIdentity() {
        val one = rom("a", "Same", "gba")
        val two = rom("b", "Same", "gba")
        val records = listOf(SuccessfulOpenRecord(two.id, 7), SuccessfulOpenRecord(one.id, 7))
        assertEquals(listOf(one, two), HomeItemOrdering.order(listOf(two, one), records))
        assertEquals(listOf(one), HomeItemOrdering.order(listOf(two, one), emptyList()))
    }
}
