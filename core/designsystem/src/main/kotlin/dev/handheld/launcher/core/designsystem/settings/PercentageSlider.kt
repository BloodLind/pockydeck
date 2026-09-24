package dev.handheld.launcher.core.designsystem.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.designsystem.contract.LocalControllerInput
import dev.handheld.launcher.core.designsystem.contract.rememberControlFocusRestoration
import dev.handheld.launcher.core.designsystem.foundation.LauncherSurface
import dev.handheld.launcher.core.designsystem.foundation.LauncherText
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import kotlin.math.abs
import kotlin.math.roundToInt

data class SliderInteraction(
    val onConfirm: () -> Unit,
    val onAdjust: ((Int) -> Unit)?,
    val onFinish: (() -> Unit)?,
)

/** Confirm enters adjustment; Back finishes without moving focus or undoing the value. */
@Composable
fun PercentageSlider(
    label: String,
    value: Int,
    values: List<Int>,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    onInteractionChanged: (SliderInteraction?) -> Unit = {},
) {
    require(values.size >= 2 && values.zipWithNext().all { (a, b) -> a < b })
    val restoration = rememberControlFocusRestoration()
    val feedback = dev.handheld.launcher.core.designsystem.contract.rememberTouchFeedback()
    var index by remember(values) { mutableIntStateOf(values.indices.minBy { abs(values[it] - value) }) }
    LaunchedEffect(value, values) { index = values.indices.minBy { abs(values[it] - value) } }
    val change by rememberUpdatedState(onValueChange)
    val reportFocus by rememberUpdatedState(onInteractionChanged)
    var focused by remember { mutableStateOf(false) }
    var adjusting by remember { mutableStateOf(false) }
    val select: (Int) -> Unit = { requested ->
        val next = requested.coerceIn(values.indices)
        if (index != next) { index = next; change(values[next]); feedback() }
    }
    val selectLatest by rememberUpdatedState(select)
    val adjust = remember(values) { { delta: Int -> selectLatest(index + delta) } }
    val confirm = remember { { adjusting = !adjusting } }
    val finish = remember { { adjusting = false } }
    LaunchedEffect(focused, adjusting, adjust) {
        if (focused) reportFocus(SliderInteraction(confirm, adjust.takeIf { adjusting }, finish.takeIf { adjusting }))
    }
    DisposableEffect(Unit) { onDispose { if (focused) reportFocus(null) } }
    val controller = LocalControllerInput.current
    val color = LauncherTheme.colors.focus
    val track = LauncherTheme.colors.textMuted
    LauncherSurface(
        modifier = modifier.fillMaxWidth().focusRequester(restoration.requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) restoration.record()
                if (!it.isFocused) { adjusting = false; reportFocus(null) }
            }
            .onKeyEvent {
                if (adjusting && (it.key == Key.DirectionLeft || it.key == Key.DirectionRight)) {
                    if (it.type == KeyEventType.KeyDown) adjust(if (it.key == Key.DirectionRight) 1 else -1)
                    true
                } else if (it.key == Key.Enter || it.key == Key.DirectionCenter) {
                    if (it.type == KeyEventType.KeyUp) confirm()
                    true
                } else if (adjusting && (it.key == Key.Escape || it.key == Key.Back)) {
                    if (it.type == KeyEventType.KeyUp) finish()
                    true
                } else false
            }
            .semantics {
                contentDescription = label
                stateDescription = "${values[index]}%"
                progressBarRangeInfo = ProgressBarRangeInfo(values[index].toFloat(),
                    values.first().toFloat()..values.last().toFloat(), values.size - 2)
                setProgress { requested ->
                    selectLatest(values.indices.minBy { abs(values[it] - requested) }); true
                }
                onClick(label = if (adjusting) "Finish adjustment" else "Adjust") { confirm(); true }
            }.focusable(),
        focused = focused && controller,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = LauncherTheme.spacing.md, vertical = LauncherTheme.spacing.sm)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LauncherText(label, style = LauncherTheme.typography.settingLabel)
                LauncherText(if (adjusting) "‹  ${values[index]}%  ›" else "${values[index]}%", style = LauncherTheme.typography.settingValue)
            }
            if (supportingText != null) LauncherText(supportingText,
                style = LauncherTheme.typography.settingSupporting, color = LauncherTheme.colors.textSecondary)
            Canvas(Modifier.fillMaxWidth().height(48.dp).pointerInput(values) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    adjusting = false
                    restoration.record()
                    restoration.requester.requestFocus()
                    fun update(x: Float) {
                        val inset = 10.dp.toPx()
                        val fraction = ((x - inset) / (size.width - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        selectLatest((fraction * values.lastIndex).roundToInt())
                    }
                    update(down.position.x)
                    down.consume()
                    do {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        update(pointer.position.x)
                        pointer.consume()
                    } while (pointer.pressed)
                }
            }) {
                val inset = 10.dp.toPx()
                val start = Offset(inset, center.y)
                val end = Offset(size.width - inset, center.y)
                val thumb = Offset(start.x + (end.x - start.x) * index / values.lastIndex, center.y)
                drawLine(track, start, end, 4.dp.toPx(), StrokeCap.Round)
                drawLine(color, start, thumb, 4.dp.toPx(), StrokeCap.Round)
                drawCircle(color, 8.dp.toPx(), thumb)
            }
        }
    }
}
