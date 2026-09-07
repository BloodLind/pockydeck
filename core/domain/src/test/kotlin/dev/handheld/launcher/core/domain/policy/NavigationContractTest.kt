package dev.handheld.launcher.core.domain.policy

import dev.handheld.launcher.core.domain.model.ConfirmBackMapping
import dev.handheld.launcher.core.domain.model.ControllerButtonRole
import dev.handheld.launcher.core.domain.model.ControllerFaceButton
import dev.handheld.launcher.core.domain.model.DestinationSnapshot
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LauncherDestination
import dev.handheld.launcher.core.domain.model.LauncherLocation
import dev.handheld.launcher.core.domain.model.NavigationOrigin
import dev.handheld.launcher.core.domain.model.PageStateKey
import dev.handheld.launcher.core.domain.model.StatusValue
import dev.handheld.launcher.core.domain.repository.InMemoryControllerPreferenceRepository
import dev.handheld.launcher.core.domain.repository.InMemoryNavigationSnapshotRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationContractTest {
    @Test
    fun dockOrderAndBackOriginsAreStableWithoutAHistoryList() {
        assertEquals(
            listOf("home", "library", "apps", "favorites", "settings", "search"),
            LauncherDestination.dockOrder.map { it.persistedKey },
        )

        val library = LauncherLocation.Destination(LauncherDestination.LIBRARY)
        assertEquals(
            LauncherLocation.Destination(LauncherDestination.HOME),
            NavigationBackPolicy.resolve(library),
        )
        val shortcutSearch = LauncherLocation.ShortcutSearch(LauncherDestination.APPS)
        assertEquals(
            LauncherLocation.Destination(LauncherDestination.APPS),
            NavigationBackPolicy.resolve(shortcutSearch),
        )
        val detailsFromShortcut = LauncherLocation.ItemDetails(
            itemId = ItemId("android:fixture/Details"),
            origin = NavigationOrigin.ShortcutSearch(LauncherDestination.FAVORITES),
        )
        assertEquals(
            LauncherLocation.ShortcutSearch(LauncherDestination.FAVORITES),
            NavigationBackPolicy.resolve(detailsFromShortcut),
        )
        val home = LauncherLocation.Destination(LauncherDestination.HOME)
        assertEquals(home, NavigationBackPolicy.resolve(home))
    }

    @Test
    fun destinationSnapshotsRemainCompactAndIndependent() = runBlocking {
        val repository = InMemoryNavigationSnapshotRepository()
        val home = DestinationSnapshot(
            destination = LauncherDestination.HOME,
            selectedItemId = ItemId("android:fixture/Home"),
            firstVisibleItemId = ItemId("android:fixture/Anchor"),
            firstVisibleOffsetPx = 17,
            filterKey = PageStateKey("games"),
            sortKey = PageStateKey("recent"),
        )
        val search = DestinationSnapshot(
            destination = LauncherDestination.SEARCH,
            selectedItemId = ItemId("android:fixture/SearchResult"),
            query = "racing",
            filterKey = PageStateKey("apps"),
            sortKey = PageStateKey("title"),
        )

        repository.save(home)
        repository.save(search)

        assertEquals(home, repository.observe(LauncherDestination.HOME).first())
        assertEquals(search, repository.observe(LauncherDestination.SEARCH).first())
        assertNotEquals(
            repository.observe(LauncherDestination.HOME).first(),
            repository.observe(LauncherDestination.SEARCH).first(),
        )
        assertNull(repository.observe(LauncherDestination.APPS).first())
    }

    @Test
    fun confirmBackDefaultAndStatusStatesAreExplicit() = runBlocking {
        val repository = InMemoryControllerPreferenceRepository()
        assertEquals(ControllerFaceButton.A, ConfirmBackMapping.Default.confirm)
        assertEquals(ControllerFaceButton.B, ConfirmBackMapping.Default.back)
        assertEquals(ConfirmBackMapping.Default, repository.confirmBackMapping.first())
        assertEquals(
            ControllerFaceButton.A,
            ConfirmBackMapping.Default.buttonFor(ControllerButtonRole.CONFIRM),
        )
        assertEquals(
            ControllerButtonRole.BACK,
            ConfirmBackMapping.Default.roleFor(ControllerFaceButton.B),
        )

        val swapped = ConfirmBackMapping(ControllerFaceButton.B, ControllerFaceButton.A)
        repository.setConfirmBackMapping(swapped)
        assertEquals(swapped, repository.confirmBackMapping.first())

        val states: List<StatusValue<Int>> = listOf(
            StatusValue.Available(75),
            StatusValue.Unavailable,
            StatusValue.Unsupported,
        )
        assertEquals(3, states.distinct().size)
        assertTrue(states[0] is StatusValue.Available)
    }
}
