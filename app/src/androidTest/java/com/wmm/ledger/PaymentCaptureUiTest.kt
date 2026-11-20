package com.wmm.ledger

import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
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
        shared.secrets.setCaptureAccount(PaymentSource.WECHAT, null)
        val now = System.currentTimeMillis() - 500
        val tag = Tag("capture-meal", "EXPENSE", "验收餐饮")
        val credit = Account("capture-credit", "CREDIT", "验收信用账户", -10_000, now - 10_000)
        try {
            withLedger(LedgerSnapshot(tags = listOf(tag), accounts = listOf(credit))) { _, scenario ->
                ui.waitUntil(10_000) { ui.onNodeWithContentDescription("个人设置").isDisplayed() }
                ui.onNodeWithContentDescription("个人设置").performClick()
                ui.onNodeWithContentDescription("自动记账开关").performScrollTo().assertIsOff().performClick()
                ui.onNodeWithText("开启付款识别？").assertIsDisplayed()
                ui.onNodeWithText("取消").performClick()
                ui.onNodeWithContentDescription("自动记账开关").assertIsOff()
                ui.onNodeWithText("自动记账").performScrollTo().performClick()
                ui.onNodeWithText("使用指引").performClick()
                ui.onNodeWithText("自动记账使用指引").assertIsDisplayed()
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK").close()
                ui.waitUntil(5_000) { !ui.onNodeWithText("自动记账使用指引").isDisplayed() }
                ui.onNodeWithText("识别账单详情").assertIsOff().performClick()
                ui.onNodeWithText("识别账单详情？").assertIsDisplayed()
                ui.onNodeWithText("开启").performClick()
                ui.onNodeWithText("识别账单详情").assertIsOn()
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
                // 停止页面订阅后模拟服务写入，再通过浮层 Intent 唤回，不能依赖缓存列表。
                scenario.moveToState(Lifecycle.State.CREATED)
                android.os.SystemClock.sleep(6_000)
                val historicalAt = java.time.LocalDate.of(2024, 2, 29).atTime(11, 30)
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                val backgroundCapture = runBlocking {
                    shared.captures.enqueue(payment.copy(orderHash = "c".repeat(64), paidAt = historicalAt), "ui", now,
                        shared.dataGeneration)
                }!!
                val reviewIntent = Intent(context, MainActivity::class.java)
                    .setAction(com.wmm.ledger.capture.REVIEW_PAYMENT_ACTION)
                    .putExtra(com.wmm.ledger.capture.PAYMENT_CAPTURE_ID, backgroundCapture.id)
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.onActivity { activity ->
                    val launchIntent = activity.intent
                    InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(activity, reviewIntent)
                    // ActivityScenario 按启动 Intent 跟踪生命周期，恢复测试启动标记后继续验证重建。
                    activity.intent = launchIntent
                }
                ui.waitUntil(10_000) { ui.onNodeWithText("核对微信支出").isDisplayed() }
                scenario.recreate()
                ui.waitUntil(10_000) { ui.onNodeWithText("核对微信支出").isDisplayed() }
                ui.onNodeWithTag("entry-note").assertTextEquals("验收便利店")
                assertEquals(2, runBlocking { shared.snapshot() }.entries.size)
                ui.onNodeWithText(tag.name).performClick()
                ui.onNodeWithText("保存流水").performClick()
                ui.waitUntil(10_000) { runBlocking { shared.snapshot() }.entries.any { it.day == "2024-02-29" } }
                assertEquals(historicalAt, runBlocking { shared.snapshot() }.entries.single { it.day == "2024-02-29" }.occurredAt)
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
