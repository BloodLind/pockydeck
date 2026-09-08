package dev.handheld.launcher.core.domain.policy

import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LibraryCategory
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.LibraryItemKind
import dev.handheld.launcher.core.domain.model.SuccessfulOpenRecord
import dev.handheld.launcher.core.domain.model.UserItemOverrides

/** Recent items lead a stable console showcase; additional games stay in the full library. */
object HomeItemOrdering {
    fun order(
        items: List<LibraryItem>,
        records: List<SuccessfulOpenRecord>,
        overrides: Map<ItemId, UserItemOverrides> = emptyMap(),
    ): List<LibraryItem> {
        val active = items.filter { it.availability == Availability.Available && it.kind != LibraryItemKind.SYSTEM_ACTION }
        val latestById = records.groupBy { it.itemId }.mapValues { (_, values) -> values.maxOf { it.openOrder } }
        val recent = active.filter { it.id in latestById }.sortedWith(
            compareByDescending<LibraryItem> { latestById.getValue(it.id) }.then(LibraryItemOrdering.titleThenId),
        )
        fun gameGroup(item: LibraryItem): String? = when {
            item is LibraryItem.RomGame -> item.platformId?.let { "rom:$it" }
            item.kind == LibraryItemKind.ANDROID_APP &&
                (overrides[item.id]?.category ?: item.category) == LibraryCategory.GAME -> "android"
            else -> null
        }
        val representedGroups = recent.mapNotNull(::gameGroup).toSet()
        val remaining = active.filterNot { it.id in latestById }
        // Sorting before choosing representatives makes the showcase independent of
        // scan order, Compose recomposition, and the order of history records.
        val showcase = remaining.filter { gameGroup(it)?.let { group -> group !in representedGroups } == true }
            .sortedWith(compareBy<LibraryItem> { if (it.kind == LibraryItemKind.ANDROID_APP) 0 else 1 }
                .then(LibraryItemOrdering.titleThenId))
            .distinctBy(::gameGroup)
        val apps = remaining.filter { it.kind == LibraryItemKind.ANDROID_APP && gameGroup(it) == null }
            .sortedWith(LibraryItemOrdering.titleThenId)
        return recent + showcase + apps
    }
}
