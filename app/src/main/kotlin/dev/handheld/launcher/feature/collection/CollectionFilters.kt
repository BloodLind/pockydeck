package dev.handheld.launcher.feature.collection

import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LauncherDestination
import dev.handheld.launcher.core.domain.model.LibraryCategory
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.LibraryItemKind
import dev.handheld.launcher.core.domain.model.UserItemOverrides
import dev.handheld.launcher.core.domain.rom.scan.RomPlatforms
import dev.handheld.launcher.ui.presentation.RomPlatformLabels

/** ROMs remain games when a legacy or user category override is present. */
fun collectionCategory(item: LibraryItem, overrides: Map<ItemId, UserItemOverrides>): LibraryCategory =
    if (item.kind == LibraryItemKind.ROM_GAME) LibraryCategory.GAME
    else overrides[item.id]?.category ?: item.category

/** The same destination boundary drives visible items, filters, and category counts. */
fun collectionDestinationItems(
    destination: LauncherDestination,
    items: List<LibraryItem>,
    overrides: Map<ItemId, UserItemOverrides> = emptyMap(),
    favoriteIds: Set<ItemId> = emptySet(),
): List<LibraryItem> = items.filter { item ->
    item.availability == Availability.Available && item.kind != LibraryItemKind.SYSTEM_ACTION && when (destination) {
        LauncherDestination.LIBRARY -> collectionCategory(item, overrides) == LibraryCategory.GAME
        LauncherDestination.APPS -> item.kind == LibraryItemKind.ANDROID_APP &&
            collectionCategory(item, overrides) != LibraryCategory.GAME
        LauncherDestination.FAVORITES -> item.id in favoriteIds
        else -> true
    }
}

fun collectionCategoryDestination(category: LibraryCategory): LauncherDestination =
    if (category == LibraryCategory.GAME) LauncherDestination.LIBRARY else LauncherDestination.APPS

fun collectionCategoryCount(
    category: LibraryCategory,
    items: List<LibraryItem>,
    overrides: Map<ItemId, UserItemOverrides> = emptyMap(),
): Int = collectionDestinationItems(collectionCategoryDestination(category), items, overrides)
    .count { collectionCategory(it, overrides) == category }

/** Shared category vocabulary prevents controller routing and page filters diverging. */
fun collectionFilterKeys(
    destination: LauncherDestination,
    items: List<LibraryItem> = emptyList(),
    overrides: Map<ItemId, UserItemOverrides> = emptyMap(),
    favoriteIds: Set<ItemId> = emptySet(),
): List<String> {
    val scoped = collectionDestinationItems(destination, items, overrides, favoriteIds)
    return when (destination) {
        LauncherDestination.LIBRARY -> listOf("all") +
            (if (scoped.any { it.kind == LibraryItemKind.ANDROID_APP }) listOf("android") else emptyList()) +
            detectedConsoleFilterKeys(scoped)
        LauncherDestination.APPS -> listOf("all") + listOf("emulators", "other").filter { key ->
            scoped.any { collectionFilterMatches(it, key, overrides) }
        }
        LauncherDestination.FAVORITES -> listOf("all") + listOf("games", "apps").filter { key ->
            scoped.any { collectionFilterMatches(it, key, overrides) }
        }
        LauncherDestination.SEARCH -> listOf("all", "games", "apps", "system")
        else -> listOf("all")
    }
}

internal fun collectionFilterMatches(
    item: LibraryItem,
    key: String,
    overrides: Map<ItemId, UserItemOverrides>,
): Boolean = when (key) {
    "games" -> collectionCategory(item, overrides) == LibraryCategory.GAME
    "emulators" -> collectionCategory(item, overrides) == LibraryCategory.EMULATOR
    "other" -> collectionCategory(item, overrides) == LibraryCategory.OTHER
    "android" -> item.kind == LibraryItemKind.ANDROID_APP
    "apps" -> item.kind == LibraryItemKind.ANDROID_APP &&
        collectionCategory(item, overrides) != LibraryCategory.GAME
    "roms" -> item.kind == LibraryItemKind.ROM_GAME
    "system" -> false // Supported internal actions are provided by the root registry.
    else -> if (key.startsWith("console:")) {
        item is LibraryItem.RomGame && romConsoleFilterKey(item) == key
    } else true
}

/** Source health controls which consoles are visible; emulator installation does not hide games. */
fun detectedConsoleFilterKeys(items: List<LibraryItem>): List<String> = items
    .filterIsInstance<LibraryItem.RomGame>()
    .filter { it.availability == Availability.Available }
    .map(::romConsoleFilterKey)
    .distinct()
    .sortedWith(compareBy<String> { it == "console:unassigned" }.thenBy { collectionFilterLabel(it) })

internal fun romConsoleFilterKey(game: LibraryItem.RomGame): String =
    "console:${game.platformId?.takeIf { RomPlatforms.byId(it) != null } ?: "unassigned"}"

fun collectionFilterLabel(key: String): String = if (key.startsWith("console:")) {
    val platform = RomPlatforms.byId(key.removePrefix("console:"))
    if (platform == null) "Unassigned" else RomPlatformLabels.shortLabel(platform.id)
} else key.replaceFirstChar { it.uppercase() }
