package dev.handheld.launcher.navigation

import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LauncherDestination
import dev.handheld.launcher.core.domain.model.LauncherLocation
import dev.handheld.launcher.core.domain.model.NavigationOrigin
import dev.handheld.launcher.core.domain.model.PageStateKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LauncherNavigationControllerTest {
    @Test
    fun dockBackStaysOnEveryRootWithoutReturningToEarlierDestinations() {
        val navigation = LauncherNavigationController()

        for (destination in LauncherDestination.dockOrder) {
            navigation.selectDestination(destination)
            repeat(2) {
                navigation.back()
                assertEquals(LauncherLocation.Destination(destination), navigation.location.value)
                assertEquals(destination, navigation.location.value.selectedDockDestination())
            }
        }
    }

    @Test
    fun shortcutSearchAndDetailsReturnToTheirRecordedOrigins() {
        val navigation = LauncherNavigationController()
        val item = ItemId("android:example.app/example.app.MainActivity")

        navigation.openShortcutSearch(LauncherDestination.APPS)
        assertEquals(LauncherDestination.SEARCH, navigation.location.value.selectedDockDestination())
        navigation.openItemDetails(item, NavigationOrigin.ShortcutSearch(LauncherDestination.APPS))
        assertEquals(LauncherDestination.SEARCH, navigation.location.value.selectedDockDestination())
        navigation.back()
        assertEquals(LauncherLocation.ShortcutSearch(LauncherDestination.APPS), navigation.location.value)
        navigation.back()
        assertEquals(LauncherLocation.Destination(LauncherDestination.APPS), navigation.location.value)
        navigation.back()
        assertEquals(LauncherLocation.Destination(LauncherDestination.APPS), navigation.location.value)

        navigation.openItemDetails(item, NavigationOrigin.Destination(LauncherDestination.FAVORITES))
        assertEquals(LauncherDestination.FAVORITES, navigation.location.value.selectedDockDestination())
        navigation.back()
        assertEquals(LauncherLocation.Destination(LauncherDestination.FAVORITES), navigation.location.value)
        navigation.back()
        assertEquals(LauncherLocation.Destination(LauncherDestination.FAVORITES), navigation.location.value)
    }

    @Test
    fun registryRequiresEveryDockDestinationAndIndependentStateKeys() {
        val routes = LauncherDestination.dockOrder.map { destination ->
            LauncherDestinationRoute(
                destination = destination,
                snapshotKey = PageStateKey("state.${destination.persistedKey}"),
                content = {},
            )
        }
        val registry = LauncherRouteRegistry(routes)

        assertEquals(LauncherDestination.SEARCH, registry.routeFor(
            LauncherLocation.ShortcutSearch(LauncherDestination.HOME),
        ).destination)
        assertNotEquals(
            registry.routeFor(LauncherLocation.Destination(LauncherDestination.HOME)).snapshotKey,
            registry.routeFor(LauncherLocation.Destination(LauncherDestination.LIBRARY)).snapshotKey,
        )
    }
}
