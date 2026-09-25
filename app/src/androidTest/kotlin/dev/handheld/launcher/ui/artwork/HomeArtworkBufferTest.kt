package dev.handheld.launcher.ui.artwork

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import dev.handheld.launcher.core.designsystem.foundation.ShellMetrics
import dev.handheld.launcher.core.designsystem.foundation.ShellMetricsInput
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.core.domain.model.*
import dev.handheld.launcher.di.LauncherApplication
import dev.handheld.launcher.feature.home.HomeArtworkBuffer
import dev.handheld.launcher.feature.home.HomeScreen
import dev.handheld.launcher.feature.home.HomeUiState
import dev.handheld.launcher.feature.home.homeArtworkTargetSizePx
import dev.handheld.launcher.ui.artwork.enriched.*
import dev.handheld.launcher.ui.artwork.local.*
import dev.handheld.launcher.ui.presentation.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Local fixtures only: no games launched and no catalog/preferences changed. */
class HomeArtworkBufferTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun loader() = EnrichedArtworkLoader(context,
        (context.applicationContext as LauncherApplication).appContainer.artworkRepository)
    private fun model(id: Int, file: File) = TileUiModel(ItemId("buffer-fixture:$id"), "Fixture $id", null,
        "Game", "PS2", "Play", emptyList(), Availability.Available, emptySet(),
        TileArtwork.LocalReference(UserArtworkReference(file.path)))
    private fun image(): File {
        val file = File.createTempFile("home-buffer-", ".png", context.cacheDir)
        // Non-power-of-two dimensions also exercise exact-size decoded retention.
        val source = Bitmap.createBitmap(1030, 1030, Bitmap.Config.ARGB_8888)
        source.eraseColor(0xFFD43D91.toInt())
        try { file.outputStream().use { assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { source.recycle() }
        return file
    }

    @Test fun twentyHomeCoversAreWarmBeforeFirstVisitAndReenterWithoutLoadingEvenWhileScrolling() {
        val files = List(20) { image() }
        val models = files.mapIndexed(::model)
        val loader = loader()
        val icons = AndroidIconLoader(context)
        var selected by mutableIntStateOf(0)
        var show by mutableStateOf(false)
        var buffer by mutableStateOf(true)
        val firstFrames = mutableListOf<Boolean>()
        try {
            compose.setContent {
                CompositionLocalProvider(LocalEnrichedArtworkLoader provides loader, LocalArtworkLoadingAllowed provides false) {
                    if (buffer) HomeArtworkBuffer(models, icons, 1024)
                    if (show) key(selected) {
                        val art = rememberEnrichedArtwork(models[selected], targetSizePx = 512)
                        // This executes on the first composition, before a coroutine could load anything.
                        DisposableEffect(Unit) { firstFrames += art.painter != null && art.fromMemory; onDispose {} }
                    }
                }
            }
            compose.waitUntil(10_000) { models.all { loader.cached(it, 512) != null } }
            assertTrue("All twenty covers fit within 20 MiB even at a large display size",
                loader.cachedBytes <= 20 * 512 * 512 * 4)
            // Cache, rather than a hidden retained painter or disk reread, must own fast reentry.
            compose.runOnIdle { buffer = false }
            files.forEach { assertTrue(it.delete()) }
            repeat(3) { round ->
                compose.runOnIdle {
                    loader.setForeground(false)
                    assertTrue(loader.cachedBytes > 0)
                    loader.setForeground(true)
                }
                repeat(20) { index ->
                    compose.runOnIdle { selected = if (round % 2 == 0) index else 19 - index; show = true }
                    compose.waitForIdle()
                    compose.runOnIdle { show = false }
                }
            }
            assertEquals(60, firstFrames.size)
            assertTrue("No placeholder frame on any return", firstFrames.all { it })
            compose.runOnIdle { loader.trimMemory(clear = true) }
            assertEquals(0, loader.cachedBytes)
        } finally {
            compose.runOnUiThread { loader.setForeground(false); loader.trimMemory(true); icons.setForeground(false) }
            files.forEach { it.delete() }
        }
    }

    @Test fun bufferRemainsBoundedToTheHomeRowEvenIfGivenAWholeCollection() {
        val file = image()
        val loader = loader()
        val icons = AndroidIconLoader(context)
        try {
            compose.setContent {
                CompositionLocalProvider(LocalEnrichedArtworkLoader provides loader) {
                    HomeArtworkBuffer(List(100) {
                        model(it, if (it < 20) file else File("outside-home-$it"))
                    }, icons, 384)
                }
            }
            // Await the complete preload pass: queued workers also register lifecycle
            // cancellation until their turn finishes, in addition to retained painters.
            compose.waitUntil(5_000) { loader.cachedBytes > 0 && loader.memoryOwner.activeRequestCount == 20 }
            compose.runOnIdle { assertEquals(20, loader.memoryOwner.activeRequestCount) }
        } finally { compose.runOnUiThread { loader.setForeground(false); loader.trimMemory(true) }; file.delete() }
    }

    @Test fun actualHomePreloadsEveryOffscreenCoverAndScrollsWithoutDiskAccess() {
        val files = List(20) { image() }
        val models = files.mapIndexed(::model)
        val loader = loader()
        val icons = AndroidIconLoader(context)
        val metrics = ShellMetrics.calculate(ShellMetricsInput(800, 450, 1f))
        var target = 0
        try {
            compose.setContent {
                val density = LocalDensity.current
                SideEffect { target = homeArtworkTargetSizePx(with(density) { metrics.homeCardAllocatedSize.roundToPx() }) }
                CompositionLocalProvider(LocalEnrichedArtworkLoader provides loader, LocalArtworkLoadingAllowed provides false) {
                    LauncherTheme(referenceScale = metrics.referenceScale) {
                        HomeScreen(HomeUiState(items = models, selectedItemId = models.first().itemId, loading = false),
                            metrics, icons, Modifier.size(800.dp, 450.dp), onSelect = {}, onActivate = {},
                            onOpenLibrary = {}, onOpenDetails = {}, onRefresh = {}, onViewportChanged = { _, _ -> },
                            allowFocusRequest = false)
                    }
                }
            }
            // No scrolling has occurred: covers near the end must already be available.
            compose.waitUntil(10_000) { target > 0 && models.all { loader.cached(it, target) != null } }
            files.forEach { assertTrue(it.delete()) }
            for (index in listOf(16, 8, 0, 16)) {
                compose.onNodeWithTag("home-row").performScrollToIndex(index)
                val card = compose.onNodeWithContentDescription(models[index].title).assertIsDisplayed()
                val pixels = card.captureToImage().toPixelMap()
                val center = pixels[pixels.width / 2, pixels.height / 2]
                assertEquals("The real card shows its cached cover", 212 / 255f, center.red, .03f)
                assertEquals(61 / 255f, center.green, .03f)
                assertEquals(145 / 255f, center.blue, .03f)
            }
        } finally {
            compose.runOnUiThread { loader.setForeground(false); loader.trimMemory(true); icons.setForeground(false) }
            files.forEach { it.delete() }
        }
    }

    @Test fun cachedRomAppearsBeforeRoomOrIdleAndDoesNotLeakIntoCustomArtwork() {
        val file = image()
        val loader = loader()
        val rom = model(0, file).copy(artwork = TileArtwork.Rom)
        val loaded = runBlocking { loader.load(file.path, "ready-rom-key", 384) }
        assertNotNull(loaded)
        compose.runOnUiThread { loader.rememberSource(rom.itemId, "ready-rom-key") }
        var custom by mutableStateOf(false)
        val result = AtomicReference<EnrichedArtwork>()
        try {
            compose.setContent {
                CompositionLocalProvider(LocalEnrichedArtworkLoader provides loader, LocalArtworkLoadingAllowed provides false) {
                    val artwork = rememberEnrichedArtwork(if (custom) model(0, File("missing-cover")) else rom)
                    SideEffect { result.set(artwork) }
                }
            }
            compose.waitForIdle()
            assertNotNull(result.get().painter)
            assertTrue(result.get().fromMemory)
            compose.runOnIdle { custom = true }
            compose.waitForIdle()
            assertNull(result.get().painter)
        } finally { compose.runOnUiThread { loader.setForeground(false); loader.trimMemory(true) }; file.delete() }
    }

    @Test fun androidIconReentryHasNoPlaceholderAndPackageInvalidationReloadsIt() {
        val loader = AndroidIconLoader(context)
        val component = CurrentUserAndroidComponentId(context.packageName, "dev.handheld.launcher.MainActivity")
        runBlocking { assertTrue(loader.load(component, 64) is AndroidIconResult.Loaded) }
        var show by mutableStateOf(true)
        val result = AtomicReference<androidx.compose.ui.graphics.painter.Painter?>()
        val firstFrames = mutableListOf<Boolean>()
        compose.setContent {
            CompositionLocalProvider(LocalArtworkLoadingAllowed provides false) {
                if (show) {
                    val painter = rememberAndroidIconPainter(loader, component, targetSizePx = 64)
                    DisposableEffect(Unit) { firstFrames += painter != null; onDispose {} }
                    SideEffect { result.set(painter) }
                }
            }
        }
        compose.waitForIdle()
        val previous = result.get()
        repeat(5) {
            compose.runOnIdle { show = false; loader.setForeground(false); loader.setForeground(true) }
            compose.waitForIdle()
            compose.runOnIdle { show = true }
            compose.waitForIdle()
        }
        assertEquals(List(6) { true }, firstFrames)
        compose.runOnIdle { loader.clear() }
        compose.waitUntil(5_000) { result.get() != null && result.get() !== previous && loader.cachedBytes > 0 }
        compose.runOnIdle { loader.setForeground(false); loader.trimMemory(true) }
    }
}
