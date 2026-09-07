package dev.handheld.launcher.launch

import androidx.compose.runtime.Immutable
import dev.handheld.launcher.contract.ItemLaunchPort
import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.DestinationSnapshot
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LaunchAcknowledgement
import dev.handheld.launcher.core.domain.model.LaunchFailureReason
import dev.handheld.launcher.core.domain.model.LaunchOperationId
import dev.handheld.launcher.core.domain.model.LaunchRequest
import dev.handheld.launcher.core.domain.policy.AcknowledgedLaunchPolicy
import dev.handheld.launcher.core.domain.policy.AcknowledgedLaunchState
import dev.handheld.launcher.core.domain.policy.LaunchPolicyOutcome
import dev.handheld.launcher.core.domain.repository.CatalogRepository
import dev.handheld.launcher.core.domain.repository.LaunchDispatcher
import dev.handheld.launcher.core.domain.repository.NavigationSnapshotRepository
import dev.handheld.launcher.core.domain.repository.SuccessfulOpenRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
sealed interface LaunchCoordinatorState {
    data object Idle : LaunchCoordinatorState

    data class Pending(
        val operationId: LaunchOperationId,
        val itemId: ItemId,
        val origin: DestinationSnapshot,
    ) : LaunchCoordinatorState

    data class Succeeded(
        val operationId: LaunchOperationId,
        val itemId: ItemId,
        val origin: DestinationSnapshot,
    ) : LaunchCoordinatorState

    data class Failed(
        val operationId: LaunchOperationId,
        val itemId: ItemId,
        val origin: DestinationSnapshot,
        val reason: LaunchCoordinatorFailure,
    ) : LaunchCoordinatorState
}

enum class LaunchCoordinatorFailure {
    TARGET_UNAVAILABLE,
    REJECTED,
    DISPATCH_FAILED,
    SNAPSHOT_FAILED,
    RECENCY_WRITE_FAILED,
    INVALID_ACKNOWLEDGEMENT,
}

/**
 * Application-scoped launch path. No request is persisted, so process loss cannot replay an
 * external side effect. The origin snapshot is durable before dispatch begins.
 */
class LaunchCoordinator(
    private val catalogRepository: CatalogRepository,
    private val navigationSnapshotRepository: NavigationSnapshotRepository,
    private val launchDispatcher: LaunchDispatcher,
    private val successfulOpenRepository: SuccessfulOpenRepository,
    private val scope: CoroutineScope,
    private val operationIdFactory: () -> LaunchOperationId = {
        LaunchOperationId(UUID.randomUUID().toString())
    },
) {
    private val lock = Any()
    private var activeOperationId: LaunchOperationId? = null
    private var policyState = AcknowledgedLaunchState()
    private val mutableState = MutableStateFlow<LaunchCoordinatorState>(LaunchCoordinatorState.Idle)

    val state: StateFlow<LaunchCoordinatorState> = mutableState.asStateFlow()

    /** Returns once the activation is accepted into the single acknowledged flow. */
    fun submit(itemId: ItemId, origin: DestinationSnapshot): Boolean {
        val operationId = synchronized(lock) {
            if (activeOperationId != null) return false
            operationIdFactory().also { activeOperationId = it }
        }
        mutableState.value = LaunchCoordinatorState.Pending(operationId, itemId, origin)
        scope.launch { execute(operationId, itemId, origin) }
        return true
    }

    fun bind(originSnapshot: () -> DestinationSnapshot): ItemLaunchPort = ItemLaunchPort { itemId ->
        submit(itemId, originSnapshot())
    }

    fun clearResult() {
        if (state.value !is LaunchCoordinatorState.Pending) {
            mutableState.value = LaunchCoordinatorState.Idle
        }
    }

    private suspend fun execute(
        operationId: LaunchOperationId,
        itemId: ItemId,
        origin: DestinationSnapshot,
    ) {
        try {
            try {
                navigationSnapshotRepository.save(origin)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                fail(operationId, itemId, origin, LaunchCoordinatorFailure.SNAPSHOT_FAILED)
                return
            }

            val item = try {
                catalogRepository.findItem(itemId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (item == null || item.availability != Availability.Available) {
                fail(operationId, itemId, origin, LaunchCoordinatorFailure.TARGET_UNAVAILABLE)
                return
            }

            val request = LaunchRequest.forItem(operationId, item)
            val began = synchronized(lock) {
                AcknowledgedLaunchPolicy.begin(policyState, request).also { policyState = it.state }
            }
            if (began.outcome !is LaunchPolicyOutcome.Began) {
                fail(operationId, itemId, origin, LaunchCoordinatorFailure.DISPATCH_FAILED)
                return
            }

            val acknowledgement = try {
                launchDispatcher.dispatch(request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                LaunchAcknowledgement.Failed(operationId, LaunchFailureReason.DISPATCH_FAILED)
            }
            if (acknowledgement.operationId != operationId) {
                synchronized(lock) { policyState = AcknowledgedLaunchState() }
                fail(operationId, itemId, origin, LaunchCoordinatorFailure.INVALID_ACKNOWLEDGEMENT)
                return
            }

            val acknowledged = synchronized(lock) {
                AcknowledgedLaunchPolicy.acknowledge(policyState, acknowledgement)
                    .also { policyState = it.state }
            }
            val outcome = acknowledged.outcome as? LaunchPolicyOutcome.Acknowledged
            if (acknowledgement is LaunchAcknowledgement.Failed || outcome == null) {
                val reason = (acknowledgement as? LaunchAcknowledgement.Failed)
                    ?.reason
                    ?.toCoordinatorFailure()
                    ?: LaunchCoordinatorFailure.INVALID_ACKNOWLEDGEMENT
                fail(operationId, itemId, origin, reason)
                return
            }

            val candidate = outcome.successfulOpen
            if (candidate != null) {
                try {
                    successfulOpenRepository.recordOnce(candidate)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    fail(operationId, itemId, origin, LaunchCoordinatorFailure.RECENCY_WRITE_FAILED)
                    return
                }
            }
            mutableState.value = LaunchCoordinatorState.Succeeded(operationId, itemId, origin)
        } finally {
            synchronized(lock) {
                if (policyState.pending?.operationId == operationId) {
                    policyState = AcknowledgedLaunchState(
                        acknowledgedOperationIds = policyState.acknowledgedOperationIds,
                    )
                }
                if (activeOperationId == operationId) activeOperationId = null
            }
        }
    }

    private fun fail(
        operationId: LaunchOperationId,
        itemId: ItemId,
        origin: DestinationSnapshot,
        reason: LaunchCoordinatorFailure,
    ) {
        mutableState.value = LaunchCoordinatorState.Failed(operationId, itemId, origin, reason)
    }
}

private fun LaunchFailureReason.toCoordinatorFailure(): LaunchCoordinatorFailure = when (this) {
    LaunchFailureReason.TARGET_UNAVAILABLE -> LaunchCoordinatorFailure.TARGET_UNAVAILABLE
    LaunchFailureReason.REJECTED -> LaunchCoordinatorFailure.REJECTED
    LaunchFailureReason.DISPATCH_FAILED -> LaunchCoordinatorFailure.DISPATCH_FAILED
}
