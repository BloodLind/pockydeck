package dev.handheld.launcher.input

import dev.handheld.launcher.contract.SemanticInputAction
import dev.handheld.launcher.core.domain.model.ConfirmBackMapping
import dev.handheld.launcher.core.domain.model.ControllerFaceButton
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ControllerInputEngineTest {
    @Test
    fun `search editor owns mapped face buttons during ime while navigation stays native`() = runTest {
        var editing = true
        var mapping = ConfirmBackMapping.Default
        val actions = mutableListOf<SemanticInputAction>()
        val engine = ControllerInputEngine(this, { mapping }, { true }, imeFaceActionsEnabled = { editing }) {
            actions += it
            true
        }
        assertTrue(engine.onButtonDown(ControllerButton.A, false, 1))
        assertTrue(engine.onButtonDown(ControllerButton.A, true, 2))
        editing = false
        assertTrue(engine.onButtonUp(ControllerButton.A))
        assertFalse(engine.onButtonDown(ControllerButton.B, false, 3))
        assertFalse(engine.onButtonUp(ControllerButton.B))
        editing = true
        mapping = ConfirmBackMapping(ControllerFaceButton.B, ControllerFaceButton.A)
        assertTrue(engine.onButtonDown(ControllerButton.A, false, 4))
        assertTrue(engine.onButtonUp(ControllerButton.A))
        assertTrue(engine.onButtonDown(ControllerButton.B, false, 5))
        assertTrue(engine.onButtonUp(ControllerButton.B))
        assertFalse(engine.onButtonDown(ControllerButton.DpadDown, false, 6))
        assertFalse(engine.onButtonUp(ControllerButton.DpadDown))
        assertFalse(engine.onButtonDown(ControllerButton.X, false, 7))
        assertFalse(engine.onButtonUp(ControllerButton.X))
        assertFalse(engine.onAxes(ControllerAxes(stickY = 1f, eventTimeMillis = 8)))
        advanceTimeBy(500)
        runCurrent()
        assertEquals(listOf(SemanticInputAction.CONFIRM, SemanticInputAction.BACK, SemanticInputAction.CONFIRM), actions)
    }

    @Test
    fun `face buttons follow the complete confirm back mapping`() = runTest {
        var mapping = ConfirmBackMapping.Default
        val actions = mutableListOf<SemanticInputAction>()
        val engine = ControllerInputEngine(this, { mapping }, { false }) {
            actions += it
            true
        }

        engine.onButtonDown(ControllerButton.A, false, 1)
        engine.onButtonUp(ControllerButton.A)
        engine.onButtonDown(ControllerButton.B, false, 100)
        engine.onButtonUp(ControllerButton.B)
        mapping = ConfirmBackMapping(ControllerFaceButton.B, ControllerFaceButton.A)
        engine.onButtonDown(ControllerButton.A, false, 200)
        engine.onButtonUp(ControllerButton.A)
        engine.onButtonDown(ControllerButton.B, false, 300)

        assertEquals(
            listOf(
                SemanticInputAction.CONFIRM,
                SemanticInputAction.BACK,
                SemanticInputAction.BACK,
                SemanticInputAction.CONFIRM,
            ),
            actions,
        )
    }

    @Test
    fun `matching dpad key and hat share one edge and one repeat owner`() = runTest {
        val actions = mutableListOf<SemanticInputAction>()
        val engine = ControllerInputEngine(this, { ConfirmBackMapping.Default }, { false }) {
            actions += it
            true
        }

        assertTrue(engine.onButtonDown(ControllerButton.DpadRight, false, 1))
        engine.onAxes(ControllerAxes(hatX = 1f, eventTimeMillis = 2))
        assertEquals(listOf(SemanticInputAction.NAVIGATE_RIGHT), actions)

        advanceTimeBy(360)
        runCurrent()
        assertEquals(2, actions.size)

        engine.onButtonUp(ControllerButton.DpadRight)
        engine.onAxes(ControllerAxes(eventTimeMillis = 400))
        advanceTimeBy(500)
        runCurrent()
        assertEquals(2, actions.size)
    }

    @Test
    fun `digital and analog trigger edges are deduplicated`() = runTest {
        val actions = mutableListOf<SemanticInputAction>()
        val engine = ControllerInputEngine(this, { ConfirmBackMapping.Default }, { false }) {
            actions += it
            true
        }

        engine.onButtonDown(ControllerButton.LeftTrigger, false, 100)
        engine.onAxes(ControllerAxes(leftTrigger = 1f, eventTimeMillis = 120))
        assertEquals(listOf(SemanticInputAction.PREVIOUS_FILTER), actions)

        engine.onAxes(ControllerAxes(leftTrigger = 0f, eventTimeMillis = 180))
        engine.onButtonUp(ControllerButton.LeftTrigger)
        engine.onAxes(ControllerAxes(leftTrigger = 1f, eventTimeMillis = 200))
        assertEquals(2, actions.size)
        engine.reset()
    }

    @Test
    fun `ime and pause clear held state and stop repeats`() = runTest {
        var imeVisible = false
        val actions = mutableListOf<SemanticInputAction>()
        val engine = ControllerInputEngine(this, { ConfirmBackMapping.Default }, { imeVisible }) {
            actions += it
            true
        }

        engine.onButtonDown(ControllerButton.DpadDown, false, 1)
        imeVisible = true
        advanceTimeBy(500)
        runCurrent()
        assertEquals(listOf(SemanticInputAction.NAVIGATE_DOWN), actions)

        assertFalse(engine.onAxes(ControllerAxes(hatY = 1f, eventTimeMillis = 600)))
        // The original DOWN belonged to the launcher, so its matching UP remains consumed
        // even though IME activation cleared navigation/repeat state.
        assertTrue(engine.onButtonUp(ControllerButton.DpadDown))
        imeVisible = false
        engine.onButtonDown(ControllerButton.DpadDown, false, 700)
        engine.reset()
        advanceTimeBy(500)
        runCurrent()
        assertEquals(2, actions.size)
    }

    @Test
    fun `key up follows the owner that received its key down across ime changes`() = runTest {
        var imeVisible = true
        val engine = ControllerInputEngine(this, { ConfirmBackMapping.Default }, { imeVisible }) { true }

        assertFalse(engine.onButtonDown(ControllerButton.A, false, 1))
        imeVisible = false
        assertFalse(engine.onButtonUp(ControllerButton.A))

        assertTrue(engine.onButtonDown(ControllerButton.B, false, 100))
        imeVisible = true
        engine.onImeShown()
        assertTrue(engine.onButtonUp(ControllerButton.B))
        assertFalse(engine.onButtonUp(ControllerButton.DpadUp))
    }

    @Test
    fun `slow key and pressure reports merge in either order without restarting trigger delay`() = runTest {
        for (button in listOf(ControllerButton.LeftTrigger, ControllerButton.RightTrigger)) {
            for (analogFirst in listOf(false, true)) {
                val events = mutableListOf<Pair<SemanticInputAction, Long>>()
                val engine = ControllerInputEngine(this, { ConfirmBackMapping.Default }, { false },
                    monotonicTimeMillis = { testScheduler.currentTime }) {
                    events += it to testScheduler.currentTime
                    true
                }
                val started = testScheduler.currentTime
                val action = if (button == ControllerButton.LeftTrigger) SemanticInputAction.PREVIOUS_FILTER else SemanticInputAction.NEXT_FILTER
                fun pressure(value: Float) = engine.onAxes(ControllerAxes(
                    leftTrigger = if (button == ControllerButton.LeftTrigger) value else 0f,
                    rightTrigger = if (button == ControllerButton.RightTrigger) value else 0f,
                    eventTimeMillis = testScheduler.currentTime, deviceId = 8,
                ))
                if (analogFirst) pressure(.8f) else engine.onButtonDown(button, false, started, 8)
                advanceTimeBy(180)
                runCurrent()
                if (analogFirst) engine.onButtonDown(button, false, testScheduler.currentTime, 8) else pressure(.8f)
                engine.onButtonDown(button, true, testScheduler.currentTime + 1, 8)
                assertEquals(listOf(action to started), events)
                advanceTimeBy(469)
                runCurrent()
                assertEquals("Only one step before the deliberate hold threshold", listOf(action to started), events)
                advanceTimeBy(1)
                runCurrent()
                assertEquals(listOf(action to started, action to started + 650), events)

                // Releasing one duplicate report does not re-arm the still-held logical input.
                if (analogFirst) engine.onButtonUp(button, 8) else pressure(0f)
                assertEquals(2, events.size)
                if (analogFirst) pressure(0f) else engine.onButtonUp(button, 8)
                advanceTimeBy(500)
                runCurrent()
                assertEquals(2, events.size)
                engine.reset()
            }
        }
    }

    @Test
    fun `trigger taps released before 650ms never repeat including duplicate reports`() = runTest {
        for (button in listOf(ControllerButton.LeftTrigger, ControllerButton.RightTrigger)) {
            val actions = mutableListOf<SemanticInputAction>()
            val engine = ControllerInputEngine(this, { ConfirmBackMapping.Default }, { false },
                monotonicTimeMillis = { testScheduler.currentTime }) { actions += it; true }
            val started = testScheduler.currentTime
            engine.onButtonDown(button, false, started)
            advanceTimeBy(400)
            runCurrent()
            engine.onAxes(ControllerAxes(
                leftTrigger = if (button == ControllerButton.LeftTrigger) .9f else 0f,
                rightTrigger = if (button == ControllerButton.RightTrigger) .9f else 0f,
                eventTimeMillis = testScheduler.currentTime,
            ))
            engine.onButtonDown(button, true, testScheduler.currentTime)
            advanceTimeBy(249)
            runCurrent()
            assertEquals("A sub-threshold squeeze must still be one step", 1, actions.size)
            engine.onButtonUp(button, eventTimeMillis = testScheduler.currentTime)
            engine.onAxes(ControllerAxes(eventTimeMillis = testScheduler.currentTime))
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals("Release immediately before the threshold cancels the pending repeat", 1, actions.size)
            engine.reset()
        }
    }

    @Test
    fun `a late analog report joins an already repeating trigger without another edge or delay`() = runTest {
        val actions = mutableListOf<SemanticInputAction>()
        val engine = ControllerInputEngine(this, { ConfirmBackMapping.Default }, { false },
            monotonicTimeMillis = { testScheduler.currentTime }) { actions += it; true }
        engine.onButtonDown(ControllerButton.LeftTrigger, false, 0)
        advanceTimeBy(1_000)
        runCurrent()
        val beforeAnalog = actions.size
        assertTrue(beforeAnalog >= 3)
        engine.onAxes(ControllerAxes(leftTrigger = .7f, eventTimeMillis = testScheduler.currentTime))
        assertEquals(beforeAnalog, actions.size)
        advanceTimeBy(115)
        runCurrent()
        assertTrue("The existing repeat cadence continues", actions.size > beforeAnalog)
        engine.onButtonUp(ControllerButton.LeftTrigger)
        engine.onAxes(ControllerAxes(eventTimeMillis = testScheduler.currentTime))
        val releasedCount = actions.size
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(releasedCount, actions.size)
        engine.reset()
    }

    @Test
    fun `trigger pressure hysteresis ignores noise and genuine rapid presses are not time deduplicated`() = runTest {
        val actions = mutableListOf<SemanticInputAction>()
        val engine = ControllerInputEngine(this, { ConfirmBackMapping.Default }, { false },
            monotonicTimeMillis = { testScheduler.currentTime }) { actions += it; true }
        engine.onAxes(ControllerAxes(leftTrigger = .4f, eventTimeMillis = 0))
        assertTrue(actions.isEmpty())
        engine.onAxes(ControllerAxes(leftTrigger = .56f, eventTimeMillis = 10))
        listOf(.54f, .4f, .3f, .26f, .52f).forEachIndexed { index, pressure ->
            engine.onAxes(ControllerAxes(leftTrigger = pressure, eventTimeMillis = 11L + index))
        }
        assertEquals(listOf(SemanticInputAction.PREVIOUS_FILTER), actions)
        engine.onAxes(ControllerAxes(leftTrigger = .25f, eventTimeMillis = 20))
        engine.onAxes(ControllerAxes(leftTrigger = .56f, eventTimeMillis = 30))
        assertEquals(2, actions.size)
        engine.onAxes(ControllerAxes(leftTrigger = Float.NaN, eventTimeMillis = 40))
        engine.onAxes(ControllerAxes(leftTrigger = 1f, eventTimeMillis = 35)) // stale pressure after release
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, actions.size)
        engine.reset()
    }

    @Test
    fun `directional and trigger holds accelerate with elapsed time within a fixed maximum rate`() = runTest {
        val buttons = listOf(ControllerButton.DpadUp, ControllerButton.DpadDown, ControllerButton.DpadLeft,
            ControllerButton.DpadRight, ControllerButton.LeftTrigger, ControllerButton.RightTrigger)
        for ((index, button) in (buttons + listOf(null, null)).withIndex()) {
            val times = mutableListOf<Long>()
            val engine = ControllerInputEngine(this, { ConfirmBackMapping.Default }, { false },
                monotonicTimeMillis = { testScheduler.currentTime }) { times += testScheduler.currentTime; true }
            val started = testScheduler.currentTime
            if (button != null) engine.onButtonDown(button, false, started)
            else if (index == buttons.size) engine.onAxes(ControllerAxes(stickY = -1f, eventTimeMillis = started))
            else engine.onAxes(ControllerAxes(hatY = 1f, eventTimeMillis = started))
            advanceTimeBy(3_500)
            runCurrent()
            assertEquals(started, times.first())
            val holdDelay = if (button == ControllerButton.LeftTrigger || button == ControllerButton.RightTrigger) 650L else 360L
            assertEquals(started + holdDelay, times[1])
            val repeatIntervals = times.drop(1).zipWithNext { previous, next -> next - previous }
            assertEquals("Acceleration starts after the hold threshold", 115L, repeatIntervals.first())
            assertTrue(repeatIntervals.all { it in 55L..115L })
            assertTrue(repeatIntervals.zipWithNext().all { (previous, next) -> next <= previous })
            assertTrue(repeatIntervals.any { it < 90 })
            assertEquals(55L, repeatIntervals.last())
            engine.reset()
            val releasedCount = times.size
            advanceTimeBy(600)
            runCurrent()
            assertEquals(releasedCount, times.size)
        }
    }

    @Test
    fun `matching stick hat and keys retain a single directional owner across source handoffs`() = runTest {
        val actions = mutableListOf<SemanticInputAction>()
        val engine = ControllerInputEngine(this, { ConfirmBackMapping.Default }, { false },
            monotonicTimeMillis = { testScheduler.currentTime }) { actions += it; true }
        engine.onAxes(ControllerAxes(stickX = 1f, eventTimeMillis = 0, deviceId = 12))
        advanceTimeBy(180)
        engine.onButtonDown(ControllerButton.DpadRight, false, 180, 12)
        engine.onAxes(ControllerAxes(hatX = 1f, stickX = 1f, eventTimeMillis = 180, deviceId = 12))
        assertEquals(listOf(SemanticInputAction.NAVIGATE_RIGHT), actions)
        advanceTimeBy(180)
        runCurrent()
        assertEquals(2, actions.size)
        engine.onButtonUp(ControllerButton.DpadRight, 12)
        engine.onAxes(ControllerAxes(stickX = .4f, eventTimeMillis = 370, deviceId = 12))
        assertEquals(2, actions.size)
        engine.onAxes(ControllerAxes(eventTimeMillis = 380, deviceId = 12))
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, actions.size)
        engine.reset()
    }

    @Test
    fun `release from one controller cannot release another controllers held key`() = runTest {
        val actions = mutableListOf<SemanticInputAction>()
        val engine = ControllerInputEngine(this, { ConfirmBackMapping.Default }, { false },
            monotonicTimeMillis = { testScheduler.currentTime }) { actions += it; true }
        engine.onButtonDown(ControllerButton.DpadLeft, false, 0, 1)
        engine.onButtonDown(ControllerButton.DpadLeft, false, 10, 2)
        engine.onButtonUp(ControllerButton.DpadLeft, 1, 20)
        assertEquals(1, actions.size)
        advanceTimeBy(360)
        runCurrent()
        assertEquals(2, actions.size)
        engine.onButtonUp(ControllerButton.DpadLeft, 2, 400)
        engine.onButtonDown(ControllerButton.DpadLeft, false, 300, 2) // delayed old DOWN
        advanceTimeBy(500)
        runCurrent()
        assertEquals(2, actions.size)
        engine.reset()
    }

    @Test
    fun `reset and ime require previously held axes to return neutral before rearming`() = runTest {
        var ime = false
        val actions = mutableListOf<SemanticInputAction>()
        val engine = ControllerInputEngine(this, { ConfirmBackMapping.Default }, { ime },
            monotonicTimeMillis = { testScheduler.currentTime }) { actions += it; true }
        fun held(time: Long) = engine.onAxes(ControllerAxes(stickX = 1f, rightTrigger = 1f, eventTimeMillis = time))
        held(0)
        assertEquals(listOf(SemanticInputAction.NAVIGATE_RIGHT, SemanticInputAction.NEXT_FILTER), actions)
        engine.reset()
        held(10)
        engine.onButtonDown(ControllerButton.RightTrigger, true, 11)
        engine.onButtonUp(ControllerButton.RightTrigger, eventTimeMillis = 12)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, actions.size)

        engine.onAxes(ControllerAxes(eventTimeMillis = 20))
        held(30)
        assertEquals(4, actions.size)
        ime = true
        engine.onImeShown()
        assertFalse(held(40))
        ime = false
        held(50)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(4, actions.size)
        engine.onAxes(ControllerAxes(eventTimeMillis = 60))
        held(70)
        assertEquals(6, actions.size)
        engine.reset()
    }

    @Test
    fun `a reset inside dispatch prevents stale repeat and opposite trigger dispatch`() = runTest {
        val actions = mutableListOf<SemanticInputAction>()
        lateinit var engine: ControllerInputEngine
        engine = ControllerInputEngine(this, { ConfirmBackMapping.Default }, { false },
            monotonicTimeMillis = { testScheduler.currentTime }) {
            actions += it
            engine.reset()
            true
        }
        engine.onButtonDown(ControllerButton.DpadRight, false, 0)
        engine.onButtonUp(ControllerButton.DpadRight)
        engine.onAxes(ControllerAxes(leftTrigger = 1f, rightTrigger = 1f, eventTimeMillis = 10))
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(listOf(SemanticInputAction.NAVIGATE_RIGHT, SemanticInputAction.PREVIOUS_FILTER), actions)
    }
}
