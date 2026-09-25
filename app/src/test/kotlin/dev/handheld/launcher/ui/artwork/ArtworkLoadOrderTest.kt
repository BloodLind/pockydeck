package dev.handheld.launcher.ui.artwork

import dev.handheld.launcher.core.domain.model.ItemId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshots.Snapshot
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArtworkLoadOrderTest {
    private val ids = (0..4).map { ItemId("image:$it") }

    @Test fun `waiting workers advance in display order without a UI recomposition and close releases them`() = runTest {
        val order = ArtworkLoadOrder(ids, this)
        val admitted = mutableListOf<ItemId>()
        ids.reversed().forEach { id -> launch { if (order.awaitTurn(id)) admitted += id } }
        runCurrent()
        assertEquals(listOf(ids[0]), admitted)
        order.complete(ids[0], freshImage = true)
        advanceTimeBy(16); runCurrent()
        Snapshot.sendApplyNotifications(); runCurrent()
        assertEquals(listOf(ids[0], ids[1]), admitted)
        order.complete(ids[1])
        Snapshot.sendApplyNotifications(); runCurrent()
        assertEquals(ids.take(3), admitted)
        order.close()
        Snapshot.sendApplyNotifications(); runCurrent()
        assertEquals("Closed viewport workers must not start decoding", ids.take(3), admitted)
    }

    @Test fun `fresh images admit the next display position after one frame and cached gaps skip immediately`() = runTest {
        val order = ArtworkLoadOrder(ids, this)
        order.complete(ids[2]) // Already cached, even though an earlier card still needs disk.
        assertTrue(order.allowed(ids[0]))
        assertFalse(order.allowed(ids[1]))
        order.started(ids[0])
        order.complete(ids[0], freshImage = true)
        order.complete(ids[0]) // A duplicate visible card must not remove the preload's spacing.
        advanceTimeBy(15); runCurrent()
        assertFalse(order.allowed(ids[1]))
        advanceTimeBy(1); runCurrent()
        assertTrue(order.allowed(ids[1]))
        order.complete(ids[1]) // Missing artwork must not leave a hole in the sequence.
        assertTrue(order.allowed(ids[3]))
        assertFalse(order.allowed(ids[4]))
        order.close()
    }

    @Test fun `a stalled provider has a bounded wait and unrelated previews bypass the sequence`() = runTest {
        val order = ArtworkLoadOrder(ids, this)
        assertTrue(order.allowed(ItemId("other-page")))
        order.started(ids[0])
        advanceTimeBy(249); runCurrent()
        assertFalse(order.allowed(ids[1]))
        advanceTimeBy(1); runCurrent()
        assertTrue(order.allowed(ids[1]))
        order.complete(ids[0], freshImage = true) // A late result cannot restart or delay the wave.
        assertTrue(order.allowed(ids[1]))
        order.close()
    }

    @Test fun `reordering or leaving cancels old admission work without affecting the new viewport`() = runTest {
        val old = ArtworkLoadOrder(ids, this)
        old.started(ids[0])
        old.complete(ids[0], freshImage = true)
        old.close()
        val next = ArtworkLoadOrder(ids.reversed(), this)
        advanceUntilIdle()
        assertFalse(old.allowed(ids[1]))
        assertTrue(next.allowed(ids[4]))
        assertFalse(next.allowed(ids[3]))
        next.complete(ids[4])
        assertTrue(next.allowed(ids[3]))
        next.close()
    }
}
