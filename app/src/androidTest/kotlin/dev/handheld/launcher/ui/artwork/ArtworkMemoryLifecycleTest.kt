package dev.handheld.launcher.ui.artwork

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.handheld.launcher.core.domain.model.CurrentUserAndroidComponentId
import dev.handheld.launcher.ui.artwork.local.AndroidIconLoader
import dev.handheld.launcher.ui.artwork.local.AndroidIconResult
import dev.handheld.launcher.ui.artwork.local.rememberAndroidIconPainter
import kotlinx.coroutines.Dispatchers
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

/** Isolated decoder/icon fixtures. No catalog, preferences, or ROM database is changed. */
@RunWith(AndroidJUnit4::class)
class ArtworkMemoryLifecycleTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val component get() = CurrentUserAndroidComponentId(context.packageName, "dev.handheld.launcher.MainActivity")

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
                assertEquals(0, loader.cachedBytes)
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
