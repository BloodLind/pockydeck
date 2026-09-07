package dev.handheld.launcher.core.data.repository

import androidx.room.withTransaction
import dev.handheld.launcher.core.data.local.LauncherDatabase
import dev.handheld.launcher.core.data.local.SuccessfulOpenOperationEntity
import dev.handheld.launcher.core.data.local.SuccessfulOpenOrderStateEntity
import dev.handheld.launcher.core.data.local.SuccessfulOpenReferenceEntity
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.SuccessfulOpenCandidate
import dev.handheld.launcher.core.domain.model.SuccessfulOpenRecord
import dev.handheld.launcher.core.domain.model.SuccessfulOpenWriteResult
import dev.handheld.launcher.core.domain.repository.SuccessfulOpenRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RoomSuccessfulOpenRepository(
    private val database: LauncherDatabase,
) : SuccessfulOpenRepository {
    private val dao = database.successfulOpenDao()

    override val records: Flow<List<SuccessfulOpenRecord>> = dao.observeHistory()
        .map { rows -> rows.map { SuccessfulOpenRecord(ItemId(it.itemId), it.openOrder) } }
        .distinctUntilChanged()

    override suspend fun recordOnce(
        candidate: SuccessfulOpenCandidate,
    ): SuccessfulOpenWriteResult = database.withTransaction {
        if (dao.operationExists(candidate.operationId.value)) {
            return@withTransaction SuccessfulOpenWriteResult.AlreadyRecorded
        }

        dao.ensureOrderState(SuccessfulOpenOrderStateEntity(lastOpenOrder = 0))
        val previousOpenOrder = requireNotNull(dao.readLastOpenOrder()) {
            "Successful-open order state was not initialized"
        }
        check(previousOpenOrder < Long.MAX_VALUE) {
            "Successful-open order is exhausted and cannot be safely incremented"
        }
        val nextOpenOrder = previousOpenOrder + 1
        check(dao.updateOpenOrder(previousOpenOrder, nextOpenOrder) == 1) {
            "Successful-open order changed outside the serialized transaction"
        }

        dao.insertOperation(
            SuccessfulOpenOperationEntity(
                operationId = candidate.operationId.value,
                itemId = candidate.itemId.value,
                openOrder = nextOpenOrder,
            ),
        )
        if (dao.updateHistory(candidate.itemId.value, nextOpenOrder) == 0) {
            dao.insertHistory(
                SuccessfulOpenReferenceEntity(
                    itemId = candidate.itemId.value,
                    openOrder = nextOpenOrder,
                ),
            )
        }

        SuccessfulOpenWriteResult.Recorded(
            SuccessfulOpenRecord(candidate.itemId, nextOpenOrder),
        )
    }
}
