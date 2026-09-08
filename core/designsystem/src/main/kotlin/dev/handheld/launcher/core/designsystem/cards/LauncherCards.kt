package dev.handheld.launcher.core.designsystem.cards

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import dev.handheld.launcher.core.designsystem.contract.rememberControlFocusRestoration
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.designsystem.foundation.FocusFrame
import dev.handheld.launcher.core.designsystem.foundation.LauncherText
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme

enum class CardVariant { HomeCover, CollectionCover, AppIcon }

/** The caller supplies a local painter; controls never resolve or fetch images. */
@Composable
fun CoverArtwork(painter: Painter, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Image(painter, contentDescription, modifier.fillMaxSize(), contentScale = ContentScale.Crop)
}

@Composable
fun AppIconArtwork(painter: Painter, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Image(painter, contentDescription, modifier.fillMaxSize(), contentScale = ContentScale.Fit)
}

@Composable
fun ArtworkFallback(
    modifier: Modifier = Modifier,
    label: String = "Artwork unavailable",
    shape: Shape = RoundedCornerShape(LauncherTheme.shapes.homeInner),
) {
    Box(
        modifier.fillMaxSize().clip(shape).background(LauncherTheme.colors.surfaceArtwork)
            .padding(LauncherTheme.spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        LauncherText(label, style = LauncherTheme.typography.tileSubtitle,
            color = LauncherTheme.colors.textSecondary, maxLines = 2,
            overflow = TextOverflow.Ellipsis)
    }
}

/** Reserves two title lines and one subtitle line, including when the subtitle is absent. */
@Composable
fun TileCaption(title: String, subtitle: String? = null, modifier: Modifier = Modifier, subtitleColor: Color? = null) {
    val density = LocalDensity.current
    val type = LauncherTheme.typography
    val titleHeight = with(density) { type.tileTitle.lineHeight.toDp() * 2f }
    val subtitleHeight = with(density) { type.tileSubtitle.lineHeight.toDp() }
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(titleHeight)) {
            LauncherText(title, style = type.tileTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.fillMaxWidth().height(subtitleHeight)) {
            if (subtitle != null) LauncherText(subtitle, style = type.tileSubtitle,
                color = subtitleColor ?: LauncherTheme.colors.textSecondary, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

/** One real focus target and one activation path for every card variant. */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun CardActivation(
    title: String,
    activationEnabled: Boolean,
    unavailable: Boolean,
    unavailableReason: String,
    selected: Boolean,
    onActivate: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier,
    shape: Shape,
    focusFrameWidth: Dp,
    focusLift: Dp,
    caption: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val restoration = rememberControlFocusRestoration()
    val source = remember { MutableInteractionSource() }
    val inputMode = LocalInputModeManager.current
    val pressed by source.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).then(modifier)
            .focusRequester(restoration.requester).onFocusChanged {
            focused = it.isFocused
            onFocusChanged(it.isFocused)
            if (it.isFocused) restoration.record()
        }.clickable(source, indication = null, enabled = activationEnabled,
            role = Role.Button, onClick = {
                // A completed touch click establishes the same active item as controller focus.
                // click cancellation during scrolling never reaches this callback.
                inputMode.requestInputMode(InputMode.Keyboard)
                restoration.requester.requestFocus()
                restoration.record()
                onActivate()
            }).semantics(mergeDescendants = true) {
                this.selected = selected
                contentDescription = title
                if (!activationEnabled) disabled()
                if (unavailable) stateDescription = unavailableReason
            },
    ) {
        FocusFrame(
            modifier = if (caption != null) Modifier.fillMaxWidth().aspectRatio(1f) else Modifier,
            focused = focused, pressed = pressed,
            enabled = activationEnabled, unavailable = unavailable,
            unavailableReason = unavailableReason,
            shape = shape, focusFrameWidth = focusFrameWidth, focusLift = focusLift,
        ) { content() }
        caption?.invoke()
    }
}

/** Home receives a square allocation from ShellMetrics; collections receive a width and wrap height. */
@Composable
fun CoverTile(
    title: String,
    variant: CardVariant,
    activationEnabled: Boolean,
    onActivate: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    artwork: @Composable () -> Unit,
    badge: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    selected: Boolean = false,
    unavailable: Boolean = false,
    unavailableReason: String = "Unavailable",
    focusFrameWidth: Dp = LauncherTheme.depth.focusedElevation / 2f,
    focusLift: Dp = LauncherTheme.depth.focusLift,
    subtitleColor: Color? = null,
) {
    require(variant != CardVariant.AppIcon) { "Use AppIconTile for the app variant" }
    val isCollection = variant == CardVariant.CollectionCover
    CardActivation(title, activationEnabled, unavailable, unavailableReason, selected,
        onActivate, onFocusChanged, modifier,
        RoundedCornerShape(if (isCollection) LauncherTheme.shapes.collectionOuter else LauncherTheme.shapes.homeOuter),
        focusFrameWidth, focusLift,
        caption = if (isCollection) ({ TileCaption(title, subtitle, Modifier.padding(horizontal = 2.dp, vertical = 2.dp), subtitleColor) }) else null,
    ) {
        CardArtwork(Modifier.fillMaxSize(), artwork, badge,
            shape = RoundedCornerShape(if (isCollection) (LauncherTheme.shapes.collectionOuter - 2.dp).coerceAtLeast(2.dp) else LauncherTheme.shapes.homeInner))
    }
}

@Composable
private fun CardArtwork(
    modifier: Modifier,
    artwork: @Composable () -> Unit,
    badge: (@Composable () -> Unit)?,
    shape: Shape = RoundedCornerShape(LauncherTheme.shapes.homeInner),
) {
    Box(modifier.clip(shape)) {
        artwork()
        if (badge != null) Box(Modifier.align(Alignment.BottomStart).padding(LauncherTheme.spacing.xs)) {
            badge()
        }
    }
}

/** Native app fallback: a modest fitted icon and bounded centered title inside the square. */
@Composable
fun AppIconTile(
    title: String,
    activationEnabled: Boolean,
    onActivate: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    subtitle: String? = null,
    selected: Boolean = false,
    unavailable: Boolean = false,
    unavailableReason: String = "Unavailable",
    focusFrameWidth: Dp = LauncherTheme.depth.focusedElevation / 2f,
    focusLift: Dp = LauncherTheme.depth.focusLift,
    showCaption: Boolean = false,
) {
    val scale = LauncherTheme.referenceScale
    val type = LauncherTheme.typography
    val density = LocalDensity.current
    val titleHeight = with(density) { type.tileTitleLarge.lineHeight.toDp() * 2f }
    val subtitleHeight = with(density) { type.badgeLabel.lineHeight.toDp() }
    CardActivation(title, activationEnabled, unavailable, unavailableReason, selected,
        onActivate, onFocusChanged, modifier,
        RoundedCornerShape(if (showCaption) LauncherTheme.shapes.collectionOuter else LauncherTheme.shapes.homeOuter),
        focusFrameWidth, focusLift,
        caption = if (showCaption) ({ TileCaption(title, subtitle, Modifier.padding(horizontal = 2.dp, vertical = 2.dp)) }) else null,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(LauncherTheme.spacing.sm), contentAlignment = Alignment.Center) {
            if (showCaption) {
                Box(Modifier.size(minOf(56.dp, maxWidth * .4f, maxHeight * .4f))
                    .clip(RoundedCornerShape(LauncherTheme.shapes.smallControl))
                    .background(LauncherTheme.colors.surfaceArtwork), contentAlignment = Alignment.Center) { icon() }
                return@BoxWithConstraints
            }
            val textHeight = titleHeight + LauncherTheme.spacing.sm +
                if (subtitle != null) subtitleHeight + LauncherTheme.spacing.xxs else 0.dp
            val iconSize = minOf(80.dp * scale, maxWidth, (maxHeight - textHeight).coerceAtLeast(0.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(iconSize)
                    .clip(RoundedCornerShape(LauncherTheme.shapes.homeInner))
                    .background(LauncherTheme.colors.surfaceArtwork), contentAlignment = Alignment.Center) {
                    icon()
                }
                Spacer(Modifier.height(LauncherTheme.spacing.sm))
                Box(Modifier.fillMaxWidth().height(titleHeight), contentAlignment = Alignment.Center) {
                    LauncherText(title, modifier = Modifier.fillMaxWidth(),
                        style = type.tileTitleLarge.copy(textAlign = TextAlign.Center),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (subtitle != null) {
                    Spacer(Modifier.height(LauncherTheme.spacing.xxs))
                    LauncherText(subtitle, style = LauncherTheme.typography.badgeLabel,
                        color = LauncherTheme.colors.textSecondary, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun SearchResultCard(
    title: String,
    subtitle: String? = null,
    activationEnabled: Boolean,
    onActivate: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    artwork: @Composable () -> Unit,
    badge: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    unavailable: Boolean = false,
    unavailableReason: String = "Unavailable",
    focusFrameWidth: Dp = LauncherTheme.depth.focusedElevation / 2f,
    focusLift: Dp = LauncherTheme.depth.focusLift,
    subtitleColor: Color? = null,
) {
    val imageSize = 72.dp * LauncherTheme.referenceScale
    CardActivation(title, activationEnabled, unavailable, unavailableReason, selected,
        onActivate, onFocusChanged, modifier, RoundedCornerShape(LauncherTheme.shapes.smallControl),
        focusFrameWidth, focusLift) {
        Row(Modifier.fillMaxWidth().padding(LauncherTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            CardArtwork(Modifier.size(imageSize), artwork, badge)
            TileCaption(title, subtitle, Modifier.weight(1f).padding(start = LauncherTheme.spacing.sm), subtitleColor)
        }
    }
}
