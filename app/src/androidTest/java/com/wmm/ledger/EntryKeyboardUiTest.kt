package com.wmm.ledger

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.wmm.ledger.data.Kind
import com.wmm.ledger.data.Account
import com.wmm.ledger.data.Entry
import com.wmm.ledger.data.accountBalances
import com.wmm.ledger.data.LedgerSnapshot
import com.wmm.ledger.data.Scene
import com.wmm.ledger.data.Tag
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EntryKeyboardUiTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun dateOnlyEditorPreservesLegacyTimestampAndCalibratedBalanceWhenSaved() {
        val day = java.time.LocalDate.now().minusDays(1)
        val at = day.atTime(8, 17).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val tag = Tag("meal", Kind.EXPENSE.name, "餐饮")
        val account = Account("bank", "SAVINGS", "光大银行", 100_000, at - 1)
        val entry = Entry("timed", Kind.EXPENSE.name, 800, tag.id, account.id, "", at, day.toString())
        withLedger(LedgerSnapshot(tags = listOf(tag), accounts = listOf(account), entries = listOf(entry))) { repository, _ ->
            ui.waitUntil(10_000) { ui.onNodeWithText(tag.name).isDisplayed() }
            ui.onNodeWithText(tag.name).performClick()
            ui.onNodeWithText("08:17").assertDoesNotExist()
            ui.onNodeWithTag("entry-date").performClick()
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("input keyevent KEYCODE_BACK").close()
            ui.onNodeWithText("保存流水").performClick()
            ui.waitUntil(10_000) { ui.onNodeWithContentDescription("记一笔").isDisplayed() }
            val saved = runBlocking { repository.snapshot() }
            assertEquals(entry, saved.entries.single())
            assertEquals(99_200L, saved.accountBalances()[account.id])
        }
    }

    @Test fun rapidKeyTapsUseTheLatestAmountAndKeyEdgesRemainTappable() {
        val tag = Tag("meal", Kind.EXPENSE.name, "餐饮")
        withLedger(LedgerSnapshot(tags = listOf(tag))) { repository, _ ->
            ui.waitUntil(10_000) { ui.onNodeWithContentDescription("记一笔").isDisplayed() }
            ui.onNodeWithContentDescription("记一笔").performClick()
            ui.onNodeWithText(tag.name).performClick()
            ui.onNodeWithTag("amount-key-5").performTouchInput {
                down(Offset(1f, center.y))
                up()
            }
            ui.onNodeWithTag("amount-key-0").performTouchInput {
                repeat(2) {
                    down(center)
                    advanceEventTime(10)
                    up()
                    advanceEventTime(10)
                }
            }
            ui.onNodeWithText("¥ 500").assertIsDisplayed()
            ui.onNodeWithText("保存流水").performClick()
            ui.waitUntil(10_000) { ui.onNodeWithContentDescription("记一笔").isDisplayed() }
            assertEquals(50_000L, runBlocking { repository.snapshot() }.entries.single().cents)
        }
    }

    @Test fun categorySelectionCalculationAndNoteReturnKeepTheKeypadAvailable() {
        val scenes = listOf(Scene("home", "日常", 0), Scene("trip", "出差", 1), Scene("tour", "旅游", 2))
        val tags = listOf(Tag("lunch", Kind.EXPENSE.name, "午餐", sortOrder = 0),
            Tag("metro", Kind.EXPENSE.name, "地铁", sortOrder = 1), Tag("salary", Kind.INCOME.name, "工资"))
        val account = Account("bank", "SAVINGS", "光大银行储蓄卡账户名称较长的验收场景", 100_000,
            System.currentTimeMillis() - 10_000, isDefault = true)
        withLedger(LedgerSnapshot(tags = tags, scenes = scenes, accounts = listOf(account))) { repository, activity ->
            ui.waitUntil(10_000) { ui.onNodeWithContentDescription("记一笔").isDisplayed() }
            ui.onNodeWithContentDescription("记一笔").performClick()
            ui.onNodeWithTag("amount-keypad").assertIsDisplayed()
            assertFalse(imeVisible(activity))
            val keypadTop = ui.onNodeWithTag("amount-keypad").fetchSemanticsNode().boundsInRoot.top
            val amountBounds = ui.onNodeWithTag("entry-amount").fetchSemanticsNode().boundsInRoot
            val accountBounds = ui.onNodeWithTag("entry-account").fetchSemanticsNode().boundsInRoot
            val dateBounds = ui.onNodeWithTag("entry-date").fetchSemanticsNode().boundsInRoot
            val noteBounds = ui.onNodeWithTag("entry-note").fetchSemanticsNode().boundsInRoot
            assertTrue(accountBounds.bottom <= amountBounds.top || accountBounds.right <= amountBounds.left)
            assertTrue(dateBounds.right <= noteBounds.left)
            assertTrue(noteBounds.width > dateBounds.width)
            assertTrue(noteBounds.bottom < keypadTop)
            assertEquals(dateBounds.center.y, noteBounds.center.y, 1f)
            ui.onNodeWithText("午餐").performClick().assertIsSelected()
            ui.enterAmount("24.80")
            ui.onNodeWithText("收入").performClick().assertIsSelected()
            ui.onNodeWithText("工资").performScrollTo().performClick()
            ui.onNodeWithText("支出").performClick().assertIsSelected()
            ui.onNodeWithText("午餐").performScrollTo().performClick().assertIsSelected()
            ui.onNodeWithText("出差").performScrollTo().performClick()
            ui.enterAmount("+0.20")
            assertEquals(keypadTop, ui.onNodeWithTag("amount-keypad").fetchSemanticsNode().boundsInRoot.top, 1f)
            assertFalse(imeVisible(activity))
            ui.onNodeWithText("保存流水").assertIsEnabled()
            ui.onNodeWithTag("entry-note").performClick()
            ui.onNodeWithText("备注内容").performClick().performTextInput("午饭备注")
            ui.waitUntil(5_000) { imeVisible(activity) }
            ui.onNodeWithText("确认").performClick()
            ui.waitUntil(5_000) { !imeVisible(activity) }
            ui.onNodeWithTag("amount-keypad").assertIsDisplayed()
            activity.recreate()
            ui.onNodeWithTag("amount-keypad").assertIsDisplayed()
            ui.onNodeWithText("¥ 25.00").assertIsDisplayed()
            ui.onNodeWithText("保存流水").performClick()
            ui.waitUntil(10_000) { ui.onNodeWithContentDescription("记一笔").isDisplayed() }
            val entry = runBlocking { repository.snapshot() }.entries.single()
            assertEquals(2_500L, entry.cents)
            assertEquals("lunch", entry.tagId)
            assertEquals("trip", entry.sceneId)
            assertEquals("午饭备注", entry.note)
        }
    }

    @Test fun scrollingManyCategoriesDoesNotMoveTheAmountOrKeypadAndBackClosesTheEditor() {
        val tags = (0..35).map { Tag("tag-$it", Kind.EXPENSE.name, "分类$it", sortOrder = it) }
        withLedger(LedgerSnapshot(tags = tags)) { _, activity ->
            ui.waitUntil(10_000) { ui.onNodeWithContentDescription("记一笔").isDisplayed() }
            ui.onNodeWithContentDescription("记一笔").performClick()
            val top = ui.onNodeWithTag("entry-amount").fetchSemanticsNode().boundsInRoot.top
            ui.onNodeWithText("全部类型").performScrollTo().performClick()
            ui.onNodeWithText("分类35").performScrollTo().performClick().assertIsSelected()
            ui.enterAmount("9.99")
            assertEquals(top, ui.onNodeWithTag("entry-amount").fetchSemanticsNode().boundsInRoot.top, 1f)
            assertFalse(imeVisible(activity))
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("input keyevent KEYCODE_BACK").close()
            ui.waitUntil(5_000) { ui.onNodeWithContentDescription("记一笔").isDisplayed() }
        }
    }

    private fun imeVisible(activity: ActivityScenario<MainActivity>): Boolean {
        var visible = false
        activity.onActivity {
            visible = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return visible
    }
}
