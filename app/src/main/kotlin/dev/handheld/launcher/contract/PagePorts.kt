package dev.handheld.launcher.contract

import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LauncherDestination
import dev.handheld.launcher.core.domain.model.LauncherLocation
import dev.handheld.launcher.core.domain.model.NavigationOrigin
import kotlinx.coroutines.flow.StateFlow

/** Acceptance means queued for the acknowledged launch flow, not successfully dispatched. */
fun interface ItemLaunchPort {
    fun submit(itemId: ItemId): Boolean
}

interface LauncherNavigationPort {
    val location: StateFlow<LauncherLocation>

    fun selectDestination(destination: LauncherDestination)

    fun openShortcutSearch(origin: LauncherDestination)

    fun openItemDetails(itemId: ItemId, origin: NavigationOrigin)

    fun back()
}
