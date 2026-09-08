package dev.handheld.launcher.feature.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.contract.LauncherActionDescriptor
import dev.handheld.launcher.contract.LauncherActionMeaning
import dev.handheld.launcher.contract.SemanticInputAction
import dev.handheld.launcher.core.designsystem.foundation.LauncherText
import dev.handheld.launcher.core.designsystem.layout.PageHeading
import dev.handheld.launcher.core.designsystem.settings.ActionRow
import dev.handheld.launcher.core.designsystem.settings.ChoiceRow
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.LibraryCategory
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.SupportedItemAction
import dev.handheld.launcher.core.domain.model.UserItemOverrides
import dev.handheld.launcher.feature.collection.FocusedControlAction
import dev.handheld.launcher.feature.collection.OnFocusedAction
import dev.handheld.launcher.ui.artwork.local.AndroidIconLoader
import dev.handheld.launcher.ui.components.LibraryItemCard
import dev.handheld.launcher.ui.components.LibraryItemCardVariant
import dev.handheld.launcher.ui.presentation.toTileUiModel
import dev.handheld.launcher.ui.presentation.consoleLabel

data class ItemDetailsCallbacks(
    val onOpen: (LibraryItem) -> Unit,
    val onFavorite: (LibraryItem, Boolean) -> Unit,
    val onAppInfo: (LibraryItem.AndroidApp) -> Unit,
    val onCategoryChange: (LibraryItem, LibraryCategory?) -> Unit,
    val onFocusedAction: OnFocusedAction = {},
    val onChooseRomConsole: (ItemId) -> Unit = {},
    val onChooseRomEmulator: (ItemId) -> Unit = {},
    val onOpenRomFolders: () -> Unit = {},
)

/** Actual catalog identity and supported actions; unavailable entries never pretend to open. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ItemDetailsScreen(
    item: LibraryItem,
    overrides: UserItemOverrides?,
    favorite: Boolean,
    modifier: Modifier,
    callbacks: ItemDetailsCallbacks,
    iconLoader: AndroidIconLoader? = null,
) {
    val model = item.toTileUiModel(overrides)
    val effectiveCategory = overrides?.category ?: item.category
    val canOpen = model.canOpen
    val nextCategory = nextCategory(effectiveCategory, overrides?.category != null)
    val initial = remember(item.id) { FocusRequester() }
    val inputMode = LocalInputModeManager.current
    val controllerInput = dev.handheld.launcher.core.designsystem.contract.LocalControllerInput.current
    LaunchedEffect(item.id, controllerInput) {
        if (controllerInput) {
            inputMode.requestInputMode(InputMode.Keyboard)
            initial.requestFocus()
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
      val wide = maxWidth >= 600.dp
      Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.lg)) {
        if (wide) Column(Modifier.width(200.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm)) {
            LibraryItemCard(model, LibraryItemCardVariant.Home, false, activationEnabled = false,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f), iconLoader = iconLoader, onActivate = {})
            LauncherText("Type: ${item.kind.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }}", color = LauncherTheme.colors.textSecondary)
            LauncherText("Availability: ${availabilityLabel(item.availability)}", color = LauncherTheme.colors.textSecondary)
        }
        Column(
        Modifier.weight(1f).focusRequester(initial).focusGroup().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm),
    ) {
        LauncherText(model.title, style = LauncherTheme.typography.pageTitle)
        LauncherText(
            if (item is LibraryItem.RomGame) listOfNotNull(item.consoleLabel, item.format?.uppercase()).joinToString(" · ")
            else categoryLabel(effectiveCategory),
            color = LauncherTheme.colors.textSecondary,
        )
        if (!wide) LibraryItemCard(
            model = model,
            variant = LibraryItemCardVariant.SearchResult,
            selected = true,
            activationEnabled = canOpen,
            iconLoader = iconLoader,
            onActivate = { callbacks.onOpen(item) },
            onFocusChanged = { focused -> if (focused) callbacks.onFocusedAction(FocusedControlAction(
                LauncherActionDescriptor(SemanticInputAction.CONFIRM, LauncherActionMeaning.ACTIVATE, model.primaryActionLabel, canOpen),
                if (canOpen) ({ callbacks.onOpen(item) }) else null,
                item.id,
            )) else callbacks.onFocusedAction(null) },
        )
        if (SupportedItemAction.OPEN in item.supportedActions) {
            DetailAction(model.primaryActionLabel, canOpen, LauncherActionMeaning.ACTIVATE,
                onActivate = { callbacks.onOpen(item) }, callbacks.onFocusedAction, item)
        }
        if (SupportedItemAction.TOGGLE_FAVORITE in item.supportedActions) {
            DetailAction(if (favorite) "Remove favorite" else "Add favorite", true, LauncherActionMeaning.TOGGLE_FAVORITE,
                onActivate = { callbacks.onFavorite(item, !favorite) }, callbacks.onFocusedAction, item)
        }
        if (item is LibraryItem.AndroidApp && SupportedItemAction.OPEN_APP_INFO in item.supportedActions) {
            DetailAction("App info", true, LauncherActionMeaning.OPEN_DETAILS,
                onActivate = { callbacks.onAppInfo(item) }, callbacks.onFocusedAction, item)
        }
        if (item is LibraryItem.RomGame) {
            ChoiceRow(
                "Console", item.consoleLabel,
                onSelect = { callbacks.onChooseRomConsole(item.id) },
                supportingText = "Correct detection for this game",
                onFocusChanged = { focused -> callbacks.onFocusedAction(if (focused) FocusedControlAction(
                    LauncherActionDescriptor(SemanticInputAction.CONFIRM, LauncherActionMeaning.CHANGE_FILTER, "Choose console"),
                    { callbacks.onChooseRomConsole(item.id) }, item.id,
                ) else null) },
            )
            DetailAction("Choose emulator for this game", true, LauncherActionMeaning.OPEN_SETTINGS,
                { callbacks.onChooseRomEmulator(item.id) }, callbacks.onFocusedAction, item)
            DetailAction("ROM folders", true, LauncherActionMeaning.OPEN_SETTINGS,
                callbacks.onOpenRomFolders, callbacks.onFocusedAction, item)
        }
        if (item is LibraryItem.AndroidApp) ChoiceRow(
            label = "Category",
            value = categoryLabel(effectiveCategory),
            supportingText = "Change how this item is grouped",
            onSelect = { callbacks.onCategoryChange(item, nextCategory) },
            onFocusChanged = { focused -> if (focused) callbacks.onFocusedAction(FocusedControlAction(
                LauncherActionDescriptor(SemanticInputAction.CONFIRM, LauncherActionMeaning.CHANGE_FILTER, "Change category"),
                { callbacks.onCategoryChange(item, nextCategory) }, item.id,
            )) else callbacks.onFocusedAction(null) },
        )
        }
      }
    }
}

@Composable
private fun DetailAction(
    label: String,
    enabled: Boolean,
    meaning: LauncherActionMeaning,
    onActivate: () -> Unit,
    onFocusedAction: OnFocusedAction,
    item: LibraryItem,
) = ActionRow(
    label = label,
    enabled = enabled,
    onActivate = onActivate,
    onFocusChanged = { focused -> if (focused) onFocusedAction(FocusedControlAction(
        LauncherActionDescriptor(SemanticInputAction.CONFIRM, meaning, label, enabled),
        if (enabled) onActivate else null,
        item.id,
    )) else onFocusedAction(null) },
)

private fun nextCategory(category: LibraryCategory, hasOverride: Boolean): LibraryCategory? = when {
    !hasOverride -> LibraryCategory.GAME
    category == LibraryCategory.GAME -> LibraryCategory.EMULATOR
    category == LibraryCategory.EMULATOR -> LibraryCategory.OTHER
    else -> null
}

private fun categoryLabel(category: LibraryCategory): String = category.name.lowercase().replaceFirstChar { it.uppercase() }

private fun availabilityLabel(availability: Availability): String = when (availability) {
    Availability.Available -> "Available"
    is Availability.Unavailable -> availability.reason.name.lowercase().replaceFirstChar { it.uppercase() }
}
