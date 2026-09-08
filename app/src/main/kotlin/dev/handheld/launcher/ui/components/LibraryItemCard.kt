package dev.handheld.launcher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.designsystem.cards.AppIconArtwork
import dev.handheld.launcher.core.designsystem.cards.AppIconTile
import dev.handheld.launcher.core.designsystem.cards.ArtworkFallback
import dev.handheld.launcher.core.designsystem.cards.CardVariant
import dev.handheld.launcher.core.designsystem.cards.CoverTile
import dev.handheld.launcher.core.designsystem.cards.CoverArtwork
import dev.handheld.launcher.ui.artwork.enriched.rememberEnrichedArtwork
import dev.handheld.launcher.ui.artwork.enriched.ArtworkPendingHint
import dev.handheld.launcher.core.designsystem.cards.SearchResultCard
import dev.handheld.launcher.core.designsystem.controls.PlatformBadge
import dev.handheld.launcher.core.designsystem.foundation.LauncherText
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.UnavailabilityReason
import dev.handheld.launcher.ui.artwork.local.AndroidIconLoader
import dev.handheld.launcher.ui.artwork.local.rememberAndroidIconPainter
import dev.handheld.launcher.ui.presentation.TileArtwork
import dev.handheld.launcher.ui.presentation.TileUiModel
import dev.handheld.launcher.ui.presentation.platformAccent

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
    statusLabel: String? = null,
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
    val tagLabel = when {
        model.platformLabel == "ANDROID APP" && model.subtitle == "Emulator" -> "EMU"
        model.platformLabel == "ANDROID APP" && model.subtitle == "Game" -> "ANDROID"
        model.platformLabel == "ANDROID APP" -> "APP"
        else -> model.platformLabel
    }
    val badge: @Composable () -> Unit = { PlatformBadge(tagLabel, accentColor = model.platformAccent, compact = true) }
    val status: (@Composable () -> Unit)? = statusLabel?.takeIf(String::isNotBlank)?.let { label ->
        { RecentActivityBadge(label, iconOnly = variant == LibraryItemCardVariant.SearchResult) }
    }
    val contextualSubtitle = model.subtitle?.takeUnless {
        it.equals(model.platformLabel, ignoreCase = true) || it in setOf("App", "Game", "Emulator", "System")
    }

    if (model.artwork is TileArtwork.AndroidIcon && variant != LibraryItemCardVariant.SearchResult) {
        AppIconTile(
            title = model.title,
            activationEnabled = activationEnabled,
            onActivate = onActivate,
            onFocusChanged = onFocusChanged,
            modifier = cardModifier,
            icon = artwork,
            subtitle = if (variant == LibraryItemCardVariant.Collection) contextualSubtitle else null,
            selected = selected,
            unavailable = unavailable,
            unavailableReason = unavailableReason,
            focusFrameWidth = focusFrameWidth,
            focusLift = focusLift,
            showCaption = variant == LibraryItemCardVariant.Collection,
            badge = badge,
            status = status,
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
            status = status,
            selected = selected,
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
            subtitle = contextualSubtitle,
            badge = badge,
            status = status,
            selected = selected,
            unavailable = unavailable,
            unavailableReason = unavailableReason,
            focusFrameWidth = focusFrameWidth,
            focusLift = focusLift,
            subtitleColor = model.platformAccent.takeIf { model.typeLabel == "Game" },
        )

        LibraryItemCardVariant.SearchResult -> SearchResultCard(
            title = model.title,
            subtitle = statusLabel?.takeIf(String::isNotBlank) ?: contextualSubtitle,
            activationEnabled = activationEnabled,
            onActivate = onActivate,
            onFocusChanged = onFocusChanged,
            modifier = cardModifier,
            artwork = artwork,
            badge = badge,
            status = status,
            selected = selected,
            unavailable = unavailable,
            unavailableReason = unavailableReason,
            focusFrameWidth = focusFrameWidth,
            focusLift = focusLift,
            subtitleColor = model.platformAccent.takeIf { model.typeLabel == "Game" },
        )
    }
}

/** The dot indicates recent activity; the accessible label retains the emulator and full meaning. */
@Composable
private fun RecentActivityBadge(label: String, iconOnly: Boolean) {
    val spacing = LauncherTheme.spacing
    Row(Modifier.clearAndSetSemantics { contentDescription = label }
        .background(LauncherTheme.colors.surfaceDock, RoundedCornerShape(LauncherTheme.shapes.smallControl))
        .padding(horizontal = spacing.xxs, vertical = spacing.xxs / 2),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size((6.dp * LauncherTheme.referenceScale).coerceAtLeast(3.dp))
            .background(LauncherTheme.colors.textSecondary, CircleShape))
        if (!iconOnly) {
            Spacer(Modifier.width(spacing.xxs))
            val emulator = label.substringBefore(" · ").takeIf { " · " in label }
            LauncherText(if (emulator == null) "Recent" else "$emulator\nRecently active",
                style = LauncherTheme.typography.badgeLabel, maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TileArtwork(
    model: TileUiModel,
    iconPainter: Painter?,
) {
    val enriched = rememberEnrichedArtwork(model)
    Box(Modifier.fillMaxSize()) {
        when {
            enriched.painter != null -> CoverArtwork(enriched.painter)
            iconPainter != null -> AppIconArtwork(iconPainter)
            model.artwork is TileArtwork.LocalReference -> ArtworkFallback(label = "Custom artwork unavailable")
            else -> ArtworkFallback(label = model.typeLabel)
        }
        if (enriched.pending) ArtworkPendingHint(Modifier.align(Alignment.TopStart).padding(6.dp))
    }
}

private val UnavailabilityReason.presentationLabel: String
    get() = when (this) {
        UnavailabilityReason.REMOVED -> "Removed"
        UnavailabilityReason.SOURCE_UNAVAILABLE -> "Source unavailable"
        UnavailabilityReason.UNSUPPORTED -> "Unsupported"
        UnavailabilityReason.UNKNOWN -> "Unavailable"
    }
