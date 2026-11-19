package com.wmm.ledger

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.wmm.ledger.data.Account
import com.wmm.ledger.data.AccountType
import com.wmm.ledger.data.Kind
import com.wmm.ledger.data.LedgerSnapshot
import com.wmm.ledger.data.Period
import com.wmm.ledger.data.Tag
import com.wmm.ledger.data.accountBalances
import com.wmm.ledger.data.stats
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CreditTransferUiTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun creditPaymentAndRepaymentCanBeCreatedEditedAndDeleted() {
        val cutoff = System.currentTimeMillis() - 10_000
        val bank = Account("bank", AccountType.SAVINGS.name, "验收银行卡", 1_000_000, cutoff)
        val credit = Account("credit", AccountType.CREDIT.name, "验收信用卡", -100_000, cutoff, isDefault = true)
        val tag = Tag("meal", Kind.EXPENSE.name, "验收餐饮")
        withLedger(LedgerSnapshot(accounts = listOf(bank, credit), tags = listOf(tag))) { repository, activity ->
            ui.waitUntil(10_000) { ui.onNodeWithContentDescription("记一笔").isDisplayed() }
            ui.onNodeWithContentDescription("记一笔").performClick()
            ui.onNodeWithTag("entry-amount").performTextInput("100")
            ui.onNodeWithText(tag.name).performClick()
            ui.onNodeWithText("转账").performClick()
            ui.onNodeWithText("收入").performClick()
            ui.onNodeWithText("保存流水").assertIsNotEnabled()
            ui.onNodeWithText("支出").performClick()
            ui.onNodeWithText(tag.name).performClick()
            ui.onNodeWithText("保存流水").performClick()
            ui.waitUntil(10_000) { ui.onNodeWithText(tag.name).isDisplayed() }
            assertEquals(-110_000L, runBlocking { repository.snapshot() }.accountBalances().getValue(credit.id))

            ui.onNodeWithContentDescription("记一笔").performClick()
            ui.onNodeWithText("转账").performClick().assertIsSelected()
            ui.onNodeWithTag("entry-amount").performTextInput("500")
            ui.onNodeWithText("收入").performClick().assertIsSelected()
            ui.onNodeWithText("转账").performClick().assertIsSelected()
            ui.onNodeWithTag("entry-amount").assertIsDisplayed()
            ui.onNodeWithText("转入账户").performClick()
            ui.onNodeWithText(credit.name).performClick()
            ui.onNodeWithContentDescription("交换转出和转入账户").performClick()
            ui.onNodeWithContentDescription("交换转出和转入账户").performClick()
            activity.recreate()
            ui.onNodeWithText("转账").assertIsSelected()
            ui.onNodeWithText("保存转账").performClick()
            val direction = "${bank.name} → ${credit.name}"
            ui.waitUntil(10_000) { ui.onNodeWithText(direction).isDisplayed() }
            val paid = runBlocking { repository.snapshot() }
            assertEquals(50_000L, paid.transfers.single().cents)
            assertEquals(mapOf(bank.id to 950_000L, credit.id to -60_000L), paid.accountBalances())
            val today = LocalDate.now()
            assertEquals(10_000L, paid.stats(Period.MONTH, today, today).expense)
            assertEquals(0L, paid.stats(Period.MONTH, today, today).income)

            ui.onNodeWithText(direction).performClick()
            ui.onNodeWithTag("entry-amount").performTextReplacement("200")
            // 系统键盘动画由真实时钟驱动，等待避让完成后再用触屏点击保存按钮。
            ui.waitUntil(5_000) {
                var keyboardTop: Int? = null
                activity.onActivity {
                    val root = it.window.decorView
                    val insets = ViewCompat.getRootWindowInsets(root)
                    if (insets?.isVisible(WindowInsetsCompat.Type.ime()) == true) {
                        keyboardTop = root.height - insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                    }
                }
                val top = keyboardTop
                top != null && ui.onNodeWithText("保存转账").fetchSemanticsNode().boundsInRoot.bottom <= top
            }
            ui.onNodeWithText("保存转账").performClick()
            ui.waitUntil(10_000) { ui.onNodeWithText(direction).isDisplayed() }
            assertEquals(mapOf(bank.id to 980_000L, credit.id to -90_000L),
                runBlocking { repository.snapshot() }.accountBalances())
            ui.onNodeWithText(direction).performClick()
            ui.onNodeWithContentDescription("删除转账").performClick()
            ui.onNodeWithText("删除").performClick()
            ui.waitUntil(10_000) { ui.onNodeWithContentDescription("记一笔").isDisplayed() }
            assertEquals(mapOf(bank.id to 1_000_000L, credit.id to -110_000L),
                runBlocking { repository.snapshot() }.accountBalances())
        }
    }

    @Test fun createsCreditAccountWithDebtAndCalibratesIt() {
        withLedger(LedgerSnapshot()) { repository, _ ->
            ui.waitUntil(10_000) { ui.onNodeWithContentDescription("个人设置").isDisplayed() }
            ui.onNodeWithContentDescription("个人设置").performClick()
            ui.onNodeWithText("资产").performScrollTo().performClick()
            ui.onNodeWithText("添加账户").performClick()
            ui.onNodeWithText("账户名称").performTextInput("月付验收")
            ui.onNodeWithText("账户类型").performClick()
            ui.onNodeWithText("信用卡").assertDoesNotExist()
            ui.onNodeWithText("花呗").assertDoesNotExist()
            ui.onNodeWithText("美团月付").assertDoesNotExist()
            ui.onNodeWithText("其他信用账户").assertDoesNotExist()
            ui.onNodeWithText("信用账户").performScrollTo().performClick()
            ui.onNodeWithText("当前欠款").performTextReplacement("123.45")
            ui.onNodeWithText("保存账户").performClick()
            ui.waitUntil(10_000) { ui.onNodeWithText("待还 ¥123.45").isDisplayed() }
            val account = runBlocking { repository.snapshot() }.accounts.single()
            assertEquals(AccountType.CREDIT.name, account.type)
            assertEquals(-12_345L, account.baselineCents)
            ui.onNodeWithText("月付验收").performClick()
            ui.onNodeWithText("校准余额").performClick()
            ui.onNodeWithText("现在的欠款").performTextReplacement("80")
            ui.onNodeWithText("确认校准").performClick()
            ui.waitUntil(10_000) { ui.onNodeWithText("待还 ¥80.00").isDisplayed() }
            assertEquals(-8_000L, runBlocking { repository.snapshot() }.accountBalances().getValue(account.id))
        }
    }

    @Test fun transferNeedsTwoAccountsAndSystemBackReturnsToTheOrigin() {
        withLedger(LedgerSnapshot()) { _, activity ->
            ui.waitUntil(10_000) { ui.onNodeWithContentDescription("记一笔").isDisplayed() }
            ui.onNodeWithContentDescription("记一笔").performClick()
            ui.onNodeWithText("转账").performClick()
            ui.onNodeWithTag("entry-amount").performTextInput("20")
            ui.onNodeWithText("转入账户").performClick()
            ui.onNodeWithText("暂无其他可用账户").assertIsDisplayed()
            ui.onNodeWithText("关闭").performClick()
            ui.onNodeWithText("保存转账").assertIsNotEnabled()
            activity.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            ui.waitUntil(10_000) { ui.onNodeWithContentDescription("记一笔").isDisplayed() }
        }
    }
}
