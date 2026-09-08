package dev.handheld.launcher.core.data.rom.source.shared

import dev.handheld.launcher.core.domain.model.CatalogSourceId
import dev.handheld.launcher.core.domain.rom.RomSource
import dev.handheld.launcher.core.domain.rom.RomSourceAccessKind
import dev.handheld.launcher.core.domain.rom.RomSourceStatus
import dev.handheld.launcher.core.domain.rom.scan.RomDocument
import dev.handheld.launcher.core.domain.rom.scan.RomPlatforms
import dev.handheld.launcher.core.domain.rom.scan.RomScanPlanner
import dev.handheld.launcher.core.domain.rom.scan.RomScanRequest
import org.junit.Assert.*
import org.junit.Test

class SharedDiscoveryPolicyTest {
    @Test fun sourceReservationsUseVolumeAndPathBoundariesIncludingDisabledRoots() {
        val manual = source("manual","primary:ROMs/GBA",automatic=false)
        val disabled = source("removed","abcd-1234:NES",enabled=false)
        assertTrue(SharedDiscoveryPolicy.coveredRoot("primary:ROMs/GBA/More",listOf(manual)))
        assertFalse(SharedDiscoveryPolicy.coveredRoot("primary:ROMs/GBA-backup",listOf(manual)))
        assertFalse(SharedDiscoveryPolicy.coveredRoot("other:ROMs/GBA",listOf(manual)))
        assertTrue(SharedDiscoveryPolicy.coveredRoot("ABCD-1234:NES",listOf(disabled)))
        assertTrue(SharedDiscoveryPolicy.coveredRoot("abcd-1234:NES/More",listOf(disabled)))
    }

    @Test fun manualAndIndependentAutomaticChildrenAreExcludedWithoutHidingSiblingGames() {
        val parent = source("parent","primary:ROMs")
        val manual = source("manual","primary:ROMs/GBA",automatic=false)
        val child = source("child","primary:ROMs/NES")
        val removed = source("removed","primary:ROMs/SNES",enabled=false)
        val sources = listOf(parent,manual,child,removed)
        assertEquals(setOf(manual.physicalRootKey,child.physicalRootKey,removed.physicalRootKey),SharedDiscoveryPolicy.exclusions(parent,sources))
        assertTrue(SharedDiscoveryPolicy.exclusions(child,sources).isEmpty())
        assertTrue(SharedDiscoveryPolicy.exclusions(manual,sources).isEmpty())
    }

    @Test fun disabledAncestorPreventsAutomaticChildResurrectionButExplicitManualAccessWins() {
        val removed = source("removed","primary:ROMs",enabled=false)
        val automatic = source("auto","primary:ROMs/GBA")
        val manual = automatic.copy(id=CatalogSourceId("manual"),automaticallyDiscovered=false)
        assertEquals(setOf("primary:ROMs"),SharedDiscoveryPolicy.exclusions(automatic,listOf(removed,automatic)))
        assertTrue(SharedDiscoveryPolicy.exclusions(manual,listOf(removed,manual)).isEmpty())
    }

    @Test fun consoleAliasNeedsAnActualRecognizedGameNotAnEmptyDirectoryOrSave() {
        val platform = requireNotNull(RomPlatforms.matchingFolder("GBA"))
        assertNull(RomPlatforms.matchingFolder("some-gba-backups"))
        fun recognized(vararg docs: RomDocument) = SharedDiscoveryPolicy.hasGames(
            RomScanPlanner().plan(RomScanRequest(docs.toList(),"GBA",platform.id)),platform.id,
        )
        assertFalse(recognized())
        assertFalse(recognized(RomDocument("empty","Empty.gba",sizeBytes=0)))
        assertFalse(recognized(RomDocument("save","Game.sav",sizeBytes=16)))
        assertTrue(recognized(RomDocument("game","Game.gba",sizeBytes=16)))
        assertTrue(recognized(RomDocument("zip","Game.zip",sizeBytes=16)))
    }

    private fun source(id: String, key: String, automatic: Boolean = true, enabled: Boolean = true) = RomSource(
        CatalogSourceId(id),"content://fixture/tree/$id",key,id,enabled,RomSourceStatus.NOT_SCANNED,
        accessKind=if (automatic) RomSourceAccessKind.SHARED_STORAGE else RomSourceAccessKind.SAF,
        physicalRootKey=key,automaticallyDiscovered=automatic,
    )
}
