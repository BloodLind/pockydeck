package dev.handheld.launcher.ui.artwork

import org.junit.Assert.*
import org.junit.Test

class BackdropBlurTest {
    @Test fun `blur preserves a flat color and never mutates source artwork`() {
        val source = IntArray(9 * 5) { 0xFF336699.toInt() }
        val before = source.copyOf()
        assertArrayEquals(before, blurBackdropPixels(source, 9, 5))
        assertArrayEquals(before, source)
        assertArrayEquals(intArrayOf(0xFF336699.toInt()), blurBackdropPixels(intArrayOf(0xFF336699.toInt()), 1, 1))
    }

    @Test fun `busy cover details become a smooth opaque color wash`() {
        val stripes = IntArray(64 * 64) { if ((it % 64) / 4 % 2 == 0) 0xFFFF0000.toInt() else 0xFF0000FF.toInt() }
        val blurred = blurBackdropPixels(stripes, 64, 64)
        for (y in 16..47) for (x in 16..47) {
            val pixel = blurred[y * 64 + x]
            assertEquals(255, pixel ushr 24)
            assertTrue((pixel ushr 16 and 255) in 100..155)
            assertTrue((pixel and 255) in 100..155)
        }
        assertEquals(0xFFFF0000.toInt(), stripes[0])
    }
}
