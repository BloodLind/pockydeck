package dev.handheld.launcher.feature.home

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.core.domain.model.*
import dev.handheld.launcher.di.LauncherApplication
import dev.handheld.launcher.ui.artwork.enriched.EnrichedArtworkLoader
import dev.handheld.launcher.ui.artwork.enriched.LocalEnrichedArtworkLoader
import dev.handheld.launcher.ui.presentation.TileArtwork
import dev.handheld.launcher.ui.presentation.TileUiModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Local image fixtures only; never changes the user's catalog or preferences. */
class HomeBackdropPresentationTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun loader() = EnrichedArtworkLoader(context,
        (context.applicationContext as LauncherApplication).appContainer.artworkRepository)
    private fun image(color: Int? = null): File {
        val file = File.createTempFile("backdrop-test-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(512 * 512) { color ?: if ((it % 512) / 16 % 2 == 0) 0xFFFF0000.toInt() else 0xFF0000FF.toInt() }
        bitmap.setPixels(pixels, 0, 512, 0, 0, 512, 512)
        try { file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
        return file
    }
    private fun game(file: File, id: String = "one") = TileUiModel(ItemId(id), id, null, "Game", "PS2", "Play",
        emptyList(), Availability.Available, setOf(SupportedItemAction.OPEN),
        TileArtwork.LocalReference(UserArtworkReference(file.path)), platformId = "ps2", isRom = true)

    @Test fun backdropIsSmallBlurredAndCachedSeparatelyFromTheSharpCover() = runBlocking {
        val file = image()
        val loader = loader()
        try {
            val sharp = requireNotNull(loader.load(file.path, file.path, 128))
            val soft = requireNotNull(loader.load(file.path, file.path, 128, blurred = true))
            assertTrue(soft.width <= 128 && soft.height <= 128)
            val center = soft.asAndroidBitmap().getPixel(64, 64)
            assertTrue((center ushr 16 and 255) in 100..155)
            assertTrue((center and 255) in 100..155)
            assertNotEquals(sharp.asAndroidBitmap().getPixel(64, 64), center)
            assertTrue(sharp === loader.load(file.path, file.path, 128))
            assertTrue(soft === loader.load(file.path, file.path, 128, blurred = true))
            assertTrue(loader.cachedBytes <= 128 * 128 * 4 * 2)
        } finally { loader.trimMemory(clear = true); file.delete() }
    }

    @Test fun disabledAndRapidSelectionsDoNoImageWorkAndAppsOrMissingCoversUseTheDefaultBackground() {
        val file = image()
        val loader = loader()
        var enabled by mutableStateOf(false)
        var allowed by mutableStateOf(false)
        var selected by mutableStateOf<TileUiModel?>(game(file))
        try {
            compose.setContent {
                LauncherTheme(reducedMotion = true) {
                    CompositionLocalProvider(LocalEnrichedArtworkLoader provides loader) {
                        Box(Modifier.size(600.dp, 320.dp)) { HomeRomBackdrop(selected, enabled, allowed) }
                    }
                }
            }
            compose.waitForIdle()
            compose.onNodeWithTag("home-rom-backdrop").assertDoesNotExist()
            assertEquals(0, loader.cachedBytes)
            compose.runOnIdle { enabled = true }
            repeat(20) { index -> compose.runOnIdle { selected = game(file, "selection-$index") } }
            compose.waitForIdle()
            assertEquals("No background decode while navigating", 0, loader.cachedBytes)
            compose.runOnIdle { allowed = true }
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("home-rom-backdrop").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("home-rom-backdrop").assertIsDisplayed().assertHasNoClickAction()
            compose.runOnIdle { enabled = false }
            compose.onNodeWithTag("home-rom-backdrop").assertDoesNotExist()
            compose.runOnIdle { enabled = true; selected = game(file).copy(platformId = null, platformLabel = "ANDROID APP", isRom = false) }
            compose.onNodeWithTag("home-rom-backdrop").assertDoesNotExist()
            compose.runOnIdle { selected = game(File(file.path + ".missing")) }
            compose.waitForIdle()
            compose.onNodeWithTag("home-rom-backdrop").assertDoesNotExist()
        } finally { compose.runOnUiThread { loader.setForeground(false) }; file.delete() }
    }

    @Test fun blurReusesLoadedCoverPixelsWithoutReadingTheSourceAgain() = runBlocking {
        val file = image()
        val loader = loader()
        try {
            val cover = requireNotNull(loader.load(file.path, file.path, 384))
            assertTrue(file.delete())
            val soft = requireNotNull(loader.backdropFromMemory(game(file)))
            assertTrue(soft.width <= 128 && soft.height <= 128)
            assertTrue(cover === loader.cached(file.path, 384))
            assertTrue(soft === loader.load(file.path, file.path, 128, blurred = true))
            assertFalse(cover.asAndroidBitmap().isRecycled)
            val rom = game(file, "rom-memory-fixture").copy(artwork = TileArtwork.Rom)
            compose.runOnUiThread { loader.rememberSource(rom.itemId, file.path) }
            assertTrue(soft === loader.backdropFromMemory(rom))
        } finally { loader.trimMemory(true); file.delete() }
    }

    @Test fun cachedBackdropAppearsPromptlyAndActuallyFadesEvenWhenNavigationResumes() {
        val red = image(0xFFFF0000.toInt())
        val blue = image(0xFF0000FF.toInt())
        val loader = loader()
        runBlocking {
            requireNotNull(loader.load(red.path, red.path, 128, blurred = true))
            requireNotNull(loader.load(blue.path, blue.path, 128, blurred = true))
        }
        var enabled by mutableStateOf(false)
        var allowed by mutableStateOf(true)
        var selected by mutableStateOf(game(red, "red"))
        fun pixel(): Color {
            val pixels = compose.onNodeWithTag("backdrop-pixels").captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 2]
        }
        try {
            compose.setContent {
                LauncherTheme {
                    CompositionLocalProvider(LocalEnrichedArtworkLoader provides loader) {
                        Box(Modifier.size(200.dp, 120.dp).background(Color.Black).testTag("backdrop-pixels")) {
                            HomeRomBackdrop(selected, enabled, allowed)
                        }
                    }
                }
            }
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            compose.runOnIdle { enabled = true }
            compose.mainClock.advanceTimeBy(96)
            compose.onNodeWithTag("home-rom-backdrop").assertExists()
            val entering = pixel()
            assertTrue("The reveal must already be drawing, without a second 180 ms delay", entering.red > .01f)
            compose.runOnIdle { allowed = false }
            compose.mainClock.advanceTimeByFrame()
            val interrupted = pixel()
            compose.mainClock.advanceTimeBy(160)
            val settled = pixel()
            assertTrue("There must be a visible entrance, not an instant spawn", entering.red < settled.red - .01f)
            assertTrue("Navigation must not snap the remaining reveal to full opacity", interrupted.red < settled.red - .01f)
            compose.runOnIdle { selected = game(blue, "blue") }
            compose.mainClock.advanceTimeBy(200)
            assertEquals("The previous wash stays while navigation is active", settled.red, pixel().red, .01f)
            compose.runOnIdle { allowed = true }
            compose.mainClock.advanceTimeBy(96)
            val changing = pixel()
            assertTrue("Both images contribute during replacement", changing.red > .02f && changing.blue > .02f)
            compose.mainClock.advanceTimeBy(160)
            val final = pixel()
            assertTrue(final.blue > final.red)
            compose.onAllNodesWithTag("home-rom-backdrop").assertCountEquals(1)
        } finally {
            compose.mainClock.autoAdvance = true
            compose.runOnUiThread { loader.setForeground(false); loader.trimMemory(true) }
            red.delete(); blue.delete()
        }
    }
}
