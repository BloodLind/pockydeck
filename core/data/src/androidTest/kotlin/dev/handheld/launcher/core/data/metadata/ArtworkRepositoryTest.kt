package dev.handheld.launcher.core.data.metadata

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.handheld.launcher.core.data.rom.source.shared.SharedStoragePaths
import dev.handheld.launcher.core.data.rom.source.shared.SharedStorageVolume
import dev.handheld.launcher.core.domain.model.CatalogSourceId
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.rom.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ArtworkRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "artwork-test-${UUID.randomUUID()}"
    private lateinit var root: File
    private lateinit var database: ArtworkDatabase
    private val id = ItemId("rom:fixture")
    private val sourceId = CatalogSourceId("source:fixture")
    private val source = RomSource(sourceId, "content://fixture", "test:ROMs/gba", "gba", true, RomSourceStatus.READY,
        physicalRootKey = "test:ROMs/gba")
    private val entry = RomEntry(id, sourceId, "fixture", "content://fixture/game", "Game (USA).gba", "Game (USA)", "gba", "gba", true)
    private val roms = object : RomLibraryRepository {
        override val sources = MutableStateFlow(listOf(source))
        override val entries = MutableStateFlow(listOf(entry))
        override val consoleEmulatorDefaults = MutableStateFlow(emptyMap<String, String>())
        override val itemEmulatorOverrides = MutableStateFlow(emptyMap<ItemId, String>())
        override suspend fun findEntry(itemId: ItemId) = entry.takeIf { it.itemId == itemId }
        override suspend fun findSource(sourceId: CatalogSourceId) = source.takeIf { it.id == sourceId }
        override suspend fun addSource(treeUri: String, rootDocumentId: String, name: String): CatalogSourceId = error("unused")
        override suspend fun removeSource(sourceId: CatalogSourceId) = Unit
        override suspend fun setSourcePlatform(sourceId: CatalogSourceId, platformId: String?) = Unit
        override suspend fun setItemPlatform(itemId: ItemId, platformId: String?) = Unit
        override suspend fun setConsoleEmulator(platformId: String, emulatorId: String?) = Unit
        override suspend fun setItemEmulator(itemId: ItemId, emulatorId: String?) = Unit
    }

    @Before fun setup() {
        root = File(context.cacheDir, name).apply { mkdirs() }
        File(root, "ROMs/gba").mkdirs()
        database = Room.databaseBuilder(context, ArtworkDatabase::class.java, name).build()
    }
    @After fun cleanup() { database.close(); context.deleteDatabase(name); root.deleteRecursively() }

    private fun repository(http: ArtworkHttp): ArtworkRepository {
        val paths = SharedStoragePaths({ true }, { listOf(SharedStorageVolume("test", root, "Fixture")) })
        return ArtworkRepository(database, roms, EsDeArtworkResolver(paths), File(root, "cache/images"),
            LibretroArtworkProvider(File(root, "cache/indexes"), http)) { }
    }
    private fun png(): ByteArray {
        val bitmap = Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff4285f4.toInt()) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); bitmap.recycle() }.toByteArray()
    }

    @Test fun existingEsDeCoverWorksOfflineAndSurvivesRestart() = runBlocking {
        val cover = File(root, "ES-DE/downloaded_media/gba/covers/Game (USA).png")
        cover.parentFile!!.mkdirs(); cover.writeBytes(png())
        val repository = repository(ArtworkHttp { _, _ -> error("Local artwork must not use the network") })
        repository.request(id)
        assertNull(repository.process({ false }))
        assertEquals("ES-DE", database.artworkDao().find(id.value)?.provider)
        assertEquals(cover.canonicalFile, repository.file(database.artworkDao().find(id.value)!!))
        database.close()
        database = Room.databaseBuilder(context, ArtworkDatabase::class.java, name).build()
        assertEquals(ArtworkRecord.READY, database.artworkDao().find(id.value)?.state)
        assertTrue(cover.isFile)
    }

    @Test fun missingCoverDownloadsThenRemainsAvailableOffline() = runBlocking {
        var requests = 0
        val repository = repository(ArtworkHttp { url, _ ->
            requests++
            if (url.endsWith('/')) "<a href=\"Game%20%28USA%29.png\">x</a>".toByteArray() else png()
        })
        repository.request(id)
        assertNull(repository.process({ true }))
        val record = database.artworkDao().find(id.value)!!
        assertEquals(ArtworkRecord.READY, record.state)
        assertEquals("Libretro", record.provider)
        assertTrue(repository.file(record)!!.isFile)
        assertEquals(2, requests)
        assertNull(repository.process({ false }))
        assertEquals(2, requests)
        repository.retryMissing()
        assertEquals(ArtworkRecord.READY, database.artworkDao().find(id.value)?.state)
    }

    @Test fun pauseAndRetryStateRemainDurable() = runBlocking {
        val repository = repository(ArtworkHttp { _, _ -> throw ArtworkRequestFailure(120_000) })
        repository.request(id)
        repository.setPaused(true)
        assertNull(repository.process({ true }))
        assertEquals(0, database.artworkDao().find(id.value)?.attempts)
        repository.setPaused(false)
        assertTrue(repository.process({ true })!! >= 120_000)
        assertEquals(ArtworkRecord.RETRY, database.artworkDao().find(id.value)?.state)
        assertEquals(1, database.artworkDao().find(id.value)?.attempts)
        database.close()
        database = Room.databaseBuilder(context, ArtworkDatabase::class.java, name).build()
        assertEquals(1, database.artworkDao().find(id.value)?.attempts)
        assertTrue(database.artworkDao().find(id.value)!!.nextAttemptAt > System.currentTimeMillis())
        database.artworkDao().retryMissing()
        assertEquals(ArtworkRecord.QUEUED, database.artworkDao().find(id.value)?.state)
    }

    @Test fun rejectedLocalCoverFallsBackToDownloadWithoutOverwritingOriginal() = runBlocking {
        val cover = File(root, "ES-DE/downloaded_media/gba/covers/Game (USA).png")
        cover.parentFile!!.mkdirs(); cover.writeBytes(png())
        val original = cover.readBytes()
        val repository = repository(ArtworkHttp { url, _ ->
            if (url.endsWith('/')) "<a href=\"Game%20%28USA%29.png\">x</a>".toByteArray() else png()
        })
        repository.request(id)
        val local = database.artworkDao().find(id.value)!!
        assertEquals("ES-DE", local.provider)
        // Model the decoder rejecting a file after the initial bounds check.
        repository.invalidFile(id, local.fileReference)
        assertNull(repository.process({ true }))
        val replacement = database.artworkDao().find(id.value)!!
        assertEquals(ArtworkRecord.READY, replacement.state)
        assertEquals("Libretro", replacement.provider)
        assertTrue(repository.file(replacement)!!.isFile)
        assertArrayEquals(original, cover.readBytes())
    }
}
