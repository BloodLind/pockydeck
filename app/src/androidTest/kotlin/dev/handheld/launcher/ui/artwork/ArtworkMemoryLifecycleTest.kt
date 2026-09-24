package dev.handheld.launcher.ui.artwork

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.handheld.launcher.core.domain.model.CurrentUserAndroidComponentId
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.UserArtworkReference
import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.di.LauncherApplication
import dev.handheld.launcher.ui.presentation.TileUiModel
import dev.handheld.launcher.ui.presentation.TileArtwork
import dev.handheld.launcher.ui.artwork.enriched.EnrichedArtworkLoader
import dev.handheld.launcher.ui.artwork.enriched.LocalEnrichedArtworkLoader
import dev.handheld.launcher.ui.artwork.enriched.rememberEnrichedArtwork
import dev.handheld.launcher.ui.artwork.local.AndroidIconLoader
import dev.handheld.launcher.ui.artwork.local.AndroidIconResult
import dev.handheld.launcher.ui.artwork.local.rememberAndroidIconPainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import java.util.Collections

/** Isolated decoder/icon fixtures. No catalog, preferences, or ROM database is changed. */
@RunWith(AndroidJUnit4::class)
class ArtworkMemoryLifecycleTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val component get() = CurrentUserAndroidComponentId(context.packageName, "dev.handheld.launcher.MainActivity")

    @Test fun finishingPreloadsCanOverlapBackgroundCleanupWithoutRetainingPainters() {
        val owner = ArtworkMemoryOwner()
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).asImageBitmap()
        val painters = Collections.synchronizedList(mutableListOf<ReleasableArtworkPainter>())
        try {
            runBlocking {
                val creating = launch(Dispatchers.Default) {
                    repeat(4_000) { index ->
                        val painter = owner.painter(bitmap)
                        painters += painter
                        if (index % 2 == 0) painter.release()
                    }
                }
                repeat(40) {
                    compose.runOnUiThread { owner.updateForeground(false); owner.updateForeground(true) }
                }
                creating.join()
            }
        } finally { compose.runOnUiThread { owner.updateForeground(false) } }
        assertEquals(4_000, painters.size)
        assertTrue("Every completed preload releases its painter on background", painters.all { !it.holdsBitmap })
        assertEquals(0, owner.activeRequestCount)
        assertFalse(bitmap.asAndroidBitmap().isRecycled)
    }

    @Test fun scrollingRetainsVisibleCoverAndDefersRapidlyReplacedCardsUntilSettled() {
        val file = File.createTempFile("scroll-artwork-", ".png", context.cacheDir)
        val loader = EnrichedArtworkLoader(context, (context.applicationContext as LauncherApplication).appContainer.artworkRepository)
        val first = AtomicReference<Painter?>(null)
        val second = AtomicReference<Painter?>(null)
        var allowed by mutableStateOf(true)
        var next by mutableStateOf<Int?>(null)
        fun model(id: Int) = TileUiModel(ItemId("artwork-fixture:$id"), "Fixture $id", null, "Game", "PS2", "Play",
            emptyList(), Availability.Available, emptySet(), TileArtwork.LocalReference(UserArtworkReference(file.path)))
        try {
            val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
            try { file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
            finally { bitmap.recycle() }
            compose.setContent {
                CompositionLocalProvider(LocalEnrichedArtworkLoader provides loader, LocalArtworkLoadingAllowed provides allowed) {
                    val visible = rememberEnrichedArtwork(model(0), targetSizePx = 128)
                    val entering = next?.let { rememberEnrichedArtwork(model(it), targetSizePx = 256) }
                    SideEffect { first.set(visible.painter); second.set(entering?.painter) }
                }
            }
            compose.waitUntil(5_000) { first.get() != null }
            val displayed = first.get() as ReleasableArtworkPainter
            compose.runOnIdle { allowed = false; loader.trimMemory(clear = true) }
            repeat(40) { index ->
                compose.runOnIdle { next = index + 1 }
                compose.waitForIdle()
                assertEquals("Moving cards must not start fresh bitmap work", 0, loader.cachedBytes)
                assertEquals(null, second.get())
                assertTrue(displayed.holdsBitmap)
                assertTrue(first.get() === displayed)
            }
            compose.runOnIdle { allowed = true }
            compose.waitUntil(5_000) { second.get() != null }
            assertTrue(loader.cachedBytes > 0)
            assertTrue("Resuming must not replace/reanimate the already displayed cover", first.get() === displayed)
            compose.runOnIdle { allowed = false; loader.setForeground(false) }
            assertFalse("Lifecycle release still wins over scroll retention", displayed.holdsBitmap)
            assertTrue("Normal backgrounding retains the bounded warm cache", loader.cachedBytes > 0)
        } finally { compose.runOnUiThread { loader.setForeground(false) }; file.delete() }
    }

    @Test fun scrollingNeverDelaysAndroidIconsAndKeepsTheLoadedIcon() {
        val loader = AndroidIconLoader(context)
        var allowed by mutableStateOf(false)
        val latest = AtomicReference<Painter?>(null)
        compose.setContent {
            CompositionLocalProvider(LocalArtworkLoadingAllowed provides allowed) {
                val painter = rememberAndroidIconPainter(loader, component, targetSizePx = 64)
                SideEffect { latest.set(painter) }
            }
        }
        compose.waitUntil(5_000) { latest.get() != null }
        assertTrue("PackageManager icons load while cover work is paused", loader.cachedBytes > 0)
        val displayed = latest.get()
        compose.runOnIdle { allowed = true }
        compose.waitForIdle()
        assertTrue(latest.get() === displayed)
    }

    @Test fun streamedImageDecodeUsesDifferentBoundedSizesForSmallCardsAndLargePreview() = runBlocking {
        val file = File.createTempFile("artwork-decode-", ".png", context.cacheDir)
        try {
            withContext(Dispatchers.IO) {
                val source = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
                try { file.outputStream().use { assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
                finally { source.recycle() }
                val decoder = ArtworkBitmapDecoder(context.contentResolver)
                val small = decoder.decode(file.path, 128)
                val large = decoder.decode(file.path, 512)
                assertNotNull(small)
                assertNotNull(large)
                assertTrue(requireNotNull(small).width <= 128)
                assertTrue(requireNotNull(large).width <= 512)
                assertTrue(large.width > small.width)
                assertTrue(small.width * small.height * 4 < large.width * large.height * 4)
            }
        } finally { file.delete() }
    }

    @Test fun cacheTrimReleasesOnlyCacheOwnershipAndBackgroundReleasesPainterWithoutRecyclingBitmap() {
        val loader = AndroidIconLoader(context)
        val loaded = runBlocking { loader.load(component, 64) } as AndroidIconResult.Loaded
        assertTrue(loader.cachedBytes > 0)
        compose.runOnUiThread {
            val painter = loader.memoryOwner.painter(loaded.bitmap)
            loader.trimMemory(clear = true)
            assertEquals(0, loader.cachedBytes)
            assertTrue("The currently drawn image survives pressure trimming", painter.holdsBitmap)
            loader.setForeground(false)
            assertFalse(painter.holdsBitmap)
            assertFalse("A retained Android draw must never see a recycled bitmap", loaded.bitmap.asAndroidBitmap().isRecycled)
            loader.setForeground(true)
        }
        assertTrue(runBlocking { loader.load(component, 64) } is AndroidIconResult.Loaded)
        assertTrue(loader.cachedBytes > 0)
    }

    @Test fun rapidBackgroundAndResumeRestartsArtworkEvenWhenTheFrameClockNeverObservedBackground() {
        val loader = AndroidIconLoader(context)
        val latest = AtomicReference<Painter?>(null)
        var active by mutableStateOf(true)
        compose.setContent {
            val painter = rememberAndroidIconPainter(loader, component, active, 64)
            SideEffect { latest.set(painter) }
            if (painter != null) Image(painter, "Fixture icon", Modifier.size(64.dp))
        }
        compose.waitUntil(5_000) { latest.get() is ReleasableArtworkPainter }
        val before = latest.get() as ReleasableArtworkPainter
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnUiThread {
                loader.setForeground(false)
                assertFalse("Cleanup does not require a Compose frame", before.holdsBitmap)
                assertTrue("Return Home should reuse Android icons", loader.cachedBytes > 0)
                assertEquals(0, loader.memoryOwner.activeRequestCount)
                loader.setForeground(true)
            }
        } finally { compose.mainClock.autoAdvance = true }
        compose.waitUntil(5_000) {
            val current = latest.get()
            current is ReleasableArtworkPainter && current !== before && current.holdsBitmap
        }
        compose.runOnIdle { active = false }
        compose.waitUntil(5_000) { latest.get() == null }
        compose.runOnIdle { assertEquals("Offscreen cards unregister their lifecycle callbacks", 0, loader.memoryOwner.activeRequestCount) }
    }
}
