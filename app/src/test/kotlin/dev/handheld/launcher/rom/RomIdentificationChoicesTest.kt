package dev.handheld.launcher.rom

import dev.handheld.launcher.core.domain.model.CatalogSourceId
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.rom.RomEntry
import org.junit.Assert.*
import org.junit.Test

class RomIdentificationChoicesTest {
    private val source = CatalogSourceId("source:test")

    @Test fun assignedGamesLeaveBoundedChoicesSoLaterUnknownGamesRemainReachable() {
        val entries = (0..104).map { entry(it) }
        val first = RomIdentificationChoices.forSource(entries.reversed(),source)
        assertEquals(105,first.total)
        assertEquals(100,first.entries.size)
        val assigned = first.entries.map { it.itemId }.toSet()
        val next = RomIdentificationChoices.forSource(entries.map { if (it.itemId in assigned) it.copy(platformId="psx") else it },source)
        assertEquals(5,next.total)
        assertEquals(entries.takeLast(5).map { it.itemId },next.entries.map { it.itemId })
    }

    @Test fun choicesKeepInvalidConsoleIdsButExcludeRemovedOrOtherSourceEntries() {
        val entries = listOf(entry(0).copy(platformId="obsolete-console"),entry(1).copy(platformId="gba"),
            entry(2).copy(present=false),entry(3).copy(sourceId=CatalogSourceId("source:other")))
        assertEquals(listOf(ItemId("rom:0")),RomIdentificationChoices.forSource(entries,source).entries.map { it.itemId })
    }

    private fun entry(index: Int) = RomEntry(ItemId("rom:$index"),source,"doc:$index","content://fixture/$index",
        "Game ${index.toString().padStart(3,'0')}.iso","Game ${index.toString().padStart(3,'0')}",null,"iso",true)
}
