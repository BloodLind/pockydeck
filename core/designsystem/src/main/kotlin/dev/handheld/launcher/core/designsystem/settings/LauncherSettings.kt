package dev.handheld.launcher.core.designsystem.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import dev.handheld.launcher.core.designsystem.contract.rememberControlFocusRestoration
import dev.handheld.launcher.core.designsystem.contract.LocalControllerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.designsystem.foundation.LauncherSurface
import dev.handheld.launcher.core.designsystem.foundation.LauncherText
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme

@Composable
private fun SettingSurface(
    label: String,
    enabled: Boolean,
    modifier: Modifier,
    onFocusChanged: (Boolean) -> Unit,
    onActivate: () -> Unit,
    selected: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    val restoration = rememberControlFocusRestoration()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    LauncherSurface(
        modifier = modifier.fillMaxWidth().focusRequester(restoration.requester)
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
                if (it.isFocused) restoration.record()
            }
            .clickable(source, indication = null, enabled = enabled, role = Role.Button,
                onClick = { restoration.record(); onActivate() }),
        focused = focused && LocalControllerInput.current,
        pressed = pressed,
        enabled = enabled,
        contentDescription = label,
        shape = RoundedCornerShape(LauncherTheme.shapes.smallControl),
        selected = selected,
    ) {
        SettingContent(content)
    }
}

@Composable
private fun SettingContent(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(
            horizontal = LauncherTheme.spacing.md,
            vertical = LauncherTheme.spacing.sm,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.md),
        content = content,
    )
}

@Composable
private fun RowScope.SettingLabel(label: String, supportingText: String?, selected: Boolean = false) {
    Column(Modifier.weight(1f)) {
        LauncherText(label, style = LauncherTheme.typography.settingLabel,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (supportingText != null) {
            LauncherText(supportingText, style = LauncherTheme.typography.settingSupporting,
                color = if (selected) LauncherTheme.colors.destinationSelectedContent else LauncherTheme.colors.textSecondary, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SettingRow(
    label: String,
    supportingText: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
    onActivate: () -> Unit,
    selected: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    SettingSurface(label, enabled, modifier, onFocusChanged, onActivate, selected) {
        SettingLabel(label, supportingText, selected)
        trailing()
    }
}

@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    supportingText: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    val restoration = rememberControlFocusRestoration()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    LauncherSurface(
        modifier = modifier.fillMaxWidth().focusRequester(restoration.requester)
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
                if (it.isFocused) restoration.record()
            }
            .toggleable(value = checked, enabled = enabled, role = Role.Switch,
                interactionSource = source, indication = null,
                onValueChange = { restoration.record(); onCheckedChange(it) }),
        focused = focused && LocalControllerInput.current,
        pressed = pressed,
        enabled = enabled,
        contentDescription = label,
        shape = RoundedCornerShape(LauncherTheme.shapes.smallControl),
    ) {
        SettingContent {
            SettingLabel(label, supportingText)
            // One toggle target owns the whole row. The track/thumb is decoration.
            Box(
                Modifier.size(width = 40.dp, height = 24.dp)
                    .clearAndSetSemantics {}
                    .background(
                        if (checked) LauncherTheme.colors.confirm else LauncherTheme.colors.textMuted,
                        CircleShape,
                    ).padding(3.dp),
                contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(Modifier.size(18.dp).background(LauncherTheme.colors.textPrimary, CircleShape))
            }
        }
    }
}

@Composable
fun ChoiceRow(
    label: String,
    value: String,
    onSelect: () -> Unit,
    supportingText: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    SettingRow(label, supportingText, enabled, modifier, onFocusChanged, onSelect) {
        Box(Modifier.weight(.65f), contentAlignment = Alignment.CenterEnd) {
            LauncherText(value, style = LauncherTheme.typography.settingValue, color = LauncherTheme.colors.textSecondary,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ActionRow(
    label: String,
    supportingText: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
    onActivate: () -> Unit,
    selected: Boolean = false,
) = SettingRow(label, supportingText, enabled, modifier, onFocusChanged, onActivate, selected)
