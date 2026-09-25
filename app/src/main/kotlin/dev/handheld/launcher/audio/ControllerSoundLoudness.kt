package dev.handheld.launcher.audio

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/** Fixed headroom for the fastest possible burst; no attack/release gain pumping. */
internal class ControllerSoundLoudness {
    private val gains = ControllerSoundCue.entries.associateWith { cue ->
        // Assets are normalized to .044 RMS and <= .20 peak. Include quantization
        // and one frame of duration rounding; packaged-PCM tests enforce these bounds.
        val duration = cue.durationMillis + 1_000.0 / 44_100
        val weightedEnergy = .0441.pow(2) * duration / RMS_WINDOW_MILLIS * exp(duration / RMS_WINDOW_MILLIS)
        // Treat each entire clip as a conservative energy impulse at its start.
        // The exponential weight covers its later samples. With starts >= 40 ms
        // apart, the geometric sum remains below the RMS ceiling indefinitely.
        val budget = RMS_CEILING.pow(2) * (1 - exp(-MINIMUM_START_INTERVAL_MILLIS / RMS_WINDOW_MILLIS))
        minOf(.5, PEAK_CEILING / .201, sqrt(budget / weightedEnergy)).toFloat()
    }

    fun gain(cue: ControllerSoundCue, volumePercent: Int): Float =
        gains.getValue(cue) * volumePercent.coerceIn(0, 100) / 100f

    companion object {
        const val MINIMUM_START_INTERVAL_MILLIS = 40L
        const val RMS_WINDOW_MILLIS = 125.0
        // Digital app-signal limits at 100% launcher volume, before Android's mixer.
        // These are dBFS, not acoustic dB at the handheld's speakers.
        val PEAK_CEILING = 10.0.pow(-24.0 / 20)
        val RMS_CEILING = 10.0.pow(-36.0 / 20)
    }
}
