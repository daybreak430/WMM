package com.wmm.ledger

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.wmm.ledger.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TypedCaptureUiTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun refundAndRepaymentUseTheirOwnReviewFlows() {
        val ledger = LedgerRepository.shared(InstrumentationRegistry.getInstrumentation().targetContext)
        val purchaseDay = LocalDate.of(2025, 4, 5)
        val refundDay = purchaseDay.plusDays(1)
        val purchasedAt = purchaseDay.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val paidAt = refundDay.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val tag = Tag("test-purchase", "EXPENSE", "验收支出")
        val bank = Account("test-bank", "SAVINGS", "验收储蓄卡", 50_000, purchasedAt - 1)
        val credit = Account("test-credit", "CREDIT", "验收信用账户", -20_000, purchasedAt - 1)
        val original = Entry("test-original", "EXPENSE", 2_000, tag.id, bank.id, "待关联原单", purchasedAt, purchaseDay.toString())
        val snapshot = LedgerSnapshot(listOf(original), listOf(tag), listOf(bank, credit))
        withLedger(snapshot) { _, _ ->
            ui.waitUntil(10_000) { ui.onNodeWithContentDescription("个人设置").isDisplayed() }
            ui.onNodeWithContentDescription("个人设置").performClick()
            ui.onNodeWithText("自动记账").performScrollTo().performClick()
            fun enqueue(amount: Long, merchant: String, type: CaptureType) = runBlocking {
                ledger.captures.enqueue(RecognizedPayment(PaymentSource.ALIPAY, amount, merchant, "储蓄卡", null, paidAt, type),
                    "test", System.currentTimeMillis(), ledger.dataGeneration)
            }
            enqueue(600, "验收退款", CaptureType.REFUND)
            ui.waitUntil(10_000) { ui.onNodeWithText("待核对 · 1").isDisplayed() }
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("验收退款"))
            ui.onNodeWithText("验收退款").performClick()
            ui.waitUntil(10_000) { ui.onNodeWithText("核对支付宝退款").isDisplayed() }
            ui.onNodeWithText("保存退款").assertIsNotEnabled()
            ui.onNodeWithText("原支出").performClick()
            ui.onNodeWithText("待关联原单").performClick()
            ui.onNodeWithText("保存退款").performClick()
            ui.waitUntil(10_000) { runBlocking { ledger.snapshot() }.entries.any { it.refundOfId == original.id } }
            ui.waitUntil(10_000) { ui.onNodeWithText("待核对 · 0").isDisplayed() }
            enqueue(1_500, "验收还款", CaptureType.REPAYMENT)
            ui.waitUntil(10_000) { ui.onNodeWithText("待核对 · 1").isDisplayed() }
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("验收还款"))
            ui.onNodeWithText("验收还款").performClick()
            ui.waitUntil(10_000) { ui.onNodeWithText("核对支付宝还款").isDisplayed() }
            ui.onNodeWithText("转出账户").performClick()
            ui.onNodeWithText(bank.name).performClick()
            ui.onNodeWithText("还款账户").performClick()
            ui.onNodeWithText(credit.name).performClick()
            ui.onNodeWithText("保存转账").performClick()
            ui.waitUntil(10_000) { runBlocking { ledger.snapshot() }.transfers.size == 1 }
            val saved = runBlocking { ledger.snapshot() }
            assertEquals(47_100L, saved.accountBalances()[bank.id])
            assertEquals(-18_500L, saved.accountBalances()[credit.id])
        }
    }
}
