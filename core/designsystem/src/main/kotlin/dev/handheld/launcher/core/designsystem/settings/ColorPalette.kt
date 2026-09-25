package dev.handheld.launcher.core.designsystem.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.designsystem.contract.LocalControllerInput
import dev.handheld.launcher.core.designsystem.contract.rememberControlFocusRestoration
import dev.handheld.launcher.core.designsystem.contract.rememberTouchFeedback
import dev.handheld.launcher.core.designsystem.foundation.LauncherText
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import java.util.Locale
import kotlin.math.roundToInt

data class ColorPreset(val key: String, val label: String, val color: Color)
data class PaletteInteraction(
    val onConfirm: () -> Unit,
    val onAdjust: ((Int) -> Unit)? = null,
    val onAdjustVertical: ((Int) -> Unit)? = null,
    val onFinish: (() -> Unit)? = null,
)

/** Direct touch color selection, with explicit controller adjustment and exit. */
@Composable
fun ColorPalette(
    color: Color,
    presets: List<ColorPreset>,
    selectedPreset: String?,
    onColorChange: (Color) -> Unit,
    onPresetSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onInteractionChanged: (PaletteInteraction?) -> Unit = {},
) {
    fun hsv(value: Color) = FloatArray(3).also { android.graphics.Color.colorToHSV(value.toArgb(), it) }
    val initial = remember { hsv(color) }
    var hue by remember { mutableFloatStateOf(initial[0]) }
    var saturation by remember { mutableFloatStateOf(initial[1]) }
    var brightness by remember { mutableFloatStateOf(initial[2]) }
    val change by rememberUpdatedState(onColorChange)
    LaunchedEffect(color) {
        // Preserve a chosen hue at black/gray, where RGB alone cannot encode it.
        if (Color.hsv(hue, saturation, brightness).toArgb() != color.toArgb()) {
            val next = hsv(color)
            if (next[1] > 0f && next[2] > 0f) hue = next[0]
            saturation = next[1]
            brightness = next[2]
        }
    }
    fun select(h: Float = hue, s: Float = saturation, v: Float = brightness) {
        val previous = Color.hsv(hue, saturation, brightness).toArgb()
        hue = h.coerceIn(0f, 359.99f)
        saturation = s.coerceIn(0f, 1f)
        brightness = v.coerceIn(0f, 1f)
        val next = Color.hsv(hue, saturation, brightness)
        if (next.toArgb() != previous) change(next)
    }
    val colors = LauncherTheme.colors
    val shape = RoundedCornerShape(LauncherTheme.shapes.smallControl)
    @Composable fun Heading(modifier: Modifier) {
        Column(modifier) {
            LauncherText("Background color", style = LauncherTheme.typography.settingLabel)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(18.dp).background(color, CircleShape).border(1.dp, colors.textSecondary, CircleShape))
                val name = presets.firstOrNull { it.key == selectedPreset }?.label ?: "Custom"
                LauncherText(name + String.format(Locale.ROOT, " · #%06X", color.toArgb() and 0xFFFFFF),
                    style = LauncherTheme.typography.settingSupporting, color = colors.textSecondary,
                    modifier = Modifier.testTag("background-color-value"))
            }
        }
    }
    @Composable fun Presets(modifier: Modifier) {
        Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            presets.forEach { preset ->
                PresetSwatch(preset, selectedPreset == preset.key, { onPresetSelect(preset.key) },
                    Modifier.weight(1f), onInteractionChanged)
            }
        }
    }
    Column(modifier.fillMaxWidth().background(colors.surfaceControl, shape)
        .border(1.dp, colors.borderEmphasis, shape).padding(LauncherTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xs)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 440.dp) Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm)) {
                Heading(Modifier.weight(1f))
                Presets(Modifier.width(256.dp))
            } else Column {
                Heading(Modifier.fillMaxWidth())
                Presets(Modifier.fillMaxWidth())
            }
        }
        PaletteControl("Saturation and brightness", "background-color-field", Modifier.fillMaxWidth().height(116.dp),
            description = "Saturation ${(saturation * 100).roundToInt()}%, brightness ${(brightness * 100).roundToInt()}%",
            onStepX = { select(s = saturation + it * .02f) },
            onStepY = { select(v = brightness - it * .02f) },
            onTouch = { x, y -> select(s = x, v = 1f - y) },
            onInteractionChanged = onInteractionChanged,
            accessibilityActions = listOf(
                CustomAccessibilityAction("Increase saturation") { select(s = saturation + .05f); true },
                CustomAccessibilityAction("Decrease saturation") { select(s = saturation - .05f); true },
                CustomAccessibilityAction("Increase brightness") { select(v = brightness + .05f); true },
                CustomAccessibilityAction("Decrease brightness") { select(v = brightness - .05f); true },
            )) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            marker(Offset(saturation * size.width, (1f - brightness) * size.height))
        }
        PaletteControl("Hue", "background-color-hue", Modifier.fillMaxWidth().height(48.dp),
            description = "${hue.roundToInt()} degrees", onStepX = { select(h = hue + it * 3f) },
            onTouch = { x, _ -> select(h = x * 359.99f) }, onInteractionChanged = onInteractionChanged,
            progress = hue, onProgress = { select(h = it) }) {
            drawRect(Brush.horizontalGradient((0..6).map { Color.hsv((it * 60f).coerceAtMost(359.99f), 1f, 1f) }))
            marker(Offset(hue / 359.99f * size.width, center.y))
        }
        LauncherText("Touch the palette, or confirm to adjust with the D-pad. Back finishes. Colors keep a dark finish.",
            style = LauncherTheme.typography.settingSupporting, color = colors.textSecondary)
    }
}

@Composable
private fun PresetSwatch(preset: ColorPreset, selected: Boolean, onSelect: () -> Unit,
    modifier: Modifier, report: (PaletteInteraction?) -> Unit) {
    val restoration = rememberControlFocusRestoration()
    val source = remember { MutableInteractionSource() }
    val currentSelect by rememberUpdatedState(onSelect)
    val currentReport by rememberUpdatedState(report)
    val activate = remember { { restoration.activate { currentSelect() } } }
    var focused by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { if (focused) currentReport(null) } }
    Box(modifier.height(48.dp).focusRequester(restoration.requester)
        .onFocusChanged {
            focused = it.isFocused
            if (focused) { restoration.record(); currentReport(PaletteInteraction(activate)) }
            else currentReport(null)
        }.testTag("background-preset-${preset.key}")
        .semantics { contentDescription = preset.label }
        .selectable(selected, source, indication = null, role = Role.RadioButton, onClick = activate),
        contentAlignment = Alignment.Center) {
        val border = if (focused && LocalControllerInput.current) LauncherTheme.colors.focus
            else if (selected) Color.White else LauncherTheme.colors.textMuted
        Box(Modifier.size(38.dp).border(if (selected || focused) 2.dp else 1.dp, border, CircleShape)
            .padding(4.dp).background(preset.color, CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Canvas(Modifier.size(12.dp)) {
                drawLine(Color.White, Offset(0f, size.height * .5f), Offset(size.width * .4f, size.height), 2.dp.toPx())
                drawLine(Color.White, Offset(size.width * .4f, size.height), Offset(size.width, 0f), 2.dp.toPx())
            }
        }
    }
}

@Composable
private fun PaletteControl(
    label: String, tag: String, modifier: Modifier, description: String,
    onStepX: (Int) -> Unit, onStepY: ((Int) -> Unit)? = null,
    onTouch: (Float, Float) -> Unit, onInteractionChanged: (PaletteInteraction?) -> Unit,
    accessibilityActions: List<CustomAccessibilityAction> = emptyList(),
    progress: Float? = null, onProgress: ((Float) -> Unit)? = null,
    draw: DrawScope.() -> Unit,
) {
    val restoration = rememberControlFocusRestoration()
    val feedback = rememberTouchFeedback()
    val stepX by rememberUpdatedState(onStepX)
    val stepY by rememberUpdatedState(onStepY)
    val touch by rememberUpdatedState(onTouch)
    val report by rememberUpdatedState(onInteractionChanged)
    var focused by remember { mutableStateOf(false) }
    var adjusting by remember { mutableStateOf(false) }
    val confirm = remember { { adjusting = !adjusting } }
    val finish = remember { { adjusting = false } }
    val adjustX = remember { { delta: Int -> stepX(delta) } }
    val adjustY = remember { { delta: Int -> stepY?.invoke(delta); Unit } }
    LaunchedEffect(focused, adjusting) {
        if (focused) report(PaletteInteraction(confirm, adjustX.takeIf { adjusting },
            adjustY.takeIf { adjusting && stepY != null }, finish.takeIf { adjusting }))
    }
    DisposableEffect(Unit) { onDispose { if (focused) report(null) } }
    val shape = RoundedCornerShape(LauncherTheme.shapes.smallControl / 2)
    val frame = if (focused && LocalControllerInput.current) LauncherTheme.colors.focus else LauncherTheme.colors.borderEmphasis
    Canvas(modifier.focusRequester(restoration.requester).onFocusChanged {
        focused = it.isFocused
        if (focused) restoration.record() else { adjusting = false; report(null) }
    }.onKeyEvent {
        when {
            adjusting && (it.key == Key.DirectionLeft || it.key == Key.DirectionRight) -> {
                if (it.type == KeyEventType.KeyDown) adjustX(if (it.key == Key.DirectionRight) 1 else -1)
                true
            }
            adjusting && stepY != null && (it.key == Key.DirectionUp || it.key == Key.DirectionDown) -> {
                if (it.type == KeyEventType.KeyDown) adjustY(if (it.key == Key.DirectionDown) 1 else -1)
                true
            }
            it.key == Key.Enter || it.key == Key.DirectionCenter -> {
                if (it.type == KeyEventType.KeyUp) confirm()
                true
            }
            adjusting && (it.key == Key.Escape || it.key == Key.Back) -> {
                if (it.type == KeyEventType.KeyUp) finish()
                true
            }
            else -> false
        }
    }.semantics {
        contentDescription = label
        stateDescription = description
        customActions = accessibilityActions
        onClick(label = "Adjust") { confirm(); true }
        if (progress != null && onProgress != null) {
            progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..359.99f)
            setProgress { onProgress(it.coerceIn(0f, 359.99f)); true }
        }
    }.testTag(tag).focusable().pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            adjusting = false
            restoration.record()
            restoration.requester.requestFocus()
            feedback()
            fun update(position: Offset) = touch((position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                (position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f))
            update(down.position)
            down.consume()
            do {
                val event = awaitPointerEvent()
                val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                update(pointer.position)
                pointer.consume()
            } while (pointer.pressed)
        }
    }.border(2.dp, frame, shape).padding(3.dp).clip(shape), onDraw = draw)
}

private fun DrawScope.marker(position: Offset) {
    val radius = 6.dp.toPx()
    val center = Offset(position.x.coerceIn(radius, size.width - radius), position.y.coerceIn(radius, size.height - radius))
    drawCircle(Color.Black.copy(alpha = .6f), radius + 2.dp.toPx(), center, style = Stroke(3.dp.toPx()))
    drawCircle(Color.White, radius, center, style = Stroke(2.dp.toPx()))
}
