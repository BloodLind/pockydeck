package dev.handheld.launcher.feature.settings.emulators

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import dev.handheld.launcher.contract.LauncherActionMeaning
import dev.handheld.launcher.core.designsystem.contract.ModalFocusLifecycle
import dev.handheld.launcher.core.designsystem.foundation.LauncherText
import dev.handheld.launcher.core.designsystem.modal.LauncherDialog
import dev.handheld.launcher.core.designsystem.settings.ActionRow
import dev.handheld.launcher.core.designsystem.settings.ToggleRow
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.feature.collection.OnFocusedAction
import dev.handheld.launcher.feature.settings.settingsFocus

data class RomChoiceOption(
    val id: String,
    val label: String,
    val supportingText: String? = null,
    val enabled: Boolean = true,
)

data class RomChoiceDialogState(
    val title: String,
    val options: List<RomChoiceOption>,
    val message: String? = null,
    val selectedId: String? = null,
    val rememberLabel: String? = null,
    val rememberSelection: Boolean = false,
)

data class RomChoiceDialogCallbacks(
    val onSelect: (String) -> Unit,
    val onDismiss: () -> Unit,
    val onRememberSelection: (Boolean) -> Unit = {},
    val onFocusedAction: OnFocusedAction = {},
)

/** Root navigation owns whether this appears and applies choices only after user activation. */
@Composable
fun RomChoiceDialog(
    state: RomChoiceDialogState,
    callbacks: RomChoiceDialogCallbacks,
    modifier: Modifier = Modifier,
    lifecycle: ModalFocusLifecycle = ModalFocusLifecycle.None,
) {
    LauncherDialog(state.title, callbacks.onDismiss, modifier, lifecycle = lifecycle) {
        state.message?.let { LauncherText(it, color = LauncherTheme.colors.textSecondary) }
        state.rememberLabel?.let { label ->
            val toggle = { callbacks.onRememberSelection(!state.rememberSelection) }
            ToggleRow(
                label, state.rememberSelection, callbacks.onRememberSelection,
                onFocusChanged = settingsFocus(label, LauncherActionMeaning.CHANGE_FILTER, toggle, callbacks.onFocusedAction),
            )
        }
        state.options.forEach { option -> key(option.id) {
            val select = { callbacks.onSelect(option.id) }
            ActionRow(
                option.label,
                supportingText = listOfNotNull(
                    "Selected".takeIf { option.id == state.selectedId }, option.supportingText,
                ).joinToString(" · ").takeIf { it.isNotEmpty() },
                enabled = option.enabled,
                modifier = Modifier.semantics { selected = option.id == state.selectedId },
                onActivate = select,
                onFocusChanged = settingsFocus(option.label, LauncherActionMeaning.ACTIVATE, select, callbacks.onFocusedAction),
            )
        } }
    }
}
