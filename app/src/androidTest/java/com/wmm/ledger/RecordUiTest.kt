package com.wmm.ledger

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import org.junit.Rule
import org.junit.Test

class RecordUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun firstEntryUsesNewTagThenShowsInStatsAndCanBeRemoved() {
        val tag = "验收${System.nanoTime().toString().takeLast(8)}"
        ui.waitUntil(10_000) { ui.onAllNodesWithText("记一笔").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("记一笔").performClick()
        ui.onNodeWithTag("entry-amount").assertIsFocused().performTextInput("12.35")
        ui.onNodeWithText("保存流水").assertIsNotEnabled()
        ui.onNodeWithText("新建标签").performScrollTo().performClick()
        ui.onNodeWithText("标签名称").performTextInput(tag)
        ui.onNodeWithText("保存").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText(tag).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("保存流水").assertIsEnabled().performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("最近流水").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText(tag).assertIsDisplayed()
        ui.onNode(hasText(tag) and hasText("−¥12.35")).assertIsDisplayed()

        ui.onNodeWithText("收支统计").performClick()
        ui.onNodeWithText("月").assertIsDisplayed()
        ui.onNodeWithText(tag).performClick()
        ui.onNodeWithText("1 笔").assertIsDisplayed()
        ui.onNode(hasText(tag) and hasText("−¥12.35")).performClick()
        ui.onNodeWithContentDescription("删除流水").performClick()
        ui.onNodeWithText("删除").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("0 笔").fetchSemanticsNodes().isNotEmpty() }
        ui.runOnUiThread { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.onNodeWithText("这个期间没有流水").assertIsDisplayed()
    }

    @Test fun assetAccountCanBeAddedAndCalibratedFromItsCard() {
        val account = "验收卡${System.nanoTime().toString().takeLast(8)}"
        ui.waitUntil(10_000) { ui.onAllNodesWithText("记一笔").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("资产").performClick()
        ui.onNodeWithText("添加账户").performClick()
        ui.onNodeWithText("账户名称").performTextInput(account)
        ui.onNodeWithText("初始余额").performTextReplacement("127.31")
        ui.onNodeWithText("保存账户").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText(account).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText(account).performClick()
        ui.onNodeWithText("校准余额").performClick()
        ui.onNodeWithText("现在的真实余额").performTextReplacement("5000.00")
        ui.onNodeWithText("确认校准").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("¥5,000.00").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText(account).assertIsDisplayed()
    }
}
