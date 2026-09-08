package dev.handheld.launcher.core.data.metadata

import dev.handheld.launcher.core.data.rom.source.shared.SharedStoragePaths
import dev.handheld.launcher.core.data.rom.source.shared.SharedStorageVolume
import dev.handheld.launcher.core.domain.model.CatalogSourceId
import dev.handheld.launcher.core.domain.rom.RomSource
import dev.handheld.launcher.core.domain.rom.RomSourceStatus
import dev.handheld.launcher.core.domain.rom.scan.RomDocument
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EsDeConsoleMetadataTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun importsOnlyKnownSystemIdsAndExactPathsWithoutTitleInference() {
        assertEquals(listOf(EsDeConsolePath("gba", "/storage/games")), EsDeConsoleMetadata.systems(
            "<systemList><system><name>gba</name><path>/storage/games</path></system><system><name>My favorite games</name><path>/other</path></system></systemList>"))
        assertTrue(EsDeConsoleMetadata.games("<gameList><game><name>Game Boy Advance</name></game></gameList>", "gba").isEmpty())
        assertEquals(listOf(EsDeConsolePath("psx", "./Game.cue")), EsDeConsoleMetadata.games(
            "<alternativeEmulator>DuckStation</alternativeEmulator><gameList><game><path>./Game.cue</path><name>Ignored title</name></game></gameList>", "psx"))
    }

    @Test fun importRejectsExternalEntitiesAmbiguousFieldsAndResourceAbuse() {
        for (xml in listOf(
            "<!DOCTYPE x [<!ENTITY file SYSTEM 'file:///private'>]><gameList><game><path>&file;</path></game></gameList>",
            "<gameList><game><path>A.iso</path><path>B.iso</path></game></gameList>",
            "<gameList><game><path>${"x".repeat(4097)}</path></game></gameList>",
            "<x>".repeat(35) + "</x>".repeat(35),
            "x".repeat(EsDeConsoleMetadata.MAX_BYTES + 1),
        )) assertTrue(runCatching { EsDeConsoleMetadata.games(xml,"psx") }.isFailure)
    }

    @Test fun metadataReferencesCannotEscapeOrExpandCommandsAndVariables() {
        val root = File("/storage/roms")
        for (path in listOf("../Game.iso", "nested/../../Game.iso", "https://example/game.iso", "file:///Game.iso", "%ROMPATH%/Game.iso", "C:\\Games\\Game.iso", "./../Game.iso")) {
            assertNull(path, EsDeConsoleMetadata.file(path,root))
        }
        assertEquals(File(root,"Sub/Game.iso"), EsDeConsoleMetadata.file("./Sub/Game.iso",root))
        assertNull(EsDeConsoleMetadata.file("Game.iso"))
    }

    @Test fun absoluteMetadataMatchesTheActualFileButNotADuplicateBasename() = runBlocking {
        val volume = temporary.newFolder("volume").canonicalFile
        val sourceRoot = File(volume,"Custom").apply { mkdir() }
        val actual = File(sourceRoot,"Game.iso").apply { writeBytes(byteArrayOf(1)) }
        File(sourceRoot,"Duplicate/Game.iso").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(2)) }
        gamelist(volume,"psx","<game><path>${actual.path.replace(File.separatorChar,'/')}</path></game>")
        val result = identifier(volume).identify(source(),listOf(doc("actual","Game.iso"),doc("other","Duplicate/Game.iso")))
        assertEquals(mapOf("actual" to setOf("psx")),result)
        assertTrue(actual.isFile)
    }

    @Test fun conflictingExactMetadataIsRetainedForPlannerResolutionAndPermissionIsRequired() = runBlocking {
        val volume = temporary.newFolder("volume").canonicalFile
        val actual = File(volume,"Custom/Game.iso").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1)) }
        val game = "<game><path>${actual.path.replace(File.separatorChar,'/')}</path></game>"
        gamelist(volume,"psx",game); gamelist(volume,"ps2",game)
        assertEquals(setOf("psx","ps2"),identifier(volume).identify(source(),listOf(doc("game","Game.iso")))["game"])
        val denied = EsDeRomMetadataIdentifier(SharedStoragePaths({ false }, { listOf(SharedStorageVolume("primary",volume,"Internal")) }))
        assertTrue(denied.identify(source(),listOf(doc("game","Game.iso"))).isEmpty())
    }

    @Test fun relativeGamelistNeedsAnExplicitValidatedSystemRootRatherThanAMatchingBasename() = runBlocking {
        val volume = temporary.newFolder("volume").canonicalFile
        val root = File(volume,"Custom").apply { mkdir() }
        File(root,"Game.iso").writeBytes(byteArrayOf(1))
        gamelist(volume,"psx","<game><path>./Game.iso</path><name>Game</name></game>")
        val identifier = identifier(volume)
        val documents = listOf(doc("game","Game.iso"))
        assertTrue(identifier.identify(source(),documents).isEmpty())
        File(volume,"ES-DE/custom_systems/es_systems.xml").apply {
            parentFile.mkdirs()
            writeText("<systemList><system><name>psx</name><path>${root.path.replace(File.separatorChar,'/')}</path></system></systemList>")
        }
        assertEquals(mapOf("game" to setOf("psx")),identifier.identify(source(),documents))
        assertTrue(File(root,"Game.iso").exists())
    }

    private fun gamelist(volume: File, console: String, body: String) = File(volume,"ES-DE/gamelists/$console/gamelist.xml").apply {
        parentFile.mkdirs(); writeText("<gameList>$body</gameList>")
    }
    private fun identifier(volume: File) = EsDeRomMetadataIdentifier(SharedStoragePaths({ true }, { listOf(SharedStorageVolume("primary",volume,"Internal")) }))
    private fun source() = RomSource(CatalogSourceId("source:test"),"content://fixture/tree/Custom","primary:Custom","Custom",true,RomSourceStatus.READY,physicalRootKey="primary:Custom")
    private fun doc(id: String, path: String) = RomDocument(id,path,sizeBytes=1)
}
