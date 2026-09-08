package dev.handheld.launcher.core.domain.policy

import dev.handheld.launcher.core.domain.model.LauncherLocation
import dev.handheld.launcher.core.domain.model.LauncherDestination
import dev.handheld.launcher.core.domain.model.NavigationOrigin

object NavigationBackPolicy {
    fun resolve(location: LauncherLocation): LauncherLocation = when (location) {
        is LauncherLocation.Destination -> when (location.destination) {
            LauncherDestination.SEARCH, LauncherDestination.SETTINGS ->
                LauncherLocation.Destination(LauncherDestination.HOME)
            else -> location
        }

        is LauncherLocation.ShortcutSearch ->
            LauncherLocation.Destination(location.returnDestination)

        is LauncherLocation.ItemDetails -> location.origin.toLocation()
    }

    private fun NavigationOrigin.toLocation(): LauncherLocation = when (this) {
        is NavigationOrigin.Destination -> LauncherLocation.Destination(destination)
        is NavigationOrigin.ShortcutSearch -> LauncherLocation.ShortcutSearch(returnDestination)
    }
}
