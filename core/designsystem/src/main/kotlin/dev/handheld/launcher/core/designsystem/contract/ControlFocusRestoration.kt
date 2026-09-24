package dev.handheld.launcher.core.designsystem.contract

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester

/** Retains the exact control across modal returns and touch-to-controller changes. */
val LocalControlFocusRestoration = staticCompositionLocalOf<((() -> Unit) -> Unit)> { {} }
val LocalTouchFeedback = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
internal fun rememberTouchFeedback(): () -> Unit {
    val controller by rememberUpdatedState(LocalControllerInput.current)
    val feedback by rememberUpdatedState(LocalTouchFeedback.current)
    return remember { { if (!controller) feedback() } }
}

internal class ControlFocusRestoration(
    val requester: FocusRequester,
    private val publish: (() -> Unit) -> Unit,
    private val feedback: () -> Unit,
) {
    fun record() { publish { requester.requestFocus() } }
    fun activate(action: () -> Unit) { record(); action(); feedback() }
}

@Composable
internal fun rememberControlFocusRestoration(): ControlFocusRestoration {
    val requester = remember { FocusRequester() }
    val publish = LocalControlFocusRestoration.current
    val feedback = rememberTouchFeedback()
    return remember(requester, publish, feedback) { ControlFocusRestoration(requester, publish, feedback) }
}
