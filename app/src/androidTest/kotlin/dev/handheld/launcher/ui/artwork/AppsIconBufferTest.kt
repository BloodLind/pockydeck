package dev.handheld.launcher.ui.artwork

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.handheld.launcher.core.domain.model.*
import dev.handheld.launcher.feature.collection.AppsIconBuffer
import dev.handheld.launcher.ui.artwork.local.AndroidIconLoader
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppsIconBufferTest {
    @get:Rule val compose = createComposeRule()

    @Test fun warmsOffscreenIconsWithoutCardsAndReleasesWorkAcrossBackgroundAndPackageChanges() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = AndroidIconLoader(context)
        val items = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo }.distinctBy { it.packageName to it.name }.take(12).map {
                LibraryItem.AndroidApp(CurrentUserAndroidComponentId(it.packageName, it.name), it.name,
                    LibraryCategory.OTHER, Availability.Available, emptySet())
            }
        assertTrue("Use multiple installed icons, without activating their apps", items.size >= 2)
        var show by mutableStateOf(true)
        compose.setContent { if (show) AppsIconBuffer(items, loader, rememberLazyGridState(), 192) }
        fun awaitWarm() = compose.waitUntil(10_000) { items.all { loader.cached(it.componentId, 192) != null } }
        try {
            awaitWarm() // There are no visible cards: every icon came from the buffer.
            assertTrue(loader.cachedBytes in 1..(items.size * 192 * 192 * 4))
            compose.runOnIdle {
                loader.setForeground(false)
                loader.trimMemory(true)
                assertEquals(0, loader.cachedBytes)
                assertEquals(0, loader.memoryOwner.activeRequestCount)
            }
            compose.waitForIdle()
            assertEquals("Background work must not refill the trimmed cache", 0, loader.cachedBytes)
            compose.runOnIdle { loader.setForeground(true) }
            awaitWarm()
            compose.runOnIdle { loader.clear() }
            awaitWarm()
            compose.runOnIdle { show = false }
            compose.waitForIdle()
            assertEquals(0, loader.memoryOwner.activeRequestCount)
        } finally { compose.runOnUiThread { loader.setForeground(false); loader.trimMemory(true) } }
    }
}
