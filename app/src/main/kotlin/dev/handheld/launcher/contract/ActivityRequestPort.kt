package dev.handheld.launcher.contract

import kotlinx.coroutines.flow.StateFlow

/** Marker for work that must be performed by the foreground Activity. */
interface ActivityRequest

@JvmInline
value class ActivityRequestId(val value: String) {
    init {
        require(value.isNotBlank()) { "Activity request IDs must not be blank" }
    }
}

data class PendingActivityRequest(
    val id: ActivityRequestId,
    val request: ActivityRequest,
)

/** Opaque ownership returned to the one observer that atomically claims a request. */
class ClaimedActivityRequest internal constructor(
    val id: ActivityRequestId,
    val request: ActivityRequest,
)

sealed interface ActivityRequestAcknowledgement {
    data object Handled : ActivityRequestAcknowledgement
    data object Unsupported : ActivityRequestAcknowledgement
    data class Failed(val reason: String) : ActivityRequestAcknowledgement
}

/**
 * Application-scoped bridge for one request that needs an Activity at a time.
 * Claim removes the request from observation before the side effect. Completion records the
 * acknowledgement separately. Recollection or a competing observer cannot claim it again.
 */
interface ActivityRequestPort {
    val pending: StateFlow<PendingActivityRequest?>

    fun submit(request: PendingActivityRequest): Boolean

    fun claim(id: ActivityRequestId): ClaimedActivityRequest?

    fun complete(
        claim: ClaimedActivityRequest,
        result: ActivityRequestAcknowledgement,
    ): Boolean
}
