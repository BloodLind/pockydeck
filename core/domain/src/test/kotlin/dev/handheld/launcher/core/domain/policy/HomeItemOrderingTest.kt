package dev.handheld.launcher.core.domain.policy

import dev.handheld.launcher.core.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeItemOrderingTest {
    private fun app(id: String, title: String, game: Boolean = false) = ContractFixtures.androidApp(
        "example.$id", "example.$id.Main", title,
        category = if (game) LibraryCategory.GAME else LibraryCategory.OTHER,
    )

    @Test fun newestItemThenAndroidGamesThenRomsThenOtherApps() {
        val newest = app("last", "Z utility")
        val old = app("old", "B utility")
        val utility = app("alpha", "A utility")
        val gameZ = app("gamez", "Z game", true)
        val gameA = app("gamea", "A game", true)
        val romZ = ContractFixtures.rom("rom-z", "Z ROM")
        val romA = ContractFixtures.rom("rom-a", "A ROM")
        val history = listOf(SuccessfulOpenRecord(old.id, 8), SuccessfulOpenRecord(romZ.id, 9), SuccessfulOpenRecord(newest.id, 10))
        val result = HomeItemOrdering.order(listOf(old, romZ, gameZ, utility, newest, romA, gameA), history)
        assertEquals(listOf(newest, gameA, gameZ, romA, romZ, utility, old), result)
    }

    @Test fun newGameOrRomCanBeRecentWithoutDuplicationAndOverridesMoveAndroidGroups() {
        val native = app("native", "A native", true)
        val utility = app("utility", "Z utility")
        val rom = ContractFixtures.rom("rom", "A ROM")
        val overrides = mapOf(native.id to UserItemOverrides(category = LibraryCategory.OTHER),
            utility.id to UserItemOverrides(category = LibraryCategory.GAME))
        assertEquals(listOf(rom, utility, native), HomeItemOrdering.order(listOf(native, rom, utility),
            listOf(SuccessfulOpenRecord(rom.id, 10), SuccessfulOpenRecord(native.id, 9)), overrides))
        assertEquals(listOf(native, utility, rom), HomeItemOrdering.order(listOf(native, rom, utility),
            listOf(SuccessfulOpenRecord(native.id, 11)), overrides))
    }

    @Test fun absentRecentEntriesDoNotDisplaceCurrentGroupsAndNoArtificialHomeLimitRemains() {
        val native = app("game", "Z game", true)
        val rom = ContractFixtures.rom("rom", "Z ROM")
        val others = (1..20).map { app("app$it", "App ${it.toString().padStart(2, '0')}") }
        assertEquals(listOf(native, rom) + others, HomeItemOrdering.order(others.reversed() + rom + native,
            listOf(SuccessfulOpenRecord(ItemId("removed-game"), 99))))
    }
}
