package dev.handheld.launcher.feature.collection

import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionFilterWindowTest {
    @Test fun `wide windows still show exactly three complete categories`() {
        assertEquals(CollectionFilterWindow(0, 3, 202), collectionFilterWindow(900, listOf(60, 80, 50, 90), 0, 6))
    }

    @Test fun `last category keeps two predecessors instead of leaving empty slots`() {
        assertEquals(CollectionFilterWindow(2, 3, 242), collectionFilterWindow(300, listOf(60, 80, 50, 90, 90), 4, 6))
    }

    @Test fun `fewer available categories never produce blank or repeated choices`() {
        assertEquals(CollectionFilterWindow(0, 2, 106), collectionFilterWindow(300, listOf(50, 50), 1, 6))
        assertEquals(CollectionFilterWindow(0, 0, 0), collectionFilterWindow(300, emptyList(), 9, 6))
    }

    @Test fun `constrained windows degrade to complete targets without overflowing`() {
        assertEquals(CollectionFilterWindow(0, 2, 106), collectionFilterWindow(120, listOf(50, 50, 50), 0, 6))
        assertEquals(CollectionFilterWindow(2, 0, 0), collectionFilterWindow(48, listOf(50, 50, 50), 2, 6))
    }

    @Test fun `a wider selected category stays inside a reduced window`() {
        assertEquals(CollectionFilterWindow(4, 1, 130),
            collectionFilterWindow(200, listOf(60, 60, 60, 130, 130), 2, 2, selectedIndex = 4))
    }

    @Test fun `prefer three cells containing the selection when an adjacent window fits`() {
        assertEquals(CollectionFilterWindow(0, 3, 124),
            collectionFilterWindow(130, listOf(40, 40, 40, 100), 1, 2, selectedIndex = 2))
    }
}
