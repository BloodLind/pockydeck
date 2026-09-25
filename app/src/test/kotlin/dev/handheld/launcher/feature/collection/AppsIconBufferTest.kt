package dev.handheld.launcher.feature.collection

import org.junit.Assert.*
import org.junit.Test

class AppsIconBufferTest {
    @Test fun `small catalogs warm every icon without a scroll`() {
        assertTrue(appsIconBufferWindow(0, 0).isEmpty())
        for (count in 1..40) {
            assertEquals(0 until count, appsIconBufferWindow(count, 0))
            assertEquals(0 until count, appsIconBufferWindow(count, count - 1))
        }
    }

    @Test fun `large catalog windows remain bounded and cover the viewport after jumps or reversal`() {
        for (count in listOf(41, 100, 10_000)) for (first in listOf(0, 8, 20, count / 2, count - 1, -1, count + 3)) {
            val window = appsIconBufferWindow(count, first)
            assertTrue(window.count() <= 40)
            assertTrue(window.first >= 0 && window.last < count)
            assertTrue(first.coerceIn(0, count - 1) in window)
            assertTrue(minOf(count - 1, first.coerceAtLeast(0) + 15) in window)
        }
        assertEquals("Moving within a small block must not restart warming", appsIconBufferWindow(100, 24), appsIconBufferWindow(100, 31))
    }
}
