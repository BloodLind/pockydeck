package dev.handheld.launcher.core.domain.model

/** Stable destination keys. [dockOrder] is the only supported dock order. */
enum class LauncherDestination(val persistedKey: String) {
    HOME("home"),
    LIBRARY("library"),
    APPS("apps"),
    FAVORITES("favorites"),
    SETTINGS("settings"),
    SEARCH("search"),
    ;

    companion object {
        val dockOrder: List<LauncherDestination> = listOf(
            HOME,
            LIBRARY,
            APPS,
            FAVORITES,
            SETTINGS,
            SEARCH,
        )

        fun fromPersistedKey(value: String): LauncherDestination? =
            entries.singleOrNull { it.persistedKey == value }
    }
}

@JvmInline
value class PageStateKey(val value: String) {
    init {
        require(value.isNotBlank()) { "Page state keys must not be blank" }
    }
}

/**
 * Compact durable state for one destination. It contains stable keys only; list contents,
 * numeric selection indices, focus objects, and scroll-state objects are intentionally absent.
 */
data class DestinationSnapshot(
    val destination: LauncherDestination,
    val selectedItemId: ItemId? = null,
    val firstVisibleItemId: ItemId? = null,
    val firstVisibleOffsetPx: Int = 0,
    val query: String = "",
    val filterKey: PageStateKey? = null,
    val sortKey: PageStateKey? = null,
) {
    init {
        require(firstVisibleOffsetPx >= 0) { "First-visible offset must not be negative" }
    }
}

sealed interface NavigationOrigin {
    data class Destination(val destination: LauncherDestination) : NavigationOrigin

    data class ShortcutSearch(
        val returnDestination: LauncherDestination,
    ) : NavigationOrigin
}

/** A compact current location, without a general-purpose navigation stack. */
sealed interface LauncherLocation {
    data class Destination(val destination: LauncherDestination) : LauncherLocation

    data class ShortcutSearch(
        val returnDestination: LauncherDestination,
    ) : LauncherLocation

    data class ItemDetails(
        val itemId: ItemId,
        val origin: NavigationOrigin,
    ) : LauncherLocation
}
