package dev.handheld.launcher.core.domain.rom.scan

import org.junit.Assert.*
import org.junit.Test

class RomIdentificationTest {
    private val planner = RomScanPlanner()
    private val disc = RomDocument("disc", "Custom/Game.iso", sizeBytes=1024)

    @Test fun exactMetadataCanIdentifyAnOtherwiseAmbiguousFile() {
        val plan = planner.plan(RomScanRequest(listOf(disc), metadataPlatformCandidates=mapOf("disc" to setOf("ps2"))))
        assertEquals("ps2", plan.entries.single().platformId)
        assertTrue(plan.unresolved.isEmpty())
        assertTrue(planner.plan(RomScanRequest(listOf(disc))).entries.isEmpty())
    }

    @Test fun manualItemThenSourceAssignmentsOutrankMetadataAndNamedFolders() {
        val named = disc.copy(relativePath="PSP/Game.iso")
        val request = RomScanRequest(listOf(named), assignedPlatformId="ps2",
            documentPlatformOverrides=mapOf("disc" to "psx"), metadataPlatformCandidates=mapOf("disc" to setOf("wii", "gamecube")))
        assertEquals("psx", planner.plan(request).entries.single().platformId)
        assertEquals("ps2", planner.plan(request.copy(documentPlatformOverrides=emptyMap())).entries.single().platformId)
    }

    @Test fun ConflictingMetadataAndKnownFolderConflictsRequireAnAssignment() {
        val conflicted = planner.plan(RomScanRequest(listOf(disc), metadataPlatformCandidates=mapOf("disc" to setOf("psx", "ps2"))))
        assertTrue(conflicted.entries.isEmpty())
        assertFalse(conflicted.unresolved.single().requiresRepair)
        assertEquals(RomScanIssueCode.CONFLICTING_METADATA, conflicted.issues.single().code)
        val named = planner.plan(RomScanRequest(listOf(disc.copy(relativePath="PSP/Game.iso")),
            metadataPlatformCandidates=mapOf("disc" to setOf("ps2"))))
        assertTrue(named.entries.isEmpty())
        assertEquals(RomScanIssueCode.CONFLICTING_METADATA, named.issues.single().code)
    }

    @Test fun metadataCannotTurnAnUnsupportedFormatIntoARecognizedGame() {
        val plan = planner.plan(RomScanRequest(listOf(disc), metadataPlatformCandidates=mapOf("disc" to setOf("gba"))))
        assertTrue(plan.entries.isEmpty())
        assertEquals(RomScanIssueCode.PLATFORM_FORMAT_MISMATCH, plan.issues.single().code)
    }

    @Test fun itemAssignmentParticipatesInDescriptorGroupingRatherThanOnlyChangingItsCaption() {
        val cue = RomDocument("cue", "Unsorted/Game.cue", sizeBytes=120)
        val bin = RomDocument("bin", "Unsorted/Game.bin", sizeBytes=1024)
        val request = RomScanRequest(listOf(cue, bin), descriptorText=mapOf("cue" to "FILE \"Game.bin\" BINARY\nTRACK 01 MODE2/2352\nINDEX 01 00:00:00"),
            documentPlatformOverrides=mapOf("cue" to "psx"))
        val entry = planner.plan(request).entries.single()
        assertEquals("psx", entry.platformId)
        assertEquals(listOf("bin"), entry.companionDocumentIds)
        assertTrue(planner.plan(request).unresolved.isEmpty())
    }

    @Test fun arcadeSubtreeLookupDoesNotMixSimilarlyPrefixedSets() {
        val documents = (1..300).flatMap { number -> listOf(
            RomDocument("zip:$number", "arcade/game$number.zip", sizeBytes=20),
            RomDocument("chd:$number", "arcade/game$number/disc.chd", sizeBytes=20),
        ) }
        val plan = planner.plan(RomScanRequest(documents.reversed()))
        assertEquals(300, plan.entries.size)
        plan.entries.forEach { entry -> assertEquals(listOf(entry.documentId.replace("zip:", "chd:")), entry.companionDocumentIds) }
    }
}
