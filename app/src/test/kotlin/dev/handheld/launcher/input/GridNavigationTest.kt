package dev.handheld.launcher.input

import androidx.compose.ui.focus.FocusDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GridNavigationTest {
    @Test fun `horizontal movement crosses a row in reading order`() {
        assertEquals(5, gridMoveTarget(4, 13, 5, FocusDirection.Right))
        assertEquals(4, gridMoveTarget(5, 13, 5, FocusDirection.Left))
    }
    @Test fun `horizontal collection boundaries stop rather than focus controls or wrap`() {
        assertEquals(0, gridMoveTarget(0, 13, 5, FocusDirection.Left))
        assertEquals(12, gridMoveTarget(12, 13, 5, FocusDirection.Right))
    }
    @Test fun `vertical movement reaches a short last row and preserves normal columns`() {
        assertEquals(12, gridMoveTarget(9, 13, 5, FocusDirection.Down))
        assertEquals(7, gridMoveTarget(12, 13, 5, FocusDirection.Up))
        assertEquals(8, gridMoveTarget(3, 13, 5, FocusDirection.Down))
    }
    @Test fun `vertical boundaries allow access to header and dock`() {
        assertNull(gridMoveTarget(3, 13, 5, FocusDirection.Up))
        assertNull(gridMoveTarget(12, 13, 5, FocusDirection.Down))
        assertNull(gridMoveTarget(0, 0, 5, FocusDirection.Right))
    }

    @Test fun `discrete movement animates but the full repeat cadence does not queue decorative scrolling`() {
        assertTrue(animateGridMove(null, 0L))
        assertTrue(animateGridMove(0L, 360_000_000L))
        assertFalse(animateGridMove(360_000_000L, 475_000_000L))
        assertFalse(animateGridMove(475_000_000L, 530_000_000L))
        assertTrue(animateGridMove(530_000_000L, 1_030_000_000L))
    }

    @Test fun `accelerated pending destination still publishes focus on the completed placed row`() {
        assertEquals("40", gridFocusTarget("42", "40", setOf("38", "39", "40", "41"),
            setOf("38", "39", "40", "41"), directionUnchanged = true))
        assertEquals("42", gridFocusTarget("42", "40", setOf("40", "41", "42", "43"),
            setOf("40", "41", "42", "43"), directionUnchanged = true))
    }

    @Test fun `pending partially placed row waits for explicit scrolling instead of native bring into view`() {
        assertEquals("40", gridFocusTarget("42", "40", setOf("38", "39", "40", "41", "42"),
            setOf("38", "39", "40", "41"), directionUnchanged = true))
    }

    @Test fun `direction reversal prefers its visible destination and never refocuses the old forward target`() {
        assertEquals("36", gridFocusTarget("36", "40", setOf("36", "37", "38", "39", "40"),
            setOf("36", "37", "38", "39"), directionUnchanged = false))
        assertNull(gridFocusTarget("36", "40", setOf("38", "39", "40", "41"),
            setOf("38", "39", "40", "41"), directionUnchanged = false))
    }

    @Test fun `removed or unplaced destinations cannot receive focus`() {
        assertNull(gridFocusTarget("42", "40", setOf("20", "21"),
            setOf("20", "21"), directionUnchanged = true))
    }
}
