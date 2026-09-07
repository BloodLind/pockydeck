package dev.handheld.launcher.contract

import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.StatusValue
import dev.handheld.launcher.core.domain.model.SupportedItemAction
import dev.handheld.launcher.core.domain.model.UserItemOverrides

data class LibraryItemCardPresentation(
    val itemId: ItemId,
    val title: String,
    val subtitle: String?,
    val typeLabel: String,
    val badges: List<String>,
    val availability: Availability,
    val supportedActions: Set<SupportedItemAction>,
)

/** Maps domain data to card text/state. Artwork remains a presentation content slot. */
fun interface ItemToCardPresentationAdapter {
    fun present(
        item: LibraryItem,
        overrides: UserItemOverrides?,
    ): LibraryItemCardPresentation
}

/**
 * Formatted status consumed by the shell. Unavailable renders the stable placeholder;
 * Unsupported optional values are omitted rather than presented as temporary failures.
 */
data class StatusPresentation(
    val label: String,
    val value: StatusValue<String>,
    val contentDescription: String,
) {
    init {
        require(label.isNotBlank()) { "Status labels must not be blank" }
        require(contentDescription.isNotBlank()) { "Status descriptions must not be blank" }
    }

    val shouldRender: Boolean
        get() = value !is StatusValue.Unsupported

    val displayedValue: String?
        get() = when (val current = value) {
            is StatusValue.Available -> current.value
            StatusValue.Unavailable -> "—"
            StatusValue.Unsupported -> null
        }
}
