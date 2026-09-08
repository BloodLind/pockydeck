package dev.handheld.launcher.core.domain.model

enum class LibraryItemKind {
    ANDROID_APP,
    ROM_GAME,
    SYSTEM_ACTION,
}

enum class LibraryCategory {
    GAME,
    EMULATOR,
    OTHER,
    SYSTEM,
}

sealed interface Availability {
    data object Available : Availability

    data class Unavailable(val reason: UnavailabilityReason) : Availability
}

enum class UnavailabilityReason {
    REMOVED,
    SOURCE_UNAVAILABLE,
    UNSUPPORTED,
    UNKNOWN,
}

enum class SupportedItemAction {
    OPEN,
    VIEW_DETAILS,
    TOGGLE_FAVORITE,
    OPEN_APP_INFO,
}

sealed interface CatalogProvenance {
    data object AndroidDiscovery : CatalogProvenance

    data class UserSource(val sourceId: CatalogSourceId) : CatalogProvenance

    data object BuiltIn : CatalogProvenance
}

sealed interface LaunchTarget {
    data class AndroidComponent(
        val componentId: CurrentUserAndroidComponentId,
    ) : LaunchTarget

    /** Opaque boundary resolved by the later ROM adapter; it carries no path or format claim. */
    data class ExternalContent(val itemId: ItemId) : LaunchTarget

    data class InternalAction(val actionId: SystemActionId) : LaunchTarget
}

sealed interface LibraryItem {
    val id: ItemId
    val title: String
    val kind: LibraryItemKind
    val category: LibraryCategory
    val availability: Availability
    val supportedActions: Set<SupportedItemAction>
    val provenance: CatalogProvenance
    val launchTarget: LaunchTarget

    data class AndroidApp(
        val componentId: CurrentUserAndroidComponentId,
        override val title: String,
        override val category: LibraryCategory,
        override val availability: Availability,
        override val supportedActions: Set<SupportedItemAction>,
    ) : LibraryItem {
        init {
            require(title.isNotBlank()) { "Item titles must not be blank" }
            require(category != LibraryCategory.SYSTEM) {
                "Android applications cannot use the system-action category"
            }
        }

        override val id: ItemId = componentId.itemId
        override val kind: LibraryItemKind = LibraryItemKind.ANDROID_APP
        override val provenance: CatalogProvenance = CatalogProvenance.AndroidDiscovery
        override val launchTarget: LaunchTarget = LaunchTarget.AndroidComponent(componentId)
    }

    data class RomGame(
        override val id: ItemId,
        override val title: String,
        val sourceId: CatalogSourceId,
        override val availability: Availability,
        override val supportedActions: Set<SupportedItemAction>,
        val platformId: String? = null,
        val format: String? = null,
    ) : LibraryItem {
        init {
            require(title.isNotBlank()) { "Item titles must not be blank" }
        }

        override val kind: LibraryItemKind = LibraryItemKind.ROM_GAME
        override val category: LibraryCategory = LibraryCategory.GAME
        override val provenance: CatalogProvenance = CatalogProvenance.UserSource(sourceId)
        override val launchTarget: LaunchTarget = LaunchTarget.ExternalContent(id)
    }

    data class SystemAction(
        val actionId: SystemActionId,
        override val title: String,
        override val availability: Availability,
        override val supportedActions: Set<SupportedItemAction>,
    ) : LibraryItem {
        init {
            require(title.isNotBlank()) { "Item titles must not be blank" }
        }

        override val id: ItemId = actionId.itemId
        override val kind: LibraryItemKind = LibraryItemKind.SYSTEM_ACTION
        override val category: LibraryCategory = LibraryCategory.SYSTEM
        override val provenance: CatalogProvenance = CatalogProvenance.BuiltIn
        override val launchTarget: LaunchTarget = LaunchTarget.InternalAction(actionId)
    }
}

val LibraryItem.isRecencyEligible: Boolean
    get() = kind != LibraryItemKind.SYSTEM_ACTION

/** User-owned values are persisted separately from replaceable discovered fields. */
data class UserItemOverrides(
    val category: LibraryCategory? = null,
    val artworkReference: UserArtworkReference? = null,
)
