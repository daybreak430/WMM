package com.wmm.ledger

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.wmm.ledger.data.*
import org.junit.Rule
import org.junit.Test
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
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            java.io.File(instrumentation.targetContext.cacheDir, "daily-subtotal.png").outputStream().use {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            screenshot.recycle()
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("收 ¥12.00"))
            ui.onNodeWithText("收 ¥12.00").assertIsDisplayed()
            ui.onNode(hasScrollAction()).performScrollToNode(hasText(transfer.note))
            ui.onNodeWithText(transfer.note).assertIsDisplayed()
            ui.onNodeWithText("收 ¥0.00").assertDoesNotExist()
            ui.onNodeWithText("支 ¥0.00").assertDoesNotExist()
        }
    }
}
