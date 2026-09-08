package dev.handheld.launcher.core.data.rom.source.shared

import dev.handheld.launcher.core.domain.model.CatalogSourceId
import dev.handheld.launcher.core.domain.rom.RomSource
import dev.handheld.launcher.core.domain.rom.RomSourceAccessKind
import dev.handheld.launcher.core.domain.rom.RomSourceStatus
import dev.handheld.launcher.core.domain.rom.scan.RomScanPlanner
import dev.handheld.launcher.core.domain.rom.scan.RomScanRequest
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SharedStorageRomSourceAccessTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun enumerationFeedsActualDescriptorGroupingAndPreservesReadOnlyFiles() = runBlocking {
        val root = temporary.newFolder("volume").canonicalFile
        val folder = File(root,"PSX").apply { mkdir() }
        File(folder,"Disc.bin").writeBytes(ByteArray(24) { 3 })
        val cue = File(folder,"Disc.cue").apply { writeText("FILE \"Disc.bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\n") }
        File(folder,"Game.sav").writeBytes(byteArrayOf(1))
        val access = SharedStorageRomSourceAccess(paths(root))
        val enumeration = access.enumerate(source("PSX"))
        val plan = RomScanPlanner().plan(RomScanRequest(enumeration.documents,"PSX",descriptorText=enumeration.descriptorText))
        assertEquals(listOf("Disc.cue"),plan.entries.map { it.relativePath })
        assertEquals(listOf("primary:PSX/Disc.bin"),plan.entries.single().companionDocumentIds)
        assertTrue(enumeration.descriptorText.values.single().startsWith("FILE"))
        assertTrue(cue.readText().contains("TRACK 01"))
        assertEquals(24L,File(folder,"Disc.bin").length())
    }

    @Test fun manualSubtreeAndPrivateDirectoriesNeverEnterAutomaticEnumeration() = runBlocking {
        val root = temporary.newFolder("volume").canonicalFile
        val folder = File(root,"GBA").apply { mkdir() }
        File(folder,"Own.gba").writeBytes(byteArrayOf(1))
        File(folder,"Manual/Other.gba").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1)) }
        File(folder,"Android/data/Private.gba").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1)) }
        val enumeration = SharedStorageRomSourceAccess(paths(root)).enumerate(source("GBA").copy(
            excludedPhysicalRootKeys=setOf("primary:GBA/Manual"),
        ))
        assertEquals(listOf("Own.gba"),enumeration.documents.map { it.relativePath })
    }

    @Test fun interruptedDeepEnumerationThrowsInsteadOfPublishingPartialMissingResults() = runBlocking {
        val root = temporary.newFolder("volume").canonicalFile
        var folder = File(root,"GBA").apply { mkdir() }
        File(folder,"Existing.gba").writeBytes(byteArrayOf(1))
        repeat(50) { folder=File(folder,"d").apply { mkdir() } }
        val access = SharedStorageRomSourceAccess(paths(root))
        assertTrue(runCatching { access.enumerate(source("GBA")) }.exceptionOrNull() is IOException)
        assertTrue(access.isAvailable(source("GBA")))
        assertTrue(File(root,"GBA/Existing.gba").exists())
    }

    private fun paths(root: File) = SharedStoragePaths({ true }, { listOf(SharedStorageVolume("primary",root,"Internal")) })
    private fun source(name: String) = RomSource(CatalogSourceId("source:test"),"content://fixture/tree/test",
        "primary:$name",name,true,RomSourceStatus.NOT_SCANNED,accessKind=RomSourceAccessKind.SHARED_STORAGE,
        physicalRootKey="primary:$name",automaticallyDiscovered=true)
}
