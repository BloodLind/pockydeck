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
    private var playingCue: ControllerSoundCue? = null
    private var playingRepeatGain = 1f
    private var nextStart = Long.MIN_VALUE
    private var lastNavigationCue: ControllerSoundCue? = null
    private var lastNavigationStart = 0L
    private val loudness = ControllerSoundLoudness()

    fun play(cue: ControllerSoundCue, volumePercent: Int): Boolean {
        val now = monotonicTimeMillis()
        val baseGain = loudness.gain(cue, volumePercent)
        if (baseGain == 0f || now < nextStart) return false
        val navigation = cue != ControllerSoundCue.CONFIRM && cue != ControllerSoundCue.BACK
        // Keep one quiet level across direction changes and MOVE/SELECT callbacks.
        // Triggers have a longer initial hold delay (650 ms, versus 360 ms for D-pad).
        val repeatWindow = if (cue == ControllerSoundCue.FILTER && lastNavigationCue == cue) 750L else 500L
        val repeating = navigation && lastNavigationCue != null && now - lastNavigationStart <= repeatWindow
        val repeatGain = if (repeating) REPEAT_GAIN else 1f
        val gain = baseGain * repeatGain
        stop() // Also retires a late native tail; two voices can never add together.
        stream = start(cue, gain)
        playingGain = if (stream != 0) gain else 0f
        playingCue = if (stream != 0) cue else null
        playingRepeatGain = repeatGain
        // Stop/mute/resume cannot bypass the separation used by the level ceiling.
        if (stream != 0) {
            val startedAt = monotonicTimeMillis()
            nextStart = startedAt + cue.minimumIntervalMillis
            lastNavigationCue = if (navigation) cue else null
            lastNavigationStart = startedAt
        }
        return stream != 0
    }

    fun updateVolume(volumePercent: Int) {
        val gain = playingCue?.let { loudness.gain(it, volumePercent) * playingRepeatGain } ?: return
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
        playingCue = null
        playingRepeatGain = 1f
        if (previous != 0) try { stopStream(previous) } catch (_: RuntimeException) { }
    }

    companion object {
        // About -7 dB keeps fast RMS below the isolated click, even at the 40 ms minimum.
        const val REPEAT_GAIN = .45f
    }
}
