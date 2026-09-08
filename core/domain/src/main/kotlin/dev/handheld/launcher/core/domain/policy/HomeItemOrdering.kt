package dev.handheld.launcher.core.domain.policy

import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LibraryCategory
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.LibraryItemKind
import dev.handheld.launcher.core.domain.model.SuccessfulOpenRecord
import dev.handheld.launcher.core.domain.model.UserItemOverrides

/** Home promotes just the newest available item; older history cannot displace the game groups. */
object HomeItemOrdering {
    fun order(
        items: List<LibraryItem>,
        records: List<SuccessfulOpenRecord>,
        overrides: Map<ItemId, UserItemOverrides> = emptyMap(),
    ): List<LibraryItem> {
        val active = items.filter { it.availability == Availability.Available && it.kind != LibraryItemKind.SYSTEM_ACTION }
        val latestById = records.groupBy { it.itemId }.mapValues { (_, values) -> values.maxOf { it.openOrder } }
        val newest = active.filter { it.id in latestById }.minWithOrNull(
            compareByDescending<LibraryItem> { latestById.getValue(it.id) }.then(LibraryItemOrdering.titleThenId),
        )?.id
        fun tier(item: LibraryItem): Int = when {
            item.id == newest -> 0
            item.kind == LibraryItemKind.ANDROID_APP && (overrides[item.id]?.category ?: item.category) == LibraryCategory.GAME -> 1
            item.kind == LibraryItemKind.ROM_GAME -> 2
            else -> 3
        }
        return active.sortedWith(compareBy<LibraryItem>(::tier).then(LibraryItemOrdering.titleThenId))
    }
}
