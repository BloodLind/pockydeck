package dev.handheld.launcher.core.domain.policy

import dev.handheld.launcher.core.domain.model.LaunchAcknowledgement
import dev.handheld.launcher.core.domain.model.LaunchOperationId
import dev.handheld.launcher.core.domain.model.LaunchRequest
import dev.handheld.launcher.core.domain.model.LaunchTarget
import dev.handheld.launcher.core.domain.model.SuccessfulOpenCandidate

data class AcknowledgedLaunchState(
    val pending: LaunchRequest? = null,
    val acknowledgedOperationIds: Set<LaunchOperationId> = emptySet(),
)

data class LaunchPolicyTransition(
    val state: AcknowledgedLaunchState,
    val outcome: LaunchPolicyOutcome,
)

sealed interface LaunchPolicyOutcome {
    data class Began(val request: LaunchRequest) : LaunchPolicyOutcome
    data class SuppressedWhilePending(val pending: LaunchRequest) : LaunchPolicyOutcome
    data object IgnoredAlreadyAcknowledged : LaunchPolicyOutcome
    data object IgnoredUnknownAcknowledgement : LaunchPolicyOutcome

    data class Acknowledged(
        val successfulOpen: SuccessfulOpenCandidate?,
    ) : LaunchPolicyOutcome
}

object AcknowledgedLaunchPolicy {
    fun begin(
        state: AcknowledgedLaunchState,
        request: LaunchRequest,
    ): LaunchPolicyTransition = when {
        request.operationId in state.acknowledgedOperationIds -> LaunchPolicyTransition(
            state,
            LaunchPolicyOutcome.IgnoredAlreadyAcknowledged,
        )

        state.pending != null -> LaunchPolicyTransition(
            state,
            LaunchPolicyOutcome.SuppressedWhilePending(state.pending),
        )

        else -> LaunchPolicyTransition(
            state.copy(pending = request),
            LaunchPolicyOutcome.Began(request),
        )
    }

    fun acknowledge(
        state: AcknowledgedLaunchState,
        acknowledgement: LaunchAcknowledgement,
    ): LaunchPolicyTransition {
        if (acknowledgement.operationId in state.acknowledgedOperationIds) {
            return LaunchPolicyTransition(state, LaunchPolicyOutcome.IgnoredAlreadyAcknowledged)
        }

        val pending = state.pending
        if (pending == null || pending.operationId != acknowledgement.operationId) {
            return LaunchPolicyTransition(state, LaunchPolicyOutcome.IgnoredUnknownAcknowledgement)
        }

        val candidate = when {
            acknowledgement !is LaunchAcknowledgement.Dispatched -> null
            pending.target is LaunchTarget.InternalAction -> null
            else -> SuccessfulOpenCandidate(pending.operationId, pending.itemId)
        }
        return LaunchPolicyTransition(
            state = state.copy(
                pending = null,
                acknowledgedOperationIds = state.acknowledgedOperationIds + acknowledgement.operationId,
            ),
            outcome = LaunchPolicyOutcome.Acknowledged(candidate),
        )
    }
}
