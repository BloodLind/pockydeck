package dev.handheld.launcher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.designsystem.cards.AppIconArtwork
import dev.handheld.launcher.core.designsystem.cards.AppIconTile
import dev.handheld.launcher.core.designsystem.cards.ArtworkFallback
import dev.handheld.launcher.core.designsystem.cards.CardVariant
import dev.handheld.launcher.core.designsystem.cards.CoverTile
import dev.handheld.launcher.core.designsystem.cards.SearchResultCard
import dev.handheld.launcher.core.designsystem.controls.PlatformBadge
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.UnavailabilityReason
import dev.handheld.launcher.ui.artwork.local.AndroidIconLoader
import dev.handheld.launcher.ui.artwork.local.rememberAndroidIconPainter
import dev.handheld.launcher.ui.presentation.TileArtwork
import dev.handheld.launcher.ui.presentation.TileUiModel

@Immutable
enum class LibraryItemCardVariant {
    Home,
    Collection,
    SearchResult,
}

/** One production item card used across Home, collections, search, and details surfaces. */
@Composable
fun LibraryItemCard(
    model: TileUiModel,
    variant: LibraryItemCardVariant,
    selected: Boolean,
    activationEnabled: Boolean = model.canOpen,
    modifier: Modifier = Modifier,
    focusFrameWidth: Dp = LauncherTheme.depth.focusedElevation / 2f,
    focusLift: Dp = LauncherTheme.depth.focusLift,
    iconLoader: AndroidIconLoader? = null,
    onActivate: () -> Unit,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    val cardModifier = modifier.semantics { this.selected = selected }
    val unavailable = model.availability !is Availability.Available
    val unavailableReason = (model.availability as? Availability.Unavailable)
        ?.reason
        ?.presentationLabel
        ?: "Unavailable"
    val iconPainter = when (val artwork = model.artwork) {
        is TileArtwork.AndroidIcon -> iconLoader?.let {
            rememberAndroidIconPainter(it, artwork.componentId)
        }
        else -> null
    }
    val artwork: @Composable () -> Unit = {
        TileArtwork(
            model = model,
            iconPainter = iconPainter,
        )
    }
    val badge: (@Composable () -> Unit)? = if (variant == LibraryItemCardVariant.Home) {
        { PlatformBadge(model.platformLabel, homeAccent = true) }
    } else {
        null
    }

    if (model.artwork is TileArtwork.AndroidIcon && variant != LibraryItemCardVariant.SearchResult) {
        AppIconTile(
            title = model.title,
            activationEnabled = activationEnabled,
            onActivate = onActivate,
            onFocusChanged = onFocusChanged,
            modifier = cardModifier,
            icon = artwork,
            subtitle = if (variant == LibraryItemCardVariant.Collection) model.subtitle else null,
            selected = false,
            unavailable = unavailable,
            unavailableReason = unavailableReason,
            focusFrameWidth = focusFrameWidth,
            focusLift = focusLift,
        )
        return
    }

    when (variant) {
        LibraryItemCardVariant.Home -> CoverTile(
            title = model.title,
            variant = CardVariant.HomeCover,
            activationEnabled = activationEnabled,
            onActivate = onActivate,
            onFocusChanged = onFocusChanged,
            modifier = cardModifier,
            artwork = artwork,
            badge = badge,
            selected = false,
            unavailable = unavailable,
            unavailableReason = unavailableReason,
            focusFrameWidth = focusFrameWidth,
            focusLift = focusLift,
        )

        LibraryItemCardVariant.Collection -> CoverTile(
            title = model.title,
            variant = CardVariant.CollectionCover,
            activationEnabled = activationEnabled,
            onActivate = onActivate,
            onFocusChanged = onFocusChanged,
            modifier = cardModifier,
            artwork = artwork,
            subtitle = model.subtitle,
            selected = false,
            unavailable = unavailable,
            unavailableReason = unavailableReason,
            focusFrameWidth = focusFrameWidth,
            focusLift = focusLift,
        )

        LibraryItemCardVariant.SearchResult -> SearchResultCard(
            title = model.title,
            subtitle = model.subtitle,
            activationEnabled = activationEnabled,
            onActivate = onActivate,
            onFocusChanged = onFocusChanged,
            modifier = cardModifier,
            artwork = artwork,
            selected = false,
            unavailable = unavailable,
            unavailableReason = unavailableReason,
            focusFrameWidth = focusFrameWidth,
            focusLift = focusLift,
        )
    }
}

@Composable
private fun TileArtwork(
    model: TileUiModel,
    iconPainter: Painter?,
) {
    when {
        iconPainter != null -> AppIconArtwork(iconPainter)
        model.artwork is TileArtwork.LocalReference -> ArtworkFallback(label = "Custom artwork unavailable")
        else -> ArtworkFallback(label = model.typeLabel)
    }
}

private val UnavailabilityReason.presentationLabel: String
    get() = when (this) {
        UnavailabilityReason.REMOVED -> "Removed"
        UnavailabilityReason.SOURCE_UNAVAILABLE -> "Source unavailable"
        UnavailabilityReason.UNSUPPORTED -> "Unsupported"
        UnavailabilityReason.UNKNOWN -> "Unavailable"
    }
