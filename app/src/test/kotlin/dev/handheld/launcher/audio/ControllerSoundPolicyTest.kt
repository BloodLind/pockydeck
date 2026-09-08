package dev.handheld.launcher.audio

import dev.handheld.launcher.contract.SemanticInputAction
import dev.handheld.launcher.core.domain.model.ConfirmBackMapping
import dev.handheld.launcher.core.domain.model.ControllerFaceButton
import dev.handheld.launcher.input.ControllerAxes
import dev.handheld.launcher.input.ControllerButton
import dev.handheld.launcher.input.ControllerInputEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ControllerSoundPolicyTest {
    @Test
    fun `slow digital and analog reports sound once in either order before deliberate repeats`() = runTest {
        for (button in listOf(ControllerButton.LeftTrigger, ControllerButton.RightTrigger)) {
            for (analogFirst in listOf(false, true)) {
                val sounds = mutableListOf<ControllerSoundCue>()
                val actions = mutableListOf<SemanticInputAction>()
                val feedback = ControllerSoundPolicy({ true }, { true }, { testScheduler.currentTime }, sounds::add)
                feedback.setActive(true)
                val engine = ControllerInputEngine(this, { ConfirmBackMapping.Default }, { false },
                    monotonicTimeMillis = { testScheduler.currentTime }) { action ->
                    feedback.dispatch(action) { actions += it; true }
                }
                fun pressure(value: Float) = engine.onAxes(ControllerAxes(
                    leftTrigger = if (button == ControllerButton.LeftTrigger) value else 0f,
                    rightTrigger = if (button == ControllerButton.RightTrigger) value else 0f,
                    eventTimeMillis = testScheduler.currentTime, deviceId = 8,
                ))
                if (analogFirst) pressure(.8f)
                else engine.onButtonDown(button, false, testScheduler.currentTime, 8)
                advanceTimeBy(220)
                runCurrent()
                if (analogFirst) engine.onButtonDown(button, false, testScheduler.currentTime, 8)
                else pressure(.8f)
                engine.onButtonDown(button, true, testScheduler.currentTime, 8)
                pressure(.4f) // Hysteresis keeps a held trigger, without an extra action/cue.
                assertEquals(listOf(ControllerSoundCue.FILTER), sounds)

                advanceTimeBy(430)
                runCurrent()
                assertEquals(2, sounds.size)
                advanceTimeBy(1_000)
                runCurrent()
                assertTrue(sounds.size > 5)
                assertEquals("Audio uses the semantic repeat owner, not raw reports", actions.size, sounds.size)
                engine.onButtonUp(button, 8, testScheduler.currentTime)
                pressure(0f)
                val beforeRelease = sounds.size
                advanceTimeBy(1_000)
                runCurrent()
                assertEquals(beforeRelease, sounds.size)
                engine.reset()
            }
        }
    }

    @Test
    fun `unhandled actions and native ime navigation stay silent but mapped confirm and back retain their roles`() = runTest {
        var imeVisible = true
        val sounds = mutableListOf<ControllerSoundCue>()
        val feedback = ControllerSoundPolicy({ true }, { true }, { testScheduler.currentTime }, sounds::add)
        feedback.setActive(true)
        assertFalse(feedback.dispatch(SemanticInputAction.MENU) { false })
        val engine = ControllerInputEngine(this,
            { ConfirmBackMapping(ControllerFaceButton.B, ControllerFaceButton.A) }, { imeVisible }) {
            feedback.dispatch(it) { true }
        }
        assertFalse(engine.onButtonDown(ControllerButton.DpadRight, false, 0))
        assertFalse(engine.onAxes(ControllerAxes(stickX = 1f, eventTimeMillis = 1)))
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(sounds.isEmpty())
        imeVisible = false
        engine.onButtonDown(ControllerButton.B, false, 1_000)
        engine.onButtonDown(ControllerButton.B, true, 1_001)
        engine.onButtonUp(ControllerButton.B, eventTimeMillis = 1_002)
        advanceTimeBy(200) // A separate press after the confirmation cue has finished.
        engine.onButtonDown(ControllerButton.A, false, 1_200)
        engine.onButtonUp(ControllerButton.A, eventTimeMillis = 1_201)
        assertEquals(listOf(ControllerSoundCue.CONFIRM, ControllerSoundCue.BACK), sounds)
        engine.reset()
    }

    @Test
    fun `pause and reset cancel a held sound sequence without replaying it after resume`() = runTest {
        val sounds = mutableListOf<ControllerSoundCue>()
        val feedback = ControllerSoundPolicy({ true }, { true }, { testScheduler.currentTime }, sounds::add)
        feedback.setActive(true)
        val engine = ControllerInputEngine(this, { ConfirmBackMapping.Default }, { false },
            monotonicTimeMillis = { testScheduler.currentTime }) { feedback.dispatch(it) { true } }
        engine.onButtonDown(ControllerButton.DpadRight, false, 0)
        advanceTimeBy(360)
        runCurrent()
        assertEquals(2, sounds.size)
        feedback.setActive(false)
        engine.reset() // Same ordering as Activity.onPause/window-focus loss.
        advanceTimeBy(2_000)
        runCurrent()
        // Even a late handled callback cannot play after pause.
        assertTrue(feedback.dispatch(SemanticInputAction.NAVIGATE_RIGHT) { true })
        assertEquals(2, sounds.size)
        feedback.setActive(true)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, sounds.size)
        engine.onButtonUp(ControllerButton.DpadRight, eventTimeMillis = testScheduler.currentTime)
        engine.onButtonDown(ControllerButton.DpadLeft, false, testScheduler.currentTime)
        assertEquals(3, sounds.size)
        engine.reset()
    }

    @Test
    fun `preference and media mute take effect immediately without altering handled results or replaying sounds`() {
        var enabled = true
        var mediaAudible = true
        val sounds = mutableListOf<ControllerSoundCue>()
        val feedback = ControllerSoundPolicy({ enabled }, { mediaAudible }, play = sounds::add)
        feedback.setActive(true)
        // Turning off via Confirm suppresses that action too, after its handler has run.
        assertTrue(feedback.dispatch(SemanticInputAction.CONFIRM) { enabled = false; true })
        assertTrue(feedback.dispatch(SemanticInputAction.NAVIGATE_DOWN) { true })
        enabled = true
        mediaAudible = false
        assertTrue(feedback.dispatch(SemanticInputAction.NEXT_DESTINATION) { true })
        assertTrue(sounds.isEmpty())
        mediaAudible = true
        assertTrue(sounds.isEmpty())
        assertTrue(feedback.dispatch(SemanticInputAction.NEXT_DESTINATION) { true })
        assertEquals(listOf(ControllerSoundCue.PAGE), sounds)
    }

    @Test
    fun `audio failure cannot break input dispatch and leaving foreground during handling suppresses feedback`() {
        val failing = ControllerSoundPolicy({ true }, { true }) { throw IllegalStateException("audio unavailable") }
        failing.setActive(true)
        assertTrue(failing.dispatch(SemanticInputAction.CONFIRM) { true })
        assertFalse(failing.dispatch(SemanticInputAction.CONFIRM) { false })
        val sounds = mutableListOf<ControllerSoundCue>()
        val feedback = ControllerSoundPolicy({ true }, { true }, play = sounds::add)
        feedback.setActive(true)
        assertTrue(feedback.dispatch(SemanticInputAction.CONFIRM) { feedback.setActive(false); true })
        assertTrue(sounds.isEmpty())
    }

    @Test
    fun `busy playback drops movement filter selection and confirm calls without queueing or truncating`() {
        var now = 0L
        val played = mutableListOf<ControllerSoundCue>()
        val feedback = ControllerSoundPolicy({ true }, { true }, { now }, played::add)
        feedback.setActive(true)
        assertTrue(feedback.dispatch(SemanticInputAction.NAVIGATE_RIGHT) { true })
        now = 20
        assertTrue(feedback.dispatch(SemanticInputAction.NEXT_FILTER) { true })
        assertFalse(feedback.onItemSelected())
        assertTrue(feedback.dispatch(SemanticInputAction.CONFIRM) { true })
        now = 77 // Move62ms plus16ms rendering margin is still occupied.
        assertTrue(feedback.dispatch(SemanticInputAction.NAVIGATE_LEFT) { true })
        assertEquals(listOf(ControllerSoundCue.MOVE), played)
        now = 78
        assertTrue(feedback.onItemSelected())
        now = 1_000
        assertEquals("Skipped events never become deferred playback", listOf(ControllerSoundCue.MOVE, ControllerSoundCue.SELECT), played)
        assertTrue(feedback.dispatch(SemanticInputAction.CONFIRM) { true })
        assertEquals(ControllerSoundCue.CONFIRM, played.last())
    }

    @Test
    fun `actual delayed card selection replaces generic movement and Home trigger feedback`() {
        var now = 0L
        val played = mutableListOf<ControllerSoundCue>()
        val feedback = ControllerSoundPolicy({ true }, { true }, { now }, played::add)
        feedback.setActive(true)
        for (action in listOf(SemanticInputAction.NAVIGATE_RIGHT, SemanticInputAction.NEXT_FILTER)) {
            val before = played.size
            assertTrue(feedback.dispatch(action) { feedback.expectItemSelection(); true })
            assertEquals("Queued card focus does not emit the generic cue first", before, played.size)
            now += 16
            assertTrue(feedback.onItemSelected())
            assertEquals(ControllerSoundCue.SELECT, played.last())
            now += 200
        }
        // A header move with no expectation still gets the ordinary movement cue.
        feedback.expectItemSelection() // Out-of-dispatch hints cannot leak to the next action.
        feedback.dispatch(SemanticInputAction.NAVIGATE_LEFT) { true }
        assertEquals(ControllerSoundCue.MOVE, played.last())
        now += 200
        feedback.dispatch(SemanticInputAction.CONFIRM) { feedback.expectItemSelection(); true }
        assertEquals("Confirm retains its own sound", ControllerSoundCue.CONFIRM, played.last())
    }

    @Test
    fun `synchronous selection emits one selection cue with no following navigation duplicate`() {
        val played = mutableListOf<ControllerSoundCue>()
        val feedback = ControllerSoundPolicy({ true }, { true }, { 0L }, played::add)
        feedback.setActive(true)
        feedback.dispatch(SemanticInputAction.NAVIGATE_DOWN) { feedback.onItemSelected(); true }
        assertEquals(listOf(ControllerSoundCue.SELECT), played)
    }

    @Test
    fun `unloaded or failed playback does not occupy a voice and stop resets the busy guard`() {
        var now = 0L
        var ready = false
        var playCalls = 0
        val played = mutableListOf<ControllerSoundCue>()
        val feedback = ControllerSoundPolicy({ true }, { true }, { now }) {
            playCalls++
            if (ready) { played += it; true } else false
        }
        feedback.setActive(true)
        assertFalse(feedback.onItemSelected())
        ready = true
        assertTrue(feedback.onItemSelected())
        assertEquals(2, playCalls)
        assertFalse(feedback.onItemSelected())
        assertEquals("The busy guard rejects before SoundPool.play", 2, playCalls)
        now = 1
        feedback.stop()
        assertTrue(feedback.onItemSelected())
        feedback.setActive(false)
        assertFalse(feedback.onItemSelected())
        feedback.setActive(true)
        assertTrue(feedback.onItemSelected())
        assertEquals(3, played.size)
    }

    @Test
    fun `maximum accelerated trigger cadence keeps actions responsive while busy sound calls are skipped`() = runTest {
        var handled = 0
        val startedAt = mutableListOf<Long>()
        val feedback = ControllerSoundPolicy({ true }, { true }, { testScheduler.currentTime }) {
            startedAt += testScheduler.currentTime
            true
        }
        feedback.setActive(true)
        val engine = ControllerInputEngine(this, { ConfirmBackMapping.Default }, { false },
            monotonicTimeMillis = { testScheduler.currentTime }) { action ->
            feedback.dispatch(action) { handled++; true }
        }
        engine.onButtonDown(ControllerButton.RightTrigger, false, 0)
        advanceTimeBy(4_000)
        runCurrent()
        assertTrue(handled > startedAt.size)
        assertTrue(startedAt.size > 10)
        assertTrue(startedAt.zipWithNext().all { (previous, next) -> next - previous >= 88 })
        engine.reset()
        val stoppedCount = startedAt.size
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals("There is no audio backlog after the trigger is released", stoppedCount, startedAt.size)
    }
}
