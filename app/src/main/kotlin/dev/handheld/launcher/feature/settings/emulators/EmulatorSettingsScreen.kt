package dev.handheld.launcher.feature.settings.emulators

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import dev.handheld.launcher.contract.LauncherActionMeaning
import dev.handheld.launcher.core.designsystem.foundation.LauncherText
import dev.handheld.launcher.core.designsystem.layout.PageHeading
import dev.handheld.launcher.core.designsystem.settings.ActionRow
import dev.handheld.launcher.core.designsystem.settings.ChoiceRow
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.feature.collection.OnFocusedAction
import dev.handheld.launcher.feature.settings.settingsFocus

data class ConsoleEmulatorRow(
    val platformId: String,
    val consoleName: String,
    val gameCount: Int,
    val emulatorLabel: String,
    val candidateCount: Int,
    val coreLabel: String? = null,
)

data class EmulatorSettingsScreenState(
    val consoles: List<ConsoleEmulatorRow> = emptyList(),
    val message: String? = null,
)

data class EmulatorSettingsCallbacks(
    val onChooseEmulator: (String) -> Unit = {},
    val onChooseCore: (String) -> Unit = {},
    val onAddSource: () -> Unit = {},
    val onFocusedAction: OnFocusedAction = {},
)

/** Only consoles with detected games are supplied here; available apps come from the resolver. */
@Composable
fun EmulatorSettingsScreen(
    state: EmulatorSettingsScreenState,
    callbacks: EmulatorSettingsCallbacks,
    modifier: Modifier = Modifier,
    initialFocusRequester: FocusRequester? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm)) {
        PageHeading("Emulators")
        LauncherText(
            "Choose an app for each console. When several compatible apps are installed, you can choose when opening a game.",
            color = LauncherTheme.colors.textSecondary,
        )
        state.message?.let { LauncherText(it, color = LauncherTheme.colors.textSecondary) }
        if (state.consoles.isEmpty()) {
            LauncherText("No consoles detected yet. Add a ROM folder to find your games.", color = LauncherTheme.colors.textSecondary)
            ActionRow(
                "Add ROM folder",
                modifier = initialFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
                onActivate = callbacks.onAddSource,
                onFocusChanged = settingsFocus("Add ROM folder", LauncherActionMeaning.OPEN_SETTINGS, callbacks.onAddSource, callbacks.onFocusedAction),
            )
        }
        state.consoles.forEachIndexed { index, console -> key(console.platformId) {
            val choose = { callbacks.onChooseEmulator(console.platformId) }
            ChoiceRow(
                console.consoleName, console.emulatorLabel, choose,
                supportingText = consoleEmulatorSummary(console),
                modifier = if (index == 0 && initialFocusRequester != null) Modifier.focusRequester(initialFocusRequester) else Modifier,
                onFocusChanged = settingsFocus("Choose ${console.consoleName} emulator", LauncherActionMeaning.CHANGE_FILTER, choose, callbacks.onFocusedAction),
            )
            console.coreLabel?.let { coreLabel ->
                val chooseCore = { callbacks.onChooseCore(console.platformId) }
                ChoiceRow(
                    "RetroArch core", coreLabel, chooseCore,
                    supportingText = "Install the selected core in RetroArch",
                    onFocusChanged = settingsFocus("Choose ${console.consoleName} core", LauncherActionMeaning.CHANGE_FILTER, chooseCore, callbacks.onFocusedAction),
                )
            }
        } }
    }
}

internal fun consoleEmulatorSummary(console: ConsoleEmulatorRow): String {
    val games = "${console.gameCount} ${if (console.gameCount == 1) "game" else "games"}"
    val apps = when (console.candidateCount) {
        0 -> "No compatible app installed"
        1 -> "1 compatible app"
        else -> "${console.candidateCount} compatible apps"
    }
    return "$games · $apps"
}
