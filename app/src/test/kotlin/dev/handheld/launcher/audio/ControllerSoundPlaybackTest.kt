package dev.handheld.launcher.audio

import org.junit.Assert.*
import org.junit.Test

class ControllerSoundPlaybackTest {
    @Test fun `rapid volume increases never boost a playing tail and mute cannot resume it`() {
        var now = 0L
        val starts = mutableListOf<Float>()
        val reductions = mutableListOf<Float>()
        val stopped = mutableListOf<Int>()
        val playback = ControllerSoundPlayback({ _, gain -> starts.add(gain); starts.size },
            stopped::add, { _, gain -> reductions.add(gain) }, { now })
        assertTrue(playback.play(ControllerSoundCue.MOVE, 40))
        listOf(100, 70, 30, 90, 10, 100, 0, 100, 40).forEach(playback::updateVolume)
        val initial = starts.single()
        assertTrue(initial > 0f && initial < .2f)
        assertEquals(initial * .75f, reductions[0], .000001f)
        assertEquals(initial * .25f, reductions[1], .000001f)
        assertEquals(listOf(1), stopped)
        now = 1_000
        assertTrue(playback.play(ControllerSoundCue.MOVE, 40))
        assertEquals(listOf(initial, initial), starts)
    }

    @Test fun `volume is clamped and a new cue retires the previous voice before starting`() {
        val events = mutableListOf<String>()
        var nextId = 0
        var now = 0L
        val playback = ControllerSoundPlayback({ _, gain -> events.add("start:$gain"); ++nextId },
            { events.add("stop:$it") }, { _, _ -> fail("No live volume change expected") }, { now })
        assertFalse(playback.play(ControllerSoundCue.SELECT, -1))
        assertFalse(playback.play(ControllerSoundCue.SELECT, 0))
        assertTrue(playback.play(ControllerSoundCue.MOVE, 200))
        now = 1_000
        assertTrue(playback.play(ControllerSoundCue.MOVE, 10))
        playback.stop()
        playback.stop()
        assertEquals(4, events.size)
        assertEquals("stop:1", events[1])
        assertEquals("stop:2", events[3])
        val full = events[0].substringAfter(":").toFloat()
        val tenth = events[2].substringAfter(":").toFloat()
        assertEquals(full / 10, tenth, .000001f)
    }

    @Test fun `ten thousand rapid inputs and volume changes cannot stack voices or queue sounds`() {
        var now = 0L
        var volume = 40
        var enabled = true
        var activeVoice: Int? = null
        var nextId = 0
        var activeGain = 0f
        val starts = mutableListOf<Pair<Long, ControllerSoundCue>>()
        val playback = ControllerSoundPlayback({ cue, gain ->
            assertNull("The previous voice must retire before another starts", activeVoice)
            assertTrue("Repeat compensation never boosts the chosen volume", gain > 0f && gain <= .5f * volume / 100f)
            starts += now to cue
            activeGain = gain
            (++nextId).also { activeVoice = it }
        }, { id -> assertEquals(activeVoice, id); activeVoice = null }, { id, gain ->
            assertEquals(activeVoice, id)
            assertTrue("A settings update may only attenuate a current cue", gain < activeGain)
            activeGain = gain
        }, { now })
        val policy = ControllerSoundPolicy({ enabled }, { true }, { now }) { playback.play(it, volume) }
        policy.setActive(true)
        val actions = dev.handheld.launcher.contract.SemanticInputAction.entries
        repeat(10_000) { index ->
            now += (index % 7).toLong()
            volume = (index % 11) * 10
            enabled = index % 17 != 0
            playback.updateVolume(if (enabled) volume else 0)
            assertTrue(policy.dispatch(actions[index % actions.size]) { true })
            policy.onItemSelected() // A duplicate focus callback in the same frame must not add a voice.
        }
        assertTrue(starts.size in 100..1_000)
        starts.zipWithNext().forEach { (previous, next) ->
            assertTrue(next.first - previous.first >= previous.second.minimumIntervalMillis)
        }
        val count = starts.size
        repeat(1_000) { volume = it % 101; playback.updateVolume(volume); now++ }
        assertEquals("No volume update can replay or enqueue a cue", count, starts.size)
        policy.setActive(false)
        playback.stop()
        assertFalse(policy.onItemSelected())
        assertNull(activeVoice)
    }

    @Test fun `volume reduction scales a repeat while increase cannot bounce its tail`() {
        var now = 0L
        var id = 0
        val gains = mutableListOf<Float>()
        val reductions = mutableListOf<Float>()
        val playback = ControllerSoundPlayback({ _, gain -> gains += gain; ++id }, {},
            { _, gain -> reductions += gain }, { now })
        assertTrue(playback.play(ControllerSoundCue.SELECT, 40))
        now = 80
        assertTrue(playback.play(ControllerSoundCue.SELECT, 40))
        val repeated = gains.last()
        assertEquals("Repeats are quieter than the first action", gains.first() * .45f, repeated, 0f)
        playback.updateVolume(20)
        assertEquals(repeated / 2f, reductions.single(), .000001f)
        playback.updateVolume(100)
        assertEquals("A raise cannot amplify an already playing repeat", 1, reductions.size)
        playback.stop()
        now = 160
        assertTrue(playback.play(ControllerSoundCue.SELECT, 40))
        assertEquals("Stopping must not introduce a level change", repeated, gains.last(), 0f)
        now = 1_000
        assertTrue(playback.play(ControllerSoundCue.SELECT, 40))
        assertEquals(gains.first(), gains.last())
    }
    @Test fun `stop mute and rapid resume cannot bypass the level cap cadence`() {
        var now = 0L
        var starts = 0
        val playback = ControllerSoundPlayback({ _, _ -> ++starts }, {}, { _, _ -> }, { now })
        assertTrue(playback.play(ControllerSoundCue.SELECT, 100))
        repeat(50) {
            playback.updateVolume(0)
            playback.stop()
            assertFalse(playback.play(ControllerSoundCue.MOVE, 100))
            now++
        }
        assertEquals(1, starts)
        assertTrue(playback.play(ControllerSoundCue.SELECT, 100))
        assertEquals(2, starts)
    }

    @Test fun `navigation repeats stay quiet across cue changes and recover after idle`() {
        var now = 0L
        val gains = mutableListOf<Float>()
        val loudness = ControllerSoundLoudness()
        val playback = ControllerSoundPlayback({ _, gain -> gains += gain; gains.size }, {}, { _, _ -> }, { now })
        assertTrue(playback.play(ControllerSoundCue.MOVE, 40))
        assertEquals(loudness.gain(ControllerSoundCue.MOVE, 40), gains.last(), 0f)
        now = 360 // The first held-direction repeat is already quieter.
        listOf(ControllerSoundCue.SELECT, ControllerSoundCue.MOVE, ControllerSoundCue.PAGE,
            ControllerSoundCue.FILTER, ControllerSoundCue.SELECT).forEach { cue ->
            assertTrue(playback.play(cue, 40))
            assertEquals(loudness.gain(cue, 40) * .45f, gains.last(), 0f)
            now += 100
        }
        now += 401 // More than 500 ms since the last successful navigation sound.
        assertTrue(playback.play(ControllerSoundCue.SELECT, 40))
        assertEquals(loudness.gain(ControllerSoundCue.SELECT, 40), gains.last(), 0f)
        now += 100
        assertTrue(playback.play(ControllerSoundCue.CONFIRM, 40))
        assertEquals(loudness.gain(ControllerSoundCue.CONFIRM, 40), gains.last(), 0f)
        now += 100
        assertTrue(playback.play(ControllerSoundCue.BACK, 40))
        assertEquals(loudness.gain(ControllerSoundCue.BACK, 40), gains.last(), 0f)
    }

    @Test fun `trigger repeats are quiet after the longer initial hold delay`() {
        var now = 0L
        val gains = mutableListOf<Float>()
        val playback = ControllerSoundPlayback({ _, gain -> gains += gain; gains.size }, {}, { _, _ -> }, { now })
        assertTrue(playback.play(ControllerSoundCue.FILTER, 40))
        now = 650
        assertTrue(playback.play(ControllerSoundCue.FILTER, 40))
        now += 100
        assertTrue(playback.play(ControllerSoundCue.FILTER, 40))
        assertEquals(gains.first() * .45f, gains[1], 0f)
        assertEquals(gains[1], gains[2], 0f)
        now += 751
        assertTrue(playback.play(ControllerSoundCue.FILTER, 40))
        assertEquals(gains.first(), gains.last(), 0f)
    }

    @Test fun `failed starts and muted requests do not create or extend a repeat burst`() {
        var now = 0L
        var loaded = false
        val gains = mutableListOf<Float>()
        val playback = ControllerSoundPlayback({ _, gain -> if (loaded) { gains += gain; gains.size } else 0 },
            {}, { _, _ -> }, { now })
        assertFalse(playback.play(ControllerSoundCue.SELECT, 40))
        loaded = true
        now = 100
        assertTrue(playback.play(ControllerSoundCue.SELECT, 40))
        assertEquals(ControllerSoundLoudness().gain(ControllerSoundCue.SELECT, 40), gains.single(), 0f)
        now = 400
        loaded = false
        assertFalse(playback.play(ControllerSoundCue.SELECT, 40))
        now = 500
        assertFalse(playback.play(ControllerSoundCue.SELECT, 0))
        loaded = true
        now = 601
        assertTrue(playback.play(ControllerSoundCue.SELECT, 40))
        assertEquals(gains.first(), gains.last(), 0f)
    }
}
