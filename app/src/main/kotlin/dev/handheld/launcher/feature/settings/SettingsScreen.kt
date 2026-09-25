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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.testTag
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
import dev.handheld.launcher.core.designsystem.controls.FilterChip
import dev.handheld.launcher.core.designsystem.foundation.LauncherText
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.core.domain.model.ConfirmBackMapping
import dev.handheld.launcher.core.domain.model.DisplayPreferences
import dev.handheld.launcher.core.domain.model.BackgroundTint
import dev.handheld.launcher.ui.presentation.label
import dev.handheld.launcher.ui.presentation.color
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.handheld.launcher.core.designsystem.settings.ColorPalette
import dev.handheld.launcher.core.designsystem.settings.ColorPreset
import dev.handheld.launcher.core.domain.model.LibraryCategory
import dev.handheld.launcher.feature.collection.FocusedControlAction
import dev.handheld.launcher.feature.collection.OnFocusedAction
import dev.handheld.launcher.feature.settings.emulators.EmulatorSettingsCallbacks
import dev.handheld.launcher.feature.settings.emulators.EmulatorSettingsScreen
import dev.handheld.launcher.feature.settings.emulators.EmulatorSettingsScreenState
import dev.handheld.launcher.feature.settings.sources.RomSourcesCallbacks
import dev.handheld.launcher.feature.settings.sources.romSourcesItems
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
    val controllerSoundsEnabled: Boolean = true,
    val gridSizePercent: Int = 100,
    val homeRoleHeld: Boolean = false,
    val soundVolumePercent: Int = 40,
    val vibrationEnabled: Boolean = true,
    val homeArtworkBackground: Boolean = false,
    val listArtworkBackground: Boolean = false,
    val backgroundTint: BackgroundTint = BackgroundTint.PURPLE,
    val backgroundTintPercent: Int = 40,
    val backgroundGrainPercent: Int = 30,
    val backgroundCustomColorRgb: Int? = null,
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
    val onSetControllerSoundsEnabled: (Boolean) -> Unit = {},
    val onSetGridSizePercent: (Int) -> Unit = {},
    val onSetSoundVolumePercent: (Int) -> Unit = {},
    val onSetVibrationEnabled: (Boolean) -> Unit = {},
    val onSetHomeArtworkBackground: (Boolean) -> Unit = {},
    val onSetListArtworkBackground: (Boolean) -> Unit = {},
    val onSetBackgroundTint: (BackgroundTint) -> Unit = {},
    val onSetBackgroundTintPercent: (Int) -> Unit = {},
    val onSetBackgroundGrainPercent: (Int) -> Unit = {},
    val onSetBackgroundCustomColorRgb: (Int) -> Unit = {},
)

private val settingsSections = listOf("Controls", "Display", "Launcher", "ROM folders", "Emulators", "Artwork", "Android")

/** Both settings use the same touch, keyboard, and semantic-controller slider. */
@Composable
internal fun GridSizeChoices(percent: Int, onSelect: (Int) -> Unit, onFocusedAction: OnFocusedAction = {}) {
    ScaleSlider("Grid card size", percent, DisplayPreferences.supportedGridSizes, onSelect,
        onFocusedAction = onFocusedAction,
        supportingText = "Library, Apps and Favorites. Smaller cards show more columns; text size stays the same.")
}

@Composable
private fun ScaleSlider(label: String, percent: Int, values: List<Int>, onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier, onFocusedAction: OnFocusedAction, supportingText: String? = null) {
    dev.handheld.launcher.core.designsystem.settings.PercentageSlider(label, percent, values, onSelect, modifier,
        supportingText = supportingText, onInteractionChanged = { interaction ->
            onFocusedAction(interaction?.let {
                FocusedControlAction(LauncherActionDescriptor(SemanticInputAction.CONFIRM,
                    LauncherActionMeaning.CHANGE_FILTER, if (it.onAdjust != null) "Done" else "Adjust"),
                    onActivate = it.onConfirm, onAdjust = it.onAdjust, onBack = it.onFinish)
            })
        })
}

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
    val bodyScroll = rememberLazyListState()
    LaunchedEffect(section, hasInitialControl, restoreFocusRequest) {
        bodyScroll.scrollToItem(if (section == "ROM folders" && controllerInput) 2 else 0)
        if (hasInitialControl && controllerInput) {
            inputMode.requestInputMode(InputMode.Keyboard)
            withFrameNanos { }
            initial.requestFocus()
        }
    }
    LazyColumn(
        modifier.then(dev.handheld.launcher.core.designsystem.foundation.contentEntrance(section)).testTag("settings-body-list"),
        state = bodyScroll,
        verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm),
    ) {
        if (section == "All") item("settings-heading") { PageHeading("Settings") }
        if (section == "All" || section == "Controls") settingsSection("controls") {
            if (section != "All") PageHeading("Controls")
            ChoiceRow("Confirm button", state.confirmBackMapping.confirm.name,
                modifier = if (section == "All" || section == "Controls") Modifier.focusRequester(initial) else Modifier,
                onSelect = swapConfirmBack, supportingText = "Back uses ${state.confirmBackMapping.back.name}",
                onFocusChanged = settingsFocus("Swap Confirm and Back", LauncherActionMeaning.CHANGE_FILTER, swapConfirmBack, callbacks.onFocusedAction))
            val toggleSounds = { latestCallbacks.onSetControllerSoundsEnabled(!latestState.controllerSoundsEnabled) }
            ToggleRow("Sound effects", state.controllerSoundsEnabled, callbacks.onSetControllerSoundsEnabled,
                supportingText = "Short, soft clicks. Also follows Android media volume.",
                onFocusChanged = settingsFocus("Toggle sound effects", LauncherActionMeaning.CHANGE_FILTER,
                    toggleSounds, callbacks.onFocusedAction))
            ScaleSlider("Sound volume", state.soundVolumePercent, (0..100 step 10).toList(),
                onSelect = { latestCallbacks.onSetSoundVolumePercent(it) },
                onFocusedAction = { latestCallbacks.onFocusedAction(it) },
                supportingText = "0% is silent. Does not change game or emulator volume.")
            val toggleVibration = { latestCallbacks.onSetVibrationEnabled(!latestState.vibrationEnabled) }
            ToggleRow("Vibration feedback", state.vibrationEnabled, callbacks.onSetVibrationEnabled,
                supportingText = "Light taps where supported. Follows Android touch feedback settings.",
                onFocusChanged = settingsFocus("Toggle vibration feedback", LauncherActionMeaning.CHANGE_FILTER,
                    toggleVibration, callbacks.onFocusedAction))
        }
        if (section == "All" || section == "Display") settingsSection("display") {
            PageHeading("Display")
            LauncherText("UI scale changes text, artwork, and controls. System text size ${(fontScale * 100).toInt()}% also applies.",
                style = LauncherTheme.typography.settingSupporting, color = LauncherTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = LauncherTheme.spacing.md))
            ScaleSlider("UI scale", state.uiScalePercent, DisplayPreferences.supportedScales,
                onSelect = { latestCallbacks.onSetUiScalePercent(it) },
                modifier = if (section == "Display") Modifier.focusRequester(initial) else Modifier,
                onFocusedAction = { latestCallbacks.onFocusedAction(it) })
            GridSizeChoices(state.gridSizePercent,
                onSelect = { latestCallbacks.onSetGridSizePercent(it) },
                onFocusedAction = { latestCallbacks.onFocusedAction(it) })
            ToggleRow("Reduce motion", state.reduceMotion, onCheckedChange = callbacks.onSetReduceMotion,
                supportingText = "Remove decorative movement and animated transitions",
                onFocusChanged = settingsFocus("Toggle reduced motion", LauncherActionMeaning.CHANGE_FILTER, toggleReduceMotion, callbacks.onFocusedAction))
            val toggleHomeBackground = { latestCallbacks.onSetHomeArtworkBackground(!latestState.homeArtworkBackground) }
            ColorPalette(
                color = state.backgroundCustomColorRgb?.let { Color(it or 0xFF000000.toInt()) } ?: state.backgroundTint.color,
                presets = remember { BackgroundTint.entries.map { ColorPreset(it.persistedKey, it.label, it.color) } },
                selectedPreset = state.backgroundTint.persistedKey.takeIf { state.backgroundCustomColorRgb == null },
                onColorChange = { latestCallbacks.onSetBackgroundCustomColorRgb(it.toArgb() and 0xFFFFFF) },
                onPresetSelect = { latestCallbacks.onSetBackgroundTint(BackgroundTint.fromPersistedKey(it)) },
                modifier = Modifier.testTag("background-tint-choice"),
                onInteractionChanged = { interaction ->
                    latestCallbacks.onFocusedAction(interaction?.let {
                        FocusedControlAction(LauncherActionDescriptor(SemanticInputAction.CONFIRM,
                            LauncherActionMeaning.CHANGE_FILTER, if (it.onAdjust != null) "Done" else "Select"),
                            onActivate = it.onConfirm, onAdjust = it.onAdjust, onBack = it.onFinish,
                            onAdjustVertical = it.onAdjustVertical)
                    })
                })
            ScaleSlider("Tint strength", state.backgroundTintPercent, DisplayPreferences.supportedBackgroundLevels,
                onSelect = { latestCallbacks.onSetBackgroundTintPercent(it) },
                modifier = Modifier.testTag("background-tint-strength"),
                onFocusedAction = { latestCallbacks.onFocusedAction(it) },
                supportingText = "0% keeps neutral charcoal. Graphite is always neutral.")
            ScaleSlider("Grain intensity", state.backgroundGrainPercent, DisplayPreferences.supportedBackgroundLevels,
                onSelect = { latestCallbacks.onSetBackgroundGrainPercent(it) },
                modifier = Modifier.testTag("background-grain-intensity"),
                onFocusedAction = { latestCallbacks.onFocusedAction(it) },
                supportingText = "Soft matte texture. 0% gives a smooth background.")
            ToggleRow("Home artwork background", state.homeArtworkBackground, callbacks.onSetHomeArtworkBackground,
                modifier = Modifier.testTag("home-artwork-background-toggle"),
                supportingText = "Blur the selected ROM’s artwork behind Home. Updates when scrolling settles.",
                onFocusChanged = settingsFocus("Toggle Home artwork background", LauncherActionMeaning.CHANGE_FILTER,
                    toggleHomeBackground, callbacks.onFocusedAction))
            val toggleListBackground = { latestCallbacks.onSetListArtworkBackground(!latestState.listArtworkBackground) }
            ToggleRow("List artwork background", state.listArtworkBackground, callbacks.onSetListArtworkBackground,
                modifier = Modifier.testTag("list-artwork-background-toggle"),
                supportingText = "Blur the selected ROM’s artwork behind library lists. Updates when scrolling settles.",
                onFocusChanged = settingsFocus("Toggle list artwork background", LauncherActionMeaning.CHANGE_FILTER,
                    toggleListBackground, callbacks.onFocusedAction))
        }
        if (section == "All" || section == "Launcher") settingsSection("launcher") {
            if (section != "All") PageHeading("Launcher")
            val homeLabel = if (state.homeRoleHeld) "Change Home launcher" else "Set as Home launcher"
            ActionRow(homeLabel, state.defaultHomeSummary,
                modifier = if (section == "Launcher") Modifier.focusRequester(initial) else Modifier,
                onActivate = callbacks.onRequestDefaultHome,
                onFocusChanged = settingsFocus(homeLabel, LauncherActionMeaning.OPEN_SETTINGS, callbacks.onRequestDefaultHome, callbacks.onFocusedAction))
            state.categorySummaries.forEach { summary ->
                Row(Modifier.fillMaxWidth().padding(horizontal = LauncherTheme.spacing.md, vertical = LauncherTheme.spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    LauncherText(category(summary.category), style = LauncherTheme.typography.settingLabel)
                    LauncherText("${summary.count} items", style = LauncherTheme.typography.settingSupporting,
                        color = LauncherTheme.colors.textSecondary)
                }
            }

        }
        if (section == "All" || section == "ROM folders") {
            romSourcesItems(
                state.romSources,
                callbacks.romSources.copy(onFocusedAction = callbacks.onFocusedAction),
                initialFocusRequester = initial.takeIf { section == "ROM folders" },
            )
        }
        if (section == "All" || section == "Emulators") settingsSection("emulators") {
            EmulatorSettingsScreen(
                state.emulators,
                callbacks.emulators.copy(onFocusedAction = callbacks.onFocusedAction),
                initialFocusRequester = initial.takeIf { section == "Emulators" },
            )
        }
        if (section == "All" || section == "Artwork") settingsSection("artwork") {
            ArtworkSettingsScreen(state.artwork, callbacks.artwork, callbacks.onFocusedAction, initial.takeIf { section == "Artwork" })
        }
        if (section == "All" || section == "Android") settingsSection("android") {
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

private fun LazyListScope.settingsSection(key: String, content: @Composable () -> Unit) {
    item("settings-$key") {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm)) { content() }
    }
}

internal fun settingsFocus(label: String, meaning: LauncherActionMeaning, action: () -> Unit, callback: OnFocusedAction): (Boolean) -> Unit = { focused -> callback(if (focused) FocusedControlAction(LauncherActionDescriptor(SemanticInputAction.CONFIRM, meaning, label), action) else null) }
private fun category(category: LibraryCategory) = category.name.lowercase().replaceFirstChar { it.uppercase() }
