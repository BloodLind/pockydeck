package dev.handheld.launcher.core.designsystem.contract

/** Presentation-only hooks for containing modal focus and restoring it after dismissal. */
data class ModalFocusLifecycle(
    val onModalShown: () -> Unit,
    val onModalDismissed: () -> Unit,
) {
    companion object {
        val None = ModalFocusLifecycle(
            onModalShown = {},
            onModalDismissed = {},
        )
    }
}
