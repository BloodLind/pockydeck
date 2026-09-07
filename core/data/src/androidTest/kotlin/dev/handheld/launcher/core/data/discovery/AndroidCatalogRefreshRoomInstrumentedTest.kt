package dev.handheld.launcher.core.data.discovery

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.handheld.launcher.core.data.android.apps.AndroidPackageChangeMonitor
import dev.handheld.launcher.core.data.android.apps.BroadcastAndroidPackageChangeMonitor
import dev.handheld.launcher.core.data.discovery.fixture.AppDetailsOnlyActivity
import dev.handheld.launcher.core.data.local.LauncherDatabase
import dev.handheld.launcher.core.data.repository.RoomCatalogRepository
import dev.handheld.launcher.core.data.repository.RoomFavoriteRepository
import dev.handheld.launcher.core.data.repository.RoomItemOverrideRepository
import dev.handheld.launcher.core.data.repository.RoomSuccessfulOpenRepository
import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.CatalogInventory
import dev.handheld.launcher.core.domain.model.CurrentUserAndroidComponentId
import dev.handheld.launcher.core.domain.model.InventoryScope
import dev.handheld.launcher.core.domain.model.LaunchOperationId
import dev.handheld.launcher.core.domain.model.LibraryCategory
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.SuccessfulOpenCandidate
import dev.handheld.launcher.core.domain.model.SupportedItemAction
import dev.handheld.launcher.core.domain.model.UnavailabilityReason
import dev.handheld.launcher.core.domain.model.UserArtworkReference
import dev.handheld.launcher.core.domain.model.UserItemOverrides
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidCatalogRefreshRoomInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: LauncherDatabase
    private lateinit var databaseName: String
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "us021-${System.nanoTime()}.db"
        database = LauncherDatabase.open(context, databaseName)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun completeRemovalAndResumeRediscoveryPreserveRoomReferences() = runBlocking {
        val catalog = RoomCatalogRepository(database)
        val favorites = RoomFavoriteRepository(database)
        val overrides = RoomItemOverrideRepository(database)
        val successfulOpens = RoomSuccessfulOpenRepository(database)
        val originalA = androidItem("a", "A")
        val originalB = androidItem("b", "B")
        val updatedA = originalA.copy(title = "A updated")
        val rediscoveredB = originalB.copy(title = "B rediscovered")
        val installedC = androidItem("c", "C")
        catalog.applyInventory(complete(originalA, originalB))
        favorites.setFavorite(originalB.id, true)
        val expectedOverrides = UserItemOverrides(
            category = LibraryCategory.GAME,
            artworkReference = UserArtworkReference("user:b"),
        )
        overrides.setOverrides(originalB.id, expectedOverrides)
        successfulOpens.recordOnce(
            SuccessfulOpenCandidate(LaunchOperationId("open-b"), originalB.id),
        )
        val nextInventory = AtomicReference(complete(updatedA, installedC))
        val coordinator = AndroidCatalogRefreshCoordinator(
            catalog,
            AndroidAppDiscovery { nextInventory.get() },
            NoOpPackageChangeMonitor,
            scope,
        )

        assertEquals(setOf(originalA.id, originalB.id), coordinator.catalog.first().activeItems.map { it.id }.toSet())
        coordinator.start()
        coordinator.onResume()
        coordinator.awaitReady(AndroidCatalogRefreshTrigger.STARTUP)

        assertEquals("A updated", catalog.findItem(originalA.id)?.title)
        assertEquals(
            Availability.Unavailable(UnavailabilityReason.REMOVED),
            catalog.findItem(originalB.id)?.availability,
        )
        assertEquals(setOf(updatedA.id, installedC.id), catalog.snapshot.first().activeItems.map { it.id }.toSet())
        assertEquals(setOf(originalB.id), favorites.favoriteItemIds.first())
        assertEquals(expectedOverrides, overrides.overridesByItemId.first()[originalB.id])
        assertEquals(originalB.id, successfulOpens.records.first().single().itemId)

        nextInventory.set(complete(updatedA, rediscoveredB, installedC))
        coordinator.onResume()
        coordinator.awaitReady(AndroidCatalogRefreshTrigger.RESUME)

        assertEquals(rediscoveredB, catalog.findItem(originalB.id))
        assertTrue(originalB.id in catalog.snapshot.first().activeItems.map { it.id })
        assertEquals(setOf(originalB.id), favorites.favoriteItemIds.first())
        assertEquals(expectedOverrides, overrides.overridesByItemId.first()[originalB.id])
        assertEquals(originalB.id, successfulOpens.records.first().single().itemId)
    }

    @Test
    fun broadcastMonitorStartAndStopAreIdempotentOnAndroid13() {
        val monitor = BroadcastAndroidPackageChangeMonitor(context)
        monitor.start { }
        monitor.start { }
        monitor.stop()
        monitor.stop()
        monitor.start { }
        monitor.stop()
    }

    @Test
    fun broadcastMonitorReceivesProtectedPackageChangedFromFixtureToggle() = runBlocking {
        val fixtureContext = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation()
            .context
        val packageManager = fixtureContext.packageManager
        val fixtureComponent = ComponentName(fixtureContext, AppDetailsOnlyActivity::class.java)
        val originalState = packageManager.getComponentEnabledSetting(fixtureComponent)
        val changedState = if (
            originalState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
            originalState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
            originalState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
        ) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        val callback = CompletableDeferred<Unit>()
        val monitor = BroadcastAndroidPackageChangeMonitor(fixtureContext)
        monitor.start { callback.complete(Unit) }

        try {
            packageManager.setComponentEnabledSetting(
                fixtureComponent,
                changedState,
                PackageManager.DONT_KILL_APP,
            )
            withTimeout(5_000) { callback.await() }
        } finally {
            monitor.stop()
            packageManager.setComponentEnabledSetting(
                fixtureComponent,
                originalState,
                PackageManager.DONT_KILL_APP,
            )
        }
        assertEquals(originalState, packageManager.getComponentEnabledSetting(fixtureComponent))
        Unit
    }

    private suspend fun AndroidCatalogRefreshCoordinator.awaitReady(
        trigger: AndroidCatalogRefreshTrigger,
    ) = withTimeout(5_000) {
        refreshState.filter {
            it == AndroidCatalogRefreshState.Ready(trigger)
        }.first()
    }

    private fun complete(vararg items: LibraryItem.AndroidApp) = CatalogInventory.Complete(
        InventoryScope.CurrentUserAndroid,
        items.toList(),
    )

    private fun androidItem(key: String, title: String) = LibraryItem.AndroidApp(
        componentId = CurrentUserAndroidComponentId("dev.fixture.$key", "dev.fixture.$key.Main"),
        title = title,
        category = LibraryCategory.OTHER,
        availability = Availability.Available,
        supportedActions = setOf(SupportedItemAction.OPEN),
    )

    private object NoOpPackageChangeMonitor : AndroidPackageChangeMonitor {
        override fun start(onPackageChanged: () -> Unit) = Unit

        override fun stop() = Unit
    }
}
