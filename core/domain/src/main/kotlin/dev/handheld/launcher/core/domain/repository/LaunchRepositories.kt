package dev.handheld.launcher.core.domain.repository

import dev.handheld.launcher.core.domain.model.LaunchAcknowledgement
import dev.handheld.launcher.core.domain.model.LaunchRequest
import dev.handheld.launcher.core.domain.model.SuccessfulOpenCandidate
import dev.handheld.launcher.core.domain.model.SuccessfulOpenRecord
import dev.handheld.launcher.core.domain.model.SuccessfulOpenWriteResult
import kotlinx.coroutines.flow.Flow

interface LaunchDispatcher {
    /**
     * Dispatches a request revalidated against the current active catalog item. A success
     * acknowledges the external side effect only; it makes no process-liveness claim.
     */
    suspend fun dispatch(request: LaunchRequest): LaunchAcknowledgement
}

interface SuccessfulOpenRepository {
    val records: Flow<List<SuccessfulOpenRecord>>

    /**
     * In one local transaction, deduplicates by operation ID, allocates the next globally
     * serialized increasing order, and upserts the item's single current record.
     * Retrying the same candidate must return [SuccessfulOpenWriteResult.AlreadyRecorded].
     */
    suspend fun recordOnce(candidate: SuccessfulOpenCandidate): SuccessfulOpenWriteResult
}
