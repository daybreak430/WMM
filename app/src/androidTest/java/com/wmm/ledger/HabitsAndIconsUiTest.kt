package com.wmm.ledger

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import com.wmm.ledger.data.Entry
import com.wmm.ledger.data.Kind
import com.wmm.ledger.data.LedgerSnapshot
import com.wmm.ledger.data.LabelIcon
import com.wmm.ledger.data.Scene
import com.wmm.ledger.data.Tag
import kotlinx.coroutines.runBlocking
import java.time.ZonedDateTime
import org.junit.Rule
import org.junit.Test

class HabitsAndIconsUiTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun localHabitPrefillsAndLabelIconsApplyToBothTypesAndScenes() {
        val now = ZonedDateTime.now()
        val meal = Tag("meal", Kind.EXPENSE.name, "午餐")
        val drink = Tag("drink", Kind.EXPENSE.name, "瑞幸")
        val office = Scene("office", "工作日")
        val leisure = Scene("leisure", "旅行", sortOrder = 0)
        val entries = (1L..3L).map {
            val at = now.minusDays(it)
            Entry("entry-$it", Kind.EXPENSE.name, 1850, meal.id, null, "街角食堂", at.toInstant().toEpochMilli(),
                at.toLocalDate().toString(), office.id)
        }
        withLedger(LedgerSnapshot(entries = entries, tags = listOf(meal, drink), scenes = listOf(office, leisure))) { repository, _ ->
            ui.waitUntil(10_000) { ui.onNodeWithContentDescription("记一笔").isDisplayed() }
            ui.onNodeWithContentDescription("记一笔").performClick()
            ui.enterAmount("18.50")
            ui.onNodeWithText("按记账习惯选中").assertIsDisplayed()
            ui.onNodeWithText("午餐").assertIsSelected()
            ui.onNodeWithText("工作日").assertIsOn()
            ui.onNodeWithText("瑞幸").performClick().assertIsSelected()
            ui.onNodeWithText("按记账习惯选中").assertDoesNotExist()
            ui.onNodeWithContentDescription("关闭").performClick()

            ui.onNodeWithContentDescription("个人设置").performClick()
            ui.onNodeWithText("标签管理").performClick()
            ui.onNodeWithText("配图标").performClick()
            ui.onNodeWithText("AI 一键配图标").performClick()
            if (ui.onNodeWithText("本机推荐").isDisplayed()) ui.onNodeWithText("本机推荐").performClick()
            ui.onNodeWithText("本机推荐预览").assertIsDisplayed()
            ui.onNodeWithText("应用 4 个").performClick()
            ui.waitUntil(8_000) {
                runBlocking {
                    val ledger = repository.snapshot()
                    ledger.tags.any { it.id == meal.id && it.iconKey == LabelIcon.MEAL.name } &&
                        ledger.scenes.any { it.id == leisure.id && it.iconKey == LabelIcon.TRAVEL.name }
                }
            }
            ui.onNodeWithContentDescription("返回").performClick()
            ui.onNodeWithTag("tag-list-icon:${meal.id}", useUnmergedTree = true).assertIsDisplayed()
            ui.onNodeWithContentDescription("返回").performClick()
            ui.onNodeWithContentDescription("返回").performClick()
            ui.onAllNodes(hasText("工作日 · 午餐") and hasText("支出"))[0].assertIsDisplayed()
        }
    }

}
