package dev.handheld.launcher.feature.collection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionGridGeometryTest {
    @Test fun `density changes column count while cards exactly fill the row`() {
        for (width in listOf(320f, 640f, 800f, 1280f)) {
            var previous = 11
            for (percent in 70..140 step 10) {
                val target = 152f * percent / 100
                val columns = collectionGridColumns(width, target, 8f)
                assertTrue(columns <= previous)
                previous = columns
                val card = (width - 8 * (columns - 1)) / columns
                assertTrue(card > 0)
                assertEquals(width, columns * card + 8 * (columns - 1), .01f)
            }
        }
    }

    @Test fun `narrow windows still have one usable column`() {
        assertEquals(1, collectionGridColumns(100f, 152f, 8f))
        assertEquals(5, collectionGridColumns(800f, 152f, 8f))
    }
}
