package dev.handheld.launcher.audio

/** Owns one voice. Volume increases apply to the next cue, never to an existing tail. */
internal class ControllerSoundPlayback(
    private val start: (ControllerSoundCue, Float) -> Int,
    private val stopStream: (Int) -> Unit,
    private val setStreamVolume: (Int, Float) -> Unit,
    private val monotonicTimeMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private var stream = 0
    private var playingGain = 0f
    private var playingRepeatGain = 1f
    private val loudness = ControllerSoundLoudness()

    fun play(cue: ControllerSoundCue, volumePercent: Int): Boolean {
        val volumeGain = gain(volumePercent)
        if (volumeGain == 0f) return false
        stop() // Also retires a late native tail; two voices can never add together.
        val repeatGain = loudness.gain(cue, monotonicTimeMillis())
        val gain = volumeGain * repeatGain
        if (gain == 0f) return false
        stream = start(cue, gain)
        playingGain = if (stream != 0) gain else 0f
        playingRepeatGain = repeatGain
        if (stream != 0) loudness.started(cue, repeatGain, monotonicTimeMillis())
        return stream != 0
    }

    fun updateVolume(volumePercent: Int) {
        val gain = gain(volumePercent) * playingRepeatGain
        if (gain == 0f) stop()
        else if (stream != 0 && gain < playingGain) {
            playingGain = gain
            try { setStreamVolume(stream, gain) } catch (_: RuntimeException) { stop() }
        }
    }

    fun stop() {
        val previous = stream
        stream = 0
        playingGain = 0f
        playingRepeatGain = 1f
        if (previous != 0) try { stopStream(previous) } catch (_: RuntimeException) { }
    }

    private fun gain(percent: Int): Float = .5f * percent.coerceIn(0, 100) / 100f
}
