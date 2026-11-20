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
            dao.byFingerprint(fingerprint)?.let { existing ->
                if (existing.status != "PENDING") return@withTransaction null
                // 付款成功页已有待核对记录时，用详情页补全原交易时间，保留同一条候选。
                if (existing.paidAt == null && payment.paidAt != null) {
                    return@withTransaction existing.copy(paidAt = payment.paidAt).also { dao.update(it) }
                }
                return@withTransaction existing
            }
            if (dao.pending().size >= 200) throw LedgerError("待核对账单已达 200 条，请先处理。")
            PaymentCapture(UUID.randomUUID().toString(), payment.source.name, payment.cents, payment.merchant,
                payment.method, now, fingerprint, paidAt = payment.paidAt).also { dao.insert(it) }
        } }

    suspend fun ignore(id: String) = withContext(Dispatchers.IO) { database.withTransaction {
        dao.get(id)?.takeIf { it.status == "PENDING" }?.let { dao.update(it.copy(status = "IGNORED")) }
    } }

    suspend fun clear() = withContext(Dispatchers.IO) { dao.clear() }

    suspend fun confirm(id: String, draft: Entry, allowSimilar: Boolean) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val capture = dao.get(id) ?: throw LedgerError("识别记录已移除或过期。")
            if (capture.status == "SAVED") return@withTransaction
            if (System.currentTimeMillis() - capture.observedAt > RETENTION) throw LedgerError("识别记录已过期，请手动记账。")
            if (capture.status != "PENDING") throw LedgerError("识别记录已忽略。")
            if (!allowSimilar && capture.similarEntries(database.ledger().entries(), draft.cents).isNotEmpty()) {
                throw LedgerError("发现相似流水，请核对后再保存。")
            }
            // 流水写入与识别记录确认共用一个事务，重复点按或进程中断不会生成两笔。
            val entry = ledger.saveEntry(null, Kind.EXPENSE, draft.cents, draft.tagId, draft.accountId, draft.note,
                draft.occurredAt, LocalDate.parse(draft.day), draft.sceneId, hasTime = draft.hasTime)
            dao.update(capture.copy(status = "SAVED", entryId = entry.id))
        }
    }

    private companion object { const val RETENTION = 30L * 24 * 60 * 60 * 1000 }
}
