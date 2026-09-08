package dev.handheld.launcher.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.contract.LauncherActionDescriptor
import dev.handheld.launcher.contract.LauncherActionMeaning
import dev.handheld.launcher.contract.SemanticInputAction
import dev.handheld.launcher.core.designsystem.layout.PageHeading
import dev.handheld.launcher.core.designsystem.settings.ActionRow
import dev.handheld.launcher.core.designsystem.settings.ChoiceRow
import dev.handheld.launcher.core.designsystem.foundation.LauncherText
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.core.domain.model.ConfirmBackMapping
import dev.handheld.launcher.core.domain.model.LibraryCategory
import dev.handheld.launcher.feature.collection.FocusedControlAction
import dev.handheld.launcher.feature.collection.OnFocusedAction
import dev.handheld.launcher.feature.settings.emulators.EmulatorSettingsCallbacks
import dev.handheld.launcher.feature.settings.emulators.EmulatorSettingsScreen
import dev.handheld.launcher.feature.settings.emulators.EmulatorSettingsScreenState
import dev.handheld.launcher.feature.settings.sources.RomSourcesCallbacks
import dev.handheld.launcher.feature.settings.sources.RomSourcesScreen
import dev.handheld.launcher.feature.settings.sources.RomSourcesScreenState
import dev.handheld.launcher.platform.system.SupportedSystemAction
import dev.handheld.launcher.core.data.metadata.ArtworkSummary
import dev.handheld.launcher.feature.settings.metadata.ArtworkSettingsCallbacks
import dev.handheld.launcher.feature.settings.metadata.ArtworkSettingsScreen

data class CategorySummary(val category: LibraryCategory, val count: Int)
data class SettingsScreenState(
    val confirmBackMapping: ConfirmBackMapping,
    val defaultHomeSummary: String,
    val categorySummaries: List<CategorySummary> = emptyList(),
    val romSources: RomSourcesScreenState = RomSourcesScreenState(),
    val emulators: EmulatorSettingsScreenState = EmulatorSettingsScreenState(),
    val artwork: ArtworkSummary = ArtworkSummary(),
)
data class SettingsCallbacks(
    val onSetConfirmBackMapping: (ConfirmBackMapping) -> Unit,
    val onRequestDefaultHome: () -> Unit,
    val onOpenSystemAction: (String) -> Unit,
    val onOpenCategory: (LibraryCategory) -> Unit,
    val onFocusedAction: OnFocusedAction = {},
    val romSources: RomSourcesCallbacks = RomSourcesCallbacks(),
    val emulators: EmulatorSettingsCallbacks = EmulatorSettingsCallbacks(),
    val artwork: ArtworkSettingsCallbacks = ArtworkSettingsCallbacks(),
)

private val settingsSections = listOf("Controls", "Launcher", "ROM folders", "Emulators", "Artwork", "Android")

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(
    state: SettingsScreenState,
    modifier: Modifier,
    callbacks: SettingsCallbacks,
    systemActions: List<SupportedSystemAction>,
    initialSection: String = "Launcher",
    restoreFocusRequest: Int = 0,
) = BoxWithConstraints(modifier.fillMaxSize()) {
    var section by rememberSaveable(initialSection) {
        mutableStateOf(initialSection.takeIf { it in settingsSections } ?: "Launcher")
    }
    val sectionRequesters = remember { settingsSections.associateWith { FocusRequester() } }
    if (maxWidth > 600.dp) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.lg)) {
        Column(Modifier.width(176.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xs)) {
            PageHeading("Settings")
            settingsSections.forEach { label ->
                val selected = section == label
                ActionRow(
                    label = label,
                    supportingText = null,
                    selected = selected,
                    modifier = Modifier
                        .focusRequester(sectionRequesters.getValue(label))
                        .semantics { this.selected = selected },
                    onFocusChanged = settingsFocus(
                        "Open $label settings",
                        LauncherActionMeaning.CHANGE_FILTER,
                        { section = label },
                        callbacks.onFocusedAction,
                    ),
                    onActivate = { section = label },
                )
            }
        }
        SettingsBody(state, callbacks, systemActions, section, Modifier.weight(1f), restoreFocusRequest)
    } else SettingsBody(state, callbacks, systemActions, "All", Modifier.fillMaxSize(), restoreFocusRequest)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SettingsBody(state: SettingsScreenState, callbacks: SettingsCallbacks, systemActions: List<SupportedSystemAction>, section: String, modifier: Modifier, restoreFocusRequest: Int) {
    val swapped = ConfirmBackMapping(state.confirmBackMapping.back, state.confirmBackMapping.confirm)
    val initial = remember { FocusRequester() }
    val hasInitialControl = when (section) {
        "Controls", "Launcher", "ROM folders", "Emulators", "Artwork", "All" -> true
        "Android" -> systemActions.isNotEmpty()
        else -> false
    }
    val inputMode = LocalInputModeManager.current
    val fontScale = LocalDensity.current.fontScale
    val motionSummary = if (LauncherTheme.motion.reducedMotion) "On" else "Off"
    LaunchedEffect(section, hasInitialControl, restoreFocusRequest) {
        if (hasInitialControl) {
            inputMode.requestInputMode(InputMode.Keyboard)
            withFrameNanos { }
            initial.requestFocus()
        }
    }
    val bodyScroll = rememberScrollState()
    LaunchedEffect(section) { bodyScroll.scrollTo(0) }
    Column(
        modifier.verticalScroll(bodyScroll),
        verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm),
    ) {
        if (section == "All") PageHeading("Settings")
        if (section == "All" || section == "Controls") {
            if (section != "All") PageHeading("Controls")
            ChoiceRow("Confirm button", state.confirmBackMapping.confirm.name,
                modifier = if (section == "All" || section == "Controls") Modifier.focusRequester(initial) else Modifier,
                onSelect = { callbacks.onSetConfirmBackMapping(swapped) }, supportingText = "Back uses ${state.confirmBackMapping.back.name}",
                onFocusChanged = settingsFocus("Swap Confirm and Back", LauncherActionMeaning.CHANGE_FILTER, { callbacks.onSetConfirmBackMapping(swapped) }, callbacks.onFocusedAction))
        }
        if (section == "All" || section == "Launcher") {
            if (section != "All") PageHeading("Launcher")
            ActionRow("Set as Home launcher", state.defaultHomeSummary,
                modifier = if (section == "Launcher") Modifier.focusRequester(initial) else Modifier,
                onActivate = callbacks.onRequestDefaultHome,
                onFocusChanged = settingsFocus("Set as Home launcher", LauncherActionMeaning.OPEN_SETTINGS, callbacks.onRequestDefaultHome, callbacks.onFocusedAction))
            LauncherText(
                "Display & text size · System text ${(fontScale * 100).toInt()}% · Reduced motion $motionSummary",
                color = LauncherTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = LauncherTheme.spacing.md),
            )
            state.categorySummaries.forEach { summary -> ActionRow(category(summary.category), "${summary.count} items", onActivate = { callbacks.onOpenCategory(summary.category) },
                onFocusChanged = settingsFocus(category(summary.category), LauncherActionMeaning.CHANGE_FILTER, { callbacks.onOpenCategory(summary.category) }, callbacks.onFocusedAction)) }
        }
        if (section == "All" || section == "ROM folders") {
            RomSourcesScreen(
                state.romSources,
                callbacks.romSources.copy(onFocusedAction = callbacks.onFocusedAction),
                initialFocusRequester = initial.takeIf { section == "ROM folders" },
            )
        }
        if (section == "All" || section == "Emulators") {
            EmulatorSettingsScreen(
                state.emulators,
                callbacks.emulators.copy(onFocusedAction = callbacks.onFocusedAction),
                initialFocusRequester = initial.takeIf { section == "Emulators" },
            )
        }
        if (section == "All" || section == "Artwork") {
            ArtworkSettingsScreen(state.artwork, callbacks.artwork, callbacks.onFocusedAction, initial.takeIf { section == "Artwork" })
        }
        if (section == "All" || section == "Android") {
            if (section != "All") PageHeading("Android")
            systemActions.forEachIndexed { index, action -> ActionRow(action.title, action.description,
                modifier = if (section == "Android" && index == 0) Modifier.focusRequester(initial) else Modifier,
                onActivate = { callbacks.onOpenSystemAction(action.key) },
                onFocusChanged = settingsFocus(action.title, LauncherActionMeaning.OPEN_SETTINGS, { callbacks.onOpenSystemAction(action.key) }, callbacks.onFocusedAction)) }
        }
    }
}

internal fun settingsFocus(label: String, meaning: LauncherActionMeaning, action: () -> Unit, callback: OnFocusedAction): (Boolean) -> Unit = { focused -> callback(if (focused) FocusedControlAction(LauncherActionDescriptor(SemanticInputAction.CONFIRM, meaning, label), action) else null) }
private fun category(category: LibraryCategory) = category.name.lowercase().replaceFirstChar { it.uppercase() }
