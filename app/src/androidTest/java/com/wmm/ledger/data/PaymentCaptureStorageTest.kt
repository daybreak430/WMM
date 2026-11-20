package com.wmm.ledger.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class PaymentCaptureStorageTest {
    @Test fun confirmsOnceUpdatesCreditDebtAndRejectsStaleCandidatesAfterRestore() = runBlocking {
        val context = isolated()
        val ledger = LedgerRepository(context)
        val now = System.currentTimeMillis() - 1000
        val tag = Tag("meal", "EXPENSE", "餐饮")
        val credit = Account("credit", "CREDIT", "验收信用账户", -10_000, now - 10_000)
        ledger.replace(LedgerSnapshot(tags = listOf(tag), accounts = listOf(credit)))
        val payment = RecognizedPayment(PaymentSource.WECHAT, 1850, "验收便利店", "信用卡", "0".repeat(64))
        val capture = ledger.captures.enqueue(payment, "page-a", now, ledger.dataGeneration)!!
        assertEquals(capture.id, ledger.captures.enqueue(payment, "page-b", now, ledger.dataGeneration)!!.id)
        val detail = payment.copy(paidAt = now - 1_000)
        val enriched = ledger.captures.enqueue(detail, "detail", now, ledger.dataGeneration)!!
        assertEquals(capture.id, enriched.id)
        assertEquals(detail.paidAt, ledger.captures.pending(capture.id)?.paidAt)
        assertEquals(1, ledger.captures.observe().first().pending.size)
        assertEquals(-10_000L, ledger.snapshot().accountBalances()[credit.id])
        val draft = Entry("", "EXPENSE", capture.cents, tag.id, credit.id, capture.merchant, now,
            LedgerRepository.bookingDay(now).toString())
        ledger.captures.confirm(capture.id, draft, false)
        ledger.captures.confirm(capture.id, draft, false)
        assertEquals(1, ledger.snapshot().entries.size)
        assertEquals(-11_850L, ledger.snapshot().accountBalances()[credit.id])
        assertTrue(ledger.captures.observe().first().pending.isEmpty())
        assertNull(ledger.captures.enqueue(payment, "page-c", now, ledger.dataGeneration))
        assertNull(ledger.captures.enqueue(detail, "detail-reopened", now, ledger.dataGeneration))
        assertFalse(Backup.encode(ledger.snapshot()).decodeToString().contains("payment_captures"))

        val other = ledger.captures.enqueue(payment.copy(orderHash = "1".repeat(64)), "page", now, ledger.dataGeneration)!!
        assertTrue(runCatching { ledger.captures.confirm(other.id, draft, false) }.isFailure)
        assertEquals(1, ledger.snapshot().entries.size)
        ledger.captures.confirm(other.id, draft, true)
        assertEquals(2, ledger.snapshot().entries.size)
        val generation = ledger.dataGeneration
        ledger.replace(ledger.snapshot())
        assertEquals(0, ledger.captures.observe().first().count)
        assertNull(ledger.captures.enqueue(payment, "stale", now, generation))
        assertTrue(runCatching { ledger.captures.confirm(other.id, draft, true) }.isFailure)
    }

    @Test fun ignoresAndPrunesWithoutChangingCashflowAndEncryptsAccountBindings() = runBlocking {
        val ledger = LedgerRepository(isolated())
        val now = System.currentTimeMillis()
        val payment = RecognizedPayment(PaymentSource.ALIPAY, 300, "验收地铁", "", null)
        val capture = ledger.captures.enqueue(payment, "one", now, ledger.dataGeneration)!!
        ledger.captures.ignore(capture.id)
        assertTrue(ledger.captures.observe().first().pending.isEmpty())
        assertTrue(ledger.snapshot().entries.isEmpty())
        assertNull(ledger.captures.enqueue(payment, "one", now, ledger.dataGeneration))
        ledger.captures.enqueue(payment, "two", now, ledger.dataGeneration)
        assertEquals(1, ledger.captures.observe().first().pending.size)
        ledger.captures.prune(now + 31L * 86400000)
        assertEquals(0, ledger.captures.observe().first().count)
        ledger.secrets.setCaptureAccount(PaymentSource.ALIPAY, "private-credit-id")
        assertEquals("private-credit-id", ledger.secrets.captureAccount(PaymentSource.ALIPAY))
        ledger.secrets.setCaptureAccount(PaymentSource.ALIPAY, null)
        assertNull(ledger.secrets.captureAccount(PaymentSource.ALIPAY))
    }

    private fun isolated(): Context {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        return object : ContextWrapper(context) {
            override fun getApplicationContext() = this
            override fun getDatabasePath(name: String): File = context.getDatabasePath("$id-$name")
            override fun getSharedPreferences(name: String, mode: Int) = context.getSharedPreferences("$id-$name", mode)
        }
    }
}
