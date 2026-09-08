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
}
