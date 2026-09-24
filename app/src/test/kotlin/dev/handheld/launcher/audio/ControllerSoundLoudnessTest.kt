package dev.handheld.launcher.audio

import kotlin.math.exp
import org.junit.Assert.*
import org.junit.Test

class ControllerSoundLoudnessTest {
    @Test fun `repeats cannot accumulate energy or raise gain until the burst is over`() {
        val loudness = ControllerSoundLoudness()
        var now = 0L
        var energy = 0.0
        var previousGain = 1f
        val intervals = listOf(360L, 115L, 110L, 95L, 80L, 55L, 40L, 160L, 90L, 450L)
        repeat(10_000) { i ->
            val interval = if (i == 0) 0L else intervals[i % intervals.size]
            now += interval
            val cue = ControllerSoundCue.entries[i % ControllerSoundCue.entries.size]
            val gain = loudness.gain(cue, now)
            assertTrue("Jitter and mixed cues never raise a burst's gain", gain <= previousGain)
            // Independently integrate normalized sound energy; constant peaks alone are insufficient.
            energy = energy * exp(-interval / 200.0) + gain.toDouble() * gain * cue.durationMillis / 18.0
            assertTrue("A burst must fit one isolated click's energy budget: $energy", energy <= 1.000001)
            loudness.started(cue, gain, now)
            previousGain = gain
        }
        assertEquals("A quiet interval restores the ordinary click level", 1f,
            loudness.gain(ControllerSoundCue.MOVE, now + 600), 0f)
    }

    @Test fun `failed or dropped starts never make later clicks quieter`() {
        val loudness = ControllerSoundLoudness()
        repeat(10_000) {
            assertEquals(1f, loudness.gain(ControllerSoundCue.MOVE, it.toLong()), 0f)
        }
    }

    @Test fun `longer cues do not carry more energy and gain is never boosted`() {
        for (cue in ControllerSoundCue.entries) {
            val loudness = ControllerSoundLoudness()
            val gain = loudness.gain(cue, 0)
            assertTrue(gain in 0f..1f)
            assertEquals(18.0, cue.durationMillis * gain.toDouble() * gain, .00001)
            loudness.started(cue, gain, 0)
            val repeated = loudness.gain(cue, 80)
            assertTrue(repeated < gain)
            loudness.started(cue, repeated, 80)
            assertEquals("A missed repeat does not make the next click louder", repeated,
                loudness.gain(cue, 500), 0f)
        }
    }
}
