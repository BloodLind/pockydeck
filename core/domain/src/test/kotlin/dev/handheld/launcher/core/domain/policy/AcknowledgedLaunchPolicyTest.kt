package dev.handheld.launcher.core.domain.policy

import dev.handheld.launcher.core.domain.model.ContractFixtures
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LaunchAcknowledgement
import dev.handheld.launcher.core.domain.model.LaunchFailureReason
import dev.handheld.launcher.core.domain.model.LaunchOperationId
import dev.handheld.launcher.core.domain.model.LaunchRequest
import dev.handheld.launcher.core.domain.model.LaunchTarget
import dev.handheld.launcher.core.domain.model.SuccessfulOpenWriteResult
import dev.handheld.launcher.core.domain.repository.InMemorySuccessfulOpenRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AcknowledgedLaunchPolicyTest {
    @Test
    fun requestRejectsAnItemAndTargetIdentityMismatch() {
        val item = ContractFixtures.androidApp("example.one", "example.one.Main", "One")
        val other = ContractFixtures.androidApp("example.two", "example.two.Main", "Two")

        assertThrows(IllegalArgumentException::class.java) {
            LaunchRequest(
                operationId = LaunchOperationId("mismatch"),
                itemId = item.id,
                target = other.launchTarget,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            LaunchRequest(
                operationId = LaunchOperationId("external-mismatch"),
                itemId = item.id,
                target = LaunchTarget.ExternalContent(ItemId("rom:other")),
            )
        }
    }

    @Test
    fun successRecordsOnceWhileFailureDuplicateAndInternalActionRecordNothing() = runBlocking {
        val item = ContractFixtures.androidApp("example.game", "example.game.Main", "Game")
        val successfulOp = LaunchOperationId("success")
        val successRequest = LaunchRequest.forItem(successfulOp, item)
        var state = AcknowledgedLaunchState()

        state = AcknowledgedLaunchPolicy.begin(state, successRequest).state
        val whilePending = AcknowledgedLaunchPolicy.begin(
            state,
            LaunchRequest.forItem(LaunchOperationId("duplicate-tap"), item),
        )
        assertTrue(whilePending.outcome is LaunchPolicyOutcome.SuppressedWhilePending)

        val success = AcknowledgedLaunchPolicy.acknowledge(
            state,
            LaunchAcknowledgement.Dispatched(successfulOp),
        )
        val candidate = (success.outcome as LaunchPolicyOutcome.Acknowledged).successfulOpen
        val history = InMemorySuccessfulOpenRepository()
        assertTrue(history.recordOnce(requireNotNull(candidate)) is SuccessfulOpenWriteResult.Recorded)
        assertEquals(SuccessfulOpenWriteResult.AlreadyRecorded, history.recordOnce(candidate))
        assertEquals(1, history.current().size)

        val repeated = AcknowledgedLaunchPolicy.acknowledge(
            success.state,
            LaunchAcknowledgement.Dispatched(successfulOp),
        )
        assertEquals(LaunchPolicyOutcome.IgnoredAlreadyAcknowledged, repeated.outcome)

        val failedOp = LaunchOperationId("failed")
        val failedPending = AcknowledgedLaunchPolicy.begin(
            success.state,
            LaunchRequest.forItem(failedOp, item),
        )
        val failed = AcknowledgedLaunchPolicy.acknowledge(
            failedPending.state,
            LaunchAcknowledgement.Failed(failedOp, LaunchFailureReason.DISPATCH_FAILED),
        )
        assertNull((failed.outcome as LaunchPolicyOutcome.Acknowledged).successfulOpen)
        assertEquals(1, history.current().size)

        val system = ContractFixtures.systemAction("settings", "Settings")
        val internalOp = LaunchOperationId("internal")
        val internalPending = AcknowledgedLaunchPolicy.begin(
            failed.state,
            LaunchRequest.forItem(internalOp, system),
        )
        val internal = AcknowledgedLaunchPolicy.acknowledge(
            internalPending.state,
            LaunchAcknowledgement.Dispatched(internalOp),
        )
        assertNull((internal.outcome as LaunchPolicyOutcome.Acknowledged).successfulOpen)
        assertEquals(1, history.current().size)
    }
}
