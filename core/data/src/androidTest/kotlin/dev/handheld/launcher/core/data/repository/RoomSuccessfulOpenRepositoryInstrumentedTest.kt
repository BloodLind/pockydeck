package dev.handheld.launcher.core.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.handheld.launcher.core.data.local.LauncherDatabase
import dev.handheld.launcher.core.data.local.SuccessfulOpenOrderStateEntity
import dev.handheld.launcher.core.data.local.SuccessfulOpenReferenceEntity
import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.CurrentUserAndroidComponentId
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LaunchAcknowledgement
import dev.handheld.launcher.core.domain.model.LaunchFailureReason
import dev.handheld.launcher.core.domain.model.LaunchOperationId
import dev.handheld.launcher.core.domain.model.LaunchRequest
import dev.handheld.launcher.core.domain.model.LibraryCategory
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.SuccessfulOpenCandidate
import dev.handheld.launcher.core.domain.model.SuccessfulOpenRecord
import dev.handheld.launcher.core.domain.model.SuccessfulOpenWriteResult
import dev.handheld.launcher.core.domain.model.SupportedItemAction
import dev.handheld.launcher.core.domain.model.SystemActionId
import dev.handheld.launcher.core.domain.policy.AcknowledgedLaunchPolicy
import dev.handheld.launcher.core.domain.policy.AcknowledgedLaunchState
import dev.handheld.launcher.core.domain.policy.LaunchPolicyOutcome
import dev.handheld.launcher.core.domain.policy.LibraryItemOrdering
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomSuccessfulOpenRepositoryInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: LauncherDatabase
    private lateinit var repository: RoomSuccessfulOpenRepository
    private lateinit var databaseName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "us018-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun repeatedSuccessfulOpenPromotesOneItemWithoutDuplicatingItsIdentity() = runBlocking {
        val alpha = androidApp("alpha", "Alpha")
        val beta = androidApp("beta", "Beta")
        val charlie = androidApp("charlie", "Charlie")
        record("open-alpha", alpha.id)
        record("open-beta", beta.id)
        record("open-charlie", charlie.id)
        assertEquals(listOf(charlie.id, beta.id, alpha.id), recentOrder(alpha, beta, charlie))

        assertEquals(4L, recordedOrder(record("open-beta-again", beta.id)))
        assertEquals(listOf(beta.id, charlie.id, alpha.id), recentOrder(alpha, beta, charlie))
        assertEquals(5L, recordedOrder(record("open-beta-third", beta.id)))
        assertEquals(listOf(beta.id, charlie.id, alpha.id), recentOrder(alpha, beta, charlie))

        val records = repository.records.first()
        assertEquals(3, records.size)
        assertEquals(1, records.count { it.itemId == beta.id })
    }

    @Test
    fun repeatedSameOperationAllocatesNoOrderIncludingAfterReopen() = runBlocking {
        val candidate = SuccessfulOpenCandidate(LaunchOperationId("one-operation"), ItemId("item:b"))
        assertEquals(1L, recordedOrder(repository.recordOnce(candidate)))
        assertEquals(SuccessfulOpenWriteResult.AlreadyRecorded, repository.recordOnce(candidate))

        database.close()
        openDatabase()

        assertEquals(SuccessfulOpenWriteResult.AlreadyRecorded, repository.recordOnce(candidate))
        assertEquals(listOf(SuccessfulOpenRecord(candidate.itemId, 1)), repository.records.first())
        assertEquals(1L, database.successfulOpenDao().readLastOpenOrder())
        assertEquals(1, database.successfulOpenDao().operationCount())

        assertEquals(2L, recordedOrder(record("next-operation", ItemId("item:a"))))
    }

    @Test
    fun concurrentUniqueOperationsReceiveOneContiguousSerializedOrder() = runBlocking {
        val results = coroutineScope {
            (0 until 32).map { index ->
                async(Dispatchers.Default) {
                    repository.recordOnce(
                        SuccessfulOpenCandidate(
                            LaunchOperationId("operation-$index"),
                            ItemId("item:$index"),
                        ),
                    )
                }
            }.awaitAll()
        }

        val orders = results.map(::recordedOrder).sorted()
        assertEquals((1L..32L).toList(), orders)
        assertEquals((32L downTo 1L).toList(), repository.records.first().map { it.openOrder })
    }

    @Test
    fun concurrentSameOperationRecordsExactlyOnce() = runBlocking {
        val candidate = SuccessfulOpenCandidate(LaunchOperationId("shared-operation"), ItemId("item:b"))
        val results = coroutineScope {
            (0 until 16).map {
                async(Dispatchers.Default) { repository.recordOnce(candidate) }
            }.awaitAll()
        }

        assertEquals(1, results.count { it is SuccessfulOpenWriteResult.Recorded })
        assertEquals(15, results.count { it == SuccessfulOpenWriteResult.AlreadyRecorded })
        assertEquals(listOf(SuccessfulOpenRecord(candidate.itemId, 1)), repository.records.first())
        assertEquals(1L, database.successfulOpenDao().readLastOpenOrder())
    }

    @Test
    fun failedInternalUnknownAndRepeatedAcknowledgementsProduceNoRecordCandidate() = runBlocking {
        val app = androidApp("game", "Game")
        record("existing-success", app.id)
        val expectedRecords = repository.records.first()
        val failedOperation = LaunchOperationId("failed")
        val failedState = AcknowledgedLaunchPolicy.begin(
            AcknowledgedLaunchState(),
            LaunchRequest.forItem(failedOperation, app),
        ).state
        val failed = AcknowledgedLaunchPolicy.acknowledge(
            failedState,
            LaunchAcknowledgement.Failed(failedOperation, LaunchFailureReason.DISPATCH_FAILED),
        )
        assertNull((failed.outcome as LaunchPolicyOutcome.Acknowledged).successfulOpen)

        val internal = LibraryItem.SystemAction(
            actionId = SystemActionId("settings"),
            title = "Settings",
            availability = Availability.Available,
            supportedActions = setOf(SupportedItemAction.OPEN),
        )
        val internalOperation = LaunchOperationId("internal")
        val internalState = AcknowledgedLaunchPolicy.begin(
            failed.state,
            LaunchRequest.forItem(internalOperation, internal),
        ).state
        val internalResult = AcknowledgedLaunchPolicy.acknowledge(
            internalState,
            LaunchAcknowledgement.Dispatched(internalOperation),
        )
        assertNull((internalResult.outcome as LaunchPolicyOutcome.Acknowledged).successfulOpen)

        val unknown = AcknowledgedLaunchPolicy.acknowledge(
            internalResult.state,
            LaunchAcknowledgement.Dispatched(LaunchOperationId("unknown")),
        )
        assertEquals(LaunchPolicyOutcome.IgnoredUnknownAcknowledgement, unknown.outcome)
        val repeated = AcknowledgedLaunchPolicy.acknowledge(
            internalResult.state,
            LaunchAcknowledgement.Dispatched(internalOperation),
        )
        assertEquals(LaunchPolicyOutcome.IgnoredAlreadyAcknowledged, repeated.outcome)
        assertEquals(expectedRecords, repository.records.first())
    }

    @Test
    fun mixedRecentAndUnopenedItemsUseSharedDomainOrdering() = runBlocking {
        val lowercase = androidApp("lower", "alpha")
        val uppercase = androidApp("upper", "Alpha")
        val beta = androidApp("beta", "Beta")
        val recent = androidApp("recent", "Zulu")
        record("recent", recent.id)

        assertEquals(
            listOf(recent.id, uppercase.id, lowercase.id, beta.id),
            recentOrder(beta, lowercase, recent, uppercase),
        )
    }

    @Test
    fun receiptOrHistoryFailureRollsBackCounterAndOperationReceipt() = runBlocking {
        database.catalogReferenceDao().upsertHistoryReference(
            SuccessfulOpenReferenceEntity("item:existing", 1),
        )
        database.successfulOpenDao().ensureOrderState(
            SuccessfulOpenOrderStateEntity(lastOpenOrder = 0),
        )

        var failure: Exception? = null
        try {
            record("will-roll-back", ItemId("item:new"))
        } catch (error: Exception) {
            failure = error
        }

        assertNotNull(failure)
        assertEquals(0L, database.successfulOpenDao().readLastOpenOrder())
        assertEquals(0, database.successfulOpenDao().operationCount())
        assertEquals(
            listOf(SuccessfulOpenReferenceEntity("item:existing", 1)),
            database.catalogReferenceDao().readHistoryReferences(),
        )
    }

    @Test
    fun maximumOrderFailsAtomicallyInsteadOfWrapping() = runBlocking {
        database.successfulOpenDao().ensureOrderState(
            SuccessfulOpenOrderStateEntity(lastOpenOrder = Long.MAX_VALUE),
        )

        var failure: IllegalStateException? = null
        try {
            record("overflow", ItemId("item:overflow"))
        } catch (error: IllegalStateException) {
            failure = error
        }

        assertNotNull(failure)
        assertEquals(Long.MAX_VALUE, database.successfulOpenDao().readLastOpenOrder())
        assertEquals(0, database.successfulOpenDao().operationCount())
        assertTrue(repository.records.first().isEmpty())
    }

    private fun openDatabase() {
        database = LauncherDatabase.open(context, databaseName)
        repository = RoomSuccessfulOpenRepository(database)
    }

    private suspend fun record(operationId: String, itemId: ItemId): SuccessfulOpenWriteResult =
        repository.recordOnce(SuccessfulOpenCandidate(LaunchOperationId(operationId), itemId))

    private suspend fun recentOrder(vararg items: LibraryItem): List<ItemId> =
        LibraryItemOrdering.recentFirst(items.toList(), repository.records.first()).map { it.id }

    private fun recordedOrder(result: SuccessfulOpenWriteResult): Long =
        (result as SuccessfulOpenWriteResult.Recorded).record.openOrder

    private fun androidApp(key: String, title: String): LibraryItem.AndroidApp = LibraryItem.AndroidApp(
        componentId = CurrentUserAndroidComponentId("example.$key", "example.$key.Main"),
        title = title,
        category = LibraryCategory.OTHER,
        availability = Availability.Available,
        supportedActions = setOf(SupportedItemAction.OPEN),
    )
}
