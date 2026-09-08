package dev.handheld.launcher.rom

import dev.handheld.launcher.core.domain.rom.scan.RomDocument
import dev.handheld.launcher.core.domain.rom.scan.RomEntryKind
import dev.handheld.launcher.core.domain.rom.scan.RomScanPlan
import dev.handheld.launcher.core.domain.rom.scan.RomScanPlanner
import dev.handheld.launcher.core.domain.rom.scan.RomScanRequest
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RomArchiveSelectionTest {
    private val planner = RomScanPlanner()

    @Test fun multiGameCartridgeZipAlwaysPreparesAndRetainsEveryChoice() {
        val source = plan("NES/Collection.zip").entries.single()
        assertTrue(RomArchiveSelection.requiresPreparation(source.platformId, source.format))

        val extracted = plan("Game One.nes", "Game Two.nes", platform = source.platformId)
        val choices = RomArchiveSelection.selectableGames(extracted)
        assertEquals(listOf("Game One", "Game Two"), choices.map { it.title })
        assertEquals(2, choices.map { it.documentId }.distinct().size)
    }

    @Test fun sevenZAndTarCollectionsPrepareRegardlessOfEmulatorArchiveSupport() {
        val sources = plan("GBA/Collection.7z", "GBA/Collection.tar.gz", "GBA/Collection.tar.xz")
        assertEquals(3, sources.entries.size)
        assertTrue(sources.entries.all { RomArchiveSelection.requiresPreparation(it.platformId, it.format) })
        assertEquals(2, RomArchiveSelection.selectableGames(plan("One.gba", "Two.gba", platform = "gba")).size)
    }

    @Test fun arcadeAndDosContainersStayNativeIncludingArcadeChdCompanions() {
        val source = plan("NAOMI/ikaruga.zip", "NAOMI/ikaruga/disc.chd").entries.single()
        assertFalse(RomArchiveSelection.requiresPreparation(source.platformId, source.format))
        assertEquals(listOf("NAOMI/ikaruga/disc.chd"), source.companionDocumentIds)
        for (entry in plan("DOS/Game.zip", "DOS/Another.dosz", "Neo Geo/Collection.7z").entries) {
            assertTrue(RomArchiveSelection.preserveNativeContainer(entry.platformId, entry.format))
            assertFalse(RomArchiveSelection.requiresPreparation(entry.platformId, entry.format))
        }
    }

    @Test fun incompleteCueCannotTurnItsSurvivingTrackIntoAnAutomaticGame() {
        val extracted = plan("PSX/Game.cue", "PSX/Present.bin", texts = mapOf(
            "PSX/Game.cue" to "FILE Present.bin BINARY\nTRACK 01 MODE1/2352\nFILE Missing.bin BINARY\nTRACK 02 AUDIO",
        ))
        // The planner retains the file; only the archive launch policy can reject this partial set.
        assertEquals("PSX/Present.bin", extracted.entries.single().documentId)
        assertTrue(extracted.unresolved.single().requiresRepair)
        val failure = assertIoFailure { RomArchiveSelection.selectableGames(extracted) }
        assertTrue(failure.message.orEmpty().contains("Missing.bin"))
    }

    @Test fun damagedPlaylistBlocksArchiveSelectionEvenWhenAnotherGameIsValid() {
        val extracted = plan("PSX/Complete.chd", "PSX/Broken.m3u", texts = mapOf("PSX/Broken.m3u" to "Missing.chd"))
        assertEquals(1, extracted.entries.size)
        val failure = assertIoFailure { RomArchiveSelection.selectableGames(extracted) }
        assertTrue(failure.message.orEmpty().contains("Missing.chd"))
    }

    @Test fun completeDiscSetBecomesOneChoiceWithAllCompanions() {
        val extracted = plan("PSX/Game.m3u", "PSX/Disc 1.cue", "PSX/Disc 1.bin", "PSX/Disc 2.chd", texts = mapOf(
            "PSX/Game.m3u" to "Disc 1.cue\nDisc 2.chd",
            "PSX/Disc 1.cue" to "FILE \"Disc 1.bin\" BINARY\nTRACK 01 MODE2/2352",
        ))
        val game = RomArchiveSelection.selectableGames(extracted).single()
        assertEquals(RomEntryKind.PLAYLIST, game.kind)
        assertEquals("Game", game.title)
        assertEquals(listOf("PSX/Disc 1.cue", "PSX/Disc 1.bin", "PSX/Disc 2.chd"), game.companionDocumentIds)
    }

    @Test fun nonRepairClassificationQuestionsDoNotHideOtherwiseValidGames() {
        val extracted = plan("NES/Game.nes", "Unknown.iso")
        assertFalse(extracted.unresolved.single().requiresRepair)
        assertEquals("Game", RomArchiveSelection.selectableGames(extracted).single().title)
    }

    @Test fun nestedArchivesDoNotBecomeGameChoicesAndUnsupportedRarIsExplicit() {
        val extracted = plan("NES/Nested.zip")
        val failure = assertIoFailure { RomArchiveSelection.selectableGames(extracted) }
        assertTrue(failure.message.orEmpty().contains("Nested"))
        assertTrue(RomArchiveSelection.isArchive("rar"))
        assertFalse(RomArchiveSelection.requiresPreparation("nes", "rar"))
        assertFalse(RomArchiveSelection.requiresPreparation("gba", "gba"))
    }

    private fun plan(vararg paths: String, platform: String? = null, texts: Map<String, String> = emptyMap()): RomScanPlan =
        planner.plan(RomScanRequest(paths.map { RomDocument(it, it, sizeBytes = 1024) },
            assignedPlatformId = platform, descriptorText = texts))

    private fun assertIoFailure(action: () -> Unit): IOException = try {
        action()
        throw AssertionError("Expected damaged or empty archive rejection")
    } catch (failure: IOException) { failure }
}
