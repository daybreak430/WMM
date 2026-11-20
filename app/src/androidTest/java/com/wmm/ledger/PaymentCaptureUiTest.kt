package com.wmm.ledger

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.wmm.ledger.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PaymentCaptureUiTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun consentCanBeCancelledAndCapturedPaymentsUseTheRegularEditorAndDuplicateConfirmation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val shared = LedgerRepository.shared(context)
        val prefs = context.getSharedPreferences("payment_capture_settings", Context.MODE_PRIVATE)
        val originalOptions = prefs.all.toMap()
        val originalBinding = shared.secrets.captureAccount(PaymentSource.WECHAT)
        prefs.edit().clear().commit()
        val now = System.currentTimeMillis() - 500
        val tag = Tag("capture-meal", "EXPENSE", "验收餐饮")
        val credit = Account("capture-credit", "CREDIT", "验收信用账户", -10_000, now - 10_000)
        try {
            withLedger(LedgerSnapshot(tags = listOf(tag), accounts = listOf(credit))) { _, _ ->
                ui.waitUntil(10_000) { ui.onNodeWithContentDescription("个人设置").isDisplayed() }
                ui.onNodeWithContentDescription("个人设置").performClick()
                ui.onNodeWithText("自动记账").performScrollTo().performClick()
                ui.onNodeWithText("自动识别付款").performClick()
                ui.onNodeWithText("开启付款识别？").assertIsDisplayed()
                ui.onNodeWithText("取消").performClick()
                ui.onNodeWithText("自动识别付款").assertIsOff()
                ui.onAllNodesWithText("核对时关联账户")[0].performClick()
                ui.onNodeWithText(credit.name).performClick()
                ui.waitUntil(5_000) { shared.secrets.captureAccount(PaymentSource.WECHAT) == credit.id }
                val payment = RecognizedPayment(PaymentSource.WECHAT, 1850, "验收便利店", "信用卡(1234)", "a".repeat(64))
                fun capture(order: String) = runBlocking {
                    shared.captures.enqueue(payment.copy(orderHash = order), "ui", now, shared.dataGeneration)
                }
                capture("a".repeat(64))
                ui.waitUntil(10_000) { ui.onNodeWithText("验收便利店").isDisplayed() }
                ui.onNodeWithText("验收便利店").performClick()
                ui.onNodeWithText("核对微信支出").assertIsDisplayed()
                ui.onNodeWithText("¥ 18.50").assertIsDisplayed()
                ui.onNodeWithTag("entry-note").assertTextEquals("验收便利店")
                ui.onNodeWithText(credit.name).assertIsDisplayed()
                ui.onNodeWithText(tag.name).performClick()
                ui.onNodeWithText("保存流水").performClick()
                ui.waitUntil(10_000) { ui.onNodeWithText("待核对 · 0").isDisplayed() }
                assertEquals(-11_850L, runBlocking { shared.snapshot() }.accountBalances()[credit.id])
                capture("b".repeat(64))
                ui.waitUntil(10_000) { ui.onNodeWithText("验收便利店").isDisplayed() }
                ui.onNodeWithText("验收便利店").performClick()
                ui.onNodeWithText(tag.name).performClick()
                ui.onNodeWithText("保存流水").performClick()
                ui.onNodeWithText("发现相似流水").assertIsDisplayed()
                ui.onNodeWithText("取消").performClick()
                assertEquals(1, runBlocking { shared.snapshot() }.entries.size)
                ui.onNodeWithText("保存流水").performClick()
                ui.onNodeWithText("仍然保存").performClick()
                ui.waitUntil(10_000) { ui.onNodeWithText("待核对 · 0").isDisplayed() }
                assertEquals(2, runBlocking { shared.snapshot() }.entries.size)
                assertEquals(-13_700L, runBlocking { shared.snapshot() }.accountBalances()[credit.id])
            }
        } finally {
            shared.secrets.setCaptureAccount(PaymentSource.WECHAT, originalBinding)
            prefs.edit().clear().apply {
                originalOptions.forEach { (key, value) -> when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                } }
            }.commit()
        }
    }
}
