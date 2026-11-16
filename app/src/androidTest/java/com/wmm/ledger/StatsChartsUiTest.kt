package com.wmm.ledger

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import com.wmm.ledger.data.Entry
import com.wmm.ledger.data.Kind
import com.wmm.ledger.data.LedgerSnapshot
import com.wmm.ledger.data.Scene
import com.wmm.ledger.data.Tag
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test

class StatsChartsUiTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun chartsFollowThePeriodAndAccountForUnassignedScenes() {
        val today = LocalDate.now()
        val previousWeek = today.minusWeeks(1)
        val dinner = Tag("dinner", Kind.EXPENSE.name, "晚餐")
        val transit = Tag("transit", Kind.EXPENSE.name, "车费")
        val salary = Tag("salary", Kind.INCOME.name, "工资")
        val travel = Scene("travel", "出差")
        fun entry(id: String, tag: Tag, cents: Long, day: LocalDate, scene: Scene? = null): Entry =
            Entry(id, tag.kind, cents, tag.id, null, "", day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                day.toString(), scene?.id)
        val ledger = LedgerSnapshot(
            tags = listOf(dinner, transit, salary), scenes = listOf(travel),
            entries = listOf(entry("dinner", dinner, 6000, today, travel), entry("ride", transit, 2000, today),
                entry("older-ride", transit, 1000, previousWeek), entry("payment", salary, 12000, today)),
        )
        withLedger(ledger) { _, _ ->
            ui.waitUntil(10_000) { ui.onNodeWithText("收支统计").isDisplayed() }
            ui.onNodeWithText("收支统计").performClick()
            val list = ui.onNode(hasScrollAction())
            val sceneChart = "支出场景占比环形图"
            val unassigned = hasText("未标场景")
            list.performScrollToNode(hasContentDescription("支出类型占比环形图"))
            ui.onNodeWithContentDescription("支出类型占比环形图").assertIsDisplayed()
            list.performScrollToNode(hasContentDescription(sceneChart))
            ui.onNodeWithContentDescription(sceneChart).assertIsDisplayed()
            list.performScrollToNode(unassigned)
            ui.onNode(unassigned and hasText(if (today.month == previousWeek.month) "33.3%" else "25%")).assertIsDisplayed()
            list.performScrollToNode(hasContentDescription("收入类型占比环形图"))
            ui.onNodeWithContentDescription("收入类型占比环形图").assertIsDisplayed()

            list.performScrollToIndex(0)
            ui.onNodeWithText("周").performClick()
            list.performScrollToNode(hasText("AI 周报"))
            ui.onNodeWithText("AI 周报").assertIsDisplayed()
            list.performScrollToNode(hasText("AI 日报"))
            ui.onNodeWithText("AI 日报").assertIsDisplayed()
            list.performScrollToNode(unassigned)
            ui.onNode(unassigned and hasText("25%") and hasText("¥20.00")).assertIsDisplayed()
            list.performScrollToNode(hasText(travel.name))
            ui.onNode(hasText(travel.name) and hasText("75%")).performClick()
            ui.onNodeWithText("出差 · 晚餐").assertIsDisplayed()
        }
    }
}
