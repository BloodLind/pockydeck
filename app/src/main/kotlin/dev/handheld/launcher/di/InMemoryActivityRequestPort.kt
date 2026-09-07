package dev.handheld.launcher.di

import dev.handheld.launcher.contract.ActivityRequestAcknowledgement
import dev.handheld.launcher.contract.ActivityRequestId
import dev.handheld.launcher.contract.ActivityRequestPort
import dev.handheld.launcher.contract.ClaimedActivityRequest
import dev.handheld.launcher.contract.PendingActivityRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class InMemoryActivityRequestPort : ActivityRequestPort {
    private val mutablePending = MutableStateFlow<PendingActivityRequest?>(null)
    override val pending: StateFlow<PendingActivityRequest?> = mutablePending.asStateFlow()

    private val acknowledgements = mutableMapOf<ActivityRequestId, ActivityRequestAcknowledgement>()
    private var inFlight: ClaimedActivityRequest? = null

    @Synchronized
    override fun submit(request: PendingActivityRequest): Boolean {
        if (
            request.id in acknowledgements ||
            mutablePending.value != null ||
            inFlight != null
        ) return false
        mutablePending.value = request
        return true
    }

    @Synchronized
    override fun claim(id: ActivityRequestId): ClaimedActivityRequest? {
        val request = mutablePending.value ?: return null
        if (request.id != id || id in acknowledgements || inFlight != null) return null
        return ClaimedActivityRequest(request.id, request.request).also { claim ->
            inFlight = claim
            mutablePending.value = null
        }
    }

    @Synchronized
    override fun complete(
        claim: ClaimedActivityRequest,
        result: ActivityRequestAcknowledgement,
    ): Boolean {
        if (inFlight !== claim || claim.id in acknowledgements) return false
        acknowledgements[claim.id] = result
        inFlight = null
        return true
    }
}
