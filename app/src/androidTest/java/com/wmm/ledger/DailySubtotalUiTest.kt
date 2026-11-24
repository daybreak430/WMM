package com.wmm.ledger

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import com.wmm.ledger.data.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import java.time.LocalDate
import java.time.ZoneId

class DailySubtotalUiTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun ledgerShowsDailyIncomeAndNetExpenseAndHidesMissingDirections() {
        val today = LocalDate.now()
        fun entry(id: String, kind: Kind, cents: Long, day: LocalDate) = Entry(id, kind.name, cents,
            kind.name, null, id, day.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), day.toString())
        val purchase = entry("today-purchase", Kind.EXPENSE, 3500, today)
        val entries = listOf(purchase, entry("salary", Kind.INCOME, 50000, today),
            entry("yesterday-income", Kind.INCOME, 1200, today.minusDays(1)),
            entry("refund", Kind.EXPENSE, 500, today).copy(refundOfId = purchase.id))
        val tags = Kind.entries.map { Tag(it.name, it.name, it.title) }
        val at = today.minusDays(2).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val accounts = listOf(Account("bank", "SAVINGS", "银行", 10000, at - 1),
            Account("credit", "CREDIT", "信用账户", -10000, at - 1))
        val transfer = Transfer("transfer", 1000, "bank", "credit", "仅有转账的一天", at, today.minusDays(2).toString())
        withLedger(LedgerSnapshot(entries, tags, accounts, transfers = listOf(transfer))) { _, _ ->
            ui.waitUntil(10_000) { ui.onNodeWithContentDescription("个人设置").isDisplayed() }
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("收 ¥500.00"))
            ui.onNodeWithText("今天").assertIsDisplayed()
            ui.onNodeWithText("收 ¥500.00").assertIsDisplayed()
            ui.onNodeWithText("支 ¥30.00").assertIsDisplayed()
            capture("daily-subtotal.png")
            ui.onNode(hasScrollAction()).performTouchInput { swipeUp() }
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("收 ¥12.00"))
            ui.onNodeWithText("收 ¥12.00").assertIsDisplayed()
            ui.onNode(hasScrollAction()).performScrollToNode(hasText(transfer.note))
            ui.onNodeWithText(transfer.note).assertIsDisplayed()
            ui.onNodeWithText("收 ¥0.00").assertDoesNotExist()
            ui.onNodeWithText("支 ¥0.00").assertDoesNotExist()
            ui.onNodeWithText(transfer.note).performTouchInput { click() }
            ui.waitUntil(5_000) { ui.onNodeWithText("保存转账").isDisplayed() }
            InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            ui.waitUntil(5_000) { ui.onNodeWithContentDescription("个人设置").isDisplayed() }
        }
    }

    @Test fun longDailyAmountsStayCompleteBesideAnOlderDate() {
        val day = LocalDate.now().minusYears(1)
        val at = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val income = Entry("large-income", "INCOME", 99_999_999_999, "salary", null, "工资", at, day.toString())
        val expense = Entry("large-expense", "EXPENSE", 98_765_432_100, "purchase", null, "采购", at, day.toString())
        val tags = listOf(Tag("salary", "INCOME", "工资"), Tag("purchase", "EXPENSE", "采购"))
        withLedger(LedgerSnapshot(entries = listOf(income, expense), tags = tags)) { _, _ ->
            ui.waitUntil(10_000) { ui.onNodeWithContentDescription("个人设置").isDisplayed() }
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("支 ¥987,654,321.00"))
            ui.onNodeWithText(day.toString()).assertIsDisplayed()
            listOf("收 ¥999,999,999.99", "支 ¥987,654,321.00").forEach { amount ->
                val layouts = mutableListOf<TextLayoutResult>()
                ui.onNodeWithText(amount).assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals(1, layouts.single().lineCount)
                assertFalse(layouts.single().hasVisualOverflow)
            }
            capture("daily-subtotal-long.png")
        }
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(instrumentation.targetContext.cacheDir, name).outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }
}
