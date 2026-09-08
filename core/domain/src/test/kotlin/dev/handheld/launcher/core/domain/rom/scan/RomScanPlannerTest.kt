package dev.handheld.launcher.core.domain.rom.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomScanPlannerTest {
    private val planner = RomScanPlanner()

    @Test fun namedConsoleFoldersRequireActualGameFilesAndExcludeSupportFiles() {
        val plan = scan("Nintendo 64/", "PSP/", "NES/Adventure.NES", "NES/saves/Adventure.nes", "NES/disksys.rom",
            "PSX/scph5501.bin", "Wii/manuals/Instructions.iso", ".hidden/Unknown.gba", "PSP/notes.txt")
        assertEquals(listOf("nes"), plan.entries.map { it.platformId })
        assertEquals("Adventure", plan.entries.single().title)
        assertTrue(plan.unresolved.isEmpty())
    }

    @Test fun normalizedFolderAliasesRemainUsableWhenSeveralAliasesNormalizeIdentically() {
        assertEquals("psx", RomPlatforms.matchingFolder("Sony PlayStation")?.id)
        assertEquals("gb", RomPlatforms.matchingFolder("GAME-BOY")?.id)
        assertEquals("wiiu", RomPlatforms.matchingFolder("Nintendo Wii U")?.id)
        assertNull(RomPlatforms.matchingFolder("My Game Boy Games"))
        assertEquals("nkit.iso", RomPlatforms.formatOf("Game.NKIT.ISO"))
    }

    @Test fun sharedImageAndArchiveFormatsStaySelectableWithoutGuessingConsole() {
        val plan = scan("disc.iso", "compressed.chd", "Game.rvz", "track.bin", "game.zip", "game.7z", "portable.pbp", "cartridge.gba")
        assertEquals(listOf("gba"), plan.entries.map { it.platformId })
        assertEquals(7, plan.unresolved.size)
        assertTrue(plan.unresolved.all { !it.requiresRepair && it.candidatePlatformIds.size > 1 })
    }

    @Test fun genericSuffixesNeedConsoleContextEvenWithOneRegistryCandidate() {
        val plan = scan("backup.gz", "installer.pkg", "startup.bat", "editor.conf", "level.wad")
        assertTrue(plan.entries.isEmpty())
        assertEquals(5, plan.unresolved.size)
        assertTrue(plan.unresolved.none { it.requiresRepair })
        assertEquals("wii", scan("Wii/Channel.wad").entries.single().platformId)
    }

    @Test fun nearestFolderAndExplicitOverridesResolveAmbiguityWithoutRenamingFiles() {
        val documents = listOf(doc("PSX/Game/disc.iso"), doc("PSP/Game/disc.iso"), doc("Unsorted/Game/disc.iso"))
        val request = RomScanRequest(documents, folderPlatformOverrides = mapOf("Unsorted" to "ps2"))
        assertEquals(listOf("psp", "psx", "ps2"), planner.plan(request).entries.map { it.platformId })
        assertEquals(listOf("saturn", "saturn", "ps2"), planner.plan(request.copy(assignedPlatformId = "saturn")).entries.map { it.platformId })
        assertEquals("psp", planner.plan(RomScanRequest(listOf(doc("Game.iso")), rootDisplayName = "PSP")).entries.single().platformId)
    }

    @Test fun conflictingConsoleAndFormatRemainCorrectable() {
        val plan = scan("PSX/Pocket.gba")
        assertTrue(plan.entries.isEmpty())
        assertEquals(setOf("gba"), plan.unresolved.single().candidatePlatformIds)
        assertFalse(plan.unresolved.single().requiresRepair)
        assertTrue(plan.issues.any { it.code == RomScanIssueCode.PLATFORM_FORMAT_MISMATCH })
    }

    @Test fun validatedCueCollapsesDataAndAudioTracksIntoOneGame() {
        val cue = "FILE \"Track 01.BIN\" BINARY\n TRACK 01 MODE2/2352\n INDEX 01 00:00:00\nFILE \"Track 02.wav\" WAVE\n TRACK 02 AUDIO\n INDEX 01 00:00:00"
        val plan = scan("PSX/Game.cue", "PSX/Track 01.BIN", "PSX/Track 02.wav", texts = mapOf("PSX/Game.cue" to cue))
        val game = plan.entries.single()
        assertEquals(RomEntryKind.DISC_DESCRIPTOR, game.kind)
        assertEquals(listOf("PSX/Track 01.BIN", "PSX/Track 02.wav"), game.companionDocumentIds)
        assertTrue(plan.unresolved.isEmpty())
    }

    @Test fun gdiRecognizesDreamcastAndSuppressesItsQuotedTracks() {
        val gdi = "2\n1 0 4 2352 \"Track 01.bin\" 0\n2 45000 0 2352 track02.raw 0"
        val plan = scan("Game.gdi", "Track 01.bin", "track02.raw", texts = mapOf("Game.gdi" to gdi))
        assertEquals("dreamcast", plan.entries.single().platformId)
        assertEquals(2, plan.entries.single().companionDocumentIds.size)
        assertTrue(plan.unresolved.isEmpty())
    }

    @Test fun playlistIncludesTransitiveCueTracksAndPreservesDiscOrder() {
        val plan = scan("PSX/Game.m3u", "PSX/Disc 1.cue", "PSX/Disc 1.bin", "PSX/Disc 2.chd", texts = mapOf(
            "PSX/Game.m3u" to "\uFEFF#EXTM3U\nDisc 1.cue\nDisc 2.chd\n",
            "PSX/Disc 1.cue" to "FILE \"Disc 1.bin\" BINARY\nTRACK 01 MODE2/2352"))
        assertEquals("Game", plan.entries.single().title)
        assertEquals(RomEntryKind.PLAYLIST, plan.entries.single().kind)
        assertEquals(listOf("PSX/Disc 1.cue", "PSX/Disc 1.bin", "PSX/Disc 2.chd"), plan.entries.single().companionDocumentIds)
    }

    @Test fun unassignedValidCueStillGroupsTracksAndCanBeAssignedWithoutRepair() {
        val plan = scan("Game.cue", "Track.bin", texts = mapOf("Game.cue" to "FILE Track.bin BINARY\nTRACK 01 MODE1/2352"))
        assertTrue(plan.entries.isEmpty())
        assertEquals("Game.cue", plan.unresolved.single().documentId)
        assertEquals(listOf("Track.bin"), plan.unresolved.single().companionDocumentIds)
        assertFalse(plan.unresolved.single().requiresRepair)
    }

    @Test fun incompleteCueDoesNotSuppressOtherFilesAndNeedsRepairBeforeLaunch() {
        val plan = scan("PSX/Game.cue", "PSX/Present.bin", texts = mapOf("PSX/Game.cue" to
            "FILE Present.bin BINARY\nTRACK 01 MODE1/2352\nFILE Missing.bin BINARY\nTRACK 02 MODE1/2352"))
        assertEquals("PSX/Present.bin", plan.entries.single().documentId)
        assertTrue(plan.unresolved.single().requiresRepair)
        assertTrue(plan.unresolved.single().companionDocumentIds.isEmpty())
        assertTrue(plan.issues.any { it.code == RomScanIssueCode.MISSING_COMPANION })
    }

    @Test fun unreadableDescriptorIsRecoverableAndNeverMistakenForStandaloneRom() {
        val plan = scan("PSX/Game.cue")
        assertTrue(plan.entries.isEmpty())
        assertTrue(plan.unresolved.single().requiresRepair)
        assertEquals(RomScanIssueCode.MISSING_DESCRIPTOR_TEXT, plan.issues.single().code)
    }

    @Test fun referencesMayUseParentDirectoriesWithinSourceButNeverEscapeOrUseUrls() {
        val valid = scan("PSX/Lists/Game.m3u", "PSX/Disc.chd", texts = mapOf("PSX/Lists/Game.m3u" to "..\\Disc.chd"))
        assertEquals(1, valid.entries.size)
        for (reference in listOf("../../../outside.iso", "/storage/secret.iso", "C:\\secret.iso", "content://provider/document/rom", "https://host/game.iso")) {
            val invalid = scan("PSX/Lists/Game.m3u", texts = mapOf("PSX/Lists/Game.m3u" to reference))
            assertTrue(reference, invalid.unresolved.single().requiresRepair)
            assertEquals(reference, RomScanIssueCode.INVALID_DESCRIPTOR, invalid.issues.single().code)
        }
    }

    @Test fun cyclicAndSelfReferencingPlaylistsDoNotOverflowOrHideEachOther() {
        val plan = scan("PSX/A.m3u", "PSX/B.m3u", texts = mapOf("PSX/A.m3u" to "B.m3u", "PSX/B.m3u" to "A.m3u"))
        assertEquals(2, plan.unresolved.size)
        assertTrue(plan.unresolved.all { it.requiresRepair })
        assertTrue(plan.issues.any { it.code == RomScanIssueCode.CYCLIC_PLAYLIST })
        val self = scan("PSX/Game.m3u", texts = mapOf("PSX/Game.m3u" to "Game.m3u"))
        assertTrue(self.unresolved.single().requiresRepair)
    }

    @Test fun mixedConsolePlaylistIsNotTreatedAsOneGame() {
        val plan = scan("All.m3u", "PSX/Disc.chd", "PSP/Disc.iso", texts = mapOf("All.m3u" to "PSX/Disc.chd\nPSP/Disc.iso"))
        assertEquals(2, plan.entries.size)
        assertTrue(plan.unresolved.single().requiresRepair)
        assertTrue(plan.issues.any { it.code == RomScanIssueCode.CROSS_PLATFORM_PLAYLIST })
    }

    @Test fun caseInsensitiveReferenceFallbackMustBeUnique() {
        val plan = scan("PSX/Game.cue", "PSX/track.bin", "PSX/TRACK.BIN", texts = mapOf("PSX/Game.cue" to
            "FILE Track.Bin BINARY\nTRACK 01 MODE1/2352"))
        assertEquals(2, plan.entries.size)
        assertTrue(plan.unresolved.single().requiresRepair)
        assertEquals(RomScanIssueCode.AMBIGUOUS_COMPANION, plan.issues.single().code)
        val exact = scan("PSX/Game.cue", "PSX/track.bin", texts = mapOf("PSX/Game.cue" to "FILE TRACK.BIN BINARY\nTRACK 01 MODE1/2352"))
        assertEquals(1, exact.entries.size)
    }

    @Test fun oversizedBinaryAndMalformedDescriptorsRemainRepairable() {
        val cases = listOf("PSX/Game.cue" to "X".repeat(MAX_DESCRIPTOR_BYTES + 1),
            "PSX/Game.cue" to "FILE track.bin BINARY\n\u0000", "Game.gdi" to "2\n1 0 4 2352 track.bin 0")
        cases.forEach { (name, text) ->
            val plan = scan(name, "track.bin", texts = mapOf(name to text))
            assertTrue(plan.unresolved.first { it.documentId == name }.requiresRepair)
            assertTrue(plan.issues.any { it.code == RomScanIssueCode.INVALID_DESCRIPTOR })
        }
    }

    @Test fun conflictingProviderMetadataIsReportedRatherThanArbitrarilyChosen() {
        val plan = planner.plan(RomScanRequest(listOf(doc("game.nes", id = "a"), doc("game.nes", id = "b"),
            doc("other.gba", id = "duplicate"), doc("different.gba", id = "duplicate"), doc("../outside.gba"))))
        assertTrue(plan.entries.isEmpty())
        assertTrue(plan.issues.any { it.code == RomScanIssueCode.DUPLICATE_DOCUMENT_PATH })
        assertTrue(plan.issues.any { it.code == RomScanIssueCode.INVALID_DOCUMENT_PATH })
    }

    @Test fun zeroByteFileDoesNotCreateConsoleButUnknownSizeCanBeIndexed() {
        val plan = planner.plan(RomScanRequest(listOf(doc("PSP/Empty.iso").copy(sizeBytes = 0), doc("NES/Game.nes").copy(sizeBytes = null))))
        assertEquals(listOf("nes"), plan.entries.map { it.platformId })
        assertEquals(RomScanIssueCode.EMPTY_FILE, plan.issues.single().code)
    }

    @Test fun pspEbootUsesGameFolderTitleAndRetainsOtherIndependentGames() {
        val plan = scan("PSP/My Game/EBOOT.PBP", "PSP/My Game/module.prx", "PSP/My Game/ICON0.PNG", "PSP/My Game/Other.iso")
        assertEquals(2, plan.entries.size)
        val game = plan.entries.first { it.format == "pbp" }
        assertEquals("My Game", game.title)
        assertEquals(RomEntryKind.FOLDER_PACKAGE, game.kind)
        assertEquals(setOf("PSP/My Game/module.prx", "PSP/My Game/ICON0.PNG"), game.companionDocumentIds.toSet())
    }

    @Test fun wiiUCodeContentAndMetaFormOnePackageWithAllDependencies() {
        val plan = scan("Wii U/Adventure/code/game.rpx", "Wii U/Adventure/code/library.rpl",
            "Wii U/Adventure/content/data.bin", "Wii U/Adventure/meta/meta.xml")
        assertTrue(plan.unresolved.isEmpty())
        assertEquals("Adventure", plan.entries.single().title)
        assertEquals(RomEntryKind.FOLDER_PACKAGE, plan.entries.single().kind)
        assertEquals(3, plan.entries.single().companionDocumentIds.size)
    }

    @Test fun incompleteOrMultipleExecutableWiiUFolderNeedsRepair() {
        val incomplete = scan("Wii U/Adventure/code/game.rpx")
        assertTrue(incomplete.unresolved.single().requiresRepair)
        val multiple = scan("Wii U/Adventure/code/a.rpx", "Wii U/Adventure/code/b.rpx",
            "Wii U/Adventure/content/data.dat", "Wii U/Adventure/meta/meta.xml")
        assertTrue(multiple.entries.isEmpty())
        assertTrue(multiple.unresolved.filter { it.format == "rpx" }.all { it.requiresRepair })
    }

    @Test fun arcadeArchiveOwnsMatchingChdSubdirectoryButNotOtherGamesOrBios() {
        val plan = scan("NAOMI/ikaruga.zip", "NAOMI/ikaruga/gdl-0010.chd", "NAOMI/other.zip", "NAOMI/naomi.zip")
        assertEquals(2, plan.entries.size)
        assertEquals(listOf("NAOMI/ikaruga/gdl-0010.chd"), plan.entries.first().companionDocumentIds)
        assertTrue(plan.entries.all { it.kind == RomEntryKind.ARCHIVE })
    }

    @Test fun cloneCdAndMdsRequireAndGroupTheirCompanionImages() {
        val plan = scan("PSX/Game.ccd", "PSX/Game.img", "PSX/Game.sub", "Saturn/Another.mds", "Saturn/Another.mdf")
        assertEquals(2, plan.entries.size)
        assertTrue(plan.entries.all { it.kind == RomEntryKind.DISC_DESCRIPTOR })
        assertEquals(listOf(2, 1), plan.entries.map { it.companionDocumentIds.size })
        assertTrue(scan("PSX/Incomplete.ccd").unresolved.single().requiresRepair)
    }

    @Test fun archiveMetadataIsOpaqueAndExtractedMultipleGamesRemainMultipleChoices() {
        val archive = scan("NES/Collection.zip")
        assertEquals(RomEntryKind.ARCHIVE, archive.entries.single().kind)
        val extracted = planner.plan(RomScanRequest(listOf(doc("Game One.nes"), doc("Game Two.nes")), assignedPlatformId = "nes"))
        assertEquals(2, extracted.entries.size)
        assertEquals(listOf("Game One", "Game Two"), extracted.entries.map { it.title })
    }

    @Test fun genericCompressionAndUnsupportedRarStayDiscoverableWithLongestSuffix() {
        val plan = scan("GBA/Game.gba.gz", "Nintendo DS/Game.tar.gz", "SNES/Game.sfc.xz", "NES/Game.nes.bz2", "NES/Game.rar", "DOS/Game.dosz")
        assertEquals(6, plan.entries.size)
        assertTrue(plan.entries.all { it.kind == RomEntryKind.ARCHIVE })
        assertEquals("tar.gz", plan.entries.first { it.platformId == "nds" }.format)
        assertEquals("Game", plan.entries.first { it.platformId == "nds" }.title)
        assertTrue(plan.entries.any { it.format == "rar" })
        assertTrue(scan("Game.tar.xz", "Game.rar").unresolved.all { !it.requiresRepair && it.candidatePlatformIds.size > 1 })
    }

    @Test fun arbitraryDataDirectoryIsNotGuessedToBeScummvmGame() {
        assertTrue(scan("ScummVM/Game/resource.000", "ScummVM/Game/README.txt").entries.isEmpty())
        assertEquals("scummvm", scan("ScummVM/Game/Game.scummvm").entries.single().platformId)
    }

    @Test fun duplicateDisplayNamesRetainOpaqueDocumentIdentitiesAndStableOrdering() {
        val first = doc("NES/A/Game.nes", "opaque:a")
        val second = doc("NES/B/Game.nes", "opaque:b")
        val a = planner.plan(RomScanRequest(listOf(first, second)))
        val b = planner.plan(RomScanRequest(listOf(second, first)))
        assertEquals(a, b)
        assertEquals(listOf("opaque:a", "opaque:b"), a.entries.map { it.documentId })
        assertEquals(listOf("Game", "Game"), a.entries.map { it.title })
    }

    @Test fun registryIncludesCommonConsoleFamiliesAndEmulatorCompressionFormats() {
        for (id in listOf("nes", "snes", "n64", "gb", "gbc", "gba", "virtualboy", "nds", "3ds", "gamecube", "wii", "wiiu", "switch",
            "psx", "ps2", "psp", "psvita", "sg1000", "mastersystem", "megadrive", "gamegear", "segacd", "sega32x", "saturn", "dreamcast",
            "naomi", "atomiswave", "arcade", "neogeo", "neogeocd", "pce", "pcecd", "supergrafx", "atari2600", "atari5200", "atari7800",
            "atari800", "atarist", "atarilynx", "atarijaguar", "wonderswan", "wonderswancolor", "neogeopocket", "neogeopocketcolor", "colecovision",
            "intellivision", "vectrex", "3do", "amiga", "c64", "zxspectrum", "amstradcpc", "msx", "msx2", "dos", "scummvm")) {
            assertNotNull(id, RomPlatforms.byId(id))
        }
        for ((platform, format) in listOf("gamecube" to "rvz", "psp" to "chd", "ps2" to "zso", "wiiu" to "wua", "amiga" to "lha", "dos" to "dosz")) {
            assertTrue("$platform/$format", RomPlatforms.supports(platform, format))
        }
        assertTrue(RomScanPlanner.needsDescriptorText("GAME.M3U8"))
        assertFalse(needsDescriptorText("Game.chd"))
        assertEquals(262_144, RomScanPlanner.MAX_DESCRIPTOR_BYTES)
    }

    private fun scan(vararg paths: String, texts: Map<String, String> = emptyMap()): RomScanPlan =
        planner.plan(RomScanRequest(paths.map { doc(it) }, descriptorText = texts))

    private fun doc(path: String, id: String = path): RomDocument =
        RomDocument(id, path.removeSuffix("/"), isDirectory = path.endsWith('/'), sizeBytes = if (path.endsWith('/')) null else 1_024)
}
