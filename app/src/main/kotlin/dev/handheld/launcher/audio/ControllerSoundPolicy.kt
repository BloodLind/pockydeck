package dev.handheld.launcher.audio

import dev.handheld.launcher.contract.SemanticInputAction

internal enum class ControllerSoundCue { MOVE, CONFIRM, BACK, PAGE, FILTER }

/** Acknowledges the input engine's handled actions, never raw key/axis reports or touch. */
internal class ControllerSoundPolicy(
    private val enabled: () -> Boolean,
    private val mediaAudible: () -> Boolean,
    private val play: (ControllerSoundCue) -> Unit,
) {
    private var active = false

    fun setActive(value: Boolean) { active = value }

    fun dispatch(action: SemanticInputAction, handle: (SemanticInputAction) -> Boolean): Boolean {
        val handled = handle(action)
        if (handled && active && enabled() && mediaAudible()) {
            // Optional feedback must not change the original action's result if audio fails.
            try { play(action.cue()) } catch (_: RuntimeException) { }
        }
        return handled
    }

    private fun SemanticInputAction.cue(): ControllerSoundCue = when (this) {
        SemanticInputAction.NAVIGATE_UP, SemanticInputAction.NAVIGATE_DOWN,
        SemanticInputAction.NAVIGATE_LEFT, SemanticInputAction.NAVIGATE_RIGHT -> ControllerSoundCue.MOVE
        SemanticInputAction.CONFIRM -> ControllerSoundCue.CONFIRM
        SemanticInputAction.BACK -> ControllerSoundCue.BACK
        SemanticInputAction.PREVIOUS_DESTINATION, SemanticInputAction.NEXT_DESTINATION,
        SemanticInputAction.SECONDARY, SemanticInputAction.TERTIARY,
        SemanticInputAction.MENU -> ControllerSoundCue.PAGE
        SemanticInputAction.PREVIOUS_FILTER, SemanticInputAction.NEXT_FILTER -> ControllerSoundCue.FILTER
    }
}
