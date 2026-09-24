package dev.handheld.launcher.audio

/** Main-thread burst feedback: one start pulse, plus one end pulse only after repeated actions. */
internal class ControllerHapticFeedback(
    private val enabled: () -> Boolean,
    private val schedule: (Long, () -> Unit) -> (() -> Unit),
    private val pulse: (ControllerSoundCue) -> Boolean,
) {
    private var active = false
    private var held = false
    private var lastCue: ControllerSoundCue? = null
    private var repeated = false
    private var firstPulse = false
    private var cancelEnd: (() -> Unit)? = null

    fun setActive(value: Boolean) {
        active = value
        if (!value) { held = false; cancel() }
    }

    /** Holds cover the initial D-pad/trigger delay too, not just the accelerated repeats. */
    fun setHeld(value: Boolean) {
        held = value
        cancelEnd?.invoke()
        cancelEnd = null
        if (!held && lastCue != null) scheduleEnd()
    }

    fun onAction(cue: ControllerSoundCue): Boolean {
        if (!active || !enabled()) { cancel(); return false }
        val first = lastCue == null
        lastCue = cue
        if (first) firstPulse = emit(cue) else repeated = true
        if (!held) scheduleEnd()
        return first && firstPulse
    }

    /** Preference/lifecycle cancellation never emits a delayed pulse. */
    fun cancel() {
        cancelEnd?.invoke()
        cancelEnd = null
        lastCue = null
        repeated = false
        firstPulse = false
    }

    private fun scheduleEnd() {
        cancelEnd?.invoke()
        cancelEnd = schedule(160L) {
            cancelEnd = null
            val cue = lastCue
            val finish = repeated && firstPulse && active && enabled() && !held
            lastCue = null
            repeated = false
            firstPulse = false
            if (finish && cue != null) emit(cue)
        }
    }

    private fun emit(cue: ControllerSoundCue): Boolean =
        try { pulse(cue) } catch (_: RuntimeException) { false }
}
