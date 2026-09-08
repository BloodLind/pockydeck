package dev.handheld.launcher.rom

import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the registered provider over ContentResolver using only private fixture copies. */
@RunWith(AndroidJUnit4::class)
class PreparedRomProviderInstrumentedTest {
    private lateinit var context: Context
    private lateinit var authority: String
    private lateinit var cacheRoot: File
    private val fixtureFolders = mutableListOf<File>()
    private lateinit var first: Fixture
    private lateinit var second: Fixture

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        authority = "${context.packageName}.romcache"
        cacheRoot = File(context.noBackupFilesDir, "rom-prepared").canonicalFile
        first = fixture()
        second = fixture()
    }

    @After fun tearDown() {
        fixtureFolders.forEach { directory ->
            require(directory.canonicalFile.parentFile == cacheRoot)
            directory.deleteRecursively()
        }
    }

    @Test fun registeredProviderIsGrantOnlyAndDoesNotAdvertiseCacheRoots() {
        @Suppress("DEPRECATION")
        val provider = requireNotNull(context.packageManager.resolveContentProvider(authority, 0))
        assertTrue(provider.exported)
        assertTrue(provider.grantUriPermissions)
        assertEquals("android.permission.MANAGE_DOCUMENTS", provider.readPermission)
        assertEquals("android.permission.MANAGE_DOCUMENTS", provider.writePermission)
        context.contentResolver.query(DocumentsContract.buildRootsUri(authority), null, null, null, null).use { cursor ->
            assertNotNull(cursor)
            assertEquals(0, cursor!!.count)
        }
    }

    @Test fun completedTreeListsCompanionsAndReadsExactBytesUsingCustomProjection() {
        val tree = first.tree
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, first.id)
        val names = mutableSetOf<String>()
        context.contentResolver.query(children, arrayOf(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_FLAGS), null, null, null).use { cursor ->
            assertNotNull(cursor)
            while (cursor!!.moveToNext()) {
                names += cursor.getString(0)
                assertEquals(0, cursor.getInt(1))
            }
        }
        assertEquals(setOf("Disc 1", "game.cue"), names)
        val rom = first.document("Disc 1/game.bin")
        context.contentResolver.query(rom, arrayOf(Document.COLUMN_SIZE, Document.COLUMN_DISPLAY_NAME), null, null, null).use { cursor ->
            assertTrue(cursor!!.moveToFirst())
            assertEquals(first.bytes.size.toLong(), cursor.getLong(0))
            assertEquals("game.bin", cursor.getString(1))
        }
        val actual = context.contentResolver.openInputStream(rom)!!.use { it.readBytes() }
        assertArrayEquals(first.bytes, actual)
        assertTrue(DocumentsContract.isChildDocument(context.contentResolver, first.document(), rom))
        assertFalse(DocumentsContract.isChildDocument(context.contentResolver, first.document(), first.document()))
    }

    @Test fun incompleteAndDeletedCopiesCannotBeOpened() {
        File(first.folder, ".complete").delete()
        denied { context.contentResolver.openInputStream(first.document("Disc 1/game.bin"))!!.close() }
        noDocument(first.document())
        File(second.folder, "content/Disc 1/game.bin").delete()
        denied { context.contentResolver.openInputStream(second.document("Disc 1/game.bin"))!!.close() }
    }

    @Test fun writeModesCannotModifyPreparedGames() {
        val rom = first.document("Disc 1/game.bin")
        listOf("w", "wt", "wa", "rw", "rwt").forEach { mode ->
            denied { context.contentResolver.openFileDescriptor(rom, mode)!!.close() }
        }
        assertArrayEquals(first.bytes, File(first.folder, "content/Disc 1/game.bin").readBytes())
    }

    @Test fun treeGrantCannotEscapeToSiblingCacheOrMetadata() {
        val escaped = DocumentsContract.buildDocumentUriUsingTree(first.tree, "${second.id}/Disc 1/game.bin")
        denied { context.contentResolver.openInputStream(escaped)!!.close() }
        listOf("../.complete", "Disc 1/../../.complete", "Disc 1/./game.bin", "Disc 1\\game.bin").forEach { path ->
            noDocument(first.document(path))
        }
        assertTrue(File(first.folder, ".complete").isFile)
        assertArrayEquals(second.bytes, File(second.folder, "content/Disc 1/game.bin").readBytes())
    }

    @Test fun cancelledReadDoesNotOpenDescriptor() {
        val signal = CancellationSignal().apply { cancel() }
        var cancelled = false
        try {
            context.contentResolver.openFileDescriptor(first.document("Disc 1/game.bin"), "r", signal)?.close()
        } catch (_: OperationCanceledException) { cancelled = true }
        assertTrue(cancelled)
    }

    private fun fixture(): Fixture {
        val key = UUID.randomUUID().toString().replace("-", "").repeat(2)
        val folder = File(cacheRoot, key).apply { mkdirs() }
        require(folder.canonicalFile.parentFile == cacheRoot)
        fixtureFolders += folder
        val content = File(folder, "content").apply { mkdirs() }
        val bytes = byteArrayOf(1, 3, 5, 7, 9, 11, 13)
        File(content, "Disc 1").mkdirs()
        File(content, "Disc 1/game.bin").writeBytes(bytes)
        File(content, "game.cue").writeText("FILE \"Disc 1/game.bin\" BINARY\n TRACK 01 MODE1/2352\n INDEX 01 00:00:00\n")
        File(folder, ".complete").writeText("1")
        val id = "cache:$key"
        return Fixture(folder, id, DocumentsContract.buildTreeDocumentUri(authority, id), bytes)
    }

    private data class Fixture(val folder: File, val id: String, val tree: Uri, val bytes: ByteArray) {
        fun document(path: String = ""): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, id + if (path.isEmpty()) "" else "/$path")
    }

    private fun denied(action: () -> Unit) {
        try {
            action()
            fail("Provider allowed an unavailable, escaping or writable document")
        } catch (_: FileNotFoundException) {
            // Local and Binder provider paths can report missing trees as either exception.
        } catch (_: SecurityException) { }
    }

    private fun noDocument(uri: Uri) {
        try {
            // DocumentsProvider catches FileNotFoundException during query and returns null.
            context.contentResolver.query(uri, null, null, null, null).use { cursor ->
                assertTrue("Unavailable document exposed metadata", cursor == null || cursor.count == 0)
            }
        } catch (_: FileNotFoundException) {
        } catch (_: SecurityException) { }
    }
}
