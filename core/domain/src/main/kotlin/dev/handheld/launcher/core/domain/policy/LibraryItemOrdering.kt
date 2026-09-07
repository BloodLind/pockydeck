package dev.handheld.launcher.core.domain.policy

import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.SuccessfulOpenRecord
import dev.handheld.launcher.core.domain.model.isRecencyEligible
import java.util.Locale

object LibraryItemOrdering {
    val titleThenId: Comparator<LibraryItem> =
        compareBy<LibraryItem> { it.title.lowercase(Locale.ROOT) }
            .thenBy { it.title }
            .thenBy { it.id.value }

    fun recentFirst(
        items: List<LibraryItem>,
        records: List<SuccessfulOpenRecord>,
    ): List<LibraryItem> {
        val latestOrderByItem = records
            .groupBy(SuccessfulOpenRecord::itemId)
            .mapValues { (_, itemRecords) -> itemRecords.maxOf(SuccessfulOpenRecord::openOrder) }

        val recentComparator = Comparator<LibraryItem> { left, right ->
            val leftOrder = latestOrderByItem[left.id]
            val rightOrder = latestOrderByItem[right.id]
            when {
                leftOrder != null && rightOrder != null -> {
                    compareValues(rightOrder, leftOrder)
                        .takeUnless { it == 0 }
                        ?: titleThenId.compare(left, right)
                }

                leftOrder != null -> -1
                rightOrder != null -> 1
                else -> titleThenId.compare(left, right)
            }
        }

        val eligibleIds = items.asSequence()
            .filter { it.isRecencyEligible }
            .map(LibraryItem::id)
            .toSet()
        val effectiveRecords = latestOrderByItem.filterKeys(eligibleIds::contains)

        return items.sortedWith(
            Comparator { left, right ->
                val leftHasRecord = left.id in effectiveRecords
                val rightHasRecord = right.id in effectiveRecords
                when {
                    leftHasRecord && rightHasRecord -> recentComparator.compare(left, right)
                    leftHasRecord -> -1
                    rightHasRecord -> 1
                    else -> titleThenId.compare(left, right)
                }
            },
        )
    }
}
