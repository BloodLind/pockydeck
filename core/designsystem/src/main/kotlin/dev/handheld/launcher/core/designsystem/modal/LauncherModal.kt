package dev.handheld.launcher.core.designsystem.modal

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.designsystem.contract.ModalFocusLifecycle
import dev.handheld.launcher.core.designsystem.contract.LocalControllerInput
import dev.handheld.launcher.core.designsystem.controls.LauncherButton
import dev.handheld.launcher.core.designsystem.foundation.LauncherSurface
import dev.handheld.launcher.core.designsystem.foundation.LauncherText
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme

data class ItemAction(
    val label: String,
    val enabled: Boolean = true,
    val onActivate: () -> Unit,
)

/** In-tree modal: app input ownership and origin restoration bind through [lifecycle]. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LauncherDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    lifecycle: ModalFocusLifecycle = ModalFocusLifecycle.None,
    compactDismiss: Boolean = false,
    compactDismissScale: Float = 1f,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    val currentLifecycle by rememberUpdatedState(lifecycle)
    val initialFocus = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current
    val controllerInput = LocalControllerInput.current
    DisposableEffect(Unit) {
        currentLifecycle.onModalShown()
        onDispose { currentLifecycle.onModalDismissed() }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Separate sibling behind the panel: absorb background touches without consuming
        // button/scroll gestures inside the panel or adding an extra focusable control.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f)).pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent().changes.forEach { it.consume() }
            }
        })
        val margin = LauncherTheme.spacing.lg
        LauncherSurface(
            modifier = modifier.align(Alignment.Center)
                .width(minOf(560.dp, (maxWidth - margin * 2).coerceAtLeast(0.dp)))
                .heightIn(max = (maxHeight - margin * 2).coerceAtLeast(0.dp))
                .focusRequester(initialFocus)
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup(),
            shape = RoundedCornerShape(LauncherTheme.shapes.homeInner),
        ) {
            Column(Modifier.padding(margin), verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.md)) {
                LauncherText(title, style = LauncherTheme.typography.pageTitle,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Column(
                    Modifier.weight(1f, fill = false).fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.md),
                    content = content,
                )
                if (compactDismiss) Box(Modifier.fillMaxWidth()) {
                    LauncherButton("Close", onDismissRequest, Modifier.align(Alignment.CenterEnd))
                } else LauncherButton("Close", onDismissRequest, Modifier.fillMaxWidth())
            }
        }
    }
    // Request the group's first enabled descendant once; Close is always its fallback.
    LaunchedEffect(controllerInput) {
        if (!controllerInput) return@LaunchedEffect
        inputMode.requestInputMode(InputMode.Keyboard)
        withFrameNanos { }
        initialFocus.requestFocus()
    }
}

@Composable
fun ItemActionList(actions: List<ItemAction>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xs)) {
        actions.forEach { action ->
            LauncherButton(action.label, action.onActivate, Modifier.fillMaxWidth(), enabled = action.enabled)
        }
    }
}
