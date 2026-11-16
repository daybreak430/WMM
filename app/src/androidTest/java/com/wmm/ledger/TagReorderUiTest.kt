package com.wmm.ledger

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.wmm.ledger.data.Kind
import com.wmm.ledger.data.LedgerRepository
import com.wmm.ledger.data.LedgerSnapshot
import com.wmm.ledger.data.Scene
import com.wmm.ledger.data.Tag
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TagReorderUiTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun longPressReordersRowsAndChipsWithoutChangingSelection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = LedgerRepository(context)
        val original = runBlocking { repository.snapshot() }
        val names = listOf("工作午餐", "咖啡茶饮", "电影演出", "日常杂货", "公交地铁", "车辆停车", "健身课程", "行李寄存")
        val sceneNames = listOf("日常生活", "外地出差", "周末旅游")
        val tags = names.mapIndexed { index, name -> Tag("tag-$index", Kind.EXPENSE.name, name, sortOrder = index) }
        val scenes = sceneNames.mapIndexed { index, name -> Scene("scene-$index", name, index) }
        try {
            runBlocking { repository.replace(LedgerSnapshot(tags = tags, scenes = scenes)) }
            ActivityScenario.launch(MainActivity::class.java).use { activity ->
                ui.waitUntil(10_000) { ui.onNodeWithText("记一笔").isDisplayed() }
                openManagement()
                drag(names[0], names[1])
                waitForTagOrder(repository, listOf("tag-1", "tag-0"))
                ui.onNodeWithText(names[1]).performClick()
                ui.onNodeWithText("改名").assertIsDisplayed()
                ui.onNodeWithText("关闭").performClick()
                back()
                back()

                ui.onNodeWithText("记一笔").performClick()
                ui.onNodeWithText("类型").assertIsDisplayed()
                ui.onNodeWithText("必选").assertDoesNotExist()
                ui.onNodeWithText(names[1]).performClick().assertIsSelected()
                assertTrue(position(names[0]).y < position(names[4]).y)
                drag(names[0], names[4])
                waitForTagOrder(repository, listOf("tag-1", "tag-2", "tag-3", "tag-4", "tag-0"))
                ui.onNodeWithText(names[1]).assertIsSelected()
                drag(sceneNames[0], sceneNames[1])
                ui.waitUntil(8_000) {
                    runBlocking { repository.snapshot().scenes.single { it.id == "scene-1" }.sortOrder == 0 }
                }
                ui.onNodeWithText(sceneNames[1]).performClick().assertIsOn()
                back()
                activity.recreate()
                ui.waitUntil(10_000) { ui.onNodeWithText("记一笔").isDisplayed() }
                openManagement()
                assertTrue(position(names[4]).y < position(names[0]).y)
            }
        } finally {
            runBlocking { repository.replace(original) }
        }
    }

    private fun waitForTagOrder(repository: LedgerRepository, prefix: List<String>) {
        ui.waitUntil(8_000) {
            runBlocking { repository.snapshot().tags.sortedBy { it.sortOrder }.take(prefix.size).map { it.id } == prefix }
        }
    }

    private fun openManagement() {
        ui.onNodeWithContentDescription("个人设置").performClick()
        ui.onNodeWithText("标签管理").performClick()
    }

    private fun back() = ui.onNodeWithContentDescription("返回").performClick()

    private fun position(text: String) = ui.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.center

    private fun drag(from: String, to: String) {
        val distance = position(to) - position(from)
        ui.onNodeWithText(from).performTouchInput {
            down(center)
            advanceEventTime(650)
            repeat(16) { step ->
                advanceEventTime(20)
                moveTo(center + distance * ((step + 1) / 16f))
            }
            up()
        }
    }
}
