package dev.handheld.launcher.feature.settings.sources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import dev.handheld.launcher.contract.LauncherActionMeaning
import dev.handheld.launcher.core.designsystem.controls.LauncherButton
import dev.handheld.launcher.core.designsystem.foundation.LauncherText
import dev.handheld.launcher.core.designsystem.layout.PageHeading
import dev.handheld.launcher.core.designsystem.settings.ActionRow
import dev.handheld.launcher.core.designsystem.settings.ChoiceRow
import dev.handheld.launcher.core.designsystem.settings.ToggleRow
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.core.domain.model.CatalogSourceId
import dev.handheld.launcher.core.domain.rom.RomSource
import dev.handheld.launcher.core.domain.rom.RomSourceStatus
import dev.handheld.launcher.core.domain.rom.scan.RomPlatforms
import dev.handheld.launcher.feature.collection.OnFocusedAction
import dev.handheld.launcher.feature.settings.settingsFocus

data class RomSourcesScreenState(
    val sources: List<RomSource> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val cacheLimitLabel: String = "8 GiB",
    val cacheUsageLabel: String = "",
    val preparingText: String? = null,
    val storageAccessGranted: Boolean = false,
    val automaticDiscoveryEnabled: Boolean = true,
    val discoveryBusy: Boolean = false,
    val discoveryFoldersVisited: Int = 0,
)

data class RomSourcesCallbacks(
    val onAddSource: () -> Unit = {},
    val onRescanSource: (CatalogSourceId) -> Unit = {},
    val onRemoveSource: (CatalogSourceId) -> Unit = {},
    val onRegrantSource: (CatalogSourceId) -> Unit = {},
    val onChooseSourcePlatform: (CatalogSourceId) -> Unit = {},
    val onChooseCacheLimit: () -> Unit = {},
    val onClearCache: () -> Unit = {},
    val onFocusedAction: OnFocusedAction = {},
    val onSetupStorageAccess: () -> Unit = {},
    val onSetAutomaticDiscovery: (Boolean) -> Unit = {},
    val onDiscoverFolders: () -> Unit = {},
)

/** Stateless source controls. The Activity owns folder grants and acknowledged requests. */
@Composable
fun RomSourcesScreen(
    state: RomSourcesScreenState,
    callbacks: RomSourcesCallbacks,
    modifier: Modifier = Modifier,
    initialFocusRequester: FocusRequester? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm)) {
        PageHeading("ROM folders")
        LauncherText(
            "Find console folders on this device, SD cards and USB storage, or add a folder yourself. Consoles appear after games are detected.",
            color = LauncherTheme.colors.textSecondary,
        )
        ActionRow(
            if (state.storageAccessGranted) "Storage access enabled" else "Set up automatic discovery",
            if (state.storageAccessGranted) "Read-only scans · Manage access in Android settings" else "Allow All files access in Android settings to find console folders",
            modifier = initialFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
            onActivate = callbacks.onSetupStorageAccess,
            onFocusChanged = settingsFocus("Storage access", LauncherActionMeaning.OPEN_SETTINGS, callbacks.onSetupStorageAccess, callbacks.onFocusedAction),
        )
        if (state.storageAccessGranted) {
            val toggle = { callbacks.onSetAutomaticDiscovery(!state.automaticDiscoveryEnabled) }
            ToggleRow("Discover new console folders", state.automaticDiscoveryEnabled, callbacks.onSetAutomaticDiscovery,
                supportingText = "Registered folders still update when this is off",
                onFocusChanged = settingsFocus("Automatic folder discovery", LauncherActionMeaning.CHANGE_FILTER, toggle, callbacks.onFocusedAction))
            if (state.automaticDiscoveryEnabled) ActionRow("Find folders now", "Refresh connected storage and registered games",
                enabled = !state.busy, onActivate = callbacks.onDiscoverFolders,
                onFocusChanged = settingsFocus("Find console folders", LauncherActionMeaning.ACTIVATE, callbacks.onDiscoverFolders, callbacks.onFocusedAction))
        }
        ActionRow(
            "Add ROM folder",
            "Select a folder on this device, an SD card or USB storage",
            onActivate = callbacks.onAddSource,
            onFocusChanged = settingsFocus("Add ROM folder", LauncherActionMeaning.OPEN_SETTINGS, callbacks.onAddSource, callbacks.onFocusedAction),
        )
        state.preparingText?.let { LauncherText(it, color = LauncherTheme.colors.textSecondary) }
        state.message?.let { LauncherText(it, color = LauncherTheme.colors.textSecondary) }
        if (state.busy && state.preparingText == null) {
            LauncherText(if (state.discoveryBusy) "Finding console folders… ${state.discoveryFoldersVisited} folders checked" else "Updating ROM library…", color = LauncherTheme.colors.textSecondary)
        }
        state.sources.forEach { source -> key(source.id.value) {
            SourceControls(source, callbacks)
        } }
        PageHeading("Game cache")
        LauncherText(
            "Compressed games are extracted when the selected emulator needs it. Cached copies can be removed; your ROMs are kept.",
            color = LauncherTheme.colors.textSecondary,
        )
        ChoiceRow(
            "Cache limit", state.cacheLimitLabel,
            onSelect = callbacks.onChooseCacheLimit,
            supportingText = state.cacheUsageLabel.takeIf { it.isNotBlank() },
            onFocusChanged = settingsFocus("Change game cache limit", LauncherActionMeaning.CHANGE_FILTER, callbacks.onChooseCacheLimit, callbacks.onFocusedAction),
        )
        ActionRow(
            "Clear game cache", "Choose unused copies or clear all after closing emulators",
            onActivate = callbacks.onClearCache,
            onFocusChanged = settingsFocus("Clear game cache", LauncherActionMeaning.ACTIVATE, callbacks.onClearCache, callbacks.onFocusedAction),
        )
    }
}

@Composable
private fun SourceControls(source: RomSource, callbacks: RomSourcesCallbacks) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xs)) {
        LauncherText(source.name, style = LauncherTheme.typography.settingLabel)
        if (source.automaticallyDiscovered) LauncherText("Found automatically", style = LauncherTheme.typography.settingSupporting,
            color = LauncherTheme.colors.textSecondary)
        LauncherText(sourceStatusLabel(source), color = LauncherTheme.colors.textSecondary)
        source.error?.takeIf { it.isNotBlank() }?.let { LauncherText(it, color = LauncherTheme.colors.textSecondary) }
        if (source.enabled) {
            val choose = { callbacks.onChooseSourcePlatform(source.id) }
            ChoiceRow(
                "Console for this folder",
                source.defaultPlatformId?.let { RomPlatforms.byId(it)?.displayName } ?: "Automatic",
                onSelect = choose,
                supportingText = "Overrides console detection for this folder",
                onFocusChanged = settingsFocus("Choose console for ${source.name}", LauncherActionMeaning.CHANGE_FILTER, choose, callbacks.onFocusedAction),
            )
        }
        val needsAccess = !source.enabled || source.status == RomSourceStatus.UNAVAILABLE
        val rescan = { callbacks.onRescanSource(source.id) }
        val regrant = { callbacks.onRegrantSource(source.id) }
        val remove = { callbacks.onRemoveSource(source.id) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xs)) {
            if (needsAccess) LauncherButton(
                if (source.enabled) "Restore access" else "Add again", regrant, Modifier.weight(1f),
                onFocusChanged = settingsFocus("Restore ${source.name}", LauncherActionMeaning.OPEN_SETTINGS, regrant, callbacks.onFocusedAction),
            ) else LauncherButton(
                if (source.status == RomSourceStatus.SCANNING) "Scanning…" else "Scan now", rescan,
                Modifier.weight(1f), enabled = source.status != RomSourceStatus.SCANNING,
                onFocusChanged = settingsFocus("Scan ${source.name}", LauncherActionMeaning.ACTIVATE, rescan, callbacks.onFocusedAction),
            )
            if (source.enabled) LauncherButton(
                "Remove folder", remove, Modifier.weight(1f),
                onFocusChanged = settingsFocus("Remove ${source.name}", LauncherActionMeaning.ACTIVATE, remove, callbacks.onFocusedAction),
            )
        }
    }
}

internal fun sourceStatusLabel(source: RomSource): String {
    val count = "${source.gameCount} ${if (source.gameCount == 1) "game" else "games"}"
    return when {
        !source.enabled -> "Removed · Favorites and history retained"
        source.status == RomSourceStatus.NOT_SCANNED -> "Waiting to scan"
        source.status == RomSourceStatus.SCANNING -> "Scanning… · $count indexed"
        source.status == RomSourceStatus.READY -> "$count indexed"
        source.status == RomSourceStatus.UNAVAILABLE -> "Folder unavailable · $count retained"
        source.status == RomSourceStatus.ERROR -> "Scan incomplete · $count retained"
        else -> "Disabled · $count retained"
    }
}
