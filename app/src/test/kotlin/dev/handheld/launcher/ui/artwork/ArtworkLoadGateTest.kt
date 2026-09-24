package dev.handheld.launcher.ui.artwork

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArtworkLoadGateTest {
    @Test fun `held input and reversal keep loading paused until the last step settles`() = runTest {
        val gate = ArtworkLoadGate(this)
        assertTrue(gate.allowed.value)
        repeat(1_000) {
            gate.onNavigation()
            assertFalse(gate.allowed.value)
            advanceTimeBy(if (it < 10) 115 else 55)
            runCurrent()
            assertFalse("No image work between accelerated steps or a direction reversal", gate.allowed.value)
        }
        advanceTimeBy(124)
        runCurrent()
        assertFalse(gate.allowed.value)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(gate.allowed.value)
    }

    @Test fun `touch fling stays paused for its full duration and settles after release`() = runTest {
        val gate = ArtworkLoadGate(this)
        gate.onScrollChanged(true)
        advanceTimeBy(10_000)
        assertFalse(gate.allowed.value)
        gate.onNavigation()
        advanceTimeBy(1_000)
        assertFalse(gate.allowed.value)
        gate.onScrollChanged(false)
        advanceTimeBy(100)
        gate.onScrollChanged(true)
        advanceTimeBy(200)
        assertFalse("A new drag cancels an earlier resume", gate.allowed.value)
        gate.onScrollChanged(false)
        advanceTimeBy(180)
        runCurrent()
        assertTrue(gate.allowed.value)
    }
}
