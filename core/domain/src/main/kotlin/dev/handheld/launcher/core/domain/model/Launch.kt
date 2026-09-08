package dev.handheld.launcher.core.domain.model

@JvmInline
value class LaunchOperationId(val value: String) {
    init {
        require(value.isNotBlank()) { "Launch operation IDs must not be blank" }
    }
}

data class LaunchRequest(
    val operationId: LaunchOperationId,
    val itemId: ItemId,
    val target: LaunchTarget,
) {
    init {
        require(itemId == target.itemId) {
            "Launch item ID must match the target identity"
        }
    }

    companion object {
        fun forItem(operationId: LaunchOperationId, item: LibraryItem): LaunchRequest =
            LaunchRequest(operationId, item.id, item.launchTarget)
    }
}

val LaunchTarget.itemId: ItemId
    get() = when (this) {
        is LaunchTarget.AndroidComponent -> componentId.itemId
        is LaunchTarget.ExternalContent -> itemId
        is LaunchTarget.InternalAction -> actionId.itemId
    }

enum class LaunchFailureReason {
    CANCELLED,
    TARGET_UNAVAILABLE,
    REJECTED,
    DISPATCH_FAILED,
}

sealed interface LaunchAcknowledgement {
    val operationId: LaunchOperationId

    data class Dispatched(
        override val operationId: LaunchOperationId,
    ) : LaunchAcknowledgement

    data class Failed(
        override val operationId: LaunchOperationId,
        val reason: LaunchFailureReason,
    ) : LaunchAcknowledgement
}

data class SuccessfulOpenCandidate(
    val operationId: LaunchOperationId,
    val itemId: ItemId,
)

data class SuccessfulOpenRecord(
    val itemId: ItemId,
    val openOrder: Long,
) {
    init {
        require(openOrder > 0) { "Successful-open order must be positive" }
    }
}

sealed interface SuccessfulOpenWriteResult {
    data class Recorded(val record: SuccessfulOpenRecord) : SuccessfulOpenWriteResult
    data object AlreadyRecorded : SuccessfulOpenWriteResult
}
