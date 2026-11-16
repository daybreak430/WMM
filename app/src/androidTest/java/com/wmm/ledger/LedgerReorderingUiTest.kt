package com.wmm.ledger

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.wmm.ledger.data.Account
import com.wmm.ledger.data.Entry
import com.wmm.ledger.data.Kind
import com.wmm.ledger.data.LedgerRepository
import com.wmm.ledger.data.LedgerSnapshot
import com.wmm.ledger.data.Scene
import com.wmm.ledger.data.Tag
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LedgerReorderingUiTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun longPressReordersRowsAndChipsWithoutChangingSelection() {
        val names = listOf("工作午餐", "咖啡茶饮", "电影演出", "日常杂货", "公交地铁", "车辆停车", "健身课程", "行李寄存")
        val sceneNames = listOf("日常生活", "外地出差", "周末旅游")
        val tags = names.mapIndexed { index, name -> Tag("tag-$index", Kind.EXPENSE.name, name, sortOrder = index) }
        val scenes = sceneNames.mapIndexed { index, name -> Scene("scene-$index", name, index) }
        withLedger(LedgerSnapshot(tags = tags, scenes = scenes)) { repository, activity ->
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
            ui.onNodeWithText(sceneNames[0]).assertIsOn()
            ui.onNodeWithText(names[1]).assertIsNotSelected()
            ui.onNodeWithTag("entry-amount").performTextInput("2.00")
            ui.onNodeWithText("保存流水").assertIsNotEnabled()
            ui.onNodeWithText(names[1]).performClick().assertIsSelected()
            assertTrue(position(names[0]).y < position(names[4]).y)
            drag(names[0], names[4])
            waitForTagOrder(repository, listOf("tag-1", "tag-2", "tag-3", "tag-4", "tag-0"))
            ui.onNodeWithText(names[1]).assertIsSelected()
            drag(sceneNames[0], sceneNames[1])
            ui.waitUntil(8_000) {
                runBlocking { repository.snapshot().scenes.single { it.id == "scene-1" }.sortOrder == 0 }
            }
            ui.onNodeWithText(sceneNames[0]).assertIsOn()
            back()
            ui.onNodeWithText("记一笔").performClick()
            ui.onNodeWithText(sceneNames[1]).assertIsOn()
            back()
            activity.recreate()
            ui.waitUntil(10_000) { ui.onNodeWithText("记一笔").isDisplayed() }
            openManagement()
            assertTrue(position(names[4]).y < position(names[0]).y)
        }
    }

    @Test fun accountOrderingAndDefaultPersistWithoutChangingOlderEntries() {
        val now = System.currentTimeMillis() - 1_000
        val day = LedgerRepository.bookingDay(now).toString()
        val tag = Tag("expense", Kind.EXPENSE.name, "旧账测试")
        val primary = Account("a", "SAVINGS", "常用银行卡", 10_000, now)
        val secondary = Account("b", "PREPAID", "备用充值卡", 5_000, now)
        val archived = Account("archived", "SAVINGS", "归档卡", 8_000, now, archived = true)
        val old = Entry("old", Kind.EXPENSE.name, 500, tag.id, null, "", now, day)
        withLedger(LedgerSnapshot(entries = listOf(old), tags = listOf(tag),
            accounts = listOf(primary, secondary, archived))) { repository, _ ->
            ui.waitUntil(10_000) { ui.onNodeWithText("记一笔").isDisplayed() }
            ui.onNodeWithContentDescription("个人设置").performClick()
            ui.onNode(hasText("资产") and hasText("¥150.00")).assertIsDisplayed().performClick()
            ui.onNodeWithText("¥150.00").assertIsDisplayed()
            drag(primary.name, secondary.name)
            ui.waitUntil(8_000) { runBlocking { repository.snapshot().accounts.first().id == secondary.id } }
            assertTrue(position(secondary.name).y < position(primary.name).y)
            ui.onNodeWithText(primary.name).performClick()
            ui.onNodeWithText("校准余额").assertIsDisplayed()
            ui.onNodeWithText("关闭").performClick()
            back()
            ui.onNodeWithText("个人设置").assertIsDisplayed()
            back()

            ui.onNodeWithText("记一笔").performClick()
            openAccountPicker()
            ui.onNode(hasText("不关联") and !hasText("账户")).performClick()
            openAccountPicker()
            ui.onNodeWithText("默认关联账户").performClick()
            assertTrue(position(secondary.name).y < position(primary.name).y)
            ui.onNodeWithText(primary.name).performClick()
            ui.waitUntil(8_000) { runBlocking { repository.snapshot().accounts.single { it.isDefault }.id == primary.id } }
            ui.onNodeWithText("关闭").performClick()
            assertAccount("不关联")
            back()

            ui.onNodeWithText(tag.name).performClick()
            assertAccount("不关联")
            back()
            ui.onNodeWithText("记一笔").performClick()
            assertAccount(primary.name)
            openAccountPicker()
            ui.onNodeWithText("默认关联账户").performClick()
            ui.onNodeWithText("不设置默认账户").performClick()
            ui.waitUntil(8_000) { runBlocking { repository.snapshot().accounts.none { it.isDefault } } }
            ui.onNodeWithText("关闭").performClick()
            assertAccount("不关联")
        }
    }

    private fun openAccountPicker() = ui.onNodeWithText("账户").performScrollTo().performClick()

    private fun assertAccount(name: String) =
        ui.onNode(hasText("账户") and hasText(name)).performScrollTo().assertIsDisplayed()

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

internal fun withLedger(data: LedgerSnapshot, test: (LedgerRepository, ActivityScenario<MainActivity>) -> Unit) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val repository = LedgerRepository(context)
    val original = runBlocking { repository.snapshot() }
    try {
        runBlocking { repository.replace(data) }
        ActivityScenario.launch(MainActivity::class.java).use { test(repository, it) }
    } finally {
        runBlocking { repository.replace(original) }
    }
}
