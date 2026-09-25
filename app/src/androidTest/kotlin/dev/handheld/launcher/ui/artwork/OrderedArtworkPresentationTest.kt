package dev.handheld.launcher.ui.artwork

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.handheld.launcher.core.domain.model.*
import dev.handheld.launcher.di.LauncherApplication
import dev.handheld.launcher.feature.home.HomeArtworkBuffer
import dev.handheld.launcher.ui.artwork.enriched.*
import dev.handheld.launcher.ui.artwork.local.AndroidIconLoader
import dev.handheld.launcher.ui.presentation.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import dev.handheld.launcher.core.data.metadata.*
import dev.handheld.launcher.core.data.rom.source.shared.SharedStoragePaths
import dev.handheld.launcher.core.data.rom.source.shared.SharedStorageVolume
import dev.handheld.launcher.core.domain.rom.RomEntry
import dev.handheld.launcher.core.domain.rom.RomLibraryRepository
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Local image fixtures only; reversed composition deliberately competes with display order. */
class OrderedArtworkPresentationTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun loader() = EnrichedArtworkLoader(context,
        (context.applicationContext as LauncherApplication).appContainer.artworkRepository)
    private fun image(index: Int): File {
        val file = File.createTempFile("ordered-cover-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(if (index == 0) 1024 else 192, 192, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF8844BB.toInt() + index * 16)
        try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
        return file
    }
    private fun model(index: Int, file: File) = TileUiModel(ItemId("ordered:$index"), "Image $index", null,
        "Game", "PS2", "Play", emptyList(), Availability.Available, emptySet(),
        TileArtwork.LocalReference(UserArtworkReference(file.path)))

    @Test fun readyRomCoversAppearInOrderWhileRequestBookkeepingIsStillBlocked() {
        val files = List(4, ::image)
        val models = files.mapIndexed(::model).map { it.copy(artwork = TileArtwork.Rom) }
        val database = Room.inMemoryDatabaseBuilder(context, ArtworkDatabase::class.java).build()
        val releaseRequests = CompletableDeferred<Unit>()
        val requests = AtomicInteger()
        val container = (context.applicationContext as LauncherApplication).appContainer
        // Only findEntry is used, and it deliberately never touches the real catalog.
        val roms = object : RomLibraryRepository by container.romRepository {
            override suspend fun findEntry(itemId: ItemId): RomEntry? {
                requests.incrementAndGet()
                releaseRequests.await()
                return null
            }
        }
        val root = File(context.cacheDir, "ordered-ready-${System.nanoTime()}").apply { mkdirs() }
        val paths = SharedStoragePaths({ true }, { listOf(SharedStorageVolume("fixture", context.cacheDir, "Fixture")) })
        val repository = ArtworkRepository(database, roms, EsDeArtworkResolver(paths), root,
            LibretroArtworkProvider(root, ArtworkHttp { _, _ -> error("No network in fixture") })) { }
        val loader = EnrichedArtworkLoader(context, repository)
        val shown = mutableListOf<Int>()
        try {
            runBlocking {
                models.forEachIndexed { index, model -> database.artworkDao().put(ArtworkRecord(
                    model.itemId.value, model.title, "ps2", state = ArtworkRecord.READY,
                    provider = "ES-DE", fileReference = files[index].canonicalPath)) }
                models.forEach { model ->
                    assertNotNull("The fixture must expose readable artwork",
                        repository.file(requireNotNull(database.artworkDao().find(model.itemId.value))))
                }
            }
            compose.setContent {
                CompositionLocalProvider(LocalEnrichedArtworkLoader provides loader) {
                    val order = rememberArtworkLoadOrder(models.map { it.itemId })
                    CompositionLocalProvider(LocalArtworkLoadOrder provides order) {
                        models.reversed().forEach { model -> key(model.itemId) {
                            val art = rememberEnrichedArtwork(model, targetSizePx = 256)
                            SideEffect {
                                val index = models.indexOf(model)
                                if (art.painter != null && index !in shown) shown += index
                            }
                        } }
                    }
                }
            }
            compose.waitUntil(5_000) { shown.size == models.size }
            assertEquals(models.indices.toList(), shown)
            assertFalse("Covers must not wait for request bookkeeping", releaseRequests.isCompleted)
            assertEquals(models.size, requests.get())
        } finally {
            compose.runOnUiThread { loader.setForeground(false); loader.trimMemory(true) }
            releaseRequests.complete(Unit)
            compose.waitForIdle()
            database.close()
            files.forEach(File::delete)
            root.delete()
        }
    }

    @Test fun reverseCompositionAndHomePreloadsStillRevealFreshCoversInDisplayOrder() {
        val files = List(8, ::image)
        val models = files.mapIndexed(::model)
        val loader = loader()
        val icons = AndroidIconLoader(context)
        val shown = mutableListOf<Int>()
        val memoryFlags = mutableListOf<Boolean>()
        val visibleAt = mutableListOf<Long>()
        var began = 0L
        try {
            compose.setContent {
                if (began == 0L) began = android.os.SystemClock.uptimeMillis()
                CompositionLocalProvider(LocalEnrichedArtworkLoader provides loader) {
                val order = rememberArtworkLoadOrder(models.map { it.itemId })
                CompositionLocalProvider(LocalArtworkLoadOrder provides order) {
                    HomeArtworkBuffer(models, icons, 256)
                    models.reversed().forEach { model -> key(model.itemId) {
                        val art = rememberEnrichedArtwork(model, targetSizePx = 256)
                        SideEffect {
                            val index = models.indexOf(model)
                            if (art.painter != null && index !in shown) {
                                shown += index
                                visibleAt += android.os.SystemClock.uptimeMillis() - began
                                memoryFlags += art.fromMemory
                            }
                        }
                    } }
                }
                }
            }
            compose.waitUntil(10_000) { shown.size == models.size }
            assertEquals(models.indices.toList(), shown)
            assertTrue("Freshly preloaded covers retain their reveal animation", memoryFlags.none { it })
            android.util.Log.i("OrderedCoverTiming", "cold first=${visibleAt.first()}ms last=${visibleAt.last()}ms arrivals=$visibleAt")
            compose.runOnIdle { loader.setForeground(false); loader.trimMemory(true); icons.setForeground(false) }
            compose.waitForIdle()
            compose.runOnIdle {
                shown.clear()
                memoryFlags.clear()
                visibleAt.clear()
                began = android.os.SystemClock.uptimeMillis()
                loader.setForeground(true)
                icons.setForeground(true)
            }
            compose.waitUntil(10_000) { shown.size == models.size }
            assertEquals("Cache reclamation restarts the same display order on resume", models.indices.toList(), shown)
            assertTrue(memoryFlags.none { it })
            android.util.Log.i("OrderedCoverTiming", "resume first=${visibleAt.first()}ms last=${visibleAt.last()}ms arrivals=$visibleAt")
        } finally {
            compose.runOnUiThread { loader.setForeground(false); loader.trimMemory(true); icons.setForeground(false) }
            files.forEach(File::delete)
        }
    }

    @Test fun cachedImagesAppearImmediatelyAndMissingFilesDoNotBlockLaterCovers() {
        val files = List(4, ::image)
        val models = files.mapIndexed(::model)
        val loader = loader()
        val shown = mutableListOf<Int>()
        var cachedOnFirstComposition = false
        try {
            assertTrue(files[1].delete())
            runBlocking { assertNotNull(loader.load(files[3].path, files[3].path, 256)) }
            compose.setContent {
                val order = rememberArtworkLoadOrder(models.map { it.itemId })
                CompositionLocalProvider(LocalArtworkLoadOrder provides order, LocalEnrichedArtworkLoader provides loader) {
                    models.reversed().forEach { model -> key(model.itemId) {
                        val art = rememberEnrichedArtwork(model, targetSizePx = 256)
                        val index = models.indexOf(model)
                        DisposableEffect(Unit) {
                            if (index == 3) cachedOnFirstComposition = art.painter != null && art.fromMemory
                            onDispose { }
                        }
                        SideEffect { if (art.painter != null && index !in shown) shown += index }
                    } }
                }
            }
            compose.waitUntil(10_000) { shown.size == 3 }
            assertTrue(cachedOnFirstComposition)
            assertEquals(listOf(3, 0, 2), shown)
        } finally {
            compose.runOnUiThread { loader.setForeground(false); loader.trimMemory(true) }
            files.forEach(File::delete)
        }
    }
}
