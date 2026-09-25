package dev.handheld.launcher.feature.collection

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.designsystem.foundation.LauncherText
import dev.handheld.launcher.core.designsystem.glyphs.LauncherGlyph
import dev.handheld.launcher.core.designsystem.glyphs.LauncherGlyphIcon
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.UnavailabilityReason
import dev.handheld.launcher.ui.artwork.local.AndroidIconLoader
import dev.handheld.launcher.ui.components.LibraryItemArtwork
import dev.handheld.launcher.ui.presentation.TileUiModel
import dev.handheld.launcher.ui.presentation.platformAccent
import java.util.Locale

/** Read-only information on the page background; only the left list owns selection. */
@Composable
internal fun CollectionListPreview(
    item: LibraryItem,
    model: TileUiModel,
    favorite: Boolean,
    iconLoader: AndroidIconLoader?,
    modifier: Modifier,
    compact: Boolean = false,
    emulatorLabel: String? = null,
) {
    val colors = LauncherTheme.colors
    val spacing = LauncherTheme.spacing
    val emulator = if (item is LibraryItem.RomGame)
        emulatorLabel ?: if (item.platformId == null) "Choose a console" else "Not available"
        else null
    if (compact) {
        Row(modifier.heightIn(min = 48.dp).testTag("collection-preview-compact"),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                LauncherText(model.title, style = LauncherTheme.typography.tileTitle,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (emulator != null) LauncherText("Using emulator: $emulator",
                    style = LauncherTheme.typography.tileSubtitle, color = colors.textSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            PreviewTag(model.platformLabel, model)
            FavoriteStatus(favorite)
        }
        return
    }
    Column(modifier.testTag("collection-preview").padding(horizontal = spacing.xs, vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            PreviewTag(model.platformLabel, model)
            if (item is LibraryItem.RomGame) item.format?.takeIf(String::isNotBlank)?.let {
                PreviewTag(it.uppercase(Locale.ROOT), model)
            }
            Spacer(Modifier.weight(1f))
            FavoriteStatus(favorite)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val contentHeight = maxHeight
            val artworkSize = minOf(maxWidth * .46f, maxHeight * .9f, 280.dp * LauncherTheme.referenceScale)
            val targetPx = with(LocalDensity.current) { artworkSize.roundToPx().coerceAtLeast(1) }
            val metadataScroll = remember(item.id) { ScrollState(0) }
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(artworkSize).clip(RoundedCornerShape(LauncherTheme.shapes.smallControl))
                    .testTag("collection-preview-artwork")) {
                    LibraryItemArtwork(model, iconLoader, targetSizePx = targetPx, fitCover = true)
                }
                Column(Modifier.weight(1f).heightIn(max = contentHeight)
                    .focusProperties { canFocus = false }.verticalScroll(metadataScroll),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    LauncherText(model.title, style = LauncherTheme.typography.homeTitle,
                        maxLines = 5, overflow = TextOverflow.Ellipsis)
                    if (emulator != null) Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                        LauncherText("Using emulator", style = LauncherTheme.typography.tileSubtitle,
                            color = colors.textSecondary)
                        LauncherText(emulator, Modifier.testTag("collection-preview-emulator"),
                            style = LauncherTheme.typography.tileTitle, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    } else LauncherText(model.subtitle ?: model.typeLabel,
                        style = LauncherTheme.typography.tileSubtitle, color = colors.textSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(5.dp).background(
                            if (item.availability == Availability.Available) colors.confirm else colors.focus,
                            androidx.compose.foundation.shape.CircleShape))
                        LauncherText(availabilityLabel(item.availability), style = LauncherTheme.typography.tileSubtitle,
                            color = colors.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewTag(label: String, model: TileUiModel) {
    LauncherText(label, Modifier.background(model.platformAccent.copy(alpha = .14f),
        RoundedCornerShape(LauncherTheme.shapes.smallControl))
        .padding(horizontal = LauncherTheme.spacing.xs, vertical = LauncherTheme.spacing.xxs),
        style = LauncherTheme.typography.tileTitle, color = model.platformAccent,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun FavoriteStatus(favorite: Boolean) {
    LauncherGlyphIcon(if (favorite) LauncherGlyph.FavoriteFilled else LauncherGlyph.FavoriteOutline,
        Modifier.size(30.dp * LauncherTheme.referenceScale).testTag("collection-preview-favorite"),
        contentDescription = if (favorite) "Favorite" else "Not favorite",
        tint = if (favorite) LauncherTheme.colors.focus else LauncherTheme.colors.textSecondary)
}

private fun availabilityLabel(availability: Availability): String = when (availability) {
    Availability.Available -> "Available"
    is Availability.Unavailable -> when (availability.reason) {
        UnavailabilityReason.REMOVED -> "No longer installed"
        UnavailabilityReason.SOURCE_UNAVAILABLE -> "Game folder unavailable"
        UnavailabilityReason.UNSUPPORTED -> "Unsupported"
        UnavailabilityReason.UNKNOWN -> "Unavailable"
    }
}
