package dev.handheld.launcher.core.designsystem.controls

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import dev.handheld.launcher.core.designsystem.glyphs.LauncherGlyph
import dev.handheld.launcher.core.designsystem.glyphs.LauncherGlyphIcon
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
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
    shape: Shape = RoundedCornerShape(50),
    leadingIcon: LauncherGlyph? = null,
    maxLines: Int = 2,
) {
    val restoration = rememberControlFocusRestoration()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    LauncherSurface(
        modifier = modifier.widthIn(min = 64.dp).heightIn(min = 48.dp).focusRequester(restoration.requester)
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
        focused = focused && LocalControllerInput.current,
        pressed = pressed,
        enabled = enabled,
        unavailable = unavailable,
        unavailableReason = unavailableReason,
        contentDescription = contentDescription,
        shape = shape,
        visualPadding = PaddingValues(vertical = 6.dp),
    ) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center) {
            Row(Modifier.width(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) LauncherGlyphIcon(leadingIcon, Modifier.size(18.dp), contentDescription = null)
                // The visible control stays compact while its complete target remains 48dp.
                LauncherText(label, Modifier.weight(1f),
                    style = LauncherTheme.typography.controlLabel.copy(fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                    maxLines = maxLines, overflow = TextOverflow.Ellipsis)
            }
        }
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
    selected: Boolean = false,
    checked: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val restoration = rememberControlFocusRestoration()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    LauncherSurface(
        modifier = modifier.focusRequester(restoration.requester)
            .semantics { checked?.let { toggleableState = ToggleableState(it) } }
            .onFocusChanged { state ->
                focused = state.isFocused
                onFocusChanged(state.isFocused)
                if (state.isFocused) restoration.record()
            }
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = if (checked == null) Role.Button else Role.Checkbox,
                onClick = { restoration.record(); onActivate() },
            ),
        focused = focused && LocalControllerInput.current,
        selected = selected,
        pressed = pressed,
        enabled = enabled,
        unavailable = unavailable,
        unavailableReason = unavailableReason,
        contentDescription = contentDescription,
        shape = shape,
        visualPadding = PaddingValues(6.dp),
    ) {
        // The target fills the surface; its decorative glyph keeps the caller's size.
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** Shared padding and width arithmetic for rendered filters and finite lazy strips. */
@Immutable
data class FilterChipGeometry(
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val cornerRadius: Dp,
    val iconGap: Dp,
) {
    fun widthFor(labelWidth: Dp, trailingIconWidth: Dp = 0.dp, trailingGap: Dp = iconGap): Dp =
        (labelWidth + horizontalPadding * 2f +
            (if (trailingIconWidth > 0.dp) trailingIconWidth + trailingGap else 0.dp) + 1.dp)
            .coerceAtLeast(48.dp) // Allow native text/padding rounding without clipping a whole strip target.
}

@Composable
fun filterChipGeometry(visualScale: Float = 1f): FilterChipGeometry {
    val scale = LauncherTheme.referenceScale * LauncherTheme.smallControlScale * visualScale.coerceIn(.75f, 1.25f)
    return FilterChipGeometry(12.dp * scale, 6.dp * scale, 8.dp * scale, LauncherTheme.spacing.xxs / 2f)
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
    compact: Boolean = true,
    trailingIcon: (@Composable () -> Unit)? = null,
    trailingIconGap: Dp = LauncherTheme.spacing.xxs / 2f,
    visualScale: Float = 1f,
) {
    val scale = visualScale.coerceIn(.75f, 1.25f)
    val geometry = filterChipGeometry(scale)
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
        focused = focused && LocalControllerInput.current,
        pressed = pressed,
        enabled = enabled,
        unavailable = unavailable,
        unavailableReason = unavailableReason,
        contentDescription = contentDescription,
        shape = RoundedCornerShape(if (compact) geometry.cornerRadius else LauncherTheme.shapes.smallControl),
        compact = compact,
    ) {
        Row(Modifier.padding(horizontal = geometry.horizontalPadding, vertical = geometry.verticalPadding),
            verticalAlignment = Alignment.CenterVertically) {
            LauncherText(label, style = LauncherTheme.typography.controlLabel.let {
                it.copy(fontSize = it.fontSize * scale, lineHeight = it.lineHeight * scale)
            },
                color = if (selected) LauncherTheme.colors.destinationSelectedContent
                    else if (focused && LocalControllerInput.current) LauncherTheme.colors.textPrimary else LauncherTheme.colors.textSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            trailingIcon?.let {
                Spacer(Modifier.width(trailingIconGap))
                it()
            }
        }
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
    val restoration = rememberControlFocusRestoration()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    LauncherSurface(
        modifier = modifier.focusRequester(restoration.requester).onFocusChanged {
            focused = it.isFocused
            onFocusChanged(it.isFocused)
            if (it.isFocused) restoration.record()
        }.clickable(source, indication = null, enabled = enabled, role = Role.Button,
            onClick = { restoration.record(); onOpenOptions() }),
        focused = focused && LocalControllerInput.current,
        pressed = pressed,
        enabled = enabled,
        unavailable = unavailable,
        unavailableReason = unavailableReason,
        contentDescription = contentDescription ?: "Sort: $selectedOption",
        shape = RoundedCornerShape(50),
        compact = true,
    ) {
        Row(Modifier.padding(horizontal = LauncherTheme.spacing.sm, vertical = LauncherTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically) {
            val iconSize = 16.dp * LauncherTheme.referenceScale * LauncherTheme.smallControlScale
            LauncherGlyphIcon(LauncherGlyph.Sort, Modifier.size(iconSize), contentDescription = null)
            Spacer(Modifier.width(LauncherTheme.spacing.xxs))
            LauncherText(selectedOption, Modifier.weight(1f, fill = false), style = LauncherTheme.typography.controlLabel,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(LauncherTheme.spacing.xxs))
            LauncherGlyphIcon(LauncherGlyph.ExpandMore, Modifier.size(iconSize), contentDescription = null)
        }
    }
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
        focused = focused && LocalControllerInput.current,
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
