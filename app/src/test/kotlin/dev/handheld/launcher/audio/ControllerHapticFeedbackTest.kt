package dev.handheld.launcher.audio

import dev.handheld.launcher.core.domain.model.ConfirmBackMapping
import dev.handheld.launcher.input.ControllerAxes
import dev.handheld.launcher.input.ControllerButton
import dev.handheld.launcher.input.ControllerInputEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ControllerHapticFeedbackTest {
    private class Fixture(scope: TestScope) {
        var enabled = true
        val pulses = mutableListOf<ControllerSoundCue>()
        val feedback = ControllerHapticFeedback({ enabled }, { wait, action ->
            val job = scope.launch { delay(wait); action() }
            val cancel: () -> Unit = { job.cancel() }
            cancel
        }) { pulses += it; true }.also { it.setActive(true) }
    }

    @Test fun `one action has one pulse without an extra ending pulse`() = runTest {
        val fixture = Fixture(this)
        assertTrue(fixture.feedback.onAction(ControllerSoundCue.CONFIRM))
        advanceTimeBy(1_000); runCurrent()
        assertEquals(listOf(ControllerSoundCue.CONFIRM), fixture.pulses)
    }

    @Test fun `rapid mixed taps produce only the first and last pulse`() = runTest {
        val fixture = Fixture(this)
        fixture.feedback.onAction(ControllerSoundCue.MOVE)
        repeat(100) {
            advanceTimeBy(50); runCurrent()
            assertFalse(fixture.feedback.onAction(ControllerSoundCue.SELECT))
            assertEquals(1, fixture.pulses.size)
        }
        fixture.feedback.onAction(ControllerSoundCue.CONFIRM)
        advanceTimeBy(159); runCurrent()
        assertEquals(1, fixture.pulses.size)
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf(ControllerSoundCue.MOVE, ControllerSoundCue.CONFIRM), fixture.pulses)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(2, fixture.pulses.size)
    }

    @Test fun `holding through the initial delay and many repeats never vibrates in the middle`() = runTest {
        val fixture = Fixture(this)
        fixture.feedback.setHeld(true)
        fixture.feedback.onAction(ControllerSoundCue.FILTER)
        advanceTimeBy(650); runCurrent()
        assertEquals(1, fixture.pulses.size)
        repeat(100) {
            fixture.feedback.onAction(ControllerSoundCue.FILTER)
            advanceTimeBy(55); runCurrent()
            assertEquals(1, fixture.pulses.size)
        }
        fixture.feedback.setHeld(false)
        advanceTimeBy(160); runCurrent()
        assertEquals(2, fixture.pulses.size)
    }

    @Test fun `single held action released before repeating does not buzz twice`() = runTest {
        val fixture = Fixture(this)
        fixture.feedback.setHeld(true)
        fixture.feedback.onAction(ControllerSoundCue.MOVE)
        advanceTimeBy(100)
        fixture.feedback.setHeld(false)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(1, fixture.pulses.size)
    }

    @Test fun `disable pause and cancellation discard the pending final pulse without replay`() = runTest {
        for (cancel in listOf<(Fixture) -> Unit>(
            { it.enabled = false; it.feedback.cancel(); it.enabled = true },
            { it.feedback.setActive(false); it.feedback.setActive(true) },
            { it.feedback.cancel() },
        )) {
            val fixture = Fixture(this)
            fixture.feedback.onAction(ControllerSoundCue.MOVE)
            fixture.feedback.onAction(ControllerSoundCue.SELECT)
            cancel(fixture)
            advanceTimeBy(1_000); runCurrent()
            assertEquals(1, fixture.pulses.size)
            fixture.enabled = false
            assertFalse(fixture.feedback.onAction(ControllerSoundCue.CONFIRM))
            fixture.enabled = true
            assertTrue(fixture.feedback.onAction(ControllerSoundCue.CONFIRM))
            advanceTimeBy(1_000); runCurrent()
            assertEquals(2, fixture.pulses.size)
        }
    }

    @Test fun `digital and analog trigger reports share one haptic hold until both release`() = runTest {
        for (analogFirst in listOf(false, true)) {
            val fixture = Fixture(this)
            val engine = ControllerInputEngine(this, { ConfirmBackMapping.Default }, { false },
                monotonicTimeMillis = { testScheduler.currentTime }, onRepeatHoldChanged = fixture.feedback::setHeld) {
                fixture.feedback.onAction(it.feedbackCue()); true
            }
            fun pressure(value: Float) = engine.onAxes(ControllerAxes(rightTrigger = value,
                eventTimeMillis = testScheduler.currentTime, deviceId = 8))
            if (analogFirst) pressure(.8f) else engine.onButtonDown(ControllerButton.RightTrigger, false, testScheduler.currentTime, 8)
            advanceTimeBy(220); runCurrent()
            if (analogFirst) engine.onButtonDown(ControllerButton.RightTrigger, false, testScheduler.currentTime, 8) else pressure(.8f)
            advanceTimeBy(3_000); runCurrent()
            assertEquals(1, fixture.pulses.size)
            engine.onButtonUp(ControllerButton.RightTrigger, 8, testScheduler.currentTime)
            advanceTimeBy(300); runCurrent()
            assertEquals("Analog trigger still holds the burst", 1, fixture.pulses.size)
            pressure(0f)
            advanceTimeBy(160); runCurrent()
            assertEquals(2, fixture.pulses.size)
            engine.reset()
        }
    }

    @Test fun `direction reversal and rapid key represses stay in one burst`() = runTest {
        val fixture = Fixture(this)
        val engine = ControllerInputEngine(this, { ConfirmBackMapping.Default }, { false },
            monotonicTimeMillis = { testScheduler.currentTime }, onRepeatHoldChanged = fixture.feedback::setHeld) {
            fixture.feedback.onAction(it.feedbackCue()); true
        }
        engine.onButtonDown(ControllerButton.DpadRight, false, 0)
        advanceTimeBy(700); runCurrent()
        engine.onButtonDown(ControllerButton.DpadLeft, false, testScheduler.currentTime)
        engine.onButtonUp(ControllerButton.DpadRight)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(1, fixture.pulses.size)
        repeat(5) {
            engine.onButtonUp(ControllerButton.DpadLeft)
            advanceTimeBy(90); runCurrent()
            engine.onButtonDown(ControllerButton.DpadLeft, false, testScheduler.currentTime)
        }
        assertEquals(1, fixture.pulses.size)
        engine.onButtonUp(ControllerButton.DpadLeft)
        advanceTimeBy(160); runCurrent()
        assertEquals(2, fixture.pulses.size)
        engine.reset()
    }
}
