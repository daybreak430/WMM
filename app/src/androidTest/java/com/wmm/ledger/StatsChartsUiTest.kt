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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.wmm.ledger.data.Entry
import com.wmm.ledger.data.Kind
import com.wmm.ledger.data.LedgerSnapshot
import com.wmm.ledger.data.Scene
import com.wmm.ledger.data.Tag
import com.wmm.ledger.data.Report
import com.wmm.ledger.data.Period
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StatsChartsUiTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun allPeriodsKeepChartsAndHideLegacyReportActions() {
        val today = LocalDate.now()
        val tags = listOf(Tag("expense", "EXPENSE", "餐饮"), Tag("income", "INCOME", "工资"))
        val entries = tags.mapIndexed { index, tag -> Entry("record-$index", tag.kind, 1200, tag.id, null, "", 1, today.toString()) }
        val reports = listOf(Period.WEEK, Period.MONTH, Period.YEAR).map { period ->
            Report(period.name, period.start(today).toString(), "0".repeat(64), 1, "DEEPSEEK", "旧报告-${period.title}")
        }
        withLedger(LedgerSnapshot(tags = tags, entries = entries, reports = reports)) { repository, _ ->
            ui.waitUntil(10_000) { ui.onNodeWithText("收支统计").isDisplayed() }
            ui.onNodeWithText("收支统计").performClick()
            val list = ui.onNode(hasScrollAction())
            listOf("周", "月", "年").forEach { period ->
                list.performScrollToIndex(0)
                ui.onNodeWithText(period).performClick()
                list.performScrollToNode(hasContentDescription("收入类型占比环形图"))
                ui.onNodeWithContentDescription("收入类型占比环形图").assertIsDisplayed()
                repeat(3) { list.performTouchInput { swipeUp() } }
                ui.onNodeWithText("AI ${period}报").assertDoesNotExist()
                ui.onNodeWithText("旧报告-$period").assertDoesNotExist()
                ui.onNodeWithText("生成报告").assertDoesNotExist()
                ui.onNodeWithText("设置模型").assertDoesNotExist()
            }
            assertEquals(reports, runBlocking { repository.snapshot() }.reports)
        }
    }

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
            list.performScrollToNode(unassigned)
            ui.onNode(unassigned and hasText("25%") and hasText("¥20.00")).assertIsDisplayed()
            list.performScrollToNode(hasText(travel.name))
            ui.onNode(hasText(travel.name) and hasText("75%")).performClick()
            ui.onNodeWithText("出差 · 晚餐").assertIsDisplayed()
        }
    }
}
