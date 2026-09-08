package dev.handheld.launcher.core.data.local

import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.CatalogSourceId
import dev.handheld.launcher.core.domain.model.CurrentUserAndroidComponentId
import dev.handheld.launcher.core.domain.model.IncompleteInventoryReason
import dev.handheld.launcher.core.domain.model.InventoryScope
import dev.handheld.launcher.core.domain.model.InventoryStatus
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LibraryCategory
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.LibraryItemKind
import dev.handheld.launcher.core.domain.model.SupportedItemAction
import dev.handheld.launcher.core.domain.model.SystemActionId
import dev.handheld.launcher.core.domain.model.UnavailabilityReason
import dev.handheld.launcher.core.domain.model.UserArtworkReference
import dev.handheld.launcher.core.domain.model.UserItemOverrides

internal data class StoredCatalogItem(
    val item: CatalogItemEntity,
    val provenance: CatalogProvenanceEntity,
    val actions: List<CatalogItemActionEntity>,
)

internal fun LibraryItem.toStoredCatalogItem(): StoredCatalogItem {
    val availabilityCode: String
    val reasonCode: String?
    when (val currentAvailability = availability) {
        Availability.Available -> {
            availabilityCode = PersistenceCodes.AVAILABLE
            reasonCode = null
        }

        is Availability.Unavailable -> {
            availabilityCode = PersistenceCodes.UNAVAILABLE
            reasonCode = currentAvailability.reason.toPersistenceCode()
        }
    }

    val provenanceEntity = when (this) {
        is LibraryItem.AndroidApp -> CatalogProvenanceEntity(
            itemId = id.value,
            provenanceCode = PersistenceCodes.ANDROID_DISCOVERY,
            sourceId = null,
            androidPackageName = componentId.packageName,
            androidActivityClassName = componentId.activityClassName,
            systemActionId = null,
        )

        is LibraryItem.RomGame -> CatalogProvenanceEntity(
            itemId = id.value,
            provenanceCode = PersistenceCodes.USER_SOURCE,
            sourceId = sourceId.value,
            androidPackageName = null,
            androidActivityClassName = null,
            systemActionId = null,
        )

        is LibraryItem.SystemAction -> CatalogProvenanceEntity(
            itemId = id.value,
            provenanceCode = PersistenceCodes.BUILT_IN,
            sourceId = null,
            androidPackageName = null,
            androidActivityClassName = null,
            systemActionId = actionId.value,
        )
    }

    return StoredCatalogItem(
        item = CatalogItemEntity(
            itemId = id.value,
            kindCode = kind.toPersistenceCode(),
            title = title,
            categoryCode = category.toPersistenceCode(),
            availabilityCode = availabilityCode,
            unavailabilityReasonCode = reasonCode,
            platformId = (this as? LibraryItem.RomGame)?.platformId,
            romFormat = (this as? LibraryItem.RomGame)?.format,
        ),
        provenance = provenanceEntity,
        actions = supportedActions
            .sortedBy(SupportedItemAction::name)
            .map { CatalogItemActionEntity(id.value, it.toPersistenceCode()) },
    )
}

internal fun CatalogItemRecord.toDomain(): LibraryItem {
    val storedProvenance = requireNotNull(provenance) {
        "Catalog item ${item.itemId} has no provenance row"
    }
    require(storedProvenance.itemId == item.itemId) {
        "Catalog provenance identity does not match its item"
    }
    val itemId = ItemId(item.itemId)
    val availability = item.toAvailability()
    val category = item.categoryCode.toLibraryCategory()
    val supportedActions = actions.mapTo(linkedSetOf()) { it.actionCode.toSupportedAction() }

    return when (item.kindCode.toLibraryItemKind()) {
        LibraryItemKind.ANDROID_APP -> {
            require(storedProvenance.provenanceCode == PersistenceCodes.ANDROID_DISCOVERY)
            val component = CurrentUserAndroidComponentId(
                packageName = requireNotNull(storedProvenance.androidPackageName),
                activityClassName = requireNotNull(storedProvenance.androidActivityClassName),
            )
            require(component.itemId == itemId) { "Android component identity does not match item ID" }
            LibraryItem.AndroidApp(component, item.title, category, availability, supportedActions)
        }

        LibraryItemKind.ROM_GAME -> {
            require(storedProvenance.provenanceCode == PersistenceCodes.USER_SOURCE)
            require(category == LibraryCategory.GAME) { "ROM records must use the game category" }
            LibraryItem.RomGame(
                id = itemId,
                title = item.title,
                sourceId = CatalogSourceId(requireNotNull(storedProvenance.sourceId)),
                availability = availability,
                supportedActions = supportedActions,
                platformId = item.platformId,
                format = item.romFormat,
            )
        }

        LibraryItemKind.SYSTEM_ACTION -> {
            require(storedProvenance.provenanceCode == PersistenceCodes.BUILT_IN)
            require(category == LibraryCategory.SYSTEM) {
                "System-action records must use the system category"
            }
            val actionId = SystemActionId(requireNotNull(storedProvenance.systemActionId))
            require(actionId.itemId == itemId) { "System action identity does not match item ID" }
            LibraryItem.SystemAction(actionId, item.title, availability, supportedActions)
        }
    }
}

internal fun InventoryStatus.toEntity(): InventoryStatusEntity? = when (this) {
    InventoryStatus.NotRequested -> null
    is InventoryStatus.Complete -> scope.toStatusEntity(PersistenceCodes.COMPLETE, null)
    is InventoryStatus.Incomplete -> scope.toStatusEntity(
        PersistenceCodes.INCOMPLETE,
        reason.toPersistenceCode(),
    )
}

internal fun InventoryStatusEntity.toDomain(): InventoryStatus {
    require(singletonId == InventoryStatusEntity.SINGLETON_ID)
    val scope = when (scopeCode) {
        PersistenceCodes.CURRENT_USER_ANDROID -> InventoryScope.CurrentUserAndroid
        PersistenceCodes.USER_SOURCE_SCOPE -> InventoryScope.UserSource(
            CatalogSourceId(requireNotNull(sourceId)),
        )

        else -> error("Unsupported inventory scope code: $scopeCode")
    }
    return when (stateCode) {
        PersistenceCodes.COMPLETE -> InventoryStatus.Complete(scope)
        PersistenceCodes.INCOMPLETE -> InventoryStatus.Incomplete(
            scope = scope,
            reason = requireNotNull(incompleteReasonCode).toIncompleteInventoryReason(),
        )

        else -> error("Unsupported inventory state code: $stateCode")
    }
}

internal fun UserItemOverrides.toEntity(itemId: ItemId): ItemOverrideEntity = ItemOverrideEntity(
    itemId = itemId.value,
    categoryCode = category?.toPersistenceCode(),
    artworkReference = artworkReference?.value,
)

internal fun ItemOverrideEntity.toDomain(): UserItemOverrides = UserItemOverrides(
    category = categoryCode?.toLibraryCategory(),
    artworkReference = artworkReference?.let(::UserArtworkReference),
)

private fun InventoryScope.toStatusEntity(
    stateCode: String,
    incompleteReasonCode: String?,
): InventoryStatusEntity = when (this) {
    InventoryScope.CurrentUserAndroid -> InventoryStatusEntity(
        stateCode = stateCode,
        scopeCode = PersistenceCodes.CURRENT_USER_ANDROID,
        sourceId = null,
        incompleteReasonCode = incompleteReasonCode,
    )

    is InventoryScope.UserSource -> InventoryStatusEntity(
        stateCode = stateCode,
        scopeCode = PersistenceCodes.USER_SOURCE_SCOPE,
        sourceId = sourceId.value,
        incompleteReasonCode = incompleteReasonCode,
    )
}

private fun CatalogItemEntity.toAvailability(): Availability = when (availabilityCode) {
    PersistenceCodes.AVAILABLE -> {
        require(unavailabilityReasonCode == null)
        Availability.Available
    }

    PersistenceCodes.UNAVAILABLE -> Availability.Unavailable(
        requireNotNull(unavailabilityReasonCode).toUnavailabilityReason(),
    )

    else -> error("Unsupported availability code: $availabilityCode")
}

private fun LibraryItemKind.toPersistenceCode(): String = when (this) {
    LibraryItemKind.ANDROID_APP -> PersistenceCodes.ANDROID_APP
    LibraryItemKind.ROM_GAME -> PersistenceCodes.ROM_GAME
    LibraryItemKind.SYSTEM_ACTION -> PersistenceCodes.SYSTEM_ACTION
}

private fun String.toLibraryItemKind(): LibraryItemKind = when (this) {
    PersistenceCodes.ANDROID_APP -> LibraryItemKind.ANDROID_APP
    PersistenceCodes.ROM_GAME -> LibraryItemKind.ROM_GAME
    PersistenceCodes.SYSTEM_ACTION -> LibraryItemKind.SYSTEM_ACTION
    else -> error("Unsupported item-kind code: $this")
}

private fun LibraryCategory.toPersistenceCode(): String = when (this) {
    LibraryCategory.GAME -> PersistenceCodes.GAME
    LibraryCategory.EMULATOR -> PersistenceCodes.EMULATOR
    LibraryCategory.OTHER -> PersistenceCodes.OTHER
    LibraryCategory.SYSTEM -> PersistenceCodes.SYSTEM
}

private fun String.toLibraryCategory(): LibraryCategory = when (this) {
    PersistenceCodes.GAME -> LibraryCategory.GAME
    PersistenceCodes.EMULATOR -> LibraryCategory.EMULATOR
    PersistenceCodes.OTHER -> LibraryCategory.OTHER
    PersistenceCodes.SYSTEM -> LibraryCategory.SYSTEM
    else -> error("Unsupported category code: $this")
}

private fun SupportedItemAction.toPersistenceCode(): String = when (this) {
    SupportedItemAction.OPEN -> PersistenceCodes.OPEN
    SupportedItemAction.VIEW_DETAILS -> PersistenceCodes.VIEW_DETAILS
    SupportedItemAction.TOGGLE_FAVORITE -> PersistenceCodes.TOGGLE_FAVORITE
    SupportedItemAction.OPEN_APP_INFO -> PersistenceCodes.OPEN_APP_INFO
}

private fun String.toSupportedAction(): SupportedItemAction = when (this) {
    PersistenceCodes.OPEN -> SupportedItemAction.OPEN
    PersistenceCodes.VIEW_DETAILS -> SupportedItemAction.VIEW_DETAILS
    PersistenceCodes.TOGGLE_FAVORITE -> SupportedItemAction.TOGGLE_FAVORITE
    PersistenceCodes.OPEN_APP_INFO -> SupportedItemAction.OPEN_APP_INFO
    else -> error("Unsupported item-action code: $this")
}

private fun UnavailabilityReason.toPersistenceCode(): String = when (this) {
    UnavailabilityReason.REMOVED -> PersistenceCodes.REMOVED
    UnavailabilityReason.SOURCE_UNAVAILABLE -> PersistenceCodes.SOURCE_UNAVAILABLE
    UnavailabilityReason.UNSUPPORTED -> PersistenceCodes.UNSUPPORTED
    UnavailabilityReason.UNKNOWN -> PersistenceCodes.UNKNOWN
}

private fun String.toUnavailabilityReason(): UnavailabilityReason = when (this) {
    PersistenceCodes.REMOVED -> UnavailabilityReason.REMOVED
    PersistenceCodes.SOURCE_UNAVAILABLE -> UnavailabilityReason.SOURCE_UNAVAILABLE
    PersistenceCodes.UNSUPPORTED -> UnavailabilityReason.UNSUPPORTED
    PersistenceCodes.UNKNOWN -> UnavailabilityReason.UNKNOWN
    else -> error("Unsupported unavailability-reason code: $this")
}

private fun IncompleteInventoryReason.toPersistenceCode(): String = when (this) {
    IncompleteInventoryReason.PARTIAL -> PersistenceCodes.PARTIAL
    IncompleteInventoryReason.FAILED -> PersistenceCodes.FAILED
    IncompleteInventoryReason.CANCELLED -> PersistenceCodes.CANCELLED
}

private fun String.toIncompleteInventoryReason(): IncompleteInventoryReason = when (this) {
    PersistenceCodes.PARTIAL -> IncompleteInventoryReason.PARTIAL
    PersistenceCodes.FAILED -> IncompleteInventoryReason.FAILED
    PersistenceCodes.CANCELLED -> IncompleteInventoryReason.CANCELLED
    else -> error("Unsupported incomplete-inventory reason code: $this")
}

private object PersistenceCodes {
    const val ANDROID_APP = "android_app"
    const val ROM_GAME = "rom_game"
    const val SYSTEM_ACTION = "system_action"
    const val GAME = "game"
    const val EMULATOR = "emulator"
    const val OTHER = "other"
    const val SYSTEM = "system"
    const val AVAILABLE = "available"
    const val UNAVAILABLE = "unavailable"
    const val REMOVED = "removed"
    const val SOURCE_UNAVAILABLE = "source_unavailable"
    const val UNSUPPORTED = "unsupported"
    const val UNKNOWN = "unknown"
    const val ANDROID_DISCOVERY = "android_discovery"
    const val USER_SOURCE = "user_source"
    const val BUILT_IN = "built_in"
    const val OPEN = "open"
    const val VIEW_DETAILS = "view_details"
    const val TOGGLE_FAVORITE = "toggle_favorite"
    const val OPEN_APP_INFO = "open_app_info"
    const val COMPLETE = "complete"
    const val INCOMPLETE = "incomplete"
    const val CURRENT_USER_ANDROID = "current_user_android"
    const val USER_SOURCE_SCOPE = "user_source"
    const val PARTIAL = "partial"
    const val FAILED = "failed"
    const val CANCELLED = "cancelled"
}
