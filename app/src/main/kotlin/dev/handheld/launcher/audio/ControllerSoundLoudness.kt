package dev.handheld.launcher.audio

import kotlin.math.exp
import kotlin.math.sqrt

/** Worker-owned energy budget for click bursts, separate from the user's volume setting. */
internal class ControllerSoundLoudness {
    private var lastStart: Long? = null
    private var recentEnergy = 0.0
    private var burstGain = 1f

    fun gain(cue: ControllerSoundCue, now: Long): Float {
        val inBurst = lastStart != null && elapsed(now) < BURST_IDLE_MILLIS
        // The budget treats a cue as an impulse; its real PCM occupies 18–72ms.
        // Leave headroom for that envelope and native start-time rounding.
        val budget = if (inBurst) .95 else 1.0
        val remaining = (budget - decayedEnergy(now)).coerceAtLeast(0.0)
        val energyGain = sqrt(remaining / cue.energy).toFloat().coerceIn(0f, 1f)
        // Timing jitter, a different cue or a missed repeat must not pump the level up.
        val ceiling = if (inBurst) burstGain else 1f
        return minOf(ceiling, energyGain)
    }

    /** Only successfully started audio spends the budget; dropped/unloaded requests do not. */
    fun started(cue: ControllerSoundCue, gain: Float, now: Long) {
        recentEnergy = decayedEnergy(now) + cue.energy * gain.toDouble() * gain
        burstGain = gain
        lastStart = now
    }

    private fun elapsed(now: Long): Long = lastStart?.let { (now - it).coerceAtLeast(0) } ?: 0
    private fun decayedEnergy(now: Long): Double =
        if (lastStart == null || elapsed(now) >= BURST_IDLE_MILLIS) 0.0
        else recentEnergy * exp(-elapsed(now) / ENERGY_DECAY_MILLIS)

    // Packaged cues have matched PCM RMS. Duration therefore measures their relative energy.
    private val ControllerSoundCue.energy: Double get() = durationMillis / REFERENCE_DURATION_MILLIS

    private companion object {
        const val ENERGY_DECAY_MILLIS = 200.0
        const val BURST_IDLE_MILLIS = 600L
        val REFERENCE_DURATION_MILLIS = ControllerSoundCue.entries.minOf { it.durationMillis }.toDouble()
    }
}
