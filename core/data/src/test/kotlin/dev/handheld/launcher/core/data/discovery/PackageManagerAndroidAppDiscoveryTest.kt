package dev.handheld.launcher.core.data.discovery

import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.CatalogInventory
import dev.handheld.launcher.core.domain.model.CurrentUserAndroidComponentId
import dev.handheld.launcher.core.domain.model.IncompleteInventoryReason
import dev.handheld.launcher.core.domain.model.InventoryScope
import dev.handheld.launcher.core.domain.model.LibraryCategory
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.SupportedItemAction
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageManagerAndroidAppDiscoveryTest {
    @Test
    fun `retains multiple launcher components from one package with stable icon references`() {
        val records = listOf(
            launcherRecord("dev.fixture.game", "dev.fixture.game.PlayerActivity", "Player"),
            launcherRecord("dev.fixture.game", ".SettingsActivity", "Settings"),
        )

        val inventory = discover(records)

        assertTrue(inventory is CatalogInventory.Complete)
        val items = inventory.observedItems.map { it as LibraryItem.AndroidApp }
        assertEquals(InventoryScope.CurrentUserAndroid, inventory.scope)
        assertEquals(2, items.size)
        assertNotEquals(items[0].componentId, items[1].componentId)
        assertEquals(
            setOf(
                CurrentUserAndroidComponentId("dev.fixture.game", "dev.fixture.game.PlayerActivity"),
                CurrentUserAndroidComponentId("dev.fixture.game", "dev.fixture.game.SettingsActivity"),
            ),
            items.map(LibraryItem.AndroidApp::componentId).toSet(),
        )
        assertEquals(items.map { it.componentId.itemId }.toSet(), items.map { it.id }.toSet())
    }

    @Test
    fun `maps accepted launcher records without category inference`() {
        val inventory = discover(
            listOf(launcherRecord("dev.fixture.emulator", "FrontendActivity", "  Emulator  ")),
        ) as CatalogInventory.Complete
        val item = inventory.observedItems.single() as LibraryItem.AndroidApp

        assertEquals("Emulator", item.title)
        assertEquals(LibraryCategory.OTHER, item.category)
        assertEquals(Availability.Available, item.availability)
        assertEquals(
            setOf(
                SupportedItemAction.OPEN,
                SupportedItemAction.VIEW_DETAILS,
                SupportedItemAction.TOGGLE_FAVORITE,
                SupportedItemAction.OPEN_APP_INFO,
            ),
            item.supportedActions,
        )
    }

    @Test
    fun `excludes self disabled unexported and malformed records`() {
        val inventory = discover(
            listOf(
                launcherRecord(OWN_PACKAGE, "$OWN_PACKAGE.MainActivity", "Self"),
                launcherRecord("dev.fixture", "dev.fixture.Disabled", "Disabled", activityEnabled = false),
                launcherRecord("dev.fixture", "dev.fixture.DisabledApp", "Disabled app", applicationEnabled = false),
                launcherRecord("dev.fixture", "dev.fixture.Private", "Private", exported = false),
                launcherRecord("bad/package", "bad.package.Bad", "Bad"),
                launcherRecord("dev.fixture", "", "Blank class"),
                launcherRecord("dev.fixture", "   ", "Whitespace class"),
                launcherRecord("dev.fixture", ".", "Dot class"),
                launcherRecord("dev.fixture", "dev.fixture.Good", null),
            ),
        ) as CatalogInventory.Complete

        val item = inventory.observedItems.single() as LibraryItem.AndroidApp
        assertEquals("dev.fixture.Good", item.componentId.activityClassName)
        assertEquals("Good", item.title)
    }

    @Test
    fun `deduplicates repeated resolver records by component identity`() {
        val record = launcherRecord("dev.fixture", "dev.fixture.Main", "Fixture")
        val inventory = discover(listOf(record, record)) as CatalogInventory.Complete

        assertEquals(1, inventory.observedItems.size)
    }

    @Test
    fun `runs provider work on supplied background dispatcher`() {
        val callerThread = Thread.currentThread()
        var providerThread: Thread? = null
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            runBlocking {
                val discovery = PackageManagerAndroidAppDiscovery(
                    source = AndroidLauncherActivitySource {
                        providerThread = Thread.currentThread()
                        emptyList()
                    },
                    ownPackageName = OWN_PACKAGE,
                    dispatcher = dispatcher,
                )
                discovery.discover()
            }
        }

        assertNotEquals(callerThread, providerThread)
    }

    @Test
    fun `reports query failure as incomplete without observations`() {
        val inventory = runBlocking {
            PackageManagerAndroidAppDiscovery(
                source = AndroidLauncherActivitySource { error("query failed") },
                ownPackageName = OWN_PACKAGE,
                dispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            ).discover()
        }

        assertEquals(
            CatalogInventory.Incomplete(
                scope = InventoryScope.CurrentUserAndroid,
                observedItems = emptyList(),
                reason = IncompleteInventoryReason.FAILED,
            ),
            inventory,
        )
    }

    @Test(expected = CancellationException::class)
    fun `does not turn cancellation into a failed scan`() {
        runBlocking {
            PackageManagerAndroidAppDiscovery(
                source = AndroidLauncherActivitySource { throw CancellationException("cancelled") },
                ownPackageName = OWN_PACKAGE,
                dispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            ).discover()
        }
    }

    private fun discover(records: List<AndroidLauncherActivity>): CatalogInventory = runBlocking {
        PackageManagerAndroidAppDiscovery(
            source = AndroidLauncherActivitySource { records },
            ownPackageName = OWN_PACKAGE,
            dispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        ).discover()
    }

    private fun launcherRecord(
        packageName: String,
        className: String,
        label: String?,
        activityEnabled: Boolean = true,
        applicationEnabled: Boolean = true,
        exported: Boolean = true,
    ) = AndroidLauncherActivity(
        packageName = packageName,
        activityClassName = className,
        label = label,
        activityEnabled = activityEnabled,
        applicationEnabled = applicationEnabled,
        exported = exported,
    )

    private companion object {
        const val OWN_PACKAGE = "dev.handheld.launcher"
    }
}
