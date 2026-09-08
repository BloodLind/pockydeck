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
import androidx.compose.runtime.rememberUpdatedState
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
import dev.handheld.launcher.core.designsystem.contract.LocalControllerInput
import dev.handheld.launcher.contract.LauncherActionDescriptor
import dev.handheld.launcher.contract.LauncherActionMeaning
import dev.handheld.launcher.contract.SemanticInputAction
import dev.handheld.launcher.core.designsystem.layout.PageHeading
import dev.handheld.launcher.core.designsystem.settings.ActionRow
import dev.handheld.launcher.core.designsystem.settings.ChoiceRow
import dev.handheld.launcher.core.designsystem.settings.ToggleRow
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
    val uiScalePercent: Int = 100,
    val reduceMotion: Boolean = false,
    val notificationAccessGranted: Boolean = false,
    val runningIndicatorsEnabled: Boolean = false,
    val runningStatusSummary: String = "Show verified app and emulator status on Home",
    val controllerSoundsEnabled: Boolean = true,
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
    val onSetUiScalePercent: (Int) -> Unit = {},
    val onSetReduceMotion: (Boolean) -> Unit = {},
    val onSetupNotificationAccess: () -> Unit = {},
    val onSetRunningIndicators: (Boolean) -> Unit = {},
    val onSetupRunningStatus: () -> Unit = {},
    val onSetControllerSoundsEnabled: (Boolean) -> Unit = {},
)

private val settingsSections = listOf("Controls", "Display", "Launcher", "ROM folders", "Emulators", "Artwork", "Android")

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
    val latestState by rememberUpdatedState(state)
    val latestCallbacks by rememberUpdatedState(callbacks)
    val swapConfirmBack = {
        val mapping = latestState.confirmBackMapping
        latestCallbacks.onSetConfirmBackMapping(ConfirmBackMapping(mapping.back, mapping.confirm))
    }
    val toggleReduceMotion = { latestCallbacks.onSetReduceMotion(!latestState.reduceMotion) }
    val initial = remember { FocusRequester() }
    val hasInitialControl = when (section) {
        "Controls", "Display", "Launcher", "ROM folders", "Emulators", "Artwork", "All" -> true
        "Android" -> true
        else -> false
    }
    val inputMode = LocalInputModeManager.current
    val fontScale = LocalDensity.current.fontScale
    val controllerInput = LocalControllerInput.current
    LaunchedEffect(section, hasInitialControl, restoreFocusRequest, controllerInput) {
        if (hasInitialControl && controllerInput) {
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
                onSelect = swapConfirmBack, supportingText = "Back uses ${state.confirmBackMapping.back.name}",
                onFocusChanged = settingsFocus("Swap Confirm and Back", LauncherActionMeaning.CHANGE_FILTER, swapConfirmBack, callbacks.onFocusedAction))
            val toggleSounds = { latestCallbacks.onSetControllerSoundsEnabled(!latestState.controllerSoundsEnabled) }
            ToggleRow("Controller sounds", state.controllerSoundsEnabled, callbacks.onSetControllerSoundsEnabled,
                supportingText = "Soft feedback for controller actions. Uses media volume.",
                onFocusChanged = settingsFocus("Toggle controller sounds", LauncherActionMeaning.CHANGE_FILTER,
                    toggleSounds, callbacks.onFocusedAction))
        }
        if (section == "All" || section == "Display") {
            PageHeading("Display")
            LauncherText("UI scale changes text, artwork, and controls. System text size ${(fontScale * 100).toInt()}% also applies.",
                style = LauncherTheme.typography.settingSupporting, color = LauncherTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = LauncherTheme.spacing.md))
            listOf(90 to "Compact", 100 to "Default", 110 to "Large", 120 to "Extra large").forEachIndexed { index, (percent, summary) ->
                val chooseScale = { latestCallbacks.onSetUiScalePercent(percent) }
                ChoiceRow("UI scale $percent%", summary, onSelect = chooseScale,
                    selected = state.uiScalePercent == percent,
                    modifier = if (section == "Display" && index == 0) Modifier.focusRequester(initial) else Modifier,
                    onFocusChanged = settingsFocus("Set UI scale to $percent%", LauncherActionMeaning.CHANGE_FILTER, chooseScale, callbacks.onFocusedAction))
            }
            ToggleRow("Reduce motion", state.reduceMotion, onCheckedChange = callbacks.onSetReduceMotion,
                supportingText = "Remove decorative movement and animated transitions",
                onFocusChanged = settingsFocus("Toggle reduced motion", LauncherActionMeaning.CHANGE_FILTER, toggleReduceMotion, callbacks.onFocusedAction))
        }
        if (section == "All" || section == "Launcher") {
            if (section != "All") PageHeading("Launcher")
            ActionRow("Set as Home launcher", state.defaultHomeSummary,
                modifier = if (section == "Launcher") Modifier.focusRequester(initial) else Modifier,
                onActivate = callbacks.onRequestDefaultHome,
                onFocusChanged = settingsFocus("Set as Home launcher", LauncherActionMeaning.OPEN_SETTINGS, callbacks.onRequestDefaultHome, callbacks.onFocusedAction))
            state.categorySummaries.forEach { summary ->
                Row(Modifier.fillMaxWidth().padding(horizontal = LauncherTheme.spacing.md, vertical = LauncherTheme.spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    LauncherText(category(summary.category), style = LauncherTheme.typography.settingLabel)
                    LauncherText("${summary.count} items", style = LauncherTheme.typography.settingSupporting,
                        color = LauncherTheme.colors.textSecondary)
                }
            }
            val toggleRunning = { latestCallbacks.onSetRunningIndicators(!latestState.runningIndicatorsEnabled) }
            ToggleRow("Running indicators", state.runningIndicatorsEnabled, callbacks.onSetRunningIndicators,
                supportingText = state.runningStatusSummary,
                onFocusChanged = settingsFocus("Toggle running indicators", LauncherActionMeaning.CHANGE_FILTER, toggleRunning, callbacks.onFocusedAction))
            if (state.runningIndicatorsEnabled) ActionRow("Set up Shizuku", "Optional access for live process readings",
                onActivate = callbacks.onSetupRunningStatus,
                onFocusChanged = settingsFocus("Set up Shizuku", LauncherActionMeaning.OPEN_SETTINGS,
                    callbacks.onSetupRunningStatus, callbacks.onFocusedAction))
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
            ActionRow("Notification indicator",
                if (state.notificationAccessGranted) "Enabled · Show a dot while notifications are present"
                else "Enable Notification access to show a status dot",
                modifier = if (section == "Android") Modifier.focusRequester(initial) else Modifier,
                onActivate = callbacks.onSetupNotificationAccess,
                onFocusChanged = settingsFocus("Notification indicator", LauncherActionMeaning.OPEN_SETTINGS,
                    callbacks.onSetupNotificationAccess, callbacks.onFocusedAction))
            systemActions.forEach { action -> ActionRow(action.title, action.description,
                onActivate = { callbacks.onOpenSystemAction(action.key) },
                onFocusChanged = settingsFocus(action.title, LauncherActionMeaning.OPEN_SETTINGS, { callbacks.onOpenSystemAction(action.key) }, callbacks.onFocusedAction)) }
        }
    }
}

internal fun settingsFocus(label: String, meaning: LauncherActionMeaning, action: () -> Unit, callback: OnFocusedAction): (Boolean) -> Unit = { focused -> callback(if (focused) FocusedControlAction(LauncherActionDescriptor(SemanticInputAction.CONFIRM, meaning, label), action) else null) }
private fun category(category: LibraryCategory) = category.name.lowercase().replaceFirstChar { it.uppercase() }
