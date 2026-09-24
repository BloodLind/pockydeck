package dev.handheld.launcher.feature.collection

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.designsystem.controls.PlatformBadge
import dev.handheld.launcher.core.designsystem.foundation.LauncherText
import dev.handheld.launcher.core.designsystem.glyphs.LauncherGlyph
import dev.handheld.launcher.core.designsystem.glyphs.LauncherGlyphIcon
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.UnavailabilityReason
import dev.handheld.launcher.core.domain.rom.scan.RomPlatforms
import dev.handheld.launcher.ui.artwork.local.AndroidIconLoader
import dev.handheld.launcher.ui.components.LibraryItemArtwork
import dev.handheld.launcher.ui.presentation.TileUiModel
import dev.handheld.launcher.ui.presentation.platformAccent
import java.util.Locale

/** Read-only catalog information. Controller focus and actions belong to the selected list row. */
@Composable
internal fun CollectionListPreview(
    item: LibraryItem,
    model: TileUiModel,
    favorite: Boolean,
    iconLoader: AndroidIconLoader?,
    modifier: Modifier,
    compact: Boolean = false,
) {
    if (compact) {
        Row(modifier.heightIn(min = 48.dp).testTag("collection-preview-compact"),
            horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically) {
            LauncherText(model.title, Modifier.weight(1f), style = LauncherTheme.typography.tileTitle,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            PlatformBadge(model.platformLabel, accentColor = model.platformAccent, compact = true)
            if (favorite) LauncherGlyphIcon(LauncherGlyph.Favorites, Modifier.size(20.dp), contentDescription = "Favorite")
        }
        return
    }
    val colors = LauncherTheme.colors
    val spacing = LauncherTheme.spacing
    val accent = model.platformAccent
    val shape = RoundedCornerShape(LauncherTheme.shapes.smallControl * 2)
    val panelBrush = remember(accent, colors) { Brush.linearGradient(listOf(
        accent.copy(alpha = .13f), colors.surfaceDock.copy(alpha = .65f), colors.surfaceDock.copy(alpha = .35f))) }
    Column(modifier.testTag("collection-preview").clip(shape).background(panelBrush)
        .border(1.dp, colors.borderEmphasis, shape).padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            PlatformBadge(model.platformLabel, accentColor = accent, compact = true)
            if (favorite) Row(horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
                verticalAlignment = Alignment.CenterVertically) {
                LauncherGlyphIcon(LauncherGlyph.Favorites, Modifier.size(16.dp * LauncherTheme.referenceScale),
                    contentDescription = null, tint = accent)
                LauncherText("Favorite", style = LauncherTheme.typography.tileSubtitle)
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val contentHeight = maxHeight
            val artworkSize = minOf(maxWidth * .46f, maxHeight * .9f, 280.dp * LauncherTheme.referenceScale)
            val targetPx = with(LocalDensity.current) { artworkSize.roundToPx().coerceAtLeast(1) }
            val metadataScroll = remember(item.id) { ScrollState(0) }
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalAlignment = Alignment.CenterVertically) {
                val coverShape = RoundedCornerShape(LauncherTheme.shapes.smallControl)
                Box(Modifier.size(artworkSize).drawWithCache {
                    val glow = Brush.radialGradient(listOf(accent.copy(alpha = .18f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height / 2f), radius = size.maxDimension * .75f)
                    onDrawBehind { drawCircle(glow, radius = size.maxDimension * .75f) }
                }.shadow(10.dp, coverShape).clip(coverShape).background(colors.surfaceArtwork)
                    .border(1.dp, colors.textPrimary.copy(alpha = .18f), coverShape)
                    .testTag("collection-preview-artwork")) {
                    LibraryItemArtwork(model, iconLoader, targetSizePx = targetPx, fitCover = true)
                    // A thin case spine gives the cover depth without another image decode.
                    Box(Modifier.width(3.dp).fillMaxHeight().background(colors.textPrimary.copy(alpha = .12f)))
                }
                Column(Modifier.weight(1f).heightIn(max = contentHeight)
                    .focusProperties { canFocus = false }.verticalScroll(metadataScroll),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    LauncherText(model.title, style = LauncherTheme.typography.homeTitle,
                        maxLines = 5, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.width(28.dp * LauncherTheme.referenceScale).height(2.dp)
                        .background(accent, RoundedCornerShape(1.dp)))
                    LauncherText(model.typeLabel,
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
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.borderEmphasis))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            PreviewFact("Platform", if (item is LibraryItem.RomGame)
                item.platformId?.let { RomPlatforms.byId(it)?.displayName } ?: "Unassigned console"
                else if (item is LibraryItem.AndroidApp) "Android" else "System", Modifier.weight(1f))
            PreviewFact(if (item is LibraryItem.RomGame) "Format" else "Category",
                if (item is LibraryItem.RomGame) item.format?.takeIf(String::isNotBlank)?.uppercase(Locale.ROOT) ?: "Unknown" else model.subtitle ?: model.typeLabel,
                Modifier.weight(.65f))
        }
    }
}

@Composable
private fun PreviewFact(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xxs)) {
        LauncherText(label, style = LauncherTheme.typography.tileSubtitle, color = LauncherTheme.colors.textSecondary)
        LauncherText(value, style = LauncherTheme.typography.tileTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
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
