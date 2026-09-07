package dev.handheld.launcher.core.data.android.apps

import dev.handheld.launcher.core.domain.model.CurrentUserAndroidComponentId
import dev.handheld.launcher.core.domain.model.LaunchAcknowledgement
import dev.handheld.launcher.core.domain.model.LaunchFailureReason
import dev.handheld.launcher.core.domain.model.LaunchOperationId
import dev.handheld.launcher.core.domain.model.LaunchRequest
import dev.handheld.launcher.core.domain.model.LaunchTarget
import dev.handheld.launcher.core.domain.model.SystemActionId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidComponentLaunchDispatcherTest {
    @Test
    fun `valid exact component is revalidated before successful dispatch`() = runBlocking {
        val events = mutableListOf<String>()
        val gateway = FakeGateway(
            validation = AndroidComponentValidation.Available,
            startResult = AndroidComponentStartResult.Started,
            events = events,
        )
        val dispatcher = dispatcher(gateway)

        val result = dispatcher.dispatch(request())

        assertEquals(LaunchAcknowledgement.Dispatched(OPERATION_ID), result)
        assertEquals(listOf("validate:$COMPONENT", "start:$COMPONENT"), events)
    }

    @Test
    fun `missing or disabled exact component returns target unavailable without starting`() = runBlocking {
        listOf(
            AndroidComponentValidation.TargetUnavailable,
            AndroidComponentValidation.Failed,
        ).forEach { validation ->
            val gateway = FakeGateway(validation = validation)
            val result = dispatcher(gateway).dispatch(request())

            val expectedReason = if (validation == AndroidComponentValidation.TargetUnavailable) {
                LaunchFailureReason.TARGET_UNAVAILABLE
            } else {
                LaunchFailureReason.DISPATCH_FAILED
            }
            assertEquals(LaunchAcknowledgement.Failed(OPERATION_ID, expectedReason), result)
            assertFalse(gateway.started)
        }
    }

    @Test
    fun `security rejection during validation or start is explicit and recoverable`() = runBlocking {
        val rejectedValidation = FakeGateway(validation = AndroidComponentValidation.Rejected)
        assertEquals(
            LaunchAcknowledgement.Failed(OPERATION_ID, LaunchFailureReason.REJECTED),
            dispatcher(rejectedValidation).dispatch(request()),
        )
        assertFalse(rejectedValidation.started)

        val rejectedStart = FakeGateway(startResult = AndroidComponentStartResult.Rejected)
        assertEquals(
            LaunchAcknowledgement.Failed(OPERATION_ID, LaunchFailureReason.REJECTED),
            dispatcher(rejectedStart).dispatch(request()),
        )
        assertTrue(rejectedStart.started)
    }

    @Test
    fun `race to missing target and unexpected start failure remain recoverable`() = runBlocking {
        val racedMissing = FakeGateway(startResult = AndroidComponentStartResult.TargetUnavailable)
        assertEquals(
            LaunchAcknowledgement.Failed(OPERATION_ID, LaunchFailureReason.TARGET_UNAVAILABLE),
            dispatcher(racedMissing).dispatch(request()),
        )

        val failedStart = FakeGateway(startResult = AndroidComponentStartResult.Failed)
        assertEquals(
            LaunchAcknowledgement.Failed(OPERATION_ID, LaunchFailureReason.DISPATCH_FAILED),
            dispatcher(failedStart).dispatch(request()),
        )
    }

    @Test
    fun `self and non Android targets are rejected before platform access`() = runBlocking {
        val gateway = FakeGateway()
        val selfComponent = CurrentUserAndroidComponentId(OWN_PACKAGE, "$OWN_PACKAGE.Main")
        val selfRequest = LaunchRequest(
            LaunchOperationId("self"),
            selfComponent.itemId,
            LaunchTarget.AndroidComponent(selfComponent),
        )
        assertEquals(
            LaunchAcknowledgement.Failed(
                LaunchOperationId("self"),
                LaunchFailureReason.REJECTED,
            ),
            dispatcher(gateway).dispatch(selfRequest),
        )

        val action = SystemActionId("settings")
        val internalRequest = LaunchRequest(
            LaunchOperationId("internal"),
            action.itemId,
            LaunchTarget.InternalAction(action),
        )
        assertEquals(
            LaunchAcknowledgement.Failed(
                LaunchOperationId("internal"),
                LaunchFailureReason.REJECTED,
            ),
            dispatcher(gateway).dispatch(internalRequest),
        )
        assertTrue(gateway.events.isEmpty())
    }

    private fun dispatcher(gateway: FakeGateway) = AndroidComponentLaunchDispatcher(
        ownPackageName = OWN_PACKAGE,
        gateway = gateway,
        dispatcher = Dispatchers.Unconfined,
    )

    private fun request() = LaunchRequest(
        operationId = OPERATION_ID,
        itemId = COMPONENT.itemId,
        target = LaunchTarget.AndroidComponent(COMPONENT),
    )

    private class FakeGateway(
        private val validation: AndroidComponentValidation = AndroidComponentValidation.Available,
        private val startResult: AndroidComponentStartResult = AndroidComponentStartResult.Started,
        val events: MutableList<String> = mutableListOf(),
    ) : AndroidComponentLaunchGateway {
        var started = false
            private set

        override fun revalidate(
            componentId: CurrentUserAndroidComponentId,
        ): AndroidComponentValidation {
            events += "validate:$componentId"
            return validation
        }

        override fun start(
            componentId: CurrentUserAndroidComponentId,
        ): AndroidComponentStartResult {
            started = true
            events += "start:$componentId"
            return startResult
        }
    }

    private companion object {
        const val OWN_PACKAGE = "dev.handheld.launcher"
        val COMPONENT = CurrentUserAndroidComponentId("dev.fixture", "dev.fixture.MainActivity")
        val OPERATION_ID = LaunchOperationId("operation")
    }
}
