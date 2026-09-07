package dev.handheld.launcher.platform.home

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Immutable
import dev.handheld.launcher.contract.ActivityRequest
import dev.handheld.launcher.contract.ActivityRequestAcknowledgement
import dev.handheld.launcher.contract.ActivityRequestId
import dev.handheld.launcher.contract.ActivityRequestPort
import dev.handheld.launcher.contract.ClaimedActivityRequest
import dev.handheld.launcher.contract.PendingActivityRequest
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Marker request for the only Activity-owned role-selection side effect. */
data object RequestHomeRoleSelection : ActivityRequest

@Immutable
sealed interface HomeRoleRequestState {
    data object Idle : HomeRoleRequestState
    data class Requested(val requestId: ActivityRequestId) : HomeRoleRequestState
    data object Accepted : HomeRoleRequestState
    data object Declined : HomeRoleRequestState
    data object Unsupported : HomeRoleRequestState
    data class Failed(val reason: String) : HomeRoleRequestState
}

/** Queues role selection only after an explicit user action; startup never prompts on its own. */
class HomeRoleRequestCoordinator(
    private val activityRequestPort: ActivityRequestPort,
    private val requestIdFactory: () -> ActivityRequestId = {
        ActivityRequestId("home-role:${UUID.randomUUID()}")
    },
) {
    private val mutableState = MutableStateFlow<HomeRoleRequestState>(HomeRoleRequestState.Idle)
    private var claimedRequest: ClaimedHomeRoleRequest? = null
    val state: StateFlow<HomeRoleRequestState> = mutableState.asStateFlow()

    fun requestSelection(): Boolean {
        val id = requestIdFactory()
        val accepted = activityRequestPort.submit(
            PendingActivityRequest(id, RequestHomeRoleSelection),
        )
        if (accepted) mutableState.value = HomeRoleRequestState.Requested(id)
        return accepted
    }

    internal fun recordResult(requestId: ActivityRequestId, result: HomeRoleRequestState) {
        val requested = mutableState.value as? HomeRoleRequestState.Requested ?: return
        if (requested.requestId == requestId) mutableState.value = result
    }

    @Synchronized
    internal fun holdClaim(request: ClaimedHomeRoleRequest) {
        claimedRequest = request
    }

    @Synchronized
    internal fun releaseClaim(request: ClaimedHomeRoleRequest) {
        if (claimedRequest === request) claimedRequest = null
    }

    @Synchronized
    internal fun currentClaim(): ClaimedHomeRoleRequest? = claimedRequest
}

class ClaimedHomeRoleRequest internal constructor(
    internal val claim: ClaimedActivityRequest,
    val intent: Intent,
)

/**
 * Small MainActivity helper. Claiming happens before the picker is shown, so recreation cannot
 * replay it. A declined picker is still a handled request and leaves normal launcher use intact.
 */
class HomeRoleActivityRequestHandler(
    activity: Activity,
    private val activityRequestPort: ActivityRequestPort,
    private val coordinator: HomeRoleRequestCoordinator,
) {
    private val roleManager = activity.getSystemService(Context.ROLE_SERVICE) as? RoleManager

    fun isHomeRoleHeld(): Boolean = roleManager?.run {
        isRoleAvailable(RoleManager.ROLE_HOME) && isRoleHeld(RoleManager.ROLE_HOME)
    } == true

    fun claim(pending: PendingActivityRequest): ClaimedHomeRoleRequest? {
        if (pending.request !== RequestHomeRoleSelection) return null
        val claim = activityRequestPort.claim(pending.id) ?: return null
        val manager = roleManager
        if (manager == null || !manager.isRoleAvailable(RoleManager.ROLE_HOME)) {
            activityRequestPort.complete(claim, ActivityRequestAcknowledgement.Unsupported)
            coordinator.recordResult(pending.id, HomeRoleRequestState.Unsupported)
            return null
        }
        if (manager.isRoleHeld(RoleManager.ROLE_HOME)) {
            activityRequestPort.complete(claim, ActivityRequestAcknowledgement.Handled)
            coordinator.recordResult(pending.id, HomeRoleRequestState.Accepted)
            return null
        }
        return ClaimedHomeRoleRequest(
            claim = claim,
            intent = manager.createRequestRoleIntent(RoleManager.ROLE_HOME),
        ).also(coordinator::holdClaim)
    }

    fun complete(request: ClaimedHomeRoleRequest, resultCode: Int) {
        val held = isHomeRoleHeld()
        activityRequestPort.complete(request.claim, ActivityRequestAcknowledgement.Handled)
        coordinator.recordResult(
            request.claim.id,
            if (held || resultCode == Activity.RESULT_OK) {
                HomeRoleRequestState.Accepted
            } else {
                HomeRoleRequestState.Declined
            },
        )
        coordinator.releaseClaim(request)
    }

    /** Lets an Activity-result callback survive Activity recreation through the app coordinator. */
    fun completeCurrent(resultCode: Int): Boolean {
        val request = coordinator.currentClaim() ?: return false
        complete(request, resultCode)
        return true
    }

    fun fail(request: ClaimedHomeRoleRequest, reason: String) {
        activityRequestPort.complete(
            request.claim,
            ActivityRequestAcknowledgement.Failed(reason),
        )
        coordinator.recordResult(request.claim.id, HomeRoleRequestState.Failed(reason))
        coordinator.releaseClaim(request)
    }
}
