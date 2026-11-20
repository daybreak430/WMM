package com.wmm.ledger.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

internal data class PaymentCaptureState(val pending: List<PaymentCapture> = emptyList(), val count: Int = 0,
    val loaded: Boolean = false)

internal class PaymentCaptureRepository(private val database: LedgerDatabase, private val ledger: LedgerRepository) {
    private val dao get() = database.paymentCaptures()

    fun observe() = database.invalidationTracker.createFlow("payment_captures").map {
        database.withTransaction { PaymentCaptureState(dao.pending(), dao.count(), true) }
    }.flowOn(Dispatchers.IO)

    suspend fun prune(now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) { dao.prune(now - RETENTION) }

    suspend fun pending(id: String): PaymentCapture? = withContext(Dispatchers.IO) {
        dao.get(id)?.takeIf { it.status == "PENDING" && System.currentTimeMillis() - it.observedAt <= RETENTION }
    }

    suspend fun enqueue(payment: RecognizedPayment, session: String, now: Long, generation: Long): PaymentCapture? =
        withContext(Dispatchers.IO) { database.withTransaction {
            if (payment.cents !in 1..MAX_CENTS || payment.merchant.length !in 2..80 || payment.method.length > 48) {
                throw LedgerError("付款信息不完整，请手动记账。")
            }
            if (generation != ledger.dataGeneration) return@withTransaction null
            dao.prune(now - RETENTION)
            val fingerprint = payment.fingerprint(session)
            val detailKey = payment.detailFingerprint()
            val existing = dao.byFingerprint(fingerprint) ?: payment.paidAt?.let { paidAt ->
                // 「更多」展开后才有单号，用详情字段找到先前的记录，再保留更稳定的单号键。
                dao.byDetail(payment.source.name, payment.type.name, paidAt, payment.cents).filter {
                    paymentMerchantKey(it.merchant) == paymentMerchantKey(payment.merchant) &&
                        (payment.orderHash == null || it.fingerprint == detailKey)
                }.singleOrNull()
            }
            if (existing != null) {
                val updated = existing.copy(paidAt = existing.paidAt ?: payment.paidAt,
                    fingerprint = if (payment.orderHash != null) fingerprint else existing.fingerprint)
                if (updated != existing) dao.update(updated)
                return@withTransaction updated.takeIf { it.status == "PENDING" }
            }
            if (dao.pending().size >= 200) throw LedgerError("待核对账单已达 200 条，请先处理。")
            PaymentCapture(UUID.randomUUID().toString(), payment.source.name, payment.cents, payment.merchant,
                payment.method, now, fingerprint, paidAt = payment.paidAt, captureType = payment.type.name).also { dao.insert(it) }
        } }

    suspend fun ignore(id: String) = withContext(Dispatchers.IO) { database.withTransaction {
        dao.get(id)?.takeIf { it.status == "PENDING" }?.let { dao.update(it.copy(status = "IGNORED")) }
    } }

    suspend fun clear() = withContext(Dispatchers.IO) { dao.clear() }

    suspend fun confirm(id: String, draft: Entry, allowSimilar: Boolean) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val capture = checkConfirmation(id, CaptureType.EXPENSE) ?: return@withTransaction
            if (!allowSimilar && capture.similarEntries(database.ledger().entries(), draft.cents).isNotEmpty()) {
                throw LedgerError("发现相似流水，请核对后再保存。")
            }
            // 流水写入与识别记录确认共用一个事务，重复点按或进程中断不会生成两笔。
            val entry = ledger.saveEntry(null, Kind.EXPENSE, draft.cents, draft.tagId, draft.accountId, draft.note,
                draft.occurredAt, LocalDate.parse(draft.day), draft.sceneId, hasTime = draft.hasTime)
            dao.update(capture.copy(status = "SAVED", entryId = entry.id))
        }
    }

    suspend fun confirmRefund(id: String, originalId: String, cents: Long, accountId: String?, note: String,
        occurredAt: Long, day: LocalDate) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val capture = checkConfirmation(id, CaptureType.REFUND) ?: return@withTransaction
            val refund = ledger.saveRefund(null, originalId, cents, accountId, note, occurredAt, day)
            dao.update(capture.copy(status = "SAVED", entryId = refund.id))
        }
    }

    suspend fun confirmRepayment(id: String, draft: Transfer) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val capture = checkConfirmation(id, CaptureType.REPAYMENT) ?: return@withTransaction
            if (database.ledger().account(draft.toAccountId)?.isCredit != true) throw LedgerError("请选择信用账户作为还款账户。")
            val transfer = ledger.saveTransfer(null, draft.cents, draft.fromAccountId, draft.toAccountId, draft.note,
                draft.occurredAt, LocalDate.parse(draft.day), draft.hasTime)
            dao.update(capture.copy(status = "SAVED", entryId = transfer.id))
        }
    }

    private suspend fun checkConfirmation(id: String, type: CaptureType): PaymentCapture? {
        val capture = dao.get(id) ?: throw LedgerError("识别记录已移除或过期。")
        if (capture.type != type) throw LedgerError("识别类型已变更，请重新核对。")
        if (capture.status == "SAVED") return null
        if (System.currentTimeMillis() - capture.observedAt > RETENTION) throw LedgerError("识别记录已过期。")
        if (capture.status != "PENDING") throw LedgerError("识别记录已忽略。")
        return capture
    }

    private companion object { const val RETENTION = 30L * 24 * 60 * 60 * 1000 }
}
