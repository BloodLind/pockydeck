package dev.handheld.launcher.core.designsystem.contract

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester

/** An optional presentation hook; the root can retain the actual control opening a modal. */
val LocalControlFocusRestoration = staticCompositionLocalOf<((() -> Unit) -> Unit)> { {} }

internal class ControlFocusRestoration(
    val requester: FocusRequester,
    private val publish: (() -> Unit) -> Unit,
) {
    fun record() { publish { requester.requestFocus() } }
}

@Composable
internal fun rememberControlFocusRestoration(): ControlFocusRestoration {
    val requester = remember { FocusRequester() }
    val publish = LocalControlFocusRestoration.current
    return remember(requester, publish) { ControlFocusRestoration(requester, publish) }
}
