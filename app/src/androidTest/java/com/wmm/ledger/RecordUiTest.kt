package com.wmm.ledger

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RecordUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun firstEntryUsesNewTagAndSceneThenShowsInStatsAndCanBeRemoved() {
        val tag = "验收${System.nanoTime().toString().takeLast(8)}"
        ui.waitUntil(10_000) { ui.onAllNodesWithText("记一笔").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("记一笔").performClick()
        ui.onNodeWithTag("entry-amount").assertIsFocused().performTextInput("12.35")
        ui.onNodeWithText("保存流水").assertIsNotEnabled()
        ui.onNodeWithText("新建标签").performScrollTo().performClick()
        ui.onNodeWithText("标签名称").performTextInput(tag)
        ui.onNodeWithText("保存").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText(tag).fetchSemanticsNodes().isNotEmpty() }
        val scene = "出差${tag.takeLast(4)}"
        val combined = "$scene · $tag"
        ui.onNodeWithText("新建场景").performScrollTo().performClick()
        ui.onNodeWithText("场景名称").performTextInput(scene)
        ui.onNodeWithText("保存").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText(scene).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText(scene).assertIsOn().performClick().assertIsOff()
        ui.onNodeWithText(scene).performClick().assertIsOn()

        ui.onNodeWithText("日期时间").performScrollTo().performClick()
        ui.onNodeWithText("日期").assertIsDisplayed()
        ui.onNodeWithText("时间").assertIsDisplayed()
        ui.onNodeWithText("取消").performClick()

        ui.onNodeWithText("备注").performScrollTo().performClick()
        ui.onNodeWithText("备注内容").performTextInput("首笔账单")
        ui.onNodeWithText("确认").performClick()
        ui.onNodeWithText("首笔账单").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("保存流水").assertIsEnabled().performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("最近流水").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText(combined).assertIsDisplayed()
        ui.onNode(hasText(combined) and hasText("−¥12.35")).assertIsDisplayed()

        ui.onNodeWithText("收支统计").performClick()
        ui.onNodeWithText("月").assertIsDisplayed()
        ui.onNode(hasScrollAction()).performScrollToNode(hasText(scene))
        ui.onNodeWithText(scene).performClick()
        val sceneEntry = ui.onNode(hasText(combined) and hasText("−¥12.35"))
        ui.waitUntil(8_000) { sceneEntry.isDisplayed() }
        sceneEntry.assertIsDisplayed()
        ui.onNodeWithContentDescription("返回").performClick()

        ui.onNode(hasScrollAction()).performScrollToNode(hasText(tag))
        ui.onNodeWithText(tag).performClick()
        ui.onNodeWithText("1 笔").assertIsDisplayed()
        ui.onNode(hasText(combined) and hasText("−¥12.35")).performClick()
        ui.onNodeWithText("首笔账单").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText(scene).performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("收入").performScrollTo().performClick()
        ui.onNodeWithText("场景").assertDoesNotExist()
        ui.onNodeWithText("支出").performClick()
        ui.onNodeWithText(scene).assertIsDisplayed()
        ui.onNodeWithContentDescription("删除流水").performClick()
        ui.onNodeWithText("删除").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("0 笔").fetchSemanticsNodes().isNotEmpty() }
        ui.runOnUiThread { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("年"))
        ui.onNodeWithText("年").performClick()
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("AI 年报"))
        ui.onNodeWithText("AI 年报").assertIsDisplayed()
    }

    @Test fun statsFiltersAndBottomTabsShowOnlyTheSelectedContentOnTheNextFrame() {
        ui.waitUntil(10_000) { ui.onAllNodesWithText("记一笔").fetchSemanticsNodes().isNotEmpty() }
        ui.mainClock.autoAdvance = false
        try {
            ui.onNodeWithText("收支统计").performClick()
            ui.mainClock.advanceTimeByFrame()
            ui.onNodeWithText("本月收支看板").assertIsDisplayed()
            ui.onNodeWithText("最近流水").assertDoesNotExist()

            ui.onNodeWithText("周").performClick()
            ui.mainClock.advanceTimeByFrame()
            ui.onNodeWithText("本周收支看板").assertIsDisplayed()
            ui.onNodeWithText("周内收支趋势").assertIsDisplayed()
            ui.onNodeWithText("本月收支看板").assertDoesNotExist()

            ui.onNodeWithText("年").performClick()
            ui.mainClock.advanceTimeByFrame()
            ui.onNodeWithText("本年收支看板").assertIsDisplayed()
            ui.onNodeWithText("周内收支趋势").assertDoesNotExist()

            ui.onNodeWithContentDescription("上一年").performClick()
            ui.mainClock.advanceTimeByFrame()
            ui.onNodeWithText("年度收支看板").assertIsDisplayed()
            ui.onNodeWithText("${LocalDate.now().year - 1}年").assertIsDisplayed()
            ui.onNodeWithText("${LocalDate.now().year}年").assertDoesNotExist()

            ui.onNodeWithText("账本").performClick()
            ui.mainClock.advanceTimeByFrame()
            ui.onNodeWithText("最近流水").assertIsDisplayed()
            ui.onNodeWithText("年度收支看板").assertDoesNotExist()

            ui.onNodeWithText("统计").performClick()
            ui.mainClock.advanceTimeByFrame()
            ui.onNodeWithText("年度收支看板").assertIsDisplayed()
            ui.onNodeWithText("最近流水").assertDoesNotExist()

            ui.onNodeWithText("账本").performClick()
            ui.mainClock.advanceTimeByFrame()
            ui.onNodeWithText("最近流水").assertIsDisplayed()
            ui.onNodeWithText("年度收支看板").assertDoesNotExist()
        } finally {
            ui.mainClock.autoAdvance = true
        }
    }

    @Test fun bottomTabDragCommitsOnReleaseAndKeepsThePageOnCancel() {
        ui.waitUntil(10_000) { ui.onAllNodesWithText("记一笔").fetchSemanticsNodes().isNotEmpty() }
        val ledger = ui.onNode(hasText("账本") and hasClickAction())
        val stats = ui.onNode(hasText("统计") and hasClickAction())
        val distance = stats.fetchSemanticsNode().boundsInRoot.center.x - ledger.fetchSemanticsNode().boundsInRoot.center.x

        ledger.performTouchInput {
            down(center)
            repeat(8) { step -> moveTo(center + Offset(distance * (step + 1) / 8, 0f)) }
        }
        ui.onNodeWithText("记一笔").assertIsDisplayed()
        ledger.performTouchInput { up() }
        ui.onNodeWithText("本月收支看板").assertIsDisplayed()

        stats.performTouchInput {
            down(center)
            repeat(8) { step -> moveTo(center - Offset(distance * (step + 1) / 8, 0f)) }
            cancel()
        }
        ui.onNodeWithText("本月收支看板").assertIsDisplayed()

        stats.performTouchInput {
            down(center)
            repeat(8) { step -> moveTo(center - Offset(distance * (step + 1) / 8, 0f)) }
            up()
        }
        ui.onNodeWithText("记一笔").assertIsDisplayed()
    }

    @Test fun assetAccountCanBeAddedAndCalibratedFromItsCard() {
        val account = "验收卡${System.nanoTime().toString().takeLast(8)}"
        ui.waitUntil(10_000) { ui.onAllNodesWithText("记一笔").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("资产").assertDoesNotExist()
        ui.onNodeWithContentDescription("个人设置").performClick()
        ui.onNodeWithText("资产").performScrollTo().performClick()
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

    @Test fun recordActionOpensFromStatsAndReturnsToTheSameTab() {
        ui.waitUntil(10_000) { ui.onAllNodesWithText("记一笔").fetchSemanticsNodes().isNotEmpty() }
        val ledger = ui.onNode(hasText("账本") and hasClickAction())
        val stats = ui.onNode(hasText("统计") and hasClickAction())
        val record = ui.onNodeWithText("记一笔")
        val ledgerX = ledger.fetchSemanticsNode().boundsInRoot.center.x
        val statsX = stats.fetchSemanticsNode().boundsInRoot.center.x
        val recordX = record.fetchSemanticsNode().boundsInRoot.center.x
        assertEquals((ledgerX + statsX) / 2, recordX, 1f)

        stats.performClick()
        stats.performTouchInput {
            down(center)
            repeat(8) { step -> moveTo(center + Offset((recordX - statsX) * (step + 1) / 8, 0f)) }
            up()
        }
        ui.onNodeWithText("本月收支看板").assertIsDisplayed()

        record.performTouchInput {
            down(center)
            repeat(8) { step -> moveTo(center + Offset((ledgerX - recordX) * (step + 1) / 8, 0f)) }
            up()
        }
        ui.onNodeWithText("本月收支看板").assertIsDisplayed()

        record.performClick()
        ui.onNodeWithTag("entry-amount").assertIsFocused()
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithText("本月收支看板").assertIsDisplayed()
    }
}
