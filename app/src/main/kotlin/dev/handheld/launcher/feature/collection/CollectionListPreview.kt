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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.contract.LauncherActionDescriptor
import dev.handheld.launcher.contract.LauncherActionMeaning
import dev.handheld.launcher.contract.SemanticInputAction
import dev.handheld.launcher.core.designsystem.controls.LauncherButton
import dev.handheld.launcher.core.designsystem.foundation.LauncherText
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.runtime.LocalRunningLabels
import dev.handheld.launcher.ui.artwork.local.AndroidIconLoader
import dev.handheld.launcher.ui.components.LibraryItemCard
import dev.handheld.launcher.ui.components.LibraryItemCardVariant
import dev.handheld.launcher.ui.presentation.TileUiModel

private enum class PreviewAction { Open, Details }

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
    var focusedAction by remember { mutableStateOf<PreviewAction?>(null) }
    fun publish(action: PreviewAction) {
        val enabled = action == PreviewAction.Details || currentModel.canOpen
        currentCallbacks.onFocusedAction(FocusedControlAction(
            LauncherActionDescriptor(SemanticInputAction.CONFIRM, LauncherActionMeaning.ACTIVATE,
                if (action == PreviewAction.Open) currentModel.primaryActionLabel else "Details", enabled),
            if (!enabled) null else if (action == PreviewAction.Open) open else details, currentItemId,
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
    LaunchedEffect(item.id, model.canOpen, model.primaryActionLabel, focusedAction) {
        focusedAction?.let(::publish)
    }
    if (compact) {
        Row(modifier.heightIn(min = 48.dp).testTag("collection-preview-compact"),
            horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            LauncherText(model.title, Modifier.weight(1f), style = LauncherTheme.typography.tileTitle,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            LauncherButton(model.primaryActionLabel, open, Modifier.focusRequester(openFocus).testTag("collection-preview-open"),
                enabled = model.canOpen, onFocusChanged = { onOpenFocusChanged(it); focused(PreviewAction.Open, it) })
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm)) {
                LauncherButton(model.primaryActionLabel, open,
                    Modifier.weight(1f).focusRequester(openFocus).testTag("collection-preview-open"), enabled = model.canOpen,
                    onFocusChanged = { onOpenFocusChanged(it); focused(PreviewAction.Open, it) })
                LauncherButton("Details", details, Modifier.weight(1f).testTag("collection-preview-details"),
                    onFocusChanged = { focused(PreviewAction.Details, it) })
            }
        }
    }
}
