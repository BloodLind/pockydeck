package dev.handheld.launcher.core.domain.policy

import dev.handheld.launcher.core.domain.model.ContractFixtures
import dev.handheld.launcher.core.domain.model.LaunchOperationId
import dev.handheld.launcher.core.domain.model.SuccessfulOpenCandidate
import dev.handheld.launcher.core.domain.model.SuccessfulOpenRecord
import dev.handheld.launcher.core.domain.repository.InMemorySuccessfulOpenRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryItemOrderingTest {
    @Test
    fun reopeningPromotesOneItemAndUnopenedItemsUseTitleThenId() = runBlocking {
        val a = ContractFixtures.androidApp("example.a", "example.a.Main", "A")
        val b = ContractFixtures.androidApp("example.b", "example.b.Main", "B")
        val c = ContractFixtures.androidApp("example.c", "example.c.Main", "C")
        val sameTitleSecond = ContractFixtures.androidApp("example.z", "example.z.Main", "Untitled")
        val sameTitleFirst = ContractFixtures.androidApp("example.d", "example.d.Main", "Untitled")
        val history = InMemorySuccessfulOpenRepository(
            listOf(
                SuccessfulOpenRecord(a.id, 1),
                SuccessfulOpenRecord(b.id, 2),
                SuccessfulOpenRecord(c.id, 3),
            ),
        )

        history.recordOnce(SuccessfulOpenCandidate(LaunchOperationId("reopen-b"), b.id))
        val ordered = LibraryItemOrdering.recentFirst(
            items = listOf(sameTitleSecond, a, c, sameTitleFirst, b),
            records = history.current(),
        )

        assertEquals(
            listOf(b.id, c.id, a.id, sameTitleFirst.id, sameTitleSecond.id),
            ordered.map { it.id },
        )
        assertEquals(1, ordered.count { it.id == b.id })
    }

    @Test
    fun systemActionCannotBecomeRecentEvenWithMalformedHistoricalInput() {
        val app = ContractFixtures.androidApp("example.app", "example.app.Main", "Z app")
        val system = ContractFixtures.systemAction("settings", "A settings")

        val ordered = LibraryItemOrdering.recentFirst(
            items = listOf(app, system),
            records = listOf(SuccessfulOpenRecord(system.id, 99)),
        )

        assertEquals(listOf(system.id, app.id), ordered.map { it.id })
    }

    @Test
    fun concurrentDistinctOperationsReceiveUniqueGlobalOrders() = runBlocking {
        val history = InMemorySuccessfulOpenRepository()
        val items = (1..40).map { index ->
            ContractFixtures.androidApp(
                packageName = "example.concurrent$index",
                className = "example.concurrent$index.Main",
                title = "Item $index",
            )
        }

        coroutineScope {
            items.mapIndexed { index, item ->
                async {
                    history.recordOnce(
                        SuccessfulOpenCandidate(LaunchOperationId("concurrent-$index"), item.id),
                    )
                }
            }.awaitAll()
        }

        assertEquals((1L..40L).toSet(), history.current().map { it.openOrder }.toSet())
        assertEquals(40, history.current().map { it.itemId }.distinct().size)
    }
}
