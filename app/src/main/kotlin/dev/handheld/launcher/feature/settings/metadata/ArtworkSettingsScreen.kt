package dev.handheld.launcher.feature.settings.metadata

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import dev.handheld.launcher.contract.LauncherActionMeaning
import dev.handheld.launcher.core.data.metadata.ArtworkSummary
import dev.handheld.launcher.core.designsystem.foundation.LauncherText
import dev.handheld.launcher.core.designsystem.layout.PageHeading
import dev.handheld.launcher.core.designsystem.settings.ActionRow
import dev.handheld.launcher.core.designsystem.settings.ChoiceRow
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.feature.collection.OnFocusedAction
import dev.handheld.launcher.feature.settings.settingsFocus

data class ArtworkSettingsCallbacks(val onSetPaused: (Boolean) -> Unit = {}, val onRetry: () -> Unit = {})

@Composable
fun ArtworkSettingsScreen(state: ArtworkSummary, callbacks: ArtworkSettingsCallbacks, onFocusedAction: OnFocusedAction,
    initial: FocusRequester? = null) {
    PageHeading("Artwork")
    val toggle = { callbacks.onSetPaused(!state.paused) }
    var toggleFocused by remember { mutableStateOf(false) }
    val toggleFocus = settingsFocus(if (state.paused) "Resume artwork" else "Pause artwork",
        LauncherActionMeaning.CHANGE_FILTER, toggle, onFocusedAction)
    // Refresh both the footer label and its action when A changes this focused setting.
    LaunchedEffect(toggleFocused, state.paused) { if (toggleFocused) toggleFocus(true) }
    ChoiceRow("Find missing artwork", if (state.paused) "Paused" else "Automatic",
        modifier = initial?.let { Modifier.focusRequester(it) } ?: Modifier,
        supportingText = "ES-DE covers first; missing covers download in the background",
        onSelect = toggle,
        onFocusChanged = { focused -> toggleFocused = focused; toggleFocus(focused) })
    LauncherText("${state.ready} ready · ${state.pending} pending · ${state.missing} without a match · ${state.failed} failed",
        style = LauncherTheme.typography.settingSupporting, color = LauncherTheme.colors.textSecondary)
    ActionRow("Retry missing artwork", "Check local media again and retry failed or unmatched games", onActivate = callbacks.onRetry,
        onFocusChanged = settingsFocus("Retry artwork", LauncherActionMeaning.ACTIVATE, callbacks.onRetry, onFocusedAction))
    LauncherText("Online source: Libretro thumbnails. No account or API key is needed. Downloads request console and game-image names; ROMs, saves and local paths stay on this device.",
        style = LauncherTheme.typography.settingSupporting, color = LauncherTheme.colors.textSecondary)
    LauncherText("Covers are cached for offline use. Original artwork belongs to its respective rights holders. Local ES-DE media and your custom artwork are preserved.",
        style = LauncherTheme.typography.settingSupporting, color = LauncherTheme.colors.textSecondary)
}
