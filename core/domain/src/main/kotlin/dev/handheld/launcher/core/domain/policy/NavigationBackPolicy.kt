package dev.handheld.launcher.core.domain.policy

import dev.handheld.launcher.core.domain.model.LauncherLocation
import dev.handheld.launcher.core.domain.model.NavigationOrigin

object NavigationBackPolicy {
    fun resolve(location: LauncherLocation): LauncherLocation = when (location) {
        // Dock pages are roots, not an implicit history stack. Only overlays have an origin.
        is LauncherLocation.Destination -> location

        is LauncherLocation.ShortcutSearch ->
            LauncherLocation.Destination(location.returnDestination)

        is LauncherLocation.ItemDetails -> location.origin.toLocation()
    }

    private fun NavigationOrigin.toLocation(): LauncherLocation = when (this) {
        is NavigationOrigin.Destination -> LauncherLocation.Destination(destination)
        is NavigationOrigin.ShortcutSearch -> LauncherLocation.ShortcutSearch(returnDestination)
    }
}
