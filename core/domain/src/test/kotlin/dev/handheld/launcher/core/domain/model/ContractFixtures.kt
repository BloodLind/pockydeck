package dev.handheld.launcher.core.domain.model

internal object ContractFixtures {
    val openActions = setOf(
        SupportedItemAction.OPEN,
        SupportedItemAction.VIEW_DETAILS,
        SupportedItemAction.TOGGLE_FAVORITE,
    )

    fun androidApp(
        packageName: String,
        className: String,
        title: String,
        availability: Availability = Availability.Available,
        category: LibraryCategory = LibraryCategory.OTHER,
    ): LibraryItem.AndroidApp = LibraryItem.AndroidApp(
        componentId = CurrentUserAndroidComponentId(packageName, className),
        title = title,
        category = category,
        availability = availability,
        supportedActions = openActions + SupportedItemAction.OPEN_APP_INFO,
    )

    fun rom(
        id: String,
        title: String,
        source: String = "source-1",
    ): LibraryItem.RomGame = LibraryItem.RomGame(
        id = ItemId(id),
        title = title,
        sourceId = CatalogSourceId(source),
        availability = Availability.Available,
        supportedActions = openActions,
    )

    fun systemAction(id: String, title: String): LibraryItem.SystemAction =
        LibraryItem.SystemAction(
            actionId = SystemActionId(id),
            title = title,
            availability = Availability.Available,
            supportedActions = setOf(SupportedItemAction.OPEN),
        )
}
