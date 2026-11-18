package com.wmm.ledger

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.wmm.ledger.data.Entry
import com.wmm.ledger.data.Account
import com.wmm.ledger.data.Kind
import com.wmm.ledger.data.LedgerSnapshot
import com.wmm.ledger.data.Tag
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ScheduledBillsUiTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun createsAndDeletesARuleAndListOmitsEvenExplicitTime() {
        val tag = Tag("ui-subscription", Kind.EXPENSE.name, "自动订阅")
        val yesterday = LocalDate.now().minusDays(1)
        val at = yesterday.atTime(8, 17).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val account = Account("hidden-account", "SAVINGS", "验收关联账户", 100_000, at - 1)
        val entry = Entry("timed", Kind.EXPENSE.name, 800, tag.id, account.id, "", at, yesterday.toString())
        withLedger(LedgerSnapshot(tags = listOf(tag), entries = listOf(entry), accounts = listOf(account))) { repository, activity ->
            ui.waitUntil(10_000) { ui.onNodeWithText(tag.name).isDisplayed() }
            ui.onNodeWithText("08:17").assertDoesNotExist()
            ui.onNodeWithText(account.name).assertDoesNotExist()
            ui.onNodeWithText(tag.name).performClick()
            ui.onNodeWithText(account.name).performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("日期时间").performScrollTo().performClick()
            ui.onNodeWithText("08:17").assertIsDisplayed()
            ui.onNodeWithText("取消").performClick()
            ui.onNodeWithContentDescription("返回").performClick()

            ui.onNodeWithContentDescription("个人设置").performClick()
            ui.onNodeWithText("定时账单").performScrollTo().performClick()
            ui.onNodeWithContentDescription("新建定时账单").performClick()
            ui.onNodeWithText("金额").performTextInput("12.50")
            ui.onNodeWithText("类型标签").performClick()
            ui.onNodeWithText(tag.name).performClick()
            ui.onNodeWithText("每周").performScrollTo().performClick().assertIsSelected()
            ui.onNodeWithText("每年").performClick().assertIsSelected()
            ui.onNodeWithText("每天").performClick().assertIsSelected()
            ui.onNodeWithText("保存定时账单").performClick()
            ui.waitUntil(10_000) { ui.onNodeWithText(tag.name).isDisplayed() }
            val saved = runBlocking { repository.snapshot() }
            assertEquals(2, saved.entries.size)
            assertFalse(saved.entries.first().hasTime)

            ui.onNodeWithText(tag.name).performClick()
            ui.onNodeWithText("金额").performTextReplacement("14.00")
            ui.onNodeWithText("保存定时账单").performClick()
            ui.waitUntil(10_000) { ui.onNodeWithText(tag.name).isDisplayed() }
            val edited = runBlocking { repository.snapshot() }
            assertEquals(1400L, edited.scheduledBills.single().cents)
            assertEquals(saved.entries, edited.entries)

            ui.onNodeWithText(tag.name).performClick()
            ui.onNodeWithContentDescription("删除定时账单").performClick()
            ui.onNodeWithText("删除").performClick()
            ui.waitUntil(10_000) { ui.onNodeWithText("还没有定时账单").isDisplayed() }
            val afterDelete = runBlocking { repository.snapshot() }
            assertEquals(2, afterDelete.entries.size)
            assertTrue(afterDelete.scheduledBills.isEmpty())
            activity.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            ui.waitUntil(10_000) { ui.onNodeWithText("标签管理").isDisplayed() }
        }
    }
}
