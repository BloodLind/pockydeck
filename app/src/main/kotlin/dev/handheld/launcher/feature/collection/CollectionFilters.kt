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

private val referenceConsoleOrder = listOf("console:gamecube", "console:psp", "console:ps2", "console:gba")

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
    .sortedWith(compareBy<String> {
        referenceConsoleOrder.indexOf(it).takeIf { index -> index >= 0 } ?: referenceConsoleOrder.size
    }.thenBy { it == "console:unassigned" }.thenBy { collectionFilterLabel(it) })

internal fun romConsoleFilterKey(game: LibraryItem.RomGame): String =
    "console:${game.platformId?.takeIf { RomPlatforms.byId(it) != null } ?: "unassigned"}"

fun collectionFilterLabel(key: String): String = if (key.startsWith("console:")) {
    val platform = RomPlatforms.byId(key.removePrefix("console:"))
    if (platform == null) "Unassigned" else RomPlatformLabels.shortLabel(platform.id)
} else key.replaceFirstChar { it.uppercase() }

data class CollectionFilterOption(val key: String, val count: Int) {
    val label: String get() = "${collectionFilterLabel(key)} ($count)"
}

/** Counts use the same availability/category/favorite rules as the actual page. */
fun collectionFilterOptions(state: CollectionUiState): List<CollectionFilterOption> {
    val scoped = collectionDestinationItems(state.destination, state.allItems, state.overrides, state.favorites)
    return collectionFilterKeys(state.destination, scoped, state.overrides, state.favorites).map { key ->
        CollectionFilterOption(key, if (key == "all") scoped.size else scoped.count { collectionFilterMatches(it, key, state.overrides) })
    }
}

/** The preview exposes a short strip plus More; an overflow selection always stays visible. */
fun primaryCollectionFilters(options: List<CollectionFilterOption>, selected: String, limit: Int = 6): List<CollectionFilterOption> {
    require(limit >= 2)
    val referenceOrder = listOf("all", "android") + referenceConsoleOrder
    val ordered = options.sortedWith(compareBy<CollectionFilterOption> {
        referenceOrder.indexOf(it.key).takeIf { index -> index >= 0 } ?: referenceOrder.size
    }.thenBy { options.indexOf(it) })
    val primary = ordered.take(limit).toMutableList()
    options.firstOrNull { it.key == selected && it !in primary }?.let { primary[primary.lastIndex] = it }
    return primary
}
