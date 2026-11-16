package com.wmm.ledger

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.wmm.ledger.data.Kind
import com.wmm.ledger.data.LedgerSnapshot
import com.wmm.ledger.data.Scene
import com.wmm.ledger.data.Tag
import org.junit.Rule
import org.junit.Test

class EntryKeyboardUiTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun sceneAndTypeCanBeTappedWhileTheKeyboardIsOpen() {
        val scenes = listOf(
            Scene("home", "日常", 0, "HOME", "SLATE"),
            Scene("trip", "出差", 1, "WORK", "INDIGO"),
            Scene("tour", "旅游", 2, "TRAVEL", "ROSE"),
        )
        val tags = listOf(
            Tag("lunch", Kind.EXPENSE.name, "午餐", sortOrder = 0, iconKey = "MEAL", iconColor = "AMBER"),
            Tag("metro", Kind.EXPENSE.name, "地铁", sortOrder = 1, iconKey = "TRAIN", iconColor = "AZURE"),
            Tag("salary", Kind.INCOME.name, "工资"),
        )
        withLedger(LedgerSnapshot(tags = tags, scenes = scenes)) { _, activity ->
            ui.waitUntil(10_000) { ui.onNodeWithContentDescription("记一笔").isDisplayed() }
            ui.onNodeWithContentDescription("记一笔").performClick()
            ui.waitUntil(8_000) { imeVisible(activity) }
            ui.onNodeWithContentDescription("记一笔").assertDoesNotExist()
            val amount = ui.onNodeWithTag("entry-amount")
            amount.assertIsFocused().performTextInput("24.80")
            ui.onNodeWithText("收入").performClick().assertIsSelected()
            ui.onNodeWithText("工资").assertIsDisplayed()
            ui.onNodeWithText("支出").performClick().assertIsSelected()
            ui.waitUntil(5_000) { imeVisible(activity) }
            ui.onNodeWithText("出差").assertIsDisplayed()
            ui.onNodeWithText("午餐").assertIsDisplayed().performClick().assertIsSelected()
            ui.onNodeWithText("保存流水").assertIsEnabled()

            amount.performClick()
            ui.waitUntil(5_000) { imeVisible(activity) }
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            automation.executeShellCommand("input keyevent KEYCODE_BACK").close()
            ui.waitUntil(5_000) { !imeVisible(activity) }
            ui.onNodeWithText("保存流水").performClick()
            ui.waitUntil(10_000) { ui.onNodeWithText("日常 · 午餐").isDisplayed() }
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
