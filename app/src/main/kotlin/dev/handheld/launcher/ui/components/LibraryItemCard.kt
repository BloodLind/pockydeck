package dev.handheld.launcher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.draw.clipToBounds
import dev.handheld.launcher.ui.artwork.LocalArtworkLoadingAllowed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.designsystem.cards.AppIconArtwork
import dev.handheld.launcher.core.designsystem.cards.AppIconTile
import dev.handheld.launcher.core.designsystem.cards.ArtworkFallback
import dev.handheld.launcher.core.designsystem.cards.CardVariant
import dev.handheld.launcher.core.designsystem.cards.CoverTile
import dev.handheld.launcher.core.designsystem.cards.CoverArtwork
import dev.handheld.launcher.ui.artwork.enriched.rememberEnrichedArtwork
import dev.handheld.launcher.ui.artwork.enriched.LocalEnrichedArtworkLoader
import dev.handheld.launcher.ui.artwork.ArtworkDecodePolicy
import dev.handheld.launcher.core.designsystem.cards.SearchResultCard
import dev.handheld.launcher.core.designsystem.controls.PlatformBadge
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.UnavailabilityReason
import dev.handheld.launcher.ui.artwork.local.AndroidIconLoader
import dev.handheld.launcher.ui.artwork.local.rememberAndroidIconPainter
import dev.handheld.launcher.ui.presentation.TileArtwork
import dev.handheld.launcher.ui.presentation.TileUiModel
import dev.handheld.launcher.ui.presentation.platformAccent
import dev.handheld.launcher.ui.presentation.LocalLastPlayed
import dev.handheld.launcher.ui.presentation.LastPlayedIndicator

@Immutable
enum class LibraryItemCardVariant {
    Home,
    Collection,
    SearchResult,
    List,
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
    maxCollectionCardHeight: Dp = Dp.Infinity,
    artworkActive: Boolean = true,
    artworkTargetSizePx: Int = ArtworkDecodePolicy.DEFAULT_TARGET_PX,
    animateArtwork: Boolean = true,
) {
    val cardModifier = modifier.semantics { this.selected = selected }
    val unavailable = model.availability !is Availability.Available
    val unavailableReason = (model.availability as? Availability.Unavailable)
        ?.reason
        ?.presentationLabel
        ?: "Unavailable"
    val artwork: @Composable () -> Unit = {
        LibraryItemArtwork(model, iconLoader, artworkActive, artworkTargetSizePx, animateArtwork)
    }
    val tagLabel = when {
        model.platformLabel == "ANDROID APP" && model.subtitle == "Emulator" -> "EMU"
        model.platformLabel == "ANDROID APP" && model.subtitle == "Game" -> "ANDROID"
        model.platformLabel == "ANDROID APP" -> "APP"
        else -> model.platformLabel
    }
    val badge: @Composable () -> Unit = { PlatformBadge(tagLabel, accentColor = model.platformAccent, compact = true) }
    val status: (@Composable () -> Unit)? = if (variant == LibraryItemCardVariant.Home && model.itemId in LocalLastPlayed.current)
        ({ LastPlayedIndicator(model.platformLabel) }) else null
    val contextualSubtitle = model.subtitle?.takeUnless {
        it.equals(model.platformLabel, ignoreCase = true) || it in setOf("App", "Game", "Emulator", "System")
    }
    val type = LauncherTheme.typography
    val captionHeight = with(LocalDensity.current) {
        type.tileTitle.lineHeight.toDp() * 2f +
            if (contextualSubtitle != null) type.tileSubtitle.lineHeight.toDp() else 0.dp
    } + 6.dp // Four dp caption padding plus native text-layout rounding slack.
    val maxArtworkSize = if (variant == LibraryItemCardVariant.Collection && maxCollectionCardHeight.value.isFinite()) {
        (maxCollectionCardHeight - captionHeight).coerceAtLeast(0.dp)
    } else Dp.Infinity
    // A large-text, short-window thumbnail cannot contain a readable platform pill.
    // Leave the cover visible instead of squeezing a clipped badge over its center.
    val artworkBadge = badge.takeIf { maxArtworkSize >= 64.dp }

    if (model.artwork is TileArtwork.AndroidIcon && variant != LibraryItemCardVariant.SearchResult && variant != LibraryItemCardVariant.List) {
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
            badge = artworkBadge,
            status = status,
            maxArtworkSize = maxArtworkSize,
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
            badge = artworkBadge,
            status = status,
            selected = selected,
            unavailable = unavailable,
            unavailableReason = unavailableReason,
            focusFrameWidth = focusFrameWidth,
            focusLift = focusLift,
            subtitleColor = model.platformAccent.takeIf { model.typeLabel == "Game" },
            maxArtworkSize = maxArtworkSize,
        )

        LibraryItemCardVariant.SearchResult, LibraryItemCardVariant.List -> SearchResultCard(
            compact = variant == LibraryItemCardVariant.List,
            title = model.title,
            subtitle = contextualSubtitle,
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

/** Artwork without a card, focus target or action semantics, also used by read-only previews. */
@Composable
fun LibraryItemArtwork(
    model: TileUiModel,
    iconLoader: AndroidIconLoader? = null,
    active: Boolean = true,
    targetSizePx: Int = ArtworkDecodePolicy.DEFAULT_TARGET_PX,
    animate: Boolean = true,
    fitCover: Boolean = false,
) {
    val visible = active && (LocalEnrichedArtworkLoader.current?.foreground ?: true) &&
        (iconLoader?.foreground ?: true)
    val order = dev.handheld.launcher.ui.artwork.LocalArtworkLoadOrder.current
    if (visible && iconLoader == null && model.artwork is TileArtwork.AndroidIcon)
        androidx.compose.runtime.SideEffect { order?.complete(model.itemId) }
    val iconPainter = when (val artwork = model.artwork) {
        is TileArtwork.AndroidIcon -> iconLoader?.let {
            rememberAndroidIconPainter(it, artwork.componentId, visible, targetSizePx)
        }
        else -> null
    }
    TileArtwork(model, iconPainter, visible, targetSizePx, animate, fitCover)
}

@Composable
private fun TileArtwork(
    model: TileUiModel,
    iconPainter: Painter?,
    active: Boolean,
    targetSizePx: Int,
    animate: Boolean,
    fitCover: Boolean,
) {
    if (!active) {
        ArtworkFallback(label = model.typeLabel)
        return
    }
    // Native icons never crossfade or slide. Avoid allocating animation state,
    // fallback layers and ROM observers for every app entering the viewport.
    if (model.artwork is TileArtwork.AndroidIcon) {
        if (iconPainter == null) ArtworkFallback(label = model.typeLabel)
        else AppIconArtwork(iconPainter)
        return
    }
    val enriched = rememberEnrichedArtwork(model, active, targetSizePx)
    val visual = ArtworkVisual(
        painter = enriched.painter ?: iconPainter,
        cover = enriched.painter != null,
        fallback = if (model.artwork is TileArtwork.LocalReference) "Custom artwork unavailable" else model.typeLabel,
    )
    val duration = LauncherTheme.motion.artworkDurationMillis
    val fade = animate && LocalArtworkLoadingAllowed.current && duration > 0 && enriched.painter != null && !enriched.fromMemory
    val opacity = remember(visual.painter) { Animatable(if (fade) 0f else 1f) }
    LaunchedEffect(opacity, fade) {
        if (fade) opacity.animateTo(1f, tween(duration, easing = FastOutSlowInEasing)) else opacity.snapTo(1f)
    }
    Box(Modifier.fillMaxSize().clipToBounds()) {
        // Keep animation reads in the draw layer: a reveal must not recompose every card
        // on every frame. The short downward slide suggests a cartridge settling in place.
        if (visual.painter != null) Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - opacity.value }) {
            ArtworkFallback(label = model.typeLabel)
        }
        Box(Modifier.fillMaxSize().graphicsLayer {
            val progress = opacity.value
            alpha = progress
            translationY = -size.height * .045f * (1f - progress)
            scaleX = .985f + .015f * progress
            scaleY = scaleX
        }) { ArtworkVisualContent(visual, fitCover) }
    }
}

private data class ArtworkVisual(val painter: Painter?, val cover: Boolean, val fallback: String)

@Composable
private fun ArtworkVisualContent(visual: ArtworkVisual, fitCover: Boolean) {
    val painter = visual.painter
    when {
        painter == null -> ArtworkFallback(label = visual.fallback)
        visual.cover -> if (fitCover) Image(painter, contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) else CoverArtwork(painter)
        else -> AppIconArtwork(painter)
    }
}

private val UnavailabilityReason.presentationLabel: String
    get() = when (this) {
        UnavailabilityReason.REMOVED -> "Removed"
        UnavailabilityReason.SOURCE_UNAVAILABLE -> "Source unavailable"
        UnavailabilityReason.UNSUPPORTED -> "Unsupported"
        UnavailabilityReason.UNKNOWN -> "Unavailable"
    }
