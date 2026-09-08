package dev.handheld.launcher.feature.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.contract.LauncherActionDescriptor
import dev.handheld.launcher.contract.LauncherActionMeaning
import dev.handheld.launcher.contract.SemanticInputAction
import dev.handheld.launcher.core.designsystem.controls.LauncherIconButton
import dev.handheld.launcher.core.designsystem.foundation.LauncherText
import dev.handheld.launcher.core.designsystem.glyphs.LauncherGlyph
import dev.handheld.launcher.core.designsystem.glyphs.LauncherGlyphIcon
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.runtime.LocalRunningLabels
import dev.handheld.launcher.ui.artwork.local.AndroidIconLoader
import dev.handheld.launcher.ui.components.LibraryItemCard
import dev.handheld.launcher.ui.components.LibraryItemCardVariant
import dev.handheld.launcher.ui.presentation.TileUiModel

private enum class PreviewAction { Open, Details, Favorite }

/** Existing catalog metadata only. The selected preview never discovers or guesses game state. */
@Composable
internal fun CollectionListPreview(
    item: LibraryItem,
    model: TileUiModel,
    favorite: Boolean,
    callbacks: CollectionScreenCallbacks,
    iconLoader: AndroidIconLoader?,
    modifier: Modifier,
    openFocus: FocusRequester,
    compact: Boolean = false,
    onOpenFocusChanged: (Boolean) -> Unit = {},
) {
    val currentItemId by rememberUpdatedState(item.id)
    val currentModel by rememberUpdatedState(model)
    val currentFavorite by rememberUpdatedState(favorite)
    val currentCallbacks by rememberUpdatedState(callbacks)
    val open: () -> Unit = remember {
        {
            if (currentModel.canOpen) {
                val id = currentItemId
                currentCallbacks.onSelect(id)
                currentCallbacks.onOpen(id)
            }
        }
    }
    val details: () -> Unit = remember { { currentCallbacks.onOpenDetails(currentItemId) } }
    val toggleFavorite: () -> Unit = remember { { currentCallbacks.onFavorite(currentItemId, !currentFavorite) } }
    var focusedAction by remember { mutableStateOf<PreviewAction?>(null) }
    fun publish(action: PreviewAction) {
        val enabled = action != PreviewAction.Open || currentModel.canOpen
        currentCallbacks.onFocusedAction(FocusedControlAction(
            LauncherActionDescriptor(SemanticInputAction.CONFIRM, when (action) {
                PreviewAction.Open -> LauncherActionMeaning.ACTIVATE
                PreviewAction.Details -> LauncherActionMeaning.OPEN_DETAILS
                PreviewAction.Favorite -> LauncherActionMeaning.TOGGLE_FAVORITE
            }, when (action) {
                PreviewAction.Open -> currentModel.primaryActionLabel
                PreviewAction.Details -> "Details"
                PreviewAction.Favorite -> favoriteActionLabel(currentFavorite)
            }, enabled),
            if (!enabled) null else when (action) {
                PreviewAction.Open -> open
                PreviewAction.Details -> details
                PreviewAction.Favorite -> toggleFavorite
            }, currentItemId,
        ))
    }
    fun focused(action: PreviewAction, hasFocus: Boolean) {
        if (hasFocus) {
            focusedAction = action
            publish(action)
        } else if (focusedAction == action) {
            focusedAction = null
            currentCallbacks.onFocusedAction(null)
        }
    }
    // Catalog replacement can keep the same native button focused. Refresh its
    // descriptor and Details/menu item ID even when no new focus event occurs.
    LaunchedEffect(item.id, model.canOpen, model.primaryActionLabel, favorite, focusedAction) {
        focusedAction?.let(::publish)
    }
    if (compact) {
        Row(modifier.heightIn(min = 48.dp).testTag("collection-preview-compact"),
            horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            LauncherText(model.title, Modifier.weight(1f), style = LauncherTheme.typography.tileTitle,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            PreviewActionButtons(model, favorite, open, details, toggleFavorite, openFocus,
                onOpenFocusChanged, ::focused, includeDetails = false)
        }
        return
    }
    BoxWithConstraints(modifier.testTag("collection-preview")) {
        val artworkSize = minOf(144.dp * LauncherTheme.referenceScale, maxWidth * .34f)
        val targetPx = with(LocalDensity.current) { artworkSize.roundToPx().coerceAtLeast(1) }
        val metadataScroll = remember(item.id) { ScrollState(0) }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm)) {
            Row(Modifier.weight(1f).fillMaxWidth().verticalScroll(metadataScroll),
                horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm)) {
                CompositionLocalProvider(LocalRunningLabels provides emptyMap()) {
                    Box(Modifier.size(artworkSize).clearAndSetSemantics { }) {
                        LibraryItemCard(model, LibraryItemCardVariant.Home, selected = false, activationEnabled = false,
                            modifier = Modifier.fillMaxSize(), iconLoader = iconLoader, onActivate = {},
                            artworkActive = true, artworkTargetSizePx = targetPx)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xs)) {
                    LauncherText(model.title, style = LauncherTheme.typography.pageTitle, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    LauncherText(model.subtitle ?: model.typeLabel, style = LauncherTheme.typography.tileSubtitle,
                        color = LauncherTheme.colors.textSecondary)
                    LauncherText(if (favorite) "Favorite" else "Not favorited", style = LauncherTheme.typography.tileSubtitle,
                        color = LauncherTheme.colors.textSecondary)
                    LauncherText(if (item.availability is Availability.Available) "Available" else "Unavailable",
                        style = LauncherTheme.typography.tileSubtitle, color = LauncherTheme.colors.textSecondary)
                }
            }
            PreviewActionButtons(model, favorite, open, details, toggleFavorite, openFocus,
                onOpenFocusChanged, ::focused)
        }
    }
}

private fun favoriteActionLabel(favorite: Boolean) = if (favorite) "Remove from favorites" else "Add to favorites"

/** Compact controls retain full native targets; their glyphs are decorative. */
@Composable
private fun PreviewActionButtons(
    model: TileUiModel,
    favorite: Boolean,
    open: () -> Unit,
    details: () -> Unit,
    toggleFavorite: () -> Unit,
    openFocus: FocusRequester,
    onOpenFocusChanged: (Boolean) -> Unit,
    onFocused: (PreviewAction, Boolean) -> Unit,
    includeDetails: Boolean = true,
) {
    Row(Modifier.testTag("collection-preview-actions"), horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xxs)) {
        LauncherIconButton(model.primaryActionLabel, open,
            Modifier.size(48.dp).focusRequester(openFocus).testTag("collection-preview-open"),
            enabled = model.canOpen, shape = CircleShape,
            onFocusChanged = { onOpenFocusChanged(it); onFocused(PreviewAction.Open, it) }) {
            LauncherGlyphIcon(LauncherGlyph.Play, Modifier.size(24.dp), contentDescription = null)
        }
        if (includeDetails) LauncherIconButton("Details", details,
            Modifier.size(48.dp).testTag("collection-preview-details"), shape = CircleShape,
            onFocusChanged = { onFocused(PreviewAction.Details, it) }) {
            LauncherGlyphIcon(LauncherGlyph.Info, Modifier.size(24.dp), contentDescription = null)
        }
        LauncherIconButton(favoriteActionLabel(favorite), toggleFavorite,
            Modifier.size(48.dp).testTag("collection-preview-favorite")
                .semantics { stateDescription = if (favorite) "Favorite" else "Not favorited" },
            shape = CircleShape, selected = favorite, checked = favorite,
            onFocusChanged = { onFocused(PreviewAction.Favorite, it) }) {
            LauncherGlyphIcon(LauncherGlyph.Favorites, Modifier.size(24.dp), contentDescription = null)
        }
    }
}
