package dev.handheld.launcher.input

import androidx.compose.ui.focus.FocusDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
