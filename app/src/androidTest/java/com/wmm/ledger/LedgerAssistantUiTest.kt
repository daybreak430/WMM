package com.wmm.ledger

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.wmm.ledger.data.*
import com.wmm.ledger.ui.LedgerQuestionScreen
import com.wmm.ledger.ui.LedgerTheme
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LedgerAssistantUiTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun homeSearchFindsCombinedFieldsAndReturnsFromEditing() {
        val day = LocalDate.now().toString()
        val tag = Tag("meal", "EXPENSE", "午餐")
        val bank = Account("bank", "SAVINGS", "测试银行", 100_000, 0)
        val credit = Account("credit", "CREDIT", "花呗", 0, 0)
        val scene = Scene("trip", "出差")
        val entry = Entry("found", "EXPENSE", 1850, tag.id, bank.id, "工作餐", System.currentTimeMillis(), day, scene.id)
        withLedger(LedgerSnapshot(tags = listOf(tag), accounts = listOf(bank, credit), scenes = listOf(scene),
            entries = listOf(entry, entry.copy(id = "other", cents = 2000, note = "其他")),
            transfers = listOf(Transfer("transfer", 5000, bank.id, credit.id, "还款", System.currentTimeMillis(), day)))) { _, _ ->
            ui.waitUntil(10_000) { ui.onNodeWithContentDescription("搜索流水").isDisplayed() }
            ui.onNodeWithText("记个账").assertDoesNotExist()
            ui.onNodeWithContentDescription("问 AI").assertIsDisplayed()
            ui.onNodeWithContentDescription("搜索流水").performClick()
            ui.onNodeWithTag("ledger-search").performTextInput("测试银行 出差 18.50 工作餐")
            ui.onNodeWithTag("ledger-search").performImeAction()
            ui.waitUntil(10_000) { ui.onNodeWithText("1 笔").isDisplayed() }
            ui.onNodeWithText("工作餐").performClick()
            ui.onNodeWithTag("entry-note").assertTextEquals("工作餐")
            ui.onNodeWithContentDescription("返回").performClick()
            ui.onNodeWithTag("ledger-search").assertTextContains("测试银行 出差 18.50 工作餐")
            ui.onNodeWithContentDescription("清空搜索").performClick()
            ui.onNodeWithTag("ledger-search").performTextInput("花呗 50")
            ui.onNodeWithTag("ledger-search").performImeAction()
            ui.waitUntil(10_000) { ui.onNodeWithText("还款").isDisplayed() }
            ui.onNodeWithText("还款").performClick()
            ui.onNodeWithTag("entry-note").assertTextEquals("还款")
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK").close()
            ui.waitUntil(5_000) { ui.onNodeWithTag("ledger-search").isDisplayed() }
            ui.onNodeWithContentDescription("返回").performClick()
            ui.onNodeWithContentDescription("问 AI").performClick()
            ui.onNodeWithTag("ledger-question").assertIsDisplayed()
        }
    }

    @Test fun questionConsentCanBeCancelledThenAcceptedAndEvidenceOpened() {
        val turns = mutableStateOf<List<QuestionTurn>>(emptyList())
        val draft = mutableStateOf("")
        var sent = 0
        var opened: Long? = null
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            activity.onActivity { owner -> owner.setContent {
                LedgerTheme(false) {
                    LedgerQuestionScreen(turns.value, draft.value, { draft.value = it }, Provider.DEEPSEEK, true, false, {}, {}, { question, consent ->
                        assertTrue(consent); sent++
                        turns.value = listOf(QuestionTurn(1, question, LedgerAnswer("最大一笔 18.50 元。",
                            listOf(LedgerReference(QuerySource.ENTRIES, "found")), false)))
                    }, { opened = it })
                }
            } }
            ui.onNodeWithTag("ledger-question").performTextInput("今年最大一笔？")
            ui.onNodeWithTag("ledger-question").performImeAction()
            ui.onNodeWithText("取消").performClick()
            ui.runOnIdle { assertEquals(0, sent) }
            ui.onNodeWithTag("ledger-question").assertTextContains("今年最大一笔？")
            ui.onNodeWithContentDescription("发送问题").performClick()
            ui.onNodeWithText("同意并提问").performClick()
            ui.onNodeWithText("最大一笔 18.50 元。").assertIsDisplayed()
            ui.onNodeWithText("查看相关流水").performClick()
            ui.runOnIdle { assertEquals(1, sent); assertEquals(1L, opened) }
        }
    }
}
