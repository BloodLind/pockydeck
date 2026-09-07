package dev.handheld.launcher.core.data.discovery

import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.CatalogSnapshot
import dev.handheld.launcher.core.domain.model.CurrentUserAndroidComponentId
import dev.handheld.launcher.core.domain.model.IncompleteInventoryReason
import dev.handheld.launcher.core.domain.model.InventoryScope
import dev.handheld.launcher.core.domain.model.InventoryStatus
import dev.handheld.launcher.core.domain.model.LibraryCategory
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.SupportedItemAction

/** Deterministic cache-first states for Home previews and consumer tests. */
object AndroidCatalogStateFixtures {
    val initialLoading = AndroidCatalogState(
        snapshot = CatalogSnapshot(emptyList(), InventoryStatus.NotRequested),
        refreshState = AndroidCatalogRefreshState.Refreshing(AndroidCatalogRefreshTrigger.STARTUP),
    )

    val staleCacheRefreshing = AndroidCatalogState(
        snapshot = cachedSnapshot(InventoryStatus.Complete(InventoryScope.CurrentUserAndroid)),
        refreshState = AndroidCatalogRefreshState.Refreshing(AndroidCatalogRefreshTrigger.RESUME),
    )

    val cachedFailure = AndroidCatalogState(
        snapshot = cachedSnapshot(
            InventoryStatus.Incomplete(
                InventoryScope.CurrentUserAndroid,
                IncompleteInventoryReason.FAILED,
            ),
        ),
        refreshState = AndroidCatalogRefreshState.Error(
            AndroidCatalogRefreshTrigger.PACKAGE_CHANGED,
            IncompleteInventoryReason.FAILED,
        ),
    )

    private fun cachedSnapshot(status: InventoryStatus) = CatalogSnapshot(
        items = listOf(
            LibraryItem.AndroidApp(
                componentId = CurrentUserAndroidComponentId(
                    "dev.handheld.fixture",
                    "dev.handheld.fixture.MainActivity",
                ),
                title = "Cached app",
                category = LibraryCategory.OTHER,
                availability = Availability.Available,
                supportedActions = setOf(
                    SupportedItemAction.OPEN,
                    SupportedItemAction.VIEW_DETAILS,
                    SupportedItemAction.TOGGLE_FAVORITE,
                    SupportedItemAction.OPEN_APP_INFO,
                ),
            ),
        ),
        inventoryStatus = status,
    )
}
