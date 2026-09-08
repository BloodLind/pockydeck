package dev.handheld.launcher.ui.presentation

import androidx.compose.runtime.Immutable
import dev.handheld.launcher.contract.ItemToCardPresentationAdapter
import dev.handheld.launcher.contract.LibraryItemCardPresentation
import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.CurrentUserAndroidComponentId
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LibraryCategory
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.LibraryItemKind
import dev.handheld.launcher.core.domain.model.SupportedItemAction
import dev.handheld.launcher.core.domain.model.UserArtworkReference
import dev.handheld.launcher.core.domain.model.UserItemOverrides

@Immutable
sealed interface TileArtwork {
    data class AndroidIcon(
        val componentId: CurrentUserAndroidComponentId,
    ) : TileArtwork

    data class LocalReference(
        val reference: UserArtworkReference,
    ) : TileArtwork

    data object Rom : TileArtwork

    data object Fallback : TileArtwork
}

@Immutable
data class TileUiModel(
    val itemId: ItemId,
    val title: String,
    val subtitle: String?,
    val typeLabel: String,
    val platformLabel: String,
    val primaryActionLabel: String,
    val badges: List<String>,
    val availability: Availability,
    val supportedActions: Set<SupportedItemAction>,
    val artwork: TileArtwork,
    val platformId: String? = null,
) {
    val canOpen: Boolean
        get() = availability == Availability.Available &&
            SupportedItemAction.OPEN in supportedActions
}

/** Production text adapter shared by Home and every catalog destination. */
object DefaultItemToCardPresentationAdapter : ItemToCardPresentationAdapter {
    override fun present(
        item: LibraryItem,
        overrides: UserItemOverrides?,
    ): LibraryItemCardPresentation {
        val effectiveCategory = overrides?.category ?: item.category
        return LibraryItemCardPresentation(
            itemId = item.id,
            title = item.title,
            subtitle = if (item is LibraryItem.RomGame) item.consoleLabel else effectiveCategory.presentationLabel,
            typeLabel = item.kind.typeLabel,
            badges = buildList {
                if (item.availability !is Availability.Available) add("Unavailable")
            },
            availability = item.availability,
            supportedActions = item.supportedActions,
        )
    }
}

fun LibraryItem.toTileUiModel(
    overrides: UserItemOverrides? = null,
    recentlyOpened: Boolean = false,
    adapter: ItemToCardPresentationAdapter = DefaultItemToCardPresentationAdapter,
): TileUiModel {
    val presentation = adapter.present(this, overrides)
    val effectiveCategory = overrides?.category ?: category
    return TileUiModel(
        itemId = presentation.itemId,
        title = presentation.title,
        subtitle = presentation.subtitle,
        typeLabel = presentation.typeLabel,
        platformLabel = when (kind) {
            LibraryItemKind.ANDROID_APP -> "ANDROID APP"
            LibraryItemKind.ROM_GAME -> (this as LibraryItem.RomGame).consoleLabel
            LibraryItemKind.SYSTEM_ACTION -> "SYSTEM"
        },
        primaryActionLabel = when {
            recentlyOpened -> "Reopen"
            kind == LibraryItemKind.ROM_GAME -> "Play"
            else -> "Open"
        },
        badges = presentation.badges + if (effectiveCategory == LibraryCategory.GAME) {
            listOf("Game")
        } else {
            emptyList()
        },
        availability = presentation.availability,
        supportedActions = presentation.supportedActions,
        artwork = overrides?.artworkReference?.let(TileArtwork::LocalReference) ?: when (this) {
            is LibraryItem.AndroidApp -> TileArtwork.AndroidIcon(componentId)
            is LibraryItem.RomGame -> TileArtwork.Rom
            else -> TileArtwork.Fallback
        },
        platformId = (this as? LibraryItem.RomGame)?.platformId,
    )
}

val LibraryItem.RomGame.consoleLabel: String
    get() = RomPlatformLabels.shortLabel(platformId)

private val LibraryCategory.presentationLabel: String
    get() = when (this) {
        LibraryCategory.GAME -> "Game"
        LibraryCategory.EMULATOR -> "Emulator"
        LibraryCategory.OTHER -> "App"
        LibraryCategory.SYSTEM -> "System"
    }

private val LibraryItemKind.typeLabel: String
    get() = when (this) {
        LibraryItemKind.ANDROID_APP -> "App"
        LibraryItemKind.ROM_GAME -> "Game"
        LibraryItemKind.SYSTEM_ACTION -> "Action"
    }
