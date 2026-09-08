package dev.handheld.launcher.core.domain.rom.scan

import org.junit.Assert.*
import org.junit.Test

class PcGameShortcutTest {
    @Test fun exportedIdsAcceptPlainTextBomAndTrailingNewline() {
        assertEquals(220, PcGameShortcut.gameId("220"))
        assertEquals(219990, PcGameShortcut.gameId("\uFEFF219990\r\n"))
        assertEquals(10680, PcGameShortcut.gameId(" 10680 "))
    }

    @Test fun malformedIdsNeverBecomeCommandsOrOtherLaunchSources() {
        for (value in listOf("", "0", "-1", "2147483648", "220\n219990", "220;echo x", "steam://rungameid/220", "STEAM_220", "22 0", "1".repeat(129), "220" + "\u3000".repeat(42))) {
            assertNull(value, PcGameShortcut.gameId(value))
        }
    }

    @Test fun windowsAndSteamFoldersProduceOnlyValidatedGameShortcuts() {
        val documents = listOf("windows/Half-Life 2.steam", "steam/Grim Dawn.STEAM", "windows/Game/setup.exe", "windows/Game/data.zip", "windows/notes.txt")
            .map { RomDocument(it, it, sizeBytes = 12) }
        val plan = RomScanPlanner().plan(RomScanRequest(documents, descriptorText = mapOf(
            "windows/Half-Life 2.steam" to "220", "steam/Grim Dawn.STEAM" to "219990")))
        assertEquals(setOf("Half-Life 2", "Grim Dawn"), plan.entries.map { it.title }.toSet())
        assertTrue(plan.entries.all { it.platformId == "windows" && it.companionDocumentIds.isEmpty() })
        assertTrue(plan.unresolved.isEmpty())
        assertFalse(RomPlatforms.supports("windows", "zip"))
        assertFalse("windows" in RomPlatforms.candidatesFor("zip"))
        assertTrue(RomScanPlanner.needsDescriptorText("Game.steam"))
    }

    @Test fun brokenAndUnreadableShortcutsRequireRepairBeforeLaunching() {
        val documents = listOf("windows/Broken.steam", "windows/Unreadable.steam").map { RomDocument(it, it, sizeBytes = 10) }
        val plan = RomScanPlanner().plan(RomScanRequest(documents, descriptorText = mapOf("windows/Broken.steam" to "0")))
        assertTrue(plan.entries.isEmpty())
        assertEquals(2, plan.unresolved.size)
        assertTrue(plan.unresolved.all { it.requiresRepair })
    }

    @Test fun eachGameNativeSourceRetainsItsFormatDuringDiscovery() {
        val documents = PcGameShortcut.sources.keys.map { RomDocument(it, "windows/Game.$it", sizeBytes = 3) }
        val plan = RomScanPlanner().plan(RomScanRequest(documents, descriptorText = documents.associate { it.documentId to "123" }))
        assertEquals(PcGameShortcut.sources.keys, plan.entries.map { it.format }.toSet())
        assertTrue(plan.entries.all { it.platformId == "windows" })
    }
}
