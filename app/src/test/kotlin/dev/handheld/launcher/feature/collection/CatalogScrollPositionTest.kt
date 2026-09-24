package dev.handheld.launcher.feature.collection

import org.junit.Assert.*
import org.junit.Test

class CatalogScrollPositionTest {
    @Test fun `list and grid use row position with smooth partial-row progress`() {
        for (columns in listOf(1, 3, 5)) {
            val top = catalogScrollPosition(100 * columns, columns, 0, 8f, 90f, 10f, 390f, false, true)
            val middle = catalogScrollPosition(100 * columns, columns, 50 * columns, 0f, 90f, 10f, 390f, true, true)
            val partial = catalogScrollPosition(100 * columns, columns, 50 * columns, -40f, 90f, 10f, 390f, true, true)
            assertEquals(0f, top.progress, 0f)
            assertEquals(390f / 9990f, top.visibleFraction, .00001f)
            assertEquals(5000f / 9600f, middle.progress, .00001f)
            assertTrue(partial.progress > middle.progress)
        }
    }

    @Test fun `native end is exact for partial grid rows padding and short final actions`() {
        val end = catalogScrollPosition(1003, 5, 999, -20f, 48f, 8f, 350f, true, false)
        assertEquals(1f, end.progress, 0f)
        assertTrue(end.visibleFraction in 0f..1f)
    }

    @Test fun `empty short and not-yet-laid-out catalogs do not divide by zero`() {
        for (count in listOf(0, 1, 3)) {
            assertEquals(CatalogScrollPosition(0f, 1f),
                catalogScrollPosition(count, 4, 0, 0f, 80f, 8f, 350f, false, false))
        }
        assertEquals(CatalogScrollPosition(0f, 1f),
            catalogScrollPosition(100, 4, 0, 0f, 0f, 8f, 0f, true, true))
    }
}
