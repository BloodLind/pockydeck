package dev.handheld.launcher.contract

import dev.handheld.launcher.di.AppContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch

class ActivityRequestPortTest {
    private data object TestRequest : ActivityRequest

    @Test
    fun pendingRequestMustBeClaimedThenCompletedByTheWinningObserver() {
        val port = AppContainer().activityRequestPort
        val request = PendingActivityRequest(ActivityRequestId("operation-1"), TestRequest)

        assertTrue(port.submit(request))
        assertEquals(request, port.pending.value)
        assertNull(port.claim(ActivityRequestId("other")))
        val claim = port.claim(request.id)
        requireNotNull(claim)
        assertNull(port.pending.value)
        assertNull(port.claim(request.id))
        assertTrue(port.complete(claim, ActivityRequestAcknowledgement.Handled))
        assertFalse(port.complete(claim, ActivityRequestAcknowledgement.Handled))
        assertFalse(port.submit(request))
    }

    @Test
    fun secondRequestIsRejectedWhileOneIsPending() {
        val port = AppContainer().activityRequestPort
        assertTrue(port.submit(PendingActivityRequest(ActivityRequestId("one"), TestRequest)))
        assertFalse(port.submit(PendingActivityRequest(ActivityRequestId("two"), TestRequest)))
    }

    @Test
    fun competingConsumersClaimOnceAndReattachmentCannotReplay() {
        val port = AppContainer().activityRequestPort
        val request = PendingActivityRequest(ActivityRequestId("competing"), TestRequest)
        assertTrue(port.submit(request))

        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val claims = Collections.synchronizedList(
            mutableListOf<ClaimedActivityRequest?>(),
        )
        val consumers = List(2) {
            Thread {
                ready.countDown()
                start.await()
                claims += port.claim(request.id)
            }
        }
        consumers.forEach(Thread::start)
        ready.await()
        start.countDown()
        consumers.forEach(Thread::join)

        val winningClaim = claims.single { it != null }
        assertEquals(1, claims.count { it != null })
        assertNull(port.pending.value)
        assertNull(port.claim(request.id))
        assertTrue(port.complete(requireNotNull(winningClaim), ActivityRequestAcknowledgement.Unsupported))
        assertNull(port.pending.value)
        assertNull(port.claim(request.id))
    }
}
