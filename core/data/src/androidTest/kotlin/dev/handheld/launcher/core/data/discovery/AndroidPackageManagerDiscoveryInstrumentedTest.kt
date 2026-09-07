package dev.handheld.launcher.core.data.discovery

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.handheld.launcher.core.data.discovery.fixture.AppDetailsOnlyActivity
import dev.handheld.launcher.core.data.discovery.fixture.FirstLauncherActivity
import dev.handheld.launcher.core.data.discovery.fixture.SecondLauncherActivity
import dev.handheld.launcher.core.domain.model.CatalogInventory
import dev.handheld.launcher.core.domain.model.LibraryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidPackageManagerDiscoveryInstrumentedTest {
    @Test
    fun exactMainLauncherQueryReturnsBothComponentsAndRejectsDetailsOnlyFixture() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixturePackageName = InstrumentationRegistry.getInstrumentation().context.packageName
        val records = PackageManagerAndroidLauncherActivitySource(context.packageManager)
            .queryMainLauncherActivities()
        val activityNames = records.map(AndroidLauncherActivity::activityClassName).toSet()

        assertTrue(FirstLauncherActivity::class.java.name in activityNames)
        assertTrue(SecondLauncherActivity::class.java.name in activityNames)
        assertFalse(AppDetailsOnlyActivity::class.java.name in activityNames)
        assertEquals(
            2,
            records.count { it.packageName == fixturePackageName },
        )
        Log.i(
            LOG_TAG,
            "fixture launcher components=" + activityNames
                .filter { it == FirstLauncherActivity::class.java.name || it == SecondLauncherActivity::class.java.name }
                .sorted()
                .joinToString(),
        )
    }

    @Test
    fun discoveryExcludesItsOwnPackage() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixturePackageName = InstrumentationRegistry.getInstrumentation().context.packageName
        val inventory = PackageManagerAndroidAppDiscovery(
            source = PackageManagerAndroidLauncherActivitySource(context.packageManager),
            ownPackageName = fixturePackageName,
            dispatcher = Dispatchers.IO,
        ).discover()

        assertFalse(
            inventory.observedItems.any {
                (it as? dev.handheld.launcher.core.domain.model.LibraryItem.AndroidApp)
                    ?.componentId?.packageName == fixturePackageName
            },
        )
    }

    @Test
    fun discoveryIncludesARealExternalSystemLauncherComponent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val inventory = PackageManagerAndroidAppDiscovery(context, Dispatchers.IO).discover()

        assertTrue(inventory is CatalogInventory.Complete)
        val systemComponents = inventory.observedItems
            .filterIsInstance<LibraryItem.AndroidApp>()
            .filter { item ->
                val applicationInfo = context.packageManager.getApplicationInfo(
                    item.componentId.packageName,
                    PackageManager.ApplicationInfoFlags.of(0L),
                )
                applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
            }
            .map { it.componentId.itemId.value }
            .sorted()
        assertTrue(systemComponents.isNotEmpty())
        Log.i(LOG_TAG, "external system components=" + systemComponents.joinToString())
        Unit
    }

    private companion object {
        const val LOG_TAG = "F07Discovery"
    }
}
