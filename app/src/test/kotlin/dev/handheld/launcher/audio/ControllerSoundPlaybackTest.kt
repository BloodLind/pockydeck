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
        assertEquals(listOf(.2f), starts)
        assertEquals(listOf(.15f, .05f), reductions)
        assertEquals(listOf(1), stopped)
        now = 1_000
        assertTrue(playback.play(ControllerSoundCue.MOVE, 40))
        assertEquals(listOf(.2f, .2f), starts)
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
        assertEquals(listOf("start:0.5", "stop:1", "start:0.05", "stop:2"), events)
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

    @Test fun `volume reduction scales a quiet repeat while increase cannot bounce its tail`() {
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
        assertTrue(repeated < gains.first())
        playback.updateVolume(20)
        assertEquals(repeated / 2f, reductions.single(), .000001f)
        playback.updateVolume(100)
        assertEquals("A raise cannot amplify an already playing repeat", 1, reductions.size)
        playback.stop()
        now = 160
        assertTrue(playback.play(ControllerSoundCue.SELECT, 40))
        assertTrue("Stopping or changing preferences must not reset burst attenuation", gains.last() <= repeated)
        now = 1_000
        assertTrue(playback.play(ControllerSoundCue.SELECT, 40))
        assertEquals(gains.first(), gains.last())
    }
}
