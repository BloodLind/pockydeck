package dev.handheld.launcher.rom

import android.Manifest
import android.content.Context
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.handheld.launcher.core.data.rom.source.shared.SharedStoragePaths
import dev.handheld.launcher.core.data.rom.source.shared.SharedStorageVolume
import dev.handheld.launcher.core.domain.model.CatalogSourceId
import dev.handheld.launcher.core.domain.rom.RomSource
import dev.handheld.launcher.core.domain.rom.RomSourceAccessKind
import dev.handheld.launcher.core.domain.rom.RomSourceStatus
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Native descriptor/provider boundaries using isolated private fixtures, without broad permission. */
@RunWith(AndroidJUnit4::class)
class SharedRomDocumentsProviderInstrumentedTest {
    private lateinit var context: Context
    private lateinit var volume: File
    private lateinit var game: File
    private lateinit var source: RomSource
    private lateinit var access: SharedRomDocumentAccess
    private var granted = true
    private var registered = true
    private var mounted = true
    private var otherSources = emptyList<RomSource>()
    private val rootId = "primary:ROMs/NES"
    private val gameId = "$rootId/Game.nes"

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        volume = Files.createTempDirectory(context.noBackupFilesDir.toPath(), "shared-rom-test-").toFile().canonicalFile
        val directory = File(volume, "ROMs/NES").apply { mkdirs() }
        game = File(directory, "Game.nes").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        source = RomSource(CatalogSourceId("source:provider-test"), "content://${SharedRomDocumentsProvider.AUTHORITY}/tree/test", rootId,
            "NES", true, RomSourceStatus.READY, accessKind = RomSourceAccessKind.SHARED_STORAGE,
            physicalRootKey = rootId, automaticallyDiscovered = true)
        val paths = SharedStoragePaths({ granted }) {
            if (mounted) listOf(SharedStorageVolume("primary", volume, "Test volume")) else emptyList()
        }
        access = SharedRomDocumentAccess(paths) { if (registered) listOf(source) + otherSources else emptyList() }
    }

    @After fun tearDown() {
        if (::volume.isInitialized) {
            require(volume.canonicalFile.parentFile == context.noBackupFilesDir.canonicalFile)
            assertTrue("The isolated fixture is removed", volume.deleteRecursively())
        }
    }

    @Test fun nativeReadAndSiblingListingStayInsideRegisteredRoot() {
        File(game.parentFile, "Track.bin").writeBytes(byteArrayOf(7, 8))
        File(game.parentFile, ".hidden.nes").writeBytes(byteArrayOf(9))
        ParcelFileDescriptor.AutoCloseInputStream(access.open(gameId, "r", null)).use {
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), it.readBytes())
        }
        assertEquals(setOf(gameId, "$rootId/Track.bin"), access.children(rootId).map { it.first }.toSet())
        assertEquals(game.canonicalFile, access.resolve(gameId).file)
    }

    @Test fun writesTraversalAndUnregisteredFilesAreRejectedWithoutChangingGame() {
        File(volume, "Outside.nes").writeBytes(byteArrayOf(9))
        for (mode in listOf("w", "rw", "rwt", "wa")) denied { access.open(gameId, mode, null).close() }
        for (id in listOf("primary:Outside.nes", "$rootId/../../Outside.nes", "$rootId\\Game.nes")) {
            denied { access.resolve(id) }
        }
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), game.readBytes())
    }

    @Test fun removalPermissionLossAndUnmountApplyToEachNewRead() {
        registered = false
        denied { access.open(gameId, "r", null).close() }
        registered = true
        source = source.copy(enabled = false)
        denied { access.open(gameId, "r", null).close() }
        source = source.copy(enabled = true)
        granted = false
        denied { access.open(gameId, "r", null).close() }
        granted = true
        mounted = false
        denied { access.open(gameId, "r", null).close() }
        mounted = true
        assertEquals(game.canonicalFile, access.resolve(gameId).file)
    }

    @Test fun linksAndCancelledReadsCannotOpenAFile() {
        val outside = File(volume, "Outside.nes").apply { writeBytes(byteArrayOf(5)) }
        Files.createSymbolicLink(File(game.parentFile, "Linked.nes").toPath(), outside.toPath())
        denied { access.open("$rootId/Linked.nes", "r", null).close() }
        assertFalse(access.children(rootId).any { it.first.endsWith("Linked.nes") })
        try {
            access.open(gameId, "r", CancellationSignal().apply { cancel() }).close()
            fail("A cancelled read must not open the game")
        } catch (_: OperationCanceledException) { }
    }

    @Test fun parentTreeCannotBypassIndependentOrRemovedSubtreeExclusions() {
        val childId = "$rootId/SNES"
        val childGameId = "$childId/Game.sfc"
        val child = File(game.parentFile, "SNES").apply { mkdirs() }
        File(child, "Game.sfc").writeBytes(byteArrayOf(6))
        otherSources = listOf(source.copy(id = CatalogSourceId("source:child-test"), rootDocumentId = childId, physicalRootKey = childId))
        assertEquals(File(child, "Game.sfc").canonicalFile, access.resolve(childGameId).file)
        assertTrue(access.isChild(childId, childGameId))
        assertFalse("A broader tree grant cannot enter an independently registered child root", access.isChild(rootId, childGameId))
        assertFalse(access.children(rootId).any { it.first == childId })

        otherSources = otherSources.map { it.copy(enabled = false) }
        denied { access.open(childGameId, "r", null).close() }
        assertFalse(access.isChild(rootId, childGameId))
        otherSources = otherSources.map { it.copy(enabled = true, accessKind = RomSourceAccessKind.SAF, automaticallyDiscovered = false) }
        denied { access.open(childGameId, "r", null).close() }
    }

    @Test fun manifestRequiresExplicitDocumentGrantsAndPublishesNoRoot() {
        val info = context.packageManager.resolveContentProvider(SharedRomDocumentsProvider.AUTHORITY, 0)
        assertNotNull(info)
        assertEquals(Manifest.permission.MANAGE_DOCUMENTS, info!!.readPermission)
        assertEquals(Manifest.permission.MANAGE_DOCUMENTS, info.writePermission)
        assertTrue(info.grantUriPermissions)
        SharedRomDocumentsProvider().queryRoots(null).use { assertEquals(0, it.count) }
    }

    private fun denied(block: () -> Unit) {
        try { block(); fail("This access must be rejected") }
        catch (_: FileNotFoundException) { }
    }
}
