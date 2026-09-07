package dev.handheld.launcher.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import dev.handheld.launcher.contract.LauncherNavigationPort
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LauncherDestination
import dev.handheld.launcher.core.domain.model.LauncherLocation
import dev.handheld.launcher.core.domain.model.NavigationOrigin
import dev.handheld.launcher.core.domain.model.PageStateKey
import dev.handheld.launcher.core.domain.policy.NavigationBackPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Compact shell navigation with no implicit destination history. The F01 policy owns the Back
 * result; this controller only publishes the resulting current location for routes to render.
 */
class LauncherNavigationController(
    initialLocation: LauncherLocation = LauncherLocation.Destination(LauncherDestination.HOME),
) : LauncherNavigationPort {
    private val mutableLocation = MutableStateFlow(initialLocation)

    override val location: StateFlow<LauncherLocation> = mutableLocation.asStateFlow()

    override fun selectDestination(destination: LauncherDestination) {
        mutableLocation.value = LauncherLocation.Destination(destination)
    }

    override fun openShortcutSearch(origin: LauncherDestination) {
        mutableLocation.value = LauncherLocation.ShortcutSearch(origin)
    }

    override fun openItemDetails(itemId: ItemId, origin: NavigationOrigin) {
        mutableLocation.value = LauncherLocation.ItemDetails(itemId, origin)
    }

    override fun back() {
        mutableLocation.value = NavigationBackPolicy.resolve(mutableLocation.value)
    }
}

/** The dock selection shown behind an overlay follows its origin without claiming focus. */
fun LauncherLocation.selectedDockDestination(): LauncherDestination = when (this) {
    is LauncherLocation.Destination -> destination
    is LauncherLocation.ShortcutSearch -> LauncherDestination.SEARCH
    is LauncherLocation.ItemDetails -> when (val origin = origin) {
        is NavigationOrigin.Destination -> origin.destination
        is NavigationOrigin.ShortcutSearch -> LauncherDestination.SEARCH
    }
}

/** A registered destination owns an independent stable state key; it never owns the shell. */
@Immutable
data class LauncherDestinationRoute(
    val destination: LauncherDestination,
    val snapshotKey: PageStateKey = PageStateKey("destination.${destination.persistedKey}"),
    val content: @Composable (LauncherRouteContext) -> Unit,
)

/** Inputs shared by current fixture routes and later production destination route adapters. */
@Immutable
data class LauncherRouteContext(
    val location: LauncherLocation,
    val snapshotKey: PageStateKey,
    val modifier: Modifier,
)

/**
 * The one registration boundary for the six dock positions. F05 can attach restoration to the
 * supplied [snapshotKey] without asking a page to understand shell state or navigation history.
 */
class LauncherRouteRegistry(routes: List<LauncherDestinationRoute>) {
    private val routesByDestination = routes.associateBy(LauncherDestinationRoute::destination)

    init {
        require(routesByDestination.size == LauncherDestination.dockOrder.size) {
            "A shell route registry must provide exactly one route for every dock destination"
        }
        require(routesByDestination.keys.containsAll(LauncherDestination.dockOrder)) {
            "A shell route registry is missing a dock destination"
        }
        require(routes.map(LauncherDestinationRoute::snapshotKey).distinct().size == routes.size) {
            "Destination snapshot keys must remain independent"
        }
    }

    fun routeFor(location: LauncherLocation): LauncherDestinationRoute = routesByDestination.getValue(
        when (location) {
            is LauncherLocation.Destination -> location.destination
            is LauncherLocation.ShortcutSearch -> LauncherDestination.SEARCH
            is LauncherLocation.ItemDetails -> when (val origin = location.origin) {
                is NavigationOrigin.Destination -> origin.destination
                is NavigationOrigin.ShortcutSearch -> LauncherDestination.SEARCH
            }
        },
    )

    @Composable
    fun Render(location: LauncherLocation, modifier: Modifier = Modifier) {
        val route = routeFor(location)
        route.content(LauncherRouteContext(location, route.snapshotKey, modifier))
    }
}
