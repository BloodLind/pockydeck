package dev.handheld.launcher.audio

import org.junit.Assert.*
import org.junit.Test

class ControllerSoundDispatcherTest {
    @Test fun `a slow driver never blocks the producer or accumulates pending input sounds`() {
        var now = 0L
        var starts = 0
        val jobs = ArrayDeque<() -> Unit>()
        val playback = ControllerSoundPlayback({ _, _ -> now += 120; ++starts }, {}, { _, _ -> }, { now })
        val dispatcher = ControllerSoundDispatcher({ jobs.add(it); true }, { now }, { true }, { 40 }, playback)
        assertTrue(dispatcher.request(ControllerSoundCue.SELECT))
        assertEquals("Submitting must not run native audio on the caller", 0, starts)
        repeat(10_000) { assertFalse(dispatcher.request(ControllerSoundCue.MOVE)) }
        assertEquals(1, jobs.size)
        jobs.removeFirst().invoke()
        assertEquals(1, starts)
        assertFalse(dispatcher.request(ControllerSoundCue.CONFIRM))
        now = 200
        assertTrue(dispatcher.request(ControllerSoundCue.SELECT))
        jobs.removeFirst().invoke()
        assertEquals(2, starts)
    }

    @Test fun `stale cues and cues cancelled by rapid settings changes never play later`() {
        var now = 0L
        var starts = 0
        var volume = 40
        val jobs = ArrayDeque<() -> Unit>()
        val dispatcher = ControllerSoundDispatcher({ jobs.add(it); true }, { now }, { true }, { volume },
            ControllerSoundPlayback({ _, _ -> ++starts }, {}, { _, _ -> }, { now }))
        assertTrue(dispatcher.request(ControllerSoundCue.SELECT))
        now = 81
        jobs.removeFirst().invoke()
        assertEquals(0, starts)
        assertTrue(dispatcher.request(ControllerSoundCue.SELECT))
        repeat(10_000) { volume = it % 101; dispatcher.preferencesChanged() }
        assertEquals("Settings updates are conflated alongside the single pending cue", 2, jobs.size)
        while (jobs.isNotEmpty()) jobs.removeFirst().invoke()
        assertEquals(0, starts)
        volume = 40
        dispatcher.preferencesChanged()
        while (jobs.isNotEmpty()) jobs.removeFirst().invoke()
        assertTrue(dispatcher.request(ControllerSoundCue.SELECT))
        dispatcher.stop()
        while (jobs.isNotEmpty()) jobs.removeFirst().invoke()
        assertEquals("Leaving the launcher invalidates a cue before the driver sees it", 0, starts)
    }

    @Test fun `mute then unmute during a blocked driver call cannot revive its tail`() {
        var now = 0L
        var volume = 40
        val jobs = ArrayDeque<() -> Unit>()
        val events = mutableListOf<String>()
        lateinit var dispatcher: ControllerSoundDispatcher
        val playback = ControllerSoundPlayback({ _, gain ->
            events += "start:$gain"
            volume = 0
            dispatcher.preferencesChanged()
            volume = 100
            dispatcher.preferencesChanged()
            now += 120
            1
        }, { events += "stop:$it" }, { _, gain -> events += "volume:$gain" }, { now })
        dispatcher = ControllerSoundDispatcher({ jobs.add(it); true }, { now }, { true }, { volume }, playback)
        assertTrue(dispatcher.request(ControllerSoundCue.MOVE))
        while (jobs.isNotEmpty()) jobs.removeFirst().invoke()
        assertEquals(listOf("start:0.2", "stop:1"), events)
        assertFalse(dispatcher.request(ControllerSoundCue.SELECT))
    }
}
