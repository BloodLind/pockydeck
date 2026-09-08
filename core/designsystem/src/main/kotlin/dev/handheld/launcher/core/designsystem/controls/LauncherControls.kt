package dev.handheld.launcher.core.designsystem.controls

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.designsystem.foundation.LauncherSurface
import dev.handheld.launcher.core.designsystem.foundation.LauncherText
import dev.handheld.launcher.core.designsystem.foundation.launcherContentColor
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme

private const val DefaultUnavailableReason = "Unavailable"

/** A state-driven, keyboard and touch activatable launcher action. */
@Composable
fun LauncherButton(
    label: String,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    unavailable: Boolean = false,
    unavailableReason: String = DefaultUnavailableReason,
    onFocusChanged: (Boolean) -> Unit = {},
    contentDescription: String? = label,
    shape: Shape = RoundedCornerShape(LauncherTheme.shapes.smallControl),
) {
    val restoration = rememberControlFocusRestoration()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    LauncherSurface(
        modifier = modifier.focusRequester(restoration.requester)
            .onFocusChanged { state ->
                focused = state.isFocused
                onFocusChanged(state.isFocused)
                if (state.isFocused) restoration.record()
            }
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = { restoration.record(); onActivate() },
            ),
        focused = focused,
        pressed = pressed,
        enabled = enabled,
        unavailable = unavailable,
        unavailableReason = unavailableReason,
        contentDescription = contentDescription,
        shape = shape,
    ) {
        LauncherText(label, Modifier.padding(horizontal = LauncherTheme.spacing.md, vertical = LauncherTheme.spacing.xs),
            style = LauncherTheme.typography.controlLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** An activatable icon control. The supplied description is the control's accessible name. */
@Composable
fun LauncherIconButton(
    contentDescription: String,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    unavailable: Boolean = false,
    unavailableReason: String = DefaultUnavailableReason,
    onFocusChanged: (Boolean) -> Unit = {},
    shape: Shape = RoundedCornerShape(LauncherTheme.shapes.smallControl),
    content: @Composable () -> Unit,
) {
    val restoration = rememberControlFocusRestoration()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    LauncherSurface(
        modifier = modifier.focusRequester(restoration.requester)
            .onFocusChanged { state ->
                focused = state.isFocused
                onFocusChanged(state.isFocused)
                if (state.isFocused) restoration.record()
            }
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = { restoration.record(); onActivate() },
            ),
        focused = focused,
        pressed = pressed,
        enabled = enabled,
        unavailable = unavailable,
        unavailableReason = unavailableReason,
        contentDescription = contentDescription,
        shape = shape,
    ) { content() }
}

/** A selected filter whose selection and controller focus are independent states. */
@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    unavailable: Boolean = false,
    unavailableReason: String = DefaultUnavailableReason,
    onFocusChanged: (Boolean) -> Unit = {},
    contentDescription: String? = label,
) {
    val restoration = rememberControlFocusRestoration()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    LauncherSurface(
        modifier = modifier.focusRequester(restoration.requester)
            .onFocusChanged { state ->
                focused = state.isFocused
                onFocusChanged(state.isFocused)
                if (state.isFocused) restoration.record()
            }
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.Checkbox,
                interactionSource = source,
                indication = null,
                onClick = { restoration.record(); onSelectedChange(!selected) },
            ),
        selected = selected,
        focused = focused,
        pressed = pressed,
        enabled = enabled,
        unavailable = unavailable,
        unavailableReason = unavailableReason,
        contentDescription = contentDescription,
        shape = RoundedCornerShape(LauncherTheme.shapes.smallControl),
    ) {
        LauncherText(label, Modifier.padding(horizontal = LauncherTheme.spacing.sm, vertical = LauncherTheme.spacing.xs),
            style = LauncherTheme.typography.controlLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A compact sort trigger. The caller owns popup presentation and selected option state. */
@Composable
fun SortSelector(
    selectedOption: String,
    options: List<String>,
    onOpenOptions: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    unavailable: Boolean = false,
    unavailableReason: String = DefaultUnavailableReason,
    onFocusChanged: (Boolean) -> Unit = {},
    contentDescription: String? = null,
) {
    require(options.isEmpty() || selectedOption in options) { "selectedOption must be in options" }
    LauncherButton(
        label = selectedOption,
        onActivate = onOpenOptions,
        modifier = modifier,
        enabled = enabled,
        unavailable = unavailable,
        unavailableReason = unavailableReason,
        onFocusChanged = onFocusChanged,
        contentDescription = contentDescription ?: "Sort: $selectedOption",
    )
}

/** Native IME-backed query input. Query ownership remains with the caller. */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    enabled: Boolean = true,
    onFocusChanged: (Boolean) -> Unit = {},
    keyboardOptions: KeyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Text,
        imeAction = ImeAction.Search,
    ),
    onSearch: () -> Unit = {},
    textStyle: TextStyle = LauncherTheme.typography.body,
    contentDescription: String = "Search",
) {
    var focused by remember { mutableStateOf(false) }
    val resolvedTextStyle = textStyle.copy(
        color = if (textStyle.color == Color.Unspecified) {
            if (enabled) launcherContentColor() else LauncherTheme.colors.textSecondary
        } else textStyle.color,
    )
    LauncherSurface(
        modifier = modifier,
        focused = focused,
        enabled = enabled,
        contentDescription = contentDescription,
        shape = RoundedCornerShape(LauncherTheme.shapes.smallControl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth().onFocusChanged { state ->
                        focused = state.isFocused
                        onFocusChanged(state.isFocused)
                    },
                    enabled = enabled,
                    textStyle = resolvedTextStyle,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    singleLine = true,
                    decorationBox = { field ->
                        if (query.isEmpty()) {
                            LauncherText(placeholder, color = LauncherTheme.colors.textSecondary)
                        }
                        field()
                    },
                )
            }
            Spacer(Modifier.width(8.dp))
        }
    }
}
